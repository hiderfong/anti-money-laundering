-- ============================================================================
-- 监管报送全生命周期：报文版本、签名、传输状态、回执和重报关联
-- ============================================================================

SET NAMES utf8mb4;

CREATE TABLE `t_regulatory_submission` (
  `id`                   BIGINT        NOT NULL COMMENT '主键ID',
  `submission_no`        VARCHAR(64)   NOT NULL COMMENT '报送流水号',
  `report_type`          VARCHAR(32)   NOT NULL COMMENT '报告类型：LARGE_TXN/SUSPICIOUS',
  `report_id`            BIGINT        NOT NULL COMMENT '源报告ID',
  `report_no`            VARCHAR(64)   NOT NULL COMMENT '源报告编号',
  `version_no`           INT           NOT NULL DEFAULT 1 COMMENT '报送版本号',
  `parent_submission_id` BIGINT        DEFAULT NULL COMMENT '上一个被退回或失败的报送版本',
  `connector_id`         BIGINT        NOT NULL COMMENT '监管报送连接器ID',
  `status`               VARCHAR(24)   NOT NULL COMMENT 'PREPARING/SUBMITTED/ACCEPTED/REJECTED/FAILED',
  `schema_version`       VARCHAR(64)   NOT NULL COMMENT '报文规范版本',
  `payload_format`       VARCHAR(16)   NOT NULL DEFAULT 'XML' COMMENT '报文格式',
  `payload_content`      LONGTEXT      DEFAULT NULL COMMENT '本版本原始报文',
  `payload_hash`         VARCHAR(128)  DEFAULT NULL COMMENT 'SHA-256报文摘要',
  `signature_algorithm`  VARCHAR(64)   DEFAULT NULL COMMENT '签名算法',
  `signature_value`      VARCHAR(1024) DEFAULT NULL COMMENT '签名值',
  `external_request_id`  VARCHAR(128)  DEFAULT NULL COMMENT '监管网关请求号',
  `submitted_by`         VARCHAR(64)   DEFAULT NULL COMMENT '报送人',
  `submitted_time`       DATETIME      DEFAULT NULL COMMENT '发送时间',
  `completed_time`       DATETIME      DEFAULT NULL COMMENT '最终完成时间',
  `receipt_status`       VARCHAR(24)   DEFAULT NULL COMMENT 'PENDING/ACCEPTED/REJECTED',
  `receipt_no`           VARCHAR(128)  DEFAULT NULL COMMENT '最新监管回执号',
  `receipt_time`         DATETIME      DEFAULT NULL COMMENT '最新回执时间',
  `return_code`          VARCHAR(64)   DEFAULT NULL COMMENT '监管返回码',
  `return_message`       VARCHAR(1024) DEFAULT NULL COMMENT '监管返回说明',
  `correction_note`      VARCHAR(1024) DEFAULT NULL COMMENT '退回修正说明',
  `failure_stage`        VARCHAR(32)   DEFAULT NULL COMMENT '失败阶段',
  `error_message`        VARCHAR(1024) DEFAULT NULL COMMENT '失败信息',
  `retry_count`          INT           NOT NULL DEFAULT 0 COMMENT '重报次数',
  `created_by`           VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
  `created_time`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`           VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
  `updated_time`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_regulatory_submission_no` (`submission_no`),
  UNIQUE KEY `uk_regulatory_report_version` (`report_type`, `report_id`, `version_no`),
  KEY `idx_regulatory_submission_status` (`status`, `created_time`),
  KEY `idx_regulatory_submission_receipt` (`receipt_status`, `receipt_time`),
  KEY `idx_regulatory_submission_connector` (`connector_id`),
  KEY `idx_regulatory_submission_parent` (`parent_submission_id`),
  CONSTRAINT `fk_regulatory_submission_connector` FOREIGN KEY (`connector_id`) REFERENCES `t_integration_connector` (`id`),
  CONSTRAINT `fk_regulatory_submission_parent` FOREIGN KEY (`parent_submission_id`) REFERENCES `t_regulatory_submission` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监管报送版本记录';

CREATE TABLE `t_regulatory_receipt` (
  `id`                BIGINT        NOT NULL COMMENT '主键ID',
  `submission_id`     BIGINT        NOT NULL COMMENT '监管报送版本ID',
  `receipt_no`        VARCHAR(128)  DEFAULT NULL COMMENT '回执编号',
  `receipt_status`    VARCHAR(24)   NOT NULL COMMENT 'PENDING/ACCEPTED/REJECTED',
  `receipt_code`      VARCHAR(64)   DEFAULT NULL COMMENT '回执码',
  `receipt_message`   VARCHAR(1024) DEFAULT NULL COMMENT '回执说明',
  `receipt_payload`   LONGTEXT      DEFAULT NULL COMMENT '完整回执原文',
  `received_time`     DATETIME      NOT NULL COMMENT '收到时间',
  `receipt_source`    VARCHAR(32)   NOT NULL COMMENT 'GATEWAY/POLL/CALLBACK',
  `created_by`        VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
  `created_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`        VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
  `updated_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_regulatory_receipt_submission` (`submission_id`, `received_time`),
  KEY `idx_regulatory_receipt_status` (`receipt_status`, `received_time`),
  CONSTRAINT `fk_regulatory_receipt_submission` FOREIGN KEY (`submission_id`) REFERENCES `t_regulatory_submission` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监管回执历史';

ALTER TABLE `t_report_submit_log`
  MODIFY COLUMN `request_data` LONGTEXT DEFAULT NULL COMMENT '报送请求报文',
  ADD COLUMN `submission_id` BIGINT DEFAULT NULL COMMENT '统一监管报送版本ID' AFTER `report_id`,
  ADD COLUMN `external_request_id` VARCHAR(128) DEFAULT NULL COMMENT '监管网关请求号' AFTER `response_data`,
  ADD COLUMN `receipt_no` VARCHAR(128) DEFAULT NULL COMMENT '监管回执号' AFTER `external_request_id`,
  ADD KEY `idx_report_submit_submission` (`submission_id`),
  ADD CONSTRAINT `fk_report_submit_submission` FOREIGN KEY (`submission_id`) REFERENCES `t_regulatory_submission` (`id`);
