package com.insurance.aml.module.monitoring.kafka;

import com.insurance.aml.common.config.KafkaConfig;
import com.insurance.aml.module.monitoring.model.dto.TransactionEvent;
import com.insurance.aml.module.monitoring.model.entity.RuleExecutionLog;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import com.insurance.aml.module.monitoring.service.RuleEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 交易事件消费者
 * 监听Kafka topic 'aml-transactions'，消费交易事件后触发规则引擎评估
 * 实现交易录入与规则评估的异步解耦
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventConsumer {

    private final RuleEngineService ruleEngineService;

    /**
     * 消费交易事件，触发规则引擎评估
     *
     * @param record Kafka消费记录
     * @param ack    手动确认
     */
    @KafkaListener(
            topics = KafkaConfig.TOPIC_AML_TRANSACTIONS,
            groupId = "${spring.kafka.consumer.group-id:aml-consumer-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onTransactionEvent(ConsumerRecord<String, Object> record, Acknowledgment ack) {
        TransactionEvent event = null;
        try {
            Object value = record.value();
            if (!(value instanceof TransactionEvent)) {
                log.warn("收到非TransactionEvent类型消息: topic={}, partition={}, offset={}, type={}",
                        record.topic(), record.partition(), record.offset(),
                        value != null ? value.getClass().getName() : "null");
                ack.acknowledge();
                return;
            }

            event = (TransactionEvent) value;
            log.info("消费交易事件: topic={}, partition={}, offset={}, transactionNo={}, customerId={}, amount={}",
                    record.topic(), record.partition(), record.offset(),
                    event.getTransactionNo(), event.getCustomerId(), event.getAmount());

            // 将TransactionEvent转换回Transaction实体
            Transaction transaction = toTransaction(event);

            // 触发规则引擎评估
            List<RuleExecutionLog> matchedLogs = ruleEngineService.evaluate(transaction);

            if (!matchedLogs.isEmpty()) {
                log.warn("交易触发告警: transactionNo={}, 命中规则数={}", event.getTransactionNo(), matchedLogs.size());
            }

            // 手动确认消费
            ack.acknowledge();
            log.debug("交易事件消费确认: transactionNo={}", event.getTransactionNo());

        } catch (Exception e) {
            log.error("交易事件消费异常: partition={}, offset={}, transactionNo={}, error={}",
                    record.partition(), record.offset(),
                    event != null ? event.getTransactionNo() : "unknown",
                    e.getMessage(), e);
            // 仍然确认，避免无限重试（可通过死信队列处理）
            ack.acknowledge();
        }
    }

    /**
     * 将TransactionEvent DTO转换为Transaction实体
     * 供RuleEngineService使用
     */
    private Transaction toTransaction(TransactionEvent event) {
        Transaction transaction = new Transaction();
        transaction.setId(event.getId());
        transaction.setTransactionNo(event.getTransactionNo());
        transaction.setPolicyId(event.getPolicyId());
        transaction.setCustomerId(event.getCustomerId());
        transaction.setTransactionType(event.getTransactionType());
        transaction.setAmount(event.getAmount());
        transaction.setCurrency(event.getCurrency());
        transaction.setPaymentMethod(event.getPaymentMethod());
        transaction.setChannel(event.getChannel());
        transaction.setCounterpartyName(event.getCounterpartyName());
        transaction.setCounterpartyAccount(event.getCounterpartyAccount());
        transaction.setCounterpartyBank(event.getCounterpartyBank());
        transaction.setIsCrossBorder(event.getIsCrossBorder());
        transaction.setTransactionTime(event.getTransactionTime());
        transaction.setRemark(event.getRemark());
        transaction.setStatus(event.getStatus());
        transaction.setSourceSystem(event.getSourceSystem());
        return transaction;
    }
}
