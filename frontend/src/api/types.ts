/**
 * API 模块通用类型定义
 * 所有模块共享的分页、响应等通用类型
 */

/** 后端统一响应结构 */
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

/** 分页请求参数 */
export interface PageParams {
  page?: number
  size?: number
  [key: string]: unknown
}

/** 分页响应数据 */
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

// ===================== 认证模块 =====================

export interface LoginParams {
  username: string
  password: string
}

export interface LoginResult {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  userId: number | string
  username: string
  realName: string
  roles: string[]
  permissions: string[]
}

export interface UserInfo {
  userId: number | string
  username: string
  realName: string
  roles: string[]
  permissions: string[]
}

// ===================== 客户(KYC)模块 =====================

export interface CustomerPageParams extends PageParams {
  keyword?: string
  status?: string
  riskLevel?: string
  customerType?: string
  startDate?: string
  endDate?: string
}

export interface CustomerInfo {
  id: string
  customerNo: string
  customerName: string
  customerType: string
  idType: string
  idNumber: string
  nationality: string
  riskLevel: string
  status: string
  phone: string
  email: string
  address: string
  occupation: string
  createdAt: string
  updatedAt: string
}

export interface CustomerCreateParams {
  customerName: string
  customerType: string
  idType: string
  idNumber: string
  nationality: string
  phone?: string
  email?: string
  address?: string
  occupation?: string
  [key: string]: unknown
}

export interface Customer360View {
  customer: CustomerInfo
  transactions: unknown[]
  alerts: unknown[]
  cases: unknown[]
  riskAssessments: unknown[]
  screenings: unknown[]
}

export interface RiskAssessmentResult {
  customerId: string
  riskScore: number
  riskLevel: string
  factors: { name: string; score: number; description: string }[]
  assessedAt: string
}

// ===================== 筛查模块 =====================

export interface ScreeningParams {
  name: string
  idNumber?: string
  dateOfBirth?: string
  nationality?: string
  screeningType?: string
}

export interface BatchScreeningParams {
  items: ScreeningParams[]
  screeningType?: string
}

export interface ScreeningResultPageParams extends PageParams {
  status?: string
  matchType?: string
  startDate?: string
  endDate?: string
}

export interface ScreeningResult {
  id: string
  requestId: string
  customerId: string
  customerName: string
  customerIdNumber?: string
  watchlistEntryId?: string
  watchlistName: string
  matchScore: number
  matchType: string
  matchField?: string
  matchDetail?: string
  reviewStatus: string
  reviewResult?: string
  reviewReason?: string
  reviewedBy?: string
  reviewedTime?: string
  whitelisted?: boolean
  createdTime: string
}

export interface ReviewParams {
  decision: string
  reason: string
  comment?: string
}

export interface WhitelistItem {
  id: string
  name: string
  idNumber: string
  listType: string
  reason: string
  addedBy: string
  addedAt: string
  expiresAt: string
}

export interface WhitelistAddParams {
  name: string
  idNumber?: string
  listType: string
  reason: string
  expiresAt?: string
}

// ===================== 交易监控模块 =====================

export interface TransactionPageParams extends PageParams {
  customerId?: string
  transactionType?: string
  minAmount?: number
  maxAmount?: number
  startDate?: string
  endDate?: string
  status?: string
  keyword?: string
}

export interface TransactionRecord {
  id: string
  transactionNo: string
  customerId: string
  customerName: string
  transactionType: string
  amount: number
  currency: string
  counterpartyName: string
  counterpartyAccount: string
  transactionDate: string
  status: string
  suspiciousFlag: boolean
  createdAt: string
}

export interface DailySummaryParams {
  startDate: string
  endDate: string
  customerId?: string
}

export interface DailySummary {
  date: string
  totalCount: number
  totalAmount: number
  suspiciousCount: number
  typeBreakdown: Record<string, { count: number; amount: number }>
}

// ===================== 规则管理模块 =====================

export interface RulePageParams extends PageParams {
  ruleName?: string
  ruleType?: string
  status?: string
}

export interface RuleInfo {
  id: string
  ruleCode: string
  ruleName: string
  ruleType: string
  description: string
  condition: string
  action: string
  status: string
  version: number
  createdBy: string
  createdAt: string
  updatedAt: string
}

