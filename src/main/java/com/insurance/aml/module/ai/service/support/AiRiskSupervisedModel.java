package com.insurance.aml.module.ai.service.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.insurance.aml.module.ai.model.dto.DistributionSnapshot;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import smile.classification.LogisticRegression;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * AI监督模型容器。
 *
 * <p>持有 Smile 逻辑回归模型与训练元数据，线程安全，磁盘持久化。
 * 持久化与并发控制风格对齐 {@code TransactionAnomalyDetector}。</p>
 */
@Slf4j
@Component
public class AiRiskSupervisedModel {

    private static final String MODEL_FILE = "ai_risk_supervised.model";
    private static final String META_FILE = "ai_risk_supervised.meta";
    private static final String DISTRIBUTION_FILE = "supervised_distribution.json";
    private static final ObjectMapper JSON = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Value("${aml.ml.ai-risk.model-path:${aml.ml.model-path:./data/models}}")
    private String modelPath;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile boolean ready = false;
    private volatile LogisticRegression model;

    @Getter private volatile int sampleCount;
    @Getter private volatile int positiveCount;
    @Getter private volatile int negativeCount;
    @Getter private volatile double accuracy;
    @Getter private volatile double auc;
    @Getter private volatile LocalDateTime trainedAt;
    @Getter private volatile String lastTrainStatus;
    @Getter private volatile String lastTrainError;
    @Getter private volatile DistributionSnapshot trainingScoreDistribution;

    /**
     * 记录最近一次训练尝试的结果与错误信息。不改变 ready / model 引用。
     */
    public void recordOutcome(String status, String errorMessage) {
        this.lastTrainStatus = status;
        this.lastTrainError = errorMessage;
    }

    /**
     * 记录训练时输出分布作为漂移监控基线，并落盘到旁路 JSON 文件。
     */
    public void recordTrainingScoreDistribution(DistributionSnapshot snapshot) {
        this.trainingScoreDistribution = snapshot;
        try {
            Path dir = Paths.get(modelPath);
            Files.createDirectories(dir);
            Path tmp = dir.resolve(DISTRIBUTION_FILE + ".tmp");
            JSON.writeValue(tmp.toFile(), snapshot);
            Files.move(tmp, dir.resolve(DISTRIBUTION_FILE),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            log.warn("[AI-ML] 监督模型训练分布落盘失败: {}", e.getMessage());
        }
    }

    private void loadDistribution() {
        Path file = Paths.get(modelPath, DISTRIBUTION_FILE);
        if (!Files.exists(file)) {
            return;
        }
        try {
            this.trainingScoreDistribution = JSON.readValue(file.toFile(), DistributionSnapshot.class);
        } catch (Exception e) {
            log.warn("[AI-ML] 监督模型训练分布加载失败: {}", e.getMessage());
        }
    }

    public boolean isReady() {
        return ready;
    }

    @PostConstruct
    public void init() {
        try {
            load();
        } catch (Exception e) {
            log.warn("[AI-ML] 监督模型加载失败，等待训练: {}", e.getMessage());
        }
        loadDistribution();
    }

    public Optional<Double> predictProbability(double[] x) {
        if (!ready) {
            return Optional.empty();
        }
        lock.readLock().lock();
        try {
            double[] posteriori = new double[2];
            model.predict(x, posteriori);
            return Optional.of(posteriori[1]);
        } catch (Exception e) {
            log.warn("[AI-ML] 监督模型推理失败: {}", e.getMessage());
            return Optional.empty();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void replace(LogisticRegression newModel, int sampleCount, int positiveCount,
                         int negativeCount, double accuracy, double auc) {
        lock.writeLock().lock();
        try {
            this.model = newModel;
            this.sampleCount = sampleCount;
            this.positiveCount = positiveCount;
            this.negativeCount = negativeCount;
            this.accuracy = accuracy;
            this.auc = auc;
            this.trainedAt = LocalDateTime.now();
            this.ready = true;
        } finally {
            lock.writeLock().unlock();
        }
        save();
    }

    private void save() {
        try {
            Path dir = Paths.get(modelPath);
            Files.createDirectories(dir);
            Path modelTmp = dir.resolve(MODEL_FILE + ".tmp");
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(modelTmp)))) {
                oos.writeObject(model);
            }
            Files.move(modelTmp, dir.resolve(MODEL_FILE),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            Path modelFile = dir.resolve(MODEL_FILE);
            Properties props = new Properties();
            props.setProperty("sampleCount", String.valueOf(sampleCount));
            props.setProperty("positiveCount", String.valueOf(positiveCount));
            props.setProperty("negativeCount", String.valueOf(negativeCount));
            props.setProperty("accuracy", String.valueOf(accuracy));
            props.setProperty("auc", String.valueOf(auc));
            props.setProperty("trainedAt", trainedAt.toString());
            props.setProperty("modelSha256", sha256Hex(modelFile));
            Path metaTmp = dir.resolve(META_FILE + ".tmp");
            try (var w = Files.newBufferedWriter(metaTmp)) {
                props.store(w, "ai-risk supervised model meta");
            }
            Files.move(metaTmp, dir.resolve(META_FILE),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            log.info("[AI-ML] 监督模型已保存: {}", dir.toAbsolutePath());
        } catch (Exception e) {
            log.error("[AI-ML] 监督模型保存失败: {}", e.getMessage(), e);
        }
    }

    private void load() throws Exception {
        Path dir = Paths.get(modelPath);
        Path modelFile = dir.resolve(MODEL_FILE);
        Path metaFile = dir.resolve(META_FILE);
        if (!Files.exists(modelFile) || !Files.exists(metaFile)) {
            return;
        }
        lock.writeLock().lock();
        try {
            Properties props = new Properties();
            try (var r = Files.newBufferedReader(metaFile)) {
                props.load(r);
            }
            String expectedSha256 = props.getProperty("modelSha256");
            if (expectedSha256 != null && !expectedSha256.equalsIgnoreCase(sha256Hex(modelFile))) {
                throw new IllegalStateException("model artifact checksum mismatch");
            }
            try (ObjectInputStream ois = new ObjectInputStream(
                    new BufferedInputStream(Files.newInputStream(modelFile)))) {
                this.model = (LogisticRegression) ois.readObject();
            }
            this.sampleCount = Integer.parseInt(props.getProperty("sampleCount", "0"));
            this.positiveCount = Integer.parseInt(props.getProperty("positiveCount", "0"));
            this.negativeCount = Integer.parseInt(props.getProperty("negativeCount", "0"));
            this.accuracy = Double.parseDouble(props.getProperty("accuracy", "0"));
            this.auc = Double.parseDouble(props.getProperty("auc", "0"));
            String t = props.getProperty("trainedAt");
            this.trainedAt = t == null ? null : LocalDateTime.parse(t);
            this.ready = true;
            log.info("[AI-ML] 监督模型已加载: samples={}, auc={}", sampleCount, auc);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private String sha256Hex(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
