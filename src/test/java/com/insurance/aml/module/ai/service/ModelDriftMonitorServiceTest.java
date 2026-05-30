package com.insurance.aml.module.ai.service;

import com.insurance.aml.module.ai.model.dto.DistributionSnapshot;
import com.insurance.aml.module.ai.model.dto.ModelDriftStatusVO;
import com.insurance.aml.module.ai.service.support.AiRiskSupervisedModel;
import com.insurance.aml.module.ai.service.support.ModelDriftMonitorService;
import com.insurance.aml.module.monitoring.mapper.TransactionMapper;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import com.insurance.aml.module.monitoring.service.TransactionAnomalyDetector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("模型漂移监控服务测试")
class ModelDriftMonitorServiceTest {

    @Mock AiRiskSupervisedModel supervisedModel;
    @Mock TransactionAnomalyDetector anomalyDetector;
    @Mock JdbcTemplate jdbcTemplate;
    @Mock TransactionMapper transactionMapper;

    private ModelDriftMonitorService newService() {
        ModelDriftMonitorService s = new ModelDriftMonitorService(
                supervisedModel, anomalyDetector, jdbcTemplate, transactionMapper);
        ReflectionTestUtils.setField(s, "windowHours", 24);
        ReflectionTestUtils.setField(s, "bins", 10);
        ReflectionTestUtils.setField(s, "warnThreshold", 0.1);
        ReflectionTestUtils.setField(s, "severeThreshold", 0.25);
        ReflectionTestUtils.setField(s, "anomalySampleCap", 10000);
        ReflectionTestUtils.setField(s, "supervisedSampleCap", 10000);
        ReflectionTestUtils.setField(s, "minSamples", 10);
        return s;
    }

    private DistributionSnapshot uniformBaseline() {
        return DistributionSnapshot.builder()
                .bins(10).lo(0.0).hi(1.0)
                .counts(new int[]{10, 10, 10, 10, 10, 10, 10, 10, 10, 10}).total(100)
                .capturedAt(LocalDateTime.now()).build();
    }

    private List<Double> uniformProbs() {
        List<Double> probs = new ArrayList<>();
        for (int b = 0; b < 10; b++) {
            for (int k = 0; k < 5; k++) {
                probs.add(b * 0.1 + 0.05);
            }
        }
        return probs;
    }

    @Test
    @DisplayName("监督基线缺失 → UNAVAILABLE")
    void supervised_noBaseline_unavailable() {
        when(supervisedModel.getTrainingScoreDistribution()).thenReturn(null);
        ModelDriftStatusVO vo = newService().computeSupervisedDrift();
        assertEquals("supervised", vo.getModelKey());
        assertEquals("UNAVAILABLE", vo.getStatus());
    }

    @Test
    @DisplayName("监督当前样本不足 → UNAVAILABLE")
    void supervised_insufficientSamples_unavailable() {
        when(supervisedModel.getTrainingScoreDistribution()).thenReturn(uniformBaseline());
        when(jdbcTemplate.queryForList(anyString(), eq(Double.class), any()))
                .thenReturn(List.of(0.1, 0.2, 0.3));
        ModelDriftStatusVO vo = newService().computeSupervisedDrift();
        assertEquals("UNAVAILABLE", vo.getStatus());
    }

    @Test
    @DisplayName("监督相同分布 → NORMAL")
    void supervised_sameDistribution_normal() {
        when(supervisedModel.getTrainingScoreDistribution()).thenReturn(uniformBaseline());
        when(jdbcTemplate.queryForList(anyString(), eq(Double.class), any()))
                .thenReturn(uniformProbs());
        ModelDriftStatusVO vo = newService().computeSupervisedDrift();
        assertEquals("NORMAL", vo.getStatus());
    }

    @Test
    @DisplayName("监督显著偏移 → SEVERE")
    void supervised_shifted_severe() {
        when(supervisedModel.getTrainingScoreDistribution()).thenReturn(uniformBaseline());
        List<Double> skewed = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            skewed.add(0.95);
        }
        when(jdbcTemplate.queryForList(anyString(), eq(Double.class), any())).thenReturn(skewed);
        ModelDriftStatusVO vo = newService().computeSupervisedDrift();
        assertEquals("SEVERE", vo.getStatus());
    }

    @Test
    @DisplayName("异常基线缺失 → UNAVAILABLE")
    void anomaly_noBaseline_unavailable() {
        when(anomalyDetector.getTrainingScoreDistribution()).thenReturn(null);
        ModelDriftStatusVO vo = newService().computeAnomalyDrift();
        assertEquals("anomaly", vo.getModelKey());
        assertEquals("UNAVAILABLE", vo.getStatus());
    }

    @Test
    @DisplayName("异常正常路径 → NORMAL")
    void anomaly_sameDistribution_normal() {
        when(anomalyDetector.getTrainingScoreDistribution()).thenReturn(uniformBaseline());
        List<Transaction> txns = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            txns.add(new Transaction());
        }
        when(transactionMapper.selectList(any())).thenReturn(txns);
        final int[] call = {0};
        when(anomalyDetector.predict(any())).thenAnswer(inv -> {
            double v = (call[0]++ % 10) * 0.1 + 0.05;
            return v;
        });
        ModelDriftStatusVO vo = newService().computeAnomalyDrift();
        assertEquals("NORMAL", vo.getStatus());
    }

    @Test
    @DisplayName("computeAll 返回固定顺序 [supervised, anomaly]")
    void computeAll_fixedOrder() {
        when(supervisedModel.getTrainingScoreDistribution()).thenReturn(null);
        when(anomalyDetector.getTrainingScoreDistribution()).thenReturn(null);
        List<ModelDriftStatusVO> all = newService().computeAll();
        assertEquals(2, all.size());
        assertEquals("supervised", all.get(0).getModelKey());
        assertEquals("anomaly", all.get(1).getModelKey());
    }

    @Test
    @DisplayName("scheduledDriftCheck 吞掉异常不外抛")
    void scheduledDriftCheck_swallowsExceptions() {
        when(supervisedModel.getTrainingScoreDistribution())
                .thenThrow(new RuntimeException("boom"));
        newService().scheduledDriftCheck();
    }

    @Test
    @DisplayName("异常分显著偏移 → SEVERE")
    void anomaly_shifted_severe() {
        when(anomalyDetector.getTrainingScoreDistribution()).thenReturn(uniformBaseline());
        List<Transaction> txns = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            txns.add(new Transaction());
        }
        when(transactionMapper.selectList(any())).thenReturn(txns);
        // all predictions concentrate in the top bin → strong drift vs uniform baseline
        when(anomalyDetector.predict(any())).thenReturn(0.95);
        ModelDriftStatusVO vo = newService().computeAnomalyDrift();
        assertEquals("SEVERE", vo.getStatus());
    }
}
