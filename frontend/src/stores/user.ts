import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import request from '@/utils/request'

interface UserInfo {
  userId: number
  username: string
  realName: string
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('aml_token') || '')
  const userInfo = ref<UserInfo | null>(null)

  const isLoggedIn = computed(() => !!token.value)

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
    localStorage.setItem('aml_token', data.accessToken)
    localStorage.setItem('aml_user', JSON.stringify(userInfo.value))
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
    localStorage.removeItem('aml_token')
    localStorage.removeItem('aml_user')
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
  }

  initFromStorage()

  return { token, userInfo, isLoggedIn, login, logout, getUserInfo }
})
