-- ============================================================================
-- 项目：保险公司反洗钱管理系统（AML System）
-- 文件：V002__init_data.sql
-- 描述：初始化基础数据 — 字典、角色、权限、用户、系统参数
-- 版本：v1.0
-- 日期：2025-05-01
-- 说明：本脚本由 Flyway 管理，请勿手动修改已执行的迁移脚本
-- ============================================================================

SET NAMES utf8mb4;

-- ============================================================================
-- 1. 数据字典类型表（t_sys_dict）
-- ============================================================================

INSERT IGNORE INTO `t_sys_dict` (`dict_code`, `dict_name`, `description`, `status`, `created_by`, `created_time`)
VALUES
  ('ID_TYPE',        '证件类型',     '客户证件类型',           'ENABLED', 'system', NOW()),
  ('CUSTOMER_TYPE',  '客户类型',     '客户分类',              'ENABLED', 'system', NOW()),
  ('RISK_LEVEL',     '风险等级',     '风险评级',              'ENABLED', 'system', NOW()),
  ('PAYMENT_METHOD', '支付方式',     '交易支付方式',           'ENABLED', 'system', NOW()),
  ('TRANSACTION_TYPE','交易类型',    '交易业务类型',           'ENABLED', 'system', NOW()),
  ('ALERT_TYPE',     '预警类型',     '预警分类',              'ENABLED', 'system', NOW()),
  ('CASE_STATUS',    '案件状态',     '案件处理状态',           'ENABLED', 'system', NOW()),
  ('SCREENING_TYPE', '筛查类型',     '名单筛查类型',           'ENABLED', 'system', NOW()),
  ('GENDER',         '性别',        '性别',                 'ENABLED', 'system', NOW()),
  ('NATIONALITY',    '国籍',        '国籍/地区',             'ENABLED', 'system', NOW()),
  ('STATUS',         '状态',        '通用状态',              'ENABLED', 'system', NOW()),
  ('CUSTOMER_STATUS', '客户状态',    '客户账户状态',           'ENABLED', 'system', NOW()),
  ('KYC_STATUS',     'KYC状态',     'KYC审核状态',           'ENABLED', 'system', NOW()),
  ('TAX_RESIDENT',   '税收居民身份',  '税收居民身份类型',        'ENABLED', 'system', NOW()),
  ('PEP_TYPE',       'PEP类型',     '政治公众人物类型',        'ENABLED', 'system', NOW()),
  ('WATCHLIST_SOURCE','名单源类型',   '制裁名单数据源类型',      'ENABLED', 'system', NOW()),
  ('MATCH_TYPE',     '匹配类型',     '名单匹配方式',           'ENABLED', 'system', NOW()),
  ('REVIEW_STATUS',  '复核状态',     '筛查结果复核状态',        'ENABLED', 'system', NOW()),
  ('REPORT_STATUS',  '报告状态',     '可疑交易报告状态',        'ENABLED', 'system', NOW());

-- ============================================================================
-- 2. 数据字典项表（t_sys_dict_item）
-- ============================================================================

-- 证件类型
INSERT IGNORE INTO `t_sys_dict_item` (`dict_id`, `item_code`, `item_label`, `item_value`, `sort_order`, `status`, `created_time`)
SELECT d.id, 'IDCARD',      '身份证',        'IDCARD',      1, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'ID_TYPE'
UNION ALL
SELECT d.id, 'PASSPORT',    '护照',          'PASSPORT',    2, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'ID_TYPE'
UNION ALL
SELECT d.id, 'HK_MACAO_TW', '港澳台通行证',   'HK_MACAO_TW', 3, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'ID_TYPE'
UNION ALL
SELECT d.id, 'MILITARY',    '军官证',        'MILITARY',    4, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'ID_TYPE'
UNION ALL
SELECT d.id, 'OTHER',       '其他',          'OTHER',       5, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'ID_TYPE';

