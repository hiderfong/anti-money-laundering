import { useUserStore } from '@/stores/user'

/**
 * 权限检查 composable
 * 用于在 setup 函数中检查用户权限和角色
 *
 * 用法:
 *   const { hasPermission, hasRole, hasAnyPermission, hasAnyRole } = usePermission()
 *   const canCreate = hasPermission('customer:create')
 *   const isAdmin = hasRole('ROLE_ADMIN')
 */
export function usePermission() {
  const userStore = useUserStore()

  /**
   * 检查是否有某个权限码
   * 管理员(ROLE_ADMIN)默认拥有所有权限
   */
  function hasPermission(code: string): boolean {
    return userStore.hasPermission(code)
  }

  /**
   * 检查是否有某个角色
   */
  function hasRole(role: string): boolean {
    return userStore.hasRole(role)
  }

  /**
   * 检查是否有任意一个权限码
   */
  function hasAnyPermission(codes: string[]): boolean {
    return userStore.hasAnyPermission(codes)
  }

  /**
   * 检查是否有任意一个角色
   */
  function hasAnyRole(roles: string[]): boolean {
    return userStore.hasAnyRole(roles)
  }

  /**
   * 检查是否有所有指定权限码
   */
  function hasAllPermissions(codes: string[]): boolean {
    if (userStore.isAdmin) return true
    return codes.every(code => userStore.permissions.includes(code))
  }

  return {
    hasPermission,
    hasRole,
    hasAnyPermission,
    hasAnyRole,
    hasAllPermissions
  }
}
