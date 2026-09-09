package org.javaup.service.impl;

import org.springframework.data.redis.core.StringRedisTemplate;

import org.javaup.mapper.ShopMapper;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.javaup.core.RedisKeyManage;
import org.javaup.dto.Result;
import org.javaup.entity.Shop;
import org.javaup.handler.BloomFilterHandler;
import org.javaup.handler.BloomFilterHandlerFactory;
import org.javaup.redis.RedisCacheImpl;
import org.javaup.redis.RedisKeyBuild;
import org.javaup.servicelock.LockType;
import org.javaup.test.BaseUnitTest;
import org.javaup.test.TestDataFactory;
import org.javaup.toolkit.SnowflakeIdGenerator;
import org.javaup.util.ServiceLockTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.redisson.api.RLock;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ShopServiceImpl 单元测试
 * 覆盖场景：查询商铺（缓存命中/未命中/布隆过滤器拦截/空值缓存防穿透）、创建商铺、更新商铺
 */
class ShopServiceImplTest extends BaseUnitTest {

    /**
     * 布隆过滤器默认桩。
     *
     * BloomFilterHandlerFactory 是 @Mock，get() 默认返回 null，
     * 生产代码拿到后直接调 contains()/add() 就 NPE。
     * 这里给一个默认实现；需要特定行为（比如 contains 返回 false）的用例，
     * 在自己内部再 stub 一次就会覆盖掉这里。
     */
    @BeforeEach
    void setUpBloomFilter() {
        org.javaup.handler.BloomFilterHandler handler =
                mock(org.javaup.handler.BloomFilterHandler.class);
        when(handler.contains(anyString())).thenReturn(true);
        when(handler.add(anyString())).thenReturn(true);
        when(bloomFilterHandlerFactory.get(anyString())).thenReturn(handler);
    }

    // ServiceImpl 的 baseMapper：不注入的话，走到 getById/list/save 等
    // 真实 DAO 方法时会抛 "baseMapper can not be null"
    // ShopServiceImpl.update() 会删 Redis 缓存，需要这个依赖
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ShopMapper shopMapper;


    @Spy
    @InjectMocks
    private ShopServiceImpl shopService;

    @Mock
    private RedisCacheImpl redisCache;

    @Mock
    private ServiceLockTool serviceLockTool;

    @Mock
    private BloomFilterHandlerFactory bloomFilterHandlerFactory;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private RLock rLock;

    private Shop testShop;
    private Long shopId;

    @BeforeEach
    void setUp() {
        shopId = TestDataFactory.nextId();
        testShop = TestDataFactory.createShop(shopId);
    }

    // ==================== queryById 测试 ====================

    @Nested
    @DisplayName("queryById - 查询商铺")
    class QueryByIdTests {

        @Test
        @DisplayName("Redis缓存命中 → 直接返回")
        void should_returnShop_when_redisCacheHit() {
            // given
            when(redisCache.get(any(RedisKeyBuild.class), eq(Shop.class))).thenReturn(testShop);

            // when
            Result result = shopService.queryById(shopId);

            // then
            assertThat(result.getData()).isEqualTo(testShop);
            verify(redisCache).get(any(RedisKeyBuild.class), eq(Shop.class));
            // 不应该查询DB
            verify(shopService, never()).getById(anyLong());
        }

