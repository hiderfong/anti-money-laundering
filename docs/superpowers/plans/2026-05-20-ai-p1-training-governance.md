# AI P1 Training Governance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the unsupervised `TransactionAnomalyDetector` to the same training-governance baseline as the supervised model (cron, CAS serialization, min-samples guard, FAILED state) and expose a unified ops surface for both models without breaking any existing API.

**Architecture:** Replace the standalone `ModelTrainingScheduler` with a `@Scheduled` method directly on `TransactionAnomalyDetector` that delegates to a new `retrain() → AnomalyTrainingResultVO` (CAS + guards + state recording). Add a thin `ModelTrainingOpsService` that aggregates both models behind two new REST endpoints (`GET /ai/risk/models/training`, `POST /ai/risk/models/training/{modelKey}/retrain`); existing supervised endpoints remain working.

**Tech Stack:** Java 21, Spring Boot 3.4 (`@Scheduled`, `@PreAuthorize`, `@Value`), Smile 3.0.1, JUnit 5 + Mockito, H2 for integration tests. Pattern matched against the P0 work just merged on `main` (`AiRiskModelTrainingService`, `AiRiskSupervisedModel`).

---

## File Structure

| File | Responsibility |
|------|----------------|
| `module/ai/model/dto/AnomalyTrainingResultVO.java` (create) | Unsupervised training outcome: status / modelReady / sampleCount / trainDurationMs / trainedAt / message (NO accuracy/auc — IF is unsupervised) |
| `module/ai/model/dto/ModelTrainingStatusVO.java` (create) | Generic ops view: modelKey / modelName / modelVersion / status / modelReady / sampleCount / trainedAt / message |
| `module/ai/service/support/AiRiskSupervisedModel.java` (modify) | Add `lastTrainStatus`/`lastTrainError` volatile fields + `recordOutcome(status, error)` method + getters |
| `module/ai/service/support/AiRiskModelTrainingService.java` (modify) | Outer `try/catch` → `FAILED`; call `supervisedModel.recordOutcome(...)` on every retrain() path |
| `module/monitoring/service/TransactionAnomalyDetector.java` (modify) | Replace `public int train()` with `private int doTrain()`; add public `retrain() → AnomalyTrainingResultVO` with CAS + min-samples guard + state tracking; add `@Scheduled scheduledRetrain()`; new `@Value`s for cron / min-samples; new volatile `lastTrainStatus`/`lastTrainError`/`lastTrainSampleCount`/`trainDurationMs` |
| `module/monitoring/service/ModelTrainingScheduler.java` (delete) | Its sole purpose moves onto the detector itself |
| `module/ai/service/support/ModelTrainingOpsService.java` (create) | `listAll() → List<ModelTrainingStatusVO>` (fixed [supervised, anomaly] order); `retrain(modelKey) → ModelTrainingStatusVO` (route to one of the two services); unknown key → `BusinessException(BAD_REQUEST)` |
| `module/ai/service/AiRiskScoringService.java` (modify) | Add `List<ModelTrainingStatusVO> listTrainableModels()` and `ModelTrainingStatusVO retrainModelByKey(String modelKey)` |
| `module/ai/service/impl/AiRiskScoringServiceImpl.java` (modify) | Inject `ModelTrainingOpsService`; delegate the two new interface methods |
| `module/ai/controller/AiRiskScoringController.java` (modify) | Add `GET /ai/risk/models/training` and `POST /ai/risk/models/training/{modelKey}/retrain` |
| `application.yml` (modify) | Add `aml.ml.anomaly.{retrain-cron,min-samples}` |
| `application-prod.yml` (modify) | Add env-var overrides for the same |
| `application-test.yml` (modify) | Add `aml.ml.anomaly.min-samples: 4` |
| Tests | `AiRiskSupervisedModelTest` (extend), `AiRiskModelTrainingServiceTest` (extend), `TransactionAnomalyDetectorRetrainTest` (create), `ModelTrainingOpsServiceTest` (create), `AiRiskScoringServiceImplTest` (extend mocks), `AiRiskScoringIntegrationTest` (extend) |

---

### Task 1: DTOs

**Files:**
- Create: `src/main/java/com/insurance/aml/module/ai/model/dto/AnomalyTrainingResultVO.java`
- Create: `src/main/java/com/insurance/aml/module/ai/model/dto/ModelTrainingStatusVO.java`

- [ ] **Step 1: Create AnomalyTrainingResultVO**

