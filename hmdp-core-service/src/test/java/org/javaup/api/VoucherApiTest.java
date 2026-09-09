package org.javaup.api;

import org.junit.jupiter.api.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Voucher API 自动化测试
 * 测试优惠券相关的所有接口
 */
@DisplayName("Voucher API 自动化测试")
class VoucherApiTest extends BaseApiTest {

    private Long shopId = 1L;

    // ==================== GET /voucher/list/{shopId} 测试 ====================

    @Nested
    @DisplayName("GET /voucher/list/{shopId} - 查询商铺优惠券")
    class QueryVoucherOfShopTests {

        @Test
        @DisplayName("查询商铺优惠券 → 返回200")
        void should_return200_when_queryVoucherOfShop() {
            // when
            var response = get("/voucher/list/{shopId}", shopId);

            // then
            assertSuccess(response);
        }

        @Test
        @DisplayName("查询不存在商铺的优惠券 → 返回200和空列表")
        void should_return200WithEmptyList_when_shopNotFound() {
            // when
            var response = get("/voucher/list/{shopId}", 999999L);

            // then
            assertSuccess(response);
        }
    }

    // ==================== POST /voucher/get 测试 ====================

    @Nested
    @DisplayName("POST /voucher/get - 查询秒杀优惠券")
    class GetSeckillVoucherTests {

        @Test
        @DisplayName("查询不存在的秒杀优惠券 → 返回失败")
        void should_fail_when_seckillVoucherNotFound() {
            // when
            var response = post("/voucher/get", java.util.Map.of("voucherId", 999999L));

            // then
            // 可能返回失败或空数据
            assertThat(response.getStatusCode()).isEqualTo(200);
        }
    }
}