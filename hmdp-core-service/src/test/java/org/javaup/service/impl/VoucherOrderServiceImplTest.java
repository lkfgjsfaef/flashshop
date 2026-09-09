package org.javaup.service.impl;

import org.javaup.mapper.VoucherOrderMapper;

import cn.hutool.core.util.StrUtil;
import org.javaup.dto.CancelVoucherOrderDto;
import org.javaup.dto.GetVoucherOrderDto;
import org.javaup.dto.Result;
import org.javaup.entity.SeckillVoucher;
import org.javaup.entity.UserInfo;
import org.javaup.entity.VoucherOrder;
import org.javaup.enums.BaseCode;
import org.javaup.enums.OrderStatus;
import org.javaup.exception.HmdpFrameException;
import org.javaup.kafka.producer.SeckillVoucherProducer;
import org.javaup.kafka.redis.RedisVoucherData;
import org.javaup.lua.SeckillVoucherDomain;
import org.javaup.lua.SeckillVoucherOperate;
import org.javaup.model.SeckillVoucherFullModel;
import org.javaup.redis.RedisCacheImpl;
import org.javaup.service.*;
import org.javaup.test.BaseUnitTest;
import org.javaup.test.ChainWrapperMocks;
import org.javaup.test.TestDataFactory;
import org.javaup.toolkit.SnowflakeIdGenerator;
import org.javaup.utils.RedisIdWorker;
import org.javaup.utils.UserHolder;
import org.junit.jupiter.api.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * VoucherOrderServiceImpl 单元测试
 * 覆盖场景：秒杀下单（成功、库存不足、重复下单、用户等级校验）、取消订单、查询订单
 */
class VoucherOrderServiceImplTest extends BaseUnitTest {
    // ServiceImpl 的 baseMapper：不注入的话，走到 getById/list/save 等
    // 真实 DAO 方法时会抛 "baseMapper can not be null"
    @Mock
    private VoucherOrderMapper voucherOrderMapper;


    @Spy
    @InjectMocks
    private VoucherOrderServiceImpl voucherOrderService;

    @Mock
    private IVoucherService voucherService;

    @Mock
    private ISeckillVoucherService seckillVoucherService;

    @Mock
    private RedisIdWorker redisIdWorker;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private SeckillVoucherOperate seckillVoucherOperate;

    @Mock
    private SeckillVoucherProducer seckillVoucherProducer;

    @Mock
    private RedisCacheImpl redisCache;

    @Mock
    private IVoucherOrderRouterService voucherOrderRouterService;

    @Mock
    private IUserInfoService userInfoService;

    @Mock
    private IVoucherReconcileLogService voucherReconcileLogService;

    @Mock
    private RedisVoucherData redisVoucherData;

    @Mock
    private RLock rLock;

    private Long currentUserId = 1L;
    private Long voucherId = 100L;
    private Long orderId = 1000L;
    private Long traceId = 2000L;

