# AI Risk Supervised Feedback Loop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the AML feedback loop by training an in-JVM Smile supervised model from accumulated manual review labels and running it in shadow alongside the unchanged rule-based score.

**Architecture:** Three new `@Component`s under `module/ai/service/support`: a pure feature vectorizer (single source of truth shared by training and inference), a thread-safe persisted model holder mirroring `TransactionAnomalyDetector`, and a training service triggered by `@Scheduled` + REST. The rule-scoring main path is untouched; `persistScoreRecord` writes shadow `model_probability`/`model_label_predicted` inside a try/catch that never degrades scoring.

**Tech Stack:** Java 21, Spring Boot 3.4, MyBatis-Plus, Smile 3.0.1 (`com.github.haifengl:smile-core`), Flyway, JUnit 5 + Mockito, H2 (integration tests).

---

## File Structure

| File | Responsibility |
|------|----------------|
| `src/main/resources/db/migration/V016__ai_risk_supervised_shadow.sql` | Add `model_probability`, `model_label_predicted` columns (idempotent) |
| `src/test/resources/schema-h2.sql` (modify) | Mirror the two columns for integration tests |
| `module/ai/model/entity/AiRiskScoreRecord.java` (modify) | Add two shadow fields |
| `module/ai/model/dto/AiRiskTrainingResultVO.java` (create) | Training/retrain outcome + metrics |
| `module/ai/service/support/AiRiskFeatureVectorizer.java` (create) | `AiRiskFeatureSummaryVO → double[]`, fixed order, single out |
| `module/ai/service/support/AiRiskSupervisedModel.java` (create) | Holds Smile model + meta; thread-safe predict; disk save/load |
| `module/ai/service/support/AiRiskModelTrainingService.java` (create) | Pull labeled records → train → evaluate → hot-swap; `@Scheduled` + manual |
| `module/ai/service/AiRiskScoringService.java` (modify) | Add `retrainModel()`, `trainingStatus()` |
| `module/ai/service/impl/AiRiskScoringServiceImpl.java` (modify) | Delegate new methods; shadow inference in `persistScoreRecord` |
| `module/ai/controller/AiRiskScoringController.java` (modify) | `POST /ai/risk/model/retrain`, `GET /ai/risk/model/training-status` |
| `src/main/resources/application.yml` (modify) | `aml.ml.ai-risk.*` config |
| Tests | `AiRiskFeatureVectorizerTest`, `AiRiskSupervisedModelTest`, `AiRiskModelTrainingServiceTest`, extend `AiRiskScoringIntegrationTest` |

All Java paths are under `src/main/java/com/insurance/aml/` and tests under `src/test/java/com/insurance/aml/`.

**Smile 3.0.1 API note:** Use `smile.classification.LogisticRegression.fit(double[][] x, int[] y)` → returns `LogisticRegression` (implements `Serializable`). Probability via `int predict(double[] x, double[] posteriori)` where `posteriori` has length 2 and `posteriori[1]` is P(class=1). Metrics: `smile.validation.metric.Accuracy.of(int[] truth, int[] pred)` and `smile.validation.metric.AUC.of(int[] truth, double[] prob)`. If a signature differs in 3.0.1, adapt the call but keep behavior; do not change the public method contracts defined here.

---

### Task 1: Database column + entity + H2 schema

**Files:**
- Create: `src/main/resources/db/migration/V016__ai_risk_supervised_shadow.sql`
- Modify: `src/main/java/com/insurance/aml/module/ai/model/entity/AiRiskScoreRecord.java`
- Modify: `src/test/resources/schema-h2.sql`

- [ ] **Step 1: Create the migration**

Create `src/main/resources/db/migration/V016__ai_risk_supervised_shadow.sql`:

