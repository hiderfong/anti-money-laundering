/**
 * 通用工具类型定义
 * 包含状态枚举、风险等级等跨模块共享的基础类型
 */

/** Element Plus Tag 组件的 type 属性值 */
export type StatusTagType = 'success' | 'warning' | 'danger' | 'info' | 'primary'

/** 风险等级 */
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'VERY_HIGH' | 'NOT_ASSESSED'

/** 风险等级中文映射 */
export const RiskLevelLabel: Record<RiskLevel, string> = {
  LOW: '低风险',
  MEDIUM: '中风险',
  HIGH: '高风险',
  VERY_HIGH: '极高风险',
  NOT_ASSESSED: '未评估',
}

/** 风险等级对应的 Tag 类型 */
export const RiskLevelTagType: Record<RiskLevel, StatusTagType> = {
  LOW: 'success',
  MEDIUM: 'warning',
  HIGH: 'danger',
  VERY_HIGH: 'danger',
  NOT_ASSESSED: 'info',
}

/** 告警状态 */
export type AlertStatus = 'NEW' | 'ASSIGNED' | 'PROCESSING' | 'CONFIRMED' | 'EXCLUDED'

export const AlertStatusLabel: Record<AlertStatus, string> = {
  NEW: '新建',
  ASSIGNED: '已分配',
  PROCESSING: '处理中',
  CONFIRMED: '已确认',
  EXCLUDED: '已排除',
}

export const AlertStatusTagType: Record<AlertStatus, StatusTagType> = {
  NEW: 'info',
  ASSIGNED: 'warning',
  PROCESSING: 'primary',
  CONFIRMED: 'success',
  EXCLUDED: 'danger',
}

/** 案件状态 */
export type CaseStatus = 'DRAFT' | 'INVESTIGATING' | 'PENDING_APPROVAL' | 'SUBMITTED' | 'CLOSED'

export const CaseStatusLabel: Record<CaseStatus, string> = {
  DRAFT: '草稿',
  INVESTIGATING: '调查中',
  PENDING_APPROVAL: '待审批',
  SUBMITTED: '已报送',
  CLOSED: '已关闭',
}

export const CaseStatusTagType: Record<CaseStatus, StatusTagType> = {
  DRAFT: 'info',
  INVESTIGATING: 'primary',
  PENDING_APPROVAL: 'warning',
  SUBMITTED: 'success',
  CLOSED: 'danger',
}

/** 报告状态 */
export type ReportStatus = 'PENDING' | 'APPROVED' | 'SUBMITTED' | 'REJECTED'

export const ReportStatusLabel: Record<ReportStatus, string> = {
  PENDING: '待审核',
  APPROVED: '已通过',
  SUBMITTED: '已报送',
  REJECTED: '已退回',
}

export const ReportStatusTagType: Record<ReportStatus, StatusTagType> = {
  PENDING: 'warning',
  APPROVED: 'success',
  SUBMITTED: 'success',
  REJECTED: 'danger',
}

/** 评估状态 */
export type AssessmentStatus = 'CREATED' | 'SCORED' | 'COMPLETED' | 'APPROVED' | 'REJECTED'

export const AssessmentStatusLabel: Record<AssessmentStatus, string> = {
  CREATED: '已创建',
  SCORED: '已评分',
  COMPLETED: '已完成',
  APPROVED: '已批准',
  REJECTED: '已退回',
}

export const AssessmentStatusTagType: Record<AssessmentStatus, StatusTagType> = {
  CREATED: 'info',
  SCORED: 'warning',
  COMPLETED: 'primary',
  APPROVED: 'success',
  REJECTED: 'danger',
}

/** KYC 状态 */
export type KycStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'REJECTED'

export const KycStatusLabel: Record<KycStatus, string> = {
  PENDING: '待处理',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成',
  REJECTED: '已拒绝',
}

/** 通用启用/禁用状态 */
export type EnableStatus = 0 | 1

/** 性别 */
export type Gender = 'MALE' | 'FEMALE' | 'UNKNOWN'

/** 客户类型 */
export type CustomerType = 'INDIVIDUAL' | 'CORPORATE'

/** 证件类型 */
export type IdType = 'ID_CARD' | 'PASSPORT' | 'BUSINESS_LICENSE' | 'OTHER'

/** 交易类型 */
export type TransactionType = 'TRANSFER' | 'DEPOSIT' | 'WITHDRAWAL' | 'PAYMENT' | 'OTHER'

/** 支付方式 */
export type PaymentMethod = 'CASH' | 'WIRE' | 'CARD' | 'CHECK' | 'ONLINE' | 'OTHER'

/** 告警类型 */
export type AlertType = 'RULE_HIT' | 'MANUAL' | 'THIRD_PARTY' | 'OTHER'

/** 案件类型 */
export type CaseType = 'STR' | 'AML' | 'FRAUD' | 'OTHER'

/** 案件优先级 */
export type CasePriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'

export const CasePriorityLabel: Record<CasePriority, string> = {
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
  URGENT: '紧急',
}

export const CasePriorityTagType: Record<CasePriority, StatusTagType> = {
  LOW: 'info',
  MEDIUM: 'warning',
  HIGH: 'danger',
  URGENT: 'danger',
}

/** 通知类型 */
export type NotificationType = 'SYSTEM' | 'ALERT' | 'CASE' | 'ASSESSMENT' | 'REPORT'

/** 匹配类型（名单筛查） */
export type MatchType = 'EXACT' | 'FUZZY' | 'PARTIAL'

/** 筛查审核状态 */
export type ScreeningReviewStatus = 'PENDING' | 'CONFIRMED' | 'EXCLUDED'

/** 整改任务状态 */
export type RectificationStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'VERIFIED'

/** 大额/可疑报告类型 */
export type LargeTxnReportType = 'LARGE' | 'SUSPICIOUS'

/** 审计操作类型 */
export type AuditOperationType = 'CREATE' | 'UPDATE' | 'DELETE' | 'LOGIN' | 'LOGOUT' | 'EXPORT' | 'IMPORT' | 'OTHER'

/** 通用排序方向 */
export type SortOrder = 'asc' | 'desc'

/** 通用选项（用于下拉框等） */
export interface SelectOption {
  label: string
  value: string | number
  disabled?: boolean
}

/** 通用键值对 */
export interface KeyValuePair<T = string> {
  key: string
  value: T
}

/** 日期范围 */
export interface DateRange {
  startDate: string
  endDate: string
}
