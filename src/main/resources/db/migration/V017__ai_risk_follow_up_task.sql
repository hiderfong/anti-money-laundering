-- ============================================================================
-- 项目：保险公司反洗钱管理系统（AML System）
-- 文件：V017__ai_risk_follow_up_task.sql
-- 描述：AI评分记录关联整改中心跟进任务
-- ============================================================================

SET NAMES utf8mb4;

SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `t_ai_risk_score_record` ADD COLUMN `follow_up_task_id` BIGINT DEFAULT NULL COMMENT ''跟进整改任务ID'' AFTER `reviewed_at`',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_ai_risk_score_record'
    AND COLUMN_NAME = 'follow_up_task_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `t_ai_risk_score_record` ADD COLUMN `follow_up_created_at` DATETIME DEFAULT NULL COMMENT ''跟进任务创建时间'' AFTER `follow_up_task_id`',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_ai_risk_score_record'
    AND COLUMN_NAME = 'follow_up_created_at'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `t_ai_risk_score_record` ADD COLUMN `follow_up_created_by` VARCHAR(64) DEFAULT NULL COMMENT ''跟进任务创建人'' AFTER `follow_up_created_at`',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_ai_risk_score_record'
    AND COLUMN_NAME = 'follow_up_created_by'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `t_ai_risk_score_record` ADD KEY `idx_ai_risk_follow_up_task` (`follow_up_task_id`)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_ai_risk_score_record'
    AND INDEX_NAME = 'idx_ai_risk_follow_up_task'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