```sql
-- ============================================================================
-- 项目：保险公司反洗钱管理系统（AML System）
-- 文件：V016__ai_risk_supervised_shadow.sql
-- 描述：监督模型影子评分字段（概率与预测标签）
-- ============================================================================

SET NAMES utf8mb4;

SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `t_ai_risk_score_record` ADD COLUMN `model_probability` DECIMAL(5,4) DEFAULT NULL COMMENT ''监督模型可疑概率(影子)'' AFTER `reviewed_at`',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_ai_risk_score_record'
    AND COLUMN_NAME = 'model_probability'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `t_ai_risk_score_record` ADD COLUMN `model_label_predicted` VARCHAR(16) DEFAULT NULL COMMENT ''监督模型预测标签(影子)'' AFTER `model_probability`',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_ai_risk_score_record'
    AND COLUMN_NAME = 'model_label_predicted'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
```

- [ ] **Step 2: Add entity fields**

In `AiRiskScoreRecord.java`, after the `private LocalDateTime reviewedAt;` line and before the closing brace, add:

```java
    private java.math.BigDecimal modelProbability;
    private String modelLabelPredicted;
```

- [ ] **Step 3: Mirror columns in H2 test schema**

In `src/test/resources/schema-h2.sql`, find the `t_ai_risk_score_record` table definition. Add these two columns to the column list (before the closing `);` of that `CREATE TABLE`, matching the file's existing comma/formatting style):

```sql
    model_probability DECIMAL(5,4) DEFAULT NULL,
    model_label_predicted VARCHAR(16) DEFAULT NULL
```

(If the last existing column has no trailing comma, add a comma to it first.)

- [ ] **Step 4: Compile to verify entity is valid**

Run: `mvn -q test-compile`
Expected: exit 0, no errors.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/V016__ai_risk_supervised_shadow.sql \
        src/main/java/com/insurance/aml/module/ai/model/entity/AiRiskScoreRecord.java \
        src/test/resources/schema-h2.sql
git commit -m "feat: add supervised shadow scoring columns (V016)"
```

---

### Task 2: AiRiskFeatureVectorizer (TDD)

Single source of truth turning `AiRiskFeatureSummaryVO` into a fixed-order numeric vector. Pure, no dependencies.

**Files:**
- Create: `src/main/java/com/insurance/aml/module/ai/service/support/AiRiskFeatureVectorizer.java`
- Test: `src/test/java/com/insurance/aml/module/ai/service/AiRiskFeatureVectorizerTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/insurance/aml/module/ai/service/AiRiskFeatureVectorizerTest.java`:

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=AiRiskFeatureVectorizerTest`
Expected: FAIL/compile error — `AiRiskFeatureVectorizer` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/com/insurance/aml/module/ai/service/support/AiRiskFeatureVectorizer.java`:

```java
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=AiRiskFeatureVectorizerTest`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/insurance/aml/module/ai/service/support/AiRiskFeatureVectorizer.java \
        src/test/java/com/insurance/aml/module/ai/service/AiRiskFeatureVectorizerTest.java
git commit -m "feat: add AiRiskFeatureVectorizer (single vectorization out)"
```

---

### Task 3: AiRiskTrainingResultVO + AiRiskSupervisedModel (TDD)

**Files:**
- Create: `src/main/java/com/insurance/aml/module/ai/model/dto/AiRiskTrainingResultVO.java`
- Create: `src/main/java/com/insurance/aml/module/ai/service/support/AiRiskSupervisedModel.java`
- Test: `src/test/java/com/insurance/aml/module/ai/service/AiRiskSupervisedModelTest.java`

- [ ] **Step 1: Create the result DTO (no test needed — plain data holder)**

Create `src/main/java/com/insurance/aml/module/ai/model/dto/AiRiskTrainingResultVO.java`:

```java
package com.insurance.aml.module.ai.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 监督模型训练/状态结果。
 */
@Data
@Builder
public class AiRiskTrainingResultVO {

    /** TRAINED / SKIPPED_INSUFFICIENT / SKIPPED_SINGLE_CLASS / NOT_TRAINED */
    private String status;
    private boolean modelReady;
    private int sampleCount;
    private int positiveCount;
    private int negativeCount;
    private double accuracy;
    private double auc;
    private LocalDateTime trainedAt;
    private String message;
}
```

- [ ] **Step 2: Write the failing test**

Create `src/test/java/com/insurance/aml/module/ai/service/AiRiskSupervisedModelTest.java`:

```java
package com.insurance.aml.module.ai.service;

import com.insurance.aml.module.ai.service.support.AiRiskSupervisedModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import smile.classification.LogisticRegression;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AI监督模型容器测试")
class AiRiskSupervisedModelTest {

    private AiRiskSupervisedModel newModel(Path dir) {
        AiRiskSupervisedModel m = new AiRiskSupervisedModel();
        ReflectionTestUtils.setField(m, "modelPath", dir.toString());
        return m;
    }

    private LogisticRegression trainTiny() {
        double[][] x = {{0,0,0,0,0,0,0,0,0,0,0,0}, {9,9,9,9,9,9,9,9,9,9,9,9},
                        {0,0,0,0,0,0,0,0,0,0,0,1}, {8,8,8,8,8,8,8,8,8,8,8,8}};
        int[] y = {0, 1, 0, 1};
        return LogisticRegression.fit(x, y);
    }

    @Test
    @DisplayName("未训练时predictProbability返回empty")
    void notReady_returnsEmpty() {
        AiRiskSupervisedModel m = new AiRiskSupervisedModel();
        assertFalse(m.isReady());
        assertTrue(m.predictProbability(new double[12]).isEmpty());
    }

    @Test
    @DisplayName("replace后就绪且能给出概率，存盘加载往返一致")
    void replaceThenSaveLoadRoundTrip(@TempDir Path dir) {
        AiRiskSupervisedModel m = newModel(dir);
        m.replace(trainTiny(), 4, 2, 2, 1.0, 1.0);
        assertTrue(m.isReady());
        Optional<Double> p = m.predictProbability(new double[]{9,9,9,9,9,9,9,9,9,9,9,9});
        assertTrue(p.isPresent());
        assertTrue(p.get() >= 0.0 && p.get() <= 1.0);

        AiRiskSupervisedModel reloaded = newModel(dir);
        reloaded.init();
        assertTrue(reloaded.isReady());
        assertEquals(4, reloaded.getSampleCount());
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn -q test -Dtest=AiRiskSupervisedModelTest`
Expected: FAIL/compile error — `AiRiskSupervisedModel` does not exist.

- [ ] **Step 4: Write implementation**

Create `src/main/java/com/insurance/aml/module/ai/service/support/AiRiskSupervisedModel.java`:

```java
package com.insurance.aml.module.ai.service.support;

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
import java.time.LocalDateTime;
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

    @Value("${aml.ml.ai-risk.model-path:./data/models}")
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
            save();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void save() {
        try {
            Path dir = Paths.get(modelPath);
            Files.createDirectories(dir);
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(dir.resolve(MODEL_FILE))))) {
                oos.writeObject(model);
            }
            Properties props = new Properties();
            props.setProperty("sampleCount", String.valueOf(sampleCount));
            props.setProperty("positiveCount", String.valueOf(positiveCount));
            props.setProperty("negativeCount", String.valueOf(negativeCount));
            props.setProperty("accuracy", String.valueOf(accuracy));
            props.setProperty("auc", String.valueOf(auc));
            props.setProperty("trainedAt", trainedAt.toString());
            try (var w = Files.newBufferedWriter(dir.resolve(META_FILE))) {
                props.store(w, "ai-risk supervised model meta");
            }
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
            try (ObjectInputStream ois = new ObjectInputStream(
                    new BufferedInputStream(Files.newInputStream(modelFile)))) {
                this.model = (LogisticRegression) ois.readObject();
            }
            Properties props = new Properties();
            try (var r = Files.newBufferedReader(metaFile)) {
                props.load(r);
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
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -q test -Dtest=AiRiskSupervisedModelTest`
Expected: PASS, 2 tests. If Smile 3.0.1's `predict(double[], double[])` signature differs, adjust the call inside `predictProbability` only (keep returning `Optional<Double>` of the positive-class probability) and re-run.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/insurance/aml/module/ai/model/dto/AiRiskTrainingResultVO.java \
        src/main/java/com/insurance/aml/module/ai/service/support/AiRiskSupervisedModel.java \
        src/test/java/com/insurance/aml/module/ai/service/AiRiskSupervisedModelTest.java
git commit -m "feat: add AiRiskSupervisedModel container with disk persistence"
```

---

### Task 4: AiRiskModelTrainingService (TDD)

**Files:**
- Create: `src/main/java/com/insurance/aml/module/ai/service/support/AiRiskModelTrainingService.java`
- Test: `src/test/java/com/insurance/aml/module/ai/service/AiRiskModelTrainingServiceTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/insurance/aml/module/ai/service/AiRiskModelTrainingServiceTest.java`:

```java
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AI监督模型训练服务测试")
class AiRiskModelTrainingServiceTest {

    @Mock
    AiRiskScoreRecordMapper mapper;

    private AiRiskModelTrainingService newService() {
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
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=AiRiskModelTrainingServiceTest`
Expected: FAIL/compile error — `AiRiskModelTrainingService` does not exist.

- [ ] **Step 3: Write implementation**

Create `src/main/java/com/insurance/aml/module/ai/service/support/AiRiskModelTrainingService.java`:

```java
package com.insurance.aml.module.ai.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.aml.module.ai.mapper.AiRiskScoreRecordMapper;
import com.insurance.aml.module.ai.model.dto.AiRiskFeatureSummaryVO;
import com.insurance.aml.module.ai.model.dto.AiRiskTrainingResultVO;
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
        return AiRiskTrainingResultVO.builder()
                .status(supervisedModel.isReady() ? "TRAINED" : "NOT_TRAINED")
                .modelReady(supervisedModel.isReady())
                .sampleCount(supervisedModel.getSampleCount())
                .positiveCount(supervisedModel.getPositiveCount())
                .negativeCount(supervisedModel.getNegativeCount())
                .accuracy(supervisedModel.getAccuracy())
                .auc(supervisedModel.getAuc())
                .trainedAt(supervisedModel.getTrainedAt())
                .message(supervisedModel.isReady() ? "模型就绪" : "模型尚未训练")
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
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=AiRiskModelTrainingServiceTest`
Expected: PASS, 3 tests. If `Accuracy.of` / `AUC.of` import paths differ in Smile 3.0.1, fix the import to the correct `smile.validation.metric.*` class; keep the computed values feeding the same builder fields.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/insurance/aml/module/ai/service/support/AiRiskModelTrainingService.java \
        src/test/java/com/insurance/aml/module/ai/service/AiRiskModelTrainingServiceTest.java
git commit -m "feat: add AiRiskModelTrainingService (labeled retrain + metrics)"
```

---

### Task 5: Wire interface, shadow inference, controller

**Files:**
- Modify: `src/main/java/com/insurance/aml/module/ai/service/AiRiskScoringService.java`
- Modify: `src/main/java/com/insurance/aml/module/ai/service/impl/AiRiskScoringServiceImpl.java`
- Modify: `src/main/java/com/insurance/aml/module/ai/controller/AiRiskScoringController.java`

- [ ] **Step 1: Add interface methods**

In `AiRiskScoringService.java`, add the import `import com.insurance.aml.module.ai.model.dto.AiRiskTrainingResultVO;` and add to the interface body:

```java
    AiRiskTrainingResultVO retrainModel();

    AiRiskTrainingResultVO trainingStatus();
```

- [ ] **Step 2: Wire impl — inject collaborators**

In `AiRiskScoringServiceImpl.java`, add imports:

```java
import com.insurance.aml.module.ai.model.dto.AiRiskTrainingResultVO;
import com.insurance.aml.module.ai.service.support.AiRiskFeatureVectorizer;
import com.insurance.aml.module.ai.service.support.AiRiskModelTrainingService;
import com.insurance.aml.module.ai.service.support.AiRiskSupervisedModel;
```

Add three fields alongside the existing `private final ... reviewService;` declarations:

```java
    private final AiRiskSupervisedModel supervisedModel;
    private final AiRiskFeatureVectorizer featureVectorizer;
    private final AiRiskModelTrainingService trainingService;
```

- [ ] **Step 3: Add delegating methods**

In `AiRiskScoringServiceImpl.java`, after the existing `exportReviewPool` override, add:

```java
    @Override
    public AiRiskTrainingResultVO retrainModel() {
        return trainingService.retrain();
    }

    @Override
    public AiRiskTrainingResultVO trainingStatus() {
        return trainingService.trainingStatus();
    }
```

- [ ] **Step 4: Add shadow inference in persistScoreRecord**

In `AiRiskScoringServiceImpl.java`, inside `persistScoreRecord`, immediately BEFORE the `scoreRecordMapper.insert(record);` line, insert:

```java
        applyShadowModelScore(record, result.getFeatureSummary());
```

Then add this private method (place it next to `buildFactorSummary`):

```java
    /**
     * 影子写入监督模型概率分。失败仅告警，绝不影响规则评分与落库。
     */
    private void applyShadowModelScore(AiRiskScoreRecord record, AiRiskFeatureSummaryVO features) {
        try {
            if (features == null || !supervisedModel.isReady()) {
                return;
            }
            supervisedModel.predictProbability(featureVectorizer.toVector(features))
                    .ifPresent(prob -> {
                        record.setModelProbability(
                                new java.math.BigDecimal(prob).setScale(4, java.math.RoundingMode.HALF_UP));
                        record.setModelLabelPredicted(prob >= 0.5 ? "SUSPICIOUS" : "NORMAL");
                    });
        } catch (Exception e) {
            log.warn("AI监督模型影子评分失败，不影响规则评分: {}", e.getMessage());
        }
    }
```

Confirm `AiRiskFeatureSummaryVO` is imported in the impl (it is — used by existing signatures).

- [ ] **Step 5: Add controller endpoints**

In `AiRiskScoringController.java`, add import `import com.insurance.aml.module.ai.model.dto.AiRiskTrainingResultVO;` and add two endpoints (place after the `getModelStatus` method, mirroring existing `Result<T>`/annotation style used in this file):

```java
    @PostMapping("/model/retrain")
    @Operation(summary = "触发监督模型重训")
    public Result<AiRiskTrainingResultVO> retrainModel() {
        return Result.success(aiRiskScoringService.retrainModel());
    }

    @GetMapping("/model/training-status")
    @Operation(summary = "查询监督模型训练状态")
    public Result<AiRiskTrainingResultVO> trainingStatus() {
        return Result.success(aiRiskScoringService.trainingStatus());
    }
```

If the existing methods in this controller do not use `@Operation`, omit that annotation to match the file's actual convention. Use the same `Result.success(...)` helper the other methods in the file use (match exactly).

- [ ] **Step 6: Compile**

Run: `mvn -q test-compile`
Expected: exit 0.

- [ ] **Step 7: Run existing AI unit tests (regression — rule path unchanged)**

Run: `mvn -q test -Dtest='AiRiskScoringServiceImplTest,AiRiskReviewServiceTest'`
Expected: PASS, 3 + 7 tests. `AiRiskScoringServiceImplTest` uses `@InjectMocks`; the three new `@Mock` collaborators must be added to that test — do so now: add `@Mock AiRiskSupervisedModel supervisedModel;`, `@Mock AiRiskFeatureVectorizer featureVectorizer;`, `@Mock AiRiskModelTrainingService trainingService;` fields to `AiRiskScoringServiceImplTest`. Re-run; expect PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/insurance/aml/module/ai/service/AiRiskScoringService.java \
        src/main/java/com/insurance/aml/module/ai/service/impl/AiRiskScoringServiceImpl.java \
        src/main/java/com/insurance/aml/module/ai/controller/AiRiskScoringController.java \
        src/test/java/com/insurance/aml/module/ai/service/AiRiskScoringServiceImplTest.java
git commit -m "feat: wire supervised shadow scoring + retrain endpoints"
```

---

### Task 6: Configuration + scheduling enablement

**Files:**
- Modify: `src/main/resources/application.yml`
- Verify: `@EnableScheduling` present

- [ ] **Step 1: Add config block**

In `src/main/resources/application.yml`, locate the existing `aml:` → `ml:` section (used by `aml.ml.model-path`). Add under `aml.ml:` a nested `ai-risk` block:

```yaml
    ai-risk:
      model-path: ./data/models
      retrain-cron: "0 0 3 * * SUN"
      min-samples: 50
```

(If `aml.ml` does not exist as nested YAML — it may be flat keys like `aml.ml.model-path:` — instead add flat keys `aml.ml.ai-risk.model-path: ./data/models`, `aml.ml.ai-risk.retrain-cron: "0 0 3 * * SUN"`, `aml.ml.ai-risk.min-samples: 50` in the same style the file already uses.)

- [ ] **Step 2: Ensure scheduling is enabled**

Run: `grep -rn "@EnableScheduling" src/main/java`
Expected: at least one match (likely on the main application class or a config). If NO match, add `@EnableScheduling` to `src/main/java/com/insurance/aml/AmlApplication.java` (class-level annotation) and its import `import org.springframework.scheduling.annotation.EnableScheduling;`.

- [ ] **Step 3: Compile**

Run: `mvn -q test-compile`
Expected: exit 0.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/application.yml src/main/java/com/insurance/aml/AmlApplication.java
git commit -m "chore: config + scheduling for ai-risk supervised retrain"
```

(If `AmlApplication.java` was not modified, omit it from the `git add`.)

---

### Task 7: Integration test — end-to-end loop

**Files:**
- Modify: `src/test/java/com/insurance/aml/integration/AiRiskScoringIntegrationTest.java`

- [ ] **Step 1: Add the failing end-to-end test**

In `AiRiskScoringIntegrationTest.java`, add a new `@Test` method (reuse the file's existing `getAuthToken()`, `createCustomer()`, `mockMvc`, `objectMapper`, `jdbcTemplate` helpers — match their exact names/signatures already in the file). Set `aml.ml.ai-risk.min-samples` low for the test via `@TestPropertySource` or `@DynamicPropertySource` if the class supports it; otherwise seed enough labeled rows. Add:

```java
    @Test
    @org.junit.jupiter.api.DisplayName("复核标注回流-训练-影子评分闭环")
    void supervisedFeedbackLoop_endToEnd() throws Exception {
        String token = getAuthToken();

        // 1) Seed labeled score records directly (both classes) so training has data.
        java.time.format.DateTimeFormatter fmt =
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String now = java.time.LocalDateTime.now().format(fmt);
        for (int i = 0; i < 30; i++) {
            String label = (i % 2 == 0) ? "TRUE_POSITIVE" : "FALSE_POSITIVE";
            String feat = (i % 2 == 0)
                    ? "{\"transactionCount90d\":20,\"kycCompleteness\":30,\"highRiskAlertCount\":3}"
                    : "{\"transactionCount90d\":1,\"kycCompleteness\":95,\"highRiskAlertCount\":0}";
            jdbcTemplate.update(
                "INSERT INTO t_ai_risk_score_record " +
                "(score_no,subject_type,subject_id,subject_name,customer_id,model_code," +
                "model_name,model_version,score,risk_level,confidence,factor_summary," +
                "feature_snapshot_json,scored_at,manual_review_label,reviewed_by,reviewed_at," +
                "created_time,updated_time,deleted) VALUES " +
                "(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
                "SEED" + i, "CUSTOMER", (long) (1000 + i), "种子客户" + i, (long) (1000 + i),
                "AI_AML_RISK_BASELINE_V1", "AI可解释风险评分基线模型", "1.0.0",
                (i % 2 == 0) ? 80 : 10, (i % 2 == 0) ? "HIGH" : "LOW", 70,
                "种子", feat, now, label, "admin", now, now, now);
        }

        // 2) Trigger retrain.
        MvcResult retrain = mockMvc.perform(post("/ai/risk/model/retrain")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rb = objectMapper.readTree(retrain.getResponse().getContentAsString());
        assertEquals("TRAINED", rb.path("data").path("status").asText());
        assertTrue(rb.path("data").path("sampleCount").asInt() >= 4);

        // 3) Score a customer; the persisted record must carry a shadow probability.
        Long customerId = createCustomer();
        mockMvc.perform(get("/ai/risk/customers/" + customerId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        Double prob = jdbcTemplate.queryForObject(
                "SELECT model_probability FROM t_ai_risk_score_record " +
                "WHERE customer_id = ? ORDER BY id DESC LIMIT 1", Double.class, customerId);
        assertTrue(prob != null && prob >= 0.0 && prob <= 1.0);
    }
```

Ensure `import static org.junit.jupiter.api.Assertions.assertEquals;` and `assertTrue` are present (the file already imports `assertTrue`/`assertFalse`; add `assertEquals` if missing). If `min-samples` default (50) exceeds the 30 seeded positives+negatives total (15+15), raise the seed loop to `i < 110` (55/55) OR add `@org.springframework.test.context.TestPropertySource(properties = "aml.ml.ai-risk.min-samples=4")` to the test class. Prefer the `@TestPropertySource` route; only fall back to more rows if another test in the class conflicts with class-level properties.

- [ ] **Step 2: Run the integration test**

Run: `mvn -q test -Dtest=AiRiskScoringIntegrationTest`
Expected: PASS (all methods including the new one). If the new test fails on the H2 insert (column mismatch), reconcile the column list with the actual `t_ai_risk_score_record` definition in `src/test/resources/schema-h2.sql` (it must include the V015 + V016 columns from Task 1).

- [ ] **Step 3: Full AI regression**

Run: `mvn -q test -Dtest='AiRiskFeatureVectorizerTest,AiRiskSupervisedModelTest,AiRiskModelTrainingServiceTest,AiRiskScoringServiceImplTest,AiRiskReviewServiceTest,AiRiskScoringIntegrationTest'`
Expected: ALL PASS. Confirms rule-score behavior unchanged and the loop works end to end.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/insurance/aml/integration/AiRiskScoringIntegrationTest.java
git commit -m "test: end-to-end supervised feedback loop integration test"
```

---

### Task 8: Finalize

- [ ] **Step 1: Full build**

Run: `mvn -q test`
Expected: BUILD SUCCESS, all modules' tests green.

- [ ] **Step 2: Merge to main and push** (follow the session's established flow)

```bash
git checkout main
git merge --ff-only feat/ai-risk-supervised-loop
git branch -d feat/ai-risk-supervised-loop
git push github main
```

- [ ] **Step 3: Report** acceptance criteria coverage (spec §9) back to the user.

---

## Self-Review

**Spec coverage:**
- §3 architecture/data flow → Tasks 2–5, 7 ✓
- §4 components: Vectorizer (T2), SupervisedModel (T3), TrainingService (T4), controller+impl+interface (T5) ✓
- §5 DB columns + entity + status VO → T1 (columns/entity); status surfaced via `AiRiskTrainingResultVO` + `trainingStatus()` (T4/T5) — note: spec §5 also mentions extending `AiRiskModelStatusVO`; superseded by the dedicated `AiRiskTrainingResultVO` + `/model/training-status` endpoint, which fully satisfies §9.6. No separate task needed.
- §6 config → T6 ✓
- §7 boundaries: insufficient/single-class skip (T4 tests), not-ready null prob (T3 test + T5 guard), shadow try/catch (T5 Step 4), NEEDS_MONITORING excluded (T4 test), single vectorizer out (T2) ✓
- §8 tests: vectorizer/model/training unit + integration extension + regression ✓
- §9 acceptance: 1→T7, 2→T7, 3→T3/T5, 4→T5, 5→T7 Step 3, 6→T3 roundtrip + trainingStatus ✓

**Placeholder scan:** No TBD/TODO; all code blocks complete; Smile-version adaptation notes are explicit fallbacks, not placeholders.

**Type consistency:** `AiRiskSupervisedModel.replace(LogisticRegression,int,int,int,double,double)` used identically in T3 test and T4 service. `predictProbability(double[]) → Optional<Double>` consistent T3/T5. `AiRiskTrainingResultVO` builder fields (`status,modelReady,sampleCount,positiveCount,negativeCount,accuracy,auc,trainedAt,message`) consistent T3 definition / T4 use. `retrainModel()`/`trainingStatus()` consistent interface (T5) ↔ controller (T5). `FEATURE_DIM=12` consistent T2 ↔ test vectors length 12 in T3.

No gaps found.
