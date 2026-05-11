/**
 * API 响应通用类型定义
 * 统一后端 API 返回结构，消除 any 类型
 */

/** 标准 API 响应结构 */
export interface ApiResponse<T = unknown> {
  /** 业务状态码，0 表示成功 */
  code: number
  /** 响应消息 */
  message: string
  /** 响应数据 */
  data: T
  /** 服务器时间戳 */
  timestamp: number
}

/** 分页查询参数 */
export interface PageQuery {
  /** 当前页码，从 1 开始 */
  page: number
  /** 每页条数 */
  size: number
  /** 排序字段 */
  sort?: string
  /** 排序方向 */
  order?: 'asc' | 'desc'
}

/** 分页结果 */
export interface PageResult<T = unknown> {
  /** 总记录数 */
  total: number
  /** 数据列表 */
  list: T[]
  /** 当前页码 */
  page: number
  /** 每页条数 */
  size: number
  /** 总页数 */
  totalPages: number
}

/** 分页 API 响应（ApiResponse + PageResult） */
export type PageResponse<T = unknown> = ApiResponse<PageResult<T>>

/** 下载/导出响应（文件流） */
export interface FileResponse {
  /** 文件 blob */
  data: Blob
  /** 文件名 */
  filename: string
}

/**
 * 批量操作响应
 */
export interface BatchResult {
  /** 成功数量 */
  successCount: number
  /** 失败数量 */
  failCount: number
  /** 失败详情 */
  failDetails?: Array<{
    id: number | string
    reason: string
  }>
}

/**
 * 登录请求
 */
export interface LoginRequest {
  username: string
  password: string
  captcha?: string
  captchaKey?: string
}

/**
 * 登录响应
 */
export interface LoginResponse {
  /** JWT access token */
  accessToken: string
  /** JWT refresh token */
  refreshToken: string
  /** token 过期时间（秒） */
  expiresIn: number
  /** 用户基本信息 */
  userInfo: {
    id: number
    username: string
    realName: string
    roles: string[]
    permissions: string[]
  }
}

/**
 * Token 刷新响应
 */
export interface TokenRefreshResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
}

/**
 * 仪表盘统计数据
 */
export interface DashboardStats {
  /** 今日新增告警数 */
  todayAlertCount: number
  /** 待处理告警数 */
  pendingAlertCount: number
  /** 活跃案件数 */
  activeCaseCount: number
  /** 待提交报告数 */
  pendingReportCount: number
  /** 近 7 天告警趋势 */
  alertTrend: Array<{ date: string; count: number }>
  /** 近 7 天交易异常趋势 */
  txnTrend: Array<{ date: string; count: number }>
  /** 告警按风险等级分布 */
  alertByRiskLevel: Array<{ level: string; count: number }>
  /** 告警按状态分布 */
  alertByStatus: Array<{ status: string; count: number }>
}

/**
 * 仪表盘概览
 */
export interface DashboardOverview {
  totalCustomers: number
  todayAlerts: number
  pendingAlerts: number
  activeCases: number
  pendingReports: number
  recentAlerts: Array<{
    id: number
    alertNo: string
    customerName: string
    riskLevel: string
    createdTime: string
  }>
}

/**
 * 系统通知统计
 */
export interface NotificationSummary {
  totalCount: number
  unreadCount: number
}
