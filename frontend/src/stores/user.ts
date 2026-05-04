import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import request from '@/utils/request'

interface UserInfo {
  userId: number | string
  username: string
  realName: string
}

function normalizeStringList(value: unknown): string[] {
  if (Array.isArray(value)) {
    return value.filter((item): item is string => typeof item === 'string' && item.length > 0)
  }

  if (typeof value === 'string' && value.length > 0) {
    return value.split(',').map(item => item.trim()).filter(Boolean)
  }

  return []
}

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

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('aml_token') || '')
  const userInfo = ref<UserInfo | null>(null)
  const roles = ref<string[]>(JSON.parse(localStorage.getItem('aml_roles') || '[]'))
  const permissions = ref<string[]>(JSON.parse(localStorage.getItem('aml_permissions') || '[]'))

  const isLoggedIn = computed(() => !!token.value)

  // 判断是否为管理员
  const isAdmin = computed(() => roles.value.includes('ROLE_ADMIN'))

  // 检查是否有某个权限
  function hasPermission(code: string): boolean {
    if (isAdmin.value) return true
    return permissions.value.includes(code)
  }

  // 检查是否有某个角色
  function hasRole(role: string): boolean {
    return roles.value.includes(role)
  }

  // 检查是否有任意一个权限
  function hasAnyPermission(codes: string[]): boolean {
    if (isAdmin.value) return true
    return codes.some(code => permissions.value.includes(code))
  }

  // 检查是否有任意一个角色
  function hasAnyRole(list: string[]): boolean {
    return list.some(role => roles.value.includes(role))
  }

  // 登录
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
    localStorage.setItem('aml_user', JSON.stringify(userInfo.value))
    localStorage.setItem('aml_roles', JSON.stringify(roles.value))
    localStorage.setItem('aml_permissions', JSON.stringify(permissions.value))
    return data
  }

  // 登出
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
    localStorage.removeItem('aml_user')
    localStorage.removeItem('aml_roles')
    localStorage.removeItem('aml_permissions')
  }

  // 获取当前用户信息
  async function getUserInfo() {
    const res: any = await request.get('/auth/me')
    userInfo.value = res.data
    return res.data
  }

  // 初始化：从 localStorage 恢复
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
