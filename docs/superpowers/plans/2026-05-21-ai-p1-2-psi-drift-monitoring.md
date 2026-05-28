# AI P1-2 PSI Drift Monitoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Monitor the output distributions of the supervised model (`model_probability`) and the IsolationForest anomaly score for drift vs their training-time baselines using PSI, surfaced via a daily scheduled check and a read endpoint, with log-only warn/severe alerts.

**Architecture:** A pure-function `PsiCalculator` plus a `DistributionSnapshot` DTO captured at training time and persisted in per-model sidecar JSON files. A new `ModelDriftMonitorService` reads each baseline, builds the current-24h distribution (supervised from `t_ai_risk_score_record`, anomaly by recomputing `predict()` over recent transactions), computes PSI, classifies NORMAL/WARN/SEVERE/UNAVAILABLE, and exposes it through `GET /ai/risk/models/drift`. Hot scoring paths are untouched.

**Tech Stack:** Java 21, Spring Boot 3.4 (`@Scheduled`, `@Value`, `@PreAuthorize`), Smile 3.0.1 `IsolationForest.score`, Jackson, MyBatis-Plus, JdbcTemplate, JUnit 5 + Mockito, H2 for integration tests. Patterns matched against the merged P0/P1 work (`AiRiskModelTrainingService`, `AiRiskSupervisedModel`, `TransactionAnomalyDetector`, `ModelTrainingOpsService`).

---

## File Structure

| File | Responsibility |
|------|----------------|
| `module/ai/service/support/PsiCalculator.java` (create) | Static pure functions: `histogram(double[],int,double,double)` and `psi(int[],int[])` with ε smoothing |
| `module/ai/model/dto/DistributionSnapshot.java` (create) | Persisted baseline: bins/lo/hi/counts/total/capturedAt; Jackson-serializable |
| `module/ai/model/dto/ModelDriftStatusVO.java` (create) | Drift result: modelKey/status/psi/sampleCount/baselineSampleCount/computedAt/message |
| `module/ai/service/support/AiRiskSupervisedModel.java` (modify) | Add `trainingScoreDistribution` volatile + getter + `recordTrainingScoreDistribution()`; sidecar `supervised_distribution.json` save/load via internal static ObjectMapper |
| `module/ai/service/support/AiRiskModelTrainingService.java` (modify) | On TRAINED, record histogram of the existing `prob[]` array |
| `module/monitoring/service/TransactionAnomalyDetector.java` (modify) | Add `trainingScoreDistribution` volatile + getter; capture training-score histogram in `doTrain()`; sidecar `anomaly_distribution.json` save/load |
| `module/ai/service/support/ModelDriftMonitorService.java` (create) | Compute supervised + anomaly PSI; `@Scheduled` daily; classify; UNAVAILABLE guards |
| `module/ai/service/AiRiskScoringService.java` (modify) | Add `List<ModelDriftStatusVO> listModelDrift()` |
| `module/ai/service/impl/AiRiskScoringServiceImpl.java` (modify) | Inject monitor service; delegate |
| `module/ai/controller/AiRiskScoringController.java` (modify) | `GET /ai/risk/models/drift` |
| `application.yml` / `application-prod.yml` / `application-test.yml` (modify) | `aml.ml.drift.*` config |
| Tests | `PsiCalculatorTest`, `DistributionSnapshotJsonTest`, `ModelDriftMonitorServiceTest` (create); `AiRiskSupervisedModelTest`, `TransactionAnomalyDetectorRetrainTest`, `AiRiskScoringIntegrationTest` (extend) |

Java main under `src/main/java/com/insurance/aml/`, tests under `src/test/java/com/insurance/aml/`.

---

### Task 1: PsiCalculator (TDD)

**Files:**
- Create: `src/main/java/com/insurance/aml/module/ai/service/support/PsiCalculator.java`
- Test: `src/test/java/com/insurance/aml/module/ai/service/PsiCalculatorTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/insurance/aml/module/ai/service/PsiCalculatorTest.java`:

