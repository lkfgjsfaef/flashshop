package org.javaup;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

/**
 * Redisson 分布式锁的手工验证用例（可重入性验证）。
 *
 * 这是集成测试：@SpringBootTest 会拉起完整上下文并连接真实的 Redis + MySQL，
 * 本地没有这些中间件时无法运行，所以默认禁用。需要验证时手动去掉 @Disabled。
 *
 * 注：这里没有用 Lombok 的 @Slf4j，显式声明 Logger，
 * 避免注解处理器未生效时编译报「找不到符号 log」。
 */
@Disabled("需要真实 Redis 环境，本地无中间件时跳过")
@SpringBootTest
class RedissonTest {

    private static final Logger log = LoggerFactory.getLogger(RedissonTest.class);

    @Resource
    private RedissonClient redissonClient;

    private RLock lock;

    @BeforeEach
    void setUp() {
        lock = redissonClient.getLock("order");
    }

    @Test
    void method1() throws InterruptedException {
        // 尝试获取锁
        boolean isLock = lock.tryLock(1L, TimeUnit.SECONDS);
        if (!isLock) {
            log.error("获取锁失败 .... 1");
            return;
        }
        try {
            log.info("获取锁成功 .... 1");
            method2();
            log.info("开始执行业务 ... 1");
        } finally {
            log.warn("准备释放锁 .... 1");
            lock.unlock();
        }
    }
    void method2() {
        // 尝试获取锁
        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.error("获取锁失败 .... 2");
            return;
        }
        try {
            log.info("获取锁成功 .... 2");
            log.info("开始执行业务 ... 2");
        } finally {
            log.warn("准备释放锁 .... 2");
            lock.unlock();
        }
    }
}