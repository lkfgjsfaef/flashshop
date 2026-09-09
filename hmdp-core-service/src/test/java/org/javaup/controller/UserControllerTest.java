package org.javaup.controller;

import org.springframework.context.annotation.Import;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.javaup.dto.LoginFormDTO;
import org.javaup.dto.Result;
import org.javaup.dto.UserDTO;
import org.javaup.entity.User;
import org.javaup.entity.UserInfo;
import org.javaup.service.IUserInfoService;
import org.javaup.service.IUserService;
import org.javaup.test.TestDataFactory;
import org.javaup.utils.UserHolder;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * UserController 切片测试
 */
@WebMvcTest(UserController.class)
@Import(org.javaup.test.MockMapperConfig.class)
class UserControllerTest extends BaseWebMvcTest {


    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IUserService userService;

    @MockBean
    private IUserInfoService userInfoService;

    @Autowired
    private ObjectMapper objectMapper;

    private Long userId = 100L;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        session = new MockHttpSession();
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    // ==================== POST /user/code 测试 ====================

    @Nested
    @DisplayName("POST /user/code - 发送验证码")
    class SendCodeTests {

        @Test
        @DisplayName("正常发送 → 返回200")
        void should_return200_when_sendCodeSuccess() throws Exception {
            // given
            when(userService.sendCode(eq("13800138000"), any())).thenReturn(Result.ok());

            // when & then
            mockMvc.perform(post("/user/code")
                            .param("phone", "13800138000")
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("手机号格式错误 → 返回失败")
        void should_fail_when_phoneInvalid() throws Exception {
            // given
            when(userService.sendCode(eq("123"), any())).thenReturn(Result.fail("手机号格式不正确"));

            // when & then
            mockMvc.perform(post("/user/code")
                            .param("phone", "123")
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ==================== POST /user/login 测试 ====================

    @Nested
    @DisplayName("POST /user/login - 登录")
    class LoginTests {

        @Test
        @DisplayName("正常登录 → 返回200和token")
        void should_return200_when_loginSuccess() throws Exception {
            // given
            LoginFormDTO loginForm = TestDataFactory.createLoginForm("13800138000", "123456");
            when(userService.login(any(), any())).thenReturn(Result.ok("mock-token"));

            // when & then
            mockMvc.perform(post("/user/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginForm))
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value("mock-token"));
        }

        @Test
        @DisplayName("验证码错误 → 返回失败")
        void should_fail_when_codeWrong() throws Exception {
            // given
            LoginFormDTO loginForm = TestDataFactory.createLoginForm("13800138000", "000000");
            when(userService.login(any(), any())).thenReturn(Result.fail("验证码错误"));

            // when & then
            mockMvc.perform(post("/user/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginForm))
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errorMsg").value("验证码错误"));
        }
    }

    // ==================== POST /user/logout 测试 ====================

    @Nested
    @DisplayName("POST /user/logout - 登出")
    class LogoutTests {

        @Test
        @DisplayName("登出 → 返回功能未完成")
        void should_returnFail_when_logout() throws Exception {
            // when & then
            mockMvc.perform(post("/user/logout"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errorMsg").value("功能未完成"));
        }
    }

    // ==================== GET /user/me 测试 ====================

    @Nested
    @DisplayName("GET /user/me - 获取当前用户")
    class MeTests {

        @Test
        @DisplayName("已登录 → 返回当前用户信息")
        void should_returnUser_when_loggedIn() throws Exception {
            // given
            UserHolder.saveUser(TestDataFactory.createUserDTO(userId));

            // when & then
            mockMvc.perform(get("/user/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(userId));
        }
    }

    // ==================== GET /user/info/{id} 测试 ====================

    @Nested
    @DisplayName("GET /user/info/{id} - 查询用户详情")
    class InfoTests {

        @Test
        @DisplayName("用户详情存在 → 返回详情（时间字段置null）")
        void should_returnInfo_when_exists() throws Exception {
            // given
            UserInfo info = TestDataFactory.createUserInfo(userId);
            when(userInfoService.getById(userId)).thenReturn(info);

            // when & then
            mockMvc.perform(get("/user/info/{id}", userId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.userId").value(userId));
        }

        @Test
        @DisplayName("用户详情不存在 → 返回空data")
        void should_returnNullData_when_notExists() throws Exception {
            // given
            when(userInfoService.getById(userId)).thenReturn(null);

            // when & then
            mockMvc.perform(get("/user/info/{id}", userId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    // ==================== POST /user/level/update 测试 ====================

    @Nested
    @DisplayName("POST /user/level/update - 更新用户等级")
    class UpdateLevelTests {

        @Test
        @DisplayName("已登录 → 调用service更新等级")
        void should_updateLevel_when_loggedIn() throws Exception {
            // given
            UserHolder.saveUser(TestDataFactory.createUserDTO(userId));
            when(userInfoService.updateUserLevel(userId, 3)).thenReturn(Result.ok());

            // when & then
            mockMvc.perform(post("/user/level/update")
                            .param("newLevel", "3"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("未登录 → 被登录拦截器挡在 Controller 之外，返回 401")
        void should_fail_when_notLoggedIn() throws Exception {
            // given：基类默认塞了登录用户，这里清掉模拟未登录
            // 请求会先经过 LoginInterceptor，它发现 ThreadLocal 里没用户就直接 401，
            // 根本进不到 Controller，所以断言的是 401 而不是业务响应体
            UserHolder.removeUser();

            // when & then
            mockMvc.perform(post("/user/level/update")
                            .param("newLevel", "3"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ==================== GET /user/{id} 测试 ====================

    @Nested
    @DisplayName("GET /user/{id} - 查询用户")
    class QueryUserByIdTests {

        @Test
        @DisplayName("用户存在 → 返回UserDTO")
        void should_returnUserDTO_when_userExists() throws Exception {
            // given
            User user = TestDataFactory.createUser(userId);
            when(userService.getById(userId)).thenReturn(user);

            // when & then
            mockMvc.perform(get("/user/{id}", userId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(userId));
        }

        @Test
        @DisplayName("用户不存在 → 返回空data")
        void should_returnNullData_when_userNotFound() throws Exception {
            // given
            when(userService.getById(userId)).thenReturn(null);

            // when & then
            mockMvc.perform(get("/user/{id}", userId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    // ==================== POST /user/sign 测试 ====================

    @Nested
    @DisplayName("POST /user/sign - 签到")
    class SignTests {

        @Test
        @DisplayName("签到 → 调用service签到")
        void should_sign_when_called() throws Exception {
            // given
            when(userService.sign()).thenReturn(Result.ok());

            // when & then
            mockMvc.perform(post("/user/sign"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    // ==================== GET /user/sign/count 测试 ====================

    @Nested
    @DisplayName("GET /user/sign/count - 签到统计")
    class SignCountTests {

        @Test
        @DisplayName("查询签到统计 → 返回统计数据")
        void should_returnSignCount_when_called() throws Exception {
            // given
            when(userService.signCount()).thenReturn(Result.ok(5));

            // when & then
            mockMvc.perform(get("/user/sign/count"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(5));
        }
    }
}