```java
package com.insurance.aml.module.ai.service;

import com.insurance.aml.module.ai.service.support.PsiCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PSI 计算器测试")
class PsiCalculatorTest {

    @Test
    @DisplayName("histogram 把 [lo,hi) 均匀分箱，越界值落入首/末箱")
    void histogram_bucketsAndClampsOutOfRange() {
        double[] values = {-1.0, 0.0, 0.05, 0.15, 0.99, 2.0};
        int[] h = PsiCalculator.histogram(values, 10, 0.0, 1.0);
        assertEquals(10, h.length);
        // -1.0 and 0.0 and 0.05 → bin 0 ; 2.0 and 0.99 → bin 9 ; 0.15 → bin 1
        assertEquals(3, h[0]);
        assertEquals(1, h[1]);
        assertEquals(2, h[9]);
    }

    @Test
    @DisplayName("相同分布 PSI 约等于 0")
    void psi_identicalDistributions_nearZero() {
        int[] a = {10, 20, 30, 40};
        int[] b = {10, 20, 30, 40};
        assertEquals(0.0, PsiCalculator.psi(a, b), 1e-9);
    }

    @Test
    @DisplayName("显著不同的分布 PSI 较大")
    void psi_differentDistributions_large() {
        int[] expected = {90, 5, 3, 2};
        int[] actual = {2, 3, 5, 90};
        assertTrue(PsiCalculator.psi(expected, actual) > 0.25,
                "强烈反转的分布 PSI 应超过严重阈值");
    }

    @Test
    @DisplayName("单边零计数被 ε 平滑，不产生 NaN/Infinity")
    void psi_zeroBin_smoothedFinite() {
        int[] expected = {50, 50, 0};
        int[] actual = {40, 40, 20};
        double psi = PsiCalculator.psi(expected, actual);
        assertTrue(Double.isFinite(psi), "零计数应被平滑为有限值, got " + psi);
    }

    @Test
    @DisplayName("长度不一致或全零返回 NaN")
    void psi_invalidInputs_nan() {
        assertTrue(Double.isNaN(PsiCalculator.psi(new int[]{1, 2}, new int[]{1, 2, 3})));
        assertTrue(Double.isNaN(PsiCalculator.psi(new int[]{0, 0}, new int[]{1, 1})));
    }

    @Test
    @DisplayName("bins=1 时同总量分布 PSI 为 0")
    void psi_singleBin_zero() {
        int[] h = PsiCalculator.histogram(new double[]{0.2, 0.5, 0.9}, 1, 0.0, 1.0);
        assertEquals(1, h.length);
        assertEquals(3, h[0]);
        assertEquals(0.0, PsiCalculator.psi(h, h), 1e-9);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=PsiCalculatorTest`
Expected: FAIL/compile error — `PsiCalculator` does not exist.

- [ ] **Step 3: Write the implementation**

Create `src/main/java/com/insurance/aml/module/ai/service/support/PsiCalculator.java`:

```java
package com.insurance.aml.module.ai.service.support;

/**
 * Population Stability Index 计算工具（纯函数，无状态）。
 *
 * <p>histogram 把数值序列按等宽分箱（越界值落入首/末箱）；psi 比较两个等长计数向量，
 * 用 ε 平滑避免 ln(0) / 除零。结果落在 [0, ∞)，越大漂移越严重。</p>
 */
public final class PsiCalculator {

    private static final double EPS = 1e-6;

    private PsiCalculator() {
    }

    public static int[] histogram(double[] values, int bins, double lo, double hi) {
        int safeBins = Math.max(1, bins);
        int[] counts = new int[safeBins];
        if (values == null || values.length == 0) {
            return counts;
        }
        double width = (hi - lo) / safeBins;
        for (double v : values) {
            int idx;
            if (width <= 0) {
                idx = 0;
            } else {
                idx = (int) Math.floor((v - lo) / width);
                if (idx < 0) {
                    idx = 0;
                } else if (idx >= safeBins) {
                    idx = safeBins - 1;
                }
            }
            counts[idx]++;
        }
        return counts;
    }

    public static double psi(int[] expected, int[] actual) {
        if (expected == null || actual == null
                || expected.length != actual.length || expected.length == 0) {
            return Double.NaN;
        }
        long expTotal = 0L;
        long actTotal = 0L;
        for (int c : expected) {
            expTotal += c;
        }
        for (int c : actual) {
            actTotal += c;
        }
        if (expTotal == 0L || actTotal == 0L) {
            return Double.NaN;
        }
        double psi = 0.0;
        for (int i = 0; i < expected.length; i++) {
            double e = Math.max((double) expected[i] / expTotal, EPS);
            double a = Math.max((double) actual[i] / actTotal, EPS);
            psi += (a - e) * Math.log(a / e);
        }
        return psi;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=PsiCalculatorTest`
Expected: PASS, 6 tests. Read `target/surefire-reports/com.insurance.aml.module.ai.service.PsiCalculatorTest.txt` to confirm "Tests run: 6, Failures: 0, Errors: 0".

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/insurance/aml/module/ai/service/support/PsiCalculator.java \
        src/test/java/com/insurance/aml/module/ai/service/PsiCalculatorTest.java