-- 客户类型
INSERT IGNORE INTO `t_sys_dict_item` (`dict_id`, `item_code`, `item_label`, `item_value`, `sort_order`, `status`, `created_time`)
SELECT d.id, 'INDIVIDUAL', '个人客户', 'INDIVIDUAL', 1, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'CUSTOMER_TYPE'
UNION ALL
SELECT d.id, 'CORPORATE',  '法人客户', 'CORPORATE',  2, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'CUSTOMER_TYPE';

-- 风险等级
INSERT IGNORE INTO `t_sys_dict_item` (`dict_id`, `item_code`, `item_label`, `item_value`, `sort_order`, `status`, `created_time`)
SELECT d.id, 'LOW',    '低风险', 'LOW',    1, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'RISK_LEVEL'
UNION ALL
SELECT d.id, 'MEDIUM', '中风险', 'MEDIUM', 2, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'RISK_LEVEL'
UNION ALL
SELECT d.id, 'HIGH',   '高风险', 'HIGH',   3, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'RISK_LEVEL';

-- 支付方式
INSERT IGNORE INTO `t_sys_dict_item` (`dict_id`, `item_code`, `item_label`, `item_value`, `sort_order`, `status`, `created_time`)
SELECT d.id, 'CASH',      '现金',    'CASH',      1, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'PAYMENT_METHOD'
UNION ALL
SELECT d.id, 'TRANSFER',  '转账',    'TRANSFER',  2, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'PAYMENT_METHOD'
UNION ALL
SELECT d.id, 'BANK_CARD', '银行卡',  'BANK_CARD', 3, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'PAYMENT_METHOD'
UNION ALL
SELECT d.id, 'CHECK',     '支票',    'CHECK',     4, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'PAYMENT_METHOD'
UNION ALL
SELECT d.id, 'OTHER',     '其他',    'OTHER',     5, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'PAYMENT_METHOD';

-- 交易类型
INSERT IGNORE INTO `t_sys_dict_item` (`dict_id`, `item_code`, `item_label`, `item_value`, `sort_order`, `status`, `created_time`)
SELECT d.id, 'PREMIUM',           '保费缴纳',   'PREMIUM',           1, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'TRANSACTION_TYPE'
UNION ALL
SELECT d.id, 'SURRENDER',         '退保',       'SURRENDER',         2, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'TRANSACTION_TYPE'
UNION ALL
SELECT d.id, 'CLAIM',             '理赔',       'CLAIM',             3, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'TRANSACTION_TYPE'
UNION ALL
SELECT d.id, 'LOAN',              '保单贷款',    'LOAN',              4, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'TRANSACTION_TYPE'
UNION ALL
SELECT d.id, 'REPAYMENT',         '贷款还款',    'REPAYMENT',         5, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'TRANSACTION_TYPE'
UNION ALL
SELECT d.id, 'PARTIAL_WITHDRAWAL', '部分领取',   'PARTIAL_WITHDRAWAL', 6, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'TRANSACTION_TYPE';

-- 预警类型
INSERT IGNORE INTO `t_sys_dict_item` (`dict_id`, `item_code`, `item_label`, `item_value`, `sort_order`, `status`, `created_time`)
SELECT d.id, 'LARGE_TXN',      '大额交易',     'LARGE_TXN',      1, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'ALERT_TYPE'
UNION ALL
SELECT d.id, 'SUSPICIOUS',     '可疑交易',     'SUSPICIOUS',     2, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'ALERT_TYPE'
UNION ALL
SELECT d.id, 'SANCTIONS_HIT',  '制裁名单命中',  'SANCTIONS_HIT',  3, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'ALERT_TYPE'
UNION ALL
SELECT d.id, 'PEP_HIT',        'PEP命中',      'PEP_HIT',        4, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'ALERT_TYPE'
UNION ALL
SELECT d.id, 'MANUAL',         '人工上报',     'MANUAL',         5, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'ALERT_TYPE';

