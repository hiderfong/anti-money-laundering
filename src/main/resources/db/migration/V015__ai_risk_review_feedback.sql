-- ============================================================================
-- 项目：保险公司反洗钱管理系统（AML System）
-- 文件：V015__ai_risk_review_feedback.sql
-- 描述：AI评分待复核池人工确认与留痕字段
-- ============================================================================

SET NAMES utf8mb4;

SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `t_ai_risk_score_record` ADD COLUMN `manual_review_label` VARCHAR(32) DEFAULT NULL COMMENT ''人工复核标签'' AFTER `scored_at`',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_ai_risk_score_record'
    AND COLUMN_NAME = 'manual_review_label'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `t_ai_risk_score_record` ADD COLUMN `manual_review_comment` VARCHAR(1024) DEFAULT NULL COMMENT ''人工复核备注'' AFTER `manual_review_label`',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_ai_risk_score_record'
    AND COLUMN_NAME = 'manual_review_comment'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `t_ai_risk_score_record` ADD COLUMN `reviewed_by` VARCHAR(64) DEFAULT NULL COMMENT ''复核人'' AFTER `manual_review_comment`',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_ai_risk_score_record'
    AND COLUMN_NAME = 'reviewed_by'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `t_ai_risk_score_record` ADD COLUMN `reviewed_at` DATETIME DEFAULT NULL COMMENT ''复核时间'' AFTER `reviewed_by`',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_ai_risk_score_record'
    AND COLUMN_NAME = 'reviewed_at'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