```java
package com.insurance.aml.module.ai.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 无监督异常检测训练/状态结果。
 * status: TRAINED / SKIPPED_INSUFFICIENT / SKIPPED_IN_PROGRESS / FAILED / NOT_TRAINED
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyTrainingResultVO {

    private String status;
    private boolean modelReady;
    private int sampleCount;
    private long trainDurationMs;
    private LocalDateTime trainedAt;
    private String message;
}
```

- [ ] **Step 2: Create ModelTrainingStatusVO**

```java
package com.insurance.aml.module.ai.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 运维端通用的模型训练状态视图，由 ModelTrainingOpsService 聚合两个模型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelTrainingStatusVO {

    /** "supervised" 或 "anomaly" */
    private String modelKey;
    private String modelName;
    private String modelVersion;
    /** TRAINED / SKIPPED_* / FAILED / NOT_TRAINED */
    private String status;
    private boolean modelReady;
    private int sampleCount;
    private LocalDateTime trainedAt;
    private String message;
}
```

- [ ] **Step 3: Compile to verify**

Run: `mvn -q test-compile`
Expected: exit 0.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/insurance/aml/module/ai/model/dto/AnomalyTrainingResultVO.java \
        src/main/java/com/insurance/aml/module/ai/model/dto/ModelTrainingStatusVO.java
git commit -m "feat: add training-governance DTOs (Anomaly + ops status)"
```

---

### Task 2: AiRiskSupervisedModel.recordOutcome + AiRiskModelTrainingService FAILED path (TDD)

**Files:**
- Modify: `src/main/java/com/insurance/aml/module/ai/service/support/AiRiskSupervisedModel.java`
- Modify: `src/main/java/com/insurance/aml/module/ai/service/support/AiRiskModelTrainingService.java`
- Test: `src/test/java/com/insurance/aml/module/ai/service/AiRiskSupervisedModelTest.java` (extend)
- Test: `src/test/java/com/insurance/aml/module/ai/service/AiRiskModelTrainingServiceTest.java` (extend)

- [ ] **Step 1: Write the failing test on the model**

Append to `AiRiskSupervisedModelTest`:

```java
    @Test
    @org.junit.jupiter.api.DisplayName("recordOutcome 不动模型，仅写 lastTrainStatus/lastTrainError")
    void recordOutcome_writesObservableFields() {
        AiRiskSupervisedModel m = new AiRiskSupervisedModel();
        m.recordOutcome("FAILED", "boom");
        assertEquals("FAILED", m.getLastTrainStatus());
        assertEquals("boom", m.getLastTrainError());
        assertFalse(m.isReady(), "recordOutcome 不应改变 ready 标记");
        m.recordOutcome("TRAINED", null);
        assertEquals("TRAINED", m.getLastTrainStatus());
        assertEquals(null, m.getLastTrainError(), "TRAINED 时应清空 lastTrainError");
    }
```

(Ensure `assertEquals` is imported; the file already imports `assertFalse`.)

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=AiRiskSupervisedModelTest`
Expected: FAIL (`recordOutcome` / `getLastTrainStatus` / `getLastTrainError` do not exist).

- [ ] **Step 3: Implement on the model**

In `AiRiskSupervisedModel.java`, add two volatile fields next to the existing `@Getter` ones (use the same `@Getter` Lombok pattern) and a `recordOutcome` method:

```java
    @Getter private volatile String lastTrainStatus;
    @Getter private volatile String lastTrainError;

    /**
     * 记录最近一次训练尝试的结果与错误信息。不改变 ready / model 引用。
     */
    public void recordOutcome(String status, String errorMessage) {
        this.lastTrainStatus = status;
        this.lastTrainError = errorMessage;
    }
```

- [ ] **Step 4: Run model test to verify it passes**

Run: `mvn -q test -Dtest=AiRiskSupervisedModelTest`
Expected: PASS, 3 tests (the original 2 + 1 new).

- [ ] **Step 5: Write the failing test on the training service**

Append to `AiRiskModelTrainingServiceTest`:

```java
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
```

(`when`, `any`, `assertEquals` are already imported in the file.)

- [ ] **Step 6: Run service test to verify it fails**

Run: `mvn -q test -Dtest=AiRiskModelTrainingServiceTest`
Expected: FAIL (currently `RuntimeException` propagates, no FAILED status, no recordOutcome).

- [ ] **Step 7: Wrap retrain() with outer try/catch + recordOutcome on every path**

In `AiRiskModelTrainingService.java`, replace the existing `retrain()` method body with:

```java
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
```

`doRetrain()` is already defined (it is the existing private method). No changes to skip-paths or training-success paths inside `doRetrain()`.

- [ ] **Step 8: Update trainingStatus() to surface lastTrainError when present**

