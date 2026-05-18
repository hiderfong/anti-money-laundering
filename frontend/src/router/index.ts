import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

/**
 * 创建 Vue Router 实例
 * 使用 history 模式，配置所有业务模块路由及权限控制元数据
 */
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
      meta: { title: '无权限', titleKey: 'menu.forbidden' }
    },
    {
      path: '/',
      component: () => import('@/layout/MainLayout.vue'),
      redirect: '/dashboard',
      children: [
        { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/dashboard/DashboardView.vue'), meta: { title: '仪表盘', titleKey: 'menu.dashboard' } },
        { path: 'kyc', name: 'CustomerList', component: () => import('@/views/kyc/CustomerList.vue'), meta: { title: '客户管理', titleKey: 'menu.kyc', roles: ['ROLE_ADMIN', 'ROLE_COMPLIANCE', 'ROLE_INVESTIGATOR', 'ROLE_VIEWER'], permissions: ['customer:view'] } },
        { path: 'kyc/:id', name: 'CustomerDetail', component: () => import('@/views/kyc/CustomerDetail.vue'), meta: { title: '客户详情', titleKey: 'menu.customerDetail', roles: ['ROLE_ADMIN', 'ROLE_COMPLIANCE', 'ROLE_INVESTIGATOR', 'ROLE_VIEWER'], permissions: ['customer:view'] } },
        { path: 'screening', name: 'Screening', component: () => import('@/views/screening/ScreeningView.vue'), meta: { title: '名单筛查', titleKey: 'menu.screening', roles: ['ROLE_ADMIN', 'ROLE_COMPLIANCE', 'ROLE_INVESTIGATOR'], permissions: ['screening:view'] } },
        { path: 'monitoring', name: 'Monitoring', component: () => import('@/views/monitoring/MonitoringView.vue'), meta: { title: '交易监测', titleKey: 'menu.monitoring', roles: ['ROLE_ADMIN', 'ROLE_COMPLIANCE', 'ROLE_INVESTIGATOR'], permissions: ['monitoring:view'] } },
        { path: 'alerts', name: 'Alerts', component: () => import('@/views/alert/AlertList.vue'), meta: { title: '预警管理', titleKey: 'menu.alerts', roles: ['ROLE_ADMIN', 'ROLE_COMPLIANCE', 'ROLE_INVESTIGATOR'], permissions: ['alert:view'] } },
        { path: 'cases', name: 'Cases', component: () => import('@/views/case/CaseList.vue'), meta: { title: '案件管理', titleKey: 'menu.cases', roles: ['ROLE_ADMIN', 'ROLE_COMPLIANCE', 'ROLE_INVESTIGATOR'], permissions: ['case:view'] } },
        { path: 'reporting', name: 'Reporting', component: () => import('@/views/reporting/ReportingView.vue'), meta: { title: '监管报送', titleKey: 'menu.reports', roles: ['ROLE_ADMIN', 'ROLE_COMPLIANCE'], permissions: ['report:view'] } },
        { path: 'str-reports', name: 'StrReports', component: () => import('@/views/reporting/StrReportView.vue'), meta: { title: 'STR报告', titleKey: 'menu.strReports', roles: ['ROLE_ADMIN', 'ROLE_COMPLIANCE'], permissions: ['report:str'] } },
        { path: 'products', name: 'Products', component: () => import('@/views/product/ProductList.vue'), meta: { title: '产品管理', titleKey: 'menu.products', roles: ['ROLE_ADMIN', 'ROLE_COMPLIANCE', 'ROLE_VIEWER'], permissions: ['product:view'] } },
        { path: 'assessment', name: 'Assessment', component: () => import('@/views/assessment/AssessmentView.vue'), meta: { title: '自评估', titleKey: 'menu.assessment', roles: ['ROLE_ADMIN', 'ROLE_COMPLIANCE'], permissions: ['assessment:view'] } },
        { path: 'special-prevention', name: 'SpecialPrevention', component: () => import('@/views/prevention/SpecialPreventionView.vue'), meta: { title: '特别预防', titleKey: 'menu.specialPrevention', roles: ['ROLE_ADMIN', 'ROLE_COMPLIANCE', 'ROLE_INVESTIGATOR'], permissions: ['special:view'] } },
        { path: 'rectifications', name: 'Rectifications', component: () => import('@/views/rectification/RectificationCenterView.vue'), meta: { title: '整改中心', titleKey: 'menu.rectifications', roles: ['ROLE_ADMIN', 'ROLE_COMPLIANCE', 'ROLE_INVESTIGATOR', 'ROLE_VIEWER'], permissions: ['rectification:view'] } },
        { path: 'investigations', name: 'Investigations', component: () => import('@/views/investigation/InvestigationCenterView.vue'), meta: { title: '调查协查', titleKey: 'menu.investigations', roles: ['ROLE_ADMIN', 'ROLE_COMPLIANCE', 'ROLE_INVESTIGATOR'], permissions: ['investigation:view'] } },
        { path: 'models', name: 'ModelManagement', component: () => import('@/views/model/ModelManagementView.vue'), meta: { title: '模型管理', titleKey: 'menu.models', roles: ['ROLE_ADMIN'], permissions: ['model:view'] } },
        { path: 'regulation-library', name: 'RegulationLibrary', component: () => import('@/views/regulation/RegulationLibraryView.vue'), meta: { title: '法规及资料库', titleKey: 'menu.regulationLibrary', roles: ['ROLE_ADMIN'], permissions: ['regulation:view'] } },
        { path: 'notifications', name: 'Notifications', component: () => import('@/views/system/NotificationView.vue'), meta: { title: '通知中心', titleKey: 'menu.notifications' } },
        { path: 'system', name: 'System', component: () => import('@/views/system/SystemView.vue'), meta: { title: '系统管理', titleKey: 'menu.system', roles: ['ROLE_ADMIN'], permissions: ['system:view'] } }
      ]
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'NotFound',
      component: () => import('@/views/error/404.vue'),
      meta: { title: '页面未找到', titleKey: 'menu.notFound' }
    }
  ]
})

// ========== 路由守卫 ==========
/**
 * 全局前置守卫
 * 处理登录校验、角色权限校验和页面标题
 */
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