export interface RuleCreateParams {
  ruleName: string
  ruleType: string
  description: string
  condition: string
  action: string
  [key: string]: unknown
}

export interface RuleVersion {
  id: string
  ruleId: string
  version: number
  condition: string
  action: string
  changedBy: string
  changedAt: string
  changeReason: string
}

// ===================== 告警管理模块 =====================

export interface AlertPageParams extends PageParams {
  alertLevel?: string
  alertType?: string
  status?: string
  assignee?: string
  startDate?: string
  endDate?: string
  keyword?: string
}

export interface AlertInfo {
  id: string
  alertNo: string
  alertType: string
  alertLevel: string
  alertSource: string
  customerId: string
  customerName: string
  description: string
  status: string
  assignee: string
  assigneeName: string
  ruleId: string
  ruleName: string
  createdAt: string
  updatedAt: string
  processedAt: string
}

export interface AlertAssignParams {
  assigneeId: string
  comment?: string
}

export interface AlertProcessParams {
  decision: string
  reason: string
  comment?: string
  escalateTo?: string
}

export interface BatchProcessParams {
  alertIds: string[]
  decision: string
  reason: string
  comment?: string
}

export interface AlertStatistics {
  countByStatus: Record<string, number | string>
  countByRiskLevel: Record<string, number | string>
  totalCount: number | string
}

// ===================== 案件管理模块 =====================

export interface CasePageParams extends PageParams {
  caseNo?: string
  caseType?: string
  status?: string
  priority?: string
  assignee?: string
  startDate?: string
  endDate?: string
}

export interface CaseInfo {
  id: string
  caseNo: string
  caseType: string
  title: string
  description: string
  status: string
  priority: string
  assignee: string
  assigneeName: string
  customerId: string
  customerName: string
  relatedAlertIds: string[]
  createdAt: string
  updatedAt: string
  closedAt: string
}

export interface CaseCreateParams {
  caseType: string
  title: string
  description: string
  priority: string
  customerId?: string
  relatedAlertIds?: string[]
  assigneeId?: string
  [key: string]: unknown
}

export interface CaseStatusChangeParams {
  status: string
  reason?: string
  comment?: string
}

export interface InvestigationParams {
  content: string
  attachments?: string[]
  conclusion?: string
}

export interface CaseCloseParams {
  conclusion: string
  closeReason: string
  attachments?: string[]
}

// ===================== STR 报告模块 =====================

export interface STRCreateParams {
  caseId: string
  customerId: string
  suspicionType: string
  description: string
  transactionPeriod: { startDate: string; endDate: string }
  totalAmount: number
  currency: string
  involvedAccounts: string[]
  supportingEvidence: string[]
  [key: string]: unknown
}

export interface STRReviewParams {
  decision: string
  comment: string
  revisionRequired?: string
}

export interface STRReport {
  id: string
  reportNo: string
  caseId: string
  customerId: string
  customerName: string
  suspicionType: string
  description: string
  status: string
  reviewer: string
  submittedToRegulatorAt: string
  createdAt: string
  updatedAt: string
}

// ===================== 报送管理模块 =====================

export interface ReportingPageParams extends PageParams {
  reportType?: string
  status?: string
  reportStatus?: string
  startDate?: string
  endDate?: string
}

export interface ReportingRecord {
  id: string
  reportNo: string
  customerId?: string
  customerName?: string
  transactionId?: string
  reportDate: string
  transactionTime: string
  transactionType: string
  amount: number
  currency: string
  paymentMethod?: string
  counterpartyInfo?: string
  reportStatus: string
  reviewedBy?: string
  reviewedTime?: string
  submittedBy?: string
  submittedTime?: string
  xmlContent?: string
  submitResponse?: string
  createdTime: string
  updatedTime?: string
}

export interface ReportReviewParams {
  decision: string
  comment: string
}

// ===================== 产品管理模块 =====================

export interface ProductPageParams extends PageParams {
  productName?: string
  productType?: string
  status?: string
  riskLevel?: string
}

export interface ProductInfo {
  id: string
  productCode: string
  productName: string
  productType: string
  description: string
  riskLevel: string
  status: string
  createdAt: string
  updatedAt: string
}

