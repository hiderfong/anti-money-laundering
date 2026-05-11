-- ============================================================================
-- 项目：保险公司反洗钱管理系统（AML System）
-- 文件：V003__rbac_roles_permissions.sql
-- 描述：RBAC权限控制 — 补充角色、按钮级权限、角色-权限关联
-- 版本：v1.0
-- 日期：2025-05-04
-- ============================================================================

SET NAMES utf8mb4;

-- ============================================================================
-- 1. 补充角色（合规专员、调查员、只读用户）
-- ============================================================================

INSERT IGNORE INTO `t_role` (`role_code`, `role_name`, `description`, `status`, `created_by`, `created_time`)
VALUES
  ('ROLE_COMPLIANCE',   '合规专员',   '负责合规审查、报送等业务',          'ENABLED', 'system', NOW()),
  ('ROLE_INVESTIGATOR', '调查员',    '负责预警核查、案件调查',            'ENABLED', 'system', NOW()),
  ('ROLE_VIEWER',       '只读用户',   '仅查看数据，无操作权限',            'ENABLED', 'system', NOW());

-- ============================================================================
-- 2. 按钮级权限（module:action 格式）
-- ============================================================================

INSERT IGNORE INTO `t_permission` (`permission_code`, `permission_name`, `parent_id`, `type`, `path`, `sort_order`, `icon`, `status`, `created_time`)
VALUES
  -- 客户管理
  ('customer:view',     '客户-查看',   0, 'BUTTON', NULL, 101, NULL, 'ENABLED', NOW()),
  ('customer:create',   '客户-新增',   0, 'BUTTON', NULL, 102, NULL, 'ENABLED', NOW()),
  ('customer:update',   '客户-编辑',   0, 'BUTTON', NULL, 103, NULL, 'ENABLED', NOW()),
  ('customer:delete',   '客户-删除',   0, 'BUTTON', NULL, 104, NULL, 'ENABLED', NOW()),
  ('customer:export',   '客户-导出',   0, 'BUTTON', NULL, 105, NULL, 'ENABLED', NOW()),
  -- 名单筛查
  ('screening:view',    '筛查-查看',   0, 'BUTTON', NULL, 201, NULL, 'ENABLED', NOW()),
  ('screening:execute', '筛查-执行',   0, 'BUTTON', NULL, 202, NULL, 'ENABLED', NOW()),
  -- 交易监测
  ('monitoring:view',   '监测-查看',   0, 'BUTTON', NULL, 301, NULL, 'ENABLED', NOW()),
  ('monitoring:config', '监测-配置',   0, 'BUTTON', NULL, 302, NULL, 'ENABLED', NOW()),
  -- 预警管理
  ('alert:view',        '预警-查看',   0, 'BUTTON', NULL, 401, NULL, 'ENABLED', NOW()),
  ('alert:process',     '预警-处理',   0, 'BUTTON', NULL, 402, NULL, 'ENABLED', NOW()),
  ('alert:dismiss',     '预警-排除',   0, 'BUTTON', NULL, 403, NULL, 'ENABLED', NOW()),
  -- 案件管理
  ('case:view',         '案件-查看',   0, 'BUTTON', NULL, 501, NULL, 'ENABLED', NOW()),
  ('case:create',       '案件-创建',   0, 'BUTTON', NULL, 502, NULL, 'ENABLED', NOW()),
  ('case:approve',      '案件-审批',   0, 'BUTTON', NULL, 503, NULL, 'ENABLED', NOW()),
  -- 报告
  ('report:view',       '报告-查看',   0, 'BUTTON', NULL, 601, NULL, 'ENABLED', NOW()),
  ('report:str',        'STR报告',    0, 'BUTTON', NULL, 602, NULL, 'ENABLED', NOW()),
  ('report:submit',     '报告-报送',   0, 'BUTTON', NULL, 603, NULL, 'ENABLED', NOW()),
  -- 产品管理
  ('product:view',      '产品-查看',   0, 'BUTTON', NULL, 701, NULL, 'ENABLED', NOW()),
  ('product:manage',    '产品-管理',   0, 'BUTTON', NULL, 702, NULL, 'ENABLED', NOW()),
  -- 自评估
  ('assessment:view',   '评估-查看',   0, 'BUTTON', NULL, 801, NULL, 'ENABLED', NOW()),
  ('assessment:manage', '评估-管理',   0, 'BUTTON', NULL, 802, NULL, 'ENABLED', NOW()),
  -- 系统管理
  ('system:view',       '系统-查看',   0, 'BUTTON', NULL, 901, NULL, 'ENABLED', NOW()),
  ('system:user',       '系统-用户管理', 0, 'BUTTON', NULL, 902, NULL, 'ENABLED', NOW()),
  ('system:role',       '系统-角色管理', 0, 'BUTTON', NULL, 903, NULL, 'ENABLED', NOW());

-- ============================================================================
-- 3. 管理员角色：拥有全部按钮权限
-- ============================================================================

INSERT IGNORE INTO `t_role_permission` (`role_id`, `permission_id`, `created_by`, `created_time`)
SELECT r.id, p.id, 'system', NOW()
FROM t_role r, t_permission p
WHERE r.role_code = 'ROLE_ADMIN' AND p.type = 'BUTTON';

-- ============================================================================
-- 4. 合规专员角色权限
-- ============================================================================

INSERT IGNORE INTO `t_role_permission` (`role_id`, `permission_id`, `created_by`, `created_time`)
SELECT r.id, p.id, 'system', NOW()
FROM t_role r, t_permission p
WHERE r.role_code = 'ROLE_COMPLIANCE'
  AND p.permission_code IN (
    'customer:view', 'customer:create', 'customer:update', 'customer:export',
    'screening:view', 'screening:execute',
    'monitoring:view', 'monitoring:config',
    'alert:view', 'alert:process', 'alert:dismiss',
    'case:view', 'case:create', 'case:approve',
    'report:view', 'report:str', 'report:submit',
    'product:view', 'product:manage',
    'assessment:view', 'assessment:manage'
  );

-- ============================================================================
-- 5. 调查员角色权限
-- ============================================================================

INSERT IGNORE INTO `t_role_permission` (`role_id`, `permission_id`, `created_by`, `created_time`)
SELECT r.id, p.id, 'system', NOW()
FROM t_role r, t_permission p
WHERE r.role_code = 'ROLE_INVESTIGATOR'
  AND p.permission_code IN (
    'customer:view', 'customer:create', 'customer:update',
    'screening:view', 'screening:execute',
    'monitoring:view',
    'alert:view', 'alert:process',
    'case:view', 'case:create'
  );

-- ============================================================================
-- 6. 只读用户角色权限
-- ============================================================================

INSERT IGNORE INTO `t_role_permission` (`role_id`, `permission_id`, `created_by`, `created_time`)
SELECT r.id, p.id, 'system', NOW()
FROM t_role r, t_permission p
WHERE r.role_code = 'ROLE_VIEWER'
  AND p.permission_code IN (
    'customer:view',
    'alert:view',
    'case:view',
    'product:view',
    'report:view'
  );