In `AiRiskModelTrainingService.java`, locate the `trainingStatus()` method. Change its `.message(ready ? "模型就绪" : "模型尚未训练")` line so that, when `supervisedModel.getLastTrainStatus()` is non-null, the last train status/error is surfaced. Replace that one line with:

```java
            .message(buildStatusMessage(ready))
```

And add this private helper at the bottom of the class:

```java
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
```

- [ ] **Step 9: Run both tests + regression**

Run: `mvn -q test -Dtest='AiRiskSupervisedModelTest,AiRiskModelTrainingServiceTest'`
Expected: PASS (AiRiskSupervisedModelTest 3, AiRiskModelTrainingServiceTest 4). Confirm via surefire reports.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/insurance/aml/module/ai/service/support/AiRiskSupervisedModel.java \
        src/main/java/com/insurance/aml/module/ai/service/support/AiRiskModelTrainingService.java \
        src/test/java/com/insurance/aml/module/ai/service/AiRiskSupervisedModelTest.java \
        src/test/java/com/insurance/aml/module/ai/service/AiRiskModelTrainingServiceTest.java
git commit -m "feat: supervised model recordOutcome + FAILED status path"
```

---

### Task 3: TransactionAnomalyDetector retrain governance + delete legacy scheduler (TDD)

**Files:**
- Modify: `src/main/java/com/insurance/aml/module/monitoring/service/TransactionAnomalyDetector.java`
- Delete: `src/main/java/com/insurance/aml/module/monitoring/service/ModelTrainingScheduler.java`
- Test: `src/test/java/com/insurance/aml/module/monitoring/service/TransactionAnomalyDetectorRetrainTest.java` (create)
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/resources/application-prod.yml`
- Modify: `src/test/resources/application-test.yml`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/insurance/aml/module/monitoring/service/TransactionAnomalyDetectorRetrainTest.java`:

```java
package com.insurance.aml.module.monitoring.service;

import com.insurance.aml.module.ai.model.dto.AnomalyTrainingResultVO;
import com.insurance.aml.module.monitoring.mapper.TransactionMapper;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionAnomalyDetector 训练治理测试")
class TransactionAnomalyDetectorRetrainTest {

    @Mock
    TransactionMapper transactionMapper;

    private TransactionAnomalyDetector newDetector() {
        TransactionAnomalyDetector d = new TransactionAnomalyDetector(transactionMapper);
        ReflectionTestUtils.setField(d, "modelPath", "./target/test-data/models-" + System.nanoTime());
        ReflectionTestUtils.setField(d, "trainingDays", 90);
        ReflectionTestUtils.setField(d, "anomalyThreshold", 0.7);
        ReflectionTestUtils.setField(d, "numTrees", 10);
        ReflectionTestUtils.setField(d, "subsampleSize", 32);
        ReflectionTestUtils.setField(d, "minSamples", 4);
        return d;
    }

    private Transaction txn(double amount, int idSeed) {
        Transaction t = new Transaction();
        t.setId((long) idSeed);
        t.setTransactionNo("TXN" + idSeed);
        t.setAmount(BigDecimal.valueOf(amount));
        t.setIsCrossBorder(false);
        t.setTransactionTime(LocalDateTime.now().minusHours(idSeed));
        t.setCustomerId(1L);
        return t;
    }

    @Test
    @DisplayName("样本不足时跳过训练，模型保持未就绪")
    void retrain_insufficient_skips() {
        when(transactionMapper.selectList(any())).thenReturn(new ArrayList<>());
        TransactionAnomalyDetector d = newDetector();

        AnomalyTrainingResultVO result = d.retrain();

        assertEquals("SKIPPED_INSUFFICIENT", result.getStatus());
        assertFalse(d.isModelReady());
    }

    @Test
    @DisplayName("正常样本量训练成功并填充指标")
    void retrain_normal_trains() {
        List<Transaction> rows = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            rows.add(txn(100.0 + i, i));
        }
        when(transactionMapper.selectList(any())).thenReturn(rows);
        TransactionAnomalyDetector d = newDetector();

        AnomalyTrainingResultVO result = d.retrain();

