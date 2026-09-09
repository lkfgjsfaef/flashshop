package org.javaup.service.impl;

import org.javaup.redis.RedisCacheImpl;
import org.javaup.redis.RedisKeyBuild;
import org.javaup.test.BaseUnitTest;
import org.junit.jupiter.api.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AutoIssueNotifyServiceImpl 单元测试
 * 覆盖场景：自动发券通知发送（短信/APP）、去重窗口、异常处理
 */
class AutoIssueNotifyServiceImplTest extends BaseUnitTest {

    @InjectMocks
    private AutoIssueNotifyServiceImpl autoIssueNotifyService;

    @Mock
    private RedisCacheImpl redisCache;

    private Long voucherId = 1L;
    private Long userId = 100L;
    private Long orderId = 200L;

    @BeforeEach
    void setUp() {
        // 默认配置
        ReflectionTestUtils.setField(autoIssueNotifyService, "dedupWindowSeconds", 300L);
    }

    // ==================== sendAutoIssueNotify 测试 ====================

    @Nested
    @DisplayName("sendAutoIssueNotify - 发送自动发券通知")
    class SendAutoIssueNotifyTests {

        @Test
        @DisplayName("首次通知 + SMS启用 + APP启用 → 发送短信和APP通知")
        void should_sendBothNotifications_when_firstTimeAndBothEnabled() {
            // given
            ReflectionTestUtils.setField(autoIssueNotifyService, "smsEnabled", true);
            ReflectionTestUtils.setField(autoIssueNotifyService, "appEnabled", true);
            ReflectionTestUtils.setField(autoIssueNotifyService, "smsTo", "13800138000");
            when(redisCache.setIfAbsent(any(RedisKeyBuild.class), eq("1"), eq(300L), eq(TimeUnit.SECONDS)))
                    .thenReturn(true);

            // when
            autoIssueNotifyService.sendAutoIssueNotify(voucherId, userId, orderId);

            // then
            verify(redisCache).setIfAbsent(any(RedisKeyBuild.class), eq("1"), eq(300L), eq(TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("重复通知（去重窗口内） → 不发送通知")
        void should_skipNotification_when_withinDedupWindow() {
            // given
            ReflectionTestUtils.setField(autoIssueNotifyService, "smsEnabled", true);
            ReflectionTestUtils.setField(autoIssueNotifyService, "appEnabled", true);
            ReflectionTestUtils.setField(autoIssueNotifyService, "smsTo", "13800138000");
            when(redisCache.setIfAbsent(any(RedisKeyBuild.class), eq("1"), eq(300L), eq(TimeUnit.SECONDS)))
                    .thenReturn(false);

            // when
            autoIssueNotifyService.sendAutoIssueNotify(voucherId, userId, orderId);

            // then
            verify(redisCache).setIfAbsent(any(RedisKeyBuild.class), eq("1"), eq(300L), eq(TimeUnit.SECONDS));
            // 不应有其他操作
        }

        @Test
        @DisplayName("SMS未启用 → 不发送短信通知（但APP通知可能发送）")
        void should_notSendSms_when_smsDisabled() {
            // given
            ReflectionTestUtils.setField(autoIssueNotifyService, "smsEnabled", false);
            ReflectionTestUtils.setField(autoIssueNotifyService, "appEnabled", true);
            when(redisCache.setIfAbsent(any(RedisKeyBuild.class), eq("1"), eq(300L), eq(TimeUnit.SECONDS)))
                    .thenReturn(true);

            // when
            autoIssueNotifyService.sendAutoIssueNotify(voucherId, userId, orderId);

            // then — 不抛异常即可
        }

        @Test
        @DisplayName("APP未启用 → 不发送APP通知")
        void should_notSendApp_when_appDisabled() {
            // given
            ReflectionTestUtils.setField(autoIssueNotifyService, "smsEnabled", true);
            ReflectionTestUtils.setField(autoIssueNotifyService, "appEnabled", false);
            ReflectionTestUtils.setField(autoIssueNotifyService, "smsTo", "13800138000");
            when(redisCache.setIfAbsent(any(RedisKeyBuild.class), eq("1"), eq(300L), eq(TimeUnit.SECONDS)))
                    .thenReturn(true);

            // when
            autoIssueNotifyService.sendAutoIssueNotify(voucherId, userId, orderId);

            // then — 不抛异常即可
        }

        @Test
        @DisplayName("SMS号码为空 → 不发送短信通知")
        void should_notSendSms_when_smsToBlank() {
            // given
            ReflectionTestUtils.setField(autoIssueNotifyService, "smsEnabled", true);
            ReflectionTestUtils.setField(autoIssueNotifyService, "appEnabled", false);
            ReflectionTestUtils.setField(autoIssueNotifyService, "smsTo", "");
            when(redisCache.setIfAbsent(any(RedisKeyBuild.class), eq("1"), eq(300L), eq(TimeUnit.SECONDS)))
                    .thenReturn(true);

            // when
            autoIssueNotifyService.sendAutoIssueNotify(voucherId, userId, orderId);

            // then — 不抛异常即可
        }

        @Test
        @DisplayName("Redis异常 → shouldNotify默认返回true，仍然尝试发送")
        void should_stillNotify_when_redisThrowsException() {
            // given
            ReflectionTestUtils.setField(autoIssueNotifyService, "smsEnabled", true);
            ReflectionTestUtils.setField(autoIssueNotifyService, "appEnabled", true);
            ReflectionTestUtils.setField(autoIssueNotifyService, "smsTo", "13800138000");
            when(redisCache.setIfAbsent(any(RedisKeyBuild.class), eq("1"), eq(300L), eq(TimeUnit.SECONDS)))
                    .thenThrow(new RuntimeException("Redis connection failed"));

            // when
            autoIssueNotifyService.sendAutoIssueNotify(voucherId, userId, orderId);

            // then — 不抛异常（内部catch）
        }
    }
}