git commit -m "feat: add PsiCalculator (histogram + PSI with smoothing)"
```

---

### Task 2: DTOs (DistributionSnapshot + ModelDriftStatusVO) (TDD for JSON round-trip)

**Files:**
- Create: `src/main/java/com/insurance/aml/module/ai/model/dto/DistributionSnapshot.java`
- Create: `src/main/java/com/insurance/aml/module/ai/model/dto/ModelDriftStatusVO.java`
- Test: `src/test/java/com/insurance/aml/module/ai/service/DistributionSnapshotJsonTest.java`

- [ ] **Step 1: Create DistributionSnapshot**

Create `src/main/java/com/insurance/aml/module/ai/model/dto/DistributionSnapshot.java`:

```java
package com.insurance.aml.module.ai.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 训练时输出分布快照，作为 PSI 漂移监控的参考基线。持久化到模型旁路 JSON 文件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DistributionSnapshot {

    private int bins;
    private double lo;
    private double hi;
    private int[] counts;
    private int total;
    private LocalDateTime capturedAt;
}
```

- [ ] **Step 2: Create ModelDriftStatusVO**

Create `src/main/java/com/insurance/aml/module/ai/model/dto/ModelDriftStatusVO.java`:

```java
package com.insurance.aml.module.ai.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 模型漂移监控结果。status: NORMAL / WARN / SEVERE / UNAVAILABLE
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelDriftStatusVO {

    /** "supervised" 或 "anomaly" */
    private String modelKey;
    private String status;
    private Double psi;
    private int sampleCount;
    private int baselineSampleCount;
    private LocalDateTime computedAt;
    private String message;
}
```

- [ ] **Step 3: Write the failing JSON round-trip test**

Create `src/test/java/com/insurance/aml/module/ai/service/DistributionSnapshotJsonTest.java`:

```java
package com.insurance.aml.module.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.aml.module.ai.model.dto.DistributionSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("DistributionSnapshot JSON 往返测试")
class DistributionSnapshotJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules(); // 注册 JavaTime 模块以支持 LocalDateTime

    @Test
    @DisplayName("序列化后反序列化字段一致")
    void roundTrip() throws Exception {
        DistributionSnapshot snap = DistributionSnapshot.builder()
                .bins(4)
                .lo(0.0)
                .hi(1.0)
                .counts(new int[]{1, 2, 3, 4})
                .total(10)
                .capturedAt(LocalDateTime.of(2026, 5, 21, 4, 0, 0))
                .build();

        String json = mapper.writeValueAsString(snap);
        DistributionSnapshot back = mapper.readValue(json, DistributionSnapshot.class);

        assertEquals(4, back.getBins());
        assertEquals(0.0, back.getLo());
        assertEquals(1.0, back.getHi());
        assertArrayEquals(new int[]{1, 2, 3, 4}, back.getCounts());
        assertEquals(10, back.getTotal());
        assertEquals(LocalDateTime.of(2026, 5, 21, 4, 0, 0), back.getCapturedAt());
    }
}
```

- [ ] **Step 4: Run the test**

Run: `mvn -q test -Dtest=DistributionSnapshotJsonTest`
Expected: PASS, 1 test. (If `findAndRegisterModules()` cannot find the JavaTime module on the classpath, the test will fail at `LocalDateTime` deserialization — Spring Boot's `jackson-datatype-jsr310` is on the classpath in this project, so it will register. If it fails, the production code in Task 3/4 must use the same approach, so note it.)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/insurance/aml/module/ai/model/dto/DistributionSnapshot.java \
        src/main/java/com/insurance/aml/module/ai/model/dto/ModelDriftStatusVO.java \
        src/test/java/com/insurance/aml/module/ai/service/DistributionSnapshotJsonTest.java
git commit -m "feat: add DistributionSnapshot + ModelDriftStatusVO DTOs"
```

---

### Task 3: Supervised baseline capture + persistence (TDD)

**Files:**
- Modify: `src/main/java/com/insurance/aml/module/ai/service/support/AiRiskSupervisedModel.java`
- Modify: `src/main/java/com/insurance/aml/module/ai/service/support/AiRiskModelTrainingService.java`
- Test: `src/test/java/com/insurance/aml/module/ai/service/AiRiskSupervisedModelTest.java` (extend)

- [ ] **Step 1: Write the failing test**

Append to `AiRiskSupervisedModelTest`:

```java
    @Test
    @org.junit.jupiter.api.DisplayName("训练分布快照 record + save/load 往返")
    void trainingScoreDistribution_roundTrip(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
        com.insurance.aml.module.ai.model.dto.DistributionSnapshot snap =
                com.insurance.aml.module.ai.model.dto.DistributionSnapshot.builder()
                        .bins(10).lo(0.0).hi(1.0)
                        .counts(new int[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1}).total(10)
                        .capturedAt(java.time.LocalDateTime.now()).build();

        AiRiskSupervisedModel m = new AiRiskSupervisedModel();
        org.springframework.test.util.ReflectionTestUtils.setField(m, "modelPath", dir.toString());
        m.recordTrainingScoreDistribution(snap);
        assertEquals(10, m.getTrainingScoreDistribution().getBins());

        AiRiskSupervisedModel reloaded = new AiRiskSupervisedModel();
        org.springframework.test.util.ReflectionTestUtils.setField(reloaded, "modelPath", dir.toString());
        reloaded.init();
        // init() loads the model (absent here) silently; loadDistribution reads the sidecar
        assertEquals(10, reloaded.getTrainingScoreDistribution().getTotal());
        assertArrayEquals(new int[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
                reloaded.getTrainingScoreDistribution().getCounts());
    }
```

Ensure `assertArrayEquals` is imported in the test file (add `import static org.junit.jupiter.api.Assertions.assertArrayEquals;` if missing).

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=AiRiskSupervisedModelTest`
Expected: FAIL — `recordTrainingScoreDistribution` / `getTrainingScoreDistribution` do not exist.

- [ ] **Step 3: Implement on AiRiskSupervisedModel**

In `AiRiskSupervisedModel.java`:

a) Add imports:

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.aml.module.ai.model.dto.DistributionSnapshot;
```

b) Add a static ObjectMapper constant and a sidecar filename constant near the existing `MODEL_FILE`/`META_FILE` constants:

```java
    private static final String DISTRIBUTION_FILE = "supervised_distribution.json";
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
```

c) Add the field next to the other `@Getter volatile` fields:

```java
    @Getter private volatile DistributionSnapshot trainingScoreDistribution;
```

d) Add the record method (place near `recordOutcome`):

