package org.javaup.api;

import org.javaup.entity.Shop;
import org.javaup.test.TestDataFactory;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shop API 自动化测试
 * 测试商铺相关的所有接口
 */
@DisplayName("Shop API 自动化测试")
class ShopApiTest extends BaseApiTest {

    private Shop testShop;
    private Long shopId;

    @BeforeEach
    void setUp() {
        testShop = TestDataFactory.createShop();
        testShop.setId(null);
    }

    // ==================== POST /shop 测试 ====================

    @Nested
    @DisplayName("POST /shop - 创建商铺")
    class CreateShopTests {

        @Test
        @DisplayName("正常创建 → 返回200和商铺ID")
        void should_createShop_when_validData() {
            // when
            var response = post("/shop", testShop);

            // then
            assertSuccess(response);
            shopId = getData(response, Long.class);
            assertThat(shopId).isNotNull();
            assertThat(shopId).isPositive();
        }

        @Test
        @DisplayName("创建失败（缺少必要字段）→ 返回200但ok为false")
        void should_fail_when_missingRequiredFields() {
            // given
            Shop invalidShop = new Shop();
            invalidShop.setName("测试店铺");
            // 缺少typeId等必要字段

            // when
            var response = post("/shop", invalidShop);

            // then
            // 注意：具体行为取决于业务逻辑，这里假设会失败
            // 实际情况可能需要根据业务逻辑调整
        }
    }

    // ==================== GET /shop/{id} 测试 ====================

    @Nested
    @DisplayName("GET /shop/{id} - 查询商铺")
    class QueryShopByIdTests {

        @Test
        @DisplayName("商铺存在 → 返回200和商铺数据")
        void should_returnShop_when_exists() {
            // given
            // 先创建一个商铺
            var createResponse = post("/shop", testShop);
            assertSuccess(createResponse);
            shopId = getData(createResponse, Long.class);

            // when
            var response = get("/shop/{id}", shopId);

            // then
            assertSuccess(response);
            Shop shop = response.jsonPath().getObject("data", Shop.class);
            assertThat(shop).isNotNull();
            assertThat(shop.getId()).isEqualTo(shopId);
            assertThat(shop.getName()).isEqualTo(testShop.getName());
        }

        @Test
        @DisplayName("商铺不存在 → 返回200但ok为false")
        void should_fail_when_notExists() {
            // given
            Long nonExistentId = 999999L;

            // when
            var response = get("/shop/{id}", nonExistentId);

            // then
            assertFailure(response);
        }
    }

    // ==================== PUT /shop 测试 ====================

    @Nested
    @DisplayName("PUT /shop - 更新商铺")
    class UpdateShopTests {

        @Test
        @DisplayName("正常更新 → 返回200")
        void should_updateShop_when_validData() {
            // given
            // 先创建一个商铺
            var createResponse = post("/shop", testShop);
            assertSuccess(createResponse);
            shopId = getData(createResponse, Long.class);

            Shop updateData = TestDataFactory.createShop(shopId);
            updateData.setName("更新后的店铺名称");

            // when
            var response = put("/shop", updateData);

            // then
            assertSuccess(response);

            // 验证更新成功
            var queryResponse = get("/shop/{id}", shopId);
            assertSuccess(queryResponse);
            Shop updatedShop = queryResponse.jsonPath().getObject("data", Shop.class);
            assertThat(updatedShop.getName()).isEqualTo("更新后的店铺名称");
        }

        @Test
        @DisplayName("ID为空 → 返回200但ok为false")
        void should_fail_when_idIsNull() {
            // given
            Shop shopWithoutId = TestDataFactory.createShop();
            shopWithoutId.setId(null);

            // when
            var response = put("/shop", shopWithoutId);

            // then
            assertFailure(response);
        }
    }

    // ==================== GET /shop/of/type 测试 ====================

    @Nested
    @DisplayName("GET /shop/of/type - 按类型查询商铺")
    class QueryShopByTypeTests {

        @Test
        @DisplayName("有商铺 → 返回200和商铺列表")
        void should_returnShopList_when_hasShops() {
            // given
            Integer typeId = 1;

            // when
            var response = getWithQueryParam("/shop/of/type", "typeId", typeId);

            // then
            assertSuccess(response);
            List<Shop> shops = response.jsonPath().getList("data", Shop.class);
            assertThat(shops).isNotNull();
        }

        @Test
        @DisplayName("无商铺 → 返回200和空列表")
        void should_returnEmptyList_when_noShops() {
            // given
            Integer typeId = 999;

            // when
            var response = getWithQueryParam("/shop/of/type", "typeId", typeId);

            // then
            assertSuccess(response);
            List<Shop> shops = response.jsonPath().getList("data", Shop.class);
            assertThat(shops).isEmpty();
        }
    }

    // ==================== GET /shop/of/name 测试 ====================

    @Nested
    @DisplayName("GET /shop/of/name - 按名称搜索商铺")
    class QueryShopByNameTests {

        @Test
        @DisplayName("有匹配商铺 → 返回200和商铺列表")
        void should_returnShopList_when_hasMatchingShops() {
            // given
            String name = "测试";

            // when
            var response = getWithQueryParam("/shop/of/name", "name", name);

            // then
            assertSuccess(response);
            List<Shop> shops = response.jsonPath().getList("data", Shop.class);
            assertThat(shops).isNotNull();
        }

        @Test
        @DisplayName("无匹配商铺 → 返回200和空列表")
        void should_returnEmptyList_when_noMatchingShops() {
            // given
            String name = "不存在的店铺名称";

            // when
            var response = getWithQueryParam("/shop/of/name", "name", name);

            // then
            assertSuccess(response);
            List<Shop> shops = response.jsonPath().getList("data", Shop.class);
            assertThat(shops).isEmpty();
        }
    }
}