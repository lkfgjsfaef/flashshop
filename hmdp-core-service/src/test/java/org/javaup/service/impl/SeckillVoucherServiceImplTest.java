package org.javaup.service.impl;

import cn.hutool.core.date.LocalDateTimeUtil;
import org.javaup.cache.SeckillVoucherLocalCache;
import org.javaup.core.RedisKeyManage;
import org.javaup.entity.SeckillVoucher;
import org.javaup.entity.Voucher;
import org.javaup.handler.BloomFilterHandler;
import org.javaup.handler.BloomFilterHandlerFactory;
import org.javaup.mapper.SeckillVoucherMapper;
import org.javaup.model.SeckillVoucherFullModel;
import org.javaup.redis.RedisCacheImpl;
import org.javaup.redis.RedisKeyBuild;
import org.javaup.service.IVoucherService;
import org.javaup.servicelock.LockType;
import org.javaup.test.BaseUnitTest;
import org.javaup.test.ChainWrapperMocks;
import org.javaup.test.TestDataFactory;
import org.javaup.util.ServiceLockTool;
import org.junit.jupiter.api.*;
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
 * SeckillVoucherServiceImpl 单元测试
 * 覆盖场景：查询秒杀优惠券（多级缓存）、加载库存、回滚库存
 */
class SeckillVoucherServiceImplTest extends BaseUnitTest {

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


    @Spy
    @InjectMocks
    private SeckillVoucherServiceImpl seckillVoucherService;

    @Mock
    private ServiceLockTool serviceLockTool;

    @Mock
    private RedisCacheImpl redisCache;

    @Mock
    private BloomFilterHandlerFactory bloomFilterHandlerFactory;

    @Mock
    private SeckillVoucherLocalCache seckillVoucherLocalCache;

    @Mock
    private SeckillVoucherMapper seckillVoucherMapper;

    @Mock
    private IVoucherService voucherService;

    @Mock
    private RLock rLock;

    private Long voucherId = 100L;
    private SeckillVoucher testSeckillVoucher;
    private SeckillVoucherFullModel testModel;

    @BeforeEach
    void setUp() {
        testSeckillVoucher = TestDataFactory.createSeckillVoucher(voucherId);
        testModel = TestDataFactory.createSeckillVoucherFullModel(voucherId);
        when(serviceLockTool.getLock(any(LockType.class), anyString(), any(String[].class))).thenReturn(rLock);
    }

    // ==================== queryByVoucherId 测试 ====================

    @Nested
    @DisplayName("queryByVoucherId - 查询秒杀优惠券")
    class QueryByVoucherIdTests {

        @Test
        @DisplayName("本地缓存命中 → 直接返回")
        void should_returnModel_when_localCacheHit() {
            // given
            when(seckillVoucherLocalCache.get(anyString())).thenReturn(testModel);

            // when
            SeckillVoucherFullModel result = seckillVoucherService.queryByVoucherId(voucherId);

            // then
            assertThat(result).isEqualTo(testModel);
            verify(redisCache, never()).get(any(RedisKeyBuild.class), any());
        }

        @Test
        @DisplayName("Redis缓存命中 → 写入本地缓存并返回")
        void should_returnModelAndWriteLocalCache_when_redisCacheHit() {
            // given
            when(seckillVoucherLocalCache.get(anyString())).thenReturn(null);
            when(redisCache.get(any(RedisKeyBuild.class), eq(SeckillVoucherFullModel.class))).thenReturn(testModel);

            // when
            SeckillVoucherFullModel result = seckillVoucherService.queryByVoucherId(voucherId);

            // then
            assertThat(result).isEqualTo(testModel);
            verify(seckillVoucherLocalCache).put(anyString(), eq(testModel));
        }

