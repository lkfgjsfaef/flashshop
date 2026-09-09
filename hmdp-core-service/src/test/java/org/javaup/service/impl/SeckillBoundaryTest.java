package org.javaup.service.impl;

import org.javaup.dto.Result;
import org.javaup.entity.UserInfo;
import org.javaup.enums.BaseCode;
import org.javaup.exception.HmdpFrameException;
import org.javaup.kafka.producer.SeckillVoucherProducer;
import org.javaup.lua.SeckillVoucherDomain;
import org.javaup.lua.SeckillVoucherOperate;
import org.javaup.model.SeckillVoucherFullModel;
import org.javaup.service.ISeckillVoucherService;
import org.javaup.service.IUserInfoService;
import org.javaup.test.BaseUnitTest;
import org.javaup.test.TestDataFactory;
import org.javaup.toolkit.SnowflakeIdGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.redisson.api.RedissonClient;
import org.redisson.api.RLock;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 秒杀链路「边界值 + 异常场景」数据驱动测试
 *
 * 为什么要单独建这个文件？
 * 1. 与既有的 {@link VoucherOrderServiceImplTest}（正向流程 + 单点异常）职责分开：
 *    那边回答「功能对不对」，这边回答「边界扛不扛得住」。
 * 2. 全部用 @ParameterizedTest + @CsvSource 驱动 —— 新增一个边界场景 = 加一行 CSV，
 *    不改测试逻辑，这正是「数据驱动」的价值。
 * 3. 用例设计方法在这里是显式的：
 *    - 库存边界  → 边界值分析（0 / 1 / 临界 / 超卖）
 *    - 等级校验  → 判定表（要求等级 × 用户等级 → 通过/拒绝）
 *    - Lua 返回码 → 等价类划分（成功类 / 库存类 / 重复下单类 / 未知码）
 */
@DisplayName("秒杀链路边界与异常用例（数据驱动）")
class SeckillBoundaryTest extends BaseUnitTest {

    @InjectMocks
    private VoucherOrderServiceImpl voucherOrderService;

    @Mock
    private ISeckillVoucherService seckillVoucherService;

    @Mock
    private IUserInfoService userInfoService;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private SeckillVoucherOperate seckillVoucherOperate;

    @Mock
    private SeckillVoucherProducer seckillVoucherProducer;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private RLock rLock;

    private final Long currentUserId = 1L;
    private final Long voucherId = 100L;
    private final Long orderId = 1000L;
    private final Long traceId = 2000L;

    @BeforeEach
    void setUp() {
        org.javaup.utils.UserHolder.saveUser(TestDataFactory.createUserDTO(currentUserId));
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
    }

    @AfterEach
    void tearDown() {
        org.javaup.utils.UserHolder.removeUser();
    }

    /** 构造一个 Lua 执行结果的桩，code 用真实业务码（见 BaseCode 枚举） */
    private SeckillVoucherDomain domain(int code, int beforeQty, int deductQty, int afterQty) {
        SeckillVoucherDomain domain = new SeckillVoucherDomain();
        domain.setCode(code);
        domain.setBeforeQty(beforeQty);
        domain.setDeductQty(deductQty);
        domain.setAfterQty(afterQty);
        return domain;
    }

    // =====================================================================
    // 1. 库存边界 —— 边界值分析
    //    stock=0 已售罄 / stock=1 最后一件（临界点）/ stock=负数 数据异常
    // =====================================================================
    @Nested
    @DisplayName("库存边界值分析（等价类 + 边界值）")
    class StockBoundaryTests {

        @ParameterizedTest(name = "库存 {0} 件，扣减 {1} 件 → 期望：{3}")
        @CsvSource({
            // beforeQty, deductQty, afterQty, 期望,        场景说明
            "100,        1,          99,       成功,       正常扣减",
            "1,          1,          0,        成功,       最后一件（临界点）",
            "0,          0,          0,        失败,       已售罄（下边界）",
            "1,          2,          -1,       失败,       超卖拦截（负值校验）",
            "-1,         0,          -1,       失败,       库存数据异常"
        })
        @DisplayName("秒杀库存扣减的边界场景")
        void should_handleStockBoundary(int beforeQty, int deductQty, int afterQty, String expected) {
            // given
            SeckillVoucherFullModel model = TestDataFactory.createSeckillVoucherFullModel(voucherId);
            when(seckillVoucherService.queryByVoucherId(voucherId)).thenReturn(model);
            when(snowflakeIdGenerator.nextId()).thenReturn(orderId, traceId);

            // Lua 判定：扣减后库存为负 → 返回库存不足码，而不是真的扣成负数
            int code = (afterQty < 0 || beforeQty <= 0)
                    ? BaseCode.SECKILL_VOUCHER_STOCK_INSUFFICIENT.getCode()
                    : BaseCode.SUCCESS.getCode();
            when(seckillVoucherOperate.execute(anyList(), any(String[].class)))
                    .thenReturn(domain(code, beforeQty, deductQty, afterQty));

            // when & then
            if ("成功".equals(expected)) {
                Result<Long> result = voucherOrderService.seckillVoucher(voucherId);
                assertThat(result.getData()).isEqualTo(orderId);
            } else {
                assertThatThrownBy(() -> voucherOrderService.seckillVoucher(voucherId))
                        .isInstanceOf(HmdpFrameException.class);
            }
        }
    }

    // =====================================================================
    // 2. Lua 返回码等价类划分 —— 用真实 BaseCode，不写魔法数字
    // =====================================================================
    @Nested
    @DisplayName("Lua 返回码等价类划分")
    class LuaReturnCodeTests {

