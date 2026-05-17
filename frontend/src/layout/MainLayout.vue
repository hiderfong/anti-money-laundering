<template>
  <el-container class="layout-container">
    <!-- 侧边栏 -->
    <el-aside :width="effectiveCollapse ? '77px' : '220px'" class="aside">
      <div class="logo">
        <img src="@/assets/shield.svg" alt="盾" class="logo-icon" />
        <span v-show="!effectiveCollapse" class="logo-text">天枢智盾 AML</span>
      </div>
      <el-menu
        :default-active="currentRoute"
        :collapse="effectiveCollapse"
        router
      >
        <template v-for="item in filteredMenus" :key="item.path">
          <el-menu-item :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <template #title>{{ t(item.titleKey) }}</template>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <!-- 主内容区 -->
    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" :class="{ disabled: isMobile }" @click="toggleSidebar" size="18">
            <Fold v-if="!effectiveCollapse" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item>{{ currentTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <LanguageSwitcher />
          <ThemeToggle />
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-icon><UserFilled /></el-icon>
              {{ userStore.userInfo?.realName || t('common.user') }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">{{ t('common.logout') }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed, onBeforeUnmount, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import ThemeToggle from '@/components/ThemeToggle.vue'
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'

interface MenuItem {
  path: string
  titleKey: string
  icon: string
  roles?: string[]
  permissions?: string[]
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { t } = useI18n()
const isCollapse = ref(false)
const isMobile = ref(false)

const effectiveCollapse = computed(() => isMobile.value || isCollapse.value)

// 菜单配置：定义角色/权限要求
const menuItems: MenuItem[] = [
  { path: '/dashboard', titleKey: 'menu.dashboard', icon: 'Odometer' },
  { path: '/kyc', titleKey: 'menu.kyc', icon: 'User', roles: ['ROLE_ADMIN', 'ROLE_COMPLIANCE', 'ROLE_INVESTIGATOR', 'ROLE_VIEWER'], permissions: ['customer:view'] },
  { path: '/screening', titleKey: 'menu.screening', icon: 'Search', roles: ['ROLE_ADMIN', 'ROLE_COMPLIANCE', 'ROLE_INVESTIGATOR'], permissions: ['screening:view'] },
  { path: '/monitoring', titleKey: 'menu.monitoring', icon: 'Monitor', roles: ['ROLE_ADMIN', 'ROLE_COMPLIANCE', 'ROLE_INVESTIGATOR'], permissions: ['monitoring:view'] },
  { path: '/alerts', titleKey: 'menu.alerts', icon: 'Bell', roles: ['ROLE_ADMIN', 'ROLE_COMPLIANCE', 'ROLE_INVESTIGATOR'], permissions: ['alert:view'] },
  { path: '/cases', titleKey: 'menu.cases', icon: 'FolderOpened', roles: ['ROLE_ADMIN', 'ROLE_COMPLIANCE', 'ROLE_INVESTIGATOR'], permissions: ['case:view'] },
  { path: '/str-reports', titleKey: 'menu.strReports', icon: 'Warning', roles: ['ROLE_ADMIN', 'ROLE_COMPLIANCE'], permissions: ['report:str'] },
  { path: '/reporting', titleKey: 'menu.reports', icon: 'Document', roles: ['ROLE_ADMIN', 'ROLE_COMPLIANCE'], permissions: ['report:view'] },
  { path: '/products', titleKey: 'menu.products', icon: 'Goods', roles: ['ROLE_ADMIN', 'ROLE_COMPLIANCE', 'ROLE_VIEWER'], permissions: ['product:view'] },
  { path: '/assessment', titleKey: 'menu.assessment', icon: 'DataAnalysis', roles: ['ROLE_ADMIN', 'ROLE_COMPLIANCE'], permissions: ['assessment:view'] },
  { path: '/special-prevention', titleKey: 'menu.specialPrevention', icon: 'Operation', roles: ['ROLE_ADMIN', 'ROLE_COMPLIANCE', 'ROLE_INVESTIGATOR'], permissions: ['special:view'] },
  { path: '/rectifications', titleKey: 'menu.rectifications', icon: 'Check', roles: ['ROLE_ADMIN', 'ROLE_COMPLIANCE', 'ROLE_INVESTIGATOR', 'ROLE_VIEWER'], permissions: ['rectification:view'] },
  { path: '/investigations', titleKey: 'menu.investigations', icon: 'Search', roles: ['ROLE_ADMIN', 'ROLE_COMPLIANCE', 'ROLE_INVESTIGATOR'], permissions: ['investigation:view'] },
  { path: '/models', titleKey: 'menu.models', icon: 'DataAnalysis', roles: ['ROLE_ADMIN'], permissions: ['model:view'] },
  { path: '/regulation-library', titleKey: 'menu.regulationLibrary', icon: 'Document', roles: ['ROLE_ADMIN'], permissions: ['regulation:view'] },
  { path: '/notifications', titleKey: 'menu.notifications', icon: 'Notification' },
  { path: '/system', titleKey: 'menu.system', icon: 'Setting', roles: ['ROLE_ADMIN'], permissions: ['system:view'] }
]

const activeMenuItem = computed(() => {
  return menuItems.find(item => route.path === item.path || route.path.startsWith(`${item.path}/`))
})

const currentRoute = computed(() => activeMenuItem.value?.path || route.path)
const currentTitle = computed(() => {
  const titleKey = (route.meta?.titleKey as string | undefined) || activeMenuItem.value?.titleKey || 'menu.dashboard'
  return t(titleKey)
})

// 根据用户角色和权限动态过滤菜单
const filteredMenus = computed(() => {
  return menuItems.filter(item => {
    // 无角色/权限限制的菜单直接显示
    if (!item.roles && !item.permissions) return true

    // 管理员显示所有菜单
    if (userStore.isAdmin) return true

    // 角色检查：任一角色匹配即通过
    const roleOk = !item.roles || item.roles.some(role => userStore.hasRole(role))
    // 权限检查：任一权限匹配即通过
    const permOk = !item.permissions || item.permissions.some(perm => userStore.hasPermission(perm))

    // 两者都满足才显示（roles 和 permissions 是 AND 关系）
    return roleOk && permOk
  })
})

function syncViewport() {
  isMobile.value = window.innerWidth < 768
}

function toggleSidebar() {
  if (isMobile.value) return
  isCollapse.value = !isCollapse.value
}

async function handleCommand(command: string) {
  if (command === 'logout') {
    await userStore.logout()
    router.push('/login')
  }
}

onMounted(() => {
  syncViewport()
  window.addEventListener('resize', syncViewport)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncViewport)
})
</script>

<style scoped>
.layout-container {
  height: 100vh;
  background: var(--bg-deepest);
}

.aside {
  background: var(--sidebar-bg);
  border-right: 1px solid rgba(255, 255, 255, 0.08);
  transition: width 0.2s ease;
  overflow-x: hidden;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: rgba(148, 163, 184, 0.35) transparent;
}

.logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  padding: 0 16px;
}

.logo-icon {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
}

.logo-text {
  font-size: 15px;
  font-weight: 600;
  color: #fff;
  letter-spacing: 0;
  white-space: nowrap;
}

.aside :deep(.el-menu) {
  --el-menu-item-height: 47.5px;
  --el-menu-item-font-size: 16.15px;
  --el-menu-icon-width: 27.55px;
  padding-top: 10px;
}

.aside :deep(.el-menu-item) {
  height: 47.5px;
  line-height: 47.5px;
  margin: 2.85px 9px;
  padding: 0 14.25px !important;
  font-size: 16.15px !important;
  font-weight: 600;
  border-radius: 8px;
}

.aside :deep(.el-menu-item .el-icon) {
  width: 20.9px !important;
  margin-right: 8px !important;
  font-size: 20.9px !important;
}

.aside :deep(.el-menu--collapse .el-menu-item) {
  justify-content: center;
  padding: 0 !important;
  margin: 3px 10px;
}

.aside :deep(.el-menu--collapse .el-menu-item .el-icon) {
  margin-right: 0 !important;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 52px;
  border-bottom: 1px solid var(--border-subtle);
  background: var(--bg-panel);
  padding: 0 20px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
  flex: 1;
}

.collapse-btn {
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  cursor: pointer;
  color: var(--text-tertiary);
  transition: color 0.15s ease;
}

.collapse-btn:hover {
  color: var(--text-primary);
}

.collapse-btn.disabled {
  cursor: default;
  opacity: 0.35;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: var(--text-tertiary);
  font-size: 13px;
  font-weight: 510;
  transition: color 0.15s ease;
}

.user-info:hover {
  color: var(--text-primary);
}

.main {
  background: var(--bg-deepest);
  padding: 24px;
  overflow-y: auto;
}

@media (max-width: 767px) {
  .header {
    padding: 0 12px;
  }

  .header-left {
    gap: 10px;
  }

  .header-left :deep(.el-breadcrumb) {
    max-width: 96px;
    overflow: hidden;
    white-space: nowrap;
  }

  .user-info {
    max-width: 112px;
    overflow: hidden;
    white-space: nowrap;
  }

  .main {
    padding: 12px;
    overflow-x: auto;
  }
}
</style>