-- 案件状态
INSERT IGNORE INTO `t_sys_dict_item` (`dict_id`, `item_code`, `item_label`, `item_value`, `sort_order`, `status`, `created_time`)
SELECT d.id, 'DRAFT',             '草稿',       'DRAFT',             1, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'CASE_STATUS'
UNION ALL
SELECT d.id, 'INVESTIGATING',     '调查中',     'INVESTIGATING',     2, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'CASE_STATUS'
UNION ALL
SELECT d.id, 'PENDING_APPROVAL',  '待审批',     'PENDING_APPROVAL',  3, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'CASE_STATUS'
UNION ALL
SELECT d.id, 'SUBMITTED',         '已报送',     'SUBMITTED',         4, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'CASE_STATUS'
UNION ALL
SELECT d.id, 'CLOSED',            '已结案',     'CLOSED',            5, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'CASE_STATUS';

-- 筛查类型
INSERT IGNORE INTO `t_sys_dict_item` (`dict_id`, `item_code`, `item_label`, `item_value`, `sort_order`, `status`, `created_time`)
SELECT d.id, 'CUSTOMER_ONBOARD', '客户准入',   'CUSTOMER_ONBOARD', 1, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'SCREENING_TYPE'
UNION ALL
SELECT d.id, 'INFO_CHANGE',      '信息变更',   'INFO_CHANGE',      2, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'SCREENING_TYPE'
UNION ALL
SELECT d.id, 'TRANSACTION',      '交易筛查',   'TRANSACTION',      3, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'SCREENING_TYPE'
UNION ALL
SELECT d.id, 'PERIODIC',         '定期筛查',   'PERIODIC',         4, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'SCREENING_TYPE'
UNION ALL
SELECT d.id, 'BATCH',            '批量筛查',   'BATCH',            5, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'SCREENING_TYPE';

-- 性别
INSERT IGNORE INTO `t_sys_dict_item` (`dict_id`, `item_code`, `item_label`, `item_value`, `sort_order`, `status`, `created_time`)
SELECT d.id, 'MALE',   '男', 'MALE',   1, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'GENDER'
UNION ALL
SELECT d.id, 'FEMALE', '女', 'FEMALE', 2, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'GENDER';

-- 国籍（常用）
INSERT IGNORE INTO `t_sys_dict_item` (`dict_id`, `item_code`, `item_label`, `item_value`, `sort_order`, `status`, `created_time`)
SELECT d.id, 'CN', '中国',       'CN', 1,  'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'NATIONALITY'
UNION ALL
SELECT d.id, 'US', '美国',       'US', 2,  'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'NATIONALITY'
UNION ALL
SELECT d.id, 'GB', '英国',       'GB', 3,  'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'NATIONALITY'
UNION ALL
SELECT d.id, 'JP', '日本',       'JP', 4,  'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'NATIONALITY'
UNION ALL
SELECT d.id, 'KR', '韩国',       'KR', 5,  'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'NATIONALITY'
UNION ALL
SELECT d.id, 'DE', '德国',       'DE', 6,  'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'NATIONALITY'
UNION ALL
SELECT d.id, 'FR', '法国',       'FR', 7,  'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'NATIONALITY'
UNION ALL
SELECT d.id, 'AU', '澳大利亚',   'AU', 8,  'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'NATIONALITY'
UNION ALL
SELECT d.id, 'CA', '加拿大',     'CA', 9,  'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'NATIONALITY'
UNION ALL
SELECT d.id, 'SG', '新加坡',     'SG', 10, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'NATIONALITY'
UNION ALL
SELECT d.id, 'HK', '中国香港',   'HK', 11, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'NATIONALITY'
UNION ALL
SELECT d.id, 'MO', '中国澳门',   'MO', 12, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'NATIONALITY'
UNION ALL
SELECT d.id, 'TW', '中国台湾',   'TW', 13, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'NATIONALITY';