```java
    /**
     * 记录训练时输出分布作为漂移监控基线，并落盘到旁路 JSON 文件。
     */
    public void recordTrainingScoreDistribution(DistributionSnapshot snapshot) {
        this.trainingScoreDistribution = snapshot;
        try {
            java.nio.file.Path dir = java.nio.file.Paths.get(modelPath);
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Path tmp = dir.resolve(DISTRIBUTION_FILE + ".tmp");
            JSON.writeValue(tmp.toFile(), snapshot);
            java.nio.file.Files.move(tmp, dir.resolve(DISTRIBUTION_FILE),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            log.warn("[AI-ML] 监督模型训练分布落盘失败: {}", e.getMessage());
        }
    }

    private void loadDistribution() {
        java.nio.file.Path file = java.nio.file.Paths.get(modelPath, DISTRIBUTION_FILE);
        if (!java.nio.file.Files.exists(file)) {
            return;
        }
        try {
            this.trainingScoreDistribution = JSON.readValue(file.toFile(), DistributionSnapshot.class);
        } catch (Exception e) {
            log.warn("[AI-ML] 监督模型训练分布加载失败: {}", e.getMessage());
        }
    }
```

e) In the existing `init()` method (the `@PostConstruct`), after the existing `load()` call (inside or after its try/catch), add a call to `loadDistribution()`. The existing `init()` looks like:

```java
    @PostConstruct
    public void init() {
        try {
            load();
        } catch (Exception e) {
            log.warn("[AI-ML] 监督模型加载失败，等待训练: {}", e.getMessage());
        }
        loadDistribution();
    }
```

(Add only the `loadDistribution();` line after the catch block; keep the existing body.)

- [ ] **Step 4: Run model test to verify it passes**

Run: `mvn -q test -Dtest=AiRiskSupervisedModelTest`
Expected: PASS (the original tests + the new round-trip test).

- [ ] **Step 5: Wire the capture into training**

In `AiRiskModelTrainingService.java`, add imports:

```java
import com.insurance.aml.module.ai.model.dto.DistributionSnapshot;
import com.insurance.aml.module.ai.service.support.PsiCalculator;
import java.time.LocalDateTime;
```

In `doRetrain()`, locate the TRAINED success path where `supervisedModel.replace(model, sampleCount, ...)` is called (after `prob[]` is computed and the model is replaced). Immediately after the `supervisedModel.replace(...)` line, add:

```java
        supervisedModel.recordTrainingScoreDistribution(DistributionSnapshot.builder()
                .bins(10).lo(0.0).hi(1.0)
                .counts(PsiCalculator.histogram(prob, 10, 0.0, 1.0))
                .total(prob.length)
                .capturedAt(LocalDateTime.now())
                .build());
```

`prob` is the existing `double[] prob = new double[y.length]` local already computed for AUC. If `LocalDateTime` is already imported, do not duplicate the import.

- [ ] **Step 6: Run supervised training tests**

Run: `mvn -q test -Dtest='AiRiskModelTrainingServiceTest,AiRiskSupervisedModelTest'`
Expected: PASS (AiRiskModelTrainingServiceTest 5, AiRiskSupervisedModelTest = prior count + 1).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/insurance/aml/module/ai/service/support/AiRiskSupervisedModel.java \
        src/main/java/com/insurance/aml/module/ai/service/support/AiRiskModelTrainingService.java \
        src/test/java/com/insurance/aml/module/ai/service/AiRiskSupervisedModelTest.java
git commit -m "feat: capture supervised training score distribution baseline"
```

---

### Task 4: Anomaly baseline capture + persistence (TDD)

**Files:**
- Modify: `src/main/java/com/insurance/aml/module/monitoring/service/TransactionAnomalyDetector.java`
- Test: `src/test/java/com/insurance/aml/module/monitoring/service/TransactionAnomalyDetectorRetrainTest.java` (extend)

- [ ] **Step 1: Write the failing test**

Append to `TransactionAnomalyDetectorRetrainTest`:

```java
    @Test
    @DisplayName("训练成功后捕获分布快照，counts 总和等于训练样本数")
    void retrain_capturesTrainingDistribution() {
        List<Transaction> rows = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            rows.add(txn(100.0 + i, i));
        }
        when(transactionMapper.selectList(any())).thenReturn(rows);
        TransactionAnomalyDetector d = newDetector();

        AnomalyTrainingResultVO result = d.retrain();

        assertEquals("TRAINED", result.getStatus());
        com.insurance.aml.module.ai.model.dto.DistributionSnapshot snap =
                d.getTrainingScoreDistribution();
        org.junit.jupiter.api.Assertions.assertNotNull(snap, "训练后应有分布快照");
        int sum = 0;
        for (int c : snap.getCounts()) {
            sum += c;
        }
        assertEquals(60, sum, "counts 总和应等于训练样本数");
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=TransactionAnomalyDetectorRetrainTest`
Expected: FAIL — `getTrainingScoreDistribution` does not exist.

- [ ] **Step 3: Implement on TransactionAnomalyDetector**

In `TransactionAnomalyDetector.java`:

a) Add imports:

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.aml.module.ai.model.dto.DistributionSnapshot;
import com.insurance.aml.module.ai.service.support.PsiCalculator;
```

b) Add sidecar filename + static ObjectMapper constants near the top of the class (next to other constants/fields):

