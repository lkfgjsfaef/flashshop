package org.javaup.controller;

import org.springframework.context.annotation.Import;

import org.javaup.dto.Result;
import org.javaup.enums.BaseCode;
import org.javaup.exception.HmdpFrameException;
import org.javaup.execute.RateLimitHandler;
import org.javaup.ratelimit.extension.RateLimitScene;
import org.javaup.service.IReconciliationTaskService;
import org.javaup.service.ISeckillAccessTokenService;
import org.javaup.service.IVoucherOrderService;
import org.javaup.test.TestDataFactory;
import org.javaup.utils.UserHolder;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * VoucherOrderController 切片测试
 */
@WebMvcTest(VoucherOrderController.class)
@Import(org.javaup.test.MockMapperConfig.class)
class VoucherOrderControllerTest extends BaseWebMvcTest {


    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IVoucherOrderService voucherOrderService;

    @MockBean
    private ISeckillAccessTokenService accessTokenService;

    @MockBean
    private RateLimitHandler rateLimitHandler;

    @MockBean
    private IReconciliationTaskService reconciliationTaskService;

    private Long voucherId = 100L;
    private Long orderId = 1000L;
    private Long currentUserId = 1L;

    @BeforeEach
    void setUp() {
        UserHolder.saveUser(TestDataFactory.createUserDTO(currentUserId));
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    // ==================== GET /voucher-order/seckill/token/{id} 测试 ====================

    @Nested
    @DisplayName("GET /voucher-order/seckill/token/{id} - 获取秒杀令牌")
    class IssueSeckillAccessTokenTests {

        @Test
        @DisplayName("正常获取 → 返回200和令牌")
        void should_return200_when_issueTokenSuccess() throws Exception {
            // given
            String token = "test-token-123";
            when(accessTokenService.issueAccessToken(voucherId, currentUserId)).thenReturn(token);

            // when & then
            mockMvc.perform(get("/voucher-order/seckill/token/{id}", voucherId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(token));
        }
    }

    // ==================== POST /voucher-order/seckill/{id} 测试 ====================

    @Nested
    @DisplayName("POST /voucher-order/seckill/{id} - 秒杀下单")
    class SeckillVoucherTests {

        @Test
        @DisplayName("秒杀成功 → 返回200和订单ID")
        void should_return200_when_seckillSuccess() throws Exception {
            // given
            when(voucherOrderService.seckillVoucher(voucherId)).thenReturn(Result.ok(orderId));

            // when & then
            mockMvc.perform(post("/voucher-order/seckill/{id}", voucherId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(orderId));
        }

        @Test
        @DisplayName("秒杀失败 → 返回200但ok为false")
        void should_return200WithFalse_when_seckillFails() throws Exception {
            // given
            when(voucherOrderService.seckillVoucher(voucherId)).thenReturn(Result.fail("库存不足"));

            // when & then
            mockMvc.perform(post("/voucher-order/seckill/{id}", voucherId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errorMsg").value("库存不足"));
        }

        @Test
        @DisplayName("令牌校验失败 → 返回200但ok为false")
        void should_return200WithFalse_when_tokenValidationFails() throws Exception {
            // given
            when(accessTokenService.isEnabled()).thenReturn(true);
            when(accessTokenService.validateAndConsume(voucherId, currentUserId, "invalid-token")).thenReturn(false);

            // when & then
            mockMvc.perform(post("/voucher-order/seckill/{id}", voucherId)
                            .param("accessToken", "invalid-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errorMsg").value("令牌校验失败或令牌已失效"));
        }
    }

    // ==================== POST /voucher-order/cancel 测试 ====================

    @Nested
    @DisplayName("POST /voucher-order/cancel - 取消订单")
    class CancelTests {

        @Test
        @DisplayName("取消成功 → 返回200")
        void should_return200_when_cancelSuccess() throws Exception {
            // given
            // cancel 的真实签名是 Boolean cancel(CancelVoucherOrderDto)，不是 Result<Boolean>
            when(voucherOrderService.cancel(any())).thenReturn(true);

            // when & then
            mockMvc.perform(post("/voucher-order/cancel")
                            .contentType("application/json")
                            .content("{\"voucherId\": 100}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(true));
        }

        @Test
        @DisplayName("订单不存在 → 抛业务异常，由全局异常处理器转成失败响应")
        void should_returnFailure_when_orderNotExist() throws Exception {
            // given：cancel 失败时抛异常，而不是返回 false
            when(voucherOrderService.cancel(any()))
                    .thenThrow(new HmdpFrameException(BaseCode.SECKILL_VOUCHER_ORDER_NOT_EXIST));

            // when & then
            mockMvc.perform(post("/voucher-order/cancel")
                            .contentType("application/json")
                            .content("{\"voucherId\": 100}"))
                    .andExpect(status().isOk())
                    // Result 里的字段名是 success / errorMsg，没有 ok
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ==================== POST /voucher-order/reconciliation/task/all 测试 ====================

    @Nested
    @DisplayName("POST /voucher-order/reconciliation/task/all - 执行对账任务")
    class ReconciliationTaskAllTests {

        @Test
        @DisplayName("执行成功 → 返回200")
        void should_return200_when_executeSuccess() throws Exception {
            // when & then
            mockMvc.perform(post("/voucher-order/reconciliation/task/all"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }
}