package org.javaup.controller;

import org.javaup.test.TestDataFactory;
import org.javaup.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Controller 切片测试（@WebMvcTest）的公共基类。
 *
 * 解决两个 @WebMvcTest 的常见问题：
 *
 * 1. MvcConfig 注入了 StringRedisTemplate（token 刷新拦截器要用），
 *    而切片测试不加载 Redis 自动配置 → 这里补一个 @MockBean。
 *
 * 2. LoginInterceptor 会检查 ThreadLocal 里有没有登录用户，没有就返回 401，
 *    导致所有接口都压根进不到 Controller。这里在 @BeforeEach 里塞一个
 *    模拟登录用户，让用例能专注验证参数绑定与响应封装。
 *    （比把拦截器整个 mock 掉更真实，也能顺带覆盖到拦截器的放行分支。）
 *
 * 如果某个用例要专门验证「未登录被拦截」，在用例里先 UserHolder.removeUser() 即可。
 */
@AutoConfigureMockMvc(addFilters = false)
public abstract class BaseWebMvcTest {

    @MockBean
    protected StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUpLoginUser() {
        UserHolder.saveUser(TestDataFactory.createUserDTO(1L));
    }

    @AfterEach
    void clearLoginUser() {
        UserHolder.removeUser();
    }
}