-- 通用状态
INSERT IGNORE INTO `t_sys_dict_item` (`dict_id`, `item_code`, `item_label`, `item_value`, `sort_order`, `status`, `created_time`)
SELECT d.id, 'ENABLED',  '启用', 'ENABLED',  1, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'STATUS'
UNION ALL
SELECT d.id, 'DISABLED', '禁用', 'DISABLED', 2, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'STATUS';

-- 客户状态
INSERT IGNORE INTO `t_sys_dict_item` (`dict_id`, `item_code`, `item_label`, `item_value`, `sort_order`, `status`, `created_time`)
SELECT d.id, 'ACTIVE',   '正常', 'ACTIVE',   1, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'CUSTOMER_STATUS'
UNION ALL
SELECT d.id, 'INACTIVE', '停用', 'INACTIVE', 2, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'CUSTOMER_STATUS'
UNION ALL
SELECT d.id, 'FROZEN',   '冻结', 'FROZEN',   3, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'CUSTOMER_STATUS';

-- KYC状态
INSERT IGNORE INTO `t_sys_dict_item` (`dict_id`, `item_code`, `item_label`, `item_value`, `sort_order`, `status`, `created_time`)
SELECT d.id, 'INCOMPLETE', '未完成', 'INCOMPLETE', 1, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'KYC_STATUS'
UNION ALL
SELECT d.id, 'COMPLETE',   '已完成', 'COMPLETE',   2, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'KYC_STATUS'
UNION ALL
SELECT d.id, 'REVIEWING',  '审核中', 'REVIEWING',  3, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'KYC_STATUS';

-- 税收居民身份
INSERT IGNORE INTO `t_sys_dict_item` (`dict_id`, `item_code`, `item_label`, `item_value`, `sort_order`, `status`, `created_time`)
SELECT d.id, 'CHINA', '中国税收居民',     'CHINA', 1, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'TAX_RESIDENT'
UNION ALL
SELECT d.id, 'OTHER', '非中国税收居民',   'OTHER', 2, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'TAX_RESIDENT'
UNION ALL
SELECT d.id, 'BOTH',  '双重税收居民',     'BOTH',  3, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'TAX_RESIDENT';

-- PEP类型
INSERT IGNORE INTO `t_sys_dict_item` (`dict_id`, `item_code`, `item_label`, `item_value`, `sort_order`, `status`, `created_time`)
SELECT d.id, 'DOMESTIC',     '国内PEP',       'DOMESTIC',     1, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'PEP_TYPE'
UNION ALL
SELECT d.id, 'INTERNATIONAL', '国际PEP',      'INTERNATIONAL', 2, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'PEP_TYPE'
UNION ALL
SELECT d.id, 'INTL_ORG',     '国际组织PEP',   'INTL_ORG',     3, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'PEP_TYPE';

-- 名单源类型
INSERT IGNORE INTO `t_sys_dict_item` (`dict_id`, `item_code`, `item_label`, `item_value`, `sort_order`, `status`, `created_time`)
SELECT d.id, 'UN',    '联合国制裁名单',       'UN',    1, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'WATCHLIST_SOURCE'
UNION ALL
SELECT d.id, 'OFAC',  '美国OFAC名单',        'OFAC',  2, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'WATCHLIST_SOURCE'
UNION ALL
SELECT d.id, 'EU',    '欧盟制裁名单',         'EU',    3, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'WATCHLIST_SOURCE'
UNION ALL
SELECT d.id, 'MPS',   '中国人民银行名单',      'MPS',   4, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'WATCHLIST_SOURCE'
UNION ALL
SELECT d.id, 'PBOC',  '银保监会名单',         'PBOC',  5, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'WATCHLIST_SOURCE'
UNION ALL
SELECT d.id, 'FATF',  'FATF高风险名单',       'FATF',  6, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'WATCHLIST_SOURCE'
UNION ALL
SELECT d.id, 'OTHER', '其他名单',            'OTHER', 7, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'WATCHLIST_SOURCE';

