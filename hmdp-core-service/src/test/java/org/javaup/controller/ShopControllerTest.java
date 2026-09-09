package org.javaup.controller;

import org.springframework.context.annotation.Import;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.javaup.dto.Result;
import org.javaup.entity.Shop;
import org.javaup.service.IShopService;
import org.javaup.test.ChainWrapperMocks;
import org.javaup.test.TestDataFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ShopController 切片测试
 * 仅加载Web层，不启动完整Spring上下文
 */
@WebMvcTest(ShopController.class)
@Import(org.javaup.test.MockMapperConfig.class)
class ShopControllerTest extends BaseWebMvcTest {


    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IShopService shopService;

    @Autowired
    private ObjectMapper objectMapper;

    private Shop testShop;
    private Long shopId = 1L;

    @BeforeEach
    void setUp() {
        testShop = TestDataFactory.createShop(shopId);
    }

    // ==================== GET /shop/{id} 测试 ====================

    @Nested
    @DisplayName("GET /shop/{id} - 查询商铺")
    class QueryShopByIdTests {

        @Test
        @DisplayName("商铺存在 → 返回200和商铺数据")
        void should_return200_when_shopExists() throws Exception {
            // given
            when(shopService.queryById(shopId)).thenReturn(Result.ok(testShop));

            // when & then
            mockMvc.perform(get("/shop/{id}", shopId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(shopId))
                    .andExpect(jsonPath("$.data.name").value(testShop.getName()));
        }

        @Test
        @DisplayName("商铺不存在 → 返回200但data为null")
        void should_return200WithNullData_when_shopNotFound() throws Exception {
            // given
            when(shopService.queryById(shopId)).thenReturn(Result.fail("店铺不存在！"));

            // when & then
            mockMvc.perform(get("/shop/{id}", shopId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errorMsg").value("店铺不存在！"));
        }
    }

    // ==================== POST /shop 测试 ====================

    @Nested
    @DisplayName("POST /shop - 创建商铺")
    class SaveShopTests {

        @Test
        @DisplayName("正常创建 → 返回200和商铺ID")
        void should_return200_when_saveSuccess() throws Exception {
            // given
            Shop newShop = TestDataFactory.createShop();
            newShop.setId(null);
            Long generatedId = TestDataFactory.nextId();
            when(shopService.saveShop(any(Shop.class))).thenReturn(Result.ok(generatedId));

            // when & then
            mockMvc.perform(post("/shop")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(newShop)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(generatedId));
        }
    }

    // ==================== PUT /shop 测试 ====================

    @Nested
    @DisplayName("PUT /shop - 更新商铺")
    class UpdateShopTests {

        @Test
        @DisplayName("正常更新 → 返回200")
        void should_return200_when_updateSuccess() throws Exception {
            // given
            when(shopService.update(any(Shop.class))).thenReturn(Result.ok());

            // when & then
            mockMvc.perform(put("/shop")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(testShop)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("ID为空 → 返回200但ok为false")
        void should_return200WithFalse_when_idIsNull() throws Exception {
            // given
            Shop shopWithoutId = TestDataFactory.createShop();
            shopWithoutId.setId(null);
            when(shopService.update(any(Shop.class))).thenReturn(Result.fail("店铺id不能为空"));

            // when & then
            mockMvc.perform(put("/shop")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(shopWithoutId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errorMsg").value("店铺id不能为空"));
        }
    }

    // ==================== GET /shop/of/type 测试 ====================

    @Nested
    @DisplayName("GET /shop/of/type - 按类型查询商铺")
    class QueryShopByTypeTests {

        @Test
        @DisplayName("有商铺 → 返回200和商铺列表")
        void should_return200_when_hasShops() throws Exception {
            // given
            Integer typeId = 1;
            when(shopService.queryShopByType(eq(typeId), anyInt(), any(), any()))
                    .thenReturn(Result.ok(java.util.List.of(testShop)));

            // when & then
            mockMvc.perform(get("/shop/of/type")
                            .param("typeId", typeId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(1));
        }

        @Test
        @DisplayName("无商铺 → 返回200和空列表")
        void should_return200WithEmptyList_when_noShops() throws Exception {
            // given
            Integer typeId = 999;
            when(shopService.queryShopByType(eq(typeId), anyInt(), any(), any()))
                    .thenReturn(Result.ok(java.util.Collections.emptyList()));

            // when & then
            mockMvc.perform(get("/shop/of/type")
                            .param("typeId", typeId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }
    }

    // ==================== GET /shop/of/name 测试 ====================

    @Nested
    @DisplayName("GET /shop/of/name - 按名称搜索商铺")
    class QueryShopByNameTests {

        @Test
        @DisplayName("有匹配商铺 → 返回200和商铺列表")
        void should_return200_when_hasMatchingShops() throws Exception {
            // given
            String name = "测试";
            when(shopService.query()).thenReturn(ChainWrapperMocks.queryChain(java.util.List.of(testShop)));

            // when & then
            mockMvc.perform(get("/shop/of/name")
                            .param("name", name))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(1));
        }
    }

    // ==================== Mock辅助类 ====================

    /**
     * MyBatis-Plus QueryWrapper 的简单Mock
     */
}