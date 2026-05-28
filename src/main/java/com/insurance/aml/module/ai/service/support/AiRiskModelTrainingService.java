package com.insurance.aml.module.ai.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.aml.module.ai.mapper.AiRiskScoreRecordMapper;
import com.insurance.aml.module.ai.model.dto.AiRiskFeatureSummaryVO;
import com.insurance.aml.module.ai.model.dto.AiRiskTrainingResultVO;
import com.insurance.aml.module.ai.model.dto.DistributionSnapshot;
import com.insurance.aml.module.ai.model.entity.AiRiskScoreRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import smile.classification.LogisticRegression;
import smile.validation.metric.AUC;
import smile.validation.metric.Accuracy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AI监督模型训练服务。
 *
 * <p>从已人工复核的评分记录拉取标注，复用 {@link AiRiskFeatureVectorizer} 向量化，
 * 训练 Smile 逻辑回归并热替换到 {@link AiRiskSupervisedModel}。
 * 标签映射：TRUE_POSITIVE→1，FALSE_POSITIVE→0，NEEDS_MONITORING 排除。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiRiskModelTrainingService {

    private final AiRiskScoreRecordMapper scoreRecordMapper;
    private final AiRiskFeatureVectorizer vectorizer;
    private final AiRiskSupervisedModel supervisedModel;
    private final ObjectMapper objectMapper;

    @Value("${aml.ml.ai-risk.min-samples:50}")
    private int minSamples;

    /** 串行化训练，避免 @Scheduled 与手动 POST 并发时产生 .model/.meta 错配。 */
    private final java.util.concurrent.atomic.AtomicBoolean trainingInProgress =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    @Scheduled(cron = "${aml.ml.ai-risk.retrain-cron:0 0 3 * * SUN}")
    public void scheduledRetrain() {
        try {
            AiRiskTrainingResultVO result = retrain();
            log.info("[AI-ML] 定时重训完成: {}", result.getStatus());
        } catch (Exception e) {
            log.error("[AI-ML] 定时重训失败: {}", e.getMessage(), e);
        }
    }

    public AiRiskTrainingResultVO retrain() {
        if (!trainingInProgress.compareAndSet(false, true)) {
            log.warn("[AI-ML] 已有训练正在进行，跳过重复触发");
            AiRiskTrainingResultVO skipped = AiRiskTrainingResultVO.builder()
                    .status("SKIPPED_IN_PROGRESS")
                    .modelReady(supervisedModel.isReady())
                    .message("训练正在进行中")
                    .build();
            supervisedModel.recordOutcome(skipped.getStatus(), null);
            return skipped;
        }
        try {
            AiRiskTrainingResultVO result;
            try {
                result = doRetrain();
            } catch (Exception e) {
                log.error("[AI-ML] 监督模型训练失败: {}", e.getMessage(), e);
                result = AiRiskTrainingResultVO.builder()
                        .status("FAILED")
                        .modelReady(supervisedModel.isReady())
                        .message(e.getClass().getSimpleName() + ": " + e.getMessage())
                        .build();
            }
            supervisedModel.recordOutcome(
                    result.getStatus(),
                    "FAILED".equals(result.getStatus()) ? result.getMessage() : null);
            return result;
        } finally {
            trainingInProgress.set(false);
        }
    }

    private AiRiskTrainingResultVO doRetrain() {
        // MVP: loads all labeled rows. TODO(perf): cap with a recency window (e.g. ORDER BY reviewed_at DESC LIMIT N) once the labeled corpus grows.
        List<AiRiskScoreRecord> records = scoreRecordMapper.selectList(
                new LambdaQueryWrapper<AiRiskScoreRecord>()
                        .isNotNull(AiRiskScoreRecord::getManualReviewLabel)
                        .isNotNull(AiRiskScoreRecord::getFeatureSnapshotJson));

        List<double[]> xs = new ArrayList<>();
        List<Integer> ys = new ArrayList<>();
        for (AiRiskScoreRecord r : records) {
            Integer label = mapLabel(r.getManualReviewLabel());
            if (label == null) {
                continue;
            }
            double[] vec = parseVector(r.getFeatureSnapshotJson());
            if (vec == null) {
                continue;
            }
            xs.add(vec);
            ys.add(label);
        }

        int sampleCount = xs.size();
        long positive = ys.stream().filter(v -> v == 1).count();
        long negative = sampleCount - positive;

        if (sampleCount < minSamples) {
            return skip("SKIPPED_INSUFFICIENT",
                    "标注样本不足: " + sampleCount + " < " + minSamples, sampleCount,
                    (int) positive, (int) negative);
        }
        if (positive == 0 || negative == 0) {
            return skip("SKIPPED_SINGLE_CLASS",
                    "仅单一类别，无法训练二分类", sampleCount, (int) positive, (int) negative);
        }

        double[][] x = xs.toArray(new double[0][]);
        int[] y = ys.stream().mapToInt(Integer::intValue).toArray();
        LogisticRegression model = LogisticRegression.fit(x, y);

        int[] pred = new int[y.length];
        double[] prob = new double[y.length];
        for (int i = 0; i < y.length; i++) {
            double[] post = new double[2];
            pred[i] = model.predict(x[i], post);
            prob[i] = post[1];
        }
        double accuracy = Accuracy.of(y, pred);
        double auc = AUC.of(y, prob);

        supervisedModel.replace(model, sampleCount, (int) positive, (int) negative, accuracy, auc);
        supervisedModel.recordTrainingScoreDistribution(DistributionSnapshot.builder()
                .bins(10).lo(0.0).hi(1.0)
                .counts(PsiCalculator.histogram(prob, 10, 0.0, 1.0))
                .total(prob.length)
                .capturedAt(LocalDateTime.now())
                .build());
        log.info("[AI-ML] 监督模型训练完成: samples={}, acc={}, auc={}", sampleCount, accuracy, auc);

        return AiRiskTrainingResultVO.builder()
                .status("TRAINED")
                .modelReady(true)
                .sampleCount(sampleCount)
                .positiveCount((int) positive)
                .negativeCount((int) negative)
                .accuracy(accuracy)
                .auc(auc)
                .trainedAt(supervisedModel.getTrainedAt())
                .message("训练成功")
                .build();
    }

    public AiRiskTrainingResultVO trainingStatus() {
        boolean ready = supervisedModel.isReady();
        String last = supervisedModel.getLastTrainStatus();
        String reportedStatus = (last != null) ? last : (ready ? "TRAINED" : "NOT_TRAINED");
        return AiRiskTrainingResultVO.builder()
                .status(reportedStatus)
                .modelReady(ready)
                .sampleCount(supervisedModel.getSampleCount())
                .positiveCount(supervisedModel.getPositiveCount())
                .negativeCount(supervisedModel.getNegativeCount())
                .accuracy(supervisedModel.getAccuracy())
                .auc(supervisedModel.getAuc())
                .trainedAt(supervisedModel.getTrainedAt())
                .message(buildStatusMessage(ready))
                .build();
    }

    private AiRiskTrainingResultVO skip(String status, String msg, int n, int pos, int neg) {
        log.warn("[AI-ML] 跳过训练: {}", msg);
        return AiRiskTrainingResultVO.builder()
                .status(status)
                .modelReady(supervisedModel.isReady())
                .sampleCount(n)
                .positiveCount(pos)
                .negativeCount(neg)
                .message(msg)
                .build();
    }

    private Integer mapLabel(String label) {
        if ("TRUE_POSITIVE".equals(label)) {
            return 1;
        }
        if ("FALSE_POSITIVE".equals(label)) {
            return 0;
        }
        return null; // NEEDS_MONITORING and anything else excluded
    }

    private double[] parseVector(String featureSnapshotJson) {
        if (!StringUtils.hasText(featureSnapshotJson)) {
            return null;
        }
        try {
            AiRiskFeatureSummaryVO f = objectMapper.readValue(featureSnapshotJson, AiRiskFeatureSummaryVO.class);
            return vectorizer.toVector(f);
        } catch (Exception e) {
            log.warn("[AI-ML] 特征快照解析失败，跳过该样本: {}", e.getMessage());
            return null;
        }
    }

    private String buildStatusMessage(boolean ready) {
        String last = supervisedModel.getLastTrainStatus();
        String err = supervisedModel.getLastTrainError();
        if (last == null) {
            return ready ? "模型就绪" : "模型尚未训练";
        }
        if ("FAILED".equals(last) && err != null) {
            return "上次训练失败: " + err;
        }
        return ready ? "模型就绪 (上次: " + last + ")" : "模型尚未训练 (上次: " + last + ")";
    }
}
