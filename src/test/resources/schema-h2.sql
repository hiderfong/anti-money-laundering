-- H2兼容的简化测试表结构
CREATE TABLE IF NOT EXISTS t_user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  real_name VARCHAR(64) NOT NULL,
  email VARCHAR(128),
  phone VARCHAR(128),
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
  phone VARCHAR(128),
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

CREATE TABLE IF NOT EXISTS t_customer_beneficial_owner (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_id BIGINT NOT NULL,
  owner_name VARCHAR(128) NOT NULL,
  owner_id_type VARCHAR(32),
  owner_id_number VARCHAR(128),
  nationality VARCHAR(64),
  birth_date DATE,
  ownership_percentage DECIMAL(5,2),
  control_type VARCHAR(16),
  control_description VARCHAR(512),
  relationship VARCHAR(64),
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
  created_by VARCHAR(64),
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64),
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_watchlist_alias (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  watchlist_id BIGINT NOT NULL,
  alias_name VARCHAR(256) NOT NULL,
  alias_type VARCHAR(16) NOT NULL,
  language VARCHAR(16),
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_watchlist_identity (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  watchlist_id BIGINT NOT NULL,
  id_type VARCHAR(32) NOT NULL,
  id_number VARCHAR(128) NOT NULL,
  issuing_country VARCHAR(64),
  expiry_date VARCHAR(32),
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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

CREATE TABLE IF NOT EXISTS t_screening_request (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  request_no VARCHAR(64),
  customer_id BIGINT,
  screening_type VARCHAR(32),
  request_source VARCHAR(32),
  request_data CLOB,
  total_scanned INT,
  total_hit INT,
  status VARCHAR(16) DEFAULT 'PROCESSING',
  error_message TEXT,
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  completed_time TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_whitelist (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_id BIGINT,
  customer_name VARCHAR(128),
  watchlist_entry_id BIGINT,
  watchlist_name VARCHAR(256),
  exclude_reason VARCHAR(512),
  evidence TEXT,
  effective_date DATE,
  expiry_date DATE,
  approved_by VARCHAR(64),
  approved_time TIMESTAMP,
  review_status VARCHAR(16) DEFAULT 'ACTIVE',
  created_by VARCHAR(64),
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64),
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_transaction_daily_summary (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_id BIGINT NOT NULL,
  summary_date DATE NOT NULL,
  transaction_type VARCHAR(32),
  payment_method VARCHAR(16),
  is_cross_border BOOLEAN DEFAULT FALSE,
  total_amount DECIMAL(18,2),
  transaction_count INT DEFAULT 0,
  large_txn_flag BOOLEAN DEFAULT FALSE,
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_watchlist_source (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  source_code VARCHAR(32) NOT NULL,
  source_name VARCHAR(128) NOT NULL,
  source_type VARCHAR(16) NOT NULL,
  update_frequency VARCHAR(16) NOT NULL,
  file_format VARCHAR(16),
  file_url VARCHAR(512),
  last_update_time TIMESTAMP,
  next_update_time TIMESTAMP,
  total_entries INT DEFAULT 0,
  status VARCHAR(16) DEFAULT 'ENABLED',
  created_by VARCHAR(64),
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64),
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_watchlist_update_job (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  job_no VARCHAR(32) NOT NULL,
  source_id BIGINT,
  source_name VARCHAR(128),
  update_mode VARCHAR(16) NOT NULL,
  status VARCHAR(16) DEFAULT 'PENDING',
  total_entries INT DEFAULT 0,
  added_count INT DEFAULT 0,
  updated_count INT DEFAULT 0,
  expired_count INT DEFAULT 0,
  started_time TIMESTAMP,
  completed_time TIMESTAMP,
  error_message VARCHAR(512),
  created_by VARCHAR(64),
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64),
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_retrospective_screening_job (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  job_no VARCHAR(32) NOT NULL,
  job_name VARCHAR(128) NOT NULL,
  scope_type VARCHAR(32) NOT NULL,
  customer_ids CLOB,
  watchlist_source_id BIGINT,
  status VARCHAR(16) DEFAULT 'PENDING',
  total_customers INT DEFAULT 0,
  processed_customers INT DEFAULT 0,
  total_hits INT DEFAULT 0,
  started_time TIMESTAMP,
  completed_time TIMESTAMP,
  remark VARCHAR(512),
  created_by VARCHAR(64),
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64),
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_special_measure (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  measure_no VARCHAR(32) NOT NULL,
  customer_id BIGINT NOT NULL,
  customer_name VARCHAR(128),
  measure_type VARCHAR(32) NOT NULL,
  trigger_type VARCHAR(32) NOT NULL,
  related_result_id BIGINT,
  related_alert_id BIGINT,
  control_level VARCHAR(16) DEFAULT 'MEDIUM',
  measure_content CLOB NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE,
  status VARCHAR(16) DEFAULT 'ACTIVE',
  decision_reason CLOB,
  closed_reason VARCHAR(512),
  created_by VARCHAR(64),
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64),
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_freeze_seizure_deduction (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  record_no VARCHAR(32) NOT NULL,
  customer_id BIGINT NOT NULL,
  customer_name VARCHAR(128),
  authority_name VARCHAR(256) NOT NULL,
  document_no VARCHAR(128) NOT NULL,
  action_type VARCHAR(16) NOT NULL,
  amount DECIMAL(18,2),
  currency VARCHAR(8) DEFAULT 'CNY',
  effective_date DATE NOT NULL,
  expiry_date DATE,
  status VARCHAR(16) DEFAULT 'ACTIVE',
  handler VARCHAR(64),
  remark VARCHAR(512),
  created_by VARCHAR(64),
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64),
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_rectification_task (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  assessment_id BIGINT,
  source_type VARCHAR(32) DEFAULT 'SELF_ASSESSMENT',
  source_id BIGINT,
  issue_description CLOB NOT NULL,
  issue_category VARCHAR(64),
  severity VARCHAR(16) NOT NULL,
  responsible_dept VARCHAR(128),
  responsible_person VARCHAR(64),
  deadline DATE NOT NULL,
  status VARCHAR(16) DEFAULT 'OPEN',
  progress_percent INT DEFAULT 0,
  completion_evidence CLOB,
  completed_time TIMESTAMP,
  verification_status VARCHAR(16) DEFAULT 'PENDING',
  verified_by VARCHAR(64),
  verified_time TIMESTAMP,
  verify_result CLOB,
  closed_time TIMESTAMP,
  created_by VARCHAR(64),
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64),
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_investigation_request (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  request_no VARCHAR(32) NOT NULL,
  authority_name VARCHAR(256) NOT NULL,
  request_type VARCHAR(32) NOT NULL,
  document_no VARCHAR(128) NOT NULL,
  customer_id BIGINT,
  customer_name VARCHAR(128),
  related_case_id BIGINT,
  priority VARCHAR(16) DEFAULT 'MEDIUM',
  received_date DATE NOT NULL,
  due_date DATE NOT NULL,
  status VARCHAR(24) DEFAULT 'RECEIVED',
  handler VARCHAR(64),
  summary CLOB NOT NULL,
  response_summary CLOB,
  completed_time TIMESTAMP,
  closed_time TIMESTAMP,
  created_by VARCHAR(64),
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64),
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_investigation_action (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  request_id BIGINT NOT NULL,
  action_no VARCHAR(32) NOT NULL,
  action_type VARCHAR(32) NOT NULL,
  action_content CLOB NOT NULL,
  action_result CLOB,
  operator VARCHAR(64),
  action_time TIMESTAMP NOT NULL,
  attachment_ref VARCHAR(512),
  created_by VARCHAR(64),
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64),
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_case (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  case_no VARCHAR(32) NOT NULL,
  alert_id BIGINT,
  customer_id BIGINT NOT NULL,
  customer_name VARCHAR(128),
  case_status VARCHAR(32) DEFAULT 'DRAFT',
  case_type VARCHAR(32),
  priority INT DEFAULT 0,
  summary VARCHAR(2048),
  investigator_id BIGINT,
  reviewer_id BIGINT,
  approver_id BIGINT,
  submit_time TIMESTAMP,
  close_time TIMESTAMP,
  close_reason VARCHAR(512),
  created_by VARCHAR(64),
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64),
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_case_status_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  case_id BIGINT NOT NULL,
  from_status VARCHAR(32),
  to_status VARCHAR(32) NOT NULL,
  remark VARCHAR(2048),
  changed_by VARCHAR(64),
  changed_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_case_investigation (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  case_id BIGINT NOT NULL,
  content VARCHAR(4096) NOT NULL,
  conclusion VARCHAR(32),
  investigator_id BIGINT NOT NULL,
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_case_attachment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  case_id BIGINT NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  file_path VARCHAR(512) NOT NULL,
  file_size BIGINT,
  file_type VARCHAR(32),
  upload_by BIGINT NOT NULL,
  upload_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_large_txn_report (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  report_no VARCHAR(32) NOT NULL,
  customer_id BIGINT NOT NULL,
  customer_name VARCHAR(128),
  transaction_id BIGINT,
  report_date DATE NOT NULL,
  transaction_time TIMESTAMP NOT NULL,
  transaction_type VARCHAR(32) NOT NULL,
  amount DECIMAL(18,2) NOT NULL,
  currency VARCHAR(8) DEFAULT 'CNY',
  payment_method VARCHAR(16),
  counterparty_info VARCHAR(2048),
  report_status VARCHAR(16) DEFAULT 'DRAFT',
  reviewed_by VARCHAR(64),
  reviewed_time TIMESTAMP,
  submitted_by VARCHAR(64),
  submitted_time TIMESTAMP,
  xml_content CLOB,
  submit_response VARCHAR(2048),
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_report_submit_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  report_type VARCHAR(32) NOT NULL,
  report_id BIGINT NOT NULL,
  submit_time TIMESTAMP NOT NULL,
  submit_status VARCHAR(16) NOT NULL,
  request_data CLOB,
  response_data VARCHAR(2048),
  error_message VARCHAR(512),
  retry_count INT DEFAULT 0,
  max_retries INT DEFAULT 3,
  next_retry_time TIMESTAMP,
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_product (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  product_code VARCHAR(32) NOT NULL,
  product_name VARCHAR(256) NOT NULL,
  product_type VARCHAR(32) NOT NULL,
  product_sub_type VARCHAR(64),
  payment_mode VARCHAR(16),
  has_cash_value BOOLEAN DEFAULT FALSE,
  has_investment_feature BOOLEAN DEFAULT FALSE,
  surrender_flexibility VARCHAR(16),
  beneficiary_changeable BOOLEAN DEFAULT TRUE,
  risk_level VARCHAR(16) DEFAULT 'LOW',
  risk_score INT DEFAULT 0,
  risk_factors VARCHAR(2048),
  status VARCHAR(16) DEFAULT 'ACTIVE',
  effective_date DATE,
  expiry_date DATE,
  created_by VARCHAR(64),
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64),
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_product_risk_assessment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  product_id BIGINT NOT NULL,
  assessment_date DATE NOT NULL,
  assessor VARCHAR(64) NOT NULL,
  client_group_score INT,
  payment_mode_score INT,
  product_structure_score INT,
  surrender_score INT,
  beneficiary_score INT,
  channel_score INT,
  total_score INT NOT NULL,
  risk_level VARCHAR(16) NOT NULL,
  assessment_result VARCHAR(2048),
  approved_by VARCHAR(64),
  approved_time TIMESTAMP,
  status VARCHAR(16) DEFAULT 'DRAFT',
  created_by VARCHAR(64),
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64),
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_sys_dict (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  dict_code VARCHAR(64) NOT NULL,
  dict_name VARCHAR(128) NOT NULL,
  description VARCHAR(512),
  status VARCHAR(16) DEFAULT 'ACTIVE',
  created_by VARCHAR(64),
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64),
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_sys_dict_item (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  dict_id BIGINT NOT NULL,
  item_code VARCHAR(64) NOT NULL,
  item_label VARCHAR(255) NOT NULL,
  item_value VARCHAR(255) NOT NULL,
  sort_order INT DEFAULT 0,
  status VARCHAR(16) DEFAULT 'ACTIVE',
  remark VARCHAR(512),
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_notification (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  type VARCHAR(32) NOT NULL,
  title VARCHAR(255) NOT NULL,
  content VARCHAR(2048),
  related_type VARCHAR(64),
  related_id VARCHAR(64),
  is_read BOOLEAN DEFAULT FALSE,
  read_time TIMESTAMP,
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_aml_model (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  model_code VARCHAR(64) NOT NULL,
  model_name VARCHAR(256) NOT NULL,
  model_type VARCHAR(32) NOT NULL,
  scenario VARCHAR(64) NOT NULL,
  algorithm_type VARCHAR(64),
  version VARCHAR(32) DEFAULT '1.0.0',
  lifecycle_status VARCHAR(32) DEFAULT 'DRAFT',
  owner VARCHAR(64),
  governance_level VARCHAR(16) DEFAULT 'L2',
  risk_level VARCHAR(16) DEFAULT 'MEDIUM',
  training_dataset VARCHAR(256),
  validation_dataset VARCHAR(256),
  test_result VARCHAR(32),
  last_test_time TIMESTAMP,
  deployment_env VARCHAR(32),
  deployed_time TIMESTAMP,
  monitor_status VARCHAR(32) DEFAULT 'NOT_STARTED',
  precision_rate DECIMAL(8,4),
  recall_rate DECIMAL(8,4),
  false_positive_rate DECIMAL(8,4),
  drift_score DECIMAL(8,4),
  last_monitor_time TIMESTAMP,
  iteration_plan CLOB,
  archive_reason CLOB,
  archived_time TIMESTAMP,
  description CLOB,
  config_json CLOB,
  created_by VARCHAR(64),
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64),
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_aml_model_lifecycle_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  model_id BIGINT NOT NULL,
  model_code VARCHAR(64) NOT NULL,
  action_type VARCHAR(32) NOT NULL,
  from_status VARCHAR(32),
  to_status VARCHAR(32) NOT NULL,
  operator VARCHAR(64),
  action_time TIMESTAMP NOT NULL,
  action_summary CLOB,
  result_metric CLOB,
  artifact_ref VARCHAR(512),
  created_by VARCHAR(64),
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64),
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_regulation_category (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  category_code VARCHAR(64) NOT NULL,
  category_name VARCHAR(128) NOT NULL,
  category_type VARCHAR(32) DEFAULT 'GENERAL',
  parent_id BIGINT DEFAULT 0,
  sort_order INT DEFAULT 0,
  status VARCHAR(32) DEFAULT 'ENABLED',
  description VARCHAR(512),
  created_by VARCHAR(64),
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64),
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_regulation_document (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  doc_code VARCHAR(64) NOT NULL,
  title VARCHAR(256) NOT NULL,
  doc_type VARCHAR(32) NOT NULL,
  category_id BIGINT,
  category_name VARCHAR(128),
  source_type VARCHAR(32) DEFAULT 'INTERNAL',
  source_org VARCHAR(128),
  publish_date DATE,
  effective_date DATE,
  status VARCHAR(32) DEFAULT 'DRAFT',
  important_flag BOOLEAN DEFAULT FALSE,
  summary VARCHAR(1024),
  content CLOB,
  tags VARCHAR(512),
  reference_url VARCHAR(512),
  attachment_ref VARCHAR(512),
  view_count INT DEFAULT 0,
  created_by VARCHAR(64),
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64),
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 插入测试数据，admin 密码为 admin123
INSERT INTO t_user (id, username, password_hash, real_name, status) VALUES (1, 'admin', '$2a$10$c4ISGZ.nKFX0iC34wYd.8.OdmgqOLJXsrmyMocQY67X4j9gjoFojq', '系统管理员', 'ENABLED');
INSERT INTO t_role (id, role_code, role_name) VALUES (1, 'ROLE_ADMIN', '系统管理员');
INSERT INTO t_user_role (user_id, role_id) VALUES (1, 1);
INSERT INTO t_permission (id, permission_code, permission_name, type, path, sort_order, status) VALUES (1, 'system:view', '系统管理查看', 'API', '/system', 1, 'ENABLED');
INSERT INTO t_permission (id, permission_code, permission_name, type, path, sort_order, status) VALUES (2, 'special:view', '特别预防查看', 'API', '/special-prevention', 2, 'ENABLED');
INSERT INTO t_permission (id, permission_code, permission_name, type, path, sort_order, status) VALUES (3, 'special:manage', '特别预防管理', 'API', '/special-prevention', 3, 'ENABLED');
INSERT INTO t_permission (id, permission_code, permission_name, type, path, sort_order, status) VALUES (4, 'rectification:view', '整改查看', 'API', '/rectifications', 4, 'ENABLED');
INSERT INTO t_permission (id, permission_code, permission_name, type, path, sort_order, status) VALUES (5, 'rectification:manage', '整改管理', 'API', '/rectifications', 5, 'ENABLED');
INSERT INTO t_permission (id, permission_code, permission_name, type, path, sort_order, status) VALUES (6, 'investigation:view', '调查协查查看', 'API', '/investigations', 6, 'ENABLED');
INSERT INTO t_permission (id, permission_code, permission_name, type, path, sort_order, status) VALUES (7, 'investigation:manage', '调查协查管理', 'API', '/investigations', 7, 'ENABLED');
INSERT INTO t_permission (id, permission_code, permission_name, type, path, sort_order, status) VALUES (8, 'system:user', '系统用户管理', 'API', '/system/users', 8, 'ENABLED');
INSERT INTO t_permission (id, permission_code, permission_name, type, path, sort_order, status) VALUES (9, 'model:view', '模型管理查看', 'API', '/models', 9, 'ENABLED');
INSERT INTO t_permission (id, permission_code, permission_name, type, path, sort_order, status) VALUES (10, 'model:manage', '模型管理操作', 'API', '/models', 10, 'ENABLED');
INSERT INTO t_permission (id, permission_code, permission_name, type, path, sort_order, status) VALUES (11, 'regulation:view', '法规资料库查看', 'API', '/regulation-library', 11, 'ENABLED');
INSERT INTO t_permission (id, permission_code, permission_name, type, path, sort_order, status) VALUES (12, 'regulation:manage', '法规资料库管理', 'API', '/regulation-library', 12, 'ENABLED');
INSERT INTO t_permission (id, permission_code, permission_name, type, path, sort_order, status) VALUES (13, 'MENU_REGULATION_LIBRARY', '法规及资料库菜单', 'MENU', '/regulation-library', 13, 'ENABLED');
INSERT INTO t_role_permission (role_id, permission_id) VALUES (1, 1);
INSERT INTO t_role_permission (role_id, permission_id) VALUES (1, 2);
INSERT INTO t_role_permission (role_id, permission_id) VALUES (1, 3);
INSERT INTO t_role_permission (role_id, permission_id) VALUES (1, 4);
INSERT INTO t_role_permission (role_id, permission_id) VALUES (1, 5);
INSERT INTO t_role_permission (role_id, permission_id) VALUES (1, 6);
INSERT INTO t_role_permission (role_id, permission_id) VALUES (1, 7);
INSERT INTO t_role_permission (role_id, permission_id) VALUES (1, 8);
INSERT INTO t_role_permission (role_id, permission_id) VALUES (1, 9);
INSERT INTO t_role_permission (role_id, permission_id) VALUES (1, 10);
INSERT INTO t_role_permission (role_id, permission_id) VALUES (1, 11);
INSERT INTO t_role_permission (role_id, permission_id) VALUES (1, 12);
INSERT INTO t_role_permission (role_id, permission_id) VALUES (1, 13);
