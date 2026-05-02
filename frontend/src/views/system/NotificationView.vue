<template>
  <div class="notification-container">
    <!-- 顶部统计 -->
    <el-card shadow="never" class="stat-card">
      <div class="stat-row">
        <div class="stat-item">
          <el-icon size="28" color="#409eff"><Bell /></el-icon>
          <div class="stat-info">
            <span class="stat-value">{{ unreadCount }}</span>
            <span class="stat-label">未读通知</span>
          </div>
        </div>
        <el-button type="primary" @click="handleReadAll" :disabled="unreadCount === 0">
          <el-icon><Check /></el-icon>全部已读
        </el-button>
      </div>
    </el-card>

    <!-- 筛选栏 -->
    <el-card shadow="never" class="filter-card">
      <div class="filter-row">
        <el-radio-group v-model="filterReadStatus" @change="handleFilterChange">
          <el-radio-button label="">全部</el-radio-button>
          <el-radio-button label="false">未读</el-radio-button>
          <el-radio-button label="true">已读</el-radio-button>
        </el-radio-group>
        <el-select v-model="filterType" placeholder="通知类型" clearable @change="handleFilterChange" style="width: 160px;">
          <el-option label="系统通知" value="SYSTEM" />
          <el-option label="预警通知" value="ALERT" />
          <el-option label="案件通知" value="CASE" />
          <el-option label="报告通知" value="REPORT" />
        </el-select>
      </div>
    </el-card>

    <!-- 通知列表 -->
    <div v-loading="loading" class="notification-list">
      <el-empty v-if="notifications.length === 0 && !loading" description="暂无通知" />
      <el-card
        v-for="item in notifications"
        :key="item.id"
        shadow="hover"
        class="notification-card"
        :class="{ unread: !item.isRead }"
        @click="handleRead(item)"
      >
        <div class="notification-content">
          <div class="notification-header">
            <span v-if="!item.isRead" class="unread-dot"></span>
            <el-tag :type="typeTagMap[item.type]" size="small" disable-transitions>{{ typeLabelMap[item.type] }}</el-tag>
            <span class="notification-title" :class="{ 'title-bold': !item.isRead }">{{ item.title }}</span>
            <span class="notification-time">{{ item.createdTime }}</span>
          </div>
          <div class="notification-body">{{ item.content }}</div>
        </div>
      </el-card>
    </div>

    <!-- 分页 -->
    <div class="pagination-wrap" v-if="total > 0">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="fetchNotifications"
        @current-change="fetchNotifications"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

interface Notification {
  id: number
  title: string
  content: string
  type: string
  isRead: boolean
  createdTime: string
}

const notifications = ref<Notification[]>([])
const loading = ref(false)
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const unreadCount = ref(0)

const filterReadStatus = ref('')
const filterType = ref('')

const typeLabelMap: Record<string, string> = {
  SYSTEM: '系统通知',
  ALERT: '预警通知',
  CASE: '案件通知',
  REPORT: '报告通知'
}

const typeTagMap: Record<string, string> = {
  SYSTEM: 'info',
  ALERT: 'danger',
  CASE: 'warning',
  REPORT: 'success'
}

async function fetchNotifications() {
  loading.value = true
  try {
    const params: Record<string, any> = {
      page: currentPage.value,
      size: pageSize.value
    }
    if (filterReadStatus.value !== '') {
      params.isRead = filterReadStatus.value === 'true'
    }
    if (filterType.value) {
      params.type = filterType.value
    }
    const res = await request.get('/system/notifications/my', { params })
    notifications.value = res.data?.records ?? res.data?.list ?? res.data ?? []
    total.value = res.data?.total ?? 0
  } catch {
    ElMessage.error('获取通知列表失败')
  } finally {
    loading.value = false
  }
}

async function fetchUnreadCount() {
  try {
    const res = await request.get('/system/notifications/unread-count')
    unreadCount.value = res.data?.count ?? res.data ?? 0
  } catch {
    // ignore
  }
}

async function handleRead(item: Notification) {
  if (item.isRead) return
  try {
    await request.post(`/system/notifications/${item.id}/read`)
    item.isRead = true
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  } catch {
    ElMessage.error('标记已读失败')
  }
}

async function handleReadAll() {
  try {
    await ElMessageBox.confirm('确定将所有通知标记为已读？', '提示', { type: 'info' })
    await request.post('/system/notifications/read-all')
    notifications.value.forEach(n => (n.isRead = true))
    unreadCount.value = 0
    ElMessage.success('全部已读')
  } catch {
    // cancelled or error
  }
}

function handleFilterChange() {
  currentPage.value = 1
  fetchNotifications()
}

onMounted(() => {
  fetchNotifications()
  fetchUnreadCount()
})
</script>

<style scoped>
.notification-container { display: flex; flex-direction: column; gap: 16px; }
.stat-card :deep(.el-card__body) { padding: 16px 20px; }
.stat-row { display: flex; align-items: center; justify-content: space-between; }
.stat-item { display: flex; align-items: center; gap: 12px; }
.stat-info { display: flex; flex-direction: column; }
.stat-value { font-size: 28px; font-weight: bold; color: #303133; line-height: 1.2; }
.stat-label { font-size: 13px; color: #909399; }
.filter-card :deep(.el-card__body) { padding: 12px 20px; }
.filter-row { display: flex; align-items: center; gap: 16px; }
.notification-list { min-height: 200px; display: flex; flex-direction: column; gap: 10px; }
.notification-card { cursor: pointer; transition: border-color 0.2s; }
.notification-card.unread { border-left: 3px solid #409eff; }
.notification-header { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.unread-dot { width: 8px; height: 8px; border-radius: 50%; background: #409eff; flex-shrink: 0; }
.notification-title { flex: 1; font-size: 14px; color: #303133; }
.title-bold { font-weight: 600; }
.notification-time { font-size: 12px; color: #909399; white-space: nowrap; }
.notification-body { font-size: 13px; color: #606266; padding-left: 16px; line-height: 1.6; }
.pagination-wrap { display: flex; justify-content: flex-end; padding: 12px 0; }
</style>
