package com.insurance.aml.module.monitoring.kafka;

import com.insurance.aml.common.config.KafkaConfig;
import com.insurance.aml.module.monitoring.model.dto.TransactionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * 交易事件生产者
 * 负责将交易事件发送到Kafka topic 'aml-transactions'
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "aml.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class TransactionEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 发送交易事件到Kafka
     *
     * @param event 交易事件
     */
    public void sendTransactionEvent(TransactionEvent event) {
        String key = event.getTransactionNo();
        String topic = KafkaConfig.TOPIC_AML_TRANSACTIONS;

        log.info("发送交易事件到Kafka: topic={}, transactionNo={}, customerId={}, amount={}",
                topic, key, event.getCustomerId(), event.getAmount());

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(topic, key, event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("交易事件发送成功: topic={}, partition={}, offset={}, transactionNo={}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        key);
            } else {
                log.error("交易事件发送失败: transactionNo={}, error={}", key, ex.getMessage(), ex);
            }
        });
    }
}
