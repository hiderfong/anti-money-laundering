package com.insurance.aml.module.ai.service;

import com.insurance.aml.module.ai.model.dto.AiRiskFeatureSummaryVO;
import com.insurance.aml.module.ai.service.support.AiRiskFeatureVectorizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("AI风险特征向量化器测试")
class AiRiskFeatureVectorizerTest {

    private final AiRiskFeatureVectorizer vectorizer = new AiRiskFeatureVectorizer();

    @Test
    @DisplayName("向量维度恒定且与FEATURE_DIM一致")
    void toVector_dimensionIsStable() {
        double[] v = vectorizer.toVector(new AiRiskFeatureSummaryVO());
        assertEquals(AiRiskFeatureVectorizer.FEATURE_DIM, v.length);
    }

    @Test
    @DisplayName("空VO的BigDecimal字段按0处理不抛NPE")
    void toVector_handlesDefaults() {
        AiRiskFeatureSummaryVO f = new AiRiskFeatureSummaryVO();
        double[] v = vectorizer.toVector(f);
        for (double d : v) {
            assertEquals(0.0, d, 0.0001);
        }
    }

    @Test
    @DisplayName("特征值按固定顺序映射")
    void toVector_mapsKnownPositions() {
        AiRiskFeatureSummaryVO f = new AiRiskFeatureSummaryVO();
        f.setTransactionCount90d(7);
        f.setTotalAmount90d(BigDecimal.valueOf(1234.5));
        f.setKycCompleteness(80);
        double[] v = vectorizer.toVector(f);
        assertEquals(7.0, v[0], 0.0001);
        assertEquals(1234.5, v[1], 0.0001);
        assertEquals(80.0, v[10], 0.0001);
    }
}
