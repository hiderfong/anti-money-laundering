# AI P1 — 异常检测训练治理 + 与监督模型的运维统一

> 日期：2026-05-20
> 代码基线：`e0b3112` (main，P0 闭环已合入)
> 关联：`docs/development/AI反洗钱增强建议-20260519.md`（P1 项首条）；P0 spec `docs/superpowers/specs/2026-05-19-ai-risk-supervised-feedback-loop-design.md`
> 状态：设计已确认，待 spec review → writing-plans

---

## 1. 背景与目标

P0 落地了监督模型的训练闭环与治理（cron + 手动触发 + 串行化 + 状态码 + 落盘）。同期项目内的 `TransactionAnomalyDetector`（Smile Isolation Forest）仍只在 `@PostConstruct` 启动时训一次，无周期重训、无样本不足守卫、无失败状态可观测。这意味着无监督模型会持续陈旧，且与监督模型的训练治理风格不一致，运维需分两处看健康。

**目标**：对 `TransactionAnomalyDetector` 引入与监督模型同形的训练治理，并提供一个统一的运维端点裂变两个模型的训练状态与触发入口。监督模型既有端点保留向后兼容。

---

## 2. 关键决策（已确认）

| 决策 | 选择 |
|------|------|
| 本轮交付范围 | 仅 IF 周期重训 + 与监督模型训练治理统一；漂移 PSI、IF 分校准、异步训练均推迟独立轮次 |
| 统一粒度 | 约定一致（状态码/CAS/min-samples）+ 新增一个运维端点；**不**引入 `TrainableModel` 抽象接口（YAGNI，仅两个实现） |
| `AnomalyTrainingResultVO` 归属 | 放 `module/ai/model/dto/`（与 `AiRiskTrainingResultVO` 并列） |
| 旧端点处置 | 保留 `POST /ai/risk/model/retrain` 与 `GET /ai/risk/model/training-status`，新增统一端点并行 |

**IN（本轮）**
- `TransactionAnomalyDetector`：`@Scheduled` 周期重训 + `min-samples` 守卫 + `AtomicBoolean` 串行化 + `lastTrain*` 字段 + 返回 `AnomalyTrainingResultVO`
- `AiRiskModelTrainingService`：补 `FAILED` 状态码与外层异常捕获（保持现有 SKIPPED_* 与 TRAINED 不变）
- 新增 `ModelTrainingOpsService` 聚合两个模型；新增统一端点：
  - `GET /ai/risk/models/training` 列出两个模型训练状态
  - `POST /ai/risk/models/training/{modelKey}/retrain` 按 key 触发，key ∈ {`supervised`, `anomaly`}

**OUT（推迟独立轮次）**
- 漂移监控 PSI（特征分布 + 异常分分布偏移、阈值告警、自动触发重训）
- IF 异常分校准成概率并纳入监督模型特征（涉及 `FEATURE_DIM` 变更与已训模型失效）
- 异步训练（202 + jobId）

**不变量**
- `TransactionAnomalyDetector.predict(Transaction) → double` 推理行为完全不变；该方法被 `AiRiskFactorEvaluator` 作为评分因子使用。
- 监督模型 `applyShadowModelScore` 行为不变；规则评分主链路零改动。
- 既有 `GET /ai/risk/model-status` 输出结构不变。

---

## 3. 架构与数据流

```
@Scheduled (anomaly cron)            @Scheduled (supervised cron, 既有)
       │                                       │
       ▼                                       ▼
TransactionAnomalyDetector.retrain()   AiRiskModelTrainingService.retrain()
  AtomicBoolean CAS  → SKIPPED_IN_PROGRESS    AtomicBoolean CAS  → SKIPPED_IN_PROGRESS
  样本 < min-samples → SKIPPED_INSUFFICIENT   样本 < min-samples → SKIPPED_INSUFFICIENT
  fit/save 抛异常    → FAILED + lastError     单一类别           → SKIPPED_SINGLE_CLASS
  正常                → TRAINED + 更新 last*  fit/save 抛异常    → FAILED + lastError
                                              正常                → TRAINED + 更新 last*
       │                                       │
       └────────────┬──────────────────────────┘
                    ▼
       ModelTrainingOpsService
         listAll() → List<ModelTrainingStatusVO>（[supervised, anomaly]，顺序固定）
         retrain(modelKey) → 按 key 委派；未知 key → BAD_REQUEST
                    │
                    ▼
       AiRiskScoringController（新两个端点）
         GET  /ai/risk/models/training
         POST /ai/risk/models/training/{modelKey}/retrain
```

---

## 4. 组件清单