-- 匹配类型
INSERT IGNORE INTO `t_sys_dict_item` (`dict_id`, `item_code`, `item_label`, `item_value`, `sort_order`, `status`, `created_time`)
SELECT d.id, 'EXACT',     '精确匹配',   'EXACT',     1, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'MATCH_TYPE'
UNION ALL
SELECT d.id, 'FUZZY',     '模糊匹配',   'FUZZY',     2, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'MATCH_TYPE'
UNION ALL
SELECT d.id, 'COMPOSITE', '组合匹配',   'COMPOSITE', 3, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'MATCH_TYPE';

-- 复核状态
INSERT IGNORE INTO `t_sys_dict_item` (`dict_id`, `item_code`, `item_label`, `item_value`, `sort_order`, `status`, `created_time`)
SELECT d.id, 'PENDING_REVIEW', '待复核', 'PENDING_REVIEW', 1, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'REVIEW_STATUS'
UNION ALL
SELECT d.id, 'CONFIRMED',      '已确认', 'CONFIRMED',      2, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'REVIEW_STATUS'
UNION ALL
SELECT d.id, 'EXCLUDED',       '已排除', 'EXCLUDED',       3, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'REVIEW_STATUS'
UNION ALL
SELECT d.id, 'ESCALATED',      '已升级', 'ESCALATED',      4, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'REVIEW_STATUS';

-- 报告状态
INSERT IGNORE INTO `t_sys_dict_item` (`dict_id`, `item_code`, `item_label`, `item_value`, `sort_order`, `status`, `created_time`)
SELECT d.id, 'DRAFT',          '草稿',   'DRAFT',          1, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'REPORT_STATUS'
UNION ALL
SELECT d.id, 'PENDING_REVIEW', '待审核', 'PENDING_REVIEW', 2, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'REPORT_STATUS'
UNION ALL
SELECT d.id, 'APPROVED',       '已批准', 'APPROVED',       3, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'REPORT_STATUS'
UNION ALL
SELECT d.id, 'REJECTED',       '已驳回', 'REJECTED',       4, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'REPORT_STATUS'
UNION ALL
SELECT d.id, 'SUBMITTED',      '已报送', 'SUBMITTED',      5, 'ENABLED', NOW() FROM t_sys_dict d WHERE d.dict_code = 'REPORT_STATUS';

-- ============================================================================
-- 3. 角色表（t_role）
-- ============================================================================

INSERT IGNORE INTO `t_role` (`role_code`, `role_name`, `description`, `status`, `created_by`, `created_time`)
VALUES
  ('ROLE_ADMIN',       '系统管理员',   '系统管理员，拥有全部权限',       'ENABLED', 'system', NOW()),
  ('ROLE_AML_OFFICER', '反洗钱专员',   '负责日常反洗钱业务操作',        'ENABLED', 'system', NOW()),
  ('ROLE_AML_MANAGER', '反洗钱主管',   '负责反洗钱业务审核与管理',      'ENABLED', 'system', NOW());

-- ============================================================================
-- 4. 权限表（t_permission）
-- ============================================================================

INSERT IGNORE INTO `t_permission` (`permission_code`, `permission_name`, `parent_id`, `type`, `path`, `sort_order`, `icon`, `status`, `created_time`)
VALUES
  ('MENU_KYC',        'KYC管理',   0, 'MENU', '/kyc',        1,  'user',       'ENABLED', NOW()),
  ('MENU_SCREENING',  '名单筛查',   0, 'MENU', '/screening',  2,  'search',     'ENABLED', NOW()),
  ('MENU_MONITORING', '交易监测',   0, 'MENU', '/monitoring', 3,  'chart',      'ENABLED', NOW()),
  ('MENU_ALERT',      '预警管理',   0, 'MENU', '/alert',      4,  'warning',    'ENABLED', NOW()),
  ('MENU_CASE',       '案件管理',   0, 'MENU', '/case',       5,  'folder',     'ENABLED', NOW()),
  ('MENU_REPORT',     '监管报送',   0, 'MENU', '/report',     6,  'file-text',  'ENABLED', NOW()),
  ('MENU_PRODUCT',    '产品管理',   0, 'MENU', '/product',    7,  'shopping',   'ENABLED', NOW()),
  ('MENU_ASSESSMENT', '自评估',    0, 'MENU', '/assessment', 8,  'check',      'ENABLED', NOW()),
  ('MENU_SYSTEM',     '系统管理',   0, 'MENU', '/system',     9,  'setting',    'ENABLED', NOW());

