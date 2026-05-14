-- ============================================================================
-- 项目：保险公司反洗钱管理系统（AML System）
-- 文件：V008__investigation_assistance.sql
-- 描述：反洗钱调查协查中心
-- ============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. 调查协查请求
-- ---------------------------------------------------------------------------
CREATE TABLE `t_investigation_request` (
  `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `request_no`         VARCHAR(32)  NOT NULL COMMENT '请求编号',
  `authority_name`     VARCHAR(256) NOT NULL COMMENT '有权机关',
  `request_type`       VARCHAR(32)  NOT NULL COMMENT '请求类型：INQUIRY/REVIEW/COPY/ASSIST_INVESTIGATION/OTHER',
  `document_no`        VARCHAR(128) NOT NULL COMMENT '文书编号',
  `customer_id`        BIGINT       DEFAULT NULL COMMENT '关联客户ID',
  `customer_name`      VARCHAR(128) DEFAULT NULL COMMENT '客户姓名',
  `related_case_id`    BIGINT       DEFAULT NULL COMMENT '关联案件ID',
  `priority`           VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM' COMMENT '优先级：LOW/MEDIUM/HIGH/URGENT',
  `received_date`      DATE         NOT NULL COMMENT '接收日期',
  `due_date`           DATE         NOT NULL COMMENT '办理期限',
  `status`             VARCHAR(24)  NOT NULL DEFAULT 'RECEIVED' COMMENT '状态：RECEIVED/PROCESSING/WAITING_APPROVAL/RESPONDED/CLOSED/RETURNED',
  `handler`            VARCHAR(64)  DEFAULT NULL COMMENT '经办人',
  `summary`            TEXT         NOT NULL COMMENT '请求摘要',
  `response_summary`   TEXT         DEFAULT NULL COMMENT '回复摘要',
  `completed_time`     DATETIME     DEFAULT NULL COMMENT '完成时间',
  `closed_time`        DATETIME     DEFAULT NULL COMMENT '关闭时间',
  `created_by`         VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `created_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`         VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
  `updated_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_investigation_request_no` (`request_no`),
  KEY `idx_ir_authority_name` (`authority_name`),
  KEY `idx_ir_customer_id` (`customer_id`),
  KEY `idx_ir_related_case_id` (`related_case_id`),
  KEY `idx_ir_status` (`status`),
  KEY `idx_ir_due_date` (`due_date`),
  KEY `idx_ir_created_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='反洗钱调查协查请求表';

-- ---------------------------------------------------------------------------
-- 2. 调查协查动作记录
-- ---------------------------------------------------------------------------
CREATE TABLE `t_investigation_action` (
  `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `request_id`         BIGINT       NOT NULL COMMENT '调查协查请求ID',
  `action_no`          VARCHAR(32)  NOT NULL COMMENT '动作编号',
  `action_type`        VARCHAR(32)  NOT NULL COMMENT '动作类型：INQUIRY/REVIEW/COPY/DATA_EXPORT/RESPONSE/OTHER',
  `action_content`     TEXT         NOT NULL COMMENT '动作内容',
  `action_result`      TEXT         DEFAULT NULL COMMENT '动作结果',
  `operator`           VARCHAR(64)  DEFAULT NULL COMMENT '操作人',
  `action_time`        DATETIME     NOT NULL COMMENT '动作时间',
  `attachment_ref`     VARCHAR(512) DEFAULT NULL COMMENT '附件或证据引用',
  `created_by`         VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `created_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`         VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
  `updated_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_investigation_action_no` (`action_no`),
  KEY `idx_ia_request_id` (`request_id`),
  KEY `idx_ia_action_type` (`action_type`),
  KEY `idx_ia_action_time` (`action_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='反洗钱调查协查动作记录表';

-- ---------------------------------------------------------------------------
-- 3. 权限与角色授权
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `t_permission` (`permission_code`, `permission_name`, `parent_id`, `type`, `path`, `sort_order`, `icon`, `status`, `created_time`)
VALUES
  ('MENU_INVESTIGATION', '调查协查', 0, 'MENU', '/investigations', 12, 'search', 'ENABLED', NOW()),
  ('investigation:view', '调查协查-查看', 0, 'BUTTON', NULL, 1201, NULL, 'ENABLED', NOW()),
  ('investigation:manage', '调查协查-管理', 0, 'BUTTON', NULL, 1202, NULL, 'ENABLED', NOW());

INSERT IGNORE INTO `t_role_permission` (`role_id`, `permission_id`, `created_by`, `created_time`)
SELECT r.id, p.id, 'system', NOW()
FROM t_role r, t_permission p
WHERE r.role_code = 'ROLE_ADMIN'
  AND p.permission_code IN ('investigation:view', 'investigation:manage', 'MENU_INVESTIGATION');

INSERT IGNORE INTO `t_role_permission` (`role_id`, `permission_id`, `created_by`, `created_time`)
SELECT r.id, p.id, 'system', NOW()
FROM t_role r, t_permission p
WHERE r.role_code = 'ROLE_COMPLIANCE'
  AND p.permission_code IN ('investigation:view', 'investigation:manage', 'MENU_INVESTIGATION');

INSERT IGNORE INTO `t_role_permission` (`role_id`, `permission_id`, `created_by`, `created_time`)
SELECT r.id, p.id, 'system', NOW()
FROM t_role r, t_permission p
WHERE r.role_code = 'ROLE_INVESTIGATOR'
  AND p.permission_code IN ('investigation:view', 'investigation:manage', 'MENU_INVESTIGATION');
