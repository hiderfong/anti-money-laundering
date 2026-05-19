-- ============================================================================
-- 项目：保险公司反洗钱管理系统（AML System）
-- 文件：V014__ai_risk_score_records.sql
-- 描述：AI风险评分记录与模型版本绑定
-- ============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. AI风险评分记录表
-- ---------------------------------------------------------------------------
CREATE TABLE `t_ai_risk_score_record` (
  `id`                    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `score_no`              VARCHAR(64)   NOT NULL COMMENT '评分流水号',
  `subject_type`          VARCHAR(32)   NOT NULL COMMENT '评分主体类型：CUSTOMER/TRANSACTION/ALERT',
  `subject_id`            BIGINT        NOT NULL COMMENT '评分主体ID',
  `subject_name`          VARCHAR(256)  DEFAULT NULL COMMENT '评分主体名称',
  `customer_id`           BIGINT        DEFAULT NULL COMMENT '客户ID',
  `transaction_id`        BIGINT        DEFAULT NULL COMMENT '交易ID',
  `alert_id`              BIGINT        DEFAULT NULL COMMENT '预警ID',
  `model_id`              BIGINT        DEFAULT NULL COMMENT '绑定模型ID',
  `model_code`            VARCHAR(64)   NOT NULL COMMENT '模型编码',
  `model_name`            VARCHAR(256)  DEFAULT NULL COMMENT '模型名称',
  `model_version`         VARCHAR(32)   NOT NULL COMMENT '模型版本',
  `score`                 INT           NOT NULL COMMENT 'AI风险分',
  `risk_level`            VARCHAR(16)   NOT NULL COMMENT 'AI风险等级',
  `confidence`            INT           NOT NULL DEFAULT 0 COMMENT '置信度',
  `factor_summary`        VARCHAR(1024) DEFAULT NULL COMMENT '主要贡献因子摘要',
  `feature_snapshot_json` TEXT          DEFAULT NULL COMMENT '特征快照JSON',
  `factor_snapshot_json`  TEXT          DEFAULT NULL COMMENT '贡献因子快照JSON',
  `evidence_snapshot_json` TEXT         DEFAULT NULL COMMENT '证据摘要JSON',
  `recommendation_json`   TEXT          DEFAULT NULL COMMENT '建议动作JSON',
  `scored_at`             DATETIME      NOT NULL COMMENT '评分时间',
  `created_by`            VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
  `created_time`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`            VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
  `updated_time`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_risk_score_no` (`score_no`),
  KEY `idx_ai_risk_subject` (`subject_type`, `subject_id`, `scored_at`),
  KEY `idx_ai_risk_customer` (`customer_id`, `scored_at`),
  KEY `idx_ai_risk_transaction` (`transaction_id`, `scored_at`),
  KEY `idx_ai_risk_alert` (`alert_id`, `scored_at`),
  KEY `idx_ai_risk_model` (`model_code`, `model_version`, `scored_at`),
  CONSTRAINT `fk_ai_risk_score_model` FOREIGN KEY (`model_id`) REFERENCES `t_aml_model` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI风险评分记录表';

-- ---------------------------------------------------------------------------
-- 2. 预置AI可解释风险评分基线模型
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `t_aml_model`
  (`model_code`, `model_name`, `model_type`, `scenario`, `algorithm_type`, `version`, `lifecycle_status`,
   `owner`, `governance_level`, `risk_level`, `training_dataset`, `validation_dataset`, `test_result`,
   `last_test_time`, `deployment_env`, `deployed_time`, `monitor_status`, `precision_rate`, `recall_rate`,
   `false_positive_rate`, `drift_score`, `last_monitor_time`, `iteration_plan`, `description`, `config_json`,
   `created_by`, `created_time`, `updated_by`, `updated_time`)
VALUES
  ('AI_AML_RISK_BASELINE_V1', 'AI可解释风险评分基线模型', 'HYBRID', 'CUSTOMER_RISK', 'EXPLAINABLE_SCORECARD', '1.0.0', 'MONITORING',
   '合规智能分析团队', 'L1', 'HIGH', '客户、交易、预警、案件、STR、名单和关系特征实时抽取', 'E2E业务闭环样本与人工复核样本', 'PASS',
   NOW(), 'PROD', NOW(), 'NORMAL', 0.8600, 0.8100,
   0.1800, 0.0500, NOW(), '接入人工复核反馈标签后，逐步升级为监督学习与图特征融合模型',
   '面向客户、交易和预警对象的可解释AI风险评分基线，记录每次评分的模型版本、特征快照、贡献因子和建议动作。',
   '{"subjects":["CUSTOMER","TRANSACTION","ALERT"],"windowDays":90,"explainable":true,"externalModelRequired":false}',
   'system', NOW(), 'system', NOW());

INSERT IGNORE INTO `t_aml_model_lifecycle_log`
  (`model_id`, `model_code`, `action_type`, `from_status`, `to_status`, `operator`, `action_time`, `action_summary`, `result_metric`, `artifact_ref`, `created_by`, `created_time`)
SELECT id, model_code, 'DEPLOY', NULL, lifecycle_status, 'system', NOW(),
       '系统预置AI可解释风险评分基线模型，并启用评分记录与模型版本绑定',
       JSON_OBJECT('version', version, 'subjects', JSON_ARRAY('CUSTOMER', 'TRANSACTION', 'ALERT')),
       NULL, 'system', NOW()
FROM t_aml_model
WHERE model_code = 'AI_AML_RISK_BASELINE_V1';