遵循已确立的 `module/ai/service/support/` 协作组件风格。

| 组件 | 位置 | 责任 | 依赖 |
|------|------|------|------|
| `AnomalyTrainingResultVO` (新) | `module/ai/model/dto/` | `@Data @Builder @NoArgsConstructor @AllArgsConstructor`；字段：status, modelReady, sampleCount, trainDurationMs(long), trainedAt(LocalDateTime), message。**无** accuracy/auc（IF 无监督，不提供） | 无 |
| `ModelTrainingStatusVO` (新) | `module/ai/model/dto/` | 运维通用展示：modelKey, modelName, modelVersion, status, modelReady, sampleCount, trainedAt, message | 无 |
| `TransactionAnomalyDetector` (改) | `module/monitoring/service/` | 增配置：`@Value("${aml.ml.anomaly.retrain-cron:0 30 3 * * SUN}")` cron、`@Value("${aml.ml.anomaly.min-samples:100}")` minSamples；`AtomicBoolean trainingInProgress`；新 public `AnomalyTrainingResultVO retrain()` 含 CAS + 守卫 + 委派 do-fit + 落字段；新 `@Scheduled scheduledRetrain()` try/catch；新 volatile `lastTrainStatus`/`lastTrainError`/`lastTrainSampleCount`/`trainDurationMs`（与现有 `model`/`modelReady` 共存）。说明：`@PostConstruct` 重启加载的模型其 `lastTrainSampleCount` 仅在本会话首次重训成功后才被赋真值（重启前的训练样本数不持久化，运维如需可在状态消息中体现"加载自磁盘，无本会话训练记录"）。`predict(...)` 与 `init()` 行为不变 | 不变 |
| `AiRiskModelTrainingService` (微改) | `module/ai/service/support/` | `retrain()` 外层增加 catch(Exception) → 返回 status=`FAILED`；每次 `retrain()` 完结路径（TRAINED/SKIPPED_*/FAILED）都调用 `supervisedModel.recordOutcome(status, errorMessageOrNull)`；`trainingStatus()` 同时回读 lastTrainStatus/lastTrainError；既有 SKIPPED_IN_PROGRESS / TRAINED / NOT_TRAINED 路径不变 | 不变 |
| `AiRiskSupervisedModel` (微改) | `module/ai/service/support/` | 加 volatile `lastTrainStatus`、`lastTrainError`；新方法 `recordOutcome(String status, String errorMessage)` 无锁 volatile 写（不涉及 `model` 替换，仅元数据观察字段）；getters 暴露。该方法与 `replace()` 解耦：TRAINED 时 training service 先 `replace()` 再 `recordOutcome`；SKIPPED/FAILED 时只 `recordOutcome` 不动模型 | 不变 |
| `ModelTrainingOpsService` (新) | `module/ai/service/support/` | 聚合查询 + 路由触发；内部 `MODEL_KEY_SUPERVISED="supervised"` / `MODEL_KEY_ANOMALY="anomaly"`；`listAll()` 返回 [supervised, anomaly] 固定顺序；`retrain(String modelKey)` 路由并转换返回值为 `ModelTrainingStatusVO` | `AiRiskModelTrainingService`, `TransactionAnomalyDetector` |
| `AiRiskScoringController` (改) | `module/ai/controller/` | 新增两个端点；注入 `ModelTrainingOpsService`（不直接持有 detector）；保留旧 `/model/retrain`、`/model/training-status`、`/model-status`。`@PreAuthorize` 与现有 `/model/retrain` 相同范围（`model:manage`）；GET listAll 用 `model:view` | 加注 `ModelTrainingOpsService` |
| `AiRiskScoringService` (微改) | `module/ai/service/` | 接口加 `List<ModelTrainingStatusVO> listTrainableModels()` 和 `ModelTrainingStatusVO retrainModelByKey(String modelKey)`；实现委派 `ModelTrainingOpsService` | — |

---

## 5. 配置

`application.yml`（与现有 `aml.ml.ai-risk` 兄弟节点）：
```yaml
aml:
  ml:
    anomaly:
      retrain-cron: "0 30 3 * * SUN"  # 03:30 错峰 supervised 的 03:00
      min-samples: 100
```

`application-prod.yml`（与既有 `aml.ml.ai-risk` 同风格添加 env-var 覆盖）：
```yaml
aml:
  ml:
    anomaly:
      retrain-cron: "${AML_ML_ANOMALY_RETRAIN_CRON:0 30 3 * * SUN}"
      min-samples: ${AML_ML_ANOMALY_MIN_SAMPLES:100}
```

