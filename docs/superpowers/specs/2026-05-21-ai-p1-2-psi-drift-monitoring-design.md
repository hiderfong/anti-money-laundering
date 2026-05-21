# AI P1-2 — PSI 漂移监控（监督模型概率 + IF 异常分）

> 日期：2026-05-21
> 代码基线：`4d9fb41` (main，P1 训练治理已合入)
> 关联：`docs/development/AI反洗钱增强建议-20260519.md` P1 第二条；P1 训练治理 spec `docs/superpowers/specs/2026-05-20-ai-p1-training-governance-design.md`
> 状态：设计已确认，待 spec review → writing-plans

---

## 1. 背景与目标

P0/P1 落地后，监督模型与 IsolationForest 都具备完整的训练治理（cron + CAS + min-samples + FAILED + 状态可见）。但模型上线后的两类陈旧风险仍无可观测信号：
1. **特征/输出分布漂移**：业务发生变化（新产品、新群体、季节性），模型输入分布偏离训练时分布，即使没有新数据/标签，模型预测也开始失准。
2. **静默退化**：训练时一切正常的指标，在生产数据上随时间慢慢偏移，无主动信号。

PSI（Population Stability Index）是业界对一维分布漂移的标准度量，规则简单可解释，适合规则化告警：`<0.1` 正常、`0.1≤PSI<0.25` 警示、`≥0.25` 严重漂移。

**本轮目标**：对两个模型的"输出分布"实施 PSI 漂移监控，以"训练时快照"为基线，"近 24h 实际分布"为当前，每日定时计算并按阈值落日志告警；提供运维端点按需查询。

---

## 2. 关键决策（已确认）

| 决策 | 选择 |
|------|------|
| 监控对象 | 仅模型输出：监督模型 `model_probability`、IF 异常分（共 2 条一维分布）；不监控多维特征 |
| 基线参考 | 训练时快照，持久化进各自模型的 sidecar 文件（`.meta` / `anomaly_distribution.json`） |
| 当前分布 | 过去 24h 滑动窗口；监督读 `t_ai_risk_score_record`，IF 重算 `t_transaction` 近 24h（cap 10000） |
| 触发动作 | 仅日志告警（warn/error），**不**自动重训、**不**发邮件/IM |
| 计算调度 | `@Scheduled` 每日 04:00 + on-demand `GET /ai/risk/models/drift` |

**IN（本轮）**
- 纯函数 `PsiCalculator`（histogram + psi，带 ε 平滑）
- `DistributionSnapshot` DTO，Jackson 可序列化
- `AiRiskSupervisedModel` 加 `trainingScoreDistribution` 字段 + save/load 的 .meta JSON 子条目
- `TransactionAnomalyDetector` 加 `trainingScoreDistribution` 字段 + 旁路 `anomaly_distribution.json` 落盘/加载
- 两个模型的训练服务在 TRAINED 成功路径上分别调用 `record*()` 写入分布
- 新 `ModelDriftMonitorService`：`computeSupervisedDrift()` / `computeAnomalyDrift()` / `computeAll()` / `@Scheduled`
- `AiRiskScoringService` 接口加 `listModelDrift()`；impl 委派；controller 加 `GET /ai/risk/models/drift`
- `ModelDriftStatusVO` 包含 status (NORMAL/WARN/SEVERE/UNAVAILABLE)、psi、sampleCount、baselineSampleCount、computedAt、message

**OUT（推迟独立轮次）**
- 多维特征级 PSI（每个 feature 单独跟踪）
- 自动触发重训
- 邮件/IM/PagerDuty 集成
- 历史 PSI 时序留痕（不入库）
- PSI 触发的模型回滚

**不变量**
- `TransactionAnomalyDetector.predict(Transaction) → double` 行为完全不变
- `AiRiskScoringServiceImpl.applyShadowModelScore` 行为完全不变
- 既有训练流程行为不变；只在 `replace()` / 训练成功路径**追加**写入分布快照
- 不引入新表，不修改既有表 schema

---

## 3. 架构与数据流

