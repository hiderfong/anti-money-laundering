import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/login/LoginView.vue')
    },
    {
      path: '/403',
      name: 'Forbidden',
      component: () => import('@/views/error/403.vue'),
      meta: { title: '无权限' }
    },
    {
      path: '/',
      component: () => import('@/layout/MainLayout.vue'),
      redirect: '/dashboard',
      children: [
        { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/dashboard/DashboardView.vue'), meta: { title: '仪表盘' } },
        { path: 'kyc', name: 'CustomerList', component: () => import('@/views/kyc/CustomerList.vue'), meta: { title: '客户管理', roles: ['ROLE_ADMIN', 'ROLE_COMPLIANCE', 'ROLE_INVESTIGATOR', 'ROLE_VIEWER'], permissions: ['customer:view'] } },
        { path: 'kyc/:id', name: 'CustomerDetail', component: () => import('@/views/kyc/CustomerDetail.vue'), meta: { title: '客户详情', roles: ['ROLE_ADMIN', 'ROLE_COMPLIANCE', 'ROLE_INVESTIGATOR', 'ROLE_VIEWER'], permissions: ['customer:view'] } },
        { path: 'screening', name: 'Screening', component: () => import('@/views/screening/ScreeningView.vue'), meta: { title: '名单筛查', roles: ['ROLE_ADMIN', 'ROLE_COMPLIANCE', 'ROLE_INVESTIGATOR'], permissions: ['screening:view'] } },
        { path: 'monitoring', name: 'Monitoring', component: () => import('@/views/monitoring/MonitoringView.vue'), meta: { title: '交易监测', roles: ['ROLE_ADMIN', 'ROLE_COMPLIANCE', 'ROLE_INVESTIGATOR'], permissions: ['monitoring:view'] } },
        { path: 'alerts', name: 'Alerts', component: () => import('@/views/alert/AlertList.vue'), meta: { title: '预警管理', roles: ['ROLE_ADMIN', 'ROLE_COMPLIANCE', 'ROLE_INVESTIGATOR'], permissions: ['alert:view'] } },
        { path: 'cases', name: 'Cases', component: () => import('@/views/case/CaseList.vue'), meta: { title: '案件管理', roles: ['ROLE_ADMIN', 'ROLE_COMPLIANCE', 'ROLE_INVESTIGATOR'], permissions: ['case:view'] } },
        { path: 'reporting', name: 'Reporting', component: () => import('@/views/reporting/ReportingView.vue'), meta: { title: '监管报送', roles: ['ROLE_ADMIN', 'ROLE_COMPLIANCE'], permissions: ['report:view'] } },
        { path: 'str-reports', name: 'StrReports', component: () => import('@/views/reporting/StrReportView.vue'), meta: { title: 'STR报告', roles: ['ROLE_ADMIN', 'ROLE_COMPLIANCE'], permissions: ['report:str'] } },
        { path: 'products', name: 'Products', component: () => import('@/views/product/ProductList.vue'), meta: { title: '产品管理', roles: ['ROLE_ADMIN', 'ROLE_COMPLIANCE', 'ROLE_VIEWER'], permissions: ['product:view'] } },
        { path: 'assessment', name: 'Assessment', component: () => import('@/views/assessment/AssessmentView.vue'), meta: { title: '自评估', roles: ['ROLE_ADMIN', 'ROLE_COMPLIANCE'], permissions: ['assessment:view'] } },
        { path: 'notifications', name: 'Notifications', component: () => import('@/views/system/NotificationView.vue'), meta: { title: '通知中心' } },
        { path: 'system', name: 'System', component: () => import('@/views/system/SystemView.vue'), meta: { title: '系统管理', roles: ['ROLE_ADMIN'], permissions: ['system:view'] } }
      ]
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'NotFound',
      component: () => import('@/views/error/404.vue'),
      meta: { title: '页面未找到' }
    }
  ]
})

// 路由守卫
router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('aml_token')

  // 未登录跳转登录页
  if (to.path !== '/login' && !token) {
    next('/login')
    return
  }

  // 已登录访问登录页跳转首页
  if (to.path === '/login' && token) {
    next('/dashboard')
    return
  }

  // 角色和权限校验
  if (to.meta?.roles || to.meta?.permissions) {
    try {
      const userStore = useUserStore()

      // 管理员放行
      if (userStore.isAdmin) {
        next()
        return
      }

      // 角色校验
      if (to.meta.roles) {
        const requiredRoles = to.meta.roles as string[]
        const hasRole = requiredRoles.some(role => userStore.hasRole(role))
        if (!hasRole) {
          next('/403')
          return
        }
      }

      // 权限校验
      if (to.meta.permissions) {
        const requiredPerms = to.meta.permissions as string[]
        const hasPerm = requiredPerms.some(perm => userStore.hasPermission(perm))
        if (!hasPerm) {
          next('/403')
          return
        }
      }

      next()
    } catch (e) {
      // store 未初始化时放行（Pinia 可能还没 ready）
      next()
    }
  } else {
    next()
  }
})

export default router
