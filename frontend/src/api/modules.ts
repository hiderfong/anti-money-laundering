/**
 * API 模块统一定义
 * 按业务模块组织所有后端接口调用，统一 TypeScript 类型约束
 */
import request from '@/utils/request'
import type {
  ApiResponse,
  PageResult,
  // 认证
  LoginParams,
  LoginResult,
  UserInfo,
  // 客户
  CustomerPageParams,
  CustomerInfo,
  CustomerCreateParams,
  Customer360View,
  RiskAssessmentResult,
  // 筛查
  ScreeningParams,
  BatchScreeningParams,
  ScreeningResultPageParams,
  ScreeningResult,
  ReviewParams,
  WhitelistItem,
  WhitelistAddParams,
  // 交易
  TransactionPageParams,
  TransactionRecord,
  DailySummaryParams,
  DailySummary,
  // 规则
  RulePageParams,
  RuleInfo,
  RuleCreateParams,
  RuleVersion,
  // 告警
  AlertPageParams,
  AlertInfo,
  AlertAssignParams,
  AlertProcessParams,
  BatchProcessParams,
  AlertStatistics,
  // 案件
  CasePageParams,
  CaseInfo,
  CaseCreateParams,
  CaseStatusChangeParams,
  InvestigationParams,
  CaseCloseParams,
  // STR
  STRCreateParams,
  STRReviewParams,
  STRReport,
  // 报送
  ReportingPageParams,
  ReportingRecord,
  ReportReviewParams,
  // 产品
  ProductPageParams,
  ProductInfo,
  ProductCreateParams,
  ProductAssessment,
  // 自评
  AssessmentListParams,
  AssessmentInfo,
  AssessmentCreateParams,
  ScoreParams,
  ApproveParams,
  // 整改
  RectificationListParams,
  RectificationInfo,
  RectificationCreateParams,
  RectificationStatusParams,
  // 仪表盘
  DashboardOverview,
  AlertTrendParams,
  AlertTrendItem,
  AlertStatisticsByType,
  // 系统
  UserPageParams,
  SysUser,
  UserCreateParams,
  UserUpdateParams,
  // 字典
  DictInfo,
  DictItem,
  // 通知
  NotificationParams,
  NotificationInfo,
  // 审计日志
  AuditLogPageParams,
  AuditLog,
  // 图谱
  CircularDetectionParams,
  FlowTraceParams,
  SharedAccountParams,
  HighDensityParams,
  GraphResult,
  GraphSummary,
  // 反馈
  FeedbackSummary,
  RuleFeedbackParams,
  RuleFeedback,
  AttentionItem,
} from './types'

// ====== 认证模块 ======
export const authApi = {
  /** 用户登录 */
  login(data: LoginParams) {
    return request.post<ApiResponse<LoginResult>>('/auth/login', data)
  },
  /** 用户登出 */
  logout() {
    return request.post<ApiResponse<void>>('/auth/logout')
  },
  /** 刷新 token */
  refreshToken() {
    return request.post<ApiResponse<LoginResult>>('/auth/refresh-token')
  },
  /** 获取当前用户信息 */
  getProfile() {
    return request.get<ApiResponse<UserInfo>>('/auth/current-user')
  },
}

// ====== 客户(KYC)模块 ======
export const customerApi = {
  /** 分页查询客户列表 */
  getPage(params: CustomerPageParams) {
    return request.get<ApiResponse<PageResult<CustomerInfo>>>('/customers/page', { params })
  },
  /** 根据 ID 获取客户详情 */
  getById(id: string) {
    return request.get<ApiResponse<CustomerInfo>>(`/customers/${id}`)
  },
  /** 创建客户 */
  create(data: CustomerCreateParams) {
    return request.post<ApiResponse<CustomerInfo>>('/customers', data)
  },
  /** 更新客户信息 */
  update(id: string, data: Partial<CustomerCreateParams>) {
    return request.put<ApiResponse<CustomerInfo>>(`/customers/${id}`, data)
  },
  /** 获取客户 360 视图 */
  get360(id: string) {
    return request.get<ApiResponse<Customer360View>>(`/customers/${id}/360-view`)
  },
  /** 客户风险评估 */
  riskAssessment(id: string) {
    return request.post<ApiResponse<RiskAssessmentResult>>(`/customers/${id}/assess-risk`)
  },
}