```
训练时（每次成功重训）
   ┌─────────────────────┐         ┌──────────────────────────────┐
   │ AiRiskModelTraining │         │ TransactionAnomalyDetector   │
   │   .retrain()→TRAINED│         │   .retrain()→TRAINED         │
   │  (已有 prob[] 数组) │         │  (训练后对每条样本 score)    │
   └──────────┬──────────┘         └──────────────┬───────────────┘
              │                                   │
              │ histogram(prob, 10, 0, 1)         │ histogram(scores, 10, 0, 1)
              ▼                                   ▼
   AiRiskSupervisedModel              TransactionAnomalyDetector
   .recordTrainingScoreDistribution   .recordTrainingScoreDistribution
   ── 落 .meta 的 JSON 子条目         ── 落 anomaly_distribution.json

监控时（@Scheduled 每日 04:00 / GET /ai/risk/models/drift）
   ModelDriftMonitorService.computeAll()
     │
     ├── supervised:
     │     baseline = supervisedModel.getTrainingScoreDistribution()  // 可能 null
     │     current  = jdbcTemplate.query (SELECT model_probability
     │                FROM t_ai_risk_score_record
     │                WHERE scored_at >= now-24h AND model_probability IS NOT NULL)
     │     PsiCalculator.psi(baseline.counts, histogram(current))
     │
     └── anomaly:
           baseline = detector.getTrainingScoreDistribution()         // 可能 null
           transactions = transactionMapper.selectList
                          (transaction_time >= now-24h, status=SUCCESS, LIMIT 10000)
           scores = transactions.stream().map(detector::predict)      // 读锁，不阻塞
           PsiCalculator.psi(baseline.counts, histogram(scores))

  每条结果 → ModelDriftStatusVO {modelKey, status, psi, sampleCount, baselineSampleCount, computedAt, message}
  @Scheduled 路径再按阈值 log.warn / log.error
```

---

## 4. 组件清单

| 组件 | 位置 | 责任 | 依赖 |
|------|------|------|------|
| `PsiCalculator` (新, util) | `module/ai/service/support/` | 静态纯函数：`int[] histogram(double[] values, int bins, double lo, double hi)`（lo/hi 外的值落入首/末 bin）、`double psi(int[] expected, int[] actual)`（ε=1e-6 平滑，NaN/∞ 由调用方处理）。无 Spring 依赖 | 无 |
| `DistributionSnapshot` (新, DTO) | `module/ai/model/dto/` | `@Data @Builder @NoArgsConstructor @AllArgsConstructor`：`int bins; double lo; double hi; int[] counts; int total; LocalDateTime capturedAt`。Jackson 友好 | 无 |
| `ModelDriftStatusVO` (新, DTO) | `module/ai/model/dto/` | 运维查询返回：`String modelKey; String status; Double psi; int sampleCount; int baselineSampleCount; LocalDateTime computedAt; String message`。`status ∈ {NORMAL, WARN, SEVERE, UNAVAILABLE}` | 无 |
| `AiRiskSupervisedModel` (改) | `module/ai/service/support/` | 加 `volatile DistributionSnapshot trainingScoreDistribution` + `@Getter`；新方法 `recordTrainingScoreDistribution(DistributionSnapshot)` 无锁 volatile 写；`save()` 追加一次旁路写入 `${modelPath}/supervised_distribution.json`（独立 JSON 文件，避开既有 `.meta` Properties 格式）；`load()` 已有路径中追加 `loadDistribution()`，失败仅 log.warn。**内部 `private static final ObjectMapper JSON = new ObjectMapper()`，不改构造器签名**（保持现有 `new AiRiskSupervisedModel()` 测试可用） | 内部静态 ObjectMapper |
| `AiRiskModelTrainingService` (微改) | `module/ai/service/support/` | `doRetrain()` 中已有 `double[] prob = new double[y.length]` 数组；TRAINED 路径在 `supervisedModel.replace(...)` 之后追加 `supervisedModel.recordTrainingScoreDistribution(DistributionSnapshot.builder()...counts(PsiCalculator.histogram(prob, BINS, 0, 1)).total(prob.length).capturedAt(now).build())` | PsiCalculator |
| `TransactionAnomalyDetector` (微改) | `module/monitoring/service/` | 加 `volatile DistributionSnapshot trainingScoreDistribution` + getter；`doTrain()` fit 成功后对 `features[]` 调 `model.score(features[i])` 得到训练分数数组、调 `PsiCalculator.histogram(scores, 10, 0, 1)`、写 volatile + 落盘到 `${modelPath}/anomaly_distribution.json`（独立文件，避开既有 `.stats` Properties 格式）；`@PostConstruct init()` 已有 loadModel；新增私有 `loadDistribution()` 从 `anomaly_distribution.json` 读回 volatile，失败仅 log.warn | PsiCalculator, ObjectMapper |
| `ModelDriftMonitorService` (新, @Component) | `module/ai/service/support/` | 核心；`@Value` 配置（cron、window-hours、bins、warn/severe 阈值、anomaly-sample-cap、min-samples）；公开 `computeSupervisedDrift()` / `computeAnomalyDrift()` / `computeAll()`；`@Scheduled` 调 `computeAll()` 并按 status 落 log；UNAVAILABLE 路径覆盖：基线 null、当前样本 < min-samples、PSI 计算 NaN/∞ | AiRiskSupervisedModel, TransactionAnomalyDetector, JdbcTemplate, TransactionMapper, PsiCalculator, ObjectMapper |
| `AiRiskScoringService` (微改) | `module/ai/service/` | 接口加 `List<ModelDriftStatusVO> listModelDrift()` | — |
| `AiRiskScoringServiceImpl` (微改) | `module/ai/service/impl/` | 注入 `ModelDriftMonitorService`；实现 `listModelDrift()` 单行委派 | ModelDriftMonitorService |
| `AiRiskScoringController` (微改) | `module/ai/controller/` | 新增 `@GetMapping("/models/drift")` + `@PreAuthorize("hasRole('ADMIN') or hasAuthority('model:view') or hasAuthority('monitoring:view')")` | — |

