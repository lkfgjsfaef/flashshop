package org.javaup.service.impl;

import org.javaup.mapper.FollowMapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.javaup.dto.Result;
import org.javaup.dto.UserDTO;
import org.javaup.entity.Follow;
import org.javaup.entity.User;
import org.javaup.service.IUserService;
import org.javaup.test.BaseUnitTest;
import org.javaup.test.ChainWrapperMocks;
import org.javaup.test.TestDataFactory;
import org.javaup.toolkit.SnowflakeIdGenerator;
import org.javaup.utils.UserHolder;
import org.junit.jupiter.api.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.SetOperations;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FollowServiceImpl 单元测试
 * 覆盖场景：关注/取关、查询是否关注、查询共同关注
 */
class FollowServiceImplTest extends BaseUnitTest {
    // ServiceImpl 的 baseMapper：不注入的话，走到 getById/list/save 等
    // 真实 DAO 方法时会抛 "baseMapper can not be null"
    @Mock
    private FollowMapper followMapper;


    @Spy
    @InjectMocks
    private FollowServiceImpl followService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private IUserService userService;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private SetOperations<String, String> setOperations;

    private Long currentUserId = 1L;
    private Long followUserId = 2L;

    @BeforeEach
    void setUp() {
        UserHolder.saveUser(TestDataFactory.createUserDTO(currentUserId));
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    // ==================== follow 测试 ====================

    @Nested
    @DisplayName("follow - 关注/取关")
    class FollowTests {

        @Test
        @DisplayName("关注用户 → 保存到数据库并添加到Redis集合")
        void should_saveFollowAndAddToRedis_when_follow() {
            // given
            Long generatedId = TestDataFactory.nextId();
            when(snowflakeIdGenerator.nextId()).thenReturn(generatedId);
            doReturn(true).when(followService).save(any(Follow.class));

            // when
            Result result = followService.follow(followUserId, true);

            // then
            assertThat(result.getSuccess()).isEqualTo(true);
            verify(followService).save(any(Follow.class));
            verify(setOperations).add("follows:" + currentUserId, followUserId.toString());
        }

        @Test
        @DisplayName("取关用户 → 从数据库删除并从Redis集合移除")
        void should_removeFollowAndRemoveFromRedis_when_unfollow() {
            // given
            doReturn(true).when(followService).remove(any(QueryWrapper.class));

            // when
            Result result = followService.follow(followUserId, false);

            // then
            assertThat(result.getSuccess()).isEqualTo(true);
            verify(followService).remove(any(QueryWrapper.class));
            verify(setOperations).remove("follows:" + currentUserId, followUserId.toString());
        }

        @Test
        @DisplayName("关注失败（数据库保存失败）→ 不添加到Redis")
        void should_notAddToRedis_when_saveFollowFails() {
            // given
            when(snowflakeIdGenerator.nextId()).thenReturn(TestDataFactory.nextId());
            doReturn(false).when(followService).save(any(Follow.class));

            // when
            Result result = followService.follow(followUserId, true);

            // then
            assertThat(result.getSuccess()).isEqualTo(true); // 关注接口本身返回成功
            verify(setOperations, never()).add(anyString(), anyString());
        }

        @Test
        @DisplayName("取关失败（数据库删除失败）→ 不从Redis移除")
        void should_notRemoveFromRedis_when_removeFollowFails() {
            // given
            doReturn(false).when(followService).remove(any(QueryWrapper.class));

            // when
            Result result = followService.follow(followUserId, false);

            // then
            assertThat(result.getSuccess()).isEqualTo(true);
            verify(setOperations, never()).remove(anyString(), anyString());
        }
    }

    // ==================== isFollow 测试 ====================

    @Nested
    @DisplayName("isFollow - 查询是否关注")
    class IsFollowTests {

        @Test
        @DisplayName("已关注 → 返回true")
        void should_returnTrue_when_alreadyFollowed() {
            // given
            doReturn(ChainWrapperMocks.queryChain(1L)).when(followService).query();

            // when
            Result result = followService.isFollow(followUserId);

            // then
            assertThat(result.getData()).isEqualTo(true);
        }

        @Test
        @DisplayName("未关注 → 返回false")
        void should_returnFalse_when_notFollowed() {
            // given
            doReturn(ChainWrapperMocks.queryChain(0L)).when(followService).query();

            // when
            Result result = followService.isFollow(followUserId);

            // then
            assertThat(result.getData()).isEqualTo(false);
        }
    }

    // ==================== followCommons 测试 ====================

    @Nested
    @DisplayName("followCommons - 查询共同关注")
    class FollowCommonsTests {

        @Test
        @DisplayName("有共同关注 → 返回用户列表")
        void should_returnUsers_when_hasCommonFollows() {
            // given
            Long targetUserId = 3L;
            Set<String> commonFollowIds = Set.of("4", "5");
            when(setOperations.intersect("follows:" + currentUserId, "follows:" + targetUserId))
                    .thenReturn(commonFollowIds);

            List<User> mockUsers = List.of(
                    TestDataFactory.createUser(4L),
                    TestDataFactory.createUser(5L)
            );
            when(userService.listByIds(anyList())).thenReturn(mockUsers);

            // when
            Result result = followService.followCommons(targetUserId);

            // then
            assertThat(result.getData()).isNotNull();
            List<UserDTO> users = (List<UserDTO>) result.getData();
            assertThat(users).hasSize(2);
        }

        @Test
        @DisplayName("无共同关注 → 返回空列表")
        void should_returnEmptyList_when_noCommonFollows() {
            // given
            Long targetUserId = 3L;
            when(setOperations.intersect("follows:" + currentUserId, "follows:" + targetUserId))
                    .thenReturn(Collections.emptySet());

            // when
            Result result = followService.followCommons(targetUserId);

            // then
            assertThat(result.getData()).isEqualTo(Collections.emptyList());
        }

        @Test
        @DisplayName("Redis返回null → 返回空列表")
        void should_returnEmptyList_when_redisReturnsNull() {
            // given
            Long targetUserId = 3L;
            when(setOperations.intersect("follows:" + currentUserId, "follows:" + targetUserId))
                    .thenReturn(null);

            // when
            Result result = followService.followCommons(targetUserId);

            // then
            assertThat(result.getData()).isEqualTo(Collections.emptyList());
        }
    }

    // ==================== Mock辅助类 ====================

    /**
     * MyBatis-Plus QueryWrapper 的简单Mock
     */
}