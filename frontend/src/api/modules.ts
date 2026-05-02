/**
 * API 模块统一定义
 * 按业务模块组织所有后端接口调用
 */
import request from '@/utils/request'

// ====== 认证模块 ======
export const authApi = {
  /** 用户登录 */
  login(data: { username: string; password: string }) {
    return request.post('/auth/login', data)
  },
  /** 用户登出 */
  logout() {
    return request.post('/auth/logout')
  },
  /** 刷新 token */
  refreshToken() {
    return request.post('/auth/refresh-token')
  },
  /** 获取当前用户信息 */
  getCurrentUser() {
    return request.get('/auth/current-user')
  }
}

// ====== 客户(KYC)模块 ======
export const customerApi = {
  /** 创建客户 */
  create(data: any) {
    return request.post('/customers', data)
  },
  /** 更新客户信息 */
  update(id: string, data: any) {
    return request.put(`/customers/${id}`, data)
  },
  /** 根据 ID 获取客户详情 */
  getById(id: string) {
    return request.get(`/customers/${id}`)
  },
  /** 分页查询客户列表 */
  pageQuery(params: any) {
    return request.get('/customers/page', { params })
  },
  /** 获取客户 360 视图 */
  get360View(id: string) {
    return request.get(`/customers/${id}/360-view`)
  },
  /** 客户风险评估 */
  assessRisk(id: string) {
    return request.post(`/customers/${id}/assess-risk`)
  }
}

// ====== 筛查模块 ======
export const screeningApi = {
  /** 单条筛查 */
  screen(data: any) {
    return request.post('/screening/screen', data)
  },
  /** 批量筛查 */
  batchScreen(data: any) {
    return request.post('/screening/batch-screen', data)
  },
  /** 分页查询筛查结果 */
  pageResults(params: any) {
    return request.get('/screening/results', { params })
  },
  /** 审核命中记录 */
  reviewHit(id: string, data: any) {
    return request.post(`/screening/results/${id}/review`, data)
  },
  /** 白名单管理 */
  whitelist: {
    list(params: any) { return request.get('/screening/whitelist', { params }) },
    add(data: any) { return request.post('/screening/whitelist', data) },
    remove(id: string) { return request.delete(`/screening/whitelist/${id}`) }
  }
}

// ====== 交易监控模块 ======
export const transactionApi = {
  /** 交易数据导入 */
  ingest(data: any) {
    return request.post('/transactions/ingest', data)
  },
  /** 分页查询交易记录 */
  pageQuery(params: any) {
    return request.get('/transactions/page', { params })
  },
  /** 交易日汇总 */
  dailySummary(params: any) {
    return request.get('/transactions/daily-summary', { params })
  }
}

// ====== 告警管理模块 ======
export const alertApi = {
  /** 分页查询告警 */
  pageQuery(params: any) {
    return request.get('/alerts/page', { params })
  },
  /** 获取告警详情 */
  getDetail(id: string) {
    return request.get(`/alerts/${id}`)
  },
  /** 告警分配 */
  assign(id: string, data: any) {
    return request.post(`/alerts/${id}/assign`, data)
  },
  /** 处理告警 */
  process(id: string, data: any) {
    return request.post(`/alerts/${id}/process`, data)
  },
  /** 批量处理告警 */
  batchProcess(data: any) {
    return request.post('/alerts/batch-process', data)
  },
  /** 告警统计数据 */
  statistics() {
    return request.get('/alerts/statistics')
  }
}

// ====== 案件管理模块 ======
export const caseApi = {
  /** 创建案件 */
  create(data: any) {
    return request.post('/cases', data)
  },
  /** 变更案件状态 */
  changeStatus(id: string, data: any) {
    return request.post(`/cases/${id}/change-status`, data)
  },
  /** 获取案件详情 */
  getDetail(id: string) {
    return request.get(`/cases/${id}`)
  },
  /** 分页查询案件 */
  pageQuery(params: any) {
    return request.get('/cases/page', { params })
  },
  /** 添加调查记录 */
  addInvestigation(id: string, data: any) {
    return request.post(`/cases/${id}/investigations`, data)
  },
  /** 关闭案件 */
  close(id: string, data: any) {
    return request.post(`/cases/${id}/close`, data)
  }
}

