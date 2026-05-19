# P0 复核标注回流的监督学习闭环 — 设计规格

> 日期：2026-05-19
> 代码基线：`6a2d675` (main)
> 关联：`docs/development/AI反洗钱增强建议-20260519.md`（P0 项）
> 状态：设计已确认，待 spec review → writing-plans

---

## 1. 背景与目标

`t_ai_risk_score_record` 已持续沉淀人工复核标注（`manual_review_label` ∈ {TRUE_POSITIVE, FALSE_POSITIVE, NEEDS_MONITORING}）与完整 `feature_snapshot_json`，构成一份带标签训练集，但当前无任何环节使用它（`module/ai` 内无 retrain/feedback 接线）。

**目标**：把复核标注接回训练闭环，引入 JVM 内监督模型，与现有规则评分**并行展示、影子运行**，规则分保持主链路与可解释回退。本轮交付闭环 MVP。

**非目标（后续轮次）**：冠军/挑战者自动切换；漂移监控 PSI（P1）；SHAP 可解释（P4）；图特征（P2）。

---

## 2. 关键决策（已确认）

| 决策 | 选择 |
|------|------|
| ML 实现 | Smile JVM 内（复用已有 `smile-core` 依赖），逻辑回归基线 + 概率输出 |
| 评分介入 | 并行展示 + 影子运行：规则分仍为主链路，模型概率影子写入记录 |
| 训练触发 | `@Scheduled`（cron 可配）+ 手动 REST 端点 |
| 交付范围 | 闭环 MVP（见下 IN/OUT） |

**IN**：训练服务（拉已复核样本+特征快照训练 Smile 模型）、模型持久化/加载、定时&手动触发、评分时影子写概率分、训练指标接口、单测+集成测试。
**OUT**：冠军/挑战者自动切换、漂移监控 PSI、SHAP、图特征。

---

## 3. 架构与数据流

```
已复核记录 (manual_review_label≠null + feature_snapshot_json)
  │ ① 拉取标注样本
  ▼
AiRiskModelTrainingService
  ├─ feature_snapshot_json → 固定特征向量 (AiRiskFeatureVectorizer，与推理同源)
  ├─ 标签映射: TRUE_POSITIVE→1, FALSE_POSITIVE→0, NEEDS_MONITORING→排除
  ├─ Smile LogisticRegression.fit  ② 训练
  ├─ 训练集内评估: accuracy / AUC / 正负样本量
  └─ ObjectOutputStream 存盘 (./data/models/ai_risk_supervised.model + .meta) ③
  │
  ▼ ④ @PostConstruct 加载 / 训练后热替换 (ReentrantReadWriteLock + volatile ready)
AiRiskSupervisedModel (LogisticRegression + 特征 schema + 训练元数据)
  │ ⑤ 评分时影子调用 (try/catch 隔离，不改规则分主链路)
  ▼
AiRiskScoringServiceImpl.persistScoreRecord → 额外写 model_probability / model_label_predicted
```

触发：`@Scheduled`（`aml.ml.ai-risk.retrain-cron`）+ `POST /ai/risk/model/retrain`。

---

## 4. 组件清单

遵循已拆出的 `com.insurance.aml.module.ai.service.support` 结构。

| 组件 | 位置 | 职责 | 依赖 |
|------|------|------|------|
| `AiRiskSupervisedModel` | `service/support/` | 持有 Smile `LogisticRegression` + 特征 schema + 元数据；线程安全 `predictProbability(double[])`；存盘/加载（镜像 `TransactionAnomalyDetector`：`ReentrantReadWriteLock` + `volatile boolean ready`） | 无 |
| `AiRiskFeatureVectorizer` | `service/support/` | 唯一向量化出口：`AiRiskFeatureSummaryVO → double[]`，特征顺序由内部固定常量定义；训练与推理共用，杜绝 training-serving skew | 无 |
| `AiRiskModelTrainingService` | `service/support/` | 拉标注样本 → 向量化 → `fit` → 训练集评估 → 存盘 → 热替换；返回训练指标 DTO | `AiRiskScoreRecordMapper`, `AiRiskFeatureVectorizer`, `AiRiskSupervisedModel`, `ObjectMapper` |
| `AiRiskScoringController`（扩展） | `controller/` | 新增 `POST /ai/risk/model/retrain`（触发训练返回指标）、`GET /ai/risk/model/training-status`（上次训练元数据） | `AiRiskModelTrainingService` |
| `AiRiskScoringServiceImpl`（改） | `service/impl/` | `persistScoreRecord` 影子调用模型，写 `model_probability`/`model_label_predicted`；推理 try/catch，失败仅 `log.warn` | 新增注入 `AiRiskSupervisedModel`, `AiRiskFeatureVectorizer` |

