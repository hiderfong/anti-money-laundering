-- ============================================================================
-- 项目：保险公司反洗钱管理系统（AML System）
-- 文件：V010__aml_model_management.sql
-- 描述：反洗钱模型全生命周期管理
-- ============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. 反洗钱模型主表
-- ---------------------------------------------------------------------------
CREATE TABLE `t_aml_model` (
  `id`                  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `model_code`          VARCHAR(64)   NOT NULL COMMENT '模型编码',
  `model_name`          VARCHAR(256)  NOT NULL COMMENT '模型名称',
  `model_type`          VARCHAR(32)   NOT NULL COMMENT '模型类型：RULE/STATISTICAL/GRAPH/ML/HYBRID/NAME_MATCHING',
  `scenario`            VARCHAR(64)   NOT NULL COMMENT '业务场景：CUSTOMER_RISK/TRANSACTION_MONITORING/SCREENING/NETWORK_ANALYSIS/REPORTING',
  `algorithm_type`      VARCHAR(64)   DEFAULT NULL COMMENT '算法类型或预留实现方式',
  `version`             VARCHAR(32)   NOT NULL DEFAULT '1.0.0' COMMENT '当前版本',
  `lifecycle_status`    VARCHAR(32)   NOT NULL DEFAULT 'DRAFT' COMMENT '生命周期状态：DRAFT/TESTING/TEST_PASSED/DEPLOYED/MONITORING/ITERATING/ARCHIVED',
  `owner`               VARCHAR(64)   DEFAULT NULL COMMENT '模型责任人',
  `governance_level`    VARCHAR(16)   NOT NULL DEFAULT 'L2' COMMENT '治理等级：L1/L2/L3',
  `risk_level`          VARCHAR(16)   NOT NULL DEFAULT 'MEDIUM' COMMENT '模型风险等级：LOW/MEDIUM/HIGH',
  `training_dataset`    VARCHAR(256)  DEFAULT NULL COMMENT '训练数据集或样本说明',
  `validation_dataset`  VARCHAR(256)  DEFAULT NULL COMMENT '验证数据集或样本说明',
  `test_result`         VARCHAR(32)   DEFAULT NULL COMMENT '最近测试结果：PASS/FAIL/PENDING',
  `last_test_time`      DATETIME      DEFAULT NULL COMMENT '最近测试时间',
  `deployment_env`      VARCHAR(32)   DEFAULT NULL COMMENT '部署环境：DEV/UAT/PROD',
  `deployed_time`       DATETIME      DEFAULT NULL COMMENT '最近部署时间',
  `monitor_status`      VARCHAR(32)   DEFAULT 'NOT_STARTED' COMMENT '监控状态：NOT_STARTED/NORMAL/ATTENTION/DRIFTED',
  `precision_rate`      DECIMAL(8,4)  DEFAULT NULL COMMENT '准确率',
  `recall_rate`         DECIMAL(8,4)  DEFAULT NULL COMMENT '召回率',
  `false_positive_rate` DECIMAL(8,4)  DEFAULT NULL COMMENT '误报率',
  `drift_score`         DECIMAL(8,4)  DEFAULT NULL COMMENT '漂移分数',
  `last_monitor_time`   DATETIME      DEFAULT NULL COMMENT '最近监控时间',
  `iteration_plan`      TEXT          DEFAULT NULL COMMENT '迭代计划',
  `archive_reason`      TEXT          DEFAULT NULL COMMENT '归档原因',
  `archived_time`       DATETIME      DEFAULT NULL COMMENT '归档时间',
  `description`         TEXT          DEFAULT NULL COMMENT '模型说明',
  `config_json`         TEXT          DEFAULT NULL COMMENT '模型配置JSON（预留真实模型参数）',
  `created_by`          VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
  `created_time`        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`          VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
  `updated_time`        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_aml_model_code` (`model_code`),
  KEY `idx_aml_model_type` (`model_type`),
  KEY `idx_aml_model_scenario` (`scenario`),
  KEY `idx_aml_model_lifecycle_status` (`lifecycle_status`),
  KEY `idx_aml_model_updated_time` (`updated_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='反洗钱模型主表';

-- ---------------------------------------------------------------------------
-- 2. 模型生命周期记录表
-- ---------------------------------------------------------------------------
CREATE TABLE `t_aml_model_lifecycle_log` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `model_id`        BIGINT        NOT NULL COMMENT '模型ID',
  `model_code`      VARCHAR(64)   NOT NULL COMMENT '模型编码',
  `action_type`     VARCHAR(32)   NOT NULL COMMENT '动作类型：CREATE/UPDATE/TEST/DEPLOY/MONITOR/ITERATE/ARCHIVE',
  `from_status`     VARCHAR(32)   DEFAULT NULL COMMENT '动作前状态',
  `to_status`       VARCHAR(32)   NOT NULL COMMENT '动作后状态',
  `operator`        VARCHAR(64)   DEFAULT NULL COMMENT '操作人',
  `action_time`     DATETIME      NOT NULL COMMENT '操作时间',
  `action_summary`  TEXT          DEFAULT NULL COMMENT '动作摘要',
  `result_metric`   TEXT          DEFAULT NULL COMMENT '结果指标JSON或摘要',
  `artifact_ref`    VARCHAR(512)  DEFAULT NULL COMMENT '模型包、测试报告或监控报告引用',
  `created_by`      VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
  `created_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`      VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
  `updated_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_aml_model_log_model_id` (`model_id`),
  KEY `idx_aml_model_log_action_type` (`action_type`),
  KEY `idx_aml_model_log_action_time` (`action_time`),
  CONSTRAINT `fk_aml_model_log_model` FOREIGN KEY (`model_id`) REFERENCES `t_aml_model` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='反洗钱模型生命周期记录表';

-- ---------------------------------------------------------------------------
-- 3. 权限与角色授权
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `t_permission` (`permission_code`, `permission_name`, `parent_id`, `type`, `path`, `sort_order`, `icon`, `status`, `created_time`)
VALUES
  ('MENU_MODEL', '模型管理', 0, 'MENU', '/models', 13, 'data-analysis', 'ENABLED', NOW()),
  ('model:view', '模型管理-查看', 0, 'BUTTON', NULL, 1301, NULL, 'ENABLED', NOW()),
  ('model:manage', '模型管理-管理', 0, 'BUTTON', NULL, 1302, NULL, 'ENABLED', NOW());

INSERT IGNORE INTO `t_role_permission` (`role_id`, `permission_id`, `created_by`, `created_time`)
SELECT r.id, p.id, 'system', NOW()
FROM t_role r, t_permission p
WHERE r.role_code = 'ROLE_ADMIN'
  AND p.permission_code IN ('MENU_MODEL', 'model:view', 'model:manage');

-- ---------------------------------------------------------------------------
-- 4. 预置反洗钱模型样例
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `t_aml_model`
  (`model_code`, `model_name`, `model_type`, `scenario`, `algorithm_type`, `version`, `lifecycle_status`,
   `owner`, `governance_level`, `risk_level`, `training_dataset`, `validation_dataset`, `test_result`,
   `last_test_time`, `deployment_env`, `deployed_time`, `monitor_status`, `precision_rate`, `recall_rate`,
   `false_positive_rate`, `drift_score`, `last_monitor_time`, `iteration_plan`, `description`, `config_json`,
   `created_by`, `created_time`, `updated_by`, `updated_time`)
VALUES
  ('AMLM-CTR-001', '大额现金交易识别模型', 'RULE', 'TRANSACTION_MONITORING', 'THRESHOLD_RULE', '1.2.0', 'MONITORING',
   '模型治理管理员', 'L2', 'MEDIUM', '近12个月大额交易样本', '月度交易抽样验证集', 'PASS',
   DATE_SUB(NOW(), INTERVAL 9 DAY), 'PROD', DATE_SUB(NOW(), INTERVAL 6 DAY), 'NORMAL', 0.9210, 0.8840,
   0.0820, 0.0410, DATE_SUB(NOW(), INTERVAL 1 DAY), '按季度复核大额现金阈值和地区参数',
   '识别达到监管报告阈值或接近阈值拆分交易的保险收付场景模型。', '{"threshold":"50000","currency":"CNY","window":"daily"}',
   'system', NOW(), 'system', NOW()),
  ('AMLM-STR-002', '可疑交易异常模式评分模型', 'HYBRID', 'TRANSACTION_MONITORING', 'RULE_SCORECARD', '1.0.0', 'TEST_PASSED',
   '合规模型团队', 'L1', 'HIGH', '可疑交易案例与人工排除样本', '最近三个月预警处置样本', 'PASS',
   DATE_SUB(NOW(), INTERVAL 3 DAY), 'UAT', NULL, 'NOT_STARTED', 0.8730, 0.8120,
   0.1760, 0.0660, NULL, '上线后重点跟踪误报率，补充人工反馈特征',
   '对频繁投保退保、短期大额缴费、异常受益人变更等可疑交易模式进行综合评分。', '{"scoreCutoff":"75","features":["velocity","refund","beneficiary_change"]}',
   'system', NOW(), 'system', NOW()),
  ('AMLM-NET-003', '关联账户网络识别模型', 'GRAPH', 'NETWORK_ANALYSIS', 'GRAPH_PATTERN', '0.9.0', 'ITERATING',
   '图分析小组', 'L1', 'HIGH', '客户-账户-交易关系图谱', '典型团伙交易网络样本', 'PASS',
   DATE_SUB(NOW(), INTERVAL 14 DAY), 'UAT', DATE_SUB(NOW(), INTERVAL 10 DAY), 'ATTENTION', 0.8010, 0.7680,
   0.2310, 0.1840, DATE_SUB(NOW(), INTERVAL 2 DAY), '补充共同账户、环形转账和多层转移特征权重',
   '识别共同账户、环形交易、多层资金转移等团伙化洗钱网络。', '{"maxDepth":"4","minSharedAccounts":"2","densityThreshold":"10"}',
   'system', NOW(), 'system', NOW()),
  ('AMLM-SCR-004', '制裁名单相似度匹配模型', 'NAME_MATCHING', 'SCREENING', 'PINYIN_FUZZY_MATCH', '1.1.0', 'DEPLOYED',
   '名单筛查团队', 'L2', 'HIGH', '制裁名单别名与历史筛查命中样本', '名单命中复核样本', 'PASS',
   DATE_SUB(NOW(), INTERVAL 7 DAY), 'PROD', DATE_SUB(NOW(), INTERVAL 5 DAY), 'NORMAL', 0.9480, 0.9030,
   0.0610, 0.0250, DATE_SUB(NOW(), INTERVAL 1 DAY), '增加跨语种别名归一化能力',
   '支持中文、拼音、英文别名和证件号辅助匹配的名单筛查相似度模型。', '{"threshold":"85","aliasWeight":"0.35","identityWeight":"0.45"}',
   'system', NOW(), 'system', NOW()),
  ('AMLM-PEP-005', 'PEP风险增强识别模型', 'STATISTICAL', 'CUSTOMER_RISK', 'RISK_SCORECARD', '0.8.0', 'DRAFT',
   '客户风险团队', 'L2', 'MEDIUM', '客户身份与关系人样本', 'PEP人工复核样本', 'PENDING',
   NULL, NULL, NULL, 'NOT_STARTED', NULL, NULL,
   NULL, NULL, NULL, '补齐PEP关系人、受益所有人和地域风险变量',
   '对政治公众人物、近亲属及关联人客户进行增强风险识别和评分。', '{"pepWeight":"0.4","relationshipWeight":"0.25","geoWeight":"0.2"}',
   'system', NOW(), 'system', NOW());

INSERT IGNORE INTO `t_aml_model_lifecycle_log`
  (`model_id`, `model_code`, `action_type`, `from_status`, `to_status`, `operator`, `action_time`, `action_summary`, `result_metric`, `artifact_ref`, `created_by`, `created_time`)
SELECT id, model_code, 'CREATE', NULL, lifecycle_status, 'system', created_time, '系统预置反洗钱模型样例，用于贯通模型治理业务流程',
       JSON_OBJECT('version', version, 'scenario', scenario), NULL, 'system', NOW()
FROM t_aml_model
WHERE model_code IN ('AMLM-CTR-001', 'AMLM-STR-002', 'AMLM-NET-003', 'AMLM-SCR-004', 'AMLM-PEP-005');
