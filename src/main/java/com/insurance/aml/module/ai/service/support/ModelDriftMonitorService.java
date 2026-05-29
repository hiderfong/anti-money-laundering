package com.insurance.aml.module.ai.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.insurance.aml.common.enums.TransactionStatus;
import com.insurance.aml.module.ai.model.dto.DistributionSnapshot;
import com.insurance.aml.module.ai.model.dto.ModelDriftStatusVO;
import com.insurance.aml.module.monitoring.mapper.TransactionMapper;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import com.insurance.aml.module.monitoring.service.TransactionAnomalyDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 模型输出分布漂移监控（PSI）。比较训练时基线分布与近窗口实际分布，
 * 按阈值分级 NORMAL / WARN / SEVERE / UNAVAILABLE。仅日志告警，不自动重训。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelDriftMonitorService {

    private static final String KEY_SUPERVISED = "supervised";
    private static final String KEY_ANOMALY = "anomaly";

    private final AiRiskSupervisedModel supervisedModel;
    private final TransactionAnomalyDetector anomalyDetector;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionMapper transactionMapper;

    @Value("${aml.ml.drift.window-hours:24}")
    private int windowHours;
    @Value("${aml.ml.drift.bins:10}")
    private int bins;
    @Value("${aml.ml.drift.warn-threshold:0.1}")
    private double warnThreshold;
    @Value("${aml.ml.drift.severe-threshold:0.25}")
    private double severeThreshold;
    @Value("${aml.ml.drift.anomaly-sample-cap:10000}")
    private int anomalySampleCap;
    @Value("${aml.ml.drift.min-samples:50}")
    private int minSamples;

    @Scheduled(cron = "${aml.ml.drift.cron:0 0 4 * * ?}")
    public void scheduledDriftCheck() {
        try {
            for (ModelDriftStatusVO vo : computeAll()) {
                if ("SEVERE".equals(vo.getStatus())) {
                    log.error("[AI-Drift] {} PSI={}, 严重漂移", vo.getModelKey(), vo.getPsi());
                } else if ("WARN".equals(vo.getStatus())) {
                    log.warn("[AI-Drift] {} PSI={}, 已警示", vo.getModelKey(), vo.getPsi());
                } else {
                    log.info("[AI-Drift] {} status={}, PSI={}", vo.getModelKey(), vo.getStatus(), vo.getPsi());
                }
            }
        } catch (Exception e) {
            log.error("[AI-Drift] 定时漂移检测异常: {}", e.getMessage(), e);
        }
    }

    public List<ModelDriftStatusVO> computeAll() {
        return List.of(computeSupervisedDrift(), computeAnomalyDrift());
    }

    public ModelDriftStatusVO computeSupervisedDrift() {
        DistributionSnapshot baseline = supervisedModel.getTrainingScoreDistribution();
        if (baseline == null) {
            return unavailable(KEY_SUPERVISED, "基线缺失，需先完成训练", 0, 0);
        }
        LocalDateTime since = LocalDateTime.now().minusHours(windowHours);
        List<Double> probs = jdbcTemplate.queryForList(
                "SELECT model_probability FROM t_ai_risk_score_record "
                        + "WHERE scored_at >= ? AND model_probability IS NOT NULL",
                Double.class, since);
        return classify(KEY_SUPERVISED, baseline, toArray(probs));
    }

    public ModelDriftStatusVO computeAnomalyDrift() {
        DistributionSnapshot baseline = anomalyDetector.getTrainingScoreDistribution();
        if (baseline == null) {
            return unavailable(KEY_ANOMALY, "基线缺失，需先完成训练", 0, 0);
        }
        LocalDateTime since = LocalDateTime.now().minusHours(windowHours);
        LambdaQueryWrapper<Transaction> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(Transaction::getTransactionTime, since)
                .eq(Transaction::getStatus, TransactionStatus.SUCCESS.getCode())
                .isNotNull(Transaction::getAmount)
                .last("LIMIT " + Math.max(1, Math.min(anomalySampleCap, 100_000)));
        List<Transaction> txns = transactionMapper.selectList(wrapper);
        List<Double> scores = new ArrayList<>();
        int skipped = 0;
        for (Transaction t : txns) {
            try {
                scores.add(anomalyDetector.predict(t));
            } catch (Exception e) {
                skipped++;
            }
        }
        if (skipped > 0) {
            log.warn("[AI-Drift] anomaly 重算跳过 {} 条 (scored={})", skipped, scores.size());
        }
        return classify(KEY_ANOMALY, baseline, toArray(scores));
    }

    private ModelDriftStatusVO classify(String key, DistributionSnapshot baseline, double[] current) {
        if (current.length < minSamples) {
            return unavailable(key, "近窗口样本不足: " + current.length + " < " + minSamples,
                    current.length, baseline.getTotal());
        }
        int[] currentHist = PsiCalculator.histogram(current, baseline.getBins(), baseline.getLo(), baseline.getHi());
        double psi = PsiCalculator.psi(baseline.getCounts(), currentHist);
        if (Double.isNaN(psi) || Double.isInfinite(psi)) {
            return unavailable(key, "PSI 计算异常", current.length, baseline.getTotal());
        }
        String status;
        if (psi >= severeThreshold) {
            status = "SEVERE";
        } else if (psi >= warnThreshold) {
            status = "WARN";
        } else {
            status = "NORMAL";
        }
        return ModelDriftStatusVO.builder()
                .modelKey(key)
                .status(status)
                .psi(psi)
                .sampleCount(current.length)
                .baselineSampleCount(baseline.getTotal())
                .computedAt(LocalDateTime.now())
                .message("PSI=" + String.format(java.util.Locale.ROOT, "%.4f", psi))
                .build();
    }

    private ModelDriftStatusVO unavailable(String key, String message, int sampleCount, int baselineCount) {
        return ModelDriftStatusVO.builder()
                .modelKey(key)
                .status("UNAVAILABLE")
                .psi(null)
                .sampleCount(sampleCount)
                .baselineSampleCount(baselineCount)
                .computedAt(LocalDateTime.now())
                .message(message)
                .build();
    }

    private double[] toArray(List<Double> values) {
        double[] arr = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            Double v = values.get(i);
            arr[i] = v == null ? 0.0 : v;
        }
        return arr;
    }
}