---

## 5. 数据库变更

新增 `src/main/resources/db/migration/V016__ai_risk_supervised_shadow.sql`，沿用 V015 的幂等 `information_schema` 存在性检查写法：

- `t_ai_risk_score_record` 增列：
  - `model_probability DECIMAL(5,4) DEFAULT NULL COMMENT '监督模型可疑概率(影子)'`
  - `model_label_predicted VARCHAR(16) DEFAULT NULL COMMENT '监督模型预测标签(影子)'`

实体 `AiRiskScoreRecord` 增对应字段。`AiRiskModelStatusVO` 扩展监督模型块：样本量、accuracy、AUC、上次训练时间、是否就绪、模型文件版本。

---

## 6. 配置（复用 `aml.ml.*` 命名空间，`application.yml`）

```yaml
aml:
  ml:
    ai-risk:
      model-path: ./data/models          # 与现有 IsolationForest 共用目录
      retrain-cron: "0 0 3 * * SUN"      # 每周日 03:00
      min-samples: 50                     # 低于此跳过训练并告警
```

---

## 7. 边界处理

| 场景 | 行为 |
|------|------|
| 标注样本 < `min-samples` 或单一类别（全正/全负） | 跳过训练，`log.warn`，模型保持 `ready=false` |
| 模型未就绪 | 评分仅写规则分，`model_probability` 留 null；规则分为永久可解释回退 |
| 影子推理异常 | try/catch 包裹，`log.warn` 不抛出，规则评分与落库正常完成 |
| 标签 `NEEDS_MONITORING` | 排除出训练集（语义中性，不参与二分类） |
| 训练-服务一致性 | 所有向量化经 `AiRiskFeatureVectorizer` 单一出口，特征顺序内部固定 |

**核心不变量**：规则评分主链路与落库行为零变更；模型缺失/失败永不导致评分降级。

---

## 8. 测试策略

| 类型 | 覆盖 |
|------|------|
| `AiRiskFeatureVectorizerTest`（单元） | VO→向量映射稳定性、null/默认值、特征维度恒定 |
| `AiRiskModelTrainingServiceTest`（单元） | 样本不足跳过；单一类别跳过；正常样本训练产出指标；NEEDS_MONITORING 被排除（mock mapper） |
| `AiRiskSupervisedModelTest`（单元） | 存盘→加载往返；未就绪时 predict 行为；并发读写锁 |
| 集成（扩展 `AiRiskScoringIntegrationTest` 或新增） | 复核记录 → `POST /retrain` → 断言指标 → 再评分 → 断言记录含 `model_probability` |
| 回归 | 现有 11 项 AI 测试 + 全链路集成测试保持全绿（规则分零变更） |

**Smile API**：`smile.classification.LogisticRegression.fit(double[][], int[])` + `predict(x, double[] posteriori)` 取概率；具体签名按仓库 Smile 版本在实现时核验（实现细节，不影响设计）。

---

## 9. 验收标准

1. 复核 ≥ `min-samples` 条记录后，`POST /ai/risk/model/retrain` 返回含 accuracy/AUC/样本量的指标，模型文件落盘。
2. 训练后对客户/交易/预警评分，记录写入非空 `model_probability` 与 `model_label_predicted`。
3. 模型未训练时评分正常，规则分不变，`model_probability` 为 null。
4. 影子推理抛异常时评分与落库不受影响（日志可见 warn）。
5. 现有全部 AI 单测与全链路集成测试保持全绿。
6. 重启后 `@PostConstruct` 自动加载已存盘模型，`training-status` 反映元数据。
