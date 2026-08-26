-- ============================================================================
-- 反洗钱机构与组织治理：机构档案、治理人员、股东、登记审批及用户归属
-- ============================================================================

SET NAMES utf8mb4;

CREATE TABLE `t_aml_organization` (
  `id`                    BIGINT        NOT NULL COMMENT '主键ID',
  `org_code`              VARCHAR(64)   NOT NULL COMMENT '机构编码',
  `org_name`              VARCHAR(256)  NOT NULL COMMENT '机构名称',
  `unified_credit_code`   VARCHAR(32)   NOT NULL COMMENT '统一社会信用代码',
  `lei_code`              VARCHAR(32)   DEFAULT NULL COMMENT '全球法人识别编码LEI',
  `org_type`              VARCHAR(32)   NOT NULL COMMENT '机构类型：HEAD_OFFICE/BRANCH/OUTLET',
  `parent_id`             BIGINT        DEFAULT NULL COMMENT '上级机构ID',
  `registered_address`    VARCHAR(512)  DEFAULT NULL COMMENT '注册地址',
  `business_address`      VARCHAR(512)  DEFAULT NULL COMMENT '经营地址',
  `legal_representative`  VARCHAR(128)  DEFAULT NULL COMMENT '法定代表人/负责人',
  `registered_capital`    DECIMAL(18,2) DEFAULT NULL COMMENT '注册资本（万元）',
  `business_scope`        TEXT          DEFAULT NULL COMMENT '经营范围',
  `regulator_name`        VARCHAR(256)  DEFAULT NULL COMMENT '主管监管机构',
  `status`                VARCHAR(16)   NOT NULL DEFAULT 'DISABLED' COMMENT '运营状态：ENABLED/DISABLED',
  `registration_status`   VARCHAR(32)   NOT NULL DEFAULT 'DRAFT' COMMENT '登记状态：DRAFT/PENDING_REVIEW/REJECTED/APPROVED',
  `created_by`            VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
  `created_time`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`            VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
  `updated_time`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_aml_org_code` (`org_code`),
  UNIQUE KEY `uk_aml_org_credit_code` (`unified_credit_code`),
  KEY `idx_aml_org_parent` (`parent_id`),
  KEY `idx_aml_org_type` (`org_type`),
  KEY `idx_aml_org_registration_status` (`registration_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='反洗钱机构档案';

CREATE TABLE `t_aml_org_person` (
  `id`                    BIGINT       NOT NULL COMMENT '主键ID',
  `organization_id`       BIGINT       NOT NULL COMMENT '机构ID',
  `person_type`           VARCHAR(32)  NOT NULL COMMENT '人员类型：SENIOR_MANAGER/AML_OFFICER/CONTACT',
  `person_name`           VARCHAR(128) NOT NULL COMMENT '姓名',
  `title`                 VARCHAR(128) DEFAULT NULL COMMENT '职务',
  `department`            VARCHAR(128) DEFAULT NULL COMMENT '部门',
  `phone`                 VARCHAR(32)  DEFAULT NULL COMMENT '联系电话',
  `email`                 VARCHAR(128) DEFAULT NULL COMMENT '电子邮箱',
  `start_date`            DATE         DEFAULT NULL COMMENT '任职开始日期',
  `end_date`              DATE         DEFAULT NULL COMMENT '任职结束日期',
  `financial_experience`  VARCHAR(512) DEFAULT NULL COMMENT '金融及反洗钱履历摘要',
  `primary_flag`          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否主要负责人',
  `status`                VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
  `created_by`            VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `created_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`            VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
  `updated_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_aml_org_person_org` (`organization_id`),
  KEY `idx_aml_org_person_type` (`person_type`),
  CONSTRAINT `fk_aml_org_person_org` FOREIGN KEY (`organization_id`) REFERENCES `t_aml_organization` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='机构治理人员';

CREATE TABLE `t_aml_org_shareholder` (
  `id`                    BIGINT        NOT NULL COMMENT '主键ID',
  `organization_id`       BIGINT        NOT NULL COMMENT '机构ID',
  `shareholder_name`      VARCHAR(256)  NOT NULL COMMENT '股东名称',
  `shareholder_type`      VARCHAR(32)   NOT NULL COMMENT '股东类型：INDIVIDUAL/ORGANIZATION',
  `registration_code`     VARCHAR(64)   DEFAULT NULL COMMENT '股东登记编码（机构股东）',
  `ownership_percentage`  DECIMAL(7,4)  NOT NULL COMMENT '持股比例（百分比）',
  `controlling_flag`      TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否控股股东',
  `status`                VARCHAR(16)   NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
  `created_by`            VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
  `created_time`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`            VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
  `updated_time`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_aml_org_shareholder_org` (`organization_id`),
  CONSTRAINT `fk_aml_org_shareholder_org` FOREIGN KEY (`organization_id`) REFERENCES `t_aml_organization` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='机构股东信息';

CREATE TABLE `t_aml_org_registration` (
  `id`                    BIGINT       NOT NULL COMMENT '主键ID',
  `registration_no`       VARCHAR(64)  NOT NULL COMMENT '登记申请编号',
  `organization_id`       BIGINT       NOT NULL COMMENT '机构ID',
  `registration_type`     VARCHAR(32)  NOT NULL COMMENT '登记类型：INITIAL/CHANGE',
  `version`               INT          NOT NULL DEFAULT 1 COMMENT '申请版本',
  `status`                VARCHAR(32)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/PENDING_REVIEW/REJECTED/APPROVED',
  `commitment_accepted`   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否确认合规承诺',
  `snapshot_json`         LONGTEXT     DEFAULT NULL COMMENT '提交时机构完整快照',
  `submitted_by`          VARCHAR(64)  DEFAULT NULL COMMENT '提交人',
  `submitted_at`          DATETIME     DEFAULT NULL COMMENT '提交时间',
  `reviewed_by`           VARCHAR(64)  DEFAULT NULL COMMENT '审核人',
  `reviewed_at`           DATETIME     DEFAULT NULL COMMENT '审核时间',
  `review_opinion`        VARCHAR(1024) DEFAULT NULL COMMENT '审核意见',
  `created_by`            VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `created_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`            VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
  `updated_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_aml_org_registration_no` (`registration_no`),
  KEY `idx_aml_org_registration_org` (`organization_id`),
  KEY `idx_aml_org_registration_status` (`status`),
  CONSTRAINT `fk_aml_org_registration_org` FOREIGN KEY (`organization_id`) REFERENCES `t_aml_organization` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='机构登记申请';

CREATE TABLE `t_aml_org_review_log` (
  `id`               BIGINT        NOT NULL COMMENT '主键ID',
  `registration_id`  BIGINT        NOT NULL COMMENT '登记申请ID',
  `action_type`      VARCHAR(32)   NOT NULL COMMENT '动作：CREATE/SUBMIT/APPROVE/REJECT',
  `from_status`      VARCHAR(32)   DEFAULT NULL COMMENT '原状态',
  `to_status`        VARCHAR(32)   NOT NULL COMMENT '新状态',
  `opinion`          VARCHAR(1024) DEFAULT NULL COMMENT '处理意见',
  `operator`         VARCHAR(64)   DEFAULT NULL COMMENT '操作人',
  `operated_at`      DATETIME      NOT NULL COMMENT '操作时间',
  `created_by`       VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
  `created_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`       VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
  `updated_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_aml_org_review_registration` (`registration_id`),
  CONSTRAINT `fk_aml_org_review_registration` FOREIGN KEY (`registration_id`) REFERENCES `t_aml_org_registration` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='机构登记审批日志';

ALTER TABLE `t_user`
  ADD COLUMN `organization_id` BIGINT DEFAULT NULL COMMENT '所属反洗钱机构ID' AFTER `position`,
  ADD KEY `idx_user_organization` (`organization_id`);

INSERT IGNORE INTO `t_permission`
  (`permission_code`, `permission_name`, `parent_id`, `type`, `path`, `sort_order`, `icon`, `status`, `created_time`)
VALUES
  ('MENU_ORGANIZATION', '机构治理', 0, 'MENU', '/organizations', 15, 'office-building', 'ENABLED', NOW()),
  ('organization:view', '机构治理-查看', 0, 'BUTTON', NULL, 1501, NULL, 'ENABLED', NOW()),
  ('organization:manage', '机构治理-维护', 0, 'BUTTON', NULL, 1502, NULL, 'ENABLED', NOW()),
  ('organization:review', '机构治理-审批', 0, 'BUTTON', NULL, 1503, NULL, 'ENABLED', NOW());

INSERT IGNORE INTO `t_role_permission` (`role_id`, `permission_id`, `created_by`, `created_time`)
SELECT r.id, p.id, 'system', NOW()
FROM t_role r, t_permission p
WHERE r.role_code = 'ROLE_ADMIN'
  AND p.permission_code IN ('MENU_ORGANIZATION', 'organization:view', 'organization:manage', 'organization:review');

INSERT IGNORE INTO `t_role_permission` (`role_id`, `permission_id`, `created_by`, `created_time`)
SELECT r.id, p.id, 'system', NOW()
FROM t_role r, t_permission p
WHERE r.role_code IN ('ROLE_AML_MANAGER', 'ROLE_COMPLIANCE')
  AND p.permission_code IN ('MENU_ORGANIZATION', 'organization:view', 'organization:review');

INSERT IGNORE INTO `t_role_permission` (`role_id`, `permission_id`, `created_by`, `created_time`)
SELECT r.id, p.id, 'system', NOW()
FROM t_role r, t_permission p
WHERE r.role_code = 'ROLE_AML_OFFICER'
  AND p.permission_code IN ('MENU_ORGANIZATION', 'organization:view', 'organization:manage');