测试 `application-test.yml`：加 `aml.ml.anomaly.min-samples: 4`（与 ai-risk 同低门槛）避免集成测试在小数据集上跳过。

---

## 6. 边界处理

| 场景 | 行为 |
|------|------|
| `TransactionAnomalyDetector.retrain()` 并发命中 | CAS 失败 → 返回 status=`SKIPPED_IN_PROGRESS`，modelReady=当前真值，message="训练正在进行中" |
| 训练窗口内样本数 < min-samples | status=`SKIPPED_INSUFFICIENT`，message 含实际值与阈值；不动现有模型 |
| 内部 fit/save 抛异常 | status=`FAILED`，message=异常类名 + msg；`log.error` 全栈；lastTrainError 落库；现有模型保留（不下线） |
| `@Scheduled` 抛出 | scheduledRetrain() try/catch；log.error；不让 scheduler 线程死 |
| `retrain("unknown")` | `BusinessException(ResultCode.BAD_REQUEST, "不支持的模型: " + modelKey)` |
| 模型从未训练过 | `isReady()=false`，status=`NOT_TRAINED`，sampleCount=0，trainedAt=null |
| H2 集成测试样本数极少 | `aml.ml.anomaly.min-samples=4` 测试覆盖；得 `TRAINED` 或 `SKIPPED_INSUFFICIENT`（任一均合法验收，因为本轮不要求集成测试断言具体训练结果，仅验证治理路径） |

**核心不变量复述**：`predict(Transaction)` 在重训期间持续可用（受现有 `ReentrantReadWriteLock` + volatile `modelReady` 保护，与 P0 `AiRiskSupervisedModel` 风格一致）；重训只会原子替换内存中的 model 引用。

---

## 7. 测试

| 类型 | 用例 |
|------|------|
| `TransactionAnomalyDetectorRetrainTest`（单测，Mockito mapper） | (a) 样本不足 → `SKIPPED_INSUFFICIENT`；(b) 并发 CAS → 第二次 `SKIPPED_IN_PROGRESS`；(c) fit 抛 RuntimeException → `FAILED` + lastTrainError 非空；(d) 正常 → `TRAINED` + sampleCount>0 + trainDurationMs>0；(e) `scheduledRetrain()` 在 retrain 抛异常时不外抛 |
| `ModelTrainingOpsServiceTest`（单测） | (a) `listAll()` 返回固定顺序 [supervised, anomaly] 且字段映射正确；(b) `retrain("supervised")` 委派；(c) `retrain("anomaly")` 委派；(d) `retrain("unknown")` → `BAD_REQUEST` |
| `AiRiskModelTrainingServiceTest`（已有，加一个） | 外层异常 → `FAILED` 路径（mock vectorizer 抛 RuntimeException） |
| `ModelTrainingOpsIntegrationTest`（扩展现有 `AiRiskScoringIntegrationTest` 加一个 `@Test`） | (a) `GET /ai/risk/models/training` 返回包含 supervised 与 anomaly 两条；(b) `POST /ai/risk/models/training/anomaly/retrain` 返回 200 且 status ∈ {TRAINED, SKIPPED_INSUFFICIENT}；(c) `POST /ai/risk/models/training/unknown/retrain` → 400 |
| 全量回归 | `mvn test` 整体绿（≥201 + 新增；不允许任何既有用例回归）；尤其要确认 `applyShadowModelScore` 与 `predict(Transaction)` 行为不变（既有 AI / 监控 / 集成测试通过即证） |

---

## 8. 验收标准

1. `@Scheduled` 在配置 cron 触发时调用 `TransactionAnomalyDetector.retrain()`，无论结果都记录日志；触发期间不阻塞 `predict(Transaction)` 调用方。
2. 手动 `POST /ai/risk/models/training/anomaly/retrain` 返回 `ModelTrainingStatusVO`，其 status ∈ {TRAINED, SKIPPED_INSUFFICIENT, SKIPPED_IN_PROGRESS, FAILED}。
3. `GET /ai/risk/models/training` 返回固定顺序的 [supervised, anomaly] 两个条目，字段齐全。
4. 并发触发两次 retrain（同一模型）：第二次得 `SKIPPED_IN_PROGRESS`，第一次正常完成。
5. fit/save 失败时模型不下线，`isReady()` 保持上次成功后的真值；status=FAILED 可被 listAll 看到。
6. 全部既有 AI/监控/集成测试保持绿；`predict(Transaction)` 与 `applyShadowModelScore` 行为零变更。
7. 既有 `POST /ai/risk/model/retrain` 与 `GET /ai/risk/model/training-status` 行为不变（向后兼容）。