        assertEquals("TRAINED", result.getStatus());
        assertTrue(result.isModelReady());
        assertEquals(60, result.getSampleCount());
        assertTrue(result.getTrainDurationMs() >= 0);
        assertEquals("TRAINED", d.getLastTrainStatus());
    }

    @Test
    @DisplayName("内部异常时返回FAILED且模型不下线")
    void retrain_innerException_returnsFailed() {
        when(transactionMapper.selectList(any())).thenThrow(new RuntimeException("db down"));
        TransactionAnomalyDetector d = newDetector();

        AnomalyTrainingResultVO result = d.retrain();

        assertEquals("FAILED", result.getStatus());
        assertTrue(result.getMessage() != null && result.getMessage().contains("db down"));
        assertEquals("FAILED", d.getLastTrainStatus());
        assertFalse(d.isModelReady(), "模型本就未就绪，FAILED 不会让它变就绪");
    }

    @Test
    @DisplayName("scheduledRetrain 在 retrain 抛异常时不外抛")
    void scheduledRetrain_swallowsExceptions() {
        when(transactionMapper.selectList(any())).thenThrow(new RuntimeException("boom"));
        TransactionAnomalyDetector d = newDetector();

        d.scheduledRetrain(); // must not throw
        assertEquals("FAILED", d.getLastTrainStatus());
    }
}
```

Note: this test calls `d.isModelReady()` and `d.getLastTrainStatus()` — both will be added in Step 3.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=TransactionAnomalyDetectorRetrainTest`
Expected: FAIL/compile error — `retrain()`, `scheduledRetrain()`, `isModelReady()`, `getLastTrainStatus()` not defined.

- [ ] **Step 3: Refactor TransactionAnomalyDetector**

In `src/main/java/com/insurance/aml/module/monitoring/service/TransactionAnomalyDetector.java`:

a) Add imports near the existing imports:

```java
import com.insurance.aml.module.ai.model.dto.AnomalyTrainingResultVO;
import org.springframework.scheduling.annotation.Scheduled;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Getter;
```

b) Add two new `@Value` fields next to the existing `@Value`-injected fields (preserve existing ones):

```java
    @Value("${aml.ml.anomaly.retrain-cron:0 30 3 * * SUN}")
    private String retrainCron;

    @Value("${aml.ml.anomaly.min-samples:100}")
    private int minSamples;
```

c) Add CAS + state fields near `private volatile boolean modelReady = false;`:

```java
    private final AtomicBoolean trainingInProgress = new AtomicBoolean(false);
    @Getter private volatile String lastTrainStatus;
    @Getter private volatile String lastTrainError;
    @Getter private volatile int lastTrainSampleCount;
    @Getter private volatile long trainDurationMs;
    @Getter private volatile LocalDateTime lastTrainedAt;
```

d) Expose `isModelReady()` (if not already public — verify by reading the file; if it is via `@Getter` then skip). If `modelReady` is a private field with no getter, add:

```java
    public boolean isModelReady() {
        return modelReady;
    }
```

e) Rename the existing `public int train()` to `private int doTrain()` (just the method signature line — the body stays exactly the same, including the `< 50` guard which becomes redundant after the new `minSamples` guard but is left in place because removing it changes behavior; we want this task to add governance, not weaken the inner training).

f) Add the new public `retrain()` method (place it just above the renamed `doTrain()`):

```java
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
```

Note: the inner `doTrain()` already returns 0 on `transactions.isEmpty()` and on its hardcoded `< 50` guard. With `minSamples=100` default we will hit our outer `SKIPPED_INSUFFICIENT` for anything `< 100`. With `minSamples=4` (test), the inner 0-return for empty list still yields `0 < 4` → `SKIPPED_INSUFFICIENT`. The inner `< 50` guard fires before reaching fit, returning 0 → outer reports `SKIPPED_INSUFFICIENT` too. This is acceptable behavior preservation.

g) Add the scheduled method (place near retrain):

```java
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
```

- [ ] **Step 4: Delete the legacy scheduler**

```bash
git rm src/main/java/com/insurance/aml/module/monitoring/service/ModelTrainingScheduler.java
```

(Its sole purpose was the @Scheduled wrapper; that responsibility is now on the detector itself, matching the supervised model pattern.)

- [ ] **Step 5: Update application.yml**

In `src/main/resources/application.yml`, find the `aml.ml` block (which already contains `model-path`, `training-days`, `anomaly-threshold`, `num-trees`, `subsample-size`, and the `ai-risk:` sub-block). Add a sibling `anomaly:` sub-block:

```yaml
    anomaly:
      retrain-cron: "0 30 3 * * SUN"
      min-samples: 100
```

(Indent it identically to the existing `ai-risk:` block — 4 spaces under `ml:`.)

- [ ] **Step 6: Update application-prod.yml**

In `src/main/resources/application-prod.yml`, find the `aml.ml` block added by P0. Add an `anomaly:` sub-block alongside `ai-risk:`:

```yaml
    anomaly:
      retrain-cron: "${AML_ML_ANOMALY_RETRAIN_CRON:0 30 3 * * SUN}"
      min-samples: ${AML_ML_ANOMALY_MIN_SAMPLES:100}
```

