<template>
  <div class="investigation-page">
    <section class="page-heading">
      <div>
        <h1>调查协查</h1>
        <p>集中管理有权机关询问、查阅、复制资料、协查回复与过程证据</p>
      </div>
      <el-button type="primary" @click="createDialogVisible = true">
        <el-icon><Plus /></el-icon>新增协查
      </el-button>
    </section>

    <div class="metric-grid">
      <div class="metric-card">
        <span>待处理</span>
        <strong>{{ overview.pendingRequests }}</strong>
      </div>
      <div class="metric-card">
        <span>处理中</span>
        <strong>{{ overview.processingRequests }}</strong>
      </div>
      <div class="metric-card warning">
        <span>即将到期</span>
        <strong>{{ overview.dueSoonRequests }}</strong>
      </div>
      <div class="metric-card danger">
        <span>已逾期</span>
        <strong>{{ overview.overdueRequests }}</strong>
      </div>
      <div class="metric-card success">
        <span>已闭环</span>
        <strong>{{ overview.closedRequests }}</strong>
      </div>
    </div>

    <section class="table-panel">
      <div class="toolbar">
        <el-select v-model="query.status" placeholder="办理状态" clearable @change="loadRequests">
          <el-option label="已接收" value="RECEIVED" />
          <el-option label="处理中" value="PROCESSING" />
          <el-option label="待审批" value="WAITING_APPROVAL" />
          <el-option label="已回复" value="RESPONDED" />
          <el-option label="已关闭" value="CLOSED" />
          <el-option label="已退回" value="RETURNED" />
        </el-select>
        <el-select v-model="query.requestType" placeholder="请求类型" clearable @change="loadRequests">
          <el-option label="询问" value="INQUIRY" />
          <el-option label="查阅" value="REVIEW" />
          <el-option label="复制资料" value="COPY" />
          <el-option label="协助调查" value="ASSIST_INVESTIGATION" />
          <el-option label="其他" value="OTHER" />
        </el-select>
        <el-input v-model="query.authorityName" placeholder="有权机关" clearable @keyup.enter="loadRequests" />
        <el-button @click="loadRequests"><el-icon><Refresh /></el-icon>查询</el-button>
      </div>

      <el-table :data="requests" v-loading="loading" stripe border>
        <el-table-column prop="requestNo" label="请求编号" min-width="190" fixed="left" />
        <el-table-column label="操作" width="260" fixed="left">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openAction(row)">登记动作</el-button>
            <el-button type="success" link size="small" @click="openRespond(row)">标记回复</el-button>
            <el-button type="info" link size="small" @click="openActions(row)">查看记录</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="authorityName" label="有权机关" min-width="180" show-overflow-tooltip />
        <el-table-column prop="requestType" label="类型" width="120">
          <template #default="{ row }">{{ typeLabel(row.requestType) }}</template>
        </el-table-column>
        <el-table-column prop="documentNo" label="文书编号" min-width="150" show-overflow-tooltip />
        <el-table-column prop="customerName" label="客户" min-width="130" show-overflow-tooltip />
        <el-table-column prop="priority" label="优先级" width="100">
          <template #default="{ row }">
            <el-tag :type="priorityType(row.priority)" size="small">{{ priorityLabel(row.priority) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="handler" label="经办人" width="110" />
        <el-table-column prop="receivedDate" label="接收日期" width="120" />
        <el-table-column prop="dueDate" label="办理期限" width="120" />
        <el-table-column prop="summary" label="请求摘要" min-width="240" show-overflow-tooltip />
      </el-table>
    </section>

    <el-dialog v-model="createDialogVisible" title="新增调查协查" width="680px">
      <el-form :model="createForm" label-width="110px">
        <el-form-item label="有权机关"><el-input v-model="createForm.authorityName" /></el-form-item>
        <el-form-item label="请求类型">
          <el-select v-model="createForm.requestType" style="width: 100%;">
            <el-option label="询问" value="INQUIRY" />
            <el-option label="查阅" value="REVIEW" />
            <el-option label="复制资料" value="COPY" />
            <el-option label="协助调查" value="ASSIST_INVESTIGATION" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="文书编号"><el-input v-model="createForm.documentNo" /></el-form-item>
        <el-form-item label="客户ID"><el-input v-model="createForm.customerId" placeholder="可选" /></el-form-item>
        <el-form-item label="关联案件ID"><el-input v-model="createForm.relatedCaseId" placeholder="可选" /></el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="createForm.priority" style="width: 100%;">
            <el-option label="低" value="LOW" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="高" value="HIGH" />
            <el-option label="紧急" value="URGENT" />
          </el-select>
        </el-form-item>
        <el-form-item label="办理日期">
          <el-date-picker v-model="requestDates" type="daterange" start-placeholder="接收日期" end-placeholder="办理期限" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="经办人"><el-input v-model="createForm.handler" /></el-form-item>
        <el-form-item label="请求摘要"><el-input v-model="createForm.summary" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">创建请求</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="actionDialogVisible" title="登记协查动作" width="620px">
      <el-form :model="actionForm" label-width="110px">
        <el-form-item label="动作类型">
          <el-select v-model="actionForm.actionType" style="width: 100%;">
            <el-option label="询问" value="INQUIRY" />
            <el-option label="查阅" value="REVIEW" />
            <el-option label="复制资料" value="COPY" />
            <el-option label="数据调取" value="DATA_EXPORT" />
            <el-option label="回复" value="RESPONSE" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="动作内容"><el-input v-model="actionForm.actionContent" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="动作结果"><el-input v-model="actionForm.actionResult" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="操作人"><el-input v-model="actionForm.operator" /></el-form-item>
        <el-form-item label="附件引用"><el-input v-model="actionForm.attachmentRef" placeholder="文件编号、MinIO URL 或线下归档编号" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="actionDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAction">保存动作</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="respondDialogVisible" title="标记回复" width="560px">
      <el-form :model="statusForm" label-width="110px">
        <el-form-item label="状态">
          <el-select v-model="statusForm.status" style="width: 100%;">
            <el-option label="待审批" value="WAITING_APPROVAL" />
            <el-option label="已回复" value="RESPONDED" />
            <el-option label="已关闭" value="CLOSED" />
            <el-option label="已退回" value="RETURNED" />
          </el-select>
        </el-form-item>
        <el-form-item label="回复摘要"><el-input v-model="statusForm.responseSummary" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="respondDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitStatus">更新状态</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="actionsDialogVisible" title="协查动作记录" width="760px">
      <el-table :data="actions" v-loading="actionLoading" stripe border>
        <el-table-column prop="actionNo" label="动作编号" min-width="180" fixed="left" />
        <el-table-column prop="actionType" label="动作类型" width="120">
          <template #default="{ row }">{{ actionTypeLabel(row.actionType) }}</template>
        </el-table-column>
        <el-table-column prop="operator" label="操作人" width="110" />
        <el-table-column prop="actionTime" label="动作时间" min-width="170" />
        <el-table-column prop="actionContent" label="内容" min-width="220" show-overflow-tooltip />
        <el-table-column prop="actionResult" label="结果" min-width="220" show-overflow-tooltip />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import request from '@/utils/request'

const loading = ref(false)
const submitting = ref(false)
const actionLoading = ref(false)
const requests = ref<any[]>([])
const actions = ref<any[]>([])
const currentRequestId = ref<string>('')

const overview = reactive({
  pendingRequests: 0,
  processingRequests: 0,
  dueSoonRequests: 0,
  overdueRequests: 0,
  closedRequests: 0
})

const query = reactive({ status: '', requestType: '', authorityName: '' })
const createDialogVisible = ref(false)
const actionDialogVisible = ref(false)
const respondDialogVisible = ref(false)
const actionsDialogVisible = ref(false)
const requestDates = ref<string[]>([])

const createForm = reactive({
  authorityName: '',
  requestType: 'ASSIST_INVESTIGATION',
  documentNo: '',
  customerId: '',
  relatedCaseId: '',
  priority: 'MEDIUM',
  handler: '',
  summary: ''
})

const actionForm = reactive({
  actionType: 'REVIEW',
  actionContent: '',
  actionResult: '',
  operator: '',
  attachmentRef: ''
})

const statusForm = reactive({
  status: 'RESPONDED',
  responseSummary: ''
})

async function loadOverview() {
  const res: any = await request.get('/investigations/overview')
  Object.assign(overview, res.data)
}

async function loadRequests() {
  loading.value = true
  try {
    const res: any = await request.get('/investigations', {
      params: {
        page: 1,
        size: 50,
        status: query.status || undefined,
        requestType: query.requestType || undefined,
        authorityName: query.authorityName || undefined
      }
    })
    requests.value = res.data?.list || []
  } finally {
    loading.value = false
  }
}

async function submitCreate() {
  submitting.value = true
  try {
    await request.post('/investigations', {
      ...createForm,
      customerId: createForm.customerId ? Number(createForm.customerId) : undefined,
      relatedCaseId: createForm.relatedCaseId ? Number(createForm.relatedCaseId) : undefined,
      receivedDate: requestDates.value?.[0],
      dueDate: requestDates.value?.[1]
    })
    ElMessage.success('调查协查请求已创建')
    createDialogVisible.value = false
    createForm.authorityName = ''
    createForm.documentNo = ''
    createForm.customerId = ''
    createForm.relatedCaseId = ''
    createForm.handler = ''
    createForm.summary = ''
    requestDates.value = []
    await Promise.all([loadOverview(), loadRequests()])
  } finally {
    submitting.value = false
  }
}

function openAction(row: any) {
  currentRequestId.value = row.id
  actionForm.actionContent = ''
  actionForm.actionResult = ''
  actionForm.operator = row.handler || ''
  actionForm.attachmentRef = ''
  actionDialogVisible.value = true
}

async function submitAction() {
  submitting.value = true
  try {
    await request.post(`/investigations/${currentRequestId.value}/actions`, { ...actionForm })
    ElMessage.success('动作记录已保存')
    actionDialogVisible.value = false
    await Promise.all([loadOverview(), loadRequests()])
  } finally {
    submitting.value = false
  }
}

function openRespond(row: any) {
  currentRequestId.value = row.id
  statusForm.status = row.status === 'RESPONDED' ? 'CLOSED' : 'RESPONDED'
  statusForm.responseSummary = row.responseSummary || ''
  respondDialogVisible.value = true
}

async function submitStatus() {
  submitting.value = true
  try {
    await request.put(`/investigations/${currentRequestId.value}/status`, { ...statusForm })
    ElMessage.success('协查状态已更新')
    respondDialogVisible.value = false
    await Promise.all([loadOverview(), loadRequests()])
  } finally {
    submitting.value = false
  }
}

async function openActions(row: any) {
  currentRequestId.value = row.id
  actionsDialogVisible.value = true
  actionLoading.value = true
  try {
    const res: any = await request.get(`/investigations/${row.id}/actions`, { params: { page: 1, size: 50 } })
    actions.value = res.data?.list || []
  } finally {
    actionLoading.value = false
  }
}

function typeLabel(type: string) {
  return { INQUIRY: '询问', REVIEW: '查阅', COPY: '复制资料', ASSIST_INVESTIGATION: '协助调查', OTHER: '其他' }[type] || type
}

function actionTypeLabel(type: string) {
  return { INQUIRY: '询问', REVIEW: '查阅', COPY: '复制资料', DATA_EXPORT: '数据调取', RESPONSE: '回复', OTHER: '其他' }[type] || type
}

function priorityLabel(priority: string) {
  return { LOW: '低', MEDIUM: '中', HIGH: '高', URGENT: '紧急' }[priority] || priority
}

function priorityType(priority: string) {
  return ({ LOW: 'info', MEDIUM: 'warning', HIGH: 'danger', URGENT: 'danger' }[priority] || 'info') as any
}

function statusLabel(status: string) {
  return { RECEIVED: '已接收', PROCESSING: '处理中', WAITING_APPROVAL: '待审批', RESPONDED: '已回复', CLOSED: '已关闭', RETURNED: '已退回' }[status] || status
}

function statusType(status: string) {
  return ({ RECEIVED: 'warning', PROCESSING: 'primary', WAITING_APPROVAL: 'warning', RESPONDED: 'success', CLOSED: 'info', RETURNED: 'danger' }[status] || 'info') as any
}

onMounted(async () => {
  await Promise.all([loadOverview(), loadRequests()])
})
</script>

<style scoped>
.investigation-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.page-heading h1 {
  margin: 0;
  color: var(--text-primary);
  font-size: 28px;
  font-weight: 700;
}

.page-heading p {
  margin: 8px 0 0;
  color: var(--text-secondary);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.metric-card,
.table-panel {
  background: var(--bg-panel);
  border: 1px solid var(--border-soft);
  border-radius: 8px;
}

.metric-card {
  padding: 18px;
}

.metric-card span {
  display: block;
  color: var(--text-secondary);
  font-size: 13px;
}

.metric-card strong {
  display: block;
  margin-top: 12px;
  color: var(--text-primary);
  font-size: 30px;
}

.metric-card.warning strong {
  color: var(--color-warning);
}

.metric-card.danger strong {
  color: var(--color-danger);
}

.metric-card.success strong {
  color: var(--color-success);
}

.table-panel {
  padding: 16px;
  overflow: hidden;
}

.toolbar {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-bottom: 14px;
}

.toolbar .el-select,
.toolbar .el-input {
  width: 180px;
}

:deep(.el-table) {
  --el-table-header-bg-color: var(--table-header-bg);
  --el-table-tr-bg-color: var(--bg-panel);
  --el-table-row-hover-bg-color: var(--table-row-hover);
  --el-table-border-color: var(--border-soft);
  --el-table-fixed-left-column: var(--bg-panel);
  --el-table-fixed-right-column: var(--bg-panel);
  border-radius: 6px;
}

:deep(.el-table__cell) {
  background: var(--bg-panel) !important;
}

:deep(.el-table__body tr:hover > td.el-table__cell) {
  background: var(--table-row-hover) !important;
}

@media (max-width: 960px) {
  .page-heading,
  .toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .toolbar .el-select,
  .toolbar .el-input {
    width: 100%;
  }
}
</style>
