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
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="false">未读</el-radio-button>
          <el-radio-button value="true">已读</el-radio-button>
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
          <div v-if="hasBusinessDetail(item)" class="notification-actions">
            <el-button type="primary" plain size="small" @click.stop="openDetail(item)">
              <el-icon><View /></el-icon>查看详情
            </el-button>
          </div>
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

    <el-dialog v-model="detailVisible" title="通知详情" width="640px" destroy-on-close>
      <div v-if="selectedNotification" class="detail-panel">
        <div class="detail-heading">
          <el-tag :type="typeTagMap[selectedNotification.type]" disable-transitions>
            {{ typeLabelMap[selectedNotification.type] || selectedNotification.type }}
          </el-tag>
          <h3>{{ selectedNotification.title }}</h3>
        </div>

        <el-descriptions :column="2" border>
          <el-descriptions-item label="通知状态">
            <el-tag :type="selectedNotification.isRead ? 'info' : 'warning'" size="small">
              {{ selectedNotification.isRead ? '已读' : '未读' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="通知时间">{{ selectedNotification.createdTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="阅读时间">{{ selectedNotification.readTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="关联对象">{{ relatedBusinessLabel(selectedNotification) }}</el-descriptions-item>
          <el-descriptions-item label="案件ID" v-if="selectedNotification.type === 'CASE'">
            {{ selectedNotification.caseId || selectedNotification.relatedId || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="案件编号" :span="2" v-if="selectedNotification.type === 'CASE'">
            {{ caseReference(selectedNotification) || '未关联案件编号' }}
          </el-descriptions-item>
          <el-descriptions-item label="客户名称" v-if="selectedNotification.type === 'CASE'">
            {{ selectedNotification.caseCustomerName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="案件状态" v-if="selectedNotification.type === 'CASE'">
            {{ selectedNotification.caseStatus || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="预警ID" v-if="selectedNotification.type === 'ALERT'">
            {{ selectedNotification.alertId || selectedNotification.relatedId || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="预警编号" :span="2" v-if="selectedNotification.type === 'ALERT'">
            {{ alertReference(selectedNotification) || '未关联预警编号' }}
          </el-descriptions-item>
          <el-descriptions-item label="客户名称" v-if="selectedNotification.type === 'ALERT'">
            {{ selectedNotification.alertCustomerName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="预警类型" v-if="selectedNotification.type === 'ALERT'">
            {{ selectedNotification.alertType || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="风险等级" v-if="selectedNotification.type === 'ALERT'">
            {{ selectedNotification.alertRiskLevel || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="预警状态" v-if="selectedNotification.type === 'ALERT'">
            {{ selectedNotification.alertStatus || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <section class="detail-section">
          <h4>通知内容</h4>
          <p>{{ selectedNotification.content || '-' }}</p>
        </section>
      </div>
      <template #footer>
        <el-button
          v-if="selectedNotification?.type === 'CASE'"
          type="primary"
          :disabled="!caseTargetId(selectedNotification)"
          @click="openRelatedCase(selectedNotification)"
        >
          查看案件
        </el-button>
        <el-button
          v-if="selectedNotification?.type === 'ALERT'"
          type="danger"
          :disabled="!alertTargetId(selectedNotification)"
          @click="openRelatedAlert(selectedNotification)"
        >
          查看预警
        </el-button>
        <el-button type="primary" @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { View } from '@element-plus/icons-vue'
import request from '@/utils/request'

interface Notification {
  id: number
  title: string
  content: string
  type: string
  isRead: boolean
  readTime?: string
  relatedType?: string
  relatedId?: string
  caseId?: string | number
  caseNo?: string
  caseCustomerName?: string
  caseStatus?: string
  alertId?: string | number
  alertNo?: string
  alertCustomerName?: string
  alertType?: string
  alertRiskLevel?: string
  alertStatus?: string
  createdTime: string
}

const notifications = ref<Notification[]>([])
const loading = ref(false)
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const unreadCount = ref(0)
const detailVisible = ref(false)
const selectedNotification = ref<Notification | null>(null)

const filterReadStatus = ref('')
const filterType = ref('')
const router = useRouter()

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
    const rawList = res.data?.records ?? res.data?.list ?? res.data ?? []
    const list = Array.isArray(rawList) ? rawList : []
    notifications.value = list.map(normalizeNotification)
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
    item.readTime = item.readTime || '刚刚'
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  } catch {
    ElMessage.error('标记已读失败')
  }
}

async function openDetail(item: Notification) {
  selectedNotification.value = item
  detailVisible.value = true
  if (!item.isRead) {
    await handleRead(item)
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

function normalizeNotification(raw: any): Notification {
  const readTime = raw.readTime || raw.read_at || ''
  return {
    id: Number(raw.id),
    title: raw.title || '',
    content: raw.content || '',
    type: raw.type || 'SYSTEM',
    isRead: Boolean(readTime) || Boolean(raw.isRead ?? raw.read),
    readTime,
    relatedType: raw.relatedType || '',
    relatedId: raw.relatedId || '',
    caseId: raw.caseId || '',
    caseNo: raw.caseNo || '',
    caseCustomerName: raw.caseCustomerName || '',
    caseStatus: raw.caseStatus || '',
    alertId: raw.alertId || '',
    alertNo: raw.alertNo || '',
    alertCustomerName: raw.alertCustomerName || '',
    alertType: raw.alertType || '',
    alertRiskLevel: raw.alertRiskLevel || '',
    alertStatus: raw.alertStatus || '',
    createdTime: raw.createdTime || raw.createdAt || ''
  }
}

function hasBusinessDetail(item: Notification) {
  return item.type === 'CASE' || item.type === 'ALERT'
}

function caseReference(item: Notification) {
  if (item.caseNo) return item.caseNo
  if (item.relatedType === 'CASE' && item.relatedId) {
    return item.relatedId
  }
  const match = String(item.content || '').match(/案件\s*([A-Za-z0-9_-]+)/)
  return match?.[1] || ''
}

function alertReference(item: Notification) {
  if (item.alertNo) return item.alertNo
  if (item.relatedType === 'ALERT' && item.relatedId) {
    return item.relatedId
  }
  const match = String(`${item.title || ''} ${item.content || ''}`).match(/\b((?:E2E)?(?:ALT|AL)[A-Za-z0-9_-]+)\b/)
  return match?.[1] || ''
}

function relatedBusinessLabel(item: Notification) {
  if (!item.relatedType && !item.relatedId) return '未关联'
  const typeLabel: Record<string, string> = {
    ALERT: '预警',
    CASE: '案件',
    STR: '可疑交易报告',
    REPORT: '报告'
  }
  return `${typeLabel[item.relatedType || ''] || item.relatedType}${item.relatedId ? ` #${item.relatedId}` : ''}`
}

function caseTargetId(item: Notification | null) {
  if (!item || item.type !== 'CASE') return ''
  return String(item.caseId || item.relatedId || '').trim()
}

function openRelatedCase(item: Notification | null) {
  const id = caseTargetId(item)
  if (!id) return
  detailVisible.value = false
  router.push({
    path: '/cases',
    query: {
      caseId: id,
      caseNo: item?.caseNo || caseReference(item!)
    }
  })
}

function alertTargetId(item: Notification | null) {
  if (!item || item.type !== 'ALERT') return ''
  return String(item.alertId || item.relatedId || '').trim()
}

function openRelatedAlert(item: Notification | null) {
  const id = alertTargetId(item)
  if (!id) return
  detailVisible.value = false
  router.push({
    path: '/alerts',
    query: {
      alertId: id,
      alertNo: item?.alertNo || alertReference(item!)
    }
  })
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
.notification-actions { display: flex; justify-content: flex-end; margin-top: 10px; }
.pagination-wrap { display: flex; justify-content: flex-end; padding: 12px 0; }
.detail-panel { display: flex; flex-direction: column; gap: 16px; }
.detail-heading { display: flex; align-items: center; gap: 10px; }
.detail-heading h3 { margin: 0; color: #303133; font-size: 18px; }
.detail-section h4 { margin: 0 0 8px; color: #303133; font-size: 14px; }
.detail-section p {
  margin: 0;
  color: #606266;
  line-height: 1.7;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 6px;
}
</style>
