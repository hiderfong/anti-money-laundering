package com.insurance.aml.module.monitoring.service;

import com.insurance.aml.common.util.IdGenerator;
import com.insurance.aml.module.monitoring.event.TransactionCommittedEvent;
import com.insurance.aml.module.monitoring.mapper.TransactionDailySummaryMapper;
import com.insurance.aml.module.monitoring.mapper.TransactionMapper;
import com.insurance.aml.module.monitoring.model.dto.TransactionIngestRequest;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import com.insurance.aml.module.monitoring.service.impl.TransactionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 交易服务实现类测试 - 事务事件发布验证
 *
 * 重点验证：
 * 1. 交易入库成功时，发布 TransactionCommittedEvent 而非直接发Kafka
 * 2. 事务回滚时，由Spring框架保证不触发 AFTER_COMMIT 监听器
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("交易服务 - 事务事件发布测试")
class TransactionServiceImplEventTest {

    @Mock
    private TransactionMapper transactionMapper;

    @Mock
    private TransactionDailySummaryMapper dailySummaryMapper;

    @Mock
    private IdGenerator idGenerator;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private Executor amlTaskExecutor;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    private TransactionIngestRequest testRequest;

    @BeforeEach
    void setUp() {
        testRequest = new TransactionIngestRequest();
        testRequest.setTransactionNo("TXN_TEST_001");
        testRequest.setCustomerId(100L);
        testRequest.setPolicyId(200L);
        testRequest.setTransactionType("PREMIUM");
        testRequest.setAmount(new BigDecimal("50000.00"));
        testRequest.setPaymentMethod("TRANSFER");

        // Mock Redis operations (common setup)
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    /**
     * 验证 ingestTransaction 发布 Spring ApplicationEvent 而非直接调用Kafka
     */
    @Test
    @DisplayName("入库成功 -> 发布TransactionCommittedEvent而非直接发送Kafka")
    void ingestTransaction_shouldPublishEventInsteadOfDirectKafka() {
        // 准备
        when(transactionMapper.insert(any(Transaction.class))).thenReturn(1);
        when(dailySummaryMapper.selectOne(any())).thenReturn(null);
        when(dailySummaryMapper.insert(any())).thenReturn(1);

        // 执行
        Transaction result = transactionService.ingestTransaction(testRequest);

        // 验证：应发布 TransactionCommittedEvent
        ArgumentCaptor<TransactionCommittedEvent> eventCaptor =
                ArgumentCaptor.forClass(TransactionCommittedEvent.class);
        verify(applicationEventPublisher, times(1)).publishEvent(eventCaptor.capture());

        // 验证事件内容
        TransactionCommittedEvent capturedEvent = eventCaptor.getValue();
        assertNotNull(capturedEvent.getTransaction(), "事件中应包含交易对象");
        assertEquals("TXN_TEST_001", capturedEvent.getTransaction().getTransactionNo(),
                "事件中的交易流水号应正确");
    }

    /**
     * 验证 ingestTransaction 未直接调用 Kafka producer
     * （Kafka发送已移至 TransactionEventListener，由 AFTER_COMMIT 触发）
     */
    @Test
    @DisplayName("入库成功 -> 不直接调用Kafka producer")
    void ingestTransaction_shouldNotDirectlyCallKafkaProducer() {
        // 准备
        when(transactionMapper.insert(any(Transaction.class))).thenReturn(1);
        when(dailySummaryMapper.selectOne(any())).thenReturn(null);
        when(dailySummaryMapper.insert(any())).thenReturn(1);

        // 执行
        transactionService.ingestTransaction(testRequest);

        // 验证：发布事件是通过 ApplicationEventPublisher（而非 TransactionEventProducer）
        verify(applicationEventPublisher, times(1)).publishEvent(any(TransactionCommittedEvent.class));

        // 注意：这里不验证 TransactionEventProducer，因为 TransactionServiceImpl 不再直接依赖它
        // TransactionEventProducer 仅由 TransactionEventListener 调用
    }

    /**
     * 模拟事务回滚场景：
     * 当 insert 抛出异常导致 @Transactional 回滚时，
     * publishEvent 之后的代码不会执行，且 Spring 不会触发 AFTER_COMMIT 监听器。
     *
     * 本测试验证：当 DB insert 失败时，不会发布事件（因为异常在 publishEvent 之前抛出）。
     */
    @Test
    @DisplayName("事务回滚 -> insert失败时不发布事件")
    void ingestTransaction_rollbackOnInsertFailure_shouldNotPublishEvent() {
        // 准备：模拟DB插入失败
        when(transactionMapper.insert(any(Transaction.class)))
                .thenThrow(new RuntimeException("DB constraint violation"));

        // 执行 & 验证：应抛出异常（由 @Transactional 回滚）
        assertThrows(RuntimeException.class, () ->
                transactionService.ingestTransaction(testRequest));

        // 验证：事件未被发布（因为异常在 publishEvent 之前发生）
        verify(applicationEventPublisher, never()).publishEvent(any());
    }
}