        @Test
        @DisplayName("布隆过滤器判断不存在 → 抛出异常")
        void should_throw_when_bloomFilterSaysNotExist() {
            // given
            when(seckillVoucherLocalCache.get(anyString())).thenReturn(null);
            when(redisCache.get(any(RedisKeyBuild.class), eq(SeckillVoucherFullModel.class))).thenReturn(null);
            BloomFilterHandler mockBloomFilter = mock(BloomFilterHandler.class);
            when(mockBloomFilter.contains(anyString())).thenReturn(false);
            when(bloomFilterHandlerFactory.get(anyString())).thenReturn(mockBloomFilter);

            // when & then
            assertThatThrownBy(() -> seckillVoucherService.queryByVoucherId(voucherId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("查询秒杀优惠券不存在");
        }

        @Test
        @DisplayName("空值缓存存在 → 抛出异常")
        void should_throw_when_nullValueCacheExists() {
            // given
            when(seckillVoucherLocalCache.get(anyString())).thenReturn(null);
            when(redisCache.get(any(RedisKeyBuild.class), eq(SeckillVoucherFullModel.class))).thenReturn(null);
            BloomFilterHandler mockBloomFilter = mock(BloomFilterHandler.class);
            when(mockBloomFilter.contains(anyString())).thenReturn(true);
            when(bloomFilterHandlerFactory.get(anyString())).thenReturn(mockBloomFilter);
            when(redisCache.hasKey(any(RedisKeyBuild.class))).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> seckillVoucherService.queryByVoucherId(voucherId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("查询秒杀优惠券不存在");
        }

        @Test
        @DisplayName("DB查询命中 → 写入多级缓存并返回")
        void should_returnModelAndWriteCaches_when_dbHit() {
            // given
            when(seckillVoucherLocalCache.get(anyString())).thenReturn(null);
            when(redisCache.get(any(RedisKeyBuild.class), eq(SeckillVoucherFullModel.class))).thenReturn(null);
            BloomFilterHandler mockBloomFilter = mock(BloomFilterHandler.class);
            when(mockBloomFilter.contains(anyString())).thenReturn(true);
            when(bloomFilterHandlerFactory.get(anyString())).thenReturn(mockBloomFilter);
            when(redisCache.hasKey(any(RedisKeyBuild.class))).thenReturn(false);
            doReturn(ChainWrapperMocks.lambdaQueryChain(testSeckillVoucher)).when(seckillVoucherService).lambdaQuery();
            Voucher voucher = TestDataFactory.createVoucher(voucherId);
            when(voucherService.lambdaQuery()).thenReturn(ChainWrapperMocks.lambdaQueryChain(voucher));

            // when
            SeckillVoucherFullModel result = seckillVoucherService.queryByVoucherId(voucherId);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getVoucherId()).isEqualTo(voucherId);
            verify(redisCache).set(any(RedisKeyBuild.class), any(SeckillVoucherFullModel.class), anyLong(), any(TimeUnit.class));
            verify(seckillVoucherLocalCache).put(anyString(), any(SeckillVoucherFullModel.class));
        }

        @Test
        @DisplayName("DB查询为空 → 写入空值缓存并抛出异常")
        void should_throwAndWriteNullCache_when_dbReturnsNull() {
            // given
            when(seckillVoucherLocalCache.get(anyString())).thenReturn(null);
            when(redisCache.get(any(RedisKeyBuild.class), eq(SeckillVoucherFullModel.class))).thenReturn(null);
            BloomFilterHandler mockBloomFilter = mock(BloomFilterHandler.class);
            when(mockBloomFilter.contains(anyString())).thenReturn(true);
            when(bloomFilterHandlerFactory.get(anyString())).thenReturn(mockBloomFilter);
            when(redisCache.hasKey(any(RedisKeyBuild.class))).thenReturn(false);
            doReturn(ChainWrapperMocks.lambdaQueryChain(null)).when(seckillVoucherService).lambdaQuery();

            // when & then
            assertThatThrownBy(() -> seckillVoucherService.queryByVoucherId(voucherId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("查询秒杀优惠券不存在");

            // 验证写入了空值缓存
            verify(redisCache).set(any(RedisKeyBuild.class), eq("这是一个空值"), anyLong(), any(TimeUnit.class));
        }
    }

    // ==================== loadVoucherStock 测试 ====================

    @Nested
        @DisplayName("loadVoucherStock - 加载库存")
        class LoadVoucherStockTests {

            @Test
            @DisplayName("Redis库存已存在 → 直接返回")
            void should_return_when_stockAlreadyInRedis() {
                // given
                when(redisCache.get(any(RedisKeyBuild.class), eq(String.class))).thenReturn("100");

                // when
                seckillVoucherService.loadVoucherStock(voucherId);

                // then
                verify(seckillVoucherService, never()).lambdaQuery();
            }

            @Test
            @DisplayName("布隆过滤器判断不存在 → 抛出异常")
            void should_throw_when_bloomFilterSaysNotExist() {
                // given
                BloomFilterHandler mockBloomFilter = mock(BloomFilterHandler.class);
                when(mockBloomFilter.contains(anyString())).thenReturn(false);
                when(bloomFilterHandlerFactory.get(anyString())).thenReturn(mockBloomFilter);

                // when & then
                assertThatThrownBy(() -> seckillVoucherService.loadVoucherStock(voucherId))
                        .isInstanceOf(RuntimeException.class)
                        .hasMessageContaining("查询秒杀优惠券不存在");
            }

            @Test
            @DisplayName("DB查询命中 → 写入Redis")
            void should_writeToRedis_when_dbHit() {
                // given
                when(redisCache.get(any(RedisKeyBuild.class), eq(String.class))).thenReturn(null);
                BloomFilterHandler mockBloomFilter = mock(BloomFilterHandler.class);
                when(mockBloomFilter.contains(anyString())).thenReturn(true);
                when(bloomFilterHandlerFactory.get(anyString())).thenReturn(mockBloomFilter);
                doReturn(ChainWrapperMocks.lambdaQueryChain(testSeckillVoucher)).when(seckillVoucherService).lambdaQuery();

                // when
                seckillVoucherService.loadVoucherStock(voucherId);

                // then
                verify(redisCache).set(any(RedisKeyBuild.class), eq("100"), anyLong(), any(TimeUnit.class));
            }
        }

    // ==================== rollbackStock 测试 ====================

    @Nested
        @DisplayName("rollbackStock - 回滚库存")
        class RollbackStockTests {

            @Test
            @DisplayName("回滚成功 → 返回true")
            void should_returnTrue_when_rollbackSuccess() {
                // given
                when(seckillVoucherMapper.rollbackStock(voucherId)).thenReturn(1);

                // when
                boolean result = seckillVoucherService.rollbackStock(voucherId);

                // then
                assertThat(result).isTrue();
            }

            @Test
            @DisplayName("回滚失败（无影响行）→ 返回false")
            void should_returnFalse_when_rollbackFails() {
                // given
                when(seckillVoucherMapper.rollbackStock(voucherId)).thenReturn(0);

                // when
                boolean result = seckillVoucherService.rollbackStock(voucherId);

                // then
                assertThat(result).isFalse();
            }
        }

}