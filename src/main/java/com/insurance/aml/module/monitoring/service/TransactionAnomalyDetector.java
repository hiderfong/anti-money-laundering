package com.insurance.aml.module.monitoring.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.insurance.aml.common.enums.TransactionStatus;
import com.insurance.aml.module.ai.model.dto.AnomalyTrainingResultVO;
import com.insurance.aml.module.monitoring.mapper.TransactionMapper;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import smile.anomaly.IsolationForest;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * 交易异常检测服务 - 基于Smile ML库的Isolation Forest算法
 *
 * 算法原理：
 * - Isolation Forest通过随机选择特征和分割点构建多棵决策树
 * - 异常点(离群值)在树中路径更短，更容易被隔离
 * - 平均路径长度越短，异常分数越高(接近1.0)
 * - 正常数据的异常分数接近0.5，低于0.5表示正常
 *
 * 交易特征向量(6维):
 * 1. amount         - 交易金额(归一化)
 * 2. isCrossBorder  - 是否跨境(0/1)
 * 3. hourOfDay      - 交易时间小时(0-23, 归一化)
 * 4. recentTxnCount - 近7天交易次数(归一化)
 * 5. avgAmount      - 近7天平均金额(归一化)
 * 6. amountDeviation- 金额偏离度 = (amount - avg) / std
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionAnomalyDetector {

    private final TransactionMapper transactionMapper;

    @Value("${aml.ml.model-path:./data/models}")
    private String modelPath;

    @Value("${aml.ml.training-days:90}")
    private int trainingDays;

    @Value("${aml.ml.anomaly-threshold:0.7}")
    private double anomalyThreshold;

    @Value("${aml.ml.num-trees:100}")
    private int numTrees;

    @Value("${aml.ml.subsample-size:256}")
    private int subsampleSize;

    @Value("${aml.ml.anomaly.retrain-cron:0 30 3 * * SUN}")
    private String retrainCron;

    @Value("${aml.ml.anomaly.min-samples:100}")
    private int minSamples;

    /** 特征维度 */
    private static final int FEATURE_DIM = 6;

    /** 特征归一化参数 (从训练数据中计算) */
    private double amountMean = 0.0;
    private double amountStd = 1.0;
    private double countMean = 0.0;
    private double countStd = 1.0;
    private double avgAmountMean = 0.0;
    private double avgAmountStd = 1.0;

    /** 已训练的Isolation Forest模型 */
    private IsolationForest model;

    /** 读写锁保护模型的并发访问 */
    private final ReentrantReadWriteLock modelLock = new ReentrantReadWriteLock();

    /** 模型是否已加载/训练 */
    private volatile boolean modelReady = false;

    private final AtomicBoolean trainingInProgress = new AtomicBoolean(false);
    @Getter private volatile String lastTrainStatus;
    @Getter private volatile String lastTrainError;
    @Getter private volatile int lastTrainSampleCount;
    @Getter private volatile long trainDurationMs;
    @Getter private volatile LocalDateTime lastTrainedAt;

    public boolean isModelReady() {
        return modelReady;
    }

    /**
     * 应用启动时尝试加载已保存的模型
     */
    @PostConstruct
    public void init() {
        try {
            if (loadModel()) {
                log.info("[ML] 已从磁盘加载Isolation Forest模型, 路径={}", modelPath);
            } else {
                log.info("[ML] 未找到已保存的模型，将等待首次训练");
            }
        } catch (Exception e) {
            log.warn("[ML] 加载模型失败，将等待下次训练: {}", e.getMessage());
        }
    }

    // ====================================================================
    // 模型训练
    // ====================================================================

    /**
     * 治理化训练入口：CAS 串行化 + min-samples 守卫 + 状态记录 + 异常兜底。
     */
    public AnomalyTrainingResultVO retrain() {
        if (!trainingInProgress.compareAndSet(false, true)) {
            log.warn("[ML] 已有训练正在进行，跳过重复触发");
            this.lastTrainStatus = "SKIPPED_IN_PROGRESS";
            return AnomalyTrainingResultVO.builder()
                    .status("SKIPPED_IN_PROGRESS")
                    .modelReady(modelReady)
                    .message("训练正在进行中")
                    .build();
        }
        long start = System.currentTimeMillis();
        try {
            int sampleCount;
            try {
                sampleCount = doTrain();
            } catch (Exception e) {
                log.error("[ML] Isolation Forest 训练失败: {}", e.getMessage(), e);
                this.lastTrainStatus = "FAILED";
                this.lastTrainError = e.getClass().getSimpleName() + ": " + e.getMessage();
                this.trainDurationMs = System.currentTimeMillis() - start;
                return AnomalyTrainingResultVO.builder()
                        .status("FAILED")
                        .modelReady(modelReady)
                        .message(this.lastTrainError)
                        .trainDurationMs(this.trainDurationMs)
                        .build();
            }

            this.trainDurationMs = System.currentTimeMillis() - start;
            if (sampleCount < minSamples) {
                this.lastTrainStatus = "SKIPPED_INSUFFICIENT";
                return AnomalyTrainingResultVO.builder()
                        .status("SKIPPED_INSUFFICIENT")
                        .modelReady(modelReady)
                        .sampleCount(sampleCount)
                        .trainDurationMs(this.trainDurationMs)
                        .message("训练样本不足: " + sampleCount + " < " + minSamples)
                        .build();
            }
            this.lastTrainStatus = "TRAINED";
            this.lastTrainError = null;
            this.lastTrainSampleCount = sampleCount;
            this.lastTrainedAt = LocalDateTime.now();
            return AnomalyTrainingResultVO.builder()
                    .status("TRAINED")
                    .modelReady(modelReady)
                    .sampleCount(sampleCount)
                    .trainDurationMs(this.trainDurationMs)
                    .trainedAt(this.lastTrainedAt)
                    .message("训练完成")
                    .build();
        } finally {
            trainingInProgress.set(false);
        }
    }

    @Scheduled(cron = "${aml.ml.anomaly.retrain-cron:0 30 3 * * SUN}")
    public void scheduledRetrain() {
        try {
            AnomalyTrainingResultVO result = retrain();
            log.info("[ML-Scheduler] 定时重训完成: status={}, samples={}, duration={}ms",
                    result.getStatus(), result.getSampleCount(), result.getTrainDurationMs());
        } catch (Exception e) {
            log.error("[ML-Scheduler] 定时重训外层异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 从历史交易数据训练Isolation Forest模型
     *
     * 流程:
     * 1. 查询近N天的历史交易数据
     * 2. 为每笔交易提取特征向量
     * 3. 计算全局统计量用于归一化
     * 4. 训练Isolation Forest模型
     * 5. 持久化模型到磁盘
     *
     * @return 训练样本数量
     */
    private int doTrain() {
        log.info("[ML] 开始训练Isolation Forest模型, 训练窗口={}天", trainingDays);

        long startTime = System.currentTimeMillis();

        // 1. 查询历史交易
        LocalDateTime since = LocalDateTime.now().minusDays(trainingDays);
        LambdaQueryWrapper<Transaction> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(Transaction::getTransactionTime, since)
               .eq(Transaction::getStatus, TransactionStatus.SUCCESS.getCode())
               .isNotNull(Transaction::getAmount)
               .isNotNull(Transaction::getTransactionTime)
               .orderByDesc(Transaction::getTransactionTime)
               .last("LIMIT 50000"); // 限制训练集大小

        List<Transaction> transactions = transactionMapper.selectList(wrapper);

        if (transactions.isEmpty()) {
            log.warn("[ML] 无可用历史交易数据进行训练");
            return 0;
        }

        if (transactions.size() < 50) {
            log.warn("[ML] 训练样本不足(至少需要50条), 当前仅有{}条", transactions.size());
            return 0;
        }

        log.info("[ML] 加载到{}条历史交易数据", transactions.size());

        // 2. 计算全局统计量
        computeGlobalStats(transactions);

        // 3. 构建特征矩阵
        double[][] features = new double[transactions.size()][FEATURE_DIM];
        for (int i = 0; i < transactions.size(); i++) {
            features[i] = extractFeatureVector(transactions.get(i));
        }

        // 4. 训练Isolation Forest
        // fit(data, ntrees, subsampleSize, psi, maxDepth)
        // psi: proportion of outliers expected (0.01 is a reasonable default)
        // maxDepth: ceil(log2(subsampleSize))
        int maxDepth = (int) Math.ceil(Math.log(subsampleSize) / Math.log(2));

        try {
            model = IsolationForest.fit(features, numTrees, subsampleSize, 0.01, maxDepth);
            modelReady = true;

            long duration = System.currentTimeMillis() - startTime;
            log.info("[ML] Isolation Forest模型训练完成: 样本数={}, 特征维度={}, 树数量={}, 耗时={}ms",
                    transactions.size(), FEATURE_DIM, numTrees, duration);

            // 5. 持久化模型
            saveModel();

            // 输出训练集异常分数分布
            logAnomalyDistribution(features);

            return transactions.size();

        } catch (Exception e) {
            log.error("[ML] Isolation Forest训练失败: {}", e.getMessage(), e);
            modelReady = false;
            return 0;
        }
    }

    // ====================================================================
    // 异常预测
    // ====================================================================

    /**
     * 对单笔交易计算异常分数
     *
     * @param transaction 交易对象
     * @return 异常分数 [0.0, 1.0]，越高越异常
     *         - score < 0.5 : 正常
     *         - 0.5 <= score < 0.7 : 轻度异常(关注)
     *         - 0.7 <= score < 0.85 : 中度异常(预警)
     *         - score >= 0.85 : 重度异常(高危)
     */
    public double predict(Transaction transaction) {
        if (!modelReady || model == null) {
            log.debug("[ML] 模型未就绪，跳过异常检测: transactionNo={}", transaction.getTransactionNo());
            return 0.0;
        }

        try {
            double[] features = extractFeatureVector(transaction);

            modelLock.readLock().lock();
            try {
                double score = model.score(features);
                // Isolation Forest的score: 值越大越异常，范围一般在[0,1]
                // 确保在合理范围内
                score = Math.max(0.0, Math.min(1.0, score));

                log.debug("[ML] 交易异常评分: transactionNo={}, score={}", 
                        transaction.getTransactionNo(), score);
                return score;
            } finally {
                modelLock.readLock().unlock();
            }

        } catch (Exception e) {
            log.error("[ML] 异常评分失败: transactionNo={}, error={}", 
                    transaction.getTransactionNo(), e.getMessage(), e);
            return 0.0;
        }
    }

    /**
     * 判断交易是否异常
     */
    public boolean isAnomaly(Transaction transaction) {
        return predict(transaction) >= anomalyThreshold;
    }

    /**
     * 获取模型状态信息
     */
    public Map<String, Object> getModelStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("modelReady", modelReady);
        status.put("anomalyThreshold", anomalyThreshold);
        status.put("numTrees", numTrees);
        status.put("subsampleSize", subsampleSize);
        status.put("trainingDays", trainingDays);
        status.put("featureDim", FEATURE_DIM);
        status.put("featureNames", List.of(
                "amount(归一化)", "isCrossBorder(0/1)", "hourOfDay(归一化)",
                "recentTxnCount(归一化)", "avgAmount(归一化)", "amountDeviation"));
        status.put("normalizationStats", Map.of(
                "amountMean", amountMean, "amountStd", amountStd,
                "countMean", countMean, "countStd", countStd,
                "avgAmountMean", avgAmountMean, "avgAmountStd", avgAmountStd));
        return status;
    }

    // ====================================================================
    // 特征工程
    // ====================================================================

    /**
     * 提取交易特征向量
     *
     * 特征:
     * [0] amount         - 归一化金额: (amount - mean) / std
     * [1] isCrossBorder  - 是否跨境: 0 或 1
     * [2] hourOfDay      - 归一化小时: hour / 23.0
     * [3] recentTxnCount - 归一化近7天交易次数: (count - mean) / std
     * [4] avgAmount      - 归一化近7天平均金额: (avg - mean) / std
     * [5] amountDeviation- 金额偏离度: (amount - avg) / std  (同客户历史)
     */
    private double[] extractFeatureVector(Transaction txn) {
        double[] features = new double[FEATURE_DIM];

        // [0] 金额归一化
        double amount = txn.getAmount() != null ? txn.getAmount().doubleValue() : 0.0;
        features[0] = normalize(amount, amountMean, amountStd);

        // [1] 是否跨境
        features[1] = Boolean.TRUE.equals(txn.getIsCrossBorder()) ? 1.0 : 0.0;

        // [2] 交易小时归一化
        int hour = txn.getTransactionTime() != null ? txn.getTransactionTime().getHour() : 12;
        features[2] = hour / 23.0;

        // [3]-[5] 需要查询客户近期统计
        if (txn.getCustomerId() != null && txn.getId() != null) {
            double[] customerStats = getCustomerRecentStats(txn.getCustomerId(), txn.getId());
            features[3] = normalize(customerStats[0], countMean, countStd);   // recentTxnCount
            features[4] = normalize(customerStats[1], avgAmountMean, avgAmountStd); // avgAmount
            // 金额偏离度
            double std = customerStats[2];
            if (std > 0.001) {
                features[5] = (amount - customerStats[1]) / std;
                features[5] = Math.max(-3.0, Math.min(3.0, features[5])); // 截断到[-3,3]
            } else {
                features[5] = 0.0;
            }
        } else {
            features[3] = 0.0;
            features[4] = 0.0;
            features[5] = 0.0;
        }

        return features;
    }

    /**
     * 获取客户近7天交易统计
     *
     * @return [recentCount, avgAmount, stdAmount]
     */
    private double[] getCustomerRecentStats(Long customerId, Long excludeTxnId) {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        LambdaQueryWrapper<Transaction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Transaction::getCustomerId, customerId)
               .ge(Transaction::getTransactionTime, since)
               .eq(Transaction::getStatus, TransactionStatus.SUCCESS.getCode())
               .ne(Transaction::getId, excludeTxnId)
               .select(Transaction::getAmount);

        List<Transaction> recentTxns = transactionMapper.selectList(wrapper);

        if (recentTxns.isEmpty()) {
            return new double[]{0.0, 0.0, 0.0};
        }

        double count = recentTxns.size();
        double[] amounts = recentTxns.stream()
                .mapToDouble(t -> t.getAmount() != null ? t.getAmount().doubleValue() : 0.0)
                .toArray();

        double avg = Arrays.stream(amounts).average().orElse(0.0);
        double variance = Arrays.stream(amounts)
                .map(a -> (a - avg) * (a - avg))
                .average().orElse(0.0);
        double std = Math.sqrt(variance);

        return new double[]{count, avg, std};
    }

    /**
     * 计算训练数据的全局统计量(用于归一化)
     */
    private void computeGlobalStats(List<Transaction> transactions) {
        // 金额统计
        double[] amounts = transactions.stream()
                .mapToDouble(t -> t.getAmount().doubleValue())
                .toArray();
        amountMean = Arrays.stream(amounts).average().orElse(0.0);
        double amountVariance = Arrays.stream(amounts)
                .map(a -> (a - amountMean) * (a - amountMean))
                .average().orElse(1.0);
        amountStd = Math.sqrt(amountVariance);
        if (amountStd < 0.001) amountStd = 1.0;

        // 近7天交易次数统计(用单日平均 * 7近似)
        Map<Long, Long> customerTxnCounts = transactions.stream()
                .collect(Collectors.groupingBy(Transaction::getCustomerId, Collectors.counting()));
        double[] counts = customerTxnCounts.values().stream()
                .mapToDouble(Long::doubleValue).toArray();
        countMean = Arrays.stream(counts).average().orElse(0.0);
        double countVariance = Arrays.stream(counts)
                .map(c -> (c - countMean) * (c - countMean))
                .average().orElse(1.0);
        countStd = Math.sqrt(countVariance);
        if (countStd < 0.001) countStd = 1.0;

        // 平均金额统计
        Map<Long, Double> customerAvgAmounts = transactions.stream()
                .collect(Collectors.groupingBy(
                        Transaction::getCustomerId,
                        Collectors.averagingDouble(t -> t.getAmount().doubleValue())));
        double[] avgs = customerAvgAmounts.values().stream().mapToDouble(Double::doubleValue).toArray();
        avgAmountMean = Arrays.stream(avgs).average().orElse(0.0);
        double avgVariance = Arrays.stream(avgs)
                .map(a -> (a - avgAmountMean) * (a - avgAmountMean))
                .average().orElse(1.0);
        avgAmountStd = Math.sqrt(avgVariance);
        if (avgAmountStd < 0.001) avgAmountStd = 1.0;

        log.info("[ML] 全局统计量: amount(mean={}, std={}), count(mean={}, std={}), avgAmount(mean={}, std={})",
                formatDouble(amountMean), formatDouble(amountStd),
                formatDouble(countMean), formatDouble(countStd),
                formatDouble(avgAmountMean), formatDouble(avgAmountStd));
    }

    /**
     * 归一化: (value - mean) / std
     */
    private double normalize(double value, double mean, double std) {
        if (std < 0.001) return 0.0;
        double normalized = (value - mean) / std;
        return Math.max(-5.0, Math.min(5.0, normalized)); // 截断到[-5,5]避免极端值
    }

    // ====================================================================
    // 模型持久化
    // ====================================================================

    /**
     * 保存模型到磁盘
     */
    private void saveModel() {
        modelLock.writeLock().lock();
        try {
            Path dir = Paths.get(modelPath);
            Files.createDirectories(dir);

            // 保存Isolation Forest模型
            Path modelFile = dir.resolve("isolation_forest.model");
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(modelFile)))) {
                oos.writeObject(model);
            }

            // 保存归一化参数
            Path statsFile = dir.resolve("normalization.stats");
            try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(statsFile))) {
                pw.println("amountMean=" + amountMean);
                pw.println("amountStd=" + amountStd);
                pw.println("countMean=" + countMean);
                pw.println("countStd=" + countStd);
                pw.println("avgAmountMean=" + avgAmountMean);
                pw.println("avgAmountStd=" + avgAmountStd);
                pw.println("trainTime=" + System.currentTimeMillis());
            }

            log.info("[ML] 模型已保存到: {}", dir.toAbsolutePath());
        } catch (Exception e) {
            log.error("[ML] 模型保存失败: {}", e.getMessage(), e);
        } finally {
            modelLock.writeLock().unlock();
        }
    }

    /**
     * 从磁盘加载模型
     *
     * @return true 如果加载成功
     */
    private boolean loadModel() {
        Path modelFile = Paths.get(modelPath, "isolation_forest.model");
        Path statsFile = Paths.get(modelPath, "normalization.stats");

        if (!Files.exists(modelFile) || !Files.exists(statsFile)) {
            return false;
        }

        modelLock.writeLock().lock();
        try {
            // 加载模型
            try (ObjectInputStream ois = new ObjectInputStream(
                    new BufferedInputStream(Files.newInputStream(modelFile)))) {
                model = (IsolationForest) ois.readObject();
            }

            // 加载归一化参数
            Properties props = new Properties();
            try (BufferedReader reader = Files.newBufferedReader(statsFile)) {
                props.load(reader);
            }
            amountMean = Double.parseDouble(props.getProperty("amountMean", "0"));
            amountStd = Double.parseDouble(props.getProperty("amountStd", "1"));
            countMean = Double.parseDouble(props.getProperty("countMean", "0"));
            countStd = Double.parseDouble(props.getProperty("countStd", "1"));
            avgAmountMean = Double.parseDouble(props.getProperty("avgAmountMean", "0"));
            avgAmountStd = Double.parseDouble(props.getProperty("avgAmountStd", "1"));

            modelReady = true;
            return true;

        } catch (Exception e) {
            log.error("[ML] 加载模型失败: {}", e.getMessage(), e);
            modelReady = false;
            return false;
        } finally {
            modelLock.writeLock().unlock();
        }
    }

    // ====================================================================
    // 辅助方法
    // ====================================================================

    /**
     * 输出训练集异常分数分布(用于调参诊断)
     */
    private void logAnomalyDistribution(double[][] features) {
        try {
            double[] scores = new double[features.length];
            for (int i = 0; i < features.length; i++) {
                scores[i] = model.score(features[i]);
            }

            Arrays.sort(scores);
            int len = scores.length;
            log.info("[ML] 训练集异常分数分布: min={}, P10={}, P25={}, P50={}, P75={}, P90={}, P95={}, P99={}, max={}",
                    formatDouble(scores[0]),
                    formatDouble(scores[(int)(len * 0.1)]),
                    formatDouble(scores[(int)(len * 0.25)]),
                    formatDouble(scores[(int)(len * 0.5)]),
                    formatDouble(scores[(int)(len * 0.75)]),
                    formatDouble(scores[(int)(len * 0.9)]),
                    formatDouble(scores[(int)(len * 0.95)]),
                    formatDouble(scores[(int)(len * 0.99)]),
                    formatDouble(scores[len - 1]));

            long highAnomaly = Arrays.stream(scores).filter(s -> s >= 0.85).count();
            long mediumAnomaly = Arrays.stream(scores).filter(s -> s >= 0.7 && s < 0.85).count();
            log.info("[ML] 预警统计(阈值={}): 高危(>=0.85)={}, 中危(0.7-0.85)={}, 正常={}",
                    anomalyThreshold, highAnomaly, mediumAnomaly, len - highAnomaly - mediumAnomaly);

        } catch (Exception e) {
            log.debug("[ML] 异常分数分布计算失败: {}", e.getMessage());
        }
    }

    private String formatDouble(double value) {
        return String.format("%.4f", value);
    }
}