// ====== STR 报告模块 ======
export const strReportApi = {
  /** 创建可疑交易报告 */
  create(data: any) {
    return request.post('/str-reports', data)
  },
  /** 提交审核 */
  submitForReview(id: string) {
    return request.post(`/str-reports/${id}/submit-review`)
  },
  /** 审核报告 */
  review(id: string, data: any) {
    return request.post(`/str-reports/${id}/review`, data)
  },
  /** 提交至监管机构 */
  submitToRegulator(id: string) {
    return request.post(`/str-reports/${id}/submit-regulator`)
  }
}

// ====== 报送管理模块 ======
export const reportingApi = {
  /** 生成大额交易报告 */
  generateLargeTxn(data: any) {
    return request.post('/reporting/large-txn/generate', data)
  },
  /** 审核报告 */
  reviewReport(id: string, data: any) {
    return request.post(`/reporting/${id}/review`, data)
  },
  /** 提交报告 */
  submitReport(id: string) {
    return request.post(`/reporting/${id}/submit`)
  },
  /** 分页查询报告 */
  pageQuery(params: any) {
    return request.get('/reporting/page', { params })
  },
  /** 预览 XML */
  previewXml(id: string) {
    return request.get(`/reporting/${id}/preview-xml`, { responseType: 'text' })
  }
}

// ====== 仪表盘模块 ======
export const dashboardApi = {
  /** 概览数据 */
  overview() {
    return request.get('/dashboard/overview')
  },
  /** 告警趋势 */
  alertTrend(params: any) {
    return request.get('/dashboard/alert-trend', { params })
  },
  /** 告警统计 */
  alertStatistics() {
    return request.get('/dashboard/alert-statistics')
  },
  /** KYC 统计 */
  kycStatistics() {
    return request.get('/dashboard/kyc-statistics')
  },
  /** 交易统计 */
  transactionStatistics(params: any) {
    return request.get('/dashboard/transaction-statistics', { params })
  }
}

// ====== 产品管理模块 ======
export const productApi = {
  /** 创建产品 */
  create(data: any) {
    return request.post('/products', data)
  },
  /** 更新产品 */
  update(id: string, data: any) {
    return request.put(`/products/${id}`, data)
  },
  /** 获取产品详情 */
  getById(id: string) {
    return request.get(`/products/${id}`)
  },
  /** 分页查询产品 */
  pageQuery(params: any) {
    return request.get('/products/page', { params })
  },
  /** 产品风险评估 */
  assess(id: string) {
    return request.post(`/products/${id}/assess`)
  }
}

// ====== 自评模块 ======
export const assessmentApi = {
  /** 创建自评任务 */
  create(data: any) {
    return request.post('/assessments', data)
  },
  /** 提交评分 */
  submitScore(id: string, data: any) {
    return request.post(`/assessments/${id}/scores`, data)
  },
  /** 完成自评 */
  complete(id: string) {
    return request.post(`/assessments/${id}/complete`)
  },
  /** 审批自评 */
  approve(id: string, data: any) {
    return request.post(`/assessments/${id}/approve`, data)
  },
  /** 获取自评详情 */
  getDetail(id: string) {
    return request.get(`/assessments/${id}`)
  },
  /** 自评列表 */
  list(params: any) {
    return request.get('/assessments', { params })
  }
}

// ====== 系统管理模块 ======
export const systemApi = {
  /** 系统健康检查 */
  health() {
    return request.get('/system/health')
  },
  /** 系统信息 */
  info() {
    return request.get('/system/info')
  },
  /** 字典列表 */
  dicts(params: any) {
    return request.get('/system/dicts', { params })
  },
  /** 字典项 */
  dictItems(dictCode: string) {
    return request.get(`/system/dicts/${dictCode}/items`)
  },
  /** 用户管理 */
  users: {
    list(params: any) { return request.get('/system/users', { params }) },
    create(data: any) { return request.post('/system/users', data) },
    update(id: string, data: any) { return request.put(`/system/users/${id}`, data) },
    resetPassword(id: string) { return request.post(`/system/users/${id}/reset-password`) }
  },
  /** 通知列表 */
  notifications(params: any) {
    return request.get('/system/notifications', { params })
  },
  /** 审计日志 */
  auditLogs(params: any) {
    return request.get('/system/audit-logs', { params })
  }
}