---

## 5. 配置

`application.yml`（与既有 `aml.ml.ai-risk`、`aml.ml.anomaly` 同 nested 风格添加 `aml.ml.drift`）：
```yaml
aml:
  ml:
    drift:
      cron: "0 0 4 * * ?"      # 每日 04:00（错峰已有重训）
      window-hours: 24
      bins: 10
      warn-threshold: 0.1
      severe-threshold: 0.25
      anomaly-sample-cap: 10000
      min-samples: 50          # 当前窗口最低样本数；低于此返回 UNAVAILABLE
```

`application-prod.yml`：env-var 覆盖与既有风格一致（`${AML_ML_DRIFT_CRON:0 0 4 * * ?}` 等）。

`application-test.yml`：覆盖 `aml.ml.drift.min-samples: 10`（低于生产默认 50），让集成测试在小数据集下能走入实际计算路径而非永远 UNAVAILABLE；其余默认值沿用 `application.yml`。@Scheduled 在测试环境只在 cron 命中时才触发，集成测试通过 REST 端点直接验证 `computeAll()` 输出。

---

## 6. 边界处理

| 场景 | 行为 |
|------|------|
| 模型从未训练 / 基线 null | `status=UNAVAILABLE, psi=null, message="基线缺失，需先完成训练"`；不阻断其它模型 |
| 当前窗口样本 < `min-samples`（默认 50） | `status=UNAVAILABLE, psi=null, message="近24h样本不足: N < min"`；不计算（小样本 PSI 抖动大） |
| `PsiCalculator.psi(...)` 结果 NaN/∞ | `status=UNAVAILABLE, message="PSI 计算异常: <reason>"`；`log.warn` |
| PSI < warn-threshold | `status=NORMAL` |
| warn-threshold ≤ PSI < severe-threshold | `status=WARN`；`@Scheduled` 路径 `log.warn("[AI-Drift] {} PSI={}, 已警示", modelKey, psi)` |
| PSI ≥ severe-threshold | `status=SEVERE`；`@Scheduled` 路径 `log.error("[AI-Drift] {} PSI={}, 严重漂移", modelKey, psi)` |
| `@Scheduled scheduledDriftCheck()` 抛出 | 方法体 try/catch，`log.error`，不让 scheduler 线程死 |
| IF 重算批次中 detector.predict 抛异常 | 单条 try/catch 跳过该样本，message 含跳过数；若全部失败 → `UNAVAILABLE` |
| 重训进行中查询 PSI | 读 `getTrainingScoreDistribution()`（volatile）安全；IF 重算共享 `detector.predict()` 的现有 readLock，不阻塞 |

