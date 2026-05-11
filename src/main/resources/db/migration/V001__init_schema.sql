-- ============================================================================
-- 项目：保险公司反洗钱管理系统（AML System）
-- 文件：V001__init_schema.sql
-- 描述：数据库初始化脚本 — 全量建表及索引
-- 数据库：MySQL 8.0+ / InnoDB / utf8mb4
-- 版本：v1.0
-- 日期：2025-05-01
-- 说明：本脚本由 Flyway 管理，请勿手动修改已执行的迁移脚本
-- ============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 1. 系统管理表（System Tables）
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1.1 系统用户表
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_user`;
CREATE TABLE `t_user` (
  `id`                    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username`              VARCHAR(64)   NOT NULL COMMENT '登录用户名',
  `password_hash`         VARCHAR(255)  NOT NULL COMMENT '密码哈希值（BCrypt）',
  `real_name`             VARCHAR(64)   NOT NULL COMMENT '真实姓名',
  `email`                 VARCHAR(128)  DEFAULT NULL COMMENT '电子邮箱',
  `phone`                 VARCHAR(20)   DEFAULT NULL COMMENT '手机号码',
  `department`            VARCHAR(128)  DEFAULT NULL COMMENT '所属部门',
  `position`              VARCHAR(64)   DEFAULT NULL COMMENT '职位',
  `status`                VARCHAR(16)   NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED-启用 DISABLED-禁用 LOCKED-锁定',
  `last_login_time`       DATETIME      DEFAULT NULL COMMENT '最后登录时间',
  `last_login_ip`         VARCHAR(45)   DEFAULT NULL COMMENT '最后登录IP',
  `password_changed_time` DATETIME      DEFAULT NULL COMMENT '密码最后修改时间',
  `login_fail_count`      INT           NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
  `remark`                VARCHAR(512)  DEFAULT NULL COMMENT '备注',
  `created_by`            VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
  `created_time`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`            VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
  `updated_time`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_status` (`status`),
  KEY `idx_department` (`department`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- ---------------------------------------------------------------------------
-- 1.2 角色表
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_role`;
CREATE TABLE `t_role` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_code`     VARCHAR(64)   NOT NULL COMMENT '角色编码',
  `role_name`     VARCHAR(128)  NOT NULL COMMENT '角色名称',
  `description`   VARCHAR(512)  DEFAULT NULL COMMENT '角色描述',
  `status`        VARCHAR(16)   NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED-启用 DISABLED-禁用',
  `created_by`    VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
  `created_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`    VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
  `updated_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- ---------------------------------------------------------------------------
-- 1.3 用户-角色关联表
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_user_role`;
CREATE TABLE `t_user_role` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id`      BIGINT      NOT NULL COMMENT '用户ID',
  `role_id`      BIGINT      NOT NULL COMMENT '角色ID',
  `created_by`   VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  `created_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
  KEY `idx_role_id` (`role_id`),
  CONSTRAINT `fk_ur_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_ur_role` FOREIGN KEY (`role_id`) REFERENCES `t_role` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-角色关联表';

-- ---------------------------------------------------------------------------
-- 1.4 权限表
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_permission`;
CREATE TABLE `t_permission` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `permission_code` VARCHAR(128)  NOT NULL COMMENT '权限编码',
  `permission_name` VARCHAR(128)  NOT NULL COMMENT '权限名称',
  `parent_id`       BIGINT        DEFAULT 0 COMMENT '父权限ID，0表示顶级',
  `type`            VARCHAR(16)   NOT NULL COMMENT '类型：MENU-菜单 BUTTON-按钮 API-接口',
  `path`            VARCHAR(255)  DEFAULT NULL COMMENT '路由路径或API路径',
  `sort_order`      INT           NOT NULL DEFAULT 0 COMMENT '排序号',
  `icon`            VARCHAR(64)   DEFAULT NULL COMMENT '图标',
  `status`          VARCHAR(16)   NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
  `created_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permission_code` (`permission_code`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';

-- ---------------------------------------------------------------------------
-- 1.5 角色-权限关联表
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_role_permission`;
CREATE TABLE `t_role_permission` (
  `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_id`        BIGINT      NOT NULL COMMENT '角色ID',
  `permission_id`  BIGINT      NOT NULL COMMENT '权限ID',
  `created_by`     VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  `created_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_perm` (`role_id`, `permission_id`),
  KEY `idx_permission_id` (`permission_id`),
  CONSTRAINT `fk_rp_role` FOREIGN KEY (`role_id`) REFERENCES `t_role` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_rp_perm` FOREIGN KEY (`permission_id`) REFERENCES `t_permission` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-权限关联表';

-- ---------------------------------------------------------------------------
-- 1.6 数据字典类型表
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_sys_dict`;
CREATE TABLE `t_sys_dict` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `dict_code`   VARCHAR(64)   NOT NULL COMMENT '字典编码',
  `dict_name`   VARCHAR(128)  NOT NULL COMMENT '字典名称',
  `description` VARCHAR(512)  DEFAULT NULL COMMENT '描述',
  `status`      VARCHAR(16)   NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
  `created_by`  VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
  `created_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`  VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
  `updated_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_code` (`dict_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据字典类型表';

-- ---------------------------------------------------------------------------
-- 1.7 数据字典项表
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_sys_dict_item`;
CREATE TABLE `t_sys_dict_item` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `dict_id`     BIGINT        NOT NULL COMMENT '字典类型ID',
  `item_code`   VARCHAR(64)   NOT NULL COMMENT '字典项编码',
  `item_label`  VARCHAR(255)  NOT NULL COMMENT '字典项标签',
  `item_value`  VARCHAR(255)  NOT NULL COMMENT '字典项值',
  `sort_order`  INT           NOT NULL DEFAULT 0 COMMENT '排序号',
  `status`      VARCHAR(16)   NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
  `remark`      VARCHAR(512)  DEFAULT NULL COMMENT '备注',
  `created_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_item` (`dict_id`, `item_code`),
  CONSTRAINT `fk_dict_item` FOREIGN KEY (`dict_id`) REFERENCES `t_sys_dict` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据字典项表';

-- ---------------------------------------------------------------------------
-- 1.8 系统参数表
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_sys_param`;
CREATE TABLE `t_sys_param` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `param_code`    VARCHAR(128)  NOT NULL COMMENT '参数编码',
  `param_name`    VARCHAR(255)  NOT NULL COMMENT '参数名称',
  `param_value`   TEXT          DEFAULT NULL COMMENT '参数值',
  `default_value` TEXT          DEFAULT NULL COMMENT '默认值',
  `data_type`     VARCHAR(16)   NOT NULL DEFAULT 'STRING' COMMENT '数据类型：STRING/INT/DECIMAL/BOOLEAN/JSON',
  `description`   VARCHAR(512)  DEFAULT NULL COMMENT '参数描述',
  `editable`      TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '是否可编辑：1-是 0-否',
  `status`        VARCHAR(16)   NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
  `created_by`    VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
  `created_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`    VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
  `updated_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_param_code` (`param_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统参数表';

-- ---------------------------------------------------------------------------
-- 1.9 审计日志表
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_audit_log`;
CREATE TABLE `t_audit_log` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `trace_id`        VARCHAR(64)   DEFAULT NULL COMMENT '请求追踪ID',
  `user_id`         BIGINT        DEFAULT NULL COMMENT '操作用户ID',
  `username`        VARCHAR(64)   DEFAULT NULL COMMENT '操作用户名',
  `operation_type`  VARCHAR(32)   NOT NULL COMMENT '操作类型：CREATE/UPDATE/DELETE/QUERY/LOGIN/LOGOUT/EXPORT/IMPORT',
  `module`          VARCHAR(64)   NOT NULL COMMENT '功能模块：KYC/SCREENING/MONITORING/ALERT/CASE/REPORT/SYSTEM',
  `target_type`     VARCHAR(64)   DEFAULT NULL COMMENT '操作对象类型',
  `target_id`       VARCHAR(64)   DEFAULT NULL COMMENT '操作对象ID',
  `detail`          TEXT          DEFAULT NULL COMMENT '操作详情',
  `ip_address`      VARCHAR(45)   DEFAULT NULL COMMENT '客户端IP',
  `user_agent`      VARCHAR(512)  DEFAULT NULL COMMENT 'User-Agent',
  `request_uri`     VARCHAR(512)  DEFAULT NULL COMMENT '请求URI',
  `request_method`  VARCHAR(16)   DEFAULT NULL COMMENT '请求方法',
  `response_code`   INT           DEFAULT NULL COMMENT '响应状态码',
  `duration_ms`     BIGINT        DEFAULT NULL COMMENT '请求耗时（毫秒）',
  `error_message`   TEXT          DEFAULT NULL COMMENT '错误信息',
  `created_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_created_time` (`created_time`),
  KEY `idx_module` (`module`),
  KEY `idx_operation_type` (`operation_type`),
  KEY `idx_trace_id` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志表';

-- ---------------------------------------------------------------------------
-- 1.10 通知表
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_notification`;
CREATE TABLE `t_notification` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id`       BIGINT        NOT NULL COMMENT '接收用户ID',
  `type`          VARCHAR(32)   NOT NULL COMMENT '通知类型：ALERT-预警 REVIEW-复核 ESCALATION-升级 SYSTEM-系统',
  `title`         VARCHAR(255)  NOT NULL COMMENT '通知标题',
  `content`       TEXT          DEFAULT NULL COMMENT '通知内容',
  `related_type`  VARCHAR(64)   DEFAULT NULL COMMENT '关联对象类型：ALERT/CASE/STR',
  `related_id`    VARCHAR(64)   DEFAULT NULL COMMENT '关联对象ID',
  `is_read`       TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否已读：0-未读 1-已读',
  `read_time`     DATETIME      DEFAULT NULL COMMENT '阅读时间',
  `created_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_is_read` (`user_id`, `is_read`),
  KEY `idx_type` (`type`),
  KEY `idx_created_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知表';

-- ---------------------------------------------------------------------------
-- 1.11 文件上传表
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_file_upload`;
CREATE TABLE `t_file_upload` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `original_name` VARCHAR(255)  NOT NULL COMMENT '原始文件名',
  `stored_name`   VARCHAR(255)  NOT NULL COMMENT '存储文件名',
  `file_path`     VARCHAR(512)  NOT NULL COMMENT '文件存储路径',
  `file_size`     BIGINT        NOT NULL COMMENT '文件大小（字节）',
  `file_type`     VARCHAR(32)   NOT NULL COMMENT '文件类型（MIME）',
  `file_ext`      VARCHAR(16)   DEFAULT NULL COMMENT '文件扩展名',
  `upload_by`     BIGINT        NOT NULL COMMENT '上传用户ID',
  `upload_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  `related_type`  VARCHAR(64)   DEFAULT NULL COMMENT '关联对象类型',
  `related_id`    VARCHAR(64)   DEFAULT NULL COMMENT '关联对象ID',
  `status`        VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-有效 DELETED-已删除',
  `created_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_related` (`related_type`, `related_id`),
  KEY `idx_upload_by` (`upload_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件上传表';

-- ============================================================================
-- 2. 客户/KYC 表（Customer/KYC Tables）
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 2.1 客户主表
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_customer`;
CREATE TABLE `t_customer` (
  `id`                      BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `customer_no`             VARCHAR(32)   NOT NULL COMMENT '客户编号（系统生成）',
  `customer_type`           VARCHAR(16)   NOT NULL COMMENT '客户类型：INDIVIDUAL-个人 CORPORATE-法人',
  -- 个人信息字段
  `name`                    VARCHAR(128)  NOT NULL COMMENT '姓名/名称',
  `name_en`                 VARCHAR(128)  DEFAULT NULL COMMENT '英文姓名/名称',
  `gender`                  VARCHAR(8)    DEFAULT NULL COMMENT '性别：MALE-男 FEMALE-女',
  `nationality`             VARCHAR(64)   DEFAULT NULL COMMENT '国籍',
  `ethnic_group`            VARCHAR(32)   DEFAULT NULL COMMENT '民族',
  `birth_date`              DATE          DEFAULT NULL COMMENT '出生日期/成立日期',
  `id_type`                 VARCHAR(32)   DEFAULT NULL COMMENT '证件类型：IDCARD-身份证 PASSPORT-护照 HK_MACAO_TW-港澳台通行证 MILITARY-军官证 OTHER-其他',
  `id_number`               VARCHAR(128)  DEFAULT NULL COMMENT '证件号码（应用层加密存储）',
  `id_issuing_authority`    VARCHAR(128)  DEFAULT NULL COMMENT '证件签发机关',
  `id_expiry_date`          DATE          DEFAULT NULL COMMENT '证件有效期截止日',
  `address`                 VARCHAR(512)  DEFAULT NULL COMMENT '住所地/注册地址',
  `residence_address`       VARCHAR(512)  DEFAULT NULL COMMENT '经常居住地/经营地址',
  `phone`                   VARCHAR(20)   DEFAULT NULL COMMENT '手机号码（应用层加密存储）',
  `email`                   VARCHAR(128)  DEFAULT NULL COMMENT '电子邮箱',
  -- 职业/企业信息
  `occupation`              VARCHAR(64)   DEFAULT NULL COMMENT '职业类别',
  `employer`                VARCHAR(256)  DEFAULT NULL COMMENT '工作单位',
  `job_title`               VARCHAR(64)   DEFAULT NULL COMMENT '职务',
  `annual_income_range`     VARCHAR(32)   DEFAULT NULL COMMENT '年收入区间',
  `tax_resident_status`     VARCHAR(16)   DEFAULT NULL COMMENT '税收居民身份：CHINA-中国 OTHER-其他 BOTH-双重',
  -- 法人信息
  `unified_credit_code`     VARCHAR(64)   DEFAULT NULL COMMENT '统一社会信用代码',
  `enterprise_type`         VARCHAR(64)   DEFAULT NULL COMMENT '企业类型',
  `registered_capital`      DECIMAL(18,2) DEFAULT NULL COMMENT '注册资本（万元）',
  `business_scope`          TEXT          DEFAULT NULL COMMENT '经营范围',
  `legal_representative`    VARCHAR(128)  DEFAULT NULL COMMENT '法定代表人',
  -- 风险评级
  `risk_level`              VARCHAR(16)   NOT NULL DEFAULT 'LOW' COMMENT '风险等级：LOW-低 MEDIUM-中 HIGH-高',
  `risk_score`              INT           NOT NULL DEFAULT 0 COMMENT '风险评分（0-100）',
  `risk_update_time`        DATETIME      DEFAULT NULL COMMENT '风险评级更新时间',
  -- 标记字段
  `is_pep`                  TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否PEP：0-否 1-是',
  `pep_type`                VARCHAR(32)   DEFAULT NULL COMMENT 'PEP类型：DOMESTIC/INTERNATIONAL/INTL_ORG',
  `is_sanctioned`           TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否命中制裁名单：0-否 1-是',
  -- KYC状态
  `kyc_status`              VARCHAR(16)   NOT NULL DEFAULT 'INCOMPLETE' COMMENT 'KYC状态：INCOMPLETE-未完成 COMPLETE-已完成 REVIEWING-审核中',
  `kyc_last_review_time`    DATETIME      DEFAULT NULL COMMENT 'KYC上次复核时间',
  `kyc_next_review_time`    DATETIME      DEFAULT NULL COMMENT 'KYC下次复核时间',
  `remark`                  VARCHAR(1024) DEFAULT NULL COMMENT '备注',
  `status`                  VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '客户状态：ACTIVE-正常 INACTIVE-停用 FROZEN-冻结',
  `created_by`              VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
  `created_time`            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`              VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
  `updated_time`            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_no` (`customer_no`),
  KEY `idx_name` (`name`),
  KEY `idx_id_number` (`id_number`),
  KEY `idx_risk_level` (`risk_level`),
  KEY `idx_status` (`status`),
  KEY `idx_kyc_status` (`kyc_status`),
  KEY `idx_customer_type` (`customer_type`),
  KEY `idx_created_time` (`created_time`),
  KEY `idx_unified_credit_code` (`unified_credit_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户主表';

-- ---------------------------------------------------------------------------
-- 2.2 客户受益所有人表
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_customer_beneficial_owner`;
CREATE TABLE `t_customer_beneficial_owner` (
  `id`                    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `customer_id`           BIGINT        NOT NULL COMMENT '所属法人客户ID',
  `owner_name`            VARCHAR(128)  NOT NULL COMMENT '受益所有人姓名',
  `owner_id_type`         VARCHAR(32)   DEFAULT NULL COMMENT '证件类型',
  `owner_id_number`       VARCHAR(128)  DEFAULT NULL COMMENT '证件号码（应用层加密）',
  `nationality`           VARCHAR(64)   DEFAULT NULL COMMENT '国籍',
  `birth_date`            DATE          DEFAULT NULL COMMENT '出生日期',
  `ownership_percentage`  DECIMAL(5,2)  DEFAULT NULL COMMENT '持股/权益比例（%）',
  `control_type`          VARCHAR(16)   NOT NULL COMMENT '控制方式：EQUITY-股权 CONTROL-控制 BOTH-双重',
  `control_description`   VARCHAR(512)  DEFAULT NULL COMMENT '控制关系说明',
  `relationship`          VARCHAR(64)   DEFAULT NULL COMMENT '与法人关系',
  `status`                VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
  `created_by`            VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
  `created_time`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`            VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
  `updated_time`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_customer_id` (`customer_id`),
  CONSTRAINT `fk_bo_customer` FOREIGN KEY (`customer_id`) REFERENCES `t_customer` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户受益所有人表';

-- ---------------------------------------------------------------------------
-- 2.3 身份核验记录表
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_verification_record`;
CREATE TABLE `t_verification_record` (
  `id`                    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `customer_id`           BIGINT        NOT NULL COMMENT '客户ID',
  `verification_type`     VARCHAR(32)   NOT NULL COMMENT '核验类型：TWO_FACTOR-二要素 FOUR_FACTOR-四要素 ENTERPRISE-企业 FACE-人脸',
  `verification_result`   VARCHAR(16)   NOT NULL COMMENT '核验结果：SUCCESS-成功 FAILURE-失败 PENDING-待处理',
  `request_data`          TEXT          DEFAULT NULL COMMENT '请求数据（脱敏存储）',
  `response_data`         TEXT          DEFAULT NULL COMMENT '响应数据（脱敏存储）',
  `third_party_provider`  VARCHAR(64)   DEFAULT NULL COMMENT '第三方服务商',
  `error_message`         VARCHAR(512)  DEFAULT NULL COMMENT '错误信息',
  `verified_by`           BIGINT        DEFAULT NULL COMMENT '核验操作人ID（人工核验时）',
  `verified_time`         DATETIME      DEFAULT NULL COMMENT '核验时间',
  `created_time`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_result` (`verification_result`),
  KEY `idx_created_time` (`created_time`),
  CONSTRAINT `fk_ver_customer` FOREIGN KEY (`customer_id`) REFERENCES `t_customer` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='身份核验记录表';

-- ---------------------------------------------------------------------------
-- 2.4 客户风险评级变更日志表
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_customer_risk_rating_log`;
CREATE TABLE `t_customer_risk_rating_log` (
  `id`                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `customer_id`       BIGINT        NOT NULL COMMENT '客户ID',
  `old_risk_level`    VARCHAR(16)   DEFAULT NULL COMMENT '原风险等级',
  `new_risk_level`    VARCHAR(16)   NOT NULL COMMENT '新风险等级',
  `old_risk_score`    INT           DEFAULT NULL COMMENT '原风险评分',
  `new_risk_score`    INT           NOT NULL COMMENT '新风险评分',
  `change_reason`     VARCHAR(512)  DEFAULT NULL COMMENT '变更原因',
  `change_type`       VARCHAR(16)   NOT NULL COMMENT '变更类型：AUTO-系统自动 MANUAL-人工调整',
  `changed_by`        VARCHAR(64)   DEFAULT NULL COMMENT '变更人',
  `changed_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '变更时间',
  PRIMARY KEY (`id`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_changed_time` (`changed_time`),
  CONSTRAINT `fk_rrl_customer` FOREIGN KEY (`customer_id`) REFERENCES `t_customer` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户风险评级变更日志表';

-- ---------------------------------------------------------------------------
-- 2.5 PEP名单表
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_pep_list`;
CREATE TABLE `t_pep_list` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `pep_name`        VARCHAR(128)  NOT NULL COMMENT 'PEP姓名（中文）',
  `pep_name_en`     VARCHAR(128)  DEFAULT NULL COMMENT 'PEP姓名（英文）',
  `pep_type`        VARCHAR(32)   NOT NULL COMMENT 'PEP类型：DOMESTIC-国内 INTERNATIONAL-国际 INTL_ORG-国际组织',
  `pep_position`    VARCHAR(256)  DEFAULT NULL COMMENT '职位',
  `pep_country`     VARCHAR(64)   DEFAULT NULL COMMENT '所在国家',
  `pep_organization` VARCHAR(256) DEFAULT NULL COMMENT '所属机构',
  `data_source`     VARCHAR(64)   DEFAULT NULL COMMENT '数据来源',
  `effective_date`  DATE          DEFAULT NULL COMMENT '生效日期',
  `expiry_date`     DATE          DEFAULT NULL COMMENT '失效日期',
  `status`          VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-有效 INACTIVE-无效',
  `created_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_pep_name` (`pep_name`),
  KEY `idx_pep_name_en` (`pep_name_en`),
  KEY `idx_pep_type` (`pep_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PEP名单表';

-- ---------------------------------------------------------------------------
-- 2.6 PEP关联客户表
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_pep_relation`;
CREATE TABLE `t_pep_relation` (
  `id`                  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `pep_list_id`         BIGINT        NOT NULL COMMENT 'PEP名单ID',
  `related_customer_id` BIGINT        DEFAULT NULL COMMENT '关联客户ID',
  `relation_type`       VARCHAR(16)   NOT NULL COMMENT '关联类型：FAMILY-家庭成员 ASSOCIATE-密切关系人',
  `relation_description` VARCHAR(256) DEFAULT NULL COMMENT '关联关系描述',
  `status`              VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
  `created_time`        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_pep_list_id` (`pep_list_id`),
  KEY `idx_customer_id` (`related_customer_id`),
  CONSTRAINT `fk_pr_pep` FOREIGN KEY (`pep_list_id`) REFERENCES `t_pep_list` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_pr_customer` FOREIGN KEY (`related_customer_id`) REFERENCES `t_customer` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PEP关联客户表';


-- ============================================================================
-- 3. 制裁名单/筛查表（Watchlist/Screening Tables）
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 3.1 名单源配置表
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_watchlist_source`;
CREATE TABLE `t_watchlist_source` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `source_code`     VARCHAR(32)   NOT NULL COMMENT '名单源编码',
  `source_name`     VARCHAR(128)  NOT NULL COMMENT '名单源名称',
  `source_type`     VARCHAR(16)   NOT NULL COMMENT '类型：UN/OFAC/EU/MPS/PBOC/FATF/OTHER',
  `update_frequency` VARCHAR(16)  NOT NULL COMMENT '更新频率：REALTIME/DAILY/WEEKLY/MONTHLY',
  `file_format`     VARCHAR(16)   DEFAULT NULL COMMENT '文件格式：XML/CSV/JSON',
  `file_url`        VARCHAR(512)  DEFAULT NULL COMMENT '文件下载地址',
  `last_update_time` DATETIME     DEFAULT NULL COMMENT '最近更新时间',
  `next_update_time` DATETIME     DEFAULT NULL COMMENT '下次更新时间',
  `total_entries`   INT           NOT NULL DEFAULT 0 COMMENT '名单条目总数',
  `status`          VARCHAR(16)   NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
  `created_by`      VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
  `created_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`      VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
  `updated_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_source_code` (`source_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='名单源配置表';

-- ---------------------------------------------------------------------------
-- 3.2 制裁名单主表
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_watchlist`;
CREATE TABLE `t_watchlist` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `source_id`       BIGINT        NOT NULL COMMENT '名单源ID',
  `external_id`     VARCHAR(128)  DEFAULT NULL COMMENT '外部系统原始ID',
  `entity_type`     VARCHAR(16)   NOT NULL COMMENT '实体类型：INDIVIDUAL/ORGANIZATION',
  `name`            VARCHAR(256)  NOT NULL COMMENT '名称（原始语言）',
  `name_en`         VARCHAR(256)  DEFAULT NULL COMMENT '英文名称',
  `gender`          VARCHAR(8)    DEFAULT NULL COMMENT '性别',
  `nationality`     VARCHAR(64)   DEFAULT NULL COMMENT '国籍',
  `date_of_birth`   VARCHAR(32)   DEFAULT NULL COMMENT '出生日期',
  `place_of_birth`  VARCHAR(256)  DEFAULT NULL COMMENT '出生地',
  `remarks`         TEXT          DEFAULT NULL COMMENT '备注说明',
  `list_date`       DATE          DEFAULT NULL COMMENT '列入名单日期',
  `effective_date`  DATE          DEFAULT NULL COMMENT '生效日期',
  `expiry_date`     DATE          DEFAULT NULL COMMENT '失效日期',
  `status`          VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/INACTIVE',
  `created_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_source_id` (`source_id`),
  KEY `idx_name` (`name`(100)),
  KEY `idx_name_en` (`name_en`(100)),
  KEY `idx_external_id` (`external_id`),
  KEY `idx_status` (`status`),
  CONSTRAINT `fk_wl_source` FOREIGN KEY (`source_id`) REFERENCES `t_watchlist_source` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='制裁名单主表';

-- ---------------------------------------------------------------------------
-- 3.3 名单别名表
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_watchlist_alias`;
CREATE TABLE `t_watchlist_alias` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `watchlist_id`  BIGINT        NOT NULL COMMENT '名单条目ID',
  `alias_name`    VARCHAR(256)  NOT NULL COMMENT '别名',
  `alias_type`    VARCHAR(16)   NOT NULL COMMENT '类型：ORIGINAL/ALIAS/AKA/FKA',
  `language`      VARCHAR(16)   DEFAULT NULL COMMENT '语言代码',
  `created_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_watchlist_id` (`watchlist_id`),
  KEY `idx_alias_name` (`alias_name`(100)),
  CONSTRAINT `fk_wla_watchlist` FOREIGN KEY (`watchlist_id`) REFERENCES `t_watchlist` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='名单别名表';

-- ---------------------------------------------------------------------------
-- 3.4 名单证件表
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_watchlist_identity`;
CREATE TABLE `t_watchlist_identity` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `watchlist_id`    BIGINT        NOT NULL COMMENT '名单条目ID',
  `id_type`         VARCHAR(32)   NOT NULL COMMENT '证件类型',
  `id_number`       VARCHAR(128)  NOT NULL COMMENT '证件号码',
  `issuing_country` VARCHAR(64)   DEFAULT NULL COMMENT '签发国家',
  `expiry_date`     DATE          DEFAULT NULL COMMENT '有效期截止日',
  `created_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_watchlist_id` (`watchlist_id`),
  KEY `idx_id_number` (`id_number`),
  CONSTRAINT `fk_wli_watchlist` FOREIGN KEY (`watchlist_id`) REFERENCES `t_watchlist` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='名单证件表';

-- ---------------------------------------------------------------------------
-- 3.5 名单地址表
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_watchlist_address`;
CREATE TABLE `t_watchlist_address` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `watchlist_id`  BIGINT        NOT NULL COMMENT '名单条目ID',
  `address`       VARCHAR(512)  NOT NULL COMMENT '地址',
  `city`          VARCHAR(128)  DEFAULT NULL COMMENT '城市',
  `state_province` VARCHAR(128) DEFAULT NULL COMMENT '省/州',
  `country`       VARCHAR(64)   DEFAULT NULL COMMENT '国家',
  `created_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_watchlist_id` (`watchlist_id`),
  CONSTRAINT `fk_wladdr_watchlist` FOREIGN KEY (`watchlist_id`) REFERENCES `t_watchlist` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='名单地址表';

-- ---------------------------------------------------------------------------
-- 3.6 筛查请求表
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_screening_request`;
CREATE TABLE `t_screening_request` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `request_no`      VARCHAR(32)   NOT NULL COMMENT '请求编号',
  `customer_id`     BIGINT        DEFAULT NULL COMMENT '客户ID',
  `screening_type`  VARCHAR(32)   NOT NULL COMMENT '筛查类型：CUSTOMER_ONBOARD/INFO_CHANGE/TRANSACTION/PERIODIC/BATCH',
  `request_source`  VARCHAR(32)   NOT NULL COMMENT '请求来源：KYC/MONITORING/SCHEDULED/MANUAL',
  `request_data`    TEXT          DEFAULT NULL COMMENT '请求数据JSON',
  `total_scanned`   INT           NOT NULL DEFAULT 0 COMMENT '扫描条目数',
  `total_hit`       INT           NOT NULL DEFAULT 0 COMMENT '命中条目数',
  `status`          VARCHAR(16)   NOT NULL DEFAULT 'PROCESSING' COMMENT '状态：PROCESSING/COMPLETED/FAILED',
  `error_message`   VARCHAR(512)  DEFAULT NULL COMMENT '错误信息',
  `created_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `completed_time`  DATETIME      DEFAULT NULL COMMENT '完成时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_request_no` (`request_no`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_screening_type` (`screening_type`),
  KEY `idx_status` (`status`),
  KEY `idx_created_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='筛查请求表';

-- ---------------------------------------------------------------------------
-- 3.7 筛查结果表
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_screening_result`;
CREATE TABLE `t_screening_result` (
  `id`                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `request_id`        BIGINT        NOT NULL COMMENT '筛查请求ID',
  `customer_id`       BIGINT        DEFAULT NULL COMMENT '客户ID',
  `customer_name`     VARCHAR(128)  DEFAULT NULL COMMENT '客户姓名',
  `customer_id_number` VARCHAR(128) DEFAULT NULL COMMENT '客户证件号',
  `watchlist_entry_id` BIGINT       DEFAULT NULL COMMENT '命中名单条目ID',
  `watchlist_name`    VARCHAR(256)  DEFAULT NULL COMMENT '命中名单姓名',
  `match_score`       DECIMAL(5,2)  NOT NULL COMMENT '匹配得分（0-100）',
  `match_type`        VARCHAR(16)   NOT NULL COMMENT '匹配类型：EXACT/FUZZY/COMPOSITE',
  `match_field`       VARCHAR(64)   DEFAULT NULL COMMENT '命中字段',
  `match_detail`      TEXT          DEFAULT NULL COMMENT '匹配详情JSON',
  `review_status`     VARCHAR(16)   NOT NULL DEFAULT 'PENDING_REVIEW' COMMENT '复核状态：PENDING_REVIEW/CONFIRMED/EXCLUDED/ESCALATED',
  `review_result`     VARCHAR(32)   DEFAULT NULL COMMENT '复核结论',
  `review_reason`     TEXT          DEFAULT NULL COMMENT '复核理由',
  `reviewed_by`       VARCHAR(64)   DEFAULT NULL COMMENT '复核人',
  `reviewed_time`     DATETIME      DEFAULT NULL COMMENT '复核时间',
  `whitelisted`       TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否已加入白名单',
  `created_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_request_id` (`request_id`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_review_status` (`review_status`),
  KEY `idx_match_score` (`match_score`),
  CONSTRAINT `fk_sr_request` FOREIGN KEY (`request_id`) REFERENCES `t_screening_request` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='筛查结果表';

-- ---------------------------------------------------------------------------
-- 3.8 白名单表
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_whitelist`;
CREATE TABLE `t_whitelist` (
  `id`                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `customer_id`       BIGINT        DEFAULT NULL COMMENT '客户ID',
  `customer_name`     VARCHAR(128)  DEFAULT NULL COMMENT '客户姓名',
  `watchlist_entry_id` BIGINT       DEFAULT NULL COMMENT '名单条目ID',
  `watchlist_name`    VARCHAR(256)  DEFAULT NULL COMMENT '名单姓名',
  `exclude_reason`    VARCHAR(512)  NOT NULL COMMENT '排除理由',
  `evidence`          TEXT          DEFAULT NULL COMMENT '排除证据',
  `effective_date`    DATE          NOT NULL COMMENT '生效日期',
  `expiry_date`       DATE          NOT NULL COMMENT '失效日期',
  `approved_by`       VARCHAR(64)   DEFAULT NULL COMMENT '审批人',
  `approved_time`     DATETIME      DEFAULT NULL COMMENT '审批时间',
  `review_status`     VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/EXPIRED/REVOKED',
  `created_by`        VARCHAR(64)   NOT NULL COMMENT '创建人',
  `created_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`        VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
  `updated_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_watchlist_entry_id` (`watchlist_entry_id`),
  KEY `idx_review_status` (`review_status`),
  KEY `idx_expiry_date` (`expiry_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='白名单表';


-- ============================================================================
-- 4. 产品表（Product Tables）
-- ============================================================================

DROP TABLE IF EXISTS `t_product`;
CREATE TABLE `t_product` (
  `id`                      BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `product_code`            VARCHAR(32)   NOT NULL COMMENT '产品编码',
  `product_name`            VARCHAR(256)  NOT NULL COMMENT '产品名称',
  `product_type`            VARCHAR(32)   NOT NULL COMMENT '产品类型：LIFE/PROPERTY/HEALTH/ACCIDENT/ANNUITY',
  `product_sub_type`        VARCHAR(64)   DEFAULT NULL COMMENT '产品子类型',
  `payment_mode`            VARCHAR(16)   DEFAULT NULL COMMENT '缴费方式：LUMP_SUM/PERIODIC/FLEXIBLE',
  `has_cash_value`          TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否有现金价值',
  `has_investment_feature`  TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否有投资功能',
  `surrender_flexibility`   VARCHAR(16)   DEFAULT NULL COMMENT '退保灵活性：LOW/MEDIUM/HIGH',
  `beneficiary_changeable`  TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '受益人是否可变更',
  `risk_level`              VARCHAR(16)   NOT NULL DEFAULT 'LOW' COMMENT '洗钱风险等级：LOW/MEDIUM/HIGH',
  `risk_score`              INT           NOT NULL DEFAULT 0 COMMENT '风险评分',
  `risk_factors`            TEXT          DEFAULT NULL COMMENT '风险因素说明JSON',
  `status`                  VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/INACTIVE',
  `effective_date`          DATE          DEFAULT NULL COMMENT '生效日期',
  `expiry_date`             DATE          DEFAULT NULL COMMENT '失效日期',
  `created_by`              VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
  `created_time`            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`              VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
  `updated_time`            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_code` (`product_code`),
  KEY `idx_product_type` (`product_type`),
  KEY `idx_risk_level` (`risk_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品主表';

DROP TABLE IF EXISTS `t_product_risk_assessment`;
CREATE TABLE `t_product_risk_assessment` (
  `id`                      BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `product_id`              BIGINT        NOT NULL COMMENT '产品ID',
  `assessment_date`         DATE          NOT NULL COMMENT '评估日期',
  `assessor`                VARCHAR(64)   NOT NULL COMMENT '评估人',
  `client_group_score`      INT           DEFAULT NULL COMMENT '客户群体风险得分',
  `payment_mode_score`      INT           DEFAULT NULL COMMENT '缴费方式风险得分',
  `product_structure_score` INT           DEFAULT NULL COMMENT '产品结构风险得分',
  `surrender_score`         INT           DEFAULT NULL COMMENT '退保灵活性得分',
  `beneficiary_score`       INT           DEFAULT NULL COMMENT '受益人安排得分',
  `channel_score`           INT           DEFAULT NULL COMMENT '销售渠道得分',
  `total_score`             INT           NOT NULL COMMENT '综合评分',
  `risk_level`              VARCHAR(16)   NOT NULL COMMENT '风险等级',
  `assessment_result`       TEXT          DEFAULT NULL COMMENT '评估结论',
  `approved_by`             VARCHAR(64)   DEFAULT NULL COMMENT '审批人',
  `approved_time`           DATETIME      DEFAULT NULL COMMENT '审批时间',
  `status`                  VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/APPROVED/REJECTED',
  `created_by`              VARCHAR(64)   NOT NULL COMMENT '创建人',
  `created_time`            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`              VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
  `updated_time`            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_product_id` (`product_id`),
  CONSTRAINT `fk_pra_product` FOREIGN KEY (`product_id`) REFERENCES `t_product` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品风险评估表';

-- ============================================================================
-- 5. 保单/交易表（Policy/Transaction Tables）
-- ============================================================================

DROP TABLE IF EXISTS `t_policy`;
CREATE TABLE `t_policy` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `policy_no`       VARCHAR(32)   NOT NULL COMMENT '保单号',
  `customer_id`     BIGINT        NOT NULL COMMENT '投保人客户ID',
  `product_id`      BIGINT        NOT NULL COMMENT '产品ID',
  `sum_insured`     DECIMAL(18,2) NOT NULL COMMENT '保额',
  `premium`         DECIMAL(18,2) NOT NULL COMMENT '保费',
  `payment_mode`    VARCHAR(16)   NOT NULL COMMENT '缴费方式',
  `effective_date`  DATE          NOT NULL COMMENT '保单生效日期',
  `expiry_date`     DATE          NOT NULL COMMENT '保单到期日期',
  `policy_status`   VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '保单状态：ACTIVE/LAPSED/SURRENDERED/EXPIRED/CLAIMED',
  `agent_code`      VARCHAR(32)   DEFAULT NULL COMMENT '代理人编码',
  `channel`         VARCHAR(16)   NOT NULL COMMENT '销售渠道：DIRECT/AGENCY/BANK_INSURANCE/ONLINE',
  `beneficiary_info` JSON         DEFAULT NULL COMMENT '受益人信息JSON',
  `insured_name`    VARCHAR(128)  DEFAULT NULL COMMENT '被保险人姓名',
  `insured_id_type` VARCHAR(32)   DEFAULT NULL COMMENT '被保险人证件类型',
  `insured_id_number` VARCHAR(128) DEFAULT NULL COMMENT '被保险人证件号（加密）',
  `remark`          VARCHAR(512)  DEFAULT NULL COMMENT '备注',
  `status`          VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '记录状态',
  `created_by`      VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
  `created_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`      VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
  `updated_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_policy_no` (`policy_no`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_policy_status` (`policy_status`),
  KEY `idx_channel` (`channel`),
  CONSTRAINT `fk_policy_customer` FOREIGN KEY (`customer_id`) REFERENCES `t_customer` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_policy_product` FOREIGN KEY (`product_id`) REFERENCES `t_product` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='保单主表';

DROP TABLE IF EXISTS `t_transaction`;
CREATE TABLE `t_transaction` (
  `id`                  BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `transaction_no`      VARCHAR(32)     NOT NULL COMMENT '交易流水号',
  `policy_id`           BIGINT          DEFAULT NULL COMMENT '保单ID',
  `customer_id`         BIGINT          NOT NULL COMMENT '客户ID',
  `transaction_type`    VARCHAR(32)     NOT NULL COMMENT '交易类型：PREMIUM/SURRENDER/CLAIM/LOAN/REPAYMENT/PARTIAL_WITHDRAWAL',
  `amount`              DECIMAL(18,2)   NOT NULL COMMENT '交易金额',
  `currency`            VARCHAR(8)      NOT NULL DEFAULT 'CNY' COMMENT '币种',
  `payment_method`      VARCHAR(16)     DEFAULT NULL COMMENT '支付方式：CASH/TRANSFER/BANK_CARD/CHECK/OTHER',
  `channel`             VARCHAR(32)     DEFAULT NULL COMMENT '交易渠道',
  `counterparty_name`   VARCHAR(128)    DEFAULT NULL COMMENT '交易对手姓名',
  `counterparty_account` VARCHAR(64)    DEFAULT NULL COMMENT '交易对手账号',
  `counterparty_bank`   VARCHAR(128)    DEFAULT NULL COMMENT '交易对手开户行',
  `is_cross_border`     TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否跨境交易',
  `transaction_time`    DATETIME        NOT NULL COMMENT '交易时间',
  `remark`              VARCHAR(512)    DEFAULT NULL COMMENT '备注',
  `status`              VARCHAR(16)     NOT NULL DEFAULT 'SUCCESS' COMMENT '交易状态：SUCCESS/FAILED/PENDING/REVERSED',
  `source_system`       VARCHAR(32)     DEFAULT NULL COMMENT '来源系统',
  `created_time`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_transaction_no` (`transaction_no`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_policy_id` (`policy_id`),
  KEY `idx_transaction_type` (`transaction_type`),
  KEY `idx_transaction_time` (`transaction_time`),
  KEY `idx_amount` (`amount`),
  KEY `idx_status` (`status`),
  KEY `idx_customer_time` (`customer_id`, `transaction_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交易流水表';

DROP TABLE IF EXISTS `t_transaction_daily_summary`;
CREATE TABLE `t_transaction_daily_summary` (
  `id`                BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `customer_id`       BIGINT          NOT NULL COMMENT '客户ID',
  `summary_date`      DATE            NOT NULL COMMENT '汇总日期',
  `transaction_type`  VARCHAR(32)     NOT NULL COMMENT '交易类型',
  `payment_method`    VARCHAR(16)     DEFAULT NULL COMMENT '支付方式',
  `is_cross_border`   TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否跨境',
  `total_amount`      DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT '当日累计金额',
  `transaction_count` INT             NOT NULL DEFAULT 0 COMMENT '当日交易笔数',
  `large_txn_flag`    TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否触发大额标记',
  `created_time`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_daily_summary` (`customer_id`, `summary_date`, `transaction_type`, `payment_method`, `is_cross_border`),
  KEY `idx_summary_date` (`summary_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交易日汇总表';

-- ============================================================================
-- 6. 规则引擎表（Rule Engine Tables）
-- ============================================================================

DROP TABLE IF EXISTS `t_rule_definition`;
CREATE TABLE `t_rule_definition` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `rule_code`       VARCHAR(32)   NOT NULL COMMENT '规则编码（如 SR-001）',
  `rule_name`       VARCHAR(256)  NOT NULL COMMENT '规则名称',
  `rule_category`   VARCHAR(32)   NOT NULL COMMENT '规则类别：LARGE_TXN/SUSPICIOUS/VELOCITY/THRESHOLD/CORRELATION',
  `description`     TEXT          DEFAULT NULL COMMENT '规则描述',
  `risk_weight`     INT           NOT NULL DEFAULT 50 COMMENT '风险权重（0-100）',
  `priority`        INT           NOT NULL DEFAULT 0 COMMENT '优先级（越大越优先）',
  `status`          VARCHAR(16)   NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
  `effective_date`  DATE          DEFAULT NULL COMMENT '生效日期',
  `expiry_date`     DATE          DEFAULT NULL COMMENT '失效日期',
  `config_json`     TEXT          DEFAULT NULL COMMENT '规则配置JSON（条件、阈值、参数）',
  `drools_drl`      TEXT          DEFAULT NULL COMMENT 'Drools规则脚本（如使用Drools）',
  `created_by`      VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
  `created_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`      VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
  `updated_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rule_code` (`rule_code`),
  KEY `idx_rule_category` (`rule_category`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='规则定义表';

DROP TABLE IF EXISTS `t_rule_version`;
CREATE TABLE `t_rule_version` (
  `id`                  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `rule_id`             BIGINT        NOT NULL COMMENT '规则ID',
  `version_no`          INT           NOT NULL COMMENT '版本号',
  `config_snapshot`     TEXT          DEFAULT NULL COMMENT '规则配置快照',
  `change_description`  VARCHAR(512)  DEFAULT NULL COMMENT '变更说明',
  `changed_by`          VARCHAR(64)   NOT NULL COMMENT '变更人',
  `changed_time`        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '变更时间',
  PRIMARY KEY (`id`),
  KEY `idx_rule_id` (`rule_id`),
  CONSTRAINT `fk_rv_rule` FOREIGN KEY (`rule_id`) REFERENCES `t_rule_definition` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='规则版本历史表';

DROP TABLE IF EXISTS `t_rule_execution_log`;
CREATE TABLE `t_rule_execution_log` (
  `id`                BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `rule_id`           BIGINT          NOT NULL COMMENT '规则ID',
  `rule_code`         VARCHAR(32)     NOT NULL COMMENT '规则编码',
  `transaction_id`    BIGINT          DEFAULT NULL COMMENT '交易ID',
  `customer_id`       BIGINT          DEFAULT NULL COMMENT '客户ID',
  `execution_time`    DATETIME        NOT NULL COMMENT '执行时间',
  `match_result`      TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否命中',
  `match_score`       DECIMAL(5,2)    DEFAULT NULL COMMENT '匹配得分',
  `execution_detail`  TEXT            DEFAULT NULL COMMENT '执行详情JSON',
  `duration_ms`       BIGINT          DEFAULT NULL COMMENT '执行耗时（毫秒）',
  `created_time`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_rule_id` (`rule_id`),
  KEY `idx_transaction_id` (`transaction_id`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_execution_time` (`execution_time`),
  KEY `idx_match_result` (`match_result`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='规则执行日志表';

-- ============================================================================
-- 7. 预警/案件表（Alert/Case Tables）
-- ============================================================================

DROP TABLE IF EXISTS `t_alert`;
CREATE TABLE `t_alert` (
  `id`                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `alert_no`          VARCHAR(32)   NOT NULL COMMENT '预警编号',
  `customer_id`       BIGINT        NOT NULL COMMENT '客户ID',
  `customer_name`     VARCHAR(128)  DEFAULT NULL COMMENT '客户姓名（冗余）',
  `alert_type`        VARCHAR(32)   NOT NULL COMMENT '预警类型：LARGE_TXN/SUSPICIOUS/SANCTIONS_HIT/PEP_HIT/MANUAL',
  `risk_score`        INT           NOT NULL DEFAULT 0 COMMENT '风险评分',
  `risk_level`        VARCHAR(16)   NOT NULL COMMENT '风险等级：LOW/MEDIUM/HIGH/CRITICAL',
  `source_rule_codes` VARCHAR(512)  DEFAULT NULL COMMENT '触发规则编码（逗号分隔）',
  `alert_summary`     TEXT          DEFAULT NULL COMMENT '预警摘要',
  `status`            VARCHAR(16)   NOT NULL DEFAULT 'NEW' COMMENT '状态：NEW/ASSIGNED/PROCESSING/CONFIRMED/EXCLUDED/ESCALATED',
  `assigned_to`       BIGINT        DEFAULT NULL COMMENT '分配给用户ID',
  `assigned_time`     DATETIME      DEFAULT NULL COMMENT '分配时间',
  `process_result`    VARCHAR(32)   DEFAULT NULL COMMENT '处理结果：CONFIRMED_SUSPICIOUS/EXCLUDED/ESCALATED',
  `process_remark`    TEXT          DEFAULT NULL COMMENT '处理备注',
  `process_time`      DATETIME      DEFAULT NULL COMMENT '处理时间',
  `deduplicate_key`   VARCHAR(64)   DEFAULT NULL COMMENT '去重键（防止重复预警）',
  `related_transaction_ids` TEXT     DEFAULT NULL COMMENT '关联交易ID列表',
  `created_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_alert_no` (`alert_no`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_status` (`status`),
  KEY `idx_risk_level` (`risk_level`),
  KEY `idx_assigned_to` (`assigned_to`),
  KEY `idx_created_time` (`created_time`),
  KEY `idx_alert_type` (`alert_type`),
  KEY `idx_deduplicate_key` (`deduplicate_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预警表';

DROP TABLE IF EXISTS `t_alert_rule_detail`;
CREATE TABLE `t_alert_rule_detail` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `alert_id`      BIGINT        NOT NULL COMMENT '预警ID',
  `rule_id`       BIGINT        DEFAULT NULL COMMENT '规则ID',
  `rule_code`     VARCHAR(32)   NOT NULL COMMENT '规则编码',
  `rule_name`     VARCHAR(256)  DEFAULT NULL COMMENT '规则名称',
  `match_score`   DECIMAL(5,2)  DEFAULT NULL COMMENT '匹配得分',
  `match_detail`  TEXT          DEFAULT NULL COMMENT '匹配详情JSON',
  `created_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_alert_id` (`alert_id`),
  CONSTRAINT `fk_ard_alert` FOREIGN KEY (`alert_id`) REFERENCES `t_alert` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预警规则明细表';

DROP TABLE IF EXISTS `t_alert_assignment_log`;
CREATE TABLE `t_alert_assignment_log` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `alert_id`      BIGINT        NOT NULL COMMENT '预警ID',
  `from_user_id`  BIGINT        DEFAULT NULL COMMENT '原处理人ID',
  `to_user_id`    BIGINT        NOT NULL COMMENT '新处理人ID',
  `assign_type`   VARCHAR(16)   NOT NULL COMMENT '分配类型：AUTO/MANUAL/ESCALATION',
  `assign_reason` VARCHAR(512)  DEFAULT NULL COMMENT '分配原因',
  `assigned_by`   VARCHAR(64)   DEFAULT NULL COMMENT '操作人',
  `assigned_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '分配时间',
  PRIMARY KEY (`id`),
  KEY `idx_alert_id` (`alert_id`),
  CONSTRAINT `fk_aal_alert` FOREIGN KEY (`alert_id`) REFERENCES `t_alert` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预警分配日志表';

DROP TABLE IF EXISTS `t_case`;
CREATE TABLE `t_case` (
  `id`                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `case_no`           VARCHAR(32)   NOT NULL COMMENT '案件编号',
  `alert_id`          BIGINT        DEFAULT NULL COMMENT '来源预警ID',
  `customer_id`       BIGINT        NOT NULL COMMENT '主要涉案客户ID',
  `customer_name`     VARCHAR(128)  DEFAULT NULL COMMENT '客户姓名',
  `case_status`       VARCHAR(32)   NOT NULL DEFAULT 'DRAFT' COMMENT '案件状态：DRAFT/INVESTIGATING/PENDING_APPROVAL/SUBMITTED/CLOSED',
  `case_type`         VARCHAR(32)   DEFAULT NULL COMMENT '案件类型',
  `priority`          INT           NOT NULL DEFAULT 0 COMMENT '优先级',
  `summary`           TEXT          DEFAULT NULL COMMENT '案件摘要',
  `investigator_id`   BIGINT        DEFAULT NULL COMMENT '调查人ID',
  `reviewer_id`       BIGINT        DEFAULT NULL COMMENT '审核人ID',
  `approver_id`       BIGINT        DEFAULT NULL COMMENT '签发人ID',
  `submit_time`       DATETIME      DEFAULT NULL COMMENT '报送时间',
  `close_time`        DATETIME      DEFAULT NULL COMMENT '结案时间',
  `close_reason`      VARCHAR(512)  DEFAULT NULL COMMENT '结案原因',
  `created_by`        VARCHAR(64)   NOT NULL COMMENT '创建人',
  `created_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`        VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
  `updated_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_case_no` (`case_no`),
  KEY `idx_alert_id` (`alert_id`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_case_status` (`case_status`),
  KEY `idx_investigator_id` (`investigator_id`),
  KEY `idx_created_time` (`created_time`),
  CONSTRAINT `fk_case_alert` FOREIGN KEY (`alert_id`) REFERENCES `t_alert` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='案件表';

DROP TABLE IF EXISTS `t_case_status_log`;
CREATE TABLE `t_case_status_log` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `case_id`     BIGINT        NOT NULL COMMENT '案件ID',
  `from_status` VARCHAR(32)   DEFAULT NULL COMMENT '原状态',
  `to_status`   VARCHAR(32)   NOT NULL COMMENT '新状态',
  `remark`      TEXT          DEFAULT NULL COMMENT '备注',
  `changed_by`  VARCHAR(64)   NOT NULL COMMENT '操作人',
  `changed_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '变更时间',
  PRIMARY KEY (`id`),
  KEY `idx_case_id` (`case_id`),
  CONSTRAINT `fk_csl_case` FOREIGN KEY (`case_id`) REFERENCES `t_case` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='案件状态变更日志表';

DROP TABLE IF EXISTS `t_case_investigation`;
CREATE TABLE `t_case_investigation` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `case_id`         BIGINT        NOT NULL COMMENT '案件ID',
  `content`         TEXT          NOT NULL COMMENT '调查内容',
  `conclusion`      VARCHAR(32)   DEFAULT NULL COMMENT '调查结论',
  `investigator_id` BIGINT        NOT NULL COMMENT '调查人ID',
  `created_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_case_id` (`case_id`),
  CONSTRAINT `fk_ci_case` FOREIGN KEY (`case_id`) REFERENCES `t_case` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='案件调查记录表';

DROP TABLE IF EXISTS `t_case_attachment`;
CREATE TABLE `t_case_attachment` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `case_id`       BIGINT        NOT NULL COMMENT '案件ID',
  `file_name`     VARCHAR(255)  NOT NULL COMMENT '文件名',
  `file_path`     VARCHAR(512)  NOT NULL COMMENT '文件路径',
  `file_size`     BIGINT        DEFAULT NULL COMMENT '文件大小',
  `file_type`     VARCHAR(32)   DEFAULT NULL COMMENT '文件类型',
  `upload_by`     BIGINT        NOT NULL COMMENT '上传人ID',
  `upload_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  PRIMARY KEY (`id`),
  KEY `idx_case_id` (`case_id`),
  CONSTRAINT `fk_ca_case` FOREIGN KEY (`case_id`) REFERENCES `t_case` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='案件附件表';

DROP TABLE IF EXISTS `t_str_report`;
CREATE TABLE `t_str_report` (
  `id`                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `report_no`         VARCHAR(32)   NOT NULL COMMENT '报告编号',
  `case_id`           BIGINT        DEFAULT NULL COMMENT '关联案件ID',
  `customer_id`       BIGINT        NOT NULL COMMENT '主要涉案客户ID',
  `report_type`       VARCHAR(16)   NOT NULL DEFAULT 'NORMAL' COMMENT '报告类型：NORMAL-常规 URGENT-紧急',
  `report_status`     VARCHAR(32)   NOT NULL DEFAULT 'DRAFT' COMMENT '报告状态：DRAFT/PENDING_REVIEW/APPROVED/REJECTED/SUBMITTED',
  `report_content`    TEXT          DEFAULT NULL COMMENT '报告正文',
  `analysis_opinion`  TEXT          DEFAULT NULL COMMENT '分析意见',
  `measures_taken`    TEXT          DEFAULT NULL COMMENT '已采取措施',
  `writer_id`         BIGINT        DEFAULT NULL COMMENT '撰写人ID',
  `writer_time`       DATETIME      DEFAULT NULL COMMENT '撰写时间',
  `reviewer_id`       BIGINT        DEFAULT NULL COMMENT '审核人ID',
  `reviewer_opinion`  TEXT          DEFAULT NULL COMMENT '审核意见',
  `reviewer_time`     DATETIME      DEFAULT NULL COMMENT '审核时间',
  `approver_id`       BIGINT        DEFAULT NULL COMMENT '签发人ID',
  `approver_opinion`  TEXT          DEFAULT NULL COMMENT '签发意见',
  `approver_time`     DATETIME      DEFAULT NULL COMMENT '签发时间',
  `submit_time`       DATETIME      DEFAULT NULL COMMENT '报送时间',
  `submit_result`     VARCHAR(256)  DEFAULT NULL COMMENT '报送结果',
  `created_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_report_no` (`report_no`),
  KEY `idx_case_id` (`case_id`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_report_status` (`report_status`),
  KEY `idx_created_time` (`created_time`),
  CONSTRAINT `fk_str_case` FOREIGN KEY (`case_id`) REFERENCES `t_case` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='可疑交易报告表';

-- ============================================================================
-- 8. 报送表（Reporting Tables）
-- ============================================================================

DROP TABLE IF EXISTS `t_large_txn_report`;
CREATE TABLE `t_large_txn_report` (
  `id`                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `report_no`         VARCHAR(32)   NOT NULL COMMENT '报告编号',
  `customer_id`       BIGINT        NOT NULL COMMENT '客户ID',
  `customer_name`     VARCHAR(128)  DEFAULT NULL COMMENT '客户姓名',
  `transaction_id`    BIGINT        DEFAULT NULL COMMENT '触发交易ID',
  `report_date`       DATE          NOT NULL COMMENT '报告日期',
  `transaction_time`  DATETIME      NOT NULL COMMENT '交易时间',
  `transaction_type`  VARCHAR(32)   NOT NULL COMMENT '交易类型',
  `amount`            DECIMAL(18,2) NOT NULL COMMENT '交易金额',
  `currency`          VARCHAR(8)    NOT NULL DEFAULT 'CNY' COMMENT '币种',
  `payment_method`    VARCHAR(16)   DEFAULT NULL COMMENT '支付方式',
  `counterparty_info` TEXT          DEFAULT NULL COMMENT '交易对手信息',
  `report_status`     VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/REVIEWED/SUBMITTED/FAILED/RESUBMITTED',
  `reviewed_by`       VARCHAR(64)   DEFAULT NULL COMMENT '审核人',
  `reviewed_time`     DATETIME      DEFAULT NULL COMMENT '审核时间',
  `submitted_by`      VARCHAR(64)   DEFAULT NULL COMMENT '报送人',
  `submitted_time`    DATETIME      DEFAULT NULL COMMENT '报送时间',
  `xml_content`       LONGTEXT      DEFAULT NULL COMMENT 'XML报送内容',
  `submit_response`   TEXT          DEFAULT NULL COMMENT '报送响应',
  `created_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_report_no` (`report_no`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_report_status` (`report_status`),
  KEY `idx_report_date` (`report_date`),
  KEY `idx_created_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='大额交易报告表';

DROP TABLE IF EXISTS `t_report_submit_log`;
CREATE TABLE `t_report_submit_log` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `report_type`     VARCHAR(32)   NOT NULL COMMENT '报告类型：LARGE_TXN/SUSPICIOUS/ANNUAL',
  `report_id`       BIGINT        NOT NULL COMMENT '报告ID',
  `submit_time`     DATETIME      NOT NULL COMMENT '报送时间',
  `submit_status`   VARCHAR(16)   NOT NULL COMMENT '报送状态：SUCCESS/FAILED',
  `request_data`    TEXT          DEFAULT NULL COMMENT '请求数据',
  `response_data`   TEXT          DEFAULT NULL COMMENT '响应数据',
  `error_message`   VARCHAR(512)  DEFAULT NULL COMMENT '错误信息',
  `retry_count`     INT           NOT NULL DEFAULT 0 COMMENT '重试次数',
  `max_retries`     INT           NOT NULL DEFAULT 3 COMMENT '最大重试次数',
  `next_retry_time` DATETIME      DEFAULT NULL COMMENT '下次重试时间',
  `created_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_report_type` (`report_type`),
  KEY `idx_report_id` (`report_id`),
  KEY `idx_submit_status` (`submit_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报告报送日志表';

DROP TABLE IF EXISTS `t_annual_report`;
CREATE TABLE `t_annual_report` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `report_year`     INT           NOT NULL COMMENT '报告年度',
  `report_type`     VARCHAR(32)   NOT NULL COMMENT '报告类型：ANNUAL_AML/SELF_ASSESSMENT',
  `report_status`   VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/APPROVED/SUBMITTED',
  `file_path`       VARCHAR(512)  DEFAULT NULL COMMENT '报告文件路径',
  `generated_by`    VARCHAR(64)   DEFAULT NULL COMMENT '生成人',
  `generated_time`  DATETIME      DEFAULT NULL COMMENT '生成时间',
  `approved_by`     VARCHAR(64)   DEFAULT NULL COMMENT '审批人',
  `approved_time`   DATETIME      DEFAULT NULL COMMENT '审批时间',
  `submitted_by`    VARCHAR(64)   DEFAULT NULL COMMENT '报送人',
  `submitted_time`  DATETIME      DEFAULT NULL COMMENT '报送时间',
  `created_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_report_year` (`report_year`),
  KEY `idx_report_type` (`report_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='年度报告表';

-- ============================================================================
-- 9. 自评估表（Self-Assessment Tables）
-- ============================================================================

DROP TABLE IF EXISTS `t_self_assessment`;
CREATE TABLE `t_self_assessment` (
  `id`                        BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `assessment_year`           INT           NOT NULL COMMENT '评估年度',
  `assessment_period`         VARCHAR(32)   DEFAULT NULL COMMENT '评估周期：ANNUAL/QUARTERLY',
  `assessment_status`         VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/IN_PROGRESS/COMPLETED/APPROVED',
  `assessor_id`               BIGINT        DEFAULT NULL COMMENT '评估人ID',
  `inherent_risk_score`       INT           DEFAULT NULL COMMENT '固有风险评分',
  `control_effectiveness_score` INT         DEFAULT NULL COMMENT '控制有效性评分',
  `overall_score`             INT           DEFAULT NULL COMMENT '综合评分',
  `overall_risk_level`        VARCHAR(16)   DEFAULT NULL COMMENT '综合风险等级',
  `conclusion`                TEXT          DEFAULT NULL COMMENT '评估结论',
  `approved_by`               VARCHAR(64)   DEFAULT NULL COMMENT '审批人',
  `approved_time`             DATETIME      DEFAULT NULL COMMENT '审批时间',
  `created_time`              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_assessment_year` (`assessment_year`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='风险自评估表';

DROP TABLE IF EXISTS `t_assessment_indicator`;
CREATE TABLE `t_assessment_indicator` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `indicator_code`  VARCHAR(64)   NOT NULL COMMENT '指标编码',
  `indicator_name`  VARCHAR(256)  NOT NULL COMMENT '指标名称',
  `category`        VARCHAR(32)   NOT NULL COMMENT '类别：INHERENT_RISK-固有风险 CONTROL_EFFECTIVENESS-控制有效性',
  `dimension`       VARCHAR(64)   DEFAULT NULL COMMENT '维度',
  `weight`          DECIMAL(5,2)  NOT NULL COMMENT '权重（%）',
  `scoring_criteria` TEXT         DEFAULT NULL COMMENT '评分标准',
  `max_score`       INT           NOT NULL COMMENT '满分',
  `status`          VARCHAR(16)   NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
  `created_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_indicator_code` (`indicator_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='自评估指标表';

DROP TABLE IF EXISTS `t_assessment_score`;
CREATE TABLE `t_assessment_score` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `assessment_id`   BIGINT        NOT NULL COMMENT '自评估ID',
  `indicator_id`    BIGINT        NOT NULL COMMENT '指标ID',
  `raw_value`       DECIMAL(12,2) DEFAULT NULL COMMENT '原始值',
  `score`           INT           DEFAULT NULL COMMENT '得分',
  `evidence`        TEXT          DEFAULT NULL COMMENT '评分依据',
  `data_source`     VARCHAR(128)  DEFAULT NULL COMMENT '数据来源',
  `remark`          VARCHAR(512)  DEFAULT NULL COMMENT '备注',
  `scored_by`       VARCHAR(64)   DEFAULT NULL COMMENT '评分人',
  `scored_time`     DATETIME      DEFAULT NULL COMMENT '评分时间',
  `created_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_assessment_id` (`assessment_id`),
  KEY `idx_indicator_id` (`indicator_id`),
  CONSTRAINT `fk_as_assessment` FOREIGN KEY (`assessment_id`) REFERENCES `t_self_assessment` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_as_indicator` FOREIGN KEY (`indicator_id`) REFERENCES `t_assessment_indicator` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='自评估评分明细表';

DROP TABLE IF EXISTS `t_rectification_task`;
CREATE TABLE `t_rectification_task` (
  `id`                    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `assessment_id`         BIGINT        NOT NULL COMMENT '自评估ID',
  `issue_description`     TEXT          NOT NULL COMMENT '问题描述',
  `severity`              VARCHAR(16)   NOT NULL COMMENT '严重程度：HIGH/MEDIUM/LOW',
  `responsible_dept`      VARCHAR(128)  DEFAULT NULL COMMENT '责任部门',
  `responsible_person`    VARCHAR(64)   DEFAULT NULL COMMENT '责任人',
  `deadline`              DATE          NOT NULL COMMENT '整改截止日期',
  `status`                VARCHAR(16)   NOT NULL DEFAULT 'OPEN' COMMENT '状态：OPEN/IN_PROGRESS/COMPLETED/OVERDUE',
  `completion_evidence`   TEXT          DEFAULT NULL COMMENT '完成证据',
  `completed_time`        DATETIME      DEFAULT NULL COMMENT '完成时间',
  `verified_by`           VARCHAR(64)   DEFAULT NULL COMMENT '验证人',
  `verified_time`         DATETIME      DEFAULT NULL COMMENT '验证时间',
  `created_time`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_assessment_id` (`assessment_id`),
  KEY `idx_status` (`status`),
  KEY `idx_deadline` (`deadline`),
  CONSTRAINT `fk_rt_assessment` FOREIGN KEY (`assessment_id`) REFERENCES `t_self_assessment` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='整改任务表';

-- ============================================================================
-- 完成
-- ============================================================================
SET FOREIGN_KEY_CHECKS = 1;

-- 表统计：共 34 张表
-- 系统管理：t_user, t_role, t_user_role, t_permission, t_role_permission,
--           t_sys_dict, t_sys_dict_item, t_sys_param, t_audit_log, t_notification, t_file_upload (11)
-- 客户/KYC：t_customer, t_customer_beneficial_owner, t_verification_record,
--           t_customer_risk_rating_log, t_pep_list, t_pep_relation (6)
-- 名单筛查：t_watchlist_source, t_watchlist, t_watchlist_alias, t_watchlist_identity,
--           t_watchlist_address, t_screening_request, t_screening_result, t_whitelist (8)
-- 产品：    t_product, t_product_risk_assessment (2)
-- 保单交易：t_policy, t_transaction, t_transaction_daily_summary (3)
-- 规则引擎：t_rule_definition, t_rule_version, t_rule_execution_log (3)
-- 预警案件：t_alert, t_alert_rule_detail, t_alert_assignment_log, t_case,
--           t_case_status_log, t_case_investigation, t_case_attachment, t_str_report (8)
-- 报送：    t_large_txn_report, t_report_submit_log, t_annual_report (3)
-- 自评估：  t_self_assessment, t_assessment_indicator, t_assessment_score, t_rectification_task (4)
