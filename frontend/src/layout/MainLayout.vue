<template>
  <el-container class="layout-container">
    <!-- 侧边栏 -->
    <el-aside :width="effectiveCollapse ? '64px' : '220px'" class="aside">
      <div class="logo">
        <el-icon size="24"><Lock /></el-icon>
        <span v-show="!effectiveCollapse" class="logo-text">反洗钱系统</span>
      </div>
      <el-menu
        :default-active="currentRoute"
        :collapse="effectiveCollapse"
        background-color="#001529"
        text-color="#ffffffa6"
        active-text-color="#ffffff"
        router
      >
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <template #title>仪表盘</template>
        </el-menu-item>
        <el-menu-item index="/kyc">
          <el-icon><User /></el-icon>
          <template #title>客户管理</template>
        </el-menu-item>
        <el-menu-item index="/screening">
          <el-icon><Search /></el-icon>
          <template #title>名单筛查</template>
        </el-menu-item>
        <el-menu-item index="/monitoring">
          <el-icon><Monitor /></el-icon>
          <template #title>交易监测</template>
        </el-menu-item>
        <el-menu-item index="/alerts">
          <el-icon><Bell /></el-icon>
          <template #title>预警管理</template>
        </el-menu-item>
        <el-menu-item index="/cases">
          <el-icon><FolderOpened /></el-icon>
          <template #title>案件管理</template>
        </el-menu-item>
        <el-menu-item index="/str-reports">
          <el-icon><Warning /></el-icon>
          <template #title>STR报告</template>
        </el-menu-item>
        <el-menu-item index="/reporting">
          <el-icon><Document /></el-icon>
          <template #title>监管报送</template>
        </el-menu-item>
        <el-menu-item index="/products">
          <el-icon><Goods /></el-icon>
          <template #title>产品管理</template>
        </el-menu-item>
        <el-menu-item index="/assessment">
          <el-icon><DataAnalysis /></el-icon>
          <template #title>自评估</template>
        </el-menu-item>
        <el-menu-item index="/notifications">
          <el-icon><Notification /></el-icon>
          <template #title>通知中心</template>
        </el-menu-item>
        <el-menu-item index="/system">
          <el-icon><Setting /></el-icon>
          <template #title>系统管理</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- 主内容区 -->
    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" :class="{ disabled: isMobile }" @click="toggleSidebar" size="20">
            <Fold v-if="!effectiveCollapse" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item>{{ currentTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-icon><UserFilled /></el-icon>
              {{ userStore.userInfo?.realName || '用户' }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
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
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const isCollapse = ref(false)
const isMobile = ref(false)

const currentRoute = computed(() => route.path)
const currentTitle = computed(() => (route.meta?.title as string) || '首页')
const effectiveCollapse = computed(() => isMobile.value || isCollapse.value)

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
.layout-container { height: 100vh; }
.aside {
  background: #001529;
  transition: width 0.3s;
  overflow: hidden;
}
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 18px;
  font-weight: bold;
  gap: 8px;
  border-bottom: 1px solid #ffffff1a;
}
.logo-text { white-space: nowrap; }
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e8e8e8;
  background: #fff;
  padding: 0 20px;
}
.header-left { display: flex; align-items: center; gap: 16px; }
.collapse-btn { cursor: pointer; }
.collapse-btn.disabled { cursor: default; opacity: 0.45; }
.user-info { display: flex; align-items: center; gap: 6px; cursor: pointer; }
.main { background: #f0f2f5; padding: 20px; }

@media (max-width: 767px) {
  .header {
    padding: 0 12px;
  }

  .header-left {
    gap: 10px;
    min-width: 0;
  }

  .header-left :deep(.el-breadcrumb) {
    max-width: 120px;
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
