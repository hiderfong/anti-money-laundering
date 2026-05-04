-- H2兼容的简化测试表结构
CREATE TABLE IF NOT EXISTS t_user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  real_name VARCHAR(64) NOT NULL,
  email VARCHAR(128),
  phone VARCHAR(20),
  department VARCHAR(128),
  position VARCHAR(64),
  status VARCHAR(16) DEFAULT 'ENABLED',
  last_login_time TIMESTAMP,
  last_login_ip VARCHAR(45),
  password_changed_time TIMESTAMP,
  login_fail_count INT DEFAULT 0,
  remark VARCHAR(512),
  created_by VARCHAR(64),
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64),
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_role (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  role_code VARCHAR(64) NOT NULL,
  role_name VARCHAR(128) NOT NULL,
  description VARCHAR(512),
  status VARCHAR(16) DEFAULT 'ENABLED',
  created_by VARCHAR(64),
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64),
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_user_role (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  created_by VARCHAR(64),
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_permission (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  permission_code VARCHAR(128) NOT NULL,
  permission_name VARCHAR(128) NOT NULL,
  parent_id BIGINT DEFAULT 0,
  type VARCHAR(16) NOT NULL,
  path VARCHAR(255),
  sort_order INT DEFAULT 0,
  icon VARCHAR(64),
  status VARCHAR(16) DEFAULT 'ENABLED',
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_role_permission (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  created_by VARCHAR(64),
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_customer (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_no VARCHAR(32) NOT NULL,
  customer_type VARCHAR(16) NOT NULL,
  name VARCHAR(128) NOT NULL,
  name_en VARCHAR(128),
  gender VARCHAR(8),
  nationality VARCHAR(64),
  ethnic_group VARCHAR(32),
  birth_date DATE,
  id_type VARCHAR(32),
  id_number VARCHAR(128),
  id_issuing_authority VARCHAR(128),
  id_expiry_date DATE,
  address VARCHAR(512),
  residence_address VARCHAR(512),
  phone VARCHAR(20),
  email VARCHAR(128),
  occupation VARCHAR(64),
  employer VARCHAR(256),
  job_title VARCHAR(64),
  annual_income_range VARCHAR(32),
  tax_resident_status VARCHAR(16),
  unified_credit_code VARCHAR(64),
  enterprise_type VARCHAR(64),
  registered_capital DECIMAL(18,2),
  business_scope TEXT,
  legal_representative VARCHAR(128),
  risk_level VARCHAR(16) DEFAULT 'LOW',
  risk_score INT DEFAULT 0,
  risk_update_time TIMESTAMP,
  is_pep BOOLEAN DEFAULT FALSE,
  pep_type VARCHAR(32),
  is_sanctioned BOOLEAN DEFAULT FALSE,
  kyc_status VARCHAR(16) DEFAULT 'INCOMPLETE',
  kyc_last_review_time TIMESTAMP,
  kyc_next_review_time TIMESTAMP,
  remark VARCHAR(1024),
  status VARCHAR(16) DEFAULT 'ACTIVE',
  created_by VARCHAR(64),
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64),
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_alert (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  alert_no VARCHAR(32) NOT NULL,
  customer_id BIGINT NOT NULL,
  customer_name VARCHAR(128),
  alert_type VARCHAR(32) NOT NULL,
  risk_score INT DEFAULT 0,
  risk_level VARCHAR(16) NOT NULL,
  source_rule_codes VARCHAR(512),
  alert_summary TEXT,
  status VARCHAR(16) DEFAULT 'NEW',
  assigned_to BIGINT,
  assigned_time TIMESTAMP,
  process_result VARCHAR(32),
  process_remark TEXT,
  process_time TIMESTAMP,
  deduplicate_key VARCHAR(64),
  related_transaction_ids TEXT,
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_transaction (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  transaction_no VARCHAR(32) NOT NULL,
  policy_id BIGINT,
  customer_id BIGINT NOT NULL,
  transaction_type VARCHAR(32) NOT NULL,
  amount DECIMAL(18,2) NOT NULL,
  currency VARCHAR(8) DEFAULT 'CNY',
  payment_method VARCHAR(16),
  channel VARCHAR(32),
  counterparty_name VARCHAR(128),
  counterparty_account VARCHAR(64),
  counterparty_bank VARCHAR(128),
  is_cross_border BOOLEAN DEFAULT FALSE,
  transaction_time TIMESTAMP NOT NULL,
  remark VARCHAR(512),
  status VARCHAR(16) DEFAULT 'SUCCESS',
  source_system VARCHAR(32),
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_rule_definition (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  rule_code VARCHAR(32) NOT NULL,
  rule_name VARCHAR(256) NOT NULL,
  rule_category VARCHAR(32) NOT NULL,
  description TEXT,
  risk_weight INT DEFAULT 50,
  priority INT DEFAULT 0,
  status VARCHAR(16) DEFAULT 'ENABLED',
  effective_date DATE,
  expiry_date DATE,
  config_json TEXT,
  drools_drl TEXT,
  created_by VARCHAR(64),
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64),
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_rule_execution_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  rule_id BIGINT NOT NULL,
  rule_code VARCHAR(32) NOT NULL,
  transaction_id BIGINT,
  customer_id BIGINT,
  execution_time TIMESTAMP NOT NULL,
  match_result BOOLEAN DEFAULT FALSE,
  match_score DECIMAL(5,2),
  execution_detail TEXT,
  duration_ms BIGINT,
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_watchlist (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  source_id BIGINT NOT NULL,
  external_id VARCHAR(128),
  entity_type VARCHAR(16) NOT NULL,
  name VARCHAR(256) NOT NULL,
  name_en VARCHAR(256),
  gender VARCHAR(8),
  nationality VARCHAR(64),
  date_of_birth VARCHAR(32),
  place_of_birth VARCHAR(256),
  remarks TEXT,
  list_date DATE,
  effective_date DATE,
  expiry_date DATE,
  status VARCHAR(16) DEFAULT 'ACTIVE',
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_screening_result (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  request_id BIGINT NOT NULL,
  customer_id BIGINT,
  customer_name VARCHAR(128),
  customer_id_number VARCHAR(128),
  watchlist_entry_id BIGINT,
  watchlist_name VARCHAR(256),
  match_score DECIMAL(5,2) NOT NULL,
  match_type VARCHAR(16) NOT NULL,
  match_field VARCHAR(64),
  match_detail TEXT,
  review_status VARCHAR(16) DEFAULT 'PENDING_REVIEW',
  review_result VARCHAR(32),
  review_reason TEXT,
  reviewed_by VARCHAR(64),
  reviewed_time TIMESTAMP,
  whitelisted BOOLEAN DEFAULT FALSE,
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_audit_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  trace_id VARCHAR(64),
  user_id BIGINT,
  username VARCHAR(64),
  operation_type VARCHAR(32) NOT NULL,
  module VARCHAR(64) NOT NULL,
  target_type VARCHAR(64),
  target_id VARCHAR(64),
  detail CLOB,
  ip_address VARCHAR(45),
  user_agent VARCHAR(512),
  request_uri VARCHAR(512),
  request_method VARCHAR(16),
  response_code INT,
  duration_ms BIGINT,
  error_message CLOB,
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 插入测试数据，admin 密码为 admin123
INSERT INTO t_user (id, username, password_hash, real_name, status) VALUES (1, 'admin', '$2a$10$c4ISGZ.nKFX0iC34wYd.8.OdmgqOLJXsrmyMocQY67X4j9gjoFojq', '系统管理员', 'ENABLED');
INSERT INTO t_role (id, role_code, role_name) VALUES (1, 'ROLE_ADMIN', '系统管理员');
INSERT INTO t_user_role (user_id, role_id) VALUES (1, 1);
INSERT INTO t_permission (id, permission_code, permission_name, type, path, sort_order, status) VALUES (1, 'system:view', '系统管理查看', 'API', '/system', 1, 'ENABLED');
INSERT INTO t_role_permission (role_id, permission_id) VALUES (1, 1);
