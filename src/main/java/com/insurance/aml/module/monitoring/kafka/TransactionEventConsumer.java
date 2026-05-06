package com.insurance.aml.module.monitoring.kafka;

import com.insurance.aml.common.config.KafkaConfig;
import com.insurance.aml.module.alert.model.entity.Alert;
import com.insurance.aml.module.alert.model.entity.AlertRuleDetail;
import com.insurance.aml.module.alert.service.AlertService;
import com.insurance.aml.module.monitoring.model.dto.TransactionEvent;
import com.insurance.aml.module.monitoring.model.entity.RuleExecutionLog;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import com.insurance.aml.module.monitoring.service.RuleEngineService;
import com.insurance.aml.module.monitoring.service.GraphAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 交易事件消费者
 * 监听Kafka topic 'aml-transactions'，消费交易事件后触发规则引擎评估
 * 实现交易录入与规则评估的异步解耦
 *
 * 异步管道：
 *   1. 接收Kafka事件
 *   2. 异步触发三种规则引擎并行评估（evaluateAsync）
 *   3. 评估完成后异步生成预警（createAlertAsync）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventConsumer {
    private final RuleEngineService ruleEngineService;
    private final AlertService alertService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private GraphAnalysisService graphAnalysisService;

    /**
     * 消费交易事件，触发异步规则引擎评估管道
     *
     * 流程：
     *   1) 解析Kafka消息为TransactionEvent
     *   2) 转换为Transaction实体
     *   3) 调用evaluateAsync异步并行评估三种规则引擎
     *   4) 评估完成后，异步生成预警
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
            final TransactionEvent capturedEvent = event;
            log.info("消费交易事件: topic={}, partition={}, offset={}, transactionNo={}, customerId={}, amount={}",
                    record.topic(), record.partition(), record.offset(),
                    event.getTransactionNo(), event.getCustomerId(), event.getAmount());

            // 将TransactionEvent转换回Transaction实体
            Transaction transaction = toTransaction(event);

            // ===== 同步交易数据到Neo4j图数据库 =====
            try {
                graphAnalysisService.syncTransactionToGraph(transaction);
                log.debug("[异步管道] 交易数据已同步到Neo4j图数据库: transactionNo={}", event.getTransactionNo());
            } catch (Exception graphEx) {
                log.error("[异步管道] Neo4j图数据同步失败(不影响主流程): transactionNo={}, error={}",
                        event.getTransactionNo(), graphEx.getMessage(), graphEx);
            }

            // ===== 异步管道: 三种规则引擎并行评估 =====
            ruleEngineService.evaluateAsync(transaction)
                    .thenAccept(matchedLogs -> {
                        if (!matchedLogs.isEmpty()) {
                            log.warn("[异步管道] 交易触发告警: transactionNo={}, 命中规则数={}",
                                    capturedEvent.getTransactionNo(), matchedLogs.size());

                            // ===== 异步生成预警 =====
                            generateAlertAsync(transaction, matchedLogs);
                        } else {
                            log.debug("[异步管道] 交易未命中任何规则: transactionNo={}", capturedEvent.getTransactionNo());
                        }
                    })
                    .exceptionally(ex -> {
                        log.error("[异步管道] 规则评估异常: transactionNo={}, error={}",
                                capturedEvent.getTransactionNo(), ex.getMessage(), ex);
                        return null;
                    });

            // 手动确认消费（规则评估异步执行，不阻塞ACK）
            ack.acknowledge();
            log.debug("交易事件消费确认(异步评估已提交): transactionNo={}", event.getTransactionNo());

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
     * 异步生成预警
     * 根据规则引擎评估结果构建Alert对象，调用AlertService.createAlertAsync异步创建
     */
    private void generateAlertAsync(Transaction transaction, List<RuleExecutionLog> matchedLogs) {
        try {
            // 计算最高风险分数
            BigDecimal maxScore = matchedLogs.stream()
                    .map(RuleExecutionLog::getMatchScore)
                    .filter(s -> s != null)
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);

            // 确定风险等级
            String riskLevel = resolveRiskLevel(maxScore);

            // 构建预警对象
            Alert alert = new Alert();
            alert.setCustomerId(transaction.getCustomerId());
            alert.setAlertType("RULE_MATCH");
            alert.setRiskLevel(riskLevel);
            alert.setAlertSummary(String.format("交易 %s 命中 %d 条规则, 最高风险分数: %s",
                    transaction.getTransactionNo(), matchedLogs.size(), maxScore.toPlainString()));
            alert.setRiskScore(maxScore.intValue());
            alert.setRelatedTransactionIds(String.valueOf(transaction.getId()));

            // 构建规则明细
            List<AlertRuleDetail> ruleDetails = new ArrayList<>();
            for (RuleExecutionLog log_entry : matchedLogs) {
                AlertRuleDetail detail = new AlertRuleDetail();
                detail.setRuleId(log_entry.getRuleId());
                detail.setRuleCode(log_entry.getRuleCode());
                detail.setMatchScore(log_entry.getMatchScore());
                detail.setMatchDetail(log_entry.getExecutionDetail());
                ruleDetails.add(detail);
            }

            // 异步创建预警（不阻塞消费者线程）
            alertService.createAlertAsync(alert, ruleDetails)
                    .thenAccept(created -> log.info("[异步管道] 预警创建成功: alertId={}, alertNo={}",
                            created.getId(), created.getAlertNo()))
                    .exceptionally(ex -> {
                        log.error("[异步管道] 预警创建失败: transactionNo={}, error={}",
                                transaction.getTransactionNo(), ex.getMessage(), ex);
                        return null;
                    });

        } catch (Exception e) {
            log.error("[异步管道] 构建预警对象异常: transactionNo={}, error={}",
                    transaction.getTransactionNo(), e.getMessage(), e);
        }
    }

    /**
     * 根据风险分数确定风险等级
     */
    private String resolveRiskLevel(BigDecimal score) {
        if (score.compareTo(BigDecimal.valueOf(90)) >= 0) return "CRITICAL";
        if (score.compareTo(BigDecimal.valueOf(70)) >= 0) return "HIGH";
        if (score.compareTo(BigDecimal.valueOf(40)) >= 0) return "MEDIUM";
        return "LOW";
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
