-- ============================================================================
-- 项目：保险公司反洗钱管理系统（AML System）
-- 文件：V007__special_prevention_and_rectification.sql
-- 描述：特别预防措施中心与整改中心独立化
-- ============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. 名单更新任务
-- ---------------------------------------------------------------------------
CREATE TABLE `t_watchlist_update_job` (
  `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `job_no`             VARCHAR(32)  NOT NULL COMMENT '任务编号',
  `source_id`          BIGINT       DEFAULT NULL COMMENT '名单源ID',
  `source_name`        VARCHAR(128) DEFAULT NULL COMMENT '名单源名称',
  `update_mode`        VARCHAR(16)  NOT NULL COMMENT '更新模式：MANUAL/SCHEDULED/API',
  `status`             VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/RUNNING/SUCCESS/FAILED',
  `total_entries`      INT          NOT NULL DEFAULT 0 COMMENT '更新后名单总量',
  `added_count`        INT          NOT NULL DEFAULT 0 COMMENT '新增数量',
  `updated_count`      INT          NOT NULL DEFAULT 0 COMMENT '更新数量',
  `expired_count`      INT          NOT NULL DEFAULT 0 COMMENT '失效数量',
  `started_time`       DATETIME     DEFAULT NULL COMMENT '开始时间',
  `completed_time`     DATETIME     DEFAULT NULL COMMENT '完成时间',
  `error_message`      VARCHAR(512) DEFAULT NULL COMMENT '错误信息',
  `created_by`         VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `created_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`         VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
  `updated_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_watchlist_update_job_no` (`job_no`),
  KEY `idx_wuj_source_id` (`source_id`),
  KEY `idx_wuj_status` (`status`),
  KEY `idx_wuj_created_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='名单更新任务表';

-- ---------------------------------------------------------------------------
-- 2. 回溯筛查任务
-- ---------------------------------------------------------------------------
CREATE TABLE `t_retrospective_screening_job` (
  `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `job_no`             VARCHAR(32)  NOT NULL COMMENT '任务编号',
  `job_name`           VARCHAR(128) NOT NULL COMMENT '任务名称',
  `scope_type`         VARCHAR(32)  NOT NULL COMMENT '筛查范围：ALL_CUSTOMERS/HIGH_RISK/ACTIVE_CUSTOMERS/CUSTOMER_IDS',
  `customer_ids`       TEXT         DEFAULT NULL COMMENT '指定客户ID列表，逗号分隔',
  `watchlist_source_id` BIGINT      DEFAULT NULL COMMENT '名单源ID',
  `status`             VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/RUNNING/COMPLETED/FAILED',
  `total_customers`    INT          NOT NULL DEFAULT 0 COMMENT '客户总数',
  `processed_customers` INT         NOT NULL DEFAULT 0 COMMENT '已处理客户数',
  `total_hits`         INT          NOT NULL DEFAULT 0 COMMENT '命中总数',
  `started_time`       DATETIME     DEFAULT NULL COMMENT '开始时间',
  `completed_time`     DATETIME     DEFAULT NULL COMMENT '完成时间',
  `remark`             VARCHAR(512) DEFAULT NULL COMMENT '备注',
  `created_by`         VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `created_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`         VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
  `updated_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_retrospective_job_no` (`job_no`),
  KEY `idx_rsj_scope_type` (`scope_type`),
  KEY `idx_rsj_status` (`status`),
  KEY `idx_rsj_created_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='回溯筛查任务表';

-- ---------------------------------------------------------------------------
-- 3. 特别预防措施
-- ---------------------------------------------------------------------------
CREATE TABLE `t_special_measure` (
  `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `measure_no`         VARCHAR(32)  NOT NULL COMMENT '措施编号',
  `customer_id`        BIGINT       NOT NULL COMMENT '客户ID',
  `customer_name`      VARCHAR(128) DEFAULT NULL COMMENT '客户姓名',
  `measure_type`       VARCHAR(32)  NOT NULL COMMENT '措施类型：CUSTOMER_CONTROL/TRANSACTION_RESTRICTION/ENHANCED_DUE_DILIGENCE/ACCOUNT_REVIEW',
  `trigger_type`       VARCHAR(32)  NOT NULL COMMENT '触发类型：WATCHLIST_HIT/AUTHORITY_REQUEST/MANUAL/RISK_CHANGE',
  `related_result_id`  BIGINT       DEFAULT NULL COMMENT '关联筛查结果ID',
  `related_alert_id`   BIGINT       DEFAULT NULL COMMENT '关联预警ID',
  `control_level`      VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM' COMMENT '管控级别：LOW/MEDIUM/HIGH/CRITICAL',
  `measure_content`    TEXT         NOT NULL COMMENT '措施内容',
  `start_date`         DATE         NOT NULL COMMENT '开始日期',
  `end_date`           DATE         DEFAULT NULL COMMENT '结束日期',
  `status`             VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/PAUSED/CLOSED/EXPIRED',
  `decision_reason`    TEXT         DEFAULT NULL COMMENT '决策理由',
  `closed_reason`      VARCHAR(512) DEFAULT NULL COMMENT '关闭理由',
  `created_by`         VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `created_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`         VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
  `updated_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_special_measure_no` (`measure_no`),
  KEY `idx_sm_customer_id` (`customer_id`),
  KEY `idx_sm_measure_type` (`measure_type`),
  KEY `idx_sm_status` (`status`),
  KEY `idx_sm_end_date` (`end_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='特别预防措施表';

-- ---------------------------------------------------------------------------
-- 4. 查冻扣信息
-- ---------------------------------------------------------------------------
CREATE TABLE `t_freeze_seizure_deduction` (
  `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `record_no`          VARCHAR(32)  NOT NULL COMMENT '记录编号',
  `customer_id`        BIGINT       NOT NULL COMMENT '客户ID',
  `customer_name`      VARCHAR(128) DEFAULT NULL COMMENT '客户姓名',
  `authority_name`     VARCHAR(256) NOT NULL COMMENT '有权机关',
  `document_no`        VARCHAR(128) NOT NULL COMMENT '文书编号',
  `action_type`        VARCHAR(16)  NOT NULL COMMENT '类型：QUERY/FREEZE/SEIZURE/DEDUCTION',
  `amount`             DECIMAL(18,2) DEFAULT NULL COMMENT '涉及金额',
  `currency`           VARCHAR(8)   NOT NULL DEFAULT 'CNY' COMMENT '币种',
  `effective_date`     DATE         NOT NULL COMMENT '生效日期',
  `expiry_date`        DATE         DEFAULT NULL COMMENT '到期日期',
  `status`             VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/RELEASED/EXPIRED',
  `handler`            VARCHAR(64)  DEFAULT NULL COMMENT '经办人',
  `remark`             VARCHAR(512) DEFAULT NULL COMMENT '备注',
  `created_by`         VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `created_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`         VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
  `updated_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fsd_record_no` (`record_no`),
  KEY `idx_fsd_customer_id` (`customer_id`),
  KEY `idx_fsd_action_type` (`action_type`),
  KEY `idx_fsd_status` (`status`),
  KEY `idx_fsd_expiry_date` (`expiry_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='查冻扣信息表';

-- ---------------------------------------------------------------------------
-- 5. 整改中心独立化字段
-- ---------------------------------------------------------------------------
ALTER TABLE `t_rectification_task`
  MODIFY COLUMN `assessment_id` BIGINT DEFAULT NULL COMMENT '自评估ID',
  ADD COLUMN `source_type` VARCHAR(32) NOT NULL DEFAULT 'SELF_ASSESSMENT' COMMENT '问题来源：SELF_ASSESSMENT/INTERNAL_CHECK/EXTERNAL_CHECK/REGULATOR/AUDIT' AFTER `assessment_id`,
  ADD COLUMN `source_id` BIGINT DEFAULT NULL COMMENT '来源业务ID' AFTER `source_type`,
  ADD COLUMN `issue_category` VARCHAR(64) DEFAULT NULL COMMENT '问题分类' AFTER `issue_description`,
  ADD COLUMN `progress_percent` INT NOT NULL DEFAULT 0 COMMENT '整改进度百分比' AFTER `status`,
  ADD COLUMN `verification_status` VARCHAR(16) DEFAULT 'PENDING' COMMENT '验证状态：PENDING/PASSED/RETURNED' AFTER `completed_time`,
  ADD COLUMN `verify_result` TEXT DEFAULT NULL COMMENT '验证意见' AFTER `verified_time`,
  ADD COLUMN `closed_time` DATETIME DEFAULT NULL COMMENT '销号时间' AFTER `verify_result`;

-- ---------------------------------------------------------------------------
-- 6. 权限与角色授权
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `t_permission` (`permission_code`, `permission_name`, `parent_id`, `type`, `path`, `sort_order`, `icon`, `status`, `created_time`)
VALUES
  ('MENU_SPECIAL_PREVENTION', '特别预防', 0, 'MENU', '/special-prevention', 10, 'operation', 'ENABLED', NOW()),
  ('MENU_RECTIFICATION',      '整改中心', 0, 'MENU', '/rectifications',      11, 'check',     'ENABLED', NOW()),
  ('special:view',            '特别预防-查看', 0, 'BUTTON', NULL, 1001, NULL, 'ENABLED', NOW()),
  ('special:manage',          '特别预防-管理', 0, 'BUTTON', NULL, 1002, NULL, 'ENABLED', NOW()),
  ('rectification:view',      '整改-查看',     0, 'BUTTON', NULL, 1101, NULL, 'ENABLED', NOW()),
  ('rectification:manage',    '整改-管理',     0, 'BUTTON', NULL, 1102, NULL, 'ENABLED', NOW());

INSERT IGNORE INTO `t_role_permission` (`role_id`, `permission_id`, `created_by`, `created_time`)
SELECT r.id, p.id, 'system', NOW()
FROM t_role r, t_permission p
WHERE r.role_code = 'ROLE_ADMIN'
  AND p.permission_code IN ('special:view', 'special:manage', 'rectification:view', 'rectification:manage', 'MENU_SPECIAL_PREVENTION', 'MENU_RECTIFICATION');

INSERT IGNORE INTO `t_role_permission` (`role_id`, `permission_id`, `created_by`, `created_time`)
SELECT r.id, p.id, 'system', NOW()
FROM t_role r, t_permission p
WHERE r.role_code = 'ROLE_COMPLIANCE'
  AND p.permission_code IN ('special:view', 'special:manage', 'rectification:view', 'rectification:manage', 'MENU_SPECIAL_PREVENTION', 'MENU_RECTIFICATION');

INSERT IGNORE INTO `t_role_permission` (`role_id`, `permission_id`, `created_by`, `created_time`)
SELECT r.id, p.id, 'system', NOW()
FROM t_role r, t_permission p
WHERE r.role_code = 'ROLE_INVESTIGATOR'
  AND p.permission_code IN ('special:view', 'special:manage', 'rectification:view', 'rectification:manage', 'MENU_SPECIAL_PREVENTION', 'MENU_RECTIFICATION');

INSERT IGNORE INTO `t_role_permission` (`role_id`, `permission_id`, `created_by`, `created_time`)
SELECT r.id, p.id, 'system', NOW()
FROM t_role r, t_permission p
WHERE r.role_code = 'ROLE_VIEWER'
  AND p.permission_code IN ('rectification:view');