export interface ProductCreateParams {
  productName: string
  productType: string
  description: string
  [key: string]: unknown
}

export interface ProductAssessment {
  id: string
  productId: string
  riskScore: number
  riskLevel: string
  assessedBy: string
  assessedAt: string
  factors: { name: string; score: number; description: string }[]
}

// ===================== 自评模块 =====================

export interface AssessmentListParams extends PageParams {
  status?: string
  assessmentType?: string
  startDate?: string
  endDate?: string
}

export interface AssessmentInfo {
  id: string
  assessmentNo: string
  assessmentType: string
  title: string
  status: string
  totalScore: number
  createdBy: string
  createdAt: string
  updatedAt: string
  completedAt: string
  approvedBy: string
}

export interface AssessmentCreateParams {
  assessmentType: string
  title: string
  templateId?: string
  items: { category: string; question: string; weight: number }[]
}

export interface ScoreParams {
  items: { itemId: string; score: number; comment?: string }[]
}

export interface ApproveParams {
  decision: string
  comment: string
}

// ===================== 整改模块 =====================

export interface RectificationListParams extends PageParams {
  status?: string
  assessmentId?: string
  startDate?: string
  endDate?: string
}

export interface RectificationInfo {
  id: string
  rectificationNo: string
  assessmentId: string
  description: string
  status: string
  assignedTo: string
  dueDate: string
  completedAt: string
  verifiedBy: string
  verifiedAt: string
  createdAt: string
}

export interface RectificationCreateParams {
  assessmentId: string
  description: string
  assignedTo: string
  dueDate: string
  correctiveActions: string[]
}

export interface RectificationStatusParams {
  status: string
  comment?: string
  attachments?: string[]
}

// ===================== 仪表盘模块 =====================

export interface DashboardOverview {
  totalCustomers: number
  totalAlerts: number
  pendingAlerts: number
  totalCases: number
  openCases: number
  todayTransactions: number
  recentAlertTrend: { date: string; count: number }[]
}

export interface AlertTrendParams {
  startDate: string
  endDate: string
  granularity?: 'day' | 'week' | 'month'
}

export interface AlertTrendItem {
  date: string
  count: number
  level: string
}

export interface AlertStatisticsByType {
  byType: { type: string; count: number }[]
  byLevel: { level: string; count: number }[]
  byStatus: { status: string; count: number }[]
}

// ===================== 模型管理模块 =====================

export interface ModelPageParams extends PageParams {
  keyword?: string
  modelType?: string
  scenario?: string
  lifecycleStatus?: string
  riskLevel?: string
}

export interface AmlModel {
  id: string
  modelCode: string
  modelName: string
  modelType: string
  scenario: string
  algorithmType: string
  version: string
  lifecycleStatus: string
  owner: string
  governanceLevel: string
  riskLevel: string
  trainingDataset: string
  validationDataset: string
  testResult: string
  lastTestTime: string
  deploymentEnv: string
  deployedTime: string
  monitorStatus: string
  precisionRate: number
  recallRate: number
  falsePositiveRate: number
  driftScore: number
  lastMonitorTime: string
  iterationPlan: string
  archiveReason: string
  archivedTime: string
  description: string
  configJson: string
  createdTime: string
  updatedTime: string
}

export interface ModelOverview {
  totalModels: number
  draftModels: number
  testingModels: number
  deployedModels: number
  monitoringModels: number
  iterationModels: number
  archivedModels: number
  attentionModels: number
  averageFalsePositiveRate: number
  averageDriftScore: number
}

export interface ModelCreateParams {
  modelCode: string
  modelName: string
  modelType: string
  scenario: string
  algorithmType?: string
  version?: string
  lifecycleStatus?: string
  owner?: string
  governanceLevel?: string
  riskLevel?: string
  trainingDataset?: string
  validationDataset?: string
  iterationPlan?: string
  description?: string
  configJson?: string
}

export interface ModelLifecycleParams {
  operator?: string
  actionSummary?: string
  artifactRef?: string
  deploymentEnv?: string
  testDataset?: string
  targetVersion?: string
  iterationPlan?: string
  archiveReason?: string
  monitorStatus?: string
  precisionRate?: number
  recallRate?: number
  falsePositiveRate?: number
  driftScore?: number
}

