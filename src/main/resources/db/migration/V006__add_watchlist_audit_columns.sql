-- ============================================================================
-- 项目：保险公司反洗钱管理系统（AML System）
-- 文件：V006__add_watchlist_audit_columns.sql
-- 描述：补齐 t_watchlist 与 BaseEntity 对齐所需的审计字段
-- ============================================================================

ALTER TABLE `t_watchlist`
  ADD COLUMN `created_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人' AFTER `status`,
  ADD COLUMN `updated_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人' AFTER `created_time`;
