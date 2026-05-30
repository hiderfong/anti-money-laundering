import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import request from '@/utils/request'
import { useUserStore } from './user'

vi.mock('@/utils/request', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn()
  }
}))

describe('useUserStore permissions', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  it('restores roles and permissions from localStorage', () => {
    localStorage.setItem('aml_token', 'token')
    localStorage.setItem('aml_roles', JSON.stringify(['ROLE_VIEWER']))
    localStorage.setItem('aml_permissions', JSON.stringify(['customer:view']))

    const store = useUserStore()

    expect(store.isLoggedIn).toBe(true)
    expect(store.hasRole('ROLE_VIEWER')).toBe(true)
    expect(store.hasPermission('customer:view')).toBe(true)
    expect(store.hasPermission('system:view')).toBe(false)
  })

  it('treats admin as wildcard permission holder', () => {
    localStorage.setItem('aml_roles', JSON.stringify(['ROLE_ADMIN']))
    localStorage.setItem('aml_permissions', JSON.stringify([]))

    const store = useUserStore()

    expect(store.isAdmin).toBe(true)
    expect(store.hasPermission('system:view')).toBe(true)
    expect(store.hasAnyPermission(['report:submit', 'model:rollback'])).toBe(true)
  })

  it('persists roles and permissions returned by login api', async () => {
    vi.mocked(request.post).mockResolvedValueOnce({
      data: {
        accessToken: 'access-token',
        refreshToken: 'refresh-token',
        userId: 7,
        username: 'compliance',
        realName: '合规专员',
        roles: ['ROLE_COMPLIANCE'],
        permissions: 'report:view, report:str'
      }
    })

    const store = useUserStore()
    await store.login('compliance', 'password')

    expect(store.roles).toEqual(['ROLE_COMPLIANCE'])
    expect(store.permissions).toEqual(['report:view', 'report:str'])
    expect(localStorage.getItem('aml_token')).toBe('access-token')
    expect(JSON.parse(localStorage.getItem('aml_permissions') || '[]')).toEqual(['report:view', 'report:str'])
  })
})
