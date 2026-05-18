import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import request from '@/utils/request'

/** 用户信息数据结构 */
interface UserInfo {
  userId: number | string
  username: string
  realName: string
  roles?: string[]
  permissions?: string[]
}

/**
 * 规范化字符串列表
 * 支持数组和逗号分隔字符串两种输入
 */
function normalizeStringList(value: unknown): string[] {
  if (Array.isArray(value)) {
    return value.filter((item): item is string => typeof item === 'string' && item.length > 0)
  }

  if (typeof value === 'string' && value.length > 0) {
    return value.split(',').map(item => item.trim()).filter(Boolean)
  }

  return []
}

/**
 * 从JWT Token中解析角色列表
 * 处理Base64URL编码并提取roles字段
 */
function decodeJwtRoles(accessToken: string): string[] {
  try {
    const payload = accessToken.split('.')[1]
    if (!payload) return []

    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/')
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')
    const data = JSON.parse(atob(padded))

    return normalizeStringList(data.roles)
  } catch (e) {
    return []
  }
}

/**
 * 用户状态管理 Store
 * 管理登录态、用户信息、角色权限，支持持久化到 localStorage
 */
export const useUserStore = defineStore('user', () => {
  /** 访问令牌 */
  const token = ref<string>(localStorage.getItem('aml_token') || '')
  /** 用户信息 */
  const userInfo = ref<UserInfo | null>(null)
  /** 角色列表 */
  const roles = ref<string[]>(JSON.parse(localStorage.getItem('aml_roles') || '[]'))
  /** 权限列表 */
  const permissions = ref<string[]>(JSON.parse(localStorage.getItem('aml_permissions') || '[]'))

  /** 是否已登录 */
  const isLoggedIn = computed(() => !!token.value)

  /** 是否为管理员角色 */
  const isAdmin = computed(() => roles.value.includes('ROLE_ADMIN'))

  /**
   * 检查是否拥有指定权限
   * 管理员自动拥有所有权限
   */
  function hasPermission(code: string): boolean {
    if (isAdmin.value) return true
    return permissions.value.includes(code)
  }

  /** 检查是否拥有指定角色 */
  function hasRole(role: string): boolean {
    return roles.value.includes(role)
  }

  /**
   * 检查是否拥有任意一个指定权限
   * 管理员自动拥有所有权限
   */
  function hasAnyPermission(codes: string[]): boolean {
    if (isAdmin.value) return true
    return codes.some(code => permissions.value.includes(code))
  }

  /** 检查是否拥有任意一个指定角色 */
  function hasAnyRole(list: string[]): boolean {
    return list.some(role => roles.value.includes(role))
  }

  /**
   * 用户登录
   * 成功后保存token、用户信息、角色权限到localStorage
   */
  async function login(username: string, password: string) {
    const res: any = await request.post('/auth/login', { username, password })
    const data = res.data
    token.value = data.accessToken
    userInfo.value = {
      userId: data.userId,
      username: data.username,
      realName: data.realName
    }
    const responseRoles = normalizeStringList(data.roles)
    roles.value = responseRoles.length ? responseRoles : decodeJwtRoles(data.accessToken)
    permissions.value = normalizeStringList(data.permissions)
    localStorage.setItem('aml_token', data.accessToken)
    localStorage.setItem('aml_refresh_token', data.refreshToken)
    localStorage.setItem('aml_user', JSON.stringify(userInfo.value))
    localStorage.setItem('aml_roles', JSON.stringify(roles.value))
    localStorage.setItem('aml_permissions', JSON.stringify(permissions.value))
    return data
  }

  /**
   * 用户登出
   * 调用后端登出接口并清除所有本地状态
   */
  async function logout() {
    try {
      await request.post('/auth/logout')
    } catch (e) {
      // ignore
    }
    token.value = ''
    userInfo.value = null
    roles.value = []
    permissions.value = []
    localStorage.removeItem('aml_token')
    localStorage.removeItem('aml_refresh_token')
    localStorage.removeItem('aml_user')
    localStorage.removeItem('aml_roles')
    localStorage.removeItem('aml_permissions')
  }

  /**
   * 获取当前用户信息
   * 用于页面刷新后恢复用户状态
   */
  async function getUserInfo() {
    const res: any = await request.get('/auth/me')
    const data = res.data
    userInfo.value = {
      userId: data.userId,
      username: data.username,
      realName: data.realName,
      roles: normalizeStringList(data.roles),
      permissions: normalizeStringList(data.permissions)
    }
    roles.value = userInfo.value.roles || []
    permissions.value = userInfo.value.permissions || []
    localStorage.setItem('aml_user', JSON.stringify(userInfo.value))
    localStorage.setItem('aml_roles', JSON.stringify(roles.value))
    localStorage.setItem('aml_permissions', JSON.stringify(permissions.value))
    return userInfo.value
  }

  /**
   * 从 localStorage 恢复用户状态
   * 页面刷新时自动调用
   */
  function initFromStorage() {
    const saved = localStorage.getItem('aml_user')
    if (saved) {
      try { userInfo.value = JSON.parse(saved) } catch (e) { /* ignore */ }
    }
    if (!roles.value.length && token.value) {
      roles.value = decodeJwtRoles(token.value)
      localStorage.setItem('aml_roles', JSON.stringify(roles.value))
    }
  }

  initFromStorage()

  return {
    token, userInfo, roles, permissions, isLoggedIn, isAdmin,
    hasPermission, hasRole, hasAnyPermission, hasAnyRole,
    login, logout, getUserInfo
  }
})
