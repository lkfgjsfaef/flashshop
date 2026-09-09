package org.javaup.controller;

import org.springframework.context.annotation.Import;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.javaup.dto.Result;
import org.javaup.dto.UserDTO;
import org.javaup.entity.Blog;
import org.javaup.service.IBlogService;
import org.javaup.test.TestDataFactory;
import org.javaup.utils.UserHolder;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * BlogController 切片测试
 */
@WebMvcTest(BlogController.class)
@Import(org.javaup.test.MockMapperConfig.class)
class BlogControllerTest extends BaseWebMvcTest {


    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IBlogService blogService;

    @Autowired
    private ObjectMapper objectMapper;

    private Long blogId = 100L;
    private Long userId = 1L;

    @BeforeEach
    void setUp() {
        UserHolder.saveUser(TestDataFactory.createUserDTO(userId));
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    // ==================== POST /blog 测试 ====================

    @Nested
    @DisplayName("POST /blog - 发布笔记")
    class SaveBlogTests {

        @Test
        @DisplayName("正常发布 → 返回200和笔记ID")
        void should_return200_when_saveSuccess() throws Exception {
            // given
            Blog blog = TestDataFactory.createBlog(userId);
            blog.setId(null);
            when(blogService.saveBlog(any(Blog.class))).thenReturn(Result.ok(blogId));

            // when & then
            mockMvc.perform(post("/blog")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(blog)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(blogId));
        }
    }

    // ==================== PUT /blog/like/{id} 测试 ====================

    @Nested
    @DisplayName("PUT /blog/like/{id} - 点赞/取消点赞")
    class LikeBlogTests {

        @Test
        @DisplayName("点赞 → 返回200")
        void should_return200_when_likeSuccess() throws Exception {
            // given
            when(blogService.likeBlog(blogId)).thenReturn(Result.ok());

            // when & then
            mockMvc.perform(put("/blog/like/{id}", blogId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    // ==================== GET /blog/hot 测试 ====================

    @Nested
    @DisplayName("GET /blog/hot - 热门笔记")
    class QueryHotBlogTests {

        @Test
        @DisplayName("查询热门笔记 → 返回200和分页数据")
        void should_return200_when_queryHotBlog() throws Exception {
            // given
            when(blogService.queryHotBlog(1)).thenReturn(Result.ok(List.of()));

            // when & then
            mockMvc.perform(get("/blog/hot"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    // ==================== GET /blog/{id} 测试 ====================

    @Nested
    @DisplayName("GET /blog/{id} - 查询笔记详情")
    class QueryBlogByIdTests {

        @Test
        @DisplayName("笔记存在 → 返回200和笔记详情")
        void should_return200_when_blogExists() throws Exception {
            // given
            Blog blog = TestDataFactory.createBlog(userId);
            blog.setId(blogId);
            when(blogService.queryBlogById(blogId)).thenReturn(Result.ok(blog));

            // when & then
            mockMvc.perform(get("/blog/{id}", blogId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(blogId));
        }

        @Test
        @DisplayName("笔记不存在 → 返回失败")
        void should_fail_when_blogNotFound() throws Exception {
            // given
            when(blogService.queryBlogById(blogId)).thenReturn(Result.fail("笔记不存在！"));

            // when & then
            mockMvc.perform(get("/blog/{id}", blogId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errorMsg").value("笔记不存在！"));
        }
    }

    // ==================== GET /blog/likes/{id} 测试 ====================

    @Nested
    @DisplayName("GET /blog/likes/{id} - 查询点赞用户")
    class QueryBlogLikesTests {

        @Test
        @DisplayName("查询点赞用户 → 返回200")
        void should_return200_when_queryBlogLikes() throws Exception {
            // given
            when(blogService.queryBlogLikes(blogId)).thenReturn(Result.ok(List.of()));

            // when & then
            mockMvc.perform(get("/blog/likes/{id}", blogId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    // ==================== GET /blog/of/me 测试 ====================

    @Nested
    @DisplayName("GET /blog/of/me - 我的笔记")
    class QueryMyBlogTests {

        @Test
        @DisplayName("查询我的笔记 → 返回200")
        void should_return200_when_queryMyBlog() throws Exception {
            // given：controller 里是 blogService.query().eq("user_id", ...).page(...)，
            // @MockBean 默认返回 null 会 NPE，必须 stub 链式查询返回一个分页结果
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<Blog> page =
                    new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
            page.setRecords(List.of(TestDataFactory.createBlog(userId)));
            when(blogService.query()).thenReturn(org.javaup.test.ChainWrapperMocks.queryChain(page));

            // when & then
            mockMvc.perform(get("/blog/of/me")
                            .param("current", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }
}