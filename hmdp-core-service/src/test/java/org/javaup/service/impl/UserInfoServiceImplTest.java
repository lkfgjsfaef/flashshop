package org.javaup.service.impl;

import org.javaup.mapper.UserInfoMapper;

import org.javaup.core.RedisKeyManage;
import org.javaup.dto.Result;
import org.javaup.entity.UserInfo;
import org.javaup.exception.HmdpFrameException;
import org.javaup.redis.RedisCacheImpl;
import org.javaup.redis.RedisKeyBuild;
import org.javaup.test.BaseUnitTest;
import org.javaup.test.ChainWrapperMocks;
import org.javaup.test.TestDataFactory;
import org.junit.jupiter.api.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UserInfoServiceImpl 单元测试
 * 覆盖场景：查询用户信息（缓存命中/DB回源/用户不存在）、更新用户等级
 */
class UserInfoServiceImplTest extends BaseUnitTest {
    // ServiceImpl 的 baseMapper：不注入的话，走到 getById/list/save 等
    // 真实 DAO 方法时会抛 "baseMapper can not be null"
    @Mock
    private UserInfoMapper userInfoMapper;


    @InjectMocks
    private UserInfoServiceImpl userInfoService;

    @Mock
    private RedisCacheImpl redisCache;

    private Long userId = 100L;

    // ==================== getByUserId 测试 ====================

    @Nested
    @DisplayName("getByUserId - 查询用户信息")
    class GetByUserIdTests {

        @Test
        @DisplayName("Redis缓存命中 → 直接返回缓存数据")
        void should_returnFromCache_when_cacheHit() {
            // given
            UserInfo cachedInfo = TestDataFactory.createUserInfo(userId);
            when(redisCache.get(any(RedisKeyBuild.class), eq(UserInfo.class))).thenReturn(cachedInfo);

            // when
            UserInfo result = userInfoService.getByUserId(userId);

            // then
            assertThat(result).isEqualTo(cachedInfo);
            verify(redisCache).get(any(RedisKeyBuild.class), eq(UserInfo.class));
        }

        @Test
        @DisplayName("缓存未命中 + DB存在 → 回源DB并写缓存")
        void should_queryDBAndWriteCache_when_cacheMiss() {
            // given
            when(redisCache.get(any(RedisKeyBuild.class), eq(UserInfo.class))).thenReturn(null);
            UserInfo dbInfo = TestDataFactory.createUserInfo(userId);
            // Mock lambdaQuery chain
            UserInfoServiceImpl spyService = spy(userInfoService);
            doReturn(ChainWrapperMocks.lambdaQueryChain(List.of(dbInfo)))
                    .when(spyService).lambdaQuery();

            // when
            UserInfo result = spyService.getByUserId(userId);

            // then
            assertThat(result).isEqualTo(dbInfo);
            verify(redisCache).set(any(RedisKeyBuild.class), eq(dbInfo));
        }

        @Test
        @DisplayName("缓存未命中 + DB不存在 → 抛出HmdpFrameException")
        void should_throw_when_userNotFoundInDB() {
            // given
            when(redisCache.get(any(RedisKeyBuild.class), eq(UserInfo.class))).thenReturn(null);
            UserInfoServiceImpl spyService = spy(userInfoService);
            doReturn(ChainWrapperMocks.lambdaQueryChain(Collections.emptyList()))
                    .when(spyService).lambdaQuery();

            // when & then
            assertThatThrownBy(() -> spyService.getByUserId(userId))
                    .isInstanceOf(HmdpFrameException.class);
        }
    }

    // ==================== updateUserLevel 测试 ====================

    @Nested
    @DisplayName("updateUserLevel - 更新用户等级")
    class UpdateUserLevelTests {

        @Test
        @DisplayName("参数非法(userId为null) → 返回失败")
        void should_fail_when_userIdIsNull() {
            // when
            Result<Void> result = userInfoService.updateUserLevel(null, 2);

            // then
            assertThat(result.getSuccess()).isEqualTo(false);
        }

        @Test
        @DisplayName("参数非法(newLevel为null) → 返回失败")
        void should_fail_when_newLevelIsNull() {
            // when
            Result<Void> result = userInfoService.updateUserLevel(userId, null);

            // then
            assertThat(result.getSuccess()).isEqualTo(false);
        }

        @Test
        @DisplayName("参数非法(newLevel<=0) → 返回失败")
        void should_fail_when_newLevelNonPositive() {
            // when
            Result<Void> result = userInfoService.updateUserLevel(userId, 0);

            // then
            assertThat(result.getSuccess()).isEqualTo(false);
        }

        @Test
        @DisplayName("用户不存在 → 返回失败")
        void should_fail_when_userInfoNotFound() {
            // given
            UserInfoServiceImpl spyService = spy(userInfoService);
            doReturn(ChainWrapperMocks.lambdaQueryChain(Collections.emptyList()))
                    .when(spyService).lambdaQuery();

            // when
            Result<Void> result = spyService.updateUserLevel(userId, 2);

            // then
            assertThat(result.getSuccess()).isEqualTo(false);
            assertThat((String) result.getErrorMsg()).contains("用户信息不存在");
        }

        @Test
        @DisplayName("等级相同 → 直接返回成功（无需更新）")
        void should_returnOk_when_sameLevel() {
            // given
            UserInfo existing = TestDataFactory.createUserInfoWithLevel(userId, 2);
            UserInfoServiceImpl spyService = spy(userInfoService);
            doReturn(ChainWrapperMocks.lambdaQueryChain(List.of(existing)))
                    .when(spyService).lambdaQuery();

            // when
            Result<Void> result = spyService.updateUserLevel(userId, 2);

            // then
            assertThat(result.getSuccess()).isEqualTo(true);
        }

        @Test
        @DisplayName("正常更新等级 → 更新DB、删缓存、维护Redis集合")
        void should_updateDbAndCache_when_validLevelChange() {
            // given
            UserInfo existing = TestDataFactory.createUserInfoWithLevel(userId, 1);
            UserInfoServiceImpl spyService = spy(userInfoService);
            doReturn(ChainWrapperMocks.lambdaQueryChain(List.of(existing)))
                    .when(spyService).lambdaQuery();
            doReturn(ChainWrapperMocks.lambdaUpdateChain(true))
                    .when(spyService).lambdaUpdate();

            // when
            Result<Void> result = spyService.updateUserLevel(userId, 3);

            // then
            assertThat(result.getSuccess()).isEqualTo(true);
            // 验证删除缓存
            verify(redisCache).del(any(RedisKeyBuild.class));
            // 验证从旧等级集合移除
            verify(redisCache).removeForSet(any(RedisKeyBuild.class), eq(userId));
            // 验证添加到新等级集合
            verify(redisCache).addForSet(any(RedisKeyBuild.class), eq(userId));
        }

        @Test
        @DisplayName("DB更新失败 → 返回失败")
        void should_fail_when_dbUpdateFails() {
            // given
            UserInfo existing = TestDataFactory.createUserInfoWithLevel(userId, 1);
            UserInfoServiceImpl spyService = spy(userInfoService);
            doReturn(ChainWrapperMocks.lambdaQueryChain(List.of(existing)))
                    .when(spyService).lambdaQuery();
            doReturn(ChainWrapperMocks.lambdaUpdateChain(false))
                    .when(spyService).lambdaUpdate();

            // when
            Result<Void> result = spyService.updateUserLevel(userId, 3);

            // then
            assertThat(result.getSuccess()).isEqualTo(false);
            assertThat((String) result.getErrorMsg()).contains("更新等级失败");
        }
    }

    // ==================== Mock辅助类 ====================

}