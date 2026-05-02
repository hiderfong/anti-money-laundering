import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/login/LoginView.vue')
    },
    {
      path: '/',
      component: () => import('@/layout/MainLayout.vue'),
      redirect: '/dashboard',
      children: [
        { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/dashboard/DashboardView.vue'), meta: { title: '仪表盘' } },
        { path: 'kyc', name: 'CustomerList', component: () => import('@/views/kyc/CustomerList.vue'), meta: { title: '客户管理' } },
        { path: 'kyc/:id', name: 'CustomerDetail', component: () => import('@/views/kyc/CustomerDetail.vue'), meta: { title: '客户详情' } },
        { path: 'screening', name: 'Screening', component: () => import('@/views/screening/ScreeningView.vue'), meta: { title: '名单筛查' } },
        { path: 'monitoring', name: 'Monitoring', component: () => import('@/views/monitoring/MonitoringView.vue'), meta: { title: '交易监测' } },
        { path: 'alerts', name: 'Alerts', component: () => import('@/views/alert/AlertList.vue'), meta: { title: '预警管理' } },
        { path: 'cases', name: 'Cases', component: () => import('@/views/case/CaseList.vue'), meta: { title: '案件管理' } },
        { path: 'reporting', name: 'Reporting', component: () => import('@/views/reporting/ReportingView.vue'), meta: { title: '监管报送' } },
        { path: 'products', name: 'Products', component: () => import('@/views/product/ProductList.vue'), meta: { title: '产品管理' } },
        { path: 'assessment', name: 'Assessment', component: () => import('@/views/assessment/AssessmentView.vue'), meta: { title: '自评估' } },
        { path: 'system', name: 'System', component: () => import('@/views/system/SystemView.vue'), meta: { title: '系统管理' } }
      ]
    }
  ]
})

// 路由守卫
router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('aml_token')
  if (to.path !== '/login' && !token) {
    next('/login')
  } else if (to.path === '/login' && token) {
    next('/dashboard')
  } else {
    next()
  }
})

export default router
