package org.javaup.api;

import org.javaup.entity.Blog;
import org.javaup.test.TestDataFactory;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Blog API 自动化测试
 * 测试笔记相关的所有接口
 */
@DisplayName("Blog API 自动化测试")
class BlogApiTest extends BaseApiTest {

    // ==================== GET /blog/hot 测试 ====================

    @Nested
    @DisplayName("GET /blog/hot - 热门笔记")
    class QueryHotBlogTests {

        @Test
        @DisplayName("查询热门笔记 → 返回200")
        void should_return200_when_queryHotBlog() {
            // when
            var response = getWithQueryParam("/blog/hot", "current", 1);

            // then
            assertSuccess(response);
        }
    }

    // ==================== GET /blog/{id} 测试 ====================

    @Nested
    @DisplayName("GET /blog/{id} - 查询笔记详情")
    class QueryBlogByIdTests {

        @Test
        @DisplayName("查询不存在的笔记 → 返回失败")
        void should_fail_when_blogNotFound() {
            // when
            var response = get("/blog/{id}", 999999L);

            // then
            assertFailure(response);
        }
    }

    // ==================== GET /blog/likes/{id} 测试 ====================

    @Nested
    @DisplayName("GET /blog/likes/{id} - 查询点赞用户")
    class QueryBlogLikesTests {

        @Test
        @DisplayName("查询笔记点赞用户 → 返回200")
        void should_return200_when_queryBlogLikes() {
            // when
            var response = get("/blog/likes/{id}", 999999L);

            // then
            assertSuccess(response);
        }
    }

    // ==================== GET /blog/of/user 测试 ====================

    @Nested
    @DisplayName("GET /blog/of/user - 查询用户笔记")
    class QueryBlogByUserIdTests {

        @Test
        @DisplayName("查询用户笔记 → 返回200")
        void should_return200_when_queryBlogByUserId() {
            // when
            var response = getWithQueryParam("/blog/of/user", "id", 1);

            // then
            assertSuccess(response);
        }
    }
}