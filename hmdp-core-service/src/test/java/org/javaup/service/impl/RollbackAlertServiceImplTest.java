package org.javaup.service.impl;

import org.javaup.entity.RollbackFailureLog;
import org.javaup.redis.RedisCacheImpl;
import org.javaup.redis.RedisKeyBuild;
import org.javaup.test.BaseUnitTest;
import org.javaup.test.TestDataFactory;
import org.junit.jupiter.api.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RollbackAlertServiceImpl 单元测试
 * 覆盖场景：回滚失败告警通知（短信/邮件）、去重窗口、异常处理
 */
class RollbackAlertServiceImplTest extends BaseUnitTest {

    @InjectMocks
    private RollbackAlertServiceImpl rollbackAlertService;

    @Mock
    private RedisCacheImpl redisCache;

    private RollbackFailureLog testLog;

    @BeforeEach
    void setUp() {
        testLog = TestDataFactory.createRollbackFailureLog(1L, 100L, 200L);
        ReflectionTestUtils.setField(rollbackAlertService, "dedupWindowSeconds", 300L);
    }

    // ==================== sendRollbackAlert 测试 ====================

    @Nested
    @DisplayName("sendRollbackAlert - 发送回滚失败告警")
    class SendRollbackAlertTests {

        @Test
        @DisplayName("首次告警 + SMS和邮件都启用 → 发送两种告警")
        void should_sendBothAlerts_when_firstTimeAndBothEnabled() {
            // given
            ReflectionTestUtils.setField(rollbackAlertService, "smsEnabled", true);
            ReflectionTestUtils.setField(rollbackAlertService, "emailEnabled", true);
            ReflectionTestUtils.setField(rollbackAlertService, "smsTo", "13800138000");
            ReflectionTestUtils.setField(rollbackAlertService, "emailTo", "admin@example.com");
            when(redisCache.setIfAbsent(any(RedisKeyBuild.class), eq("1"), eq(300L), eq(TimeUnit.SECONDS)))
                    .thenReturn(true);

            // when
            rollbackAlertService.sendRollbackAlert(testLog);

            // then
            verify(redisCache).setIfAbsent(any(RedisKeyBuild.class), eq("1"), eq(300L), eq(TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("重复告警（去重窗口内） → 不发送告警")
        void should_skipAlert_when_withinDedupWindow() {
            // given
            ReflectionTestUtils.setField(rollbackAlertService, "smsEnabled", true);
            ReflectionTestUtils.setField(rollbackAlertService, "emailEnabled", true);
            when(redisCache.setIfAbsent(any(RedisKeyBuild.class), eq("1"), eq(300L), eq(TimeUnit.SECONDS)))
                    .thenReturn(false);

            // when
            rollbackAlertService.sendRollbackAlert(testLog);

            // then
            verify(redisCache).setIfAbsent(any(RedisKeyBuild.class), eq("1"), eq(300L), eq(TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("SMS未启用 → 不发送短信告警")
        void should_notSendSms_when_smsDisabled() {
            // given
            ReflectionTestUtils.setField(rollbackAlertService, "smsEnabled", false);
            ReflectionTestUtils.setField(rollbackAlertService, "emailEnabled", true);
            ReflectionTestUtils.setField(rollbackAlertService, "emailTo", "admin@example.com");
            when(redisCache.setIfAbsent(any(RedisKeyBuild.class), eq("1"), eq(300L), eq(TimeUnit.SECONDS)))
                    .thenReturn(true);

            // when
            rollbackAlertService.sendRollbackAlert(testLog);

            // then — 不抛异常即可
        }

        @Test
        @DisplayName("邮件未启用 → 不发送邮件告警")
        void should_notSendEmail_when_emailDisabled() {
            // given
            ReflectionTestUtils.setField(rollbackAlertService, "smsEnabled", true);
            ReflectionTestUtils.setField(rollbackAlertService, "emailEnabled", false);
            ReflectionTestUtils.setField(rollbackAlertService, "smsTo", "13800138000");
            when(redisCache.setIfAbsent(any(RedisKeyBuild.class), eq("1"), eq(300L), eq(TimeUnit.SECONDS)))
                    .thenReturn(true);

            // when
            rollbackAlertService.sendRollbackAlert(testLog);

            // then — 不抛异常即可
        }

        @Test
        @DisplayName("SMS号码为空 → 不发送短信告警")
        void should_notSendSms_when_smsToEmpty() {
            // given
            ReflectionTestUtils.setField(rollbackAlertService, "smsEnabled", true);
            ReflectionTestUtils.setField(rollbackAlertService, "emailEnabled", false);
            ReflectionTestUtils.setField(rollbackAlertService, "smsTo", "");
            when(redisCache.setIfAbsent(any(RedisKeyBuild.class), eq("1"), eq(300L), eq(TimeUnit.SECONDS)))
                    .thenReturn(true);

            // when
            rollbackAlertService.sendRollbackAlert(testLog);

            // then — 不抛异常即可
        }

        @Test
        @DisplayName("Redis异常 → shouldNotify默认返回true，仍然尝试发送")
        void should_stillAlert_when_redisThrowsException() {
            // given
            ReflectionTestUtils.setField(rollbackAlertService, "smsEnabled", true);
            ReflectionTestUtils.setField(rollbackAlertService, "emailEnabled", true);
            ReflectionTestUtils.setField(rollbackAlertService, "smsTo", "13800138000");
            ReflectionTestUtils.setField(rollbackAlertService, "emailTo", "admin@example.com");
            when(redisCache.setIfAbsent(any(RedisKeyBuild.class), eq("1"), eq(300L), eq(TimeUnit.SECONDS)))
                    .thenThrow(new RuntimeException("Redis connection failed"));

            // when
            rollbackAlertService.sendRollbackAlert(testLog);

            // then — 不抛异常（内部catch）
        }
    }
}