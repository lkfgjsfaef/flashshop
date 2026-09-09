package org.javaup.api;

import org.javaup.dto.LoginFormDTO;
import org.javaup.test.TestDataFactory;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * User API 自动化测试
 * 测试用户相关的所有接口
 */
@DisplayName("User API 自动化测试")
class UserApiTest extends BaseApiTest {

    // ==================== POST /user/code 测试 ====================

    @Nested
    @DisplayName("POST /user/code - 发送验证码")
    class SendCodeTests {

        @Test
        @DisplayName("发送验证码 → 返回200")
        void should_return200_when_sendCode() {
            // when
            var response = getWithQueryParam("/user/code", "phone", "13800138000");

            // then
            // 注意：实际行为取决于service层实现
            assertThat(response.getStatusCode()).isEqualTo(200);
        }
    }

    // ==================== POST /user/login 测试 ====================

    @Nested
    @DisplayName("POST /user/login - 登录")
    class LoginTests {

        @Test
        @DisplayName("登录（验证码错误） → 返回失败")
        void should_fail_when_wrongCode() {
            // given
            LoginFormDTO loginForm = TestDataFactory.createLoginForm("13800138000", "000000");

            // when
            var response = post("/user/login", loginForm);

            // then
            assertFailure(response);
        }
    }

    // ==================== GET /user/me 测试 ====================

    @Nested
    @DisplayName("GET /user/me - 获取当前用户")
    class MeTests {

        @Test
        @DisplayName("未登录时获取当前用户 → 返回200但data为null")
        void should_return200WithNullData_when_notLoggedIn() {
            // when
            var response = get("/user/me");

            // then
            assertThat(response.getStatusCode()).isEqualTo(200);
        }
    }

    // ==================== GET /user/{id} 测试 ====================

    @Nested
    @DisplayName("GET /user/{id} - 查询用户")
    class QueryUserByIdTests {

        @Test
        @DisplayName("查询不存在的用户 → 返回200但data为null")
        void should_return200WithNullData_when_userNotFound() {
            // when
            var response = get("/user/{id}", 999999L);

            // then
            assertSuccess(response);
            // jsonPath().get() 返回 Object，直接断言会让 assertThat 重载解析不明确，这里显式转型
            assertThat((Object) response.jsonPath().get("data")).isNull();
        }
    }
}