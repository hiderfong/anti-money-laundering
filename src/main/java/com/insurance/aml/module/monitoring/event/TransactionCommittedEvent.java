package com.insurance.aml.module.monitoring.event;

import com.insurance.aml.module.monitoring.model.entity.Transaction;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 交易入库成功事件（Spring ApplicationEvent）
 *
 * 在数据库事务成功提交后，由 @TransactionalEventListener(AFTER_COMMIT) 监听，
 * 确保只有事务真正落库后才发送Kafka消息，避免事务回滚导致数据不一致。
 */
@Getter
public class TransactionCommittedEvent extends ApplicationEvent {

    private final Transaction transaction;

    public TransactionCommittedEvent(Object source, Transaction transaction) {
        super(source);
        this.transaction = transaction;
    }
}