// ====== 筛查模块 ======
export const screeningApi = {
  /** 单条筛查 */
  screen(data: ScreeningParams) {
    return request.post<ApiResponse<ScreeningResult>>('/screening/screen', data)
  },
  /** 批量筛查 */
  batchScreen(data: BatchScreeningParams) {
    return request.post<ApiResponse<ScreeningResult[]>>('/screening/batch-screen', data)
  },
  /** 分页查询筛查结果 */
  getResults(params: ScreeningResultPageParams) {
    return request.get<ApiResponse<PageResult<ScreeningResult>>>('/screening/results', { params })
  },
  /** 审核命中记录 */
  review(id: string, data: ReviewParams) {
    return request.post<ApiResponse<void>>(`/screening/results/${id}/review`, data)
  },
  /** 分页查询白名单 */
  getWhitelist(params: { page?: number; size?: number; keyword?: string }) {
    return request.get<ApiResponse<PageResult<WhitelistItem>>>('/screening/whitelist', { params })
  },
  /** 添加白名单 */
  addWhitelist(data: WhitelistAddParams) {
    return request.post<ApiResponse<WhitelistItem>>('/screening/whitelist', data)
  },
}

// ====== 交易监控模块 ======
export const transactionApi = {
  /** 分页查询交易记录 */
  getPage(params: TransactionPageParams) {
    return request.get<ApiResponse<PageResult<TransactionRecord>>>('/transactions/page', { params })
  },
  /** 交易日汇总 */
  getDailySummary(params: DailySummaryParams) {
    return request.get<ApiResponse<DailySummary[]>>('/transactions/daily-summary', { params })
  },
}

// ====== 规则管理模块 ======
export const ruleApi = {
  /** 分页查询规则 */
  getPage(params: RulePageParams) {
    return request.get<ApiResponse<PageResult<RuleInfo>>>('/rules/page', { params })
  },
  /** 创建规则 */
  create(data: RuleCreateParams) {
    return request.post<ApiResponse<RuleInfo>>('/rules', data)
  },
  /** 更新规则 */
  update(id: string, data: Partial<RuleCreateParams>) {
    return request.put<ApiResponse<RuleInfo>>(`/rules/${id}`, data)
  },
  /** 启用规则 */
  enable(id: string) {
    return request.post<ApiResponse<void>>(`/rules/${id}/enable`)
  },
  /** 禁用规则 */
  disable(id: string) {
    return request.post<ApiResponse<void>>(`/rules/${id}/disable`)
  },
  /** 获取规则版本历史 */
  getVersions(id: string) {
    return request.get<ApiResponse<RuleVersion[]>>(`/rules/${id}/versions`)
  },
}

// ====== 告警管理模块 ======
export const alertApi = {
  /** 分页查询告警 */
  getPage(params: AlertPageParams) {
    return request.get<ApiResponse<PageResult<AlertInfo>>>('/alerts/page', { params })
  },
  /** 获取告警详情 */
  getById(id: string) {
    return request.get<ApiResponse<AlertInfo>>(`/alerts/${id}`)
  },
  /** 告警分配 */
  assign(id: string, data: AlertAssignParams) {
    return request.post<ApiResponse<void>>(`/alerts/${id}/assign`, data)
  },
  /** 处理告警 */
  process(id: string, data: AlertProcessParams) {
    return request.post<ApiResponse<void>>(`/alerts/${id}/process`, data)
  },
  /** 批量处理告警 */
  batchProcess(data: BatchProcessParams) {
    return request.post<ApiResponse<void>>('/alerts/batch-process', data)
  },
  /** 告警统计数据 */
  getStatistics() {
    return request.get<ApiResponse<AlertStatistics>>('/alerts/statistics')
  },
}