```java
    private static final String DISTRIBUTION_FILE = "anomaly_distribution.json";
    private static final ObjectMapper DRIFT_JSON = new ObjectMapper().findAndRegisterModules();
```

c) Add the field next to the other `@Getter volatile` fields:

```java
    @Getter private volatile DistributionSnapshot trainingScoreDistribution;
```

d) In `doTrain()`, after the `model = IsolationForest.fit(...)` line and the existing `logAnomalyDistribution(features)` call, add a call to a new private method `captureTrainingDistribution(features)`. Find the block (around line 251-260) where fit succeeds and `saveModel()` / `logAnomalyDistribution(features)` are invoked, and add right after the existing `logAnomalyDistribution(features);`:

```java
            captureTrainingDistribution(features);
```

e) Add the new private methods (place near `logAnomalyDistribution`):

```java
    /**
     * 捕获训练集异常分分布作为漂移监控基线，并落盘到旁路 JSON 文件。
     */
    private void captureTrainingDistribution(double[][] features) {
        try {
            double[] scores = new double[features.length];
            for (int i = 0; i < features.length; i++) {
                scores[i] = model.score(features[i]);
            }
            DistributionSnapshot snapshot = DistributionSnapshot.builder()
                    .bins(10).lo(0.0).hi(1.0)
                    .counts(PsiCalculator.histogram(scores, 10, 0.0, 1.0))
                    .total(scores.length)
                    .capturedAt(java.time.LocalDateTime.now())
                    .build();
            this.trainingScoreDistribution = snapshot;
            java.nio.file.Path dir = java.nio.file.Paths.get(modelPath);
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Path tmp = dir.resolve(DISTRIBUTION_FILE + ".tmp");
            DRIFT_JSON.writeValue(tmp.toFile(), snapshot);
            java.nio.file.Files.move(tmp, dir.resolve(DISTRIBUTION_FILE),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            log.warn("[ML] 异常检测训练分布捕获失败: {}", e.getMessage());
        }
    }

    private void loadTrainingDistribution() {
        java.nio.file.Path file = java.nio.file.Paths.get(modelPath, DISTRIBUTION_FILE);
        if (!java.nio.file.Files.exists(file)) {
            return;
        }
        try {
            this.trainingScoreDistribution = DRIFT_JSON.readValue(file.toFile(), DistributionSnapshot.class);
        } catch (Exception e) {
            log.warn("[ML] 异常检测训练分布加载失败: {}", e.getMessage());
        }
    }
```

f) In the existing `@PostConstruct init()` method, after the existing model-load logic, add a call to `loadTrainingDistribution();`. Read the current `init()` body and append the single line at the end of the method (after any model load attempt), keeping all existing behavior.

- [ ] **Step 4: Run anomaly tests to verify they pass**

Run: `mvn -q test -Dtest=TransactionAnomalyDetectorRetrainTest`
Expected: PASS (prior 5 tests + 1 new = 6). Read the surefire report to confirm.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/insurance/aml/module/monitoring/service/TransactionAnomalyDetector.java \
        src/test/java/com/insurance/aml/module/monitoring/service/TransactionAnomalyDetectorRetrainTest.java
git commit -m "feat: capture anomaly training score distribution baseline"
```

---

### Task 5: ModelDriftMonitorService (TDD)

**Files:**
- Create: `src/main/java/com/insurance/aml/module/ai/service/support/ModelDriftMonitorService.java`
- Test: `src/test/java/com/insurance/aml/module/ai/service/ModelDriftMonitorServiceTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/insurance/aml/module/ai/service/ModelDriftMonitorServiceTest.java`:

```java
package com.insurance.aml.module.ai.service;

import com.insurance.aml.module.ai.model.dto.DistributionSnapshot;
import com.insurance.aml.module.ai.model.dto.ModelDriftStatusVO;
import com.insurance.aml.module.ai.service.support.AiRiskSupervisedModel;
import com.insurance.aml.module.ai.service.support.ModelDriftMonitorService;
import com.insurance.aml.module.monitoring.mapper.TransactionMapper;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import com.insurance.aml.module.monitoring.service.TransactionAnomalyDetector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("模型漂移监控服务测试")
class ModelDriftMonitorServiceTest {

    @Mock AiRiskSupervisedModel supervisedModel;
    @Mock TransactionAnomalyDetector anomalyDetector;
    @Mock JdbcTemplate jdbcTemplate;
    @Mock TransactionMapper transactionMapper;

    private ModelDriftMonitorService newService() {
        ModelDriftMonitorService s = new ModelDriftMonitorService(
                supervisedModel, anomalyDetector, jdbcTemplate, transactionMapper);
        ReflectionTestUtils.setField(s, "windowHours", 24);
        ReflectionTestUtils.setField(s, "bins", 10);
        ReflectionTestUtils.setField(s, "warnThreshold", 0.1);
        ReflectionTestUtils.setField(s, "severeThreshold", 0.25);
        ReflectionTestUtils.setField(s, "anomalySampleCap", 10000);
        ReflectionTestUtils.setField(s, "minSamples", 10);
        return s;
    }

