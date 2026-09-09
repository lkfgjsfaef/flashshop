package org.javaup.controller;

import org.springframework.context.annotation.Import;

import org.javaup.dto.Result;
import org.javaup.service.IFollowService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FollowController 切片测试
 */
@WebMvcTest(FollowController.class)
@Import(org.javaup.test.MockMapperConfig.class)
class FollowControllerTest extends BaseWebMvcTest {


    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IFollowService followService;

    private Long followUserId = 200L;

    // ==================== PUT /follow/{id}/{isFollow} 测试 ====================

    @Nested
    @DisplayName("PUT /follow/{id}/{isFollow} - 关注/取关")
    class FollowTests {

        @Test
        @DisplayName("关注 → 返回200")
        void should_return200_when_follow() throws Exception {
            // given
            when(followService.follow(followUserId, true)).thenReturn(Result.ok());

            // when & then
            mockMvc.perform(put("/follow/{id}/{isFollow}", followUserId, true))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("取关 → 返回200")
        void should_return200_when_unfollow() throws Exception {
            // given
            when(followService.follow(followUserId, false)).thenReturn(Result.ok());

            // when & then
            mockMvc.perform(put("/follow/{id}/{isFollow}", followUserId, false))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    // ==================== GET /follow/or/not/{id} 测试 ====================

    @Nested
    @DisplayName("GET /follow/or/not/{id} - 查询是否关注")
    class IsFollowTests {

        @Test
        @DisplayName("已关注 → 返回true")
        void should_returnTrue_when_following() throws Exception {
            // given
            when(followService.isFollow(followUserId)).thenReturn(Result.ok(true));

            // when & then
            mockMvc.perform(get("/follow/or/not/{id}", followUserId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(true));
        }

        @Test
        @DisplayName("未关注 → 返回false")
        void should_returnFalse_when_notFollowing() throws Exception {
            // given
            when(followService.isFollow(followUserId)).thenReturn(Result.ok(false));

            // when & then
            mockMvc.perform(get("/follow/or/not/{id}", followUserId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(false));
        }
    }

    // ==================== GET /follow/common/{id} 测试 ====================

    @Nested
    @DisplayName("GET /follow/common/{id} - 共同关注")
    class FollowCommonsTests {

        @Test
        @DisplayName("有共同关注 → 返回共同关注列表")
        void should_returnCommonFollows_when_exists() throws Exception {
            // given
            when(followService.followCommons(followUserId)).thenReturn(Result.ok(List.of()));

            // when & then
            mockMvc.perform(get("/follow/common/{id}", followUserId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }
}