// ====== 案件管理模块 ======
export const caseApi = {
  /** 分页查询案件 */
  getPage(params: CasePageParams) {
    return request.get<ApiResponse<PageResult<CaseInfo>>>('/cases/page', { params })
  },
  /** 获取案件详情 */
  getById(id: string) {
    return request.get<ApiResponse<CaseInfo>>(`/cases/${id}`)
  },
  /** 创建案件 */
  create(data: CaseCreateParams) {
    return request.post<ApiResponse<CaseInfo>>('/cases', data)
  },
  /** 变更案件状态 */
  changeStatus(id: string, data: CaseStatusChangeParams) {
    return request.post<ApiResponse<void>>(`/cases/${id}/change-status`, data)
  },
  /** 添加调查记录 */
  addInvestigation(id: string, data: InvestigationParams) {
    return request.post<ApiResponse<void>>(`/cases/${id}/investigations`, data)
  },
  /** 关闭案件 */
  close(id: string, data: CaseCloseParams) {
    return request.post<ApiResponse<void>>(`/cases/${id}/close`, data)
  },
}

// ====== STR 报告模块 ======
export const strReportApi = {
  /** 创建可疑交易报告 */
  create(data: STRCreateParams) {
    return request.post<ApiResponse<STRReport>>('/str-reports', data)
  },
  /** 提交审核 */
  submitReview(id: string) {
    return request.post<ApiResponse<void>>(`/str-reports/${id}/submit-review`)
  },
  /** 审核报告 */
  review(id: string, data: STRReviewParams) {
    return request.post<ApiResponse<void>>(`/str-reports/${id}/review`, data)
  },
  /** 提交至监管机构 */
  submitRegulator(id: string) {
    return request.post<ApiResponse<void>>(`/str-reports/${id}/submit-regulator`)
  },
  /** 获取报告详情 */
  getById(id: string) {
    return request.get<ApiResponse<STRReport>>(`/str-reports/${id}`)
  },
}

// ====== 报送管理模块 ======
export const reportingApi = {
  /** 分页查询报告 */
  getPage(params: ReportingPageParams) {
    return request.get<ApiResponse<PageResult<ReportingRecord>>>('/reporting/page', { params })
  },
  /** 生成报告 */
  generate(data: { reportType: string; startDate: string; endDate: string; [key: string]: unknown }) {
    return request.post<ApiResponse<ReportingRecord>>('/reporting/generate', data)
  },
  /** 审核报告 */
  review(id: string, data: ReportReviewParams) {
    return request.post<ApiResponse<void>>(`/reporting/${id}/review`, data)
  },
  /** 提交报告 */
  submit(id: string) {
    return request.post<ApiResponse<void>>(`/reporting/${id}/submit`)
  },
  /** 导出 XML */
  exportXml(id: string) {
    return request.get<string>(`/reporting/${id}/export-xml`, { responseType: 'text' as any })
  },
  /** 重试失败报告 */
  retryFailed(id: string) {
    return request.post<ApiResponse<void>>(`/reporting/${id}/retry`)
  },
}

// ====== 产品管理模块 ======
export const productApi = {
  /** 分页查询产品 */
  getPage(params: ProductPageParams) {
    return request.get<ApiResponse<PageResult<ProductInfo>>>('/products/page', { params })
  },
  /** 获取产品详情 */
  getById(id: string) {
    return request.get<ApiResponse<ProductInfo>>(`/products/${id}`)
  },
  /** 创建产品 */
  create(data: ProductCreateParams) {
    return request.post<ApiResponse<ProductInfo>>('/products', data)
  },
  /** 更新产品 */
  update(id: string, data: Partial<ProductCreateParams>) {
    return request.put<ApiResponse<ProductInfo>>(`/products/${id}`, data)
  },
  /** 产品风险评估 */
  assess(id: string) {
    return request.post<ApiResponse<ProductAssessment>>(`/products/${id}/assess`)
  },
  /** 获取产品评估历史 */
  getAssessments(id: string) {
    return request.get<ApiResponse<ProductAssessment[]>>(`/products/${id}/assessments`)
  },
}

