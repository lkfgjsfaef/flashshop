package org.javaup.api;

import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Follow API 自动化测试
 * 测试关注相关的所有接口
 */
@DisplayName("Follow API 自动化测试")
class FollowApiTest extends BaseApiTest {

    private Long followUserId = 200L;

    // ==================== PUT /follow/{id}/{isFollow} 测试 ====================

    @Nested
    @DisplayName("PUT /follow/{id}/{isFollow} - 关注/取关")
    class FollowTests {

        @Test
        @DisplayName("未登录时关注 → 可能返回失败")
        void should_handleFollow_when_requestSent() {
            // when
            var response = given()
                    .contentType(io.restassured.http.ContentType.JSON)
                    .when()
                    .put("/follow/{id}/{isFollow}", followUserId, true);

            // then
            assertThat(response.getStatusCode()).isEqualTo(200);
        }
    }

    // ==================== GET /follow/or/not/{id} 测试 ====================

    @Nested
    @DisplayName("GET /follow/or/not/{id} - 查询是否关注")
    class IsFollowTests {

        @Test
        @DisplayName("查询是否关注 → 返回200")
        void should_return200_when_queryIsFollow() {
            // when
            var response = get("/follow/or/not/{id}", followUserId);

            // then
            assertThat(response.getStatusCode()).isEqualTo(200);
        }
    }

    // ==================== GET /follow/common/{id} 测试 ====================

    @Nested
    @DisplayName("GET /follow/common/{id} - 共同关注")
    class FollowCommonsTests {

        @Test
        @DisplayName("查询共同关注 → 返回200")
        void should_return200_when_queryFollowCommons() {
            // when
            var response = get("/follow/common/{id}", followUserId);

            // then
            assertThat(response.getStatusCode()).isEqualTo(200);
        }
    }

}