export interface ModelLifecycleLog {
  id: string
  modelId: string
  modelCode: string
  actionType: string
  fromStatus: string
  toStatus: string
  operator: string
  actionTime: string
  actionSummary: string
  resultMetric: string
  artifactRef: string
}

export interface AiRiskReviewPoolParams extends PageParams {
  subjectType?: string
  riskLevel?: string
  autoLabel?: string
  minScore?: number
  modelCode?: string
  pendingOnly?: boolean
}

export interface AiRiskReviewPoolItem {
  id: string
  scoreNo: string
  subjectType: string
  subjectId: string
  subjectName: string
  modelCode: string
  modelName: string
  modelVersion: string
  score: number
  riskLevel: string
  confidence: number
  autoLabel: string
  autoLabelText: string
  reviewStatus: string
  priorityLevel: string
  verificationBasis: string
  factorSummary: string
  scoredAt: string
  manualReviewLabel?: string
  manualReviewLabelText?: string
  manualReviewComment?: string
  reviewedBy?: string
  reviewedAt?: string
  followUpTaskId?: string
  followUpCreatedAt?: string
  followUpCreatedBy?: string
}

export interface AiRiskReviewPoolOverview {
  totalScores: number | string
  pendingReviewCount: number | string
  likelyTruePositiveCount: number | string
  likelyFalsePositiveCount: number | string
  unconfirmedCount: number | string
  highOrCriticalCount: number | string
  corroboratedCount: number | string
  highScoreNoRuleHitCount: number | string
  lowScoreWithDispositionCount: number | string
  latestScoredAt: string
}

export interface AiRiskReviewRequest {
  reviewLabel: string
  reviewComment?: string
  reviewer?: string
}

export interface AiRiskFollowUpTaskRequest {
  taskType?: 'RECTIFICATION' | 'MONITORING'
  issueCategory?: string
  severity?: 'HIGH' | 'MEDIUM' | 'LOW'
  responsibleDept?: string
  responsiblePerson?: string
  deadline?: string
  comment?: string
}

// ===================== 法规及资料库模块 =====================

export interface RegulationCategory {
  id: string
  categoryCode: string
  categoryName: string
  categoryType: string
  parentId: string
  sortOrder: number
  status: string
  description: string
  createdTime: string
  updatedTime: string
}

export interface RegulationCategoryParams {
  categoryCode: string
  categoryName: string
  categoryType?: string
  parentId?: string
  sortOrder?: number
  status?: string
  description?: string
}

export interface RegulationDocumentPageParams extends PageParams {
  keyword?: string
  docType?: string
  categoryId?: string
  status?: string
  sourceType?: string
  importantFlag?: boolean
}

export interface RegulationDocument {
  id: string
  docCode: string
  title: string
  docType: string
  categoryId: string
  categoryName: string
  sourceType: string
  sourceOrg: string
  publishDate: string
  effectiveDate: string
  status: string
  importantFlag: boolean
  summary: string
  content: string
  tags: string
  referenceUrl: string
  attachmentRef: string
  viewCount: number
  createdTime: string
  updatedTime: string
}

export interface RegulationDocumentParams {
  docCode: string
  title: string
  docType: string
  categoryId?: string
  sourceType?: string
  sourceOrg?: string
  publishDate?: string
  effectiveDate?: string
  status?: string
  importantFlag?: boolean
  summary?: string
  content?: string
  tags?: string
  referenceUrl?: string
  attachmentRef?: string
}

export interface RegulationOverview {
  totalDocuments: number
  regulationDocuments: number
  policyDocuments: number
  trainingDocuments: number
  regulatoryUpdates: number
  industryUpdates: number
  publishedDocuments: number
  importantDocuments: number
  categories: number
}

// ===================== 系统管理模块 =====================

export interface UserPageParams extends PageParams {
  username?: string
  realName?: string
  status?: number
  role?: string
}

export interface SysUser {
  id: string
  username: string
  realName: string
  email: string
  phone: string
  status: number
  roles: string[]
  createdAt: string
  lastLoginTime: string
}

export interface UserCreateParams {
  username: string
  password: string
  realName: string
  email: string
  phone: string
  roles: string[]
}