- [ ] **Step 7: Update application-test.yml**

In `src/test/resources/application-test.yml`, find the `aml.ml.ai-risk.min-samples: 4` entry. Add the parallel anomaly key under the same `aml.ml` node:

```yaml
    anomaly:
      min-samples: 4
```

- [ ] **Step 8: Run the new test + monitoring regression**

Run: `mvn -q test -Dtest='TransactionAnomalyDetectorRetrainTest,RuleEngineServiceTest'`
Expected: TransactionAnomalyDetectorRetrainTest 4/0/0; RuleEngineServiceTest unchanged result (it only mocks `TransactionAnomalyDetector`, so refactor cannot break it).

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/insurance/aml/module/monitoring/service/TransactionAnomalyDetector.java \
        src/test/java/com/insurance/aml/module/monitoring/service/TransactionAnomalyDetectorRetrainTest.java \
        src/main/resources/application.yml \
        src/main/resources/application-prod.yml \
        src/test/resources/application-test.yml
git add -u src/main/java/com/insurance/aml/module/monitoring/service/ModelTrainingScheduler.java
git commit -m "feat: AnomalyDetector retrain governance + delete legacy scheduler"
```

---

### Task 4: ModelTrainingOpsService (TDD)

**Files:**
- Create: `src/main/java/com/insurance/aml/module/ai/service/support/ModelTrainingOpsService.java`
- Test: `src/test/java/com/insurance/aml/module/ai/service/ModelTrainingOpsServiceTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/insurance/aml/module/ai/service/ModelTrainingOpsServiceTest.java`:

```java
package com.insurance.aml.module.ai.service;

import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.result.ResultCode;
import com.insurance.aml.module.ai.model.dto.AiRiskTrainingResultVO;
import com.insurance.aml.module.ai.model.dto.AnomalyTrainingResultVO;
import com.insurance.aml.module.ai.model.dto.ModelTrainingStatusVO;
import com.insurance.aml.module.ai.service.support.AiRiskModelTrainingService;
import com.insurance.aml.module.ai.service.support.ModelTrainingOpsService;
import com.insurance.aml.module.monitoring.service.TransactionAnomalyDetector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("模型训练运维聚合服务测试")
class ModelTrainingOpsServiceTest {

    @Mock
    AiRiskModelTrainingService aiRiskTrainingService;
    @Mock
    TransactionAnomalyDetector anomalyDetector;

    @InjectMocks
    ModelTrainingOpsService service;

    @Test
    @DisplayName("listAll 返回固定顺序 [supervised, anomaly]")
    void listAll_fixedOrder() {
        when(aiRiskTrainingService.trainingStatus()).thenReturn(
                AiRiskTrainingResultVO.builder().status("NOT_TRAINED").modelReady(false)
                        .message("尚未训练").build());
        when(anomalyDetector.getLastTrainStatus()).thenReturn(null);
        when(anomalyDetector.isModelReady()).thenReturn(false);

        List<ModelTrainingStatusVO> all = service.listAll();

        assertEquals(2, all.size());
        assertEquals("supervised", all.get(0).getModelKey());
        assertEquals("anomaly", all.get(1).getModelKey());
    }

    @Test
    @DisplayName("retrain(supervised) 委派到 AiRiskModelTrainingService.retrain()")
    void retrain_supervised_delegates() {
        when(aiRiskTrainingService.retrain()).thenReturn(
                AiRiskTrainingResultVO.builder().status("TRAINED").modelReady(true)
                        .sampleCount(50).message("ok").build());

        ModelTrainingStatusVO result = service.retrain("supervised");

        assertEquals("supervised", result.getModelKey());
        assertEquals("TRAINED", result.getStatus());
        assertEquals(50, result.getSampleCount());
    }

    @Test
    @DisplayName("retrain(anomaly) 委派到 TransactionAnomalyDetector.retrain()")
    void retrain_anomaly_delegates() {
        when(anomalyDetector.retrain()).thenReturn(
                AnomalyTrainingResultVO.builder().status("TRAINED").modelReady(true)
                        .sampleCount(120).trainDurationMs(42).message("ok").build());

        ModelTrainingStatusVO result = service.retrain("anomaly");

        assertEquals("anomaly", result.getModelKey());
        assertEquals("TRAINED", result.getStatus());
        assertEquals(120, result.getSampleCount());
    }

    @Test
    @DisplayName("未知 modelKey 抛 BAD_REQUEST")
    void retrain_unknown_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.retrain("not-a-model"));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=ModelTrainingOpsServiceTest`
Expected: FAIL/compile error — class does not exist.

- [ ] **Step 3: Implement ModelTrainingOpsService**

Create `src/main/java/com/insurance/aml/module/ai/service/support/ModelTrainingOpsService.java`:

```java
package com.insurance.aml.module.ai.service.support;