// ====== 自评模块 ======
export const assessmentApi = {
  /** 自评列表 */
  getList(params: AssessmentListParams) {
    return request.get<ApiResponse<PageResult<AssessmentInfo>>>('/assessments', { params })
  },
  /** 获取自评详情 */
  getById(id: string) {
    return request.get<ApiResponse<AssessmentInfo>>(`/assessments/${id}`)
  },
  /** 创建自评任务 */
  create(data: AssessmentCreateParams) {
    return request.post<ApiResponse<AssessmentInfo>>('/assessments', data)
  },
  /** 提交评分 */
  score(id: string, data: ScoreParams) {
    return request.post<ApiResponse<void>>(`/assessments/${id}/scores`, data)
  },
  /** 完成自评 */
  complete(id: string) {
    return request.post<ApiResponse<void>>(`/assessments/${id}/complete`)
  },
  /** 审批自评 */
  approve(id: string, data: ApproveParams) {
    return request.post<ApiResponse<void>>(`/assessments/${id}/approve`, data)
  },
}

// ====== 整改模块 ======
export const rectificationApi = {
  /** 整改列表 */
  getList(params: RectificationListParams) {
    return request.get<ApiResponse<PageResult<RectificationInfo>>>('/rectifications', { params })
  },
  /** 创建整改任务 */
  create(data: RectificationCreateParams) {
    return request.post<ApiResponse<RectificationInfo>>('/rectifications', data)
  },
  /** 更新整改状态 */
  updateStatus(id: string, data: RectificationStatusParams) {
    return request.put<ApiResponse<void>>(`/rectifications/${id}/status`, data)
  },
  /** 验证整改 */
  verify(id: string, data: { comment: string; approved: boolean }) {
    return request.post<ApiResponse<void>>(`/rectifications/${id}/verify`, data)
  },
}

// ====== 仪表盘模块 ======
export const dashboardApi = {
  /** 概览数据 */
  getOverview() {
    return request.get<ApiResponse<DashboardOverview>>('/dashboard/overview')
  },
  /** 告警趋势 */
  getAlertTrend(params: AlertTrendParams) {
    return request.get<ApiResponse<AlertTrendItem[]>>('/dashboard/alert-trend', { params })
  },
  /** 告警统计 */
  getAlertStatistics() {
    return request.get<ApiResponse<AlertStatisticsByType>>('/dashboard/alert-statistics')
  },
}

// ====== 系统管理模块 ======
export const systemApi = {
  /** 用户列表 */
  getUsers(params: UserPageParams) {
    return request.get<ApiResponse<PageResult<SysUser>>>('/system/users', { params })
  },
  /** 创建用户 */
  createUser(data: UserCreateParams) {
    return request.post<ApiResponse<SysUser>>('/system/users', data)
  },
  /** 更新用户 */
  updateUser(id: string, data: UserUpdateParams) {
    return request.put<ApiResponse<SysUser>>(`/system/users/${id}`, data)
  },
  /** 删除用户 */
  deleteUser(id: string) {
    return request.delete<ApiResponse<void>>(`/system/users/${id}`)
  },
  /** 重置密码 */
  resetPassword(id: string) {
    return request.post<ApiResponse<void>>(`/system/users/${id}/reset-password`)
  },
}

