package com.insurance.aml.module.monitoring.service;

import com.insurance.aml.module.ai.model.dto.AnomalyTrainingResultVO;
import com.insurance.aml.module.monitoring.mapper.TransactionMapper;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionAnomalyDetector 训练治理测试")
class TransactionAnomalyDetectorRetrainTest {

    @org.junit.jupiter.api.BeforeAll
    static void initMyBatisPlusTableInfo() {
        // 让 LambdaQueryWrapper.select(Transaction::getAmount) 能在纯单测里解析到列名
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(
                com.insurance.aml.module.monitoring.mapper.TransactionMapper.class.getName());
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                assistant,
                com.insurance.aml.module.monitoring.model.entity.Transaction.class);
    }

    @Mock
    TransactionMapper transactionMapper;

    private TransactionAnomalyDetector newDetector() {
        TransactionAnomalyDetector d = new TransactionAnomalyDetector(transactionMapper);
        ReflectionTestUtils.setField(d, "modelPath", "./target/test-data/models-" + System.nanoTime());
        ReflectionTestUtils.setField(d, "trainingDays", 90);
        ReflectionTestUtils.setField(d, "anomalyThreshold", 0.7);
        ReflectionTestUtils.setField(d, "numTrees", 10);
        ReflectionTestUtils.setField(d, "subsampleSize", 32);
        ReflectionTestUtils.setField(d, "minSamples", 4);
        return d;
    }

    private Transaction txn(double amount, int idSeed) {
        Transaction t = new Transaction();
        t.setId((long) idSeed);
        t.setTransactionNo("TXN" + idSeed);
        t.setAmount(BigDecimal.valueOf(amount));
        t.setIsCrossBorder(false);
        t.setTransactionTime(LocalDateTime.now().minusHours(idSeed));
        t.setCustomerId(1L);
        return t;
    }

    @Test
    @DisplayName("样本不足时跳过训练，模型保持未就绪")
    void retrain_insufficient_skips() {
        when(transactionMapper.selectList(any())).thenReturn(new ArrayList<>());
        TransactionAnomalyDetector d = newDetector();

        AnomalyTrainingResultVO result = d.retrain();

        assertEquals("SKIPPED_INSUFFICIENT", result.getStatus());
        assertFalse(d.isModelReady());
    }

    @Test
    @DisplayName("正常样本量训练成功并填充指标")
    void retrain_normal_trains() {
        List<Transaction> rows = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            rows.add(txn(100.0 + i, i));
        }
        when(transactionMapper.selectList(any())).thenReturn(rows);
        TransactionAnomalyDetector d = newDetector();

        AnomalyTrainingResultVO result = d.retrain();

        assertEquals("TRAINED", result.getStatus());
        assertTrue(result.isModelReady());
        assertEquals(60, result.getSampleCount());
        assertTrue(result.getTrainDurationMs() >= 0);
        assertEquals("TRAINED", d.getLastTrainStatus());
    }

    @Test
    @DisplayName("内部异常时返回FAILED且模型不下线")
    void retrain_innerException_returnsFailed() {
        when(transactionMapper.selectList(any())).thenThrow(new RuntimeException("db down"));
        TransactionAnomalyDetector d = newDetector();

        AnomalyTrainingResultVO result = d.retrain();

        assertEquals("FAILED", result.getStatus());
        assertTrue(result.getMessage() != null && result.getMessage().contains("db down"));
        assertEquals("FAILED", d.getLastTrainStatus());
        assertFalse(d.isModelReady(), "模型本就未就绪，FAILED 不会让它变就绪");
    }

    @Test
    @DisplayName("scheduledRetrain 在 retrain 抛异常时不外抛")
    void scheduledRetrain_swallowsExceptions() {
        when(transactionMapper.selectList(any())).thenThrow(new RuntimeException("boom"));
        TransactionAnomalyDetector d = newDetector();

        d.scheduledRetrain(); // must not throw
        assertEquals("FAILED", d.getLastTrainStatus());
    }

    @Test
    @DisplayName("Smile fit 失败时返回FAILED")
    void retrain_fitFailure_returnsFailed() {
        List<Transaction> rows = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            rows.add(txn(100.0 + i, i));
        }
        when(transactionMapper.selectList(any())).thenReturn(rows);
        TransactionAnomalyDetector d = newDetector();
        // numTrees=0 → Smile throws IllegalArgumentException("Invalid number of trees: 0")
        ReflectionTestUtils.setField(d, "numTrees", 0);

        AnomalyTrainingResultVO result = d.retrain();

        assertEquals("FAILED", result.getStatus());
        assertEquals("FAILED", d.getLastTrainStatus());
    }
}