import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.result.ResultCode;
import com.insurance.aml.module.ai.model.dto.AiRiskTrainingResultVO;
import com.insurance.aml.module.ai.model.dto.AnomalyTrainingResultVO;
import com.insurance.aml.module.ai.model.dto.ModelTrainingStatusVO;
import com.insurance.aml.module.monitoring.service.TransactionAnomalyDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 训练运维聚合服务：把监督模型与无监督异常检测的训练状态/触发统一对外。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelTrainingOpsService {

    public static final String MODEL_KEY_SUPERVISED = "supervised";
    public static final String MODEL_KEY_ANOMALY = "anomaly";

    private static final String SUPERVISED_NAME = "AI可解释风险评分基线模型";
    private static final String SUPERVISED_VERSION = "1.0.0";
    private static final String ANOMALY_NAME = "交易异常检测 (Isolation Forest)";
    private static final String ANOMALY_VERSION = "1.0.0";

    private final AiRiskModelTrainingService aiRiskTrainingService;
    private final TransactionAnomalyDetector anomalyDetector;

    public List<ModelTrainingStatusVO> listAll() {
        return List.of(supervisedStatus(), anomalyStatus());
    }

    public ModelTrainingStatusVO retrain(String modelKey) {
        if (MODEL_KEY_SUPERVISED.equals(modelKey)) {
            return toStatus(aiRiskTrainingService.retrain());
        }
        if (MODEL_KEY_ANOMALY.equals(modelKey)) {
            return toStatus(anomalyDetector.retrain());
        }
        throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的模型: " + modelKey);
    }

    private ModelTrainingStatusVO supervisedStatus() {
        AiRiskTrainingResultVO s = aiRiskTrainingService.trainingStatus();
        return ModelTrainingStatusVO.builder()
                .modelKey(MODEL_KEY_SUPERVISED)
                .modelName(SUPERVISED_NAME)
                .modelVersion(SUPERVISED_VERSION)
                .status(s.getStatus())
                .modelReady(s.isModelReady())
                .sampleCount(s.getSampleCount())
                .trainedAt(s.getTrainedAt())
                .message(s.getMessage())
                .build();
    }

    private ModelTrainingStatusVO anomalyStatus() {
        String last = anomalyDetector.getLastTrainStatus();
        boolean ready = anomalyDetector.isModelReady();
        return ModelTrainingStatusVO.builder()
                .modelKey(MODEL_KEY_ANOMALY)
                .modelName(ANOMALY_NAME)
                .modelVersion(ANOMALY_VERSION)
                .status(last == null ? (ready ? "TRAINED" : "NOT_TRAINED") : last)
                .modelReady(ready)
                .sampleCount(anomalyDetector.getLastTrainSampleCount())
                .trainedAt(anomalyDetector.getLastTrainedAt())
                .message(ready ? "模型就绪" : "模型尚未训练")
                .build();
    }

    private ModelTrainingStatusVO toStatus(AiRiskTrainingResultVO r) {
        return ModelTrainingStatusVO.builder()
                .modelKey(MODEL_KEY_SUPERVISED)
                .modelName(SUPERVISED_NAME)
                .modelVersion(SUPERVISED_VERSION)
                .status(r.getStatus())
                .modelReady(r.isModelReady())
                .sampleCount(r.getSampleCount())
                .trainedAt(r.getTrainedAt())
                .message(r.getMessage())
                .build();
    }

    private ModelTrainingStatusVO toStatus(AnomalyTrainingResultVO r) {
        return ModelTrainingStatusVO.builder()
                .modelKey(MODEL_KEY_ANOMALY)
                .modelName(ANOMALY_NAME)
                .modelVersion(ANOMALY_VERSION)
                .status(r.getStatus())
                .modelReady(r.isModelReady())
                .sampleCount(r.getSampleCount())
                .trainedAt(r.getTrainedAt())
                .message(r.getMessage())
                .build();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=ModelTrainingOpsServiceTest`
Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/insurance/aml/module/ai/service/support/ModelTrainingOpsService.java \
        src/test/java/com/insurance/aml/module/ai/service/ModelTrainingOpsServiceTest.java
git commit -m "feat: add ModelTrainingOpsService aggregating both models"
```

---

### Task 5: Wire interface + impl + controller endpoints

**Files:**
- Modify: `src/main/java/com/insurance/aml/module/ai/service/AiRiskScoringService.java`
- Modify: `src/main/java/com/insurance/aml/module/ai/service/impl/AiRiskScoringServiceImpl.java`
- Modify: `src/main/java/com/insurance/aml/module/ai/controller/AiRiskScoringController.java`
- Modify: `src/test/java/com/insurance/aml/module/ai/service/AiRiskScoringServiceImplTest.java` (add @Mock)

- [ ] **Step 1: Interface — add two methods**

In `AiRiskScoringService.java`, add import `import com.insurance.aml.module.ai.model.dto.ModelTrainingStatusVO;` and add to the interface body:

```java
    List<ModelTrainingStatusVO> listTrainableModels();

    ModelTrainingStatusVO retrainModelByKey(String modelKey);
```

(`List` is already imported.)

- [ ] **Step 2: Impl — inject service + delegate**

In `AiRiskScoringServiceImpl.java`:

a) Add imports:

```java
import com.insurance.aml.module.ai.model.dto.ModelTrainingStatusVO;
import com.insurance.aml.module.ai.service.support.ModelTrainingOpsService;
```

b) Add field next to the other support collaborators:

```java
    private final ModelTrainingOpsService modelTrainingOpsService;
```

c) Add the two overrides after `trainingStatus()`:

```java
    @Override
    public List<ModelTrainingStatusVO> listTrainableModels() {
        return modelTrainingOpsService.listAll();
    }

    @Override
    public ModelTrainingStatusVO retrainModelByKey(String modelKey) {
        return modelTrainingOpsService.retrain(modelKey);
    }
```

- [ ] **Step 3: Update the existing unit test mocks**

In `AiRiskScoringServiceImplTest.java`, add to the `@Mock` field block:

```java
    @Mock
    com.insurance.aml.module.ai.service.support.ModelTrainingOpsService modelTrainingOpsService;
```

(Existing 3 guard tests stay untouched.)

- [ ] **Step 4: Controller — add two endpoints**

In `AiRiskScoringController.java`, add import `import com.insurance.aml.module.ai.model.dto.ModelTrainingStatusVO;` and add two endpoints after the existing `trainingStatus()` method:

```java
    @GetMapping("/models/training")
    @Operation(summary = "列出所有可训练模型的训练状态")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:view') or hasAuthority('monitoring:view')")
    public Result<List<ModelTrainingStatusVO>> listTrainableModels() {
        return Result.success(aiRiskScoringService.listTrainableModels());
    }

    @PostMapping("/models/training/{modelKey}/retrain")
    @Operation(summary = "按模型键触发训练")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:manage')")
    public Result<ModelTrainingStatusVO> retrainModelByKey(
            @PathVariable @Parameter(description = "模型键: supervised | anomaly") String modelKey) {
        return Result.success(aiRiskScoringService.retrainModelByKey(modelKey));
    }
```

- [ ] **Step 5: Compile + run unit-test regression**

```bash
mvn -q test-compile
mvn -q test -Dtest='AiRiskScoringServiceImplTest,AiRiskReviewServiceTest,AiRiskModelTrainingServiceTest,AiRiskSupervisedModelTest,AiRiskFeatureVectorizerTest,ModelTrainingOpsServiceTest,TransactionAnomalyDetectorRetrainTest'
```

Expected: ALL surefire reports show Failures: 0, Errors: 0. Counts (sum): 3 + 7 + 4 + 3 + 4 + 4 + 4 = 29.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/insurance/aml/module/ai/service/AiRiskScoringService.java \
        src/main/java/com/insurance/aml/module/ai/service/impl/AiRiskScoringServiceImpl.java \
        src/main/java/com/insurance/aml/module/ai/controller/AiRiskScoringController.java \
        src/test/java/com/insurance/aml/module/ai/service/AiRiskScoringServiceImplTest.java