    private DistributionSnapshot uniformBaseline() {
        return DistributionSnapshot.builder()
                .bins(10).lo(0.0).hi(1.0)
                .counts(new int[]{10, 10, 10, 10, 10, 10, 10, 10, 10, 10}).total(100)
                .capturedAt(LocalDateTime.now()).build();
    }

    private List<Double> uniformProbs() {
        List<Double> probs = new ArrayList<>();
        for (int b = 0; b < 10; b++) {
            for (int k = 0; k < 5; k++) {
                probs.add(b * 0.1 + 0.05);
            }
        }
        return probs; // 50 values evenly spread across 10 bins
    }

    @Test
    @DisplayName("监督基线缺失 → UNAVAILABLE")
    void supervised_noBaseline_unavailable() {
        when(supervisedModel.getTrainingScoreDistribution()).thenReturn(null);
        ModelDriftStatusVO vo = newService().computeSupervisedDrift();
        assertEquals("supervised", vo.getModelKey());
        assertEquals("UNAVAILABLE", vo.getStatus());
    }

    @Test
    @DisplayName("监督当前样本不足 → UNAVAILABLE")
    void supervised_insufficientSamples_unavailable() {
        when(supervisedModel.getTrainingScoreDistribution()).thenReturn(uniformBaseline());
        when(jdbcTemplate.queryForList(anyString(), eq(Double.class), any()))
                .thenReturn(List.of(0.1, 0.2, 0.3)); // 3 < minSamples(10)
        ModelDriftStatusVO vo = newService().computeSupervisedDrift();
        assertEquals("UNAVAILABLE", vo.getStatus());
    }

    @Test
    @DisplayName("监督相同分布 → NORMAL")
    void supervised_sameDistribution_normal() {
        when(supervisedModel.getTrainingScoreDistribution()).thenReturn(uniformBaseline());
        when(jdbcTemplate.queryForList(anyString(), eq(Double.class), any()))
                .thenReturn(uniformProbs());
        ModelDriftStatusVO vo = newService().computeSupervisedDrift();
        assertEquals("NORMAL", vo.getStatus());
    }

