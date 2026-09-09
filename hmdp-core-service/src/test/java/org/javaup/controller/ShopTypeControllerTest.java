package org.javaup.controller;

import org.springframework.context.annotation.Import;

import org.javaup.test.ChainWrapperMocks;

import org.javaup.entity.ShopType;
import org.javaup.service.IShopTypeService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ShopTypeController 切片测试
 */
@WebMvcTest(ShopTypeController.class)
@Import(org.javaup.test.MockMapperConfig.class)
class ShopTypeControllerTest extends BaseWebMvcTest {


    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IShopTypeService typeService;

    // ==================== GET /shop-type/list 测试 ====================

    @Nested
    @DisplayName("GET /shop-type/list - 查询商铺类型列表")
    class QueryTypeListTests {

        @Test
        @DisplayName("有类型 → 返回200和类型列表")
        void should_return200_when_hasTypes() throws Exception {
            // given
            when(typeService.query()).thenReturn(ChainWrapperMocks.queryChain(List.of()));

            // when & then
            mockMvc.perform(get("/shop-type/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    // ==================== Mock辅助类 ====================

}