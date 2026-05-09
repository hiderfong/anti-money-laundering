-- Encrypted customer phone values include IV and authentication tag, so the
-- ciphertext is longer than the original phone number.
ALTER TABLE `t_customer`
  MODIFY COLUMN `phone` VARCHAR(128) DEFAULT NULL COMMENT '手机号码（应用层加密存储）';
