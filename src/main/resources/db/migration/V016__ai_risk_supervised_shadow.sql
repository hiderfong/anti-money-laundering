-- ============================================================================
-- 项目：保险公司反洗钱管理系统（AML System）
-- 文件：V016__ai_risk_supervised_shadow.sql
-- 描述：监督模型影子评分字段（概率与预测标签）
-- ============================================================================

SET NAMES utf8mb4;

SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `t_ai_risk_score_record` ADD COLUMN `model_probability` DECIMAL(5,4) DEFAULT NULL COMMENT ''监督模型可疑概率(影子)'' AFTER `reviewed_at`',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_ai_risk_score_record'
    AND COLUMN_NAME = 'model_probability'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `t_ai_risk_score_record` ADD COLUMN `model_label_predicted` VARCHAR(16) DEFAULT NULL COMMENT ''监督模型预测标签(影子)'' AFTER `model_probability`',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_ai_risk_score_record'
    AND COLUMN_NAME = 'model_label_predicted'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
