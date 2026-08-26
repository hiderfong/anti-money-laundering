-- ============================================================================
-- 外部系统集成中心：连接器、同步任务、执行记录及权限
-- ============================================================================

SET NAMES utf8mb4;

CREATE TABLE `t_integration_connector` (
  `id`                     BIGINT        NOT NULL COMMENT '主键ID',
  `connector_code`         VARCHAR(64)   NOT NULL COMMENT '连接器编码',
  `connector_name`         VARCHAR(128)  NOT NULL COMMENT '连接器名称',
  `business_type`          VARCHAR(64)   NOT NULL COMMENT '业务类型',
  `transport_type`         VARCHAR(32)   NOT NULL COMMENT '传输类型：MOCK/REST/SFTP/MQ/FILE',
  `endpoint_url`           VARCHAR(512)  NOT NULL COMMENT '端点地址',
  `auth_type`              VARCHAR(32)   NOT NULL DEFAULT 'NONE' COMMENT '认证类型',
  `credential_ref`         VARCHAR(128)  DEFAULT NULL COMMENT '凭据引用名，不存储密钥明文',
  `status`                 VARCHAR(16)   NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
  `health_status`          VARCHAR(16)   NOT NULL DEFAULT 'UNKNOWN' COMMENT '健康状态：UNKNOWN/HEALTHY/UNHEALTHY',
  `timeout_seconds`        INT           NOT NULL DEFAULT 30 COMMENT '超时时间（秒）',
  `max_retries`            INT           NOT NULL DEFAULT 2 COMMENT '默认最大重试次数',
  `retry_interval_seconds` INT           NOT NULL DEFAULT 30 COMMENT '重试间隔（秒）',
  `last_health_check_time` DATETIME      DEFAULT NULL COMMENT '最近健康检查时间',
  `last_success_time`      DATETIME      DEFAULT NULL COMMENT '最近成功时间',
  `last_failure_time`      DATETIME      DEFAULT NULL COMMENT '最近失败时间',
  `last_error_message`     VARCHAR(1024) DEFAULT NULL COMMENT '最近错误摘要',
  `description`            VARCHAR(512)  DEFAULT NULL COMMENT '连接器说明',
  `created_by`             VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
  `created_time`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`             VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
  `updated_time`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_integration_connector_code` (`connector_code`),
  KEY `idx_integration_connector_business` (`business_type`),
  KEY `idx_integration_connector_health` (`health_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部系统连接器';

CREATE TABLE `t_integration_job` (
  `id`                BIGINT       NOT NULL COMMENT '主键ID',
  `job_code`          VARCHAR(64)  NOT NULL COMMENT '任务编码',
  `job_name`          VARCHAR(128) NOT NULL COMMENT '任务名称',
  `connector_id`      BIGINT       NOT NULL COMMENT '连接器ID',
  `business_object`   VARCHAR(64)  NOT NULL COMMENT '业务对象',
  `direction`         VARCHAR(16)  NOT NULL COMMENT '数据方向：INBOUND/OUTBOUND/BIDIRECTIONAL',
  `cron_expression`   VARCHAR(64)  NOT NULL COMMENT 'Spring Cron表达式',
  `batch_size`        INT          NOT NULL DEFAULT 1000 COMMENT '单批处理数量',
  `max_retries`       INT          NOT NULL DEFAULT 2 COMMENT '任务最大重试次数',
  `enabled`           TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
  `execution_status`  VARCHAR(16)  NOT NULL DEFAULT 'IDLE' COMMENT '执行状态',
  `last_run_time`     DATETIME     DEFAULT NULL COMMENT '最近运行时间',
  `next_run_time`     DATETIME     DEFAULT NULL COMMENT '下次运行时间',
  `description`       VARCHAR(512) DEFAULT NULL COMMENT '任务说明',
  `created_by`        VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `created_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`        VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
  `updated_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_integration_job_code` (`job_code`),
  KEY `idx_integration_job_connector` (`connector_id`),
  KEY `idx_integration_job_next_run` (`enabled`, `next_run_time`),
  CONSTRAINT `fk_integration_job_connector` FOREIGN KEY (`connector_id`) REFERENCES `t_integration_connector` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部系统集成任务';

CREATE TABLE `t_integration_run` (
  `id`                BIGINT        NOT NULL COMMENT '主键ID',
  `run_no`            VARCHAR(64)   NOT NULL COMMENT '运行编号',
  `job_id`            BIGINT        DEFAULT NULL COMMENT '任务ID，健康检查时为空',
  `connector_id`      BIGINT        NOT NULL COMMENT '连接器ID',
  `retry_of_run_id`   BIGINT        DEFAULT NULL COMMENT '原失败运行ID',
  `trigger_type`      VARCHAR(32)   NOT NULL COMMENT '触发类型：HEALTH_CHECK/MANUAL/SCHEDULED/RETRY',
  `status`            VARCHAR(16)   NOT NULL COMMENT '状态：RUNNING/SUCCESS/FAILED',
  `attempt_count`     INT           NOT NULL DEFAULT 0 COMMENT '总尝试次数',
  `retry_count`       INT           NOT NULL DEFAULT 0 COMMENT '重试次数',
  `records_read`      INT           NOT NULL DEFAULT 0 COMMENT '读取记录数',
  `records_written`   INT           NOT NULL DEFAULT 0 COMMENT '写入记录数',
  `records_skipped`   INT           NOT NULL DEFAULT 0 COMMENT '跳过记录数',
  `error_count`       INT           NOT NULL DEFAULT 0 COMMENT '错误记录数',
  `started_time`      DATETIME      NOT NULL COMMENT '开始时间',
  `completed_time`    DATETIME      DEFAULT NULL COMMENT '完成时间',
  `duration_ms`       BIGINT        DEFAULT NULL COMMENT '执行耗时（毫秒）',
  `request_summary`   VARCHAR(1024) DEFAULT NULL COMMENT '请求摘要',
  `response_summary`  VARCHAR(1024) DEFAULT NULL COMMENT '响应摘要',
  `error_message`     VARCHAR(1024) DEFAULT NULL COMMENT '错误摘要',
  `trace_id`          VARCHAR(64)   DEFAULT NULL COMMENT '链路追踪ID',
  `executed_by`       VARCHAR(64)   DEFAULT NULL COMMENT '执行人',
  `created_by`        VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
  `created_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`        VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
  `updated_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_integration_run_no` (`run_no`),
  KEY `idx_integration_run_job` (`job_id`),
  KEY `idx_integration_run_connector` (`connector_id`),
  KEY `idx_integration_run_status_time` (`status`, `started_time`),
  CONSTRAINT `fk_integration_run_job` FOREIGN KEY (`job_id`) REFERENCES `t_integration_job` (`id`),
  CONSTRAINT `fk_integration_run_connector` FOREIGN KEY (`connector_id`) REFERENCES `t_integration_connector` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部集成运行记录';

INSERT IGNORE INTO `t_permission`
  (`permission_code`, `permission_name`, `parent_id`, `type`, `path`, `sort_order`, `icon`, `status`, `created_time`)
VALUES
  ('MENU_INTEGRATION', '集成中心', 0, 'MENU', '/integrations', 16, 'connection', 'ENABLED', NOW()),
  ('integration:view', '集成中心-查看', 0, 'BUTTON', NULL, 1601, NULL, 'ENABLED', NOW()),
  ('integration:manage', '集成中心-维护', 0, 'BUTTON', NULL, 1602, NULL, 'ENABLED', NOW()),
  ('integration:execute', '集成中心-执行', 0, 'BUTTON', NULL, 1603, NULL, 'ENABLED', NOW());

INSERT IGNORE INTO `t_role_permission` (`role_id`, `permission_id`, `created_by`, `created_time`)
SELECT r.id, p.id, 'system', NOW()
FROM t_role r, t_permission p
WHERE r.role_code = 'ROLE_ADMIN'
  AND p.permission_code IN ('MENU_INTEGRATION', 'integration:view', 'integration:manage', 'integration:execute');

INSERT IGNORE INTO `t_role_permission` (`role_id`, `permission_id`, `created_by`, `created_time`)
SELECT r.id, p.id, 'system', NOW()
FROM t_role r, t_permission p
WHERE r.role_code IN ('ROLE_AML_MANAGER', 'ROLE_COMPLIANCE')
  AND p.permission_code IN ('MENU_INTEGRATION', 'integration:view', 'integration:execute');
