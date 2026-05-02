import axios from 'axios'
import type { AxiosInstance, InternalAxiosRequestConfig, AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'

// 创建 axios 实例
const request: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

function normalizePageTotal(payload: any) {
  const total = payload?.data?.total
  if (typeof total === 'string' && /^\d+$/.test(total)) {
    return {
      ...payload,
      data: {
        ...payload.data,
        total: Number(total)
      }
    }
  }
  return payload
}

// 请求拦截器：添加 Token
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

// 响应拦截器：统一错误处理
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
  (error) => {
    if (error.response?.status === 401) {
      // 仅在非登录页时清除并跳转
      if (window.location.pathname !== '/login') {
        localStorage.removeItem('aml_token')
        localStorage.removeItem('aml_user')
        ElMessage.error('登录已过期，请重新登录')
        window.location.href = '/login'
      }
    } else if (error.response?.status === 403) {
      ElMessage.error('权限不足')
    } else {
      ElMessage.error(error.response?.data?.message || '网络错误')
    }
    return Promise.reject(error)
  }
)

export default request