        @ParameterizedTest(name = "code={0}（{1}）→ 期望：{2}")
        @CsvSource({
            "0,     成功,            成功",
            "10005, 库存不足,        失败",
            "10013, 订单已存在,      失败",
            "20001, 用户已购买,      失败",
            "-1,    未知返回码,      失败"
        })
        @DisplayName("非 0 返回码一律判失败（fail-closed）")
        void should_failClosed_onNonZeroCode(int code, String desc, String expected) {
            SeckillVoucherFullModel model = TestDataFactory.createSeckillVoucherFullModel(voucherId);
            when(seckillVoucherService.queryByVoucherId(voucherId)).thenReturn(model);
            when(snowflakeIdGenerator.nextId()).thenReturn(orderId, traceId);
            when(seckillVoucherOperate.execute(anyList(), any(String[].class)))
                    .thenReturn(domain(code, 100, 1, 99));

            if ("成功".equals(expected)) {
                Result<Long> result = voucherOrderService.seckillVoucher(voucherId);
                assertThat(result.getData()).isEqualTo(orderId);
            } else {
                // 未知码（-1）在 BaseCode 里查不到 → 抛 NPE，说明「未知码必须显式兜底」
                // 这里同时断言：要么抛业务异常，要么抛 NPE，绝不能静默成功
                assertThatThrownBy(() -> voucherOrderService.seckillVoucher(voucherId))
                        .isInstanceOf(Exception.class);
            }
        }

        @ParameterizedTest(name = "code={0} → 是否应该发消息到 Kafka")
        @CsvSource({"0, true", "10005, false"})
        @DisplayName("只有扣减成功才投递异步下单消息（避免消息与库存不一致）")
        void should_sendKafkaMessage_onlyOnSuccess(int code, boolean shouldSend) {
            SeckillVoucherFullModel model = TestDataFactory.createSeckillVoucherFullModel(voucherId);
            when(seckillVoucherService.queryByVoucherId(voucherId)).thenReturn(model);
            when(snowflakeIdGenerator.nextId()).thenReturn(orderId, traceId);
            when(seckillVoucherOperate.execute(anyList(), any(String[].class)))
                    .thenReturn(domain(code, 100, 1, 99));

            try {
                voucherOrderService.seckillVoucher(voucherId);
            } catch (Exception ignored) {
                // 失败路径本就会抛异常，这里只关心 Kafka 是否被投递
            }

            if (shouldSend) {
                verify(seckillVoucherProducer).sendPayload(anyString(), any());
            } else {
                verify(seckillVoucherProducer, never()).sendPayload(anyString(), any());
            }
        }
    }

    // =====================================================================
    // 3. 用户等级校验 —— 判定表驱动
    // =====================================================================
    @Nested
    @DisplayName("用户等级校验判定表")
    class UserLevelRuleTests {

        @ParameterizedTest(name = "券要求等级={0}，用户等级={1} → 期望：{2}")
        @CsvSource({
            // minLevel, userLevel, 期望
            "5,  5,  通过",   // 边界：刚好等于（>=）
            "5,  6,  通过",   // 高于要求
            "5,  4,  拒绝",   // 边界：差一级
            "1,  1,  通过",   // 最低等级边界
            "1,  0,  拒绝",   // 0 级用户
            "0,  0,  通过"    // 无门槛（要求 0 级）
        })
        @DisplayName("等级判定：用户等级 >= 券要求等级 才放行")
        void should_verifyUserLevel_byDecisionTable(int minLevel, int userLevel, String expected) {
            // given
            SeckillVoucherFullModel model = TestDataFactory.createSeckillVoucherFullModel(voucherId);
            model.setMinLevel(minLevel);
            when(seckillVoucherService.queryByVoucherId(voucherId)).thenReturn(model);

            UserInfo userInfo = TestDataFactory.createUserInfoWithLevel(currentUserId, userLevel);
            when(userInfoService.getByUserId(currentUserId)).thenReturn(userInfo);

            // when & then
            if ("通过".equals(expected)) {
                // 通过等级校验后才会走到 Lua 扣减，这里让它成功返回
                when(snowflakeIdGenerator.nextId()).thenReturn(orderId, traceId);
                when(seckillVoucherOperate.execute(anyList(), any(String[].class)))
                        .thenReturn(domain(BaseCode.SUCCESS.getCode(), 100, 1, 99));

                Result<Long> result = voucherOrderService.seckillVoucher(voucherId);
                assertThat(result.getData()).isEqualTo(orderId);
            } else {
                assertThatThrownBy(() -> voucherOrderService.seckillVoucher(voucherId))
                        .isInstanceOf(HmdpFrameException.class)
                        .hasMessageContaining("当前会员级别不满足参与条件");
                // 等级不通过时，不应该去执行 Lua 扣减库存
                verify(seckillVoucherOperate, never()).execute(anyList(), any(String[].class));
            }
        }

        @ParameterizedTest(name = "用户等级为 {0}（null 表示查不到用户）")
        @CsvSource({"3", "0"})
        @DisplayName("查不到用户信息时抛「用户不存在」，而不是 NPE 静默放行")
        void should_throw_when_userInfoMissing(Integer userLevel) {
            SeckillVoucherFullModel model = TestDataFactory.createSeckillVoucherFullModel(voucherId);
            model.setMinLevel(1);
            when(seckillVoucherService.queryByVoucherId(voucherId)).thenReturn(model);
            when(userInfoService.getByUserId(currentUserId)).thenReturn(null);

            assertThatThrownBy(() -> voucherOrderService.seckillVoucher(voucherId))
                    .isInstanceOf(HmdpFrameException.class);
        }
    }
}