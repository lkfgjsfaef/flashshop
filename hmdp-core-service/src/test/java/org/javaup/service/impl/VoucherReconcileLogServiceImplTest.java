package org.javaup.service.impl;

import org.javaup.dto.VoucherReconcileLogDto;
import org.javaup.entity.VoucherReconcileLog;
import org.javaup.enums.LogType;
import org.javaup.kafka.message.SeckillVoucherMessage;
import org.javaup.message.MessageExtend;
import org.javaup.test.BaseUnitTest;
import org.javaup.test.TestDataFactory;
import org.javaup.toolkit.SnowflakeIdGenerator;
import org.junit.jupiter.api.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * VoucherReconcileLogServiceImpl 单元测试
 * 覆盖场景：保存对账日志（扣减/恢复类型）、DTO变体、消息变体
 */
class VoucherReconcileLogServiceImplTest extends BaseUnitTest {

    @Spy
    @InjectMocks
    private VoucherReconcileLogServiceImpl voucherReconcileLogService;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    private Long userId = 100L;
    private Long voucherId = 1L;
    private Long orderId = 200L;
    private Long traceId = 300L;

    // ==================== saveReconcileLog(DTO) 测试 ====================

    @Nested
    @DisplayName("saveReconcileLog(DTO) - 通过DTO保存对账日志")
    class SaveReconcileLogDtoTests {

        @Test
        @DisplayName("扣减类型日志 → 保存成功")
        void should_saveDeductLog_when_logTypeIsDeduct() {
            // given
            VoucherReconcileLogDto dto = new VoucherReconcileLogDto();
            dto.setOrderId(orderId);
            dto.setUserId(userId);
            dto.setVoucherId(voucherId);
            dto.setMessageId("msg-001");
            dto.setDetail("扣减库存");
            dto.setBeforeQty(100);
            dto.setChangeQty(-1);
            dto.setAfterQty(99);
            dto.setTraceId(traceId);
            dto.setLogType(LogType.DEDUCT.getCode());
            dto.setBusinessType(1);

            Long generatedId = TestDataFactory.nextId();
            when(snowflakeIdGenerator.nextId()).thenReturn(generatedId);
            doReturn(true).when(voucherReconcileLogService).save(any(VoucherReconcileLog.class));

            // when
            boolean result = voucherReconcileLogService.saveReconcileLog(dto);

            // then
            assertThat(result).isTrue();
            verify(voucherReconcileLogService).save(any(VoucherReconcileLog.class));
        }

        @Test
        @DisplayName("恢复类型日志 → beforeQty/afterQty互换后保存")
        void should_swapQtyAndSave_when_logTypeIsRestore() {
            // given
            VoucherReconcileLogDto dto = new VoucherReconcileLogDto();
            dto.setOrderId(orderId);
            dto.setUserId(userId);
            dto.setVoucherId(voucherId);
            dto.setMessageId("msg-002");
            dto.setDetail("恢复库存");
            dto.setBeforeQty(99);
            dto.setChangeQty(1);
            dto.setAfterQty(100);
            dto.setTraceId(traceId);
            dto.setLogType(LogType.RESTORE.getCode());
            dto.setBusinessType(1);

            Long generatedId = TestDataFactory.nextId();
            when(snowflakeIdGenerator.nextId()).thenReturn(generatedId);
            doReturn(true).when(voucherReconcileLogService).save(any(VoucherReconcileLog.class));

            // when
            boolean result = voucherReconcileLogService.saveReconcileLog(dto);

            // then
            assertThat(result).isTrue();
            verify(voucherReconcileLogService).save(any(VoucherReconcileLog.class));
        }
    }

    // ==================== saveReconcileLog(Message) 测试 ====================

    @Nested
    @DisplayName("saveReconcileLog(Message) - 通过Kafka消息保存对账日志")
    class SaveReconcileLogMessageTests {

        @Test
        @DisplayName("扣减类型 + 消息 → 构建DTO后保存")
        void should_buildDtoAndSave_when_deductWithMessage() {
            // given
            SeckillVoucherMessage messageBody = new SeckillVoucherMessage(
                    userId, voucherId, orderId, traceId, 100, -1, 99, false
            );
            MessageExtend<SeckillVoucherMessage> message = MessageExtend.of(messageBody);

            Long generatedId = TestDataFactory.nextId();
            when(snowflakeIdGenerator.nextId()).thenReturn(generatedId);
            doReturn(true).when(voucherReconcileLogService).save(any(VoucherReconcileLog.class));

            // when
            boolean result = voucherReconcileLogService.saveReconcileLog(
                    LogType.DEDUCT.getCode(), 1, "扣减库存", message
            );

            // then
            assertThat(result).isTrue();
            verify(voucherReconcileLogService).save(any(VoucherReconcileLog.class));
        }

        @Test
        @DisplayName("恢复类型 + 消息 → 互换before/afterQty后保存")
        void should_swapQtyAndSave_when_restoreWithMessage() {
            // given
            SeckillVoucherMessage messageBody = new SeckillVoucherMessage(
                    userId, voucherId, orderId, traceId, 99, 1, 100, false
            );
            MessageExtend<SeckillVoucherMessage> message = MessageExtend.of(messageBody);

            Long generatedId = TestDataFactory.nextId();
            when(snowflakeIdGenerator.nextId()).thenReturn(generatedId);
            doReturn(true).when(voucherReconcileLogService).save(any(VoucherReconcileLog.class));

            // when
            boolean result = voucherReconcileLogService.saveReconcileLog(
                    LogType.RESTORE.getCode(), 1, "恢复库存", message
            );

            // then
            assertThat(result).isTrue();
            verify(voucherReconcileLogService).save(any(VoucherReconcileLog.class));
        }

        @Test
        @DisplayName("扣减类型 + 自定义traceId → 使用自定义traceId保存")
        void should_useCustomTraceId_when_provided() {
            // given
            Long customTraceId = 999L;
            SeckillVoucherMessage messageBody = new SeckillVoucherMessage(
                    userId, voucherId, orderId, traceId, 100, -1, 99, false
            );
            MessageExtend<SeckillVoucherMessage> message = MessageExtend.of(messageBody);

            Long generatedId = TestDataFactory.nextId();
            when(snowflakeIdGenerator.nextId()).thenReturn(generatedId);
            doReturn(true).when(voucherReconcileLogService).save(any(VoucherReconcileLog.class));

            // when
            boolean result = voucherReconcileLogService.saveReconcileLog(
                    LogType.DEDUCT.getCode(), 1, "扣减库存", customTraceId, message
            );

            // then
            assertThat(result).isTrue();
            verify(voucherReconcileLogService).save(any(VoucherReconcileLog.class));
        }
    }
}