export interface UserUpdateParams {
  realName?: string
  email?: string
  phone?: string
  status?: number
  roles?: string[]
}

// ===================== 字典模块 =====================

export interface DictInfo {
  id: string
  dictCode: string
  dictName: string
  description: string
  status: number
}

export interface DictItem {
  id: string
  dictCode: string
  itemCode: string
  itemName: string
  sortOrder: number
  status: number
}

// ===================== 通知模块 =====================

export interface NotificationParams extends PageParams {
  type?: string
  isRead?: boolean
}

export interface NotificationInfo {
  id: string
  type: string
  title: string
  content: string
  isRead?: boolean
  read?: boolean
  readTime?: string
  relatedType?: string
  relatedId?: string
  createdTime?: string
  createdAt?: string
}

// ===================== 审计日志模块 =====================

export interface AuditLogPageParams extends PageParams {
  module?: string
  action?: string
  operator?: string
  startDate?: string
  endDate?: string
  keyword?: string
}

export interface AuditLog {
  id: string
  module: string
  action: string
  targetId: string
  targetType: string
  operatorId: string
  operatorName: string
  ipAddress: string
  details: Record<string, unknown>
  createdAt: string
}

// ===================== 图谱分析模块 =====================

export interface CircularDetectionParams {
  customerId: string
  depth?: number
  minAmount?: number
  startDate?: string
  endDate?: string
}

export interface FlowTraceParams {
  customerId?: string
  transactionId?: string
  depth?: number
  direction?: 'upstream' | 'downstream' | 'both'
}

export interface SharedAccountParams {
  accountNumber?: string
  customerId?: string
  threshold?: number
}

export interface HighDensityParams {
  customerId?: string
  startDate: string
  endDate: string
  minTransactions?: number
  amountThreshold?: number
}

export interface GraphNode {
  id: string
  label: string
  type: string
  properties: Record<string, unknown>
}

export interface GraphEdge {
  id: string
  source: string
  target: string
  label: string
  properties: Record<string, unknown>
}

export interface GraphResult {
  nodes: GraphNode[]
  edges: GraphEdge[]
  summary: Record<string, unknown>
}

export interface RingTransactionResult {
  detected: boolean
  pathNodes: Array<{
    type: string
    id: string
    name: string
    amount?: number
    bank?: string
  }>
}

export interface MultiLayerTransferResult {
  startCustomerId: string
  startCustomerName: string
  chains: Array<{
    depth: number
    fromCustomerId: string
    fromCustomerName: string
    toCustomerId: string
    toCustomerName: string
    amount?: number
    viaAccount?: string
  }>
  maxDepth: number
  suspicious: boolean
}

export interface SharedAccountResult {
  customerId: string
  detected: boolean
  sharedAccounts: Array<{
    accountNo: string
    bank?: string
    relatedCustomerId: string
    relatedCustomerName: string
  }>
}

export interface NetworkDensityResult {
  customerId: string
  customerName: string
  relatedCustomerCount: number
  transactionCount: number
  totalAmount: number
  densityAlert: boolean
  counterparties: Array<{
    counterpartyId: string
    counterpartyName: string
    transactionCount: number
    totalAmount: number
  }>
}

// ===================== 反馈模块 =====================

export interface FeedbackSummary {
  totalRules: number
  avgConfirmationRate: number
  avgFalsePositiveRate: number
  tightenCount: number
  relaxCount: number
  insufficientDataCount: number
  ruleDetails: RuleFeedback[]
}

export interface RuleFeedbackParams extends PageParams {
  ruleId?: string
  ruleCode?: string
  feedbackType?: string
  startDate?: string
  endDate?: string
}

export interface RuleFeedback {
  ruleId: string
  ruleCode: string
  ruleName: string
  ruleCategory: string
  status: string
  configJson?: string
  totalHits: number
  alertCount: number
  confirmedCount: number
  excludedCount: number
  escalatedCount: number
  pendingCount: number
  confirmationRate: number
  falsePositiveRate: number
  escalationRate: number
  processingRate: number
  adjustmentDirection: string
  adjustmentReason: string
  adjustmentDetail?: string
  statsPeriod: string
  calculatedAt: string
}

export type AttentionItem = RuleFeedback