-- ============================================================================
-- 5. 系统用户表（t_user）
--    创建管理员账号，密码为 admin123（BCrypt加密）
--    必须在 t_user_role 之前插入，因为 t_user_role 有外键约束
-- ============================================================================

INSERT IGNORE INTO `t_user` (`username`, `password_hash`, `real_name`, `department`, `position`, `status`, `remark`, `created_by`, `created_time`)
VALUES ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '系统管理员', '合规部', '系统管理员', 'ENABLED', '初始管理员账号', 'system', NOW());

-- ============================================================================
-- 6. 角色-权限关联表（t_role_permission）
--    将所有权限分配给管理员角色
-- ============================================================================

INSERT IGNORE INTO `t_role_permission` (`role_id`, `permission_id`, `created_by`, `created_time`)
SELECT r.id, p.id, 'system', NOW()
FROM t_role r, t_permission p
WHERE r.role_code = 'ROLE_ADMIN';

-- ============================================================================
-- 7. 用户-角色关联表（t_user_role）
--    将管理员角色分配给admin用户
-- ============================================================================

INSERT IGNORE INTO `t_user_role` (`user_id`, `role_id`, `created_by`, `created_time`)
SELECT u.id, r.id, 'system', NOW()
FROM t_user u, t_role r
WHERE u.username = 'admin' AND r.role_code = 'ROLE_ADMIN';

-- ============================================================================
-- 8. 系统参数表（t_sys_param）
-- ============================================================================

INSERT IGNORE INTO `t_sys_param` (`param_code`, `param_name`, `param_value`, `default_value`, `data_type`, `description`, `editable`, `status`, `created_by`, `created_time`)
VALUES
  ('LARGE_TXN_CASH_THRESHOLD',           '大额现金交易阈值',              '50000',  '50000',  'INT',  '人民币现金交易大额报告标准（元）',                       1, 'ENABLED', 'system', NOW()),
  ('LARGE_TXN_TRANSFER_THRESHOLD',       '大额转账交易阈值',              '200000', '200000', 'INT',  '人民币转账交易大额报告标准（元）',                       1, 'ENABLED', 'system', NOW()),
  ('CROSS_BORDER_THRESHOLD',             '跨境交易报告阈值',              '200000', '200000', 'INT',  '跨境交易大额报告标准（等值人民币，元）',                    1, 'ENABLED', 'system', NOW()),
  ('SUSPICIOUS_REPORT_DEADLINE_DAYS',    '可疑交易报告时限（天）',         '10',     '10',     'INT',  '发现可疑交易后提交报告的法定时限（工作日）',               1, 'ENABLED', 'system', NOW()),
  ('CUSTOMER_REVIEW_LOW_YEARS',          '低风险客户复核周期（年）',       '3',      '3',      'INT',  '低风险客户KYC定期复核周期',                              1, 'ENABLED', 'system', NOW()),
  ('CUSTOMER_REVIEW_MEDIUM_YEARS',       '中风险客户复核周期（年）',       '2',      '2',      'INT',  '中风险客户KYC定期复核周期',                              1, 'ENABLED', 'system', NOW()),
  ('CUSTOMER_REVIEW_HIGH_YEARS',         '高风险客户复核周期（年）',       '1',      '1',      'INT',  '高风险客户KYC定期复核周期',                              1, 'ENABLED', 'system', NOW()),
  ('SCREENING_MATCH_THRESHOLD',          '名单匹配阈值',                  '85',     '85',     'INT',  '名单筛查匹配度阈值（0-100），超过此值视为命中',            1, 'ENABLED', 'system', NOW());