        @Test
        @DisplayName("布隆过滤器判断不存在 → 直接抛异常")
        void should_throw_when_bloomFilterSaysNotExist() {
            // given
            when(redisCache.get(any(RedisKeyBuild.class), eq(Shop.class))).thenReturn(null);
            BloomFilterHandler mockBloomFilter = mock(BloomFilterHandler.class);
            when(mockBloomFilter.contains(anyString())).thenReturn(false);
            when(bloomFilterHandlerFactory.get(anyString())).thenReturn(mockBloomFilter);

            // when & then
            assertThatThrownBy(() -> shopService.queryById(shopId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("查询商铺不存在");
        }

        @Test
        @DisplayName("空值缓存存在 → 直接抛异常")
        void should_throw_when_nullValueCacheExists() {
            // given
            when(redisCache.get(any(RedisKeyBuild.class), eq(Shop.class))).thenReturn(null);
            BloomFilterHandler mockBloomFilter = mock(BloomFilterHandler.class);
            when(mockBloomFilter.contains(anyString())).thenReturn(true);
            when(bloomFilterHandlerFactory.get(anyString())).thenReturn(mockBloomFilter);
            when(redisCache.hasKey(any(RedisKeyBuild.class))).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> shopService.queryById(shopId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("查询商铺不存在");
        }

        @Test
        @DisplayName("DB查询为空 → 写入空值缓存并抛异常")
        void should_writeNullCache_when_dbReturnsNull() {
            // given
            when(redisCache.get(any(RedisKeyBuild.class), eq(Shop.class))).thenReturn(null);
            BloomFilterHandler mockBloomFilter = mock(BloomFilterHandler.class);
            when(mockBloomFilter.contains(anyString())).thenReturn(true);
            when(bloomFilterHandlerFactory.get(anyString())).thenReturn(mockBloomFilter);
            when(redisCache.hasKey(any(RedisKeyBuild.class))).thenReturn(false);
            when(serviceLockTool.getLock(any(LockType.class), anyString(), any(String[].class))).thenReturn(rLock);
            when(rLock.tryLock()).thenReturn(true);
            // 模拟double-check后Redis仍为空
            when(redisCache.get(any(RedisKeyBuild.class), eq(Shop.class))).thenReturn(null);
            doReturn(null).when(shopService).getById(shopId);

            // when & then
            assertThatThrownBy(() -> shopService.queryById(shopId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("查询商铺不存在");

            // 验证写入了空值缓存
            verify(redisCache).set(any(RedisKeyBuild.class), anyString(), anyLong(), any(TimeUnit.class));
        }

        @Test
        @DisplayName("DB查询命中 → 写入Redis并返回")
        void should_returnShopAndWriteCache_when_dbHit() {
            // given
            when(redisCache.get(any(RedisKeyBuild.class), eq(Shop.class))).thenReturn(null);
            BloomFilterHandler mockBloomFilter = mock(BloomFilterHandler.class);
            when(mockBloomFilter.contains(anyString())).thenReturn(true);
            when(bloomFilterHandlerFactory.get(anyString())).thenReturn(mockBloomFilter);
            when(redisCache.hasKey(any(RedisKeyBuild.class))).thenReturn(false);
            when(serviceLockTool.getLock(any(LockType.class), anyString(), any(String[].class))).thenReturn(rLock);
            when(rLock.tryLock()).thenReturn(true);
            // double-check: 第一次返回null，第二次（从DB查到后）返回shop
            when(redisCache.get(any(RedisKeyBuild.class), eq(Shop.class)))
                    .thenReturn(null)  // 第一次查询Redis
                    .thenReturn(null); // double-check后仍为null
            doReturn(testShop).when(shopService).getById(shopId);

            // when
            Result result = shopService.queryById(shopId);

            // then
            assertThat(result.getData()).isEqualTo(testShop);
            // 验证写入了Redis缓存
            verify(redisCache).set(any(RedisKeyBuild.class), eq(testShop), anyLong(), any(TimeUnit.class));
        }

        @Test
        @DisplayName("DB查询返回null → 写入空值缓存")
        void should_writeNullCache_when_shopNotFoundInDB() {
            // given
            when(redisCache.get(any(RedisKeyBuild.class), eq(Shop.class))).thenReturn(null);
            BloomFilterHandler mockBloomFilter = mock(BloomFilterHandler.class);
            when(mockBloomFilter.contains(anyString())).thenReturn(true);
            when(bloomFilterHandlerFactory.get(anyString())).thenReturn(mockBloomFilter);
            when(redisCache.hasKey(any(RedisKeyBuild.class))).thenReturn(false);
            when(serviceLockTool.getLock(any(LockType.class), anyString(), any(String[].class))).thenReturn(rLock);
            when(rLock.tryLock()).thenReturn(true);
            doReturn(null).when(shopService).getById(shopId);

            // when & then
            assertThatThrownBy(() -> shopService.queryById(shopId))
                    .isInstanceOf(RuntimeException.class);

            // 验证写入了空值缓存
            verify(redisCache).set(any(RedisKeyBuild.class), eq("这是一个空值"), anyLong(), any(TimeUnit.class));
        }
    }

    // ==================== saveShop 测试 ====================

    @Nested
    @DisplayName("saveShop - 创建商铺")
    class SaveShopTests {

        @Test
        @DisplayName("正常创建商铺 → 返回商铺ID")
        void should_returnShopId_when_saveSuccess() {
            // given
            Shop newShop = TestDataFactory.createShop();
            newShop.setId(null);
            Long generatedId = TestDataFactory.nextId();
            when(snowflakeIdGenerator.nextId()).thenReturn(generatedId);
            doReturn(true).when(shopService).save(any(Shop.class));

            // when
            Result<Long> result = shopService.saveShop(newShop);

            // then
            assertThat(result.getData()).isEqualTo(generatedId);
            verify(shopService).save(any(Shop.class));
            verify(bloomFilterHandlerFactory.get(anyString())).add(String.valueOf(generatedId));
        }
    }

    // ==================== update 测试 ====================

    @Nested
    @DisplayName("update - 更新商铺")
    class UpdateTests {

        @Test
        @DisplayName("id为空 → 返回失败")
        void should_fail_when_idIsNull() {
            // given
            Shop shop = TestDataFactory.createShop();
            shop.setId(null);

            // when
            Result result = shopService.update(shop);

            // then
            assertThat(result.getSuccess()).isEqualTo(false);
        }

        @Test
        @DisplayName("正常更新 → 返回成功并删除缓存")
        void should_success_when_updateValid() {
            // given
            doReturn(true).when(shopService).updateById(any(Shop.class));

            // when
            Result result = shopService.update(testShop);

            // then
            assertThat(result.getSuccess()).isEqualTo(true);
            verify(shopService).updateById(testShop);
        }
    }

}