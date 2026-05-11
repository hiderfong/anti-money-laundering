/**
 * 业务实体类型定义
 * 覆盖反洗钱系统中所有核心业务实体
 */

import type {
  RiskLevel,
  AlertStatus,
  AlertType,
  CaseStatus,
  CaseType,
  CasePriority,
  CustomerType,
  IdType,
  KycStatus,
  TransactionType,
  PaymentMethod,
  MatchType,
  ScreeningReviewStatus,
  ReportStatus,
  AssessmentStatus,
  RectificationStatus,
  LargeTxnReportType,
  NotificationType,
  AuditOperationType,
} from './common'

// ============================================================
// 客户管理
// ============================================================

/** 客户基本信息 */
export interface Customer {
  id: number
  /** 客户姓名/企业名称 */
  name: string
  /** 客户类型：个人/企业 */
  customerType: CustomerType
  /** 证件类型 */
  idType: IdType
  /** 证件号码 */
  idNumber: string
  /** 联系电话 */
  phone: string
  /** 电子邮箱 */
  email: string
  /** 国籍/注册国 */
  nationality: string
  /** 风险等级 */
  riskLevel: RiskLevel
  /** 风险评分 */
  riskScore: number
  /** KYC 审核状态 */
  kycStatus: KycStatus
  /** 账户状态：1-启用 0-禁用 */
  status: number
  /** 联系地址 */
  address: string
  /** 创建时间 */
  createdTime: string
  /** 更新时间 */
  updatedTime: string
}

/** 客户查询参数 */
export interface CustomerQuery {
  name?: string
  customerType?: CustomerType
  idNumber?: string
  riskLevel?: RiskLevel
  kycStatus?: KycStatus
  status?: number
  page: number
  size: number
}

// ============================================================
// 告警管理
// ============================================================

/** 告警信息 */
export interface Alert {
  id: number
  /** 告警编号 */
  alertNo: string
  /** 关联客户 ID */
  customerId: number
  /** 客户姓名 */
  customerName: string
  /** 告警类型 */
  alertType: AlertType
  /** 风险评分 */
  riskScore: number
  /** 风险等级 */
  riskLevel: RiskLevel
  /** 触发规则编码列表 */
  sourceRuleCodes: string[]
  /** 告警摘要 */
  alertSummary: string
  /** 处理状态 */
  status: AlertStatus
  /** 处理人用户 ID */
  assignedTo: number | null
  /** 分配时间 */
  assignedTime: string | null
  /** 处理结果 */
  processResult: string | null
  /** 处理备注 */
  processRemark: string | null
  /** 处理时间 */
  processTime: string | null
  /** 创建时间 */
  createdTime: string
}

/** 告警查询参数 */
export interface AlertQuery {
  alertNo?: string
  customerName?: string
  alertType?: AlertType
  riskLevel?: RiskLevel
  status?: AlertStatus
  assignedTo?: number
  startDate?: string
  endDate?: string
  page: number
  size: number
}

// ============================================================
// 案件管理
// ============================================================

/** 案件信息 */
export interface Case {
  id: number
  /** 案件编号 */
  caseNo: string
  /** 关联告警 ID */
  alertId: number
  /** 关联客户 ID */
  customerId: number
  /** 客户姓名 */
  customerName: string
  /** 案件类型 */
  caseType: CaseType
  /** 案件状态 */
  caseStatus: CaseStatus
  /** 优先级 */
  priority: CasePriority
  /** 案件摘要 */
  summary: string
  /** 创建人用户 ID */
  createdBy: number
  /** 创建时间 */
  createdTime: string
}

/** 案件查询参数 */
export interface CaseQuery {
  caseNo?: string
  customerName?: string
  caseType?: CaseType
  caseStatus?: CaseStatus
  priority?: CasePriority
  startDate?: string
  endDate?: string
  page: number
  size: number
}

// ============================================================
// 交易管理
// ============================================================

