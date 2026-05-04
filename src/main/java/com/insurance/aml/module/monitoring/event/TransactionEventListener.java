package com.insurance.aml.module.monitoring.event;

import com.insurance.aml.module.monitoring.kafka.TransactionEventProducer;
import com.insurance.aml.module.monitoring.model.dto.TransactionEvent;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 交易事务事件监听器
 *
 * 使用 @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT) 确保：
 * - 只有在数据库事务成功提交后，才发送Kafka消息
 * - 事务回滚时，事件监听器不会执行，Kafka消息不会发出
 * - 保证数据库与Kafka之间的最终一致性
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventListener {

    private final TransactionEventProducer transactionEventProducer;

    /**
     * 事务提交后发送Kafka事件
     * 仅在数据库事务成功 COMMIT 后触发；事务 ROLLBACK 时不执行。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransactionCommitted(TransactionCommittedEvent event) {
        Transaction transaction = event.getTransaction();
        log.info("[事务事件] 数据库事务已提交，发送Kafka事件: transactionNo={}, id={}",
                transaction.getTransactionNo(), transaction.getId());

        try {
            TransactionEvent kafkaEvent = TransactionEvent.fromEntity(transaction);
            transactionEventProducer.sendTransactionEvent(kafkaEvent);
            log.info("[事务事件] Kafka事件发送成功: transactionNo={}", transaction.getTransactionNo());
        } catch (Exception e) {
            log.error("[事务事件] Kafka事件发送失败: transactionNo={}, error={}",
                    transaction.getTransactionNo(), e.getMessage(), e);
        }
    }
}
