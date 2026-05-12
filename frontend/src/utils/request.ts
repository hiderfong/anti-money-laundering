import axios from 'axios'
import type { AxiosInstance, InternalAxiosRequestConfig, AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

// 创建 axios 实例
const request: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

function normalizeNumericTotal(container: any) {
  const total = container?.total
  if (typeof total === 'string' && /^\d+$/.test(total)) {
    return {
      ...container,
      total: Number(total)
    }
  }
  return container
}

function normalizePageTotal(payload: any) {
  const normalizedPayload = normalizeNumericTotal(payload)
  const normalizedData = normalizeNumericTotal(normalizedPayload?.data)
  if (normalizedData !== normalizedPayload?.data) {
    return {
      ...normalizedPayload,
      data: normalizedData
    }
  }
  return normalizedPayload
}

// ========== Token 刷新机制 ==========
let isRefreshing = false
let refreshSubscribers: Array<(token: string) => void> = []

function subscribeTokenRefresh(cb: (token: string) => void) {
  refreshSubscribers.push(cb)
}

function onTokenRefreshed(newToken: string) {
  refreshSubscribers.forEach(cb => cb(newToken))
  refreshSubscribers = []
}

async function doRefreshToken(): Promise<string> {
  const refreshToken = localStorage.getItem('aml_refresh_token')
  if (!refreshToken) {
    throw new Error('No refresh token')
  }

  // 直接用 axios 发请求，避免触发拦截器循环
  const res = await axios.post('/api/auth/refresh', { refreshToken }, {
    headers: { 'Content-Type': 'application/json' }
  })

  const data = res.data
  if (data.code !== 200 || !data.data?.accessToken) {
    throw new Error(data.message || 'Refresh failed')
  }

  const newAccessToken = data.data.accessToken
  const newRefreshToken = data.data.refreshToken

  // 更新存储
  localStorage.setItem('aml_token', newAccessToken)
  if (newRefreshToken) {
    localStorage.setItem('aml_refresh_token', newRefreshToken)
  }

  return newAccessToken
}

// ========== 请求拦截器 ==========
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('aml_token')
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// ========== 响应拦截器 ==========
request.interceptors.response.use(
  (response: AxiosResponse) => {
    const data = normalizePageTotal(response.data)
    // 业务错误
    if (data.code && data.code !== 200) {
      ElMessage.error(data.message || '请求失败')
      return Promise.reject(new Error(data.message))
    }
    return data
  },
  async (error) => {
    const originalRequest = error.config

    // 401 且不是刷新请求本身，尝试刷新 Token
    if (error.response?.status === 401 && !originalRequest._isRetry) {
      if (isRefreshing) {
        // 正在刷新中，将请求加入队列等待
        return new Promise((resolve) => {
          subscribeTokenRefresh((newToken: string) => {
            originalRequest.headers.Authorization = `Bearer ${newToken}`
            resolve(request(originalRequest))
          })
        })
      }

      isRefreshing = true
      originalRequest._isRetry = true

      try {
        const newToken = await doRefreshToken()
        isRefreshing = false
        onTokenRefreshed(newToken)

        // 重试原始请求
        originalRequest.headers.Authorization = `Bearer ${newToken}`
        return request(originalRequest)
      } catch (refreshError) {
        // 刷新失败，清除登录状态并跳转
        isRefreshing = false
        refreshSubscribers = []
        localStorage.removeItem('aml_token')
        localStorage.removeItem('aml_refresh_token')
        localStorage.removeItem('aml_user')
        localStorage.removeItem('aml_roles')
        localStorage.removeItem('aml_permissions')
        if (window.location.pathname !== '/login') {
          ElMessage.error('登录已过期，请重新登录')
          router.push('/login')
        }
        return Promise.reject(refreshError)
      }
    }

    // 非 401 错误
    if (error.response?.status === 403) {
      ElMessage.error('权限不足')
      router.push('/403')
    } else if (error.response?.status !== 401) {
      ElMessage.error(error.response?.data?.message || '网络错误')
    }

    return Promise.reject(error)
  }
)

export default request
