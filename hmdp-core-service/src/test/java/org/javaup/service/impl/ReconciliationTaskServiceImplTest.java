package org.javaup.service.impl;

import org.javaup.entity.SeckillVoucher;
import org.javaup.entity.VoucherOrder;
import org.javaup.entity.VoucherReconcileLog;
import org.javaup.enums.ReconciliationStatus;
import org.javaup.model.RedisTraceLogModel;
import org.javaup.redis.RedisCacheImpl;
import org.javaup.redis.RedisKeyBuild;
import org.javaup.service.ISeckillVoucherService;
import org.javaup.service.IVoucherOrderService;
import org.javaup.service.IVoucherReconcileLogService;
import org.javaup.test.BaseUnitTest;
import org.javaup.test.TestDataFactory;
import org.junit.jupiter.api.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ReconciliationTaskServiceImpl 单元测试
 * 覆盖场景：删除Redis库存缓存
 *
 * 注意：reconciliationTaskExecute内部使用AopContext.currentProxy()自我调用，纯Mockito无法覆盖AOP代理路径。
 * 该路径需通过集成测试（@SpringBootTest + AopContext）覆盖。
 */
class ReconciliationTaskServiceImplTest extends BaseUnitTest {

    @Spy
    @InjectMocks
    private ReconciliationTaskServiceImpl reconciliationTaskService;

    @Mock
    private ISeckillVoucherService seckillVoucherService;

    @Mock
    private IVoucherOrderService voucherOrderService;

    @Mock
    private IVoucherReconcileLogService voucherReconcileLogService;

    @Mock
    private RedisCacheImpl redisCache;

    private Long voucherId = 1L;

    @Nested
    @DisplayName("delRedisStock - 删除Redis库存缓存")
    class DelRedisStockTests {

        @Test
        @DisplayName("正常删除 → 调用Redis删除库存Key")
        void should_deleteStockKey_when_called() {
            // when
            reconciliationTaskService.delRedisStock(voucherId);

            // then
            verify(redisCache).del(any(RedisKeyBuild.class));
        }
    }
}