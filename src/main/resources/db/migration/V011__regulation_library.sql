-- ============================================================================
-- 项目：保险公司反洗钱管理系统（AML System）
-- 文件：V011__regulation_library.sql
-- 描述：法规及资料库，支持知识库分类、全文检索与监管/行业动态展示
-- ============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. 资料分类表
-- ---------------------------------------------------------------------------
CREATE TABLE `t_regulation_category` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `category_code`  VARCHAR(64)  NOT NULL COMMENT '分类编码',
  `category_name`  VARCHAR(128) NOT NULL COMMENT '分类名称',
  `category_type`  VARCHAR(32)  NOT NULL DEFAULT 'GENERAL' COMMENT '分类类型：REGULATION/POLICY/TRAINING/UPDATE/GENERAL',
  `parent_id`      BIGINT       DEFAULT 0 COMMENT '父分类ID',
  `sort_order`     INT          NOT NULL DEFAULT 0 COMMENT '排序',
  `status`         VARCHAR(32)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
  `description`    VARCHAR(512) DEFAULT NULL COMMENT '分类说明',
  `created_by`     VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `created_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`     VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
  `updated_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_regulation_category_code` (`category_code`),
  KEY `idx_regulation_category_type` (`category_type`),
  KEY `idx_regulation_category_status` (`status`),
  KEY `idx_regulation_category_sort` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='法规及资料库分类表';

