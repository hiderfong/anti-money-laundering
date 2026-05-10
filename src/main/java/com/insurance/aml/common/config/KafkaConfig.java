package com.insurance.aml.common.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka 配置类
 *
 * 功能说明：
 * 1. 配置生产者工厂 - 使用JSON序列化器
 * 2. 配置消费者工厂 - 使用JSON反序列化器，支持自动重试
 * 3. 配置监听器容器工厂 - 并发消费
 * 4. 定义AML业务Topic - 交易监控Topic和告警Topic
 *
 * @author AML Team
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "aml.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:aml-consumer-group}")
    private String groupId;

    /** 交易监控Topic名称 */
    public static final String TOPIC_AML_TRANSACTIONS = "aml-transactions";

    /** 反洗钱告警Topic名称 */
    public static final String TOPIC_AML_ALERTS = "aml-alerts";

    /**
     * 生产者配置
     *
     * @return Map<String, Object> 生产者属性
     */
    private Map<String, Object> producerConfigs() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // 生产者可靠性配置：确保消息不丢失
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return props;
    }

    /**
     * 消费者配置
     *
     * @return Map<String, Object> 消费者属性
     */
    private Map<String, Object> consumerConfigs() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // 使用ErrorHandlingDeserializer包装JSON反序列化器，避免反序列化异常导致消费者退出
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());
        // 消费者可靠性配置
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        // JSON反序列化器信任的包（安全配置）
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.insurance.aml.*");
        return props;
    }

    /**
     * 生产者工厂
     *
     * @return ProducerFactory<String, Object>
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        log.info("初始化 Kafka 生产者工厂，Bootstrap Servers: {}", bootstrapServers);
        return new DefaultKafkaProducerFactory<>(producerConfigs());
    }

    /**
     * Kafka模板 - 用于发送消息
     *
     * @param producerFactory 生产者工厂
     * @return KafkaTemplate<String, Object>
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        log.info("初始化 KafkaTemplate...");
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(producerFactory);
        // 设置默认Topic
        template.setDefaultTopic(TOPIC_AML_TRANSACTIONS);
        return template;
    }

    /**
     * 消费者工厂
     *
     * @return ConsumerFactory<String, Object>
     */
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        log.info("初始化 Kafka 消费者工厂，Group ID: {}", groupId);
        return new DefaultKafkaConsumerFactory<>(consumerConfigs());
    }

    /**
     * 监听器容器工厂 - 支持手动ACK和并发消费
     *
     * @param consumerFactory 消费者工厂
     * @return ConcurrentKafkaListenerContainerFactory<String, Object>
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory) {
        log.info("初始化 Kafka 监听器容器工厂...");
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        // 设置并发消费者数量（对应分区数）
        factory.setConcurrency(3);
        // 手动提交偏移量
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }

    /**
     * 创建交易监控Topic
     * 用于接收待检测的金融交易数据
     *
     * @return NewTopic
     */
    @Bean
    public NewTopic amlTransactionsTopic() {
        log.info("创建 Kafka Topic: {}", TOPIC_AML_TRANSACTIONS);
        // 3个分区，1个副本（生产环境建议3个副本）
        return new NewTopic(TOPIC_AML_TRANSACTIONS, 3, (short) 1);
    }

    /**
     * 创建反洗钱告警Topic
     * 用于发布可疑交易告警信息
     *
     * @return NewTopic
     */
    @Bean
    public NewTopic amlAlertsTopic() {
        log.info("创建 Kafka Topic: {}", TOPIC_AML_ALERTS);
        return new NewTopic(TOPIC_AML_ALERTS, 3, (short) 1);
    }
}
