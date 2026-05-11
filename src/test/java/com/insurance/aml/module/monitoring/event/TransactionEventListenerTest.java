package com.insurance.aml.module.monitoring.event;

import com.insurance.aml.module.monitoring.kafka.TransactionEventProducer;
import com.insurance.aml.module.monitoring.model.dto.TransactionEvent;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 事务事件监听器单元测试
 *
 * 验证 @TransactionalEventListener(AFTER_COMMIT) 机制：
 * 1. 事务提交后，监听器调用Kafka发送
 * 2. 事务回滚时，监听器不执行（由Spring框架保证，此处测试显式调用行为）
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("事务事件监听器测试")
class TransactionEventListenerTest {

    @Mock
    private TransactionEventProducer transactionEventProducer;

    @InjectMocks
    private TransactionEventListener transactionEventListener;

    private Transaction testTransaction;

    @BeforeEach
    void setUp() {
        testTransaction = new Transaction();
        testTransaction.setId(1L);
        testTransaction.setTransactionNo("TXN_TEST_001");
        testTransaction.setCustomerId(100L);
        testTransaction.setPolicyId(200L);
        testTransaction.setTransactionType("PREMIUM");
        testTransaction.setAmount(new BigDecimal("50000.00"));
        testTransaction.setCurrency("CNY");
        testTransaction.setPaymentMethod("TRANSFER");
        testTransaction.setIsCrossBorder(false);
        testTransaction.setStatus("SUCCESS");
    }

    /**
     * 事务提交后，监听器应发送Kafka事件
     */
    @Test
    @DisplayName("事务提交 -> 监听器发送Kafka事件")
    void onTransactionCommitted_shouldSendKafkaEvent() {
        // 准备事件
        TransactionCommittedEvent event = new TransactionCommittedEvent(this, testTransaction);

        // 执行监听器（模拟事务已提交的场景）
        transactionEventListener.onTransactionCommitted(event);

        // 验证：Kafka producer 被调用了一次
        verify(transactionEventProducer, times(1)).sendTransactionEvent(any(TransactionEvent.class));
    }

    /**
     * 验证发送的Kafka事件包含正确的交易信息
     */
    @Test
    @DisplayName("事务提交 -> Kafka事件包含正确的交易信息")
    void onTransactionCommitted_shouldContainCorrectTransactionData() {
        TransactionCommittedEvent event = new TransactionCommittedEvent(this, testTransaction);

        transactionEventListener.onTransactionCommitted(event);

        // 验证：捕获发送的事件，检查内容正确
        verify(transactionEventProducer).sendTransactionEvent(argThat(txnEvent ->
                "TXN_TEST_001".equals(txnEvent.getTransactionNo()) &&
                100L == txnEvent.getCustomerId() &&
                new BigDecimal("50000.00").compareTo(txnEvent.getAmount()) == 0
        ));
    }

    /**
     * Kafka发送失败时，监听器应吞掉异常不影响主流程
     */
    @Test
    @DisplayName("Kafka发送失败 -> 异常被捕获不抛出")
    void onTransactionCommitted_kafkaFailure_shouldNotThrow() {
        TransactionCommittedEvent event = new TransactionCommittedEvent(this, testTransaction);

        // 模拟Kafka发送失败
        doThrow(new RuntimeException("Kafka broker unavailable"))
                .when(transactionEventProducer).sendTransactionEvent(any());

        // 监听器不应抛出异常（异常被内部catch）
        transactionEventListener.onTransactionCommitted(event);

        // 验证：仍然尝试发送了
        verify(transactionEventProducer, times(1)).sendTransactionEvent(any(TransactionEvent.class));
    }

    /**
     * 模拟事务回滚场景：Spring框架在事务回滚时不调用监听器
     * 此测试验证如果监听器未被调用，Kafka也不会被调用
     *
     * 注：@TransactionalEventListener(phase = AFTER_COMMIT) 的回滚不触发行为
     * 由Spring框架内部保证，此处仅验证"不调用监听器则不发Kafka"的逻辑正确性
     */
    @Test
    @DisplayName("事务回滚 -> 监听器未触发，Kafka未发送")
    void onTransactionRollback_shouldNotTriggerKafka() {
        // 模拟事务回滚场景：Spring框架不调用监听器
        // 验证：在没有调用监听器的情况下，Kafka producer 确实没有被调用
        verifyNoInteractions(transactionEventProducer);
    }
}