/** 交易记录 */
export interface Transaction {
  id: number
  /** 交易流水号 */
  transactionNo: string
  /** 关联策略/产品 ID */
  policyId: number
  /** 客户 ID */
  customerId: number
  /** 交易类型 */
  transactionType: TransactionType
  /** 交易金额 */
  amount: number
  /** 币种 */
  currency: string
  /** 支付方式 */
  paymentMethod: PaymentMethod
  /** 交易渠道 */
  channel: string
  /** 对手方姓名 */
  counterpartyName: string
  /** 对手方账号 */
  counterpartyAccount: string
  /** 对手方银行 */
  counterpartyBank: string
  /** 是否跨境 */
  isCrossBorder: boolean
  /** 交易时间 */
  transactionTime: string
  /** 备注 */
  remark: string
  /** 状态 */
  status: number
  /** 来源系统 */
  sourceSystem: string
  /** 创建时间 */
  createdTime: string
}

/** 交易查询参数 */
export interface TransactionQuery {
  transactionNo?: string
  customerId?: number
  transactionType?: TransactionType
  currency?: string
  paymentMethod?: PaymentMethod
  isCrossBorder?: boolean
  minAmount?: number
  maxAmount?: number
  startDate?: string
  endDate?: string
  page: number
  size: number
}

// ============================================================
// 名单筛查
// ============================================================

/** 名单筛查结果 */
export interface ScreeningResult {
  id: number
  /** 被筛查客户姓名 */
  customerName: string
  /** 命中名单姓名 */
  matchName: string
  /** 匹配分数 (0-100) */
  matchScore: number
  /** 匹配类型 */
  matchType: MatchType
  /** 来源名单/制裁清单 */
  sourceList: string
  /** 审核状态 */
  reviewStatus: ScreeningReviewStatus
  /** 创建时间 */
  createdTime: string
}

/** 名单筛查查询参数 */
export interface ScreeningQuery {
  customerName?: string
  matchType?: MatchType
  reviewStatus?: ScreeningReviewStatus
  minScore?: number
  page: number
  size: number
}

// ============================================================
// 规则管理
// ============================================================

/** 规则定义 */
export interface RuleDefinition {
  id: number
  /** 规则名称 */
  ruleName: string
  /** 规则编码 */
  ruleCode: string
  /** 规则类型 */
  ruleType: string
  /** 规则表达式 (JSON/DSL) */
  ruleExpression: string
  /** 风险评分 */
  riskScore: number
  /** 是否启用 */
  isEnabled: boolean
  /** 规则描述 */
  description: string
  /** 创建时间 */
  createdTime: string
}

/** 规则查询参数 */
export interface RuleQuery {
  ruleName?: string
  ruleCode?: string
  ruleType?: string
  isEnabled?: boolean
  page: number
  size: number
}

// ============================================================
// 产品管理
// ============================================================

/** 产品信息 */
export interface Product {
  id: number
  /** 产品编码 */
  productCode: string
  /** 产品名称 */
  productName: string
  /** 产品类型 */
  productType: string
  /** 风险等级 */
  riskLevel: RiskLevel
  /** 产品描述 */
  description: string
  /** 状态：1-启用 0-禁用 */
  status: number
  /** 创建时间 */
  createdTime: string
}

// ============================================================
// 自评估管理
// ============================================================

/** 自评估 */
export interface SelfAssessment {
  id: number
  /** 评估名称 */
  assessmentName: string
  /** 评估类型 */
  assessmentType: string
  /** 评估状态 */
  status: AssessmentStatus
  /** 总评分 */
  totalScore: number
  /** 评估人 ID */
  assessorId: number
  /** 审批人 ID */
  approverId: number | null
  /** 开始时间 */
  startTime: string
  /** 结束时间 */
  endTime: string | null
  /** 创建时间 */
  createdTime: string
}

/** 评估查询参数 */
export interface AssessmentQuery {
  assessmentName?: string
  assessmentType?: string
  status?: AssessmentStatus
  startDate?: string
  endDate?: string
  page: number
  size: number
}

// ============================================================
// 大额/可疑交易报告
// ============================================================

/** 大额/可疑交易报告 */
export interface LargeTxnReport {
  id: number
  /** 报告编号 */
  reportNo: string
  /** 报告类型：大额/可疑 */
  reportType: LargeTxnReportType
  /** 报告期间 */
  reportPeriod: string
  /** 交易笔数 */
  transactionCount: number
  /** 交易总额 */
  totalAmount: number
  /** 报告状态 */
  reportStatus: ReportStatus
  /** 审核人 ID */
  reviewerId: number | null
  /** 提交时间 */
  submitTime: string | null
  /** 创建时间 */
  createdTime: string
}

