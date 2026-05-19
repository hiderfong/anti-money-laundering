package com.insurance.aml.module.ai.service;

import com.insurance.aml.module.ai.model.dto.AiRiskFeatureSummaryVO;
import com.insurance.aml.module.ai.service.support.AiRiskFeatureVectorizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    @DisplayName("特征值按固定顺序映射(全12位)")
    void toVector_mapsAllPositions() {
        AiRiskFeatureSummaryVO f = new AiRiskFeatureSummaryVO();
        f.setTransactionCount90d(1);                 // 0
        f.setTotalAmount90d(BigDecimal.valueOf(2));  // 1
        f.setMaxAmount90d(BigDecimal.valueOf(3));    // 2
        f.setCashTransactionCount90d(4);             // 3
        f.setCrossBorderTransactionCount90d(5);      // 4
        f.setHighAmountTransactionCount90d(6);       // 5
        f.setDistinctCounterpartyCount90d(7);        // 6
        f.setActiveAlertCount(8);                    // 7
        f.setHighRiskAlertCount(9);                  // 8
        f.setConfirmedSuspiciousAlertCount(10);      // 9
        f.setKycCompleteness(11);                    // 10
        f.setWatchlistHitCount(12);                  // 11
        double[] v = vectorizer.toVector(f);
        for (int i = 0; i < AiRiskFeatureVectorizer.FEATURE_DIM; i++) {
            assertEquals(i + 1.0, v[i], 0.0001, "position " + i);
        }
    }

    @Test
    @DisplayName("null输入抛IllegalArgumentException")
    void toVector_nullInput_throws() {
        assertThrows(IllegalArgumentException.class, () -> vectorizer.toVector(null));
    }
}