**热路径隔离**：PSI 计算只在 `@Scheduled`（每日 04:00）和 `GET /ai/risk/models/drift`（运维查询）路径执行。两者都不在评分主链路。`AiRiskFactorEvaluator` 已有的 `detector.predict(transaction)` 调用不受影响。

**分布持久化采用旁路 JSON 文件，不改 `.meta` Properties 格式**：现有 `AiRiskSupervisedModel.save()` 用 Java `Properties` 写 `.meta`。为避免引入 JSON 字段到 Properties 值的奇怪混搭，分布快照单独写入 `${modelPath}/supervised_distribution.json`，与 IF 的 `anomaly_distribution.json` 对称。`AiRiskSupervisedModel` 内部用 `private static final ObjectMapper JSON = new ObjectMapper()` 处理序列化（不引入构造器依赖，避免破坏既有 `new AiRiskSupervisedModel()` 测试模式）。`load()` 流程中追加 `loadDistribution()`，文件不存在或解析失败仅 `log.warn`，distribution 保持 null（基线缺失语义生效），原 `.meta` 加载路径完全不变。

---

## 7. 测试

| 类型 | 用例 |
|------|------|
| `PsiCalculatorTest`（单测） | (a) 完全相同分布 PSI≈0；(b) 完全不同 PSI 大；(c) 单边零计数被 ε 平滑、不 NaN；(d) histogram 边界——lo/hi 之外的值落入首/末 bin；(e) bins=1 退化（PSI=0） |
| `DistributionSnapshotJsonTest`（单测） | Jackson 序列化→反序列化往返一致（保 `.meta` 落盘可读） |
| `ModelDriftMonitorServiceTest`（单测，Mockito） | (a) supervised 基线 null → UNAVAILABLE；(b) supervised 窗口样本不足 → UNAVAILABLE；(c) supervised 相同分布 → NORMAL；(d) supervised 显著偏移 → SEVERE；(e) anomaly 基线 null → UNAVAILABLE；(f) anomaly 正常路径 → NORMAL；(g) `computeAll()` 固定顺序 [supervised, anomaly]；(h) `@Scheduled` 包装吞异常不外抛 |
| `AiRiskSupervisedModelTest`（扩展） | `recordTrainingScoreDistribution` + save/load 往返（用 `@TempDir` 复用既有测试模式） |
| `TransactionAnomalyDetectorRetrainTest`（扩展） | retrain TRAINED 后 `getTrainingScoreDistribution()` 非空，counts 总和 == 训练样本数 |
| `AiRiskScoringIntegrationTest`（扩展，@Order(4)） | `GET /ai/risk/models/drift` 返回 2 条 [supervised, anomaly]；每条 status ∈ {NORMAL, WARN, SEVERE, UNAVAILABLE}；不强约束具体值（H2 小数据集 UNAVAILABLE 是最合法的结果） |
| 全量回归 | `mvn test` 总数 ≥ 215 + 本轮新增，零失败/零错误；`predict()` 与 `applyShadowModelScore` 行为与基线一致 |

---

## 8. 验收标准

1. `GET /ai/risk/models/drift` 返回 supervised + anomaly 固定顺序两条 `ModelDriftStatusVO`。
2. 模型未训练 / 基线缺失时对应条目 `status=UNAVAILABLE` 且 message 说明原因；不影响另一个模型的输出。
3. 训练成功后再次查询，基线就绪；若当前 24h 样本 ≥ min-samples 则计算出 PSI 数值并按阈值落 NORMAL/WARN/SEVERE。
4. `@Scheduled` 每日 04:00 执行；超阈值时日志按 warn/error 级别可见，含 modelKey 与 PSI 数值。
5. 应用重启后 `@PostConstruct` 自动从 `.meta` / `anomaly_distribution.json` 加载分布快照，无需重训即可计算 PSI。
6. 现有 215 个测试 + 本轮新增全部通过；`predict()` 和 `applyShadowModelScore` 输出与基线一致（无回归）。
7. 既有 `POST /ai/risk/models/training/*`、`GET /ai/risk/models/training`、`GET /ai/risk/model-status`、`GET /ai/risk/model/training-status` 端点行为完全不变。
