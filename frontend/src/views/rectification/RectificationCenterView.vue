<template>
  <div class="rectification-page">
    <section class="page-heading">
      <div>
        <h1>整改中心</h1>
        <p>统一管理自评估、内外部检查、监管检查和审计发现问题的整改闭环</p>
      </div>
      <el-button type="primary" @click="createDialogVisible = true">
        <el-icon><Plus /></el-icon>新增整改
      </el-button>
    </section>

    <div class="metric-grid">
      <div class="metric-card">
        <span>待整改</span>
        <strong>{{ stats.open }}</strong>
      </div>
      <div class="metric-card">
        <span>整改中</span>
        <strong>{{ stats.inProgress }}</strong>
      </div>
      <div class="metric-card warning">
        <span>已逾期</span>
        <strong>{{ stats.overdue }}</strong>
      </div>
      <div class="metric-card success">
        <span>已验证</span>
        <strong>{{ stats.verified }}</strong>
      </div>
    </div>

    <section class="table-panel">
      <div class="toolbar">
        <el-select v-model="query.sourceType" placeholder="问题来源" clearable @change="loadTasks">
          <el-option label="自评估" value="SELF_ASSESSMENT" />
          <el-option label="内部检查" value="INTERNAL_CHECK" />
          <el-option label="外部检查" value="EXTERNAL_CHECK" />
          <el-option label="监管检查" value="REGULATOR" />
          <el-option label="审计发现" value="AUDIT" />
        </el-select>
        <el-select v-model="query.status" placeholder="整改状态" clearable @change="loadTasks">
          <el-option label="待整改" value="OPEN" />
          <el-option label="整改中" value="IN_PROGRESS" />
          <el-option label="已完成" value="COMPLETED" />
          <el-option label="已逾期" value="OVERDUE" />
          <el-option label="已验证" value="VERIFIED" />
        </el-select>
        <el-input v-model="query.responsiblePerson" placeholder="责任人" clearable @keyup.enter="loadTasks" />
        <el-button @click="loadTasks"><el-icon><Refresh /></el-icon>查询</el-button>
      </div>

      <el-table :data="tasks" v-loading="loading" stripe border>
        <el-table-column prop="issueDescription" label="问题描述" min-width="220" fixed="left" show-overflow-tooltip />
        <el-table-column label="操作" width="210" fixed="left">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openProgress(row)">更新进度</el-button>
            <el-button v-if="row.status === 'COMPLETED'" type="success" link size="small" @click="openVerify(row)">验证</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="sourceType" label="来源" width="130">
          <template #default="{ row }">{{ sourceLabel(row.sourceType) }}</template>
        </el-table-column>
        <el-table-column prop="issueCategory" label="分类" min-width="120" />
        <el-table-column prop="severity" label="严重程度" width="110">
          <template #default="{ row }">
            <el-tag :type="severityType(row.severity)" size="small">{{ severityLabel(row.severity) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="responsibleDept" label="责任部门" min-width="120" />
        <el-table-column prop="responsiblePerson" label="责任人" width="110" />
        <el-table-column prop="deadline" label="截止日期" width="120" />
        <el-table-column prop="progressPercent" label="进度" width="150">
          <template #default="{ row }">
            <el-progress :percentage="row.progressPercent || 0" :status="progressStatus(row)" />
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="verificationStatus" label="验证" width="110">
          <template #default="{ row }">
            <el-tag :type="verificationType(row.verificationStatus)" size="small">{{ verificationLabel(row.verificationStatus) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="createDialogVisible" title="新增整改任务" width="640px">
      <el-form :model="createForm" label-width="110px">
        <el-form-item label="问题来源">
          <el-select v-model="createForm.sourceType" style="width: 100%;">
            <el-option label="自评估" value="SELF_ASSESSMENT" />
            <el-option label="内部检查" value="INTERNAL_CHECK" />
            <el-option label="外部检查" value="EXTERNAL_CHECK" />
            <el-option label="监管检查" value="REGULATOR" />
            <el-option label="审计发现" value="AUDIT" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源ID"><el-input v-model="createForm.sourceId" placeholder="可选" /></el-form-item>
        <el-form-item label="问题分类"><el-input v-model="createForm.issueCategory" placeholder="如 客户尽调 / 名单筛查 / 报送质量" /></el-form-item>
        <el-form-item label="问题描述"><el-input v-model="createForm.issueDescription" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="严重程度">
          <el-select v-model="createForm.severity" style="width: 100%;">
            <el-option label="高" value="HIGH" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="低" value="LOW" />
          </el-select>
        </el-form-item>
        <el-form-item label="责任部门"><el-input v-model="createForm.responsibleDept" /></el-form-item>
        <el-form-item label="责任人"><el-input v-model="createForm.responsiblePerson" /></el-form-item>
        <el-form-item label="整改期限">
          <el-date-picker v-model="createForm.deadline" type="date" value-format="YYYY-MM-DD" style="width: 100%;" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">创建任务</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="progressDialogVisible" title="更新整改进度" width="520px">
      <el-form :model="progressForm" label-width="110px">
        <el-form-item label="进度">
          <el-slider v-model="progressForm.progressPercent" :min="0" :max="100" show-input />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="progressForm.status" style="width: 100%;">
            <el-option label="待整改" value="OPEN" />
            <el-option label="整改中" value="IN_PROGRESS" />
            <el-option label="已完成" value="COMPLETED" />
          </el-select>
        </el-form-item>
        <el-form-item label="证据/说明">
          <el-input v-model="progressForm.completionEvidence" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="progressDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitProgress">保存进度</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="verifyDialogVisible" title="整改验证" width="520px">
      <el-form :model="verifyForm" label-width="110px">
        <el-form-item label="验证结果">
          <el-select v-model="verifyForm.verificationStatus" style="width: 100%;">
            <el-option label="验证通过" value="PASSED" />
            <el-option label="退回整改" value="RETURNED" />
          </el-select>
        </el-form-item>
        <el-form-item label="验证意见">
          <el-input v-model="verifyForm.verifyResult" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="verifyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitVerify">提交验证</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import request from '@/utils/request'

const loading = ref(false)
const submitting = ref(false)
const tasks = ref<any[]>([])
const currentTaskId = ref<string>('')

const query = reactive({ sourceType: '', status: '', responsiblePerson: '' })
const createDialogVisible = ref(false)
const progressDialogVisible = ref(false)
const verifyDialogVisible = ref(false)

const createForm = reactive({
  sourceType: 'REGULATOR',
  sourceId: '',
  issueCategory: '',
  issueDescription: '',
  severity: 'MEDIUM',
  responsibleDept: '',
  responsiblePerson: '',
  deadline: ''
})

const progressForm = reactive({
  progressPercent: 0,
  status: 'IN_PROGRESS',
  completionEvidence: ''
})

const verifyForm = reactive({
  verificationStatus: 'PASSED',
  verifyResult: ''
})

const stats = computed(() => ({
  open: tasks.value.filter(item => item.status === 'OPEN').length,
  inProgress: tasks.value.filter(item => item.status === 'IN_PROGRESS').length,
  overdue: tasks.value.filter(item => item.status === 'OVERDUE').length,
  verified: tasks.value.filter(item => item.status === 'VERIFIED').length
}))

async function loadTasks() {
  loading.value = true
  try {
    const res: any = await request.get('/rectifications', {
      params: {
        page: 1,
        size: 50,
        sourceType: query.sourceType || undefined,
        status: query.status || undefined,
        responsiblePerson: query.responsiblePerson || undefined
      }
    })
    tasks.value = res.data?.list || []
  } finally {
    loading.value = false
  }
}

async function submitCreate() {
  submitting.value = true
  try {
    await request.post('/rectifications', {
      ...createForm,
      sourceId: createForm.sourceId ? Number(createForm.sourceId) : undefined
    })
    ElMessage.success('整改任务已创建')
    createDialogVisible.value = false
    createForm.sourceId = ''
    createForm.issueCategory = ''
    createForm.issueDescription = ''
    createForm.responsibleDept = ''
    createForm.responsiblePerson = ''
    createForm.deadline = ''
    await loadTasks()
  } finally {
    submitting.value = false
  }
}

function openProgress(row: any) {
  currentTaskId.value = row.id
  progressForm.progressPercent = row.progressPercent || 0
  progressForm.status = row.status === 'OPEN' ? 'IN_PROGRESS' : row.status
  progressForm.completionEvidence = row.completionEvidence || ''
  progressDialogVisible.value = true
}

async function submitProgress() {
  submitting.value = true
  try {
    await request.put(`/rectifications/${currentTaskId.value}/progress`, { ...progressForm })
    ElMessage.success('整改进度已更新')
    progressDialogVisible.value = false
    await loadTasks()
  } finally {
    submitting.value = false
  }
}

function openVerify(row: any) {
  currentTaskId.value = row.id
  verifyForm.verificationStatus = 'PASSED'
  verifyForm.verifyResult = row.verifyResult || ''
  verifyDialogVisible.value = true
}

async function submitVerify() {
  submitting.value = true
  try {
    await request.post(`/rectifications/${currentTaskId.value}/verify`, { ...verifyForm })
    ElMessage.success('整改验证已提交')
    verifyDialogVisible.value = false
    await loadTasks()
  } finally {
    submitting.value = false
  }
}

function sourceLabel(value: string) {
  return {
    SELF_ASSESSMENT: '自评估',
    INTERNAL_CHECK: '内部检查',
    EXTERNAL_CHECK: '外部检查',
    REGULATOR: '监管检查',
    AUDIT: '审计发现'
  }[value] || value
}

function severityLabel(value: string) {
  return { HIGH: '高', MEDIUM: '中', LOW: '低' }[value] || value
}

function severityType(value: string) {
  return ({ HIGH: 'danger', MEDIUM: 'warning', LOW: 'info' }[value] || 'info') as any
}

function statusLabel(value: string) {
  return { OPEN: '待整改', IN_PROGRESS: '整改中', COMPLETED: '已完成', OVERDUE: '已逾期', VERIFIED: '已验证' }[value] || value
}

function statusType(value: string) {
  return ({ OPEN: 'info', IN_PROGRESS: 'warning', COMPLETED: 'success', OVERDUE: 'danger', VERIFIED: 'success' }[value] || 'info') as any
}

function verificationLabel(value: string) {
  return { PENDING: '待验证', PASSED: '通过', RETURNED: '退回' }[value] || '待验证'
}

function verificationType(value: string) {
  return ({ PENDING: 'info', PASSED: 'success', RETURNED: 'warning' }[value] || 'info') as any
}

function progressStatus(row: any) {
  if (row.status === 'OVERDUE') return 'exception'
  if (row.status === 'VERIFIED') return 'success'
  return undefined
}

onMounted(loadTasks)
</script>

<style scoped>
.rectification-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.page-heading h1 {
  margin: 0;
  font-size: 26px;
  color: var(--text-primary);
}

.page-heading p {
  margin: 6px 0 0;
  color: var(--text-secondary);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.metric-card {
  padding: 16px 18px;
  background: var(--bg-surface);
  border: 1px solid var(--border-default);
  border-radius: 8px;
  box-shadow: var(--shadow-sm);
}

.metric-card span {
  display: block;
  color: var(--text-secondary);
  font-size: 13px;
  margin-bottom: 8px;
}

.metric-card strong {
  color: var(--text-primary);
  font-size: 28px;
}

.metric-card.warning strong {
  color: var(--el-color-danger);
}

.metric-card.success strong {
  color: var(--el-color-success);
}

.table-panel {
  padding: 16px;
  background: var(--bg-surface);
  border: 1px solid var(--border-default);
  border-radius: 8px;
}

.toolbar {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.toolbar .el-select,
.toolbar .el-input {
  width: 180px;
}

@media (max-width: 960px) {
  .page-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .metric-grid {
    grid-template-columns: 1fr;
  }
}
</style>
