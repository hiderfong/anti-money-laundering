package com.insurance.aml.module.ai.service.support;

import com.insurance.aml.module.ai.model.dto.AiRiskFeatureSummaryVO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * AI风险特征向量化器。
 *
 * <p>把 {@link AiRiskFeatureSummaryVO} 映射为固定顺序的数值特征向量，
 * 是训练与在线推理的唯一向量化出口，避免 training-serving skew。
 * 特征顺序一经确定不得调整（会使已存盘模型失配）。</p>
 */
@Component
public class AiRiskFeatureVectorizer {

    /** 特征维度，必须与 toVector 输出长度一致。 */
    public static final int FEATURE_DIM = 12;

    public double[] toVector(AiRiskFeatureSummaryVO f) {
        if (f == null) {
            throw new IllegalArgumentException("AiRiskFeatureSummaryVO must not be null");
        }
        return new double[]{
                f.getTransactionCount90d(),                 // 0
                d(f.getTotalAmount90d()),                    // 1
                d(f.getMaxAmount90d()),                       // 2
                f.getCashTransactionCount90d(),               // 3
                f.getCrossBorderTransactionCount90d(),        // 4
                f.getHighAmountTransactionCount90d(),         // 5
                f.getDistinctCounterpartyCount90d(),          // 6
                f.getActiveAlertCount(),                      // 7
                f.getHighRiskAlertCount(),                    // 8
                f.getConfirmedSuspiciousAlertCount(),         // 9
                f.getKycCompleteness(),                       // 10
                f.getWatchlistHitCount()                      // 11
        };
    }

    private double d(BigDecimal v) {
        return v == null ? 0.0 : v.doubleValue();
    }
}