    @Test
    @DisplayName("监督显著偏移 → SEVERE")
    void supervised_shifted_severe() {
        when(supervisedModel.getTrainingScoreDistribution()).thenReturn(uniformBaseline());
        List<Double> skewed = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            skewed.add(0.95); // all in last bin
        }
        when(jdbcTemplate.queryForList(anyString(), eq(Double.class), any())).thenReturn(skewed);
        ModelDriftStatusVO vo = newService().computeSupervisedDrift();
        assertEquals("SEVERE", vo.getStatus());
    }

    @Test
    @DisplayName("异常基线缺失 → UNAVAILABLE")
    void anomaly_noBaseline_unavailable() {
        when(anomalyDetector.getTrainingScoreDistribution()).thenReturn(null);
        ModelDriftStatusVO vo = newService().computeAnomalyDrift();
        assertEquals("anomaly", vo.getModelKey());
        assertEquals("UNAVAILABLE", vo.getStatus());
    }

    @Test
    @DisplayName("异常正常路径 → NORMAL")
    void anomaly_sameDistribution_normal() {
        when(anomalyDetector.getTrainingScoreDistribution()).thenReturn(uniformBaseline());
        List<Transaction> txns = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            txns.add(new Transaction());
        }
        when(transactionMapper.selectList(any())).thenReturn(txns);
        // predict returns values spread across the 10 bins to mirror the uniform baseline
        final int[] call = {0};
        when(anomalyDetector.predict(any())).thenAnswer(inv -> {
            double v = (call[0]++ % 10) * 0.1 + 0.05;
            return v;
        });
        ModelDriftStatusVO vo = newService().computeAnomalyDrift();
        assertEquals("NORMAL", vo.getStatus());
    }

    @Test
    @DisplayName("computeAll 返回固定顺序 [supervised, anomaly]")
    void computeAll_fixedOrder() {
        when(supervisedModel.getTrainingScoreDistribution()).thenReturn(null);
        when(anomalyDetector.getTrainingScoreDistribution()).thenReturn(null);
        List<ModelDriftStatusVO> all = newService().computeAll();
        assertEquals(2, all.size());
        assertEquals("supervised", all.get(0).getModelKey());
        assertEquals("anomaly", all.get(1).getModelKey());
    }

    @Test
    @DisplayName("scheduledDriftCheck 吞掉异常不外抛")
    void scheduledDriftCheck_swallowsExceptions() {
        when(supervisedModel.getTrainingScoreDistribution())
                .thenThrow(new RuntimeException("boom"));
        newService().scheduledDriftCheck(); // must not throw
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=ModelDriftMonitorServiceTest`
Expected: FAIL/compile error — `ModelDriftMonitorService` does not exist.

- [ ] **Step 3: Implement ModelDriftMonitorService**

Create `src/main/java/com/insurance/aml/module/ai/service/support/ModelDriftMonitorService.java`:

```java
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
                .last("LIMIT " + anomalySampleCap);
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
        if (!scores.isEmpty() && skipped > 0) {
            log.warn("[AI-Drift] anomaly 重算跳过 {} 条", skipped);
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=ModelDriftMonitorServiceTest`
Expected: PASS, 8 tests. If the supervised query stub mismatch occurs (Mockito `queryForList` overload resolution), confirm the production call uses exactly `jdbcTemplate.queryForList(String, Class, Object...)` — the test stubs `queryForList(anyString(), eq(Double.class), any())` which matches the varargs form with a single `since` argument.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/insurance/aml/module/ai/service/support/ModelDriftMonitorService.java \
        src/test/java/com/insurance/aml/module/ai/service/ModelDriftMonitorServiceTest.java
git commit -m "feat: add ModelDriftMonitorService (supervised + anomaly PSI)"
```

---

### Task 6: Wire interface + impl + controller + config

**Files:**
- Modify: `src/main/java/com/insurance/aml/module/ai/service/AiRiskScoringService.java`
- Modify: `src/main/java/com/insurance/aml/module/ai/service/impl/AiRiskScoringServiceImpl.java`
- Modify: `src/main/java/com/insurance/aml/module/ai/controller/AiRiskScoringController.java`
- Modify: `src/test/java/com/insurance/aml/module/ai/service/AiRiskScoringServiceImplTest.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/resources/application-prod.yml`
- Modify: `src/test/resources/application-test.yml`

- [ ] **Step 1: Interface — add method**

In `AiRiskScoringService.java`, add import `import com.insurance.aml.module.ai.model.dto.ModelDriftStatusVO;` and add to the interface body:

```java
    List<ModelDriftStatusVO> listModelDrift();
```

- [ ] **Step 2: Impl — inject + delegate**

In `AiRiskScoringServiceImpl.java`, add imports:

```java
import com.insurance.aml.module.ai.model.dto.ModelDriftStatusVO;
import com.insurance.aml.module.ai.service.support.ModelDriftMonitorService;
```

Add a field next to `modelTrainingOpsService`:

```java
    private final ModelDriftMonitorService modelDriftMonitorService;
```

Add the override after `listTrainableModels()` / `retrainModelByKey()`:

```java
    @Override
    public List<ModelDriftStatusVO> listModelDrift() {
        return modelDriftMonitorService.computeAll();
    }
```

- [ ] **Step 3: Update the impl unit test mocks**

In `AiRiskScoringServiceImplTest.java`, add a `@Mock` field alongside the others:

```java
    @Mock
    com.insurance.aml.module.ai.service.support.ModelDriftMonitorService modelDriftMonitorService;
```

(Do not change existing test methods.)

- [ ] **Step 4: Controller — add endpoint**

In `AiRiskScoringController.java`, add import `import com.insurance.aml.module.ai.model.dto.ModelDriftStatusVO;` and add this endpoint after the `listTrainableModels()` method:

```java
    @GetMapping("/models/drift")
    @Operation(summary = "查询模型分布漂移 (PSI)")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:view') or hasAuthority('monitoring:view')")
    public Result<List<ModelDriftStatusVO>> listModelDrift() {
        return Result.success(aiRiskScoringService.listModelDrift());
    }
```

- [ ] **Step 5: application.yml**

In `src/main/resources/application.yml`, under the `aml.ml` node (sibling to `ai-risk` and `anomaly`), add:

```yaml
    drift:
      cron: "0 0 4 * * ?"
      window-hours: 24
      bins: 10
      warn-threshold: 0.1
      severe-threshold: 0.25
      anomaly-sample-cap: 10000
      min-samples: 50
```

- [ ] **Step 6: application-prod.yml**

In `src/main/resources/application-prod.yml`, under `aml.ml` (sibling to `ai-risk` and `anomaly`), add:

```yaml
    drift:
      cron: "${AML_ML_DRIFT_CRON:0 0 4 * * ?}"
      window-hours: ${AML_ML_DRIFT_WINDOW_HOURS:24}
      bins: ${AML_ML_DRIFT_BINS:10}
      warn-threshold: ${AML_ML_DRIFT_WARN_THRESHOLD:0.1}
      severe-threshold: ${AML_ML_DRIFT_SEVERE_THRESHOLD:0.25}
      anomaly-sample-cap: ${AML_ML_DRIFT_ANOMALY_SAMPLE_CAP:10000}
      min-samples: ${AML_ML_DRIFT_MIN_SAMPLES:50}
```

- [ ] **Step 7: application-test.yml**

In `src/test/resources/application-test.yml`, under the `aml.ml` node (sibling to `ai-risk` / `anomaly`), add:

```yaml
    drift:
      min-samples: 10
```

- [ ] **Step 8: Compile + unit-test regression**

```bash
mvn -q test-compile
mvn -q test -Dtest='AiRiskScoringServiceImplTest,PsiCalculatorTest,DistributionSnapshotJsonTest,ModelDriftMonitorServiceTest,AiRiskSupervisedModelTest,TransactionAnomalyDetectorRetrainTest,AiRiskModelTrainingServiceTest,ModelTrainingOpsServiceTest,AiRiskReviewServiceTest'
```

Expected: ALL surefire reports show Failures: 0, Errors: 0. `AiRiskScoringServiceImplTest` still 3 (guard tests unchanged with one new mock).

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/insurance/aml/module/ai/service/AiRiskScoringService.java \
        src/main/java/com/insurance/aml/module/ai/service/impl/AiRiskScoringServiceImpl.java \
        src/main/java/com/insurance/aml/module/ai/controller/AiRiskScoringController.java \
        src/test/java/com/insurance/aml/module/ai/service/AiRiskScoringServiceImplTest.java \
        src/main/resources/application.yml src/main/resources/application-prod.yml \
        src/test/resources/application-test.yml
git commit -m "feat: wire drift endpoint + config"
```

---

### Task 7: Integration test + finalize

**Files:**
- Modify: `src/test/java/com/insurance/aml/integration/AiRiskScoringIntegrationTest.java`

- [ ] **Step 1: Add the integration test method**

In `AiRiskScoringIntegrationTest.java`, add a new `@Test` before the class's closing brace, reusing existing helpers (`mockMvc`, `objectMapper`, `getAuthToken`):

```java
    @Test
    @Order(4)
    @org.junit.jupiter.api.DisplayName("模型漂移监控端点 - 返回 supervised + anomaly")
    void modelDriftEndpoint_returnsBothModels() throws Exception {
        String token = getAuthToken();
        MvcResult result = mockMvc.perform(get("/ai/risk/models/drift")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertEquals(2, data.size());
        assertEquals("supervised", data.get(0).path("modelKey").asText());
        assertEquals("anomaly", data.get(1).path("modelKey").asText());
        String supervisedStatus = data.get(0).path("status").asText();
        assertTrue(
                "NORMAL".equals(supervisedStatus) || "WARN".equals(supervisedStatus)
                        || "SEVERE".equals(supervisedStatus) || "UNAVAILABLE".equals(supervisedStatus),
                "status 应为四种合法值之一: " + supervisedStatus);
    }
```

(`assertEquals`, `assertTrue`, `MvcResult`, `JsonNode`, `get`, `status` already imported.)

- [ ] **Step 2: Run the integration test**

```bash
mvn -q test -Dtest=AiRiskScoringIntegrationTest
```

Expected: Tests run: 4, Failures: 0, Errors: 0. (The drift endpoint returns UNAVAILABLE for both models in a fresh H2 context where no training has produced a baseline — a valid `status` value.)

- [ ] **Step 3: Full regression**

```bash
rm -rf data/models/ai_risk_supervised.* data/models/supervised_distribution.json \
       data/models/isolation_forest.* data/models/normalization.* data/models/anomaly_distribution.json
mvn -q test
```

Expected: BUILD SUCCESS; no regressions. Total = prior 215 + new tests from Tasks 1–7.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/insurance/aml/integration/AiRiskScoringIntegrationTest.java
git commit -m "test: end-to-end model drift endpoint"
```

- [ ] **Step 5: Merge to main and push** (matches the session's established flow)

```bash
git checkout main
git merge --ff-only feat/ai-p1-2-psi-drift
git branch -d feat/ai-p1-2-psi-drift
git push github main
```

- [ ] **Step 6: Report** spec §8 acceptance coverage to the user.

---

## Self-Review

**Spec coverage:**
- §2 IN: PsiCalculator (T1), DistributionSnapshot+VO (T2), supervised baseline capture+persist (T3), anomaly baseline capture+persist (T4), ModelDriftMonitorService + @Scheduled (T5), interface/impl/controller + ModelDriftStatusVO fields (T6), config (T6). ✓
- §3 data flow: training-time capture (T3/T4) + monitor-time compute (T5) ✓.
- §4 components: every row mapped to a task ✓.
- §5 config (3 yml) → T6 Steps 5/6/7 ✓.
- §6 boundaries: baseline null (T5 unavailable + tests a/e), sample<min (T5 classify + tests b), NaN/∞ (T5 classify), thresholds NORMAL/WARN/SEVERE (T5 + tests c/d), @Scheduled swallow (T5 + test h), predict-skip (T5 try/catch). Sidecar JSON persistence keeps .meta/.stats untouched (T3/T4). ✓
- §7 tests: PsiCalculatorTest, DistributionSnapshotJsonTest, ModelDriftMonitorServiceTest, AiRiskSupervisedModelTest+, TransactionAnomalyDetectorRetrainTest+, AiRiskScoringIntegrationTest+ — all are tasks ✓.
- §8 acceptance 1–7: 1→T6/T7, 2→T5, 3→T5, 4→T5 scheduled, 5→T3/T4 load on init, 6→T7 full regression, 7→T6 (only additive endpoint). ✓

**Placeholder scan:** none. All code blocks complete. Jackson JavaTime registration handled explicitly via `findAndRegisterModules()`.

**Type consistency:** `DistributionSnapshot` fields (bins/lo/hi/counts/total/capturedAt) used identically in T2 (def), T3 (supervised build), T4 (anomaly build), T5 (classify reads getBins/getLo/getHi/getCounts/getTotal). `PsiCalculator.histogram(double[],int,double,double)` / `psi(int[],int[])` consistent T1↔T3↔T4↔T5. `ModelDriftStatusVO` builder fields consistent T2↔T5. `recordTrainingScoreDistribution(DistributionSnapshot)` def T3 used by T3 training service. `getTrainingScoreDistribution()` def T3/T4 used by T5. `ModelDriftMonitorService` constructor `(supervisedModel, anomalyDetector, jdbcTemplate, transactionMapper)` consistent T5 test ↔ T5 impl ↔ T6 (Spring injects). `computeAll()/computeSupervisedDrift()/computeAnomalyDrift()/scheduledDriftCheck()` consistent T5↔T6.

No gaps found.