-- ---------------------------------------------------------------------------
-- 2. 法规资料文档表
-- ---------------------------------------------------------------------------
CREATE TABLE `t_regulation_document` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `doc_code`       VARCHAR(64)   NOT NULL COMMENT '资料编码',
  `title`          VARCHAR(256)  NOT NULL COMMENT '资料标题',
  `doc_type`       VARCHAR(32)   NOT NULL COMMENT '资料类型：REGULATION/POLICY/TRAINING/REGULATORY_UPDATE/INDUSTRY_UPDATE',
  `category_id`    BIGINT        DEFAULT NULL COMMENT '分类ID',
  `category_name`  VARCHAR(128)  DEFAULT NULL COMMENT '分类名称冗余',
  `source_type`    VARCHAR(32)   NOT NULL DEFAULT 'INTERNAL' COMMENT '来源类型：REGULATOR/INDUSTRY/INTERNAL',
  `source_org`     VARCHAR(128)  DEFAULT NULL COMMENT '发布机构或来源',
  `publish_date`   DATE          DEFAULT NULL COMMENT '发布日期',
  `effective_date` DATE          DEFAULT NULL COMMENT '生效日期',
  `status`         VARCHAR(32)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/PUBLISHED/ARCHIVED',
  `important_flag` TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否重点资料',
  `summary`        VARCHAR(1024) DEFAULT NULL COMMENT '摘要',
  `content`        MEDIUMTEXT    DEFAULT NULL COMMENT '正文内容',
  `tags`           VARCHAR(512)  DEFAULT NULL COMMENT '标签，逗号分隔',
  `reference_url`  VARCHAR(512)  DEFAULT NULL COMMENT '来源链接',
  `attachment_ref` VARCHAR(512)  DEFAULT NULL COMMENT '附件引用',
  `view_count`     INT           NOT NULL DEFAULT 0 COMMENT '查看次数',
  `created_by`     VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
  `created_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`     VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
  `updated_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_regulation_doc_code` (`doc_code`),
  KEY `idx_regulation_doc_type` (`doc_type`),
  KEY `idx_regulation_doc_category` (`category_id`),
  KEY `idx_regulation_doc_status` (`status`),
  KEY `idx_regulation_doc_source_type` (`source_type`),
  KEY `idx_regulation_doc_publish_date` (`publish_date`),
  FULLTEXT KEY `ft_regulation_doc_search` (`title`, `summary`, `content`, `tags`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='法规及资料库文档表';

-- ---------------------------------------------------------------------------
-- 3. 权限与角色授权
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `t_permission` (`permission_code`, `permission_name`, `parent_id`, `type`, `path`, `sort_order`, `icon`, `status`, `created_time`)
VALUES
  ('MENU_REGULATION_LIBRARY', '法规及资料库', 0, 'MENU', '/regulation-library', 14, 'document', 'ENABLED', NOW()),
  ('regulation:view', '法规资料库-查看', 0, 'BUTTON', NULL, 1401, NULL, 'ENABLED', NOW()),
  ('regulation:manage', '法规资料库-管理', 0, 'BUTTON', NULL, 1402, NULL, 'ENABLED', NOW());

INSERT IGNORE INTO `t_role_permission` (`role_id`, `permission_id`, `created_by`, `created_time`)
SELECT r.id, p.id, 'system', NOW()
FROM t_role r, t_permission p
WHERE r.role_code = 'ROLE_ADMIN'
  AND p.permission_code IN ('MENU_REGULATION_LIBRARY', 'regulation:view', 'regulation:manage');

-- ---------------------------------------------------------------------------
-- 4. 预置分类
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `t_regulation_category`
  (`category_code`, `category_name`, `category_type`, `parent_id`, `sort_order`, `status`, `description`, `created_by`, `created_time`, `updated_by`, `updated_time`)
VALUES
  ('REG-LAW', '法律法规', 'REGULATION', 0, 10, 'ENABLED', '国家法律、行政法规、监管规章和规范性文件', 'system', NOW(), 'system', NOW()),
  ('REG-RULE', '监管规则', 'REGULATION', 0, 20, 'ENABLED', '人民银行、金融监管总局等监管要求和问答口径', 'system', NOW(), 'system', NOW()),
  ('POLICY-INTERNAL', '制度文件', 'POLICY', 0, 30, 'ENABLED', '公司反洗钱内控制度、操作规程和岗位手册', 'system', NOW(), 'system', NOW()),
  ('TRAINING-MATERIAL', '培训素材', 'TRAINING', 0, 40, 'ENABLED', '培训课件、案例材料、考试题库和宣传素材', 'system', NOW(), 'system', NOW()),
  ('UPDATE-REGULATORY', '监管动态', 'UPDATE', 0, 50, 'ENABLED', '监管发布、执法处罚、检查重点和工作提示', 'system', NOW(), 'system', NOW()),
  ('UPDATE-INDUSTRY', '行业动态', 'UPDATE', 0, 60, 'ENABLED', '保险行业反洗钱实践、风险提示和案例分析', 'system', NOW(), 'system', NOW());

-- ---------------------------------------------------------------------------
-- 5. 预置法规资料与监管/行业动态样例
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `t_regulation_document`
  (`doc_code`, `title`, `doc_type`, `category_id`, `category_name`, `source_type`, `source_org`, `publish_date`, `effective_date`,
   `status`, `important_flag`, `summary`, `content`, `tags`, `reference_url`, `attachment_ref`, `view_count`, `created_by`, `created_time`, `updated_by`, `updated_time`)
VALUES
  ('AML-KB-REG-001', '中华人民共和国反洗钱法重点条款解读', 'REGULATION',
   (SELECT id FROM t_regulation_category WHERE category_code = 'REG-LAW'), '法律法规', 'REGULATOR', '全国人大/中国人民银行',
   DATE_SUB(CURDATE(), INTERVAL 120 DAY), DATE_SUB(CURDATE(), INTERVAL 90 DAY), 'PUBLISHED', 1,
   '梳理反洗钱义务主体、客户尽职调查、可疑交易报告、风险为本管理、法律责任等重点条款。',
   '本资料用于反洗钱知识库检索，覆盖客户身份识别、交易记录保存、可疑交易报告、风险为本方法、内部控制与监督检查等关键要求。',
   '反洗钱法,客户尽职调查,可疑交易报告,监管要求', NULL, NULL, 18, 'system', NOW(), 'system', NOW()),
  ('AML-KB-REG-002', '金融机构客户尽职调查和资料保存要求', 'REGULATION',
   (SELECT id FROM t_regulation_category WHERE category_code = 'REG-RULE'), '监管规则', 'REGULATOR', '中国人民银行',
   DATE_SUB(CURDATE(), INTERVAL 95 DAY), DATE_SUB(CURDATE(), INTERVAL 70 DAY), 'PUBLISHED', 1,
   '聚焦客户身份识别、受益所有人识别、持续尽职调查和资料保存期限等要求。',
   '保险机构应在客户建立业务关系、风险等级变化、交易异常、身份资料过期等场景开展持续尽职调查，并按要求保存身份资料和交易记录。',
   '客户尽职调查,受益所有人,资料保存,持续识别', NULL, NULL, 23, 'system', NOW(), 'system', NOW()),
  ('AML-KB-POL-001', '保险机构反洗钱内控制度模板', 'POLICY',
   (SELECT id FROM t_regulation_category WHERE category_code = 'POLICY-INTERNAL'), '制度文件', 'INTERNAL', '合规管理部',
   DATE_SUB(CURDATE(), INTERVAL 40 DAY), DATE_SUB(CURDATE(), INTERVAL 30 DAY), 'PUBLISHED', 0,
   '覆盖组织架构、岗位职责、客户风险评级、名单筛查、交易监测、报告报送和审计检查等制度章节。',
   '制度模板用于管理员维护公司内部反洗钱制度库，可与监管法规、业务流程和培训材料建立关联。',
   '制度模板,内控,岗位职责,报告报送', NULL, NULL, 9, 'system', NOW(), 'system', NOW()),
  ('AML-KB-TRN-001', '可疑交易识别与案例培训素材', 'TRAINING',
   (SELECT id FROM t_regulation_category WHERE category_code = 'TRAINING-MATERIAL'), '培训素材', 'INTERNAL', '反洗钱培训中心',
   DATE_SUB(CURDATE(), INTERVAL 25 DAY), NULL, 'PUBLISHED', 0,
   '围绕保险缴费、退保、受益人变更、团伙交易和异常资金流动等场景设计培训案例。',
   '培训素材可用于新员工培训、年度复训和专项宣导，帮助业务人员识别可疑交易线索并按流程上报。',
   '培训,案例,可疑交易,保险场景', NULL, NULL, 15, 'system', NOW(), 'system', NOW()),
  ('AML-KB-UPD-001', '监管动态：强化受益所有人信息管理', 'REGULATORY_UPDATE',
   (SELECT id FROM t_regulation_category WHERE category_code = 'UPDATE-REGULATORY'), '监管动态', 'REGULATOR', '监管通报',
   DATE_SUB(CURDATE(), INTERVAL 8 DAY), NULL, 'PUBLISHED', 1,
   '监管持续关注受益所有人识别穿透不足、资料更新不及时和高风险客户增强尽调不到位等问题。',
   '建议机构完善受益所有人识别规则、更新频率、异常触发机制和留痕要求，并纳入内控检查和培训。',
   '监管动态,受益所有人,增强尽调,内控检查', NULL, NULL, 31, 'system', NOW(), 'system', NOW()),
  ('AML-KB-UPD-002', '行业动态：保险反洗钱典型风险案例提示', 'INDUSTRY_UPDATE',
   (SELECT id FROM t_regulation_category WHERE category_code = 'UPDATE-INDUSTRY'), '行业动态', 'INDUSTRY', '保险行业协会',
   DATE_SUB(CURDATE(), INTERVAL 3 DAY), NULL, 'PUBLISHED', 0,
   '近期行业案例显示，短期大额缴费后退保、代缴保费、异常受益人变更仍是保险洗钱风险重点。',
   '建议在客户风险评级、交易监测模型、案件调查和培训宣导中持续纳入上述风险信号。',
   '行业动态,风险提示,保险案例,交易监测', NULL, NULL, 27, 'system', NOW(), 'system', NOW());
