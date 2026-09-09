package org.javaup.service.impl;

import org.javaup.mapper.UserMapper;

import cn.hutool.core.util.RandomUtil;
import jakarta.servlet.http.HttpSession;
import org.javaup.dto.LoginFormDTO;
import org.javaup.dto.Result;
import org.javaup.dto.UserDTO;
import org.javaup.entity.User;
import org.javaup.entity.UserInfo;
import org.javaup.entity.UserPhone;
import org.javaup.redis.RedisCacheImpl;
import org.javaup.service.IUserInfoService;
import org.javaup.service.IUserPhoneService;
import org.javaup.test.BaseUnitTest;
import org.javaup.test.ChainWrapperMocks;
import org.javaup.test.TestDataFactory;
import org.javaup.toolkit.SnowflakeIdGenerator;
import org.javaup.utils.RegexUtils;
import org.javaup.utils.UserHolder;
import org.junit.jupiter.api.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UserServiceImpl 单元测试
 * 覆盖场景：发送验证码、登录（新用户/老用户）、签到、签到统计
 */
class UserServiceImplTest extends BaseUnitTest {
    // ServiceImpl 的 baseMapper：不注入的话，走到 getById/list/save 等
    // 真实 DAO 方法时会抛 "baseMapper can not be null"
    @Mock
    private UserMapper userMapper;


    @Spy
    @InjectMocks
    private UserServiceImpl userService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private IUserInfoService userInfoService;

    @Mock
    private IUserPhoneService userPhoneService;

    @Mock
    private RedisCacheImpl redisCache;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private HttpSession session;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    // 注意：验证码 key 前缀取自 RedisConstants.LOGIN_CODE_KEY，
    // 是 "login:code:" 而不是 "sms:code:"（早前写错成 sms，导致验证码永远取不到）
    private static final String LOGIN_CODE_KEY = "login:code:";

    private static final String VALID_PHONE = "13800138000";
    private static final String INVALID_PHONE = "1234";
    private static final String VALID_CODE = "123456";

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        // login 成功后会把用户信息写进 Redis Hash，缺这个桩会 NPE
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    // ==================== sendCode 测试 ====================

    @Nested
    @DisplayName("sendCode - 发送验证码")
    class SendCodeTests {

        @Test
        @DisplayName("手机号格式正确 → 发送验证码并返回成功")
        void should_sendCode_when_phoneValid() {
            // when
            Result<String> result = userService.sendCode(VALID_PHONE, session);

            // then
            assertThat(result.getSuccess()).isEqualTo(true);
            verify(valueOperations).set(
                    eq(LOGIN_CODE_KEY + VALID_PHONE),
                    anyString(),
                    eq(2L),
                    eq(TimeUnit.MINUTES)
            );
        }

        @Test
        @DisplayName("手机号格式错误 → 返回失败")
        void should_fail_when_phoneInvalid() {
            // when
            Result<String> result = userService.sendCode(INVALID_PHONE, session);

            // then
            assertThat(result.getSuccess()).isEqualTo(false);
            assertThat(result.getErrorMsg()).isEqualTo("手机号格式错误！");
            verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        }
    }

    // ==================== login 测试 ====================

    @Nested
    @DisplayName("login - 用户登录")
    class LoginTests {

        @Test
        @DisplayName("验证码错误 → 返回失败")
        void should_fail_when_codeWrong() {
            // given
            LoginFormDTO form = TestDataFactory.createLoginForm(VALID_PHONE, "000000");
            when(valueOperations.get(LOGIN_CODE_KEY + VALID_PHONE)).thenReturn(VALID_CODE);

            // when
            Result<String> result = userService.login(form, session);

            // then
            assertThat(result.getSuccess()).isEqualTo(false);
            assertThat(result.getErrorMsg()).isEqualTo("验证码错误");
        }

        @Test
        @DisplayName("验证码过期（Redis中不存在）→ 返回失败")
        void should_fail_when_codeExpired() {
            // given
            LoginFormDTO form = TestDataFactory.createLoginForm(VALID_PHONE, VALID_CODE);
            when(valueOperations.get(LOGIN_CODE_KEY + VALID_PHONE)).thenReturn(null);

            // when
            Result<String> result = userService.login(form, session);

            // then
            assertThat(result.getSuccess()).isEqualTo(false);
            assertThat(result.getErrorMsg()).isEqualTo("验证码错误");
        }