git commit -m "feat: wire unified training ops endpoints"
```

---

### Task 6: Integration test — unified ops endpoints

**Files:**
- Modify: `src/test/java/com/insurance/aml/integration/AiRiskScoringIntegrationTest.java`

- [ ] **Step 1: Add the new test method**

In `AiRiskScoringIntegrationTest.java`, add a new `@Test` method before the class's closing brace. Use the existing helper fields/methods (`mockMvc`, `objectMapper`, `getAuthToken()`).

```java
    @Test
    @Order(3)
    @org.junit.jupiter.api.DisplayName("统一训练运维端点 - list + 触发 + 未知key")
    void trainingOps_listRetrainAndUnknownKey() throws Exception {
        String token = getAuthToken();

        // 1) GET /ai/risk/models/training 应返回两条 (supervised, anomaly) 顺序固定
        MvcResult listResult = mockMvc.perform(get("/ai/risk/models/training")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode list = objectMapper.readTree(listResult.getResponse().getContentAsString()).path("data");
        assertEquals(2, list.size());
        assertEquals("supervised", list.get(0).path("modelKey").asText());
        assertEquals("anomaly", list.get(1).path("modelKey").asText());

        // 2) POST /ai/risk/models/training/anomaly/retrain
        // H2 测试数据集中 t_transaction 通常不足以训练 (anomaly min-samples=4)，
        // 因此合法返回 SKIPPED_INSUFFICIENT 或 TRAINED；其它状态都不该出现。
        MvcResult retrainResult = mockMvc.perform(post("/ai/risk/models/training/anomaly/retrain")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(retrainResult.getResponse().getContentAsString()).path("data");
        assertEquals("anomaly", body.path("modelKey").asText());
        String status = body.path("status").asText();
        assertTrue("TRAINED".equals(status) || "SKIPPED_INSUFFICIENT".equals(status),
                "异常检测重训返回状态非预期: " + status);

        // 3) POST 未知 key → 400
        mockMvc.perform(post("/ai/risk/models/training/unknown-model/retrain")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().is4xxClientError());
    }
```

(`assertEquals`, `assertTrue`, `MvcResult`, `JsonNode`, `get`, `post`, `status` are already imported in this file.)

- [ ] **Step 2: Run the integration test**

```bash
mvn -q test -Dtest=AiRiskScoringIntegrationTest
```

Expected: surefire shows Tests run: 3, Failures: 0, Errors: 0. If the new method fails on auth (the @PreAuthorize 401/403), check that the integration test's admin token grants either `ROLE_ADMIN` or `model:manage`/`model:view`; the existing tests in this class already authenticate as `admin` with `admin123`, so they have the broadest scope.

- [ ] **Step 3: Full AI + monitoring regression**

```bash
rm -rf data/models/ai_risk_supervised.* data/models/isolation_forest.* data/models/normalization.*
mvn -q test
```

Expected: BUILD SUCCESS; surefire totals (run/fail/err): all integration + unit tests green, no regressions from the previous baseline (201 + new tests added by Tasks 2/3/4/5/6).

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/insurance/aml/integration/AiRiskScoringIntegrationTest.java
git commit -m "test: end-to-end unified training ops endpoints"
```

---

### Task 7: Finalize

- [ ] **Step 1: Full build sanity check**

```bash
mvn -q test > /dev/null 2>&1; echo "exit=$?"
```

Expected: `exit=0`.

- [ ] **Step 2: Merge to main and push** (matches the session's established flow)

```bash
git checkout main
git merge --ff-only feat/ai-p1-training-governance
git branch -d feat/ai-p1-training-governance
git push github main
```

- [ ] **Step 3: Report** spec §8 acceptance coverage back to the user.

---

## Self-Review

**Spec coverage:**
- §2 IN scope: cron/CAS/min-samples on detector → Task 3 ✓; FAILED on supervised → Task 2 ✓; ops service + endpoints → Tasks 4+5 ✓; old endpoints preserved → Task 5 (no removal) ✓.
- §3 data flow: covered by Tasks 2/3/4/5 ✓.
- §4 components: every row mapped to a task ✓.
- §5 config (3 yml files) → Task 3 Steps 5/6/7 ✓.
- §6 boundaries: SKIPPED_IN_PROGRESS (T3+T4), SKIPPED_INSUFFICIENT (T3), FAILED (T2+T3), unknown key (T4), NOT_TRAINED (T4 listAll), no-op shadow path (preserved — no change to predict() or applyShadowModelScore) ✓.
- §7 tests: each named test class is a task ✓.
- §8 acceptance: criteria 1–7 all covered.

**Placeholder scan:** none — no TBD/TODO/"similar to". All code blocks complete. Smile-version adaptations documented as a "no change needed" note in Task 3 Step 3 narrative.

**Type consistency:** `retrain()` returns `AnomalyTrainingResultVO` (T3) and `AiRiskTrainingResultVO` (T2); both have the `status`/`modelReady`/`sampleCount`/`trainedAt`/`message` shape used by T4's `toStatus()` helpers. `MODEL_KEY_SUPERVISED`/`MODEL_KEY_ANOMALY` defined in T4 are referenced by T4 tests via the literal strings ("supervised"/"anomaly"), matching constants. `getLastTrainStatus()`/`getLastTrainError()`/`getLastTrainSampleCount()`/`getLastTrainedAt()`/`isModelReady()` getters on `TransactionAnomalyDetector` defined in T3 are used by T4. `recordOutcome(String, String)` defined in T2 is used by T2's training-service wrapper.

No gaps found.
