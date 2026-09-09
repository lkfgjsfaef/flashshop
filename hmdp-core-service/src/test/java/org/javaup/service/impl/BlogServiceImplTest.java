package org.javaup.service.impl;

import org.javaup.mapper.BlogMapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.javaup.dto.Result;
import org.javaup.dto.UserDTO;
import org.javaup.entity.Blog;
import org.javaup.entity.Follow;
import org.javaup.entity.User;
import org.javaup.service.IFollowService;
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
import org.springframework.data.redis.core.ZSetOperations;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BlogServiceImpl 单元测试
 * 覆盖场景：查询热门笔记、查询笔记详情、点赞/取消点赞、查询点赞用户、发布笔记、查询关注人笔记
 */
class BlogServiceImplTest extends BaseUnitTest {
    // ServiceImpl 的 baseMapper：不注入的话，走到 getById/list/save 等
    // 真实 DAO 方法时会抛 "baseMapper can not be null"
    @Mock
    private BlogMapper blogMapper;


    @Spy
    @InjectMocks
    private BlogServiceImpl blogService;

    @Mock
    private IUserService userService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private IFollowService followService;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private Long currentUserId = 1L;
    private Long blogId = 100L;
    private Blog testBlog;

    @BeforeEach
    void setUp() {
        UserHolder.saveUser(TestDataFactory.createUserDTO(currentUserId));
        testBlog = TestDataFactory.createBlog(currentUserId);
        testBlog.setId(blogId);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    // ==================== queryHotBlog 测试 ====================

    @Nested
    @DisplayName("queryHotBlog - 查询热门笔记")
    class QueryHotBlogTests {

        @Test
        @DisplayName("正常查询 → 返回分页结果")
        void should_returnPage_when_queryHotBlog() {
            // given
            Page<Blog> mockPage = new Page<>(1, 10);
            mockPage.setRecords(List.of(testBlog));
            // 生产代码是 query().orderByDesc("liked").page(new Page<>(...))，
            // page() 属于 ChainWrapper 而不是 Service，所以这里要 stub 的是 query()
            doReturn(ChainWrapperMocks.queryChain(mockPage)).when(blogService).query();
            when(userService.getById(anyLong())).thenReturn(TestDataFactory.createUser());
            when(zSetOperations.score(anyString(), anyString())).thenReturn(null);

            // when
            Result result = blogService.queryHotBlog(1);

            // then
            assertThat(result.getData()).isNotNull();
            List<Blog> blogs = (List<Blog>) result.getData();
            assertThat(blogs).hasSize(1);
        }

        @Test
        @DisplayName("无笔记 → 返回空列表")
        void should_returnEmptyList_when_noBlogs() {
            // given
            Page<Blog> mockPage = new Page<>(1, 10);
            mockPage.setRecords(Collections.emptyList());
            doReturn(ChainWrapperMocks.queryChain(mockPage)).when(blogService).query();

            // when
            Result result = blogService.queryHotBlog(1);

            // then
            assertThat(result.getData()).isEqualTo(Collections.emptyList());
        }
    }

    // ==================== queryBlogById 测试 ====================

    @Nested
    @DisplayName("queryBlogById - 查询笔记详情")
    class QueryBlogByIdTests {

        @Test
        @DisplayName("笔记存在 → 返回笔记详情")
        void should_returnBlog_when_blogExists() {
            // given
            doReturn(testBlog).when(blogService).getById(blogId);
            when(userService.getById(currentUserId)).thenReturn(TestDataFactory.createUser());
            when(zSetOperations.score(anyString(), anyString())).thenReturn(null);

            // when
            Result result = blogService.queryBlogById(blogId);

            // then
            assertThat(result.getData()).isEqualTo(testBlog);
        }

        @Test
        @DisplayName("笔记不存在 → 返回失败")
        void should_fail_when_blogNotFound() {
            // given
            doReturn(null).when(blogService).getById(blogId);

            // when
            Result result = blogService.queryBlogById(blogId);

            // then
            assertThat(result.getSuccess()).isEqualTo(false);
            assertThat(result.getErrorMsg()).isEqualTo("笔记不存在！");
        }
    }

    // ==================== likeBlog 测试 ====================

    @Nested
    @DisplayName("likeBlog - 点赞/取消点赞")
    class LikeBlogTests {

        @Test
        @DisplayName("未点赞 → 点赞成功")
        void should_like_when_notLikedBefore() {
            // given
            when(zSetOperations.score("blog:liked:" + blogId, currentUserId.toString())).thenReturn(null);
            doReturn(ChainWrapperMocks.updateChain(true)).when(blogService).update();

            // when
            Result result = blogService.likeBlog(blogId);

            // then
            assertThat(result.getSuccess()).isEqualTo(true);
            // verify 里要么全用真实值、要么全用 matcher，混用会报 InvalidUseOfMatchersException
            verify(zSetOperations).add(eq("blog:liked:" + blogId), eq(currentUserId.toString()), anyDouble());
        }

        @Test
        @DisplayName("已点赞 → 取消点赞")
        void should_unlike_when_alreadyLiked() {
            // given
            when(zSetOperations.score("blog:liked:" + blogId, currentUserId.toString())).thenReturn(1000.0);
            doReturn(ChainWrapperMocks.updateChain(true)).when(blogService).update();

            // when
            Result result = blogService.likeBlog(blogId);

            // then
            assertThat(result.getSuccess()).isEqualTo(true);
            verify(zSetOperations).remove("blog:liked:" + blogId, currentUserId.toString());
        }

        @Test
        @DisplayName("点赞失败（数据库更新失败）→ 不操作Redis")
        void should_notUpdateRedis_when_updateFails() {
            // given
            when(zSetOperations.score("blog:liked:" + blogId, currentUserId.toString())).thenReturn(null);
            doReturn(ChainWrapperMocks.updateChain(false)).when(blogService).update();

            // when
            Result result = blogService.likeBlog(blogId);

            // then
            assertThat(result.getSuccess()).isEqualTo(true); // 接口本身返回成功
            verify(zSetOperations, never()).add(anyString(), anyString(), anyDouble());
        }
    }

    // ==================== queryBlogLikes 测试 ====================

    @Nested
    @DisplayName("queryBlogLikes - 查询点赞用户")
    class QueryBlogLikesTests {

        @Test
        @DisplayName("有点赞用户 → 返回前5名")
        void should_returnTop5_when_hasLikes() {
            // given
            Set<String> top5 = Set.of("1", "2", "3", "4", "5");
            when(zSetOperations.range("blog:liked:" + blogId, 0, 4)).thenReturn(top5);

            List<User> mockUsers = List.of(
                    TestDataFactory.createUser(1L),
                    TestDataFactory.createUser(2L),
                    TestDataFactory.createUser(3L),
                    TestDataFactory.createUser(4L),
                    TestDataFactory.createUser(5L)
            );
            when(userService.query()).thenReturn(ChainWrapperMocks.queryChain(mockUsers));

            // when
            Result result = blogService.queryBlogLikes(blogId);

            // then
            assertThat(result.getData()).isNotNull();
            List<UserDTO> users = (List<UserDTO>) result.getData();
            assertThat(users).hasSize(5);
        }

        @Test
        @DisplayName("无点赞用户 → 返回空列表")
        void should_returnEmptyList_when_noLikes() {
            // given
            when(zSetOperations.range("blog:liked:" + blogId, 0, 4)).thenReturn(Collections.emptySet());

            // when
            Result result = blogService.queryBlogLikes(blogId);

            // then
            assertThat(result.getData()).isEqualTo(Collections.emptyList());
        }
    }

    // ==================== saveBlog 测试 ====================

    @Nested
    @DisplayName("saveBlog - 发布笔记")
    class SaveBlogTests {

        @Test
        @DisplayName("正常发布 → 保存笔记并推送给粉丝")
        void should_saveBlogAndPushToFollowers_when_saveSuccess() {
            // given
            Blog newBlog = TestDataFactory.createBlog(currentUserId);
            newBlog.setId(null);
            Long generatedId = TestDataFactory.nextId();
            when(snowflakeIdGenerator.nextId()).thenReturn(generatedId);
            doReturn(true).when(blogService).save(any(Blog.class));

            List<Follow> follows = List.of(
                    TestDataFactory.createFollow(2L, currentUserId),
                    TestDataFactory.createFollow(3L, currentUserId)
            );
            when(followService.query()).thenReturn(ChainWrapperMocks.queryChain(follows));

            // when
            Result result = blogService.saveBlog(newBlog);

            // then
            assertThat(result.getData()).isEqualTo(generatedId);
            verify(blogService).save(any(Blog.class));
            // 验证推送给2个粉丝
            verify(zSetOperations, times(2)).add(anyString(), anyString(), anyDouble());
        }

        @Test
        @DisplayName("保存失败 → 返回失败")
        void should_fail_when_saveFails() {
            // given
            Blog newBlog = TestDataFactory.createBlog(currentUserId);
            when(snowflakeIdGenerator.nextId()).thenReturn(TestDataFactory.nextId());
            doReturn(false).when(blogService).save(any(Blog.class));

            // when
            Result result = blogService.saveBlog(newBlog);

            // then
            assertThat(result.getSuccess()).isEqualTo(false);
            assertThat(result.getErrorMsg()).isEqualTo("新增笔记失败!");
        }
    }

    // ==================== Mock辅助类 ====================

    /**
     * MyBatis-Plus UpdateWrapper 的简单Mock
     */
    /**
     * MyBatis-Plus QueryWrapper 的简单Mock
     */
}