// ====== 字典模块 ======
export const dictApi = {
  /** 获取字典列表 */
  getDicts(params?: { keyword?: string }) {
    return request.get<ApiResponse<DictInfo[]>>('/system/dicts', { params })
  },
  /** 获取字典项 */
  getDictItems(dictCode: string) {
    return request.get<ApiResponse<DictItem[]>>(`/system/dicts/${dictCode}/items`)
  },
  /** 刷新字典缓存 */
  refreshCache() {
    return request.post<ApiResponse<void>>('/system/dicts/refresh-cache')
  },
}

// ====== 通知模块 ======
export const notificationApi = {
  /** 获取我的通知 */
  getMy(params: NotificationParams) {
    return request.get<ApiResponse<PageResult<NotificationInfo>>>('/notifications/my', { params })
  },
  /** 标记已读 */
  markRead(id: string) {
    return request.post<ApiResponse<void>>(`/notifications/${id}/read`)
  },
  /** 全部标记已读 */
  markAllRead() {
    return request.post<ApiResponse<void>>('/notifications/read-all')
  },
  /** 获取未读数量 */
  getUnreadCount() {
    return request.get<ApiResponse<number>>('/notifications/unread-count')
  },
}

// ====== 审计日志模块 ======
export const auditLogApi = {
  /** 分页查询审计日志 */
  getPage(params: AuditLogPageParams) {
    return request.get<ApiResponse<PageResult<AuditLog>>>('/system/audit-logs/page', { params })
  },
  /** 获取日志详情 */
  getById(id: string) {
    return request.get<ApiResponse<AuditLog>>(`/system/audit-logs/${id}`)
  },
  /** 导出审计日志 */
  export(params: AuditLogPageParams) {
    return request.get('/system/audit-logs/export', { params, responseType: 'blob' as any })
  },
  /** 搜索审计日志 */
  search(params: AuditLogPageParams) {
    return request.get<ApiResponse<PageResult<AuditLog>>>('/system/audit-logs/search', { params })
  },
}

// ====== 图谱分析模块 ======
export const graphApi = {
  /** 检测循环交易 */
  detectCircular(params: CircularDetectionParams) {
    return request.post<ApiResponse<GraphResult>>('/graph/detect-circular', params)
  },
  /** 资金流向追踪 */
  traceFlow(params: FlowTraceParams) {
    return request.post<ApiResponse<GraphResult>>('/graph/trace-flow', params)
  },
  /** 检测共用账户 */
  detectSharedAccounts(params: SharedAccountParams) {
    return request.post<ApiResponse<GraphResult>>('/graph/detect-shared-accounts', params)
  },
  /** 检测高密度交易 */
  detectHighDensity(params: HighDensityParams) {
    return request.post<ApiResponse<GraphResult>>('/graph/detect-high-density', params)
  },
  /** 获取图谱摘要 */
  getSummary(params: { startDate: string; endDate: string }) {
    return request.get<ApiResponse<GraphSummary>>('/graph/summary', { params })
  },
}

// ====== 反馈模块 ======
export const feedbackApi = {
  /** 获取反馈汇总 */
  getSummary(params?: { startDate?: string; endDate?: string }) {
    return request.get<ApiResponse<FeedbackSummary>>('/feedback/summary', { params })
  },
  /** 获取规则反馈列表 */
  getRuleFeedback(params: RuleFeedbackParams) {
    return request.get<ApiResponse<PageResult<RuleFeedback>>>('/feedback/rule-feedback', { params })
  },
  /** 获取需关注事项 */
  getAttention() {
    return request.get<ApiResponse<AttentionItem[]>>('/feedback/attention')
  },
}

// ====== 统一导出 ======
export default {
  authApi,
  customerApi,
  screeningApi,
  transactionApi,
  ruleApi,
  alertApi,
  caseApi,
  strReportApi,
  reportingApi,
  productApi,
  assessmentApi,
  rectificationApi,
  dashboardApi,
  systemApi,
  dictApi,
  notificationApi,
  auditLogApi,
  graphApi,
  feedbackApi,
}