        @Test
        @DisplayName("手机号格式错误 → 返回失败")
        void should_fail_when_phoneInvalid() {
            // given
            LoginFormDTO form = TestDataFactory.createLoginForm(INVALID_PHONE, VALID_CODE);

            // when
            Result<String> result = userService.login(form, session);

            // then
            assertThat(result.getSuccess()).isEqualTo(false);
            assertThat(result.getErrorMsg()).isEqualTo("手机号格式错误！");
        }

        @Test
        @DisplayName("新用户登录 → 创建用户并返回token")
        void should_createUser_when_newUserLogin() {
            // given
            LoginFormDTO form = TestDataFactory.createLoginForm(VALID_PHONE, VALID_CODE);
            when(valueOperations.get(LOGIN_CODE_KEY + VALID_PHONE)).thenReturn(VALID_CODE);
            when(userPhoneService.lambdaQuery()).thenReturn(ChainWrapperMocks.lambdaQueryChain(null));
            when(snowflakeIdGenerator.nextId()).thenReturn(TestDataFactory.nextId());
            when(userInfoService.save(any(UserInfo.class))).thenReturn(true);
            when(userPhoneService.save(any(UserPhone.class))).thenReturn(true);
            // createUserWithPhone 里的 save(user) 是 ServiceImpl 的真实方法，
            // 内部走 getBaseMapper().insert()，而 baseMapper 注不进来（泛型擦除），
            // 在 spy 上直接 stub 掉，不执行真实方法体
            doReturn(true).when(userService).save(any(User.class));

            // when
            Result<String> result = userService.login(form, session);

            // then
            assertThat(result.getSuccess()).isEqualTo(true);
            assertThat(result.getData()).isNotNull(); // token不为空
            verify(userService).save(any(User.class));
            verify(userInfoService).save(any(UserInfo.class));
            verify(userPhoneService).save(any(UserPhone.class));
        }

        @Test
        @DisplayName("老用户登录 → 直接返回token")
        void should_returnToken_when_oldUserLogin() {
            // given
            User existingUser = TestDataFactory.createUser();
            LoginFormDTO form = TestDataFactory.createLoginForm(VALID_PHONE, VALID_CODE);
            when(valueOperations.get(LOGIN_CODE_KEY + VALID_PHONE)).thenReturn(VALID_CODE);
            when(userPhoneService.lambdaQuery()).thenReturn(ChainWrapperMocks.lambdaQueryChain(new UserPhone()));

            // 模拟老用户查询
            doReturn(ChainWrapperMocks.lambdaQueryChain(existingUser)).when(userService).lambdaQuery();

            // when
            Result<String> result = userService.login(form, session);

            // then
            assertThat(result.getSuccess()).isEqualTo(true);
            assertThat(result.getData()).isNotNull();
            // 不应该创建新用户
            verify(userService, never()).save(any(User.class));
        }
    }

    // ==================== sign 测试 ====================

    @Nested
    @DisplayName("sign - 用户签到")
    class SignTests {

        @BeforeEach
        void setUp() {
            UserHolder.saveUser(TestDataFactory.createUserDTO(1L));
        }

        @Test
        @DisplayName("正常签到 → 返回成功")
        void should_success_when_sign() {
            // when
            Result<Void> result = userService.sign();

            // then
            assertThat(result.getSuccess()).isEqualTo(true);
        }
    }

    // ==================== signCount 测试 ====================

    @Nested
    @DisplayName("signCount - 签到统计")
    class SignCountTests {

        @BeforeEach
        void setUp() {
            UserHolder.saveUser(TestDataFactory.createUserDTO(1L));
        }

        @Test
        @DisplayName("无签到记录 → 返回0")
        void should_return0_when_noSignRecords() {
            // given
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.bitField(anyString(), any())).thenReturn(null);

            // when
            Result<Integer> result = userService.signCount();

            // then
            assertThat(result.getData()).isEqualTo(0);
        }

        @Test
        @DisplayName("有签到记录 → 返回连续签到天数")
        void should_returnCount_when_hasSignRecords() {
            // given
            // 模拟连续签到3天：二进制 111 = 7
            when(valueOperations.bitField(anyString(), any())).thenReturn(java.util.List.of(7L));

            // when
            Result<Integer> result = userService.signCount();

            // then
            assertThat(result.getData()).isEqualTo(3);
        }
    }

    // ==================== Mock辅助类 ====================

    /**
     * MyBatis-Plus LambdaQueryWrapper 的简单Mock
     * 仅用于单元测试中链式调用的模拟
     */
}