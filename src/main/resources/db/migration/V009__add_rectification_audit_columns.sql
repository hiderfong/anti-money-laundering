-- ============================================================================
-- 项目：保险公司反洗钱管理系统（AML System）
-- 文件：V009__add_rectification_audit_columns.sql
-- 描述：补齐 t_rectification_task 与 BaseEntity 对齐所需的审计字段
-- ============================================================================

SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `t_rectification_task` ADD COLUMN `created_by` VARCHAR(64) DEFAULT NULL COMMENT ''创建人'' AFTER `closed_time`',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_rectification_task'
    AND COLUMN_NAME = 'created_by'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `t_rectification_task` ADD COLUMN `updated_by` VARCHAR(64) DEFAULT NULL COMMENT ''更新人'' AFTER `created_time`',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_rectification_task'
    AND COLUMN_NAME = 'updated_by'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
