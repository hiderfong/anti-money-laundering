import type { Directive, DirectiveBinding } from 'vue'
import { useUserStore } from '@/stores/user'

/**
 * v-perm 按钮级权限指令
 *
 * 用法:
 *   单个权限: <el-button v-perm="'customer:create'">新增</el-button>
 *   多个权限(任意): <el-button v-perm="['customer:create','customer:update']">编辑</el-button>
 *   角色检查: <el-button v-perm:role="'ROLE_ADMIN'">管理</el-button>
 *
 * 无权限时元素会被隐藏 (display: none)
 */
export const permDirective: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding) {
    checkPermission(el, binding)
  },
  updated(el: HTMLElement, binding: DirectiveBinding) {
    checkPermission(el, binding)
  }
}

function checkPermission(el: HTMLElement, binding: DirectiveBinding) {
  const userStore = useUserStore()
  const { value, arg } = binding

  // 管理员拥有所有权限，直接放行
  if (userStore.isAdmin) return

  if (!value) return

  let hasAccess = false

  if (arg === 'role') {
    // 角色检查模式: v-perm:role="'ROLE_ADMIN'" 或 v-perm:role="['ROLE_ADMIN','ROLE_COMPLIANCE']"
    const roles = Array.isArray(value) ? value : [value]
    hasAccess = roles.some((role: string) => userStore.hasRole(role))
  } else {
    // 权限码检查模式
    const codes = Array.isArray(value) ? value : [value]
    hasAccess = codes.some((code: string) => userStore.hasPermission(code))
  }

  if (!hasAccess) {
    // 无权限: 隐藏元素
    el.style.display = 'none'
  } else {
    // 有权限: 恢复显示
    el.style.display = ''
  }
}
