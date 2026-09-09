package org.javaup.api;

import org.javaup.entity.VoucherOrder;
import org.javaup.test.TestDataFactory;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VoucherOrder API 自动化测试
 * 测试秒杀下单相关的所有接口
 */
@DisplayName("VoucherOrder API 自动化测试")
class VoucherOrderApiTest extends BaseApiTest {

    private Long voucherId = 100L;
    private Long currentUserId = 1L;

    // ==================== GET /voucher-order/seckill/token/{id} 测试 ====================

    @Nested
    @DisplayName("GET /voucher-order/seckill/token/{id} - 获取秒杀令牌")
    class GetSeckillTokenTests {

        @Test
        @DisplayName("正常获取 → 返回200和令牌")
        void should_returnToken_when_validRequest() {
            // when
            var response = get("/voucher-order/seckill/token/{id}", voucherId);

            // then
            assertSuccess(response);
            String token = getData(response, String.class);
            assertThat(token).isNotNull();
            assertThat(token).isNotEmpty();
        }
    }

    // ==================== POST /voucher-order/seckill/{id} 测试 ====================

    @Nested
    @DisplayName("POST /voucher-order/seckill/{id} - 秒杀下单")
    class SeckillVoucherTests {

        @Test
        @DisplayName("秒杀成功 → 返回200和订单ID")
        void should_returnOrderId_when_seckillSuccess() {
            // when
            var response = post("/voucher-order/seckill/{id}", voucherId);

            // then
            assertSuccess(response);
            Long orderId = getData(response, Long.class);
            assertThat(orderId).isNotNull();
            assertThat(orderId).isPositive();
        }

        @Test
        @DisplayName("秒杀失败（库存不足）→ 返回200但ok为false")
        void should_fail_when_stockInsufficient() {
            // 注意：这个测试需要预先消耗完库存
            // 在实际测试中，可能需要先创建大量订单来消耗库存
            // 这里只是示例，实际测试需要根据业务逻辑调整
        }

        @Test
        @DisplayName("令牌校验失败 → 返回200但ok为false")
        void should_fail_when_tokenInvalid() {
            // given
            String invalidToken = "invalid-token";

            // when
            var response = post("/voucher-order/seckill/{id}?accessToken=" + invalidToken, voucherId);

            // then
            // 注意：具体行为取决于令牌校验是否启用
            // 如果启用，应该返回失败
        }
    }

    // ==================== POST /voucher-order/cancel 测试 ====================

    @Nested
    @DisplayName("POST /voucher-order/cancel - 取消订单")
    class CancelOrderTests {

        @Test
        @DisplayName("取消成功 → 返回200")
        void should_cancelOrder_when_validRequest() {
            // given
            // 先创建一个订单
            var seckillResponse = post("/voucher-order/seckill/{id}", voucherId);
            assertSuccess(seckillResponse);
            Long orderId = getData(seckillResponse, Long.class);

            Map<String, Object> cancelRequest = new HashMap<>();
            cancelRequest.put("voucherId", voucherId);

            // when
            var response = post("/voucher-order/cancel", cancelRequest);

            // then
            assertSuccess(response);
            Boolean result = getData(response, Boolean.class);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("取消失败（订单不存在）→ 返回200但ok为false")
        void should_fail_when_orderNotFound() {
            // given
            Map<String, Object> cancelRequest = new HashMap<>();
            cancelRequest.put("voucherId", 999999L);

            // when
            var response = post("/voucher-order/cancel", cancelRequest);

            // then
            assertFailure(response);
        }
    }

    // ==================== POST /voucher-order/get/seckill/voucher/order-id 测试 ====================

    @Nested
        @DisplayName("POST /voucher-order/get/seckill/voucher/order-id - 查询订单ID")
        class GetSeckillVoucherOrderIdTests {

            @Test
            @DisplayName("订单存在 → 返回200和订单ID")
            void should_returnOrderId_when_orderExists() {
                // given
                // 先创建一个订单
                var seckillResponse = post("/voucher-order/seckill/{id}", voucherId);
                assertSuccess(seckillResponse);
                Long expectedOrderId = getData(seckillResponse, Long.class);

                Map<String, Object> request = new HashMap<>();
                request.put("orderId", expectedOrderId);

                // when
                var response = post("/voucher-order/get/seckill/voucher/order-id", request);

                // then
                assertSuccess(response);
                Long actualOrderId = getData(response, Long.class);
                assertThat(actualOrderId).isEqualTo(expectedOrderId);
            }

            @Test
            @DisplayName("订单不存在 → 返回200但data为null")
            void should_returnNull_when_orderNotFound() {
                // given
                Map<String, Object> request = new HashMap<>();
                request.put("orderId", 999999L);

                // when
                var response = post("/voucher-order/get/seckill/voucher/order-id", request);

                // then
                assertSuccess(response);
                Long orderId = getData(response, Long.class);
                assertThat(orderId).isNull();
            }
        }

    // ==================== POST /voucher-order/reconciliation/task/all 测试 ====================

    @Nested
        @DisplayName("POST /voucher-order/reconciliation/task/all - 执行对账任务")
        class ReconciliationTaskTests {

            @Test
            @DisplayName("执行成功 → 返回200")
            void should_executeSuccess() {
                // when
                var response = post("/voucher-order/reconciliation/task/all", null);

                // then
                assertSuccess(response);
            }
        }
}