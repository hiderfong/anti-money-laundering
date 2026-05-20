package com.insurance.aml.module.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.aml.module.ai.mapper.AiRiskScoreRecordMapper;
import com.insurance.aml.module.ai.model.dto.AiRiskTrainingResultVO;
import com.insurance.aml.module.ai.model.entity.AiRiskScoreRecord;
import com.insurance.aml.module.ai.service.support.AiRiskFeatureVectorizer;
import com.insurance.aml.module.ai.service.support.AiRiskModelTrainingService;
import com.insurance.aml.module.ai.service.support.AiRiskSupervisedModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AI监督模型训练服务测试")
class AiRiskModelTrainingServiceTest {

    @Mock
    AiRiskScoreRecordMapper mapper;

    private AiRiskModelTrainingService newService() {
        // modelPath intentionally unset; AiRiskSupervisedModel.save() NPE is swallowed by design — fine in unit context
        AiRiskModelTrainingService s = new AiRiskModelTrainingService(
                mapper, new AiRiskFeatureVectorizer(), new AiRiskSupervisedModel(), new ObjectMapper());
        ReflectionTestUtils.setField(s, "minSamples", 4);
        return s;
    }

    private AiRiskScoreRecord labeled(String label, double base) {
        AiRiskScoreRecord r = new AiRiskScoreRecord();
        r.setManualReviewLabel(label);
        r.setFeatureSnapshotJson("{\"transactionCount90d\":" + (int) base
                + ",\"kycCompleteness\":" + (int) base + "}");
        return r;
    }

    @Test
    @DisplayName("样本不足时跳过训练")
    void retrain_insufficientSamples_skips() {
        when(mapper.selectList(any())).thenReturn(new ArrayList<>());

        AiRiskTrainingResultVO result = newService().retrain();

        assertEquals("SKIPPED_INSUFFICIENT", result.getStatus());
        assertFalse(result.isModelReady());
    }

    @Test
    @DisplayName("仅单一类别时跳过训练")
    void retrain_singleClass_skips() {
        List<AiRiskScoreRecord> rows = List.of(
                labeled("TRUE_POSITIVE", 1), labeled("TRUE_POSITIVE", 2),
                labeled("TRUE_POSITIVE", 3), labeled("TRUE_POSITIVE", 4));
        when(mapper.selectList(any())).thenReturn(rows);

        AiRiskTrainingResultVO result = newService().retrain();

        assertEquals("SKIPPED_SINGLE_CLASS", result.getStatus());
        assertEquals(4, result.getSampleCount());
    }

    @Test
    @DisplayName("NEEDS_MONITORING被排除，正负样本训练成功")
    void retrain_trainsAndExcludesNeutral() {
        List<AiRiskScoreRecord> rows = List.of(
                labeled("TRUE_POSITIVE", 9), labeled("TRUE_POSITIVE", 8),
                labeled("FALSE_POSITIVE", 0), labeled("FALSE_POSITIVE", 1),
                labeled("NEEDS_MONITORING", 5));
        when(mapper.selectList(any())).thenReturn(rows);

        AiRiskTrainingResultVO result = newService().retrain();

        assertEquals("TRAINED", result.getStatus());
        assertEquals(4, result.getSampleCount());
        assertEquals(2, result.getPositiveCount());
        assertEquals(2, result.getNegativeCount());
        assertTrue(result.isModelReady());
        assertTrue(result.getAuc() >= 0.0 && result.getAuc() <= 1.0);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("retrain 外层异常时返回FAILED并 recordOutcome")
    void retrain_innerException_returnsFailed() {
        AiRiskScoreRecordMapper mapper = org.mockito.Mockito.mock(AiRiskScoreRecordMapper.class);
        when(mapper.selectList(any())).thenThrow(new RuntimeException("db down"));
        AiRiskSupervisedModel realModel = new AiRiskSupervisedModel();
        AiRiskModelTrainingService s = new AiRiskModelTrainingService(
                mapper, new AiRiskFeatureVectorizer(), realModel, new com.fasterxml.jackson.databind.ObjectMapper());
        org.springframework.test.util.ReflectionTestUtils.setField(s, "minSamples", 4);

        AiRiskTrainingResultVO result = s.retrain();

        assertEquals("FAILED", result.getStatus());
        assertEquals("FAILED", realModel.getLastTrainStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(realModel.getLastTrainError());
    }
}
