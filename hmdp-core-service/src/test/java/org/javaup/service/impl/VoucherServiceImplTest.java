package org.javaup.service.impl;

import org.javaup.mapper.VoucherMapper;

import org.javaup.dto.Result;
import org.javaup.dto.VoucherDto;
import org.javaup.entity.Voucher;
import org.javaup.service.ISeckillVoucherService;
import org.javaup.service.IVoucherService;
import org.javaup.test.BaseUnitTest;
import org.javaup.test.ChainWrapperMocks;
import org.javaup.test.TestDataFactory;
import org.javaup.toolkit.SnowflakeIdGenerator;
import org.junit.jupiter.api.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * VoucherServiceImpl 单元测试
 * 覆盖场景：添加优惠券、查询优惠券列表
 */
class VoucherServiceImplTest extends BaseUnitTest {

    /**
     * 布隆过滤器默认桩。
     *
     * BloomFilterHandlerFactory 是 @Mock，get() 默认返回 null，
     * 生产代码拿到后直接调 contains()/add() 就 NPE。
     * 这里给一个默认实现；需要特定行为（比如 contains 返回 false）的用例，
     * 在自己内部再 stub 一次就会覆盖掉这里。
     */
    @BeforeEach
    void setUpBloomFilter() {
        org.javaup.handler.BloomFilterHandler handler =
                mock(org.javaup.handler.BloomFilterHandler.class);
        when(handler.contains(anyString())).thenReturn(true);
        when(handler.add(anyString())).thenReturn(true);
        when(bloomFilterHandlerFactory.get(anyString())).thenReturn(handler);
    }

    // ServiceImpl 的 baseMapper：不注入的话，走到 getById/list/save 等
    // 真实 DAO 方法时会抛 "baseMapper can not be null"
    @Mock
    private VoucherMapper voucherMapper;


    @Spy
    @InjectMocks
    private VoucherServiceImpl voucherService;

    @Mock
    private ISeckillVoucherService seckillVoucherService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private org.javaup.handler.BloomFilterHandlerFactory bloomFilterHandlerFactory;

    @Mock
    private org.javaup.redis.RedisCache redisCache;

    @Mock
    private org.javaup.cache.SeckillVoucherCacheInvalidationPublisher seckillVoucherCacheInvalidationPublisher;

    @Mock
    private org.javaup.service.IVoucherOrderService voucherOrderService;

    @Mock
    private org.javaup.context.DelayQueueContext delayQueueContext;

    private Long shopId = 1L;
    private Long voucherId = 100L;

    @BeforeEach
    void setUp() {
        // MyBatis-Plus ServiceImpl 的 baseMapper 是泛型字段 M，擦除后类型为 Object，
        // Mockito 的 @InjectMocks 按类型注入匹配不上，运行时 getBaseMapper() 会抛
        // "baseMapper can not be null"。这里反射把 Mapper mock 塞进父类字段，
        // 让 queryVoucherOfShop 这类走 getBaseMapper().xxx() 的真实方法能跑通。
        org.springframework.test.util.ReflectionTestUtils.setField(voucherService, "baseMapper", voucherMapper);
    }

    // ==================== addVoucher 测试 ====================

    @Nested
    @DisplayName("addVoucher - 添加优惠券")
    class AddVoucherTests {

        @Test
        @DisplayName("正常添加 → 返回优惠券ID")
        void should_returnVoucherId_when_addSuccess() {
            // given
            VoucherDto voucherDto = new VoucherDto();
            voucherDto.setShopId(shopId);
            voucherDto.setTitle("测试优惠券");
            voucherDto.setSubTitle("满100减10");
            voucherDto.setRules("全场通用");
            voucherDto.setPayValue(9000L);
            voucherDto.setActualValue(10000L);
            voucherDto.setType(1);

            doReturn(ChainWrapperMocks.lambdaQueryChain(null)).when(voucherService).lambdaQuery();
            doReturn(true).when(voucherService).save(any(Voucher.class));

            // when
            Long result = voucherService.addVoucher(voucherDto);

            // then
            assertThat(result).isEqualTo(1L);
            verify(voucherService).save(any(Voucher.class));
        }

        @Test
        @DisplayName("已有优惠券 → ID自增")
        void should_incrementId_when_voucherExists() {
            // given
            VoucherDto voucherDto = new VoucherDto();
            voucherDto.setShopId(shopId);
            voucherDto.setTitle("测试优惠券");

            Voucher existingVoucher = TestDataFactory.createVoucher(50L);
            doReturn(ChainWrapperMocks.lambdaQueryChain(existingVoucher)).when(voucherService).lambdaQuery();
            doReturn(true).when(voucherService).save(any(Voucher.class));

            // when
            Long result = voucherService.addVoucher(voucherDto);

            // then
            assertThat(result).isEqualTo(51L);
        }
    }

    // ==================== queryVoucherOfShop 测试 ====================

    @Nested
    @DisplayName("queryVoucherOfShop - 查询商铺优惠券")
    class QueryVoucherOfShopTests {

        @Test
        @DisplayName("有优惠券 → 返回列表")
        void should_returnList_when_hasVouchers() {
            // given
            // 生产代码是 getBaseMapper().queryVoucherOfShop(shopId) —— 自定义 Mapper 方法，
            // 不是 Service 的 list()，所以要 stub 的是 Mapper 而不是 Service
            when(voucherMapper.queryVoucherOfShop(shopId)).thenReturn(java.util.List.of(
                    TestDataFactory.createVoucher(1L),
                    TestDataFactory.createVoucher(2L)
            ));

            // when
            Result result = voucherService.queryVoucherOfShop(shopId);

            // then
            assertThat(result.getData()).isNotNull();
            java.util.List<Voucher> vouchers = (java.util.List<Voucher>) result.getData();
            assertThat(vouchers).hasSize(2);
        }

        @Test
        @DisplayName("无优惠券 → 返回空列表")
        void should_returnEmptyList_when_noVouchers() {
            // given
            when(voucherMapper.queryVoucherOfShop(shopId)).thenReturn(java.util.Collections.emptyList());

            // when
            Result result = voucherService.queryVoucherOfShop(shopId);

            // then
            assertThat(result.getData()).isEqualTo(java.util.Collections.emptyList());
        }
    }

    // ==================== Mock辅助类 ====================

    /**
     * MyBatis-Plus LambdaQueryWrapper 的简单Mock
     */
}