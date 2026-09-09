package org.javaup.controller;

import org.springframework.context.annotation.Import;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.javaup.dto.Result;
import org.javaup.entity.Voucher;
import org.javaup.model.SeckillVoucherFullModel;
import org.javaup.service.ISeckillVoucherService;
import org.javaup.service.IVoucherService;
import org.javaup.test.TestDataFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * VoucherController 切片测试
 */
@WebMvcTest(VoucherController.class)
@Import(org.javaup.test.MockMapperConfig.class)
class VoucherControllerTest extends BaseWebMvcTest {


    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IVoucherService voucherService;

    @MockBean
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private ObjectMapper objectMapper;

    private Long shopId = 1L;
    private Long voucherId = 100L;

    // ==================== GET /voucher/list/{shopId} 测试 ====================

    @Nested
    @DisplayName("GET /voucher/list/{shopId} - 查询商铺优惠券")
    class QueryVoucherOfShopTests {

        @Test
        @DisplayName("有优惠券 → 返回200和优惠券列表")
        void should_return200_when_hasVouchers() throws Exception {
            // given
            List<Voucher> vouchers = List.of(TestDataFactory.createVoucher(voucherId));
            when(voucherService.queryVoucherOfShop(shopId)).thenReturn(Result.ok(vouchers));

            // when & then
            mockMvc.perform(get("/voucher/list/{shopId}", shopId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(1));
        }

        @Test
        @DisplayName("无优惠券 → 返回200和空列表")
        void should_return200WithEmptyList_when_noVouchers() throws Exception {
            // given
            when(voucherService.queryVoucherOfShop(shopId)).thenReturn(Result.ok(List.of()));

            // when & then
            mockMvc.perform(get("/voucher/list/{shopId}", shopId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }
    }

    // ==================== POST /voucher/get 测试 ====================

    @Nested
    @DisplayName("POST /voucher/get - 查询秒杀优惠券")
    class GetSeckillVoucherTests {

        @Test
        @DisplayName("查询秒杀优惠券 → 返回200")
        void should_return200_when_getSeckillVoucher() throws Exception {
            // given
            SeckillVoucherFullModel model = TestDataFactory.createSeckillVoucherFullModel(voucherId);
            when(seckillVoucherService.queryByVoucherId(voucherId)).thenReturn(model);

            // when & then
            mockMvc.perform(post("/voucher/get")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"voucherId\":" + voucherId + "}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }
}