    @BeforeEach
    void setUp() {
        UserHolder.saveUser(TestDataFactory.createUserDTO(currentUserId));
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    // ==================== seckillVoucher 测试 ====================

    @Nested
    @DisplayName("seckillVoucher - 秒杀下单")
    class SeckillVoucherTests {

        @Test
        @DisplayName("秒杀成功 → 返回订单ID")
        void should_returnOrderId_when_seckillSuccess() {
            // given
            SeckillVoucherFullModel model = TestDataFactory.createSeckillVoucherFullModel(voucherId);
            when(seckillVoucherService.queryByVoucherId(voucherId)).thenReturn(model);
            when(snowflakeIdGenerator.nextId()).thenReturn(orderId).thenReturn(traceId);

            SeckillVoucherDomain successDomain = new SeckillVoucherDomain();
            // 注意：code 是 Integer，且必须用 BaseCode 里的真实业务码。
            // 早前这里写的是字符串 "0"/"1"/"2"，既编译不过，码值也不是真实业务码。
            successDomain.setCode(BaseCode.SUCCESS.getCode()); // 成功
            successDomain.setBeforeQty(100);
            successDomain.setDeductQty(1);
            successDomain.setAfterQty(99);
            when(seckillVoucherOperate.execute(anyList(), any(String[].class))).thenReturn(successDomain);

            // when
            Result<Long> result = voucherOrderService.seckillVoucher(voucherId);

            // then
            assertThat(result.getData()).isEqualTo(orderId);
            verify(seckillVoucherProducer).sendPayload(anyString(), any());
        }

        @Test
        @DisplayName("库存不足 → 抛出异常")
        void should_throw_when_stockInsufficient() {
            // given
            SeckillVoucherFullModel model = TestDataFactory.createSeckillVoucherFullModel(voucherId);
            when(seckillVoucherService.queryByVoucherId(voucherId)).thenReturn(model);
            when(snowflakeIdGenerator.nextId()).thenReturn(orderId).thenReturn(traceId);

            SeckillVoucherDomain failDomain = new SeckillVoucherDomain();
            failDomain.setCode(BaseCode.SECKILL_VOUCHER_STOCK_INSUFFICIENT.getCode()); // 库存不足
            when(seckillVoucherOperate.execute(anyList(), any(String[].class))).thenReturn(failDomain);

            // when & then
            assertThatThrownBy(() -> voucherOrderService.seckillVoucher(voucherId))
                    .isInstanceOf(HmdpFrameException.class);
        }

        @Test
        @DisplayName("重复下单 → 抛出异常")
        void should_throw_when_duplicateOrder() {
            // given
            SeckillVoucherFullModel model = TestDataFactory.createSeckillVoucherFullModel(voucherId);
            when(seckillVoucherService.queryByVoucherId(voucherId)).thenReturn(model);
            when(snowflakeIdGenerator.nextId()).thenReturn(orderId).thenReturn(traceId);

            SeckillVoucherDomain failDomain = new SeckillVoucherDomain();
            failDomain.setCode(BaseCode.VOUCHER_ORDER_EXIST.getCode()); // 重复下单（订单已存在）
            when(seckillVoucherOperate.execute(anyList(), any(String[].class))).thenReturn(failDomain);

            // when & then
            assertThatThrownBy(() -> voucherOrderService.seckillVoucher(voucherId))
                    .isInstanceOf(HmdpFrameException.class);
        }

        @Test
        @DisplayName("用户等级不满足 → 抛出异常")
        void should_throw_when_userLevelNotAllowed() {
            // given
            SeckillVoucherFullModel model = TestDataFactory.createSeckillVoucherFullModel(voucherId);
            model.setMinLevel(5); // 要求5级
            when(seckillVoucherService.queryByVoucherId(voucherId)).thenReturn(model);

            UserInfo userInfo = TestDataFactory.createUserInfoWithLevel(currentUserId, 1); // 用户只有1级
            when(userInfoService.getByUserId(currentUserId)).thenReturn(userInfo);

            // when & then
            assertThatThrownBy(() -> voucherOrderService.seckillVoucher(voucherId))
                    .isInstanceOf(HmdpFrameException.class)
                    .hasMessageContaining("当前会员级别不满足参与条件");
        }
    }

    // ==================== cancel 测试 ====================

    @Nested
        @DisplayName("cancel - 取消订单")
        class CancelTests {

            @Test
            @DisplayName("正常取消 → 回滚库存并记录日志")
            void should_rollbackStockAndLog_when_cancelSuccess() {
                // given
                CancelVoucherOrderDto dto = TestDataFactory.createCancelVoucherOrderDto(voucherId);
                VoucherOrder existingOrder = TestDataFactory.createVoucherOrder(currentUserId, voucherId);
                SeckillVoucher seckillVoucher = TestDataFactory.createSeckillVoucher(voucherId);

                doReturn(ChainWrapperMocks.lambdaQueryChain(existingOrder)).when(voucherOrderService).lambdaQuery();
                when(seckillVoucherService.lambdaQuery()).thenReturn(ChainWrapperMocks.lambdaQueryChain(seckillVoucher));
                doReturn(ChainWrapperMocks.lambdaUpdateChain(true)).when(voucherOrderService).lambdaUpdate();
                when(snowflakeIdGenerator.nextId()).thenReturn(traceId);
                when(voucherReconcileLogService.saveReconcileLog(any())).thenReturn(true);
                when(seckillVoucherService.rollbackStock(voucherId)).thenReturn(true);
                // rollbackRedisVoucherData 返回 void —— Mockito 默认就是 doNothing，
                // 且 void 方法不能用 when(...).thenReturn(...)，这里不需要额外 stub
                doNothing().when(redisVoucherData)
                        .rollbackRedisVoucherData(any(), any(), any(), any(), any(), any(), any(), any());

                // when
                // cancel 返回的是 Boolean，不是 Result<Boolean>（失败时抛异常）
                Boolean result = voucherOrderService.cancel(dto);

                // then
                assertThat(result).isEqualTo(true);
                verify(seckillVoucherService).rollbackStock(voucherId);
                verify(voucherReconcileLogService).saveReconcileLog(any());
            }

            @Test
            @DisplayName("订单不存在 → 抛出异常")
            void should_throw_when_orderNotFound() {
                // given
                CancelVoucherOrderDto dto = TestDataFactory.createCancelVoucherOrderDto(voucherId);
                doReturn(ChainWrapperMocks.lambdaQueryChain(null)).when(voucherOrderService).lambdaQuery();

                // when & then
                assertThatThrownBy(() -> voucherOrderService.cancel(dto))
                        .isInstanceOf(HmdpFrameException.class);
            }

            @Test
            @DisplayName("秒杀优惠券不存在 → 抛出异常")
            void should_throw_when_seckillVoucherNotFound() {
                // given
                CancelVoucherOrderDto dto = TestDataFactory.createCancelVoucherOrderDto(voucherId);
                VoucherOrder existingOrder = TestDataFactory.createVoucherOrder(currentUserId, voucherId);
                doReturn(ChainWrapperMocks.lambdaQueryChain(existingOrder)).when(voucherOrderService).lambdaQuery();
                when(seckillVoucherService.lambdaQuery()).thenReturn(ChainWrapperMocks.lambdaQueryChain(null));

                // when & then
                assertThatThrownBy(() -> voucherOrderService.cancel(dto))
                        .isInstanceOf(HmdpFrameException.class);
            }
        }

    // ==================== getSeckillVoucherOrder 测试 ====================

    @Nested
        @DisplayName("getSeckillVoucherOrder - 查询订单")
        class GetSeckillVoucherOrderTests {

            @Test
            @DisplayName("Redis缓存命中 → 返回订单ID")
            void should_returnOrderId_when_redisCacheHit() {
                // given
                GetVoucherOrderDto dto = TestDataFactory.createGetVoucherOrderDto(orderId);
                VoucherOrder cachedOrder = TestDataFactory.createVoucherOrder(currentUserId, voucherId);
                cachedOrder.setId(orderId);
                when(redisCache.get(any(), eq(VoucherOrder.class))).thenReturn(cachedOrder);

                // when
                Long result = voucherOrderService.getSeckillVoucherOrder(dto);

                // then
                assertThat(result).isEqualTo(orderId);
            }

            @Test
            @DisplayName("Redis缓存未命中，DB命中 → 返回订单ID")
            void should_returnOrderId_when_dbHit() {
                // given
                GetVoucherOrderDto dto = TestDataFactory.createGetVoucherOrderDto(orderId);
                when(redisCache.get(any(), eq(VoucherOrder.class))).thenReturn(null);
                // 注意：生产代码里 .one() 拿到的是 VoucherOrderRouter，再调 getOrderId()；
                // 所以这里必须 stub 一个真实的 VoucherOrderRouter 对象，而不是裸的 orderId（Long），
                // 否则 one() 返回 Long 后 .getOrderId() 会 ClassCastException
                when(voucherOrderRouterService.lambdaQuery()).thenReturn(
                        ChainWrapperMocks.lambdaQueryChain(
                                TestDataFactory.createVoucherOrderRouter(1L, 1L, orderId)));

                // when
                Long result = voucherOrderService.getSeckillVoucherOrder(dto);

                // then
                assertThat(result).isEqualTo(orderId);
            }

            @Test
            @DisplayName("Redis和DB都未命中 → 返回null")
            void should_returnNull_when_notFoundAnywhere() {
                // given
                GetVoucherOrderDto dto = TestDataFactory.createGetVoucherOrderDto(orderId);
                when(redisCache.get(any(), eq(VoucherOrder.class))).thenReturn(null);
                when(voucherOrderRouterService.lambdaQuery()).thenReturn(ChainWrapperMocks.lambdaQueryChain(null));

                // when
                Long result = voucherOrderService.getSeckillVoucherOrder(dto);

                // then
                assertThat(result).isNull();
            }
        }

    // ==================== Mock辅助类 ====================

    /**
     * MyBatis-Plus LambdaQueryWrapper 的简单Mock
     */
    /**
     * MyBatis-Plus LambdaUpdateWrapper 的简单Mock
     */
}