// ============================================================
// 可疑交易报告 (STR)
// ============================================================

/** 可疑交易报告 */
export interface StrReport {
  id: number
  /** 关联案件 ID */
  caseId: number
  /** 报告编号 */
  reportNo: string
  /** 报告类型 */
  reportType: string
  /** 报告内容（JSON 或富文本） */
  reportContent: string
  /** 报告状态 */
  reportStatus: ReportStatus
  /** 审核人 ID */
  reviewerId: number | null
  /** 审核备注 */
  reviewRemark: string | null
  /** 提交时间 */
  submitTime: string | null
  /** 创建时间 */
  createdTime: string
}

/** 报告查询参数 */
export interface ReportQuery {
  reportNo?: string
  reportType?: string
  reportStatus?: ReportStatus
  startDate?: string
  endDate?: string
  page: number
  size: number
}

// ============================================================
// 系统管理 — 用户
// ============================================================

/** 系统用户 */
export interface SysUser {
  id: number
  /** 登录用户名 */
  username: string
  /** 真实姓名 */
  realName: string
  /** 联系电话 */
  phone: string
  /** 电子邮箱 */
  email: string
  /** 状态：1-启用 0-禁用 */
  status: number
  /** 角色编码列表 */
  roles: string[]
  /** 创建时间 */
  createdTime: string
}

/** 用户查询参数 */
export interface SysUserQuery {
  username?: string
  realName?: string
  status?: number
  role?: string
  page: number
  size: number
}

// ============================================================
// 系统管理 — 字典
// ============================================================

/** 字典项 */
export interface SysDictItem {
  id: number
  /** 字典项编码 */
  itemCode: string
  /** 字典项显示文本 */
  itemLabel: string
  /** 排序序号 */
  sortOrder: number
  /** 状态：1-启用 0-禁用 */
  status: number
}

/** 字典 */
export interface SysDict {
  id: number
  /** 字典编码 */
  dictCode: string
  /** 字典名称 */
  dictName: string
  /** 描述 */
  description: string
  /** 状态：1-启用 0-禁用 */
  status: number
  /** 字典项列表 */
  items: SysDictItem[]
}

// ============================================================
// 系统管理 — 审计日志
// ============================================================

/** 审计日志 */
export interface SysAuditLog {
  id: number
  /** 操作用户 ID */
  userId: number
  /** 操作用户名 */
  username: string
  /** 操作模块 */
  module: string
  /** 操作类型 */
  operationType: AuditOperationType
  /** 操作描述 */
  description: string
  /** 客户端 IP */
  ip: string
  /** 请求 URL */
  requestUrl: string
  /** 请求方法 */
  requestMethod: string
  /** 创建时间 */
  createdTime: string
}

/** 审计日志查询参数 */
export interface AuditLogQuery {
  username?: string
  module?: string
  operationType?: AuditOperationType
  startDate?: string
  endDate?: string
  page: number
  size: number
}

// ============================================================
// 通知
// ============================================================

/** 系统通知 */
export interface Notification {
  id: number
  /** 通知标题 */
  title: string
  /** 通知内容 */
  content: string
  /** 通知类型 */
  type: NotificationType
  /** 是否已读 */
  isRead: boolean
  /** 创建时间 */
  createdTime: string
}

// ============================================================
// 整改任务
// ============================================================

/** 整改任务 */
export interface RectificationTask {
  id: number
  /** 关联评估 ID */
  assessmentId: number
  /** 问题描述 */
  issueDescription: string
  /** 整改措施 */
  rectificationMeasure: string
  /** 责任人 */
  responsiblePerson: string
  /** 整改期限 */
  deadline: string
  /** 任务状态 */
  status: RectificationStatus
  /** 核实结果 */
  verifyResult: string | null
  /** 创建时间 */
  createdTime: string
}

/** 整改任务查询参数 */
export interface RectificationQuery {
  assessmentId?: number
  status?: RectificationStatus
  responsiblePerson?: string
  page: number
  size: number
}
