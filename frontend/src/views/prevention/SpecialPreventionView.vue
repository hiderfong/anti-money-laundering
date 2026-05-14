<template>
  <div class="prevention-page">
    <section class="page-heading">
      <div>
        <h1>特别预防</h1>
        <p>名单治理、回溯筛查、命中处置、客户管控与查冻扣闭环</p>
      </div>
      <div class="heading-actions">
        <el-button type="primary" @click="syncDialogVisible = true">
          <el-icon><Refresh /></el-icon>名单更新
        </el-button>
        <el-button type="success" @click="jobDialogVisible = true">
          <el-icon><Plus /></el-icon>回溯筛查
        </el-button>
      </div>
    </section>

    <div class="metric-grid">
      <div class="metric-card">
        <span>生效管控措施</span>
        <strong>{{ overview.activeMeasures }}</strong>
      </div>
      <div class="metric-card">
        <span>生效查冻扣</span>
        <strong>{{ overview.activeFreezeRecords }}</strong>
      </div>
      <div class="metric-card">
        <span>待处理命中</span>
        <strong>{{ overview.pendingScreeningResults }}</strong>
      </div>
      <div class="metric-card">
        <span>进行中回溯</span>
        <strong>{{ overview.activeRetrospectiveJobs }}</strong>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="work-tabs">
      <el-tab-pane label="命中处置" name="hits">
        <div class="toolbar">
          <el-select v-model="hitStatus" placeholder="复核状态" clearable @change="loadHits">
            <el-option label="待复核" value="PENDING_REVIEW" />
            <el-option label="已确认" value="CONFIRMED" />
            <el-option label="已升级" value="ESCALATED" />
            <el-option label="已排除" value="EXCLUDED" />
          </el-select>
          <el-button @click="loadHits"><el-icon><Refresh /></el-icon>刷新</el-button>
        </div>
        <el-table :data="hits" v-loading="hitLoading" stripe border>
          <el-table-column prop="customerName" label="客户" min-width="140" fixed="left" />
          <el-table-column prop="watchlistName" label="命中名单" min-width="180" show-overflow-tooltip />
          <el-table-column prop="matchScore" label="分数" width="90" />
          <el-table-column prop="matchType" label="匹配类型" width="110" />
          <el-table-column prop="reviewStatus" label="复核状态" width="120">
            <template #default="{ row }">
              <el-tag :type="reviewStatusType(row.reviewStatus)" size="small">{{ reviewStatusLabel(row.reviewStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="reviewReason" label="处理理由" min-width="180" show-overflow-tooltip />
          <el-table-column label="操作" width="230" fixed="right">
            <template #default="{ row }">
              <el-button type="warning" link size="small" @click="escalateHit(row.id)">升级预警</el-button>
              <el-button type="danger" link size="small" @click="createCase(row.id)">生成案件</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="名单更新记录" name="updates">
        <div class="toolbar">
          <el-select v-model="updateStatus" placeholder="任务状态" clearable @change="loadUpdateJobs">
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILED" />
            <el-option label="运行中" value="RUNNING" />
          </el-select>
          <el-button @click="loadUpdateJobs"><el-icon><Refresh /></el-icon>刷新</el-button>
        </div>
        <el-table :data="updateJobs" v-loading="updateLoading" stripe border>
          <el-table-column prop="jobNo" label="任务编号" min-width="190" fixed="left" />
          <el-table-column prop="sourceName" label="名单源" min-width="160" />
          <el-table-column prop="updateMode" label="模式" width="110" />
          <el-table-column prop="status" label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="jobStatusType(row.status)" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="totalEntries" label="名单总量" width="110" />
          <el-table-column prop="completedTime" label="完成时间" min-width="170" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="回溯筛查任务" name="retro">
        <div class="toolbar">
          <el-select v-model="retroStatus" placeholder="任务状态" clearable @change="loadRetroJobs">
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="运行中" value="RUNNING" />
            <el-option label="失败" value="FAILED" />
          </el-select>
          <el-button @click="loadRetroJobs"><el-icon><Refresh /></el-icon>刷新</el-button>
        </div>
        <el-table :data="retroJobs" v-loading="retroLoading" stripe border>
          <el-table-column prop="jobNo" label="任务编号" min-width="190" fixed="left" />
          <el-table-column prop="jobName" label="任务名称" min-width="160" />
          <el-table-column prop="scopeType" label="范围" width="150" />
          <el-table-column prop="status" label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="jobStatusType(row.status)" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="totalCustomers" label="客户数" width="100" />
          <el-table-column prop="totalHits" label="命中数" width="100" />
          <el-table-column prop="completedTime" label="完成时间" min-width="170" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="管控措施" name="measures">
        <div class="toolbar">
          <el-select v-model="measureStatus" placeholder="措施状态" clearable @change="loadMeasures">
            <el-option label="生效中" value="ACTIVE" />
            <el-option label="已暂停" value="PAUSED" />
            <el-option label="已关闭" value="CLOSED" />
          </el-select>
          <el-button type="primary" @click="measureDialogVisible = true"><el-icon><Plus /></el-icon>新增措施</el-button>
        </div>
        <el-table :data="measures" v-loading="measureLoading" stripe border>
          <el-table-column prop="measureNo" label="措施编号" min-width="190" fixed="left" />
          <el-table-column prop="customerName" label="客户" min-width="130" />
          <el-table-column prop="measureType" label="措施类型" min-width="170" />
          <el-table-column prop="controlLevel" label="级别" width="100">
            <template #default="{ row }">
              <el-tag :type="levelType(row.controlLevel)" size="small">{{ row.controlLevel }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100" />
          <el-table-column prop="endDate" label="到期日期" width="120" />
          <el-table-column prop="measureContent" label="措施内容" min-width="220" show-overflow-tooltip />
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 'ACTIVE'" type="warning" link size="small" @click="updateMeasure(row.id, 'PAUSED')">暂停</el-button>
              <el-button v-if="row.status !== 'CLOSED'" type="danger" link size="small" @click="updateMeasure(row.id, 'CLOSED')">关闭</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="查冻扣" name="freeze">
        <div class="toolbar">
          <el-select v-model="freezeStatus" placeholder="记录状态" clearable @change="loadFreezeRecords">
            <el-option label="生效中" value="ACTIVE" />
            <el-option label="已解除" value="RELEASED" />
            <el-option label="已到期" value="EXPIRED" />
          </el-select>
          <el-button type="primary" @click="freezeDialogVisible = true"><el-icon><Plus /></el-icon>新增查冻扣</el-button>
        </div>
        <el-table :data="freezeRecords" v-loading="freezeLoading" stripe border>
          <el-table-column prop="recordNo" label="记录编号" min-width="190" fixed="left" />
          <el-table-column prop="customerName" label="客户" min-width="130" />
          <el-table-column prop="authorityName" label="有权机关" min-width="170" />
          <el-table-column prop="documentNo" label="文书编号" min-width="150" />
          <el-table-column prop="actionType" label="类型" width="110" />
          <el-table-column prop="amount" label="金额" width="120" />
          <el-table-column prop="expiryDate" label="到期日期" width="120" />
          <el-table-column prop="status" label="状态" width="100" />
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 'ACTIVE'" type="success" link size="small" @click="releaseFreeze(row.id)">解除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="syncDialogVisible" title="名单更新" width="460px">
      <el-form :model="syncForm" label-width="100px">
        <el-form-item label="名单源ID">
          <el-input v-model="syncForm.sourceId" placeholder="为空表示全部名单源" />
        </el-form-item>
        <el-form-item label="更新模式">
          <el-select v-model="syncForm.updateMode" style="width: 100%;">
            <el-option label="手工更新" value="MANUAL" />
            <el-option label="接口更新" value="API" />
            <el-option label="定时更新" value="SCHEDULED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="syncDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitSync">确认更新</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="jobDialogVisible" title="创建回溯筛查" width="560px">
      <el-form :model="retroForm" label-width="110px">
        <el-form-item label="任务名称"><el-input v-model="retroForm.jobName" /></el-form-item>
        <el-form-item label="筛查范围">
          <el-select v-model="retroForm.scopeType" style="width: 100%;">
            <el-option label="全部客户" value="ALL_CUSTOMERS" />
            <el-option label="高风险客户" value="HIGH_RISK" />
            <el-option label="活跃客户" value="ACTIVE_CUSTOMERS" />
            <el-option label="指定客户" value="CUSTOMER_IDS" />
          </el-select>
        </el-form-item>
        <el-form-item label="客户ID列表" v-if="retroForm.scopeType === 'CUSTOMER_IDS'">
          <el-input v-model="retroForm.customerIds" placeholder="多个ID用英文逗号分隔" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="retroForm.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="jobDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitRetroJob">创建任务</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="measureDialogVisible" title="新增管控措施" width="620px">
      <el-form :model="measureForm" label-width="110px">
        <el-form-item label="客户ID"><el-input v-model="measureForm.customerId" /></el-form-item>
        <el-form-item label="措施类型">
          <el-select v-model="measureForm.measureType" style="width: 100%;">
            <el-option label="客户管控" value="CUSTOMER_CONTROL" />
            <el-option label="交易限制" value="TRANSACTION_RESTRICTION" />
            <el-option label="强化尽调" value="ENHANCED_DUE_DILIGENCE" />
            <el-option label="账户复核" value="ACCOUNT_REVIEW" />
          </el-select>
        </el-form-item>
        <el-form-item label="触发类型">
          <el-select v-model="measureForm.triggerType" style="width: 100%;">
            <el-option label="名单命中" value="WATCHLIST_HIT" />
            <el-option label="有权机关要求" value="AUTHORITY_REQUEST" />
            <el-option label="人工发起" value="MANUAL" />
            <el-option label="风险变化" value="RISK_CHANGE" />
          </el-select>
        </el-form-item>
        <el-form-item label="管控级别">
          <el-select v-model="measureForm.controlLevel" style="width: 100%;">
            <el-option label="低" value="LOW" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="高" value="HIGH" />
            <el-option label="极高" value="CRITICAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="起止日期">
          <el-date-picker v-model="measureDates" type="daterange" start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="措施内容"><el-input v-model="measureForm.measureContent" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="决策理由"><el-input v-model="measureForm.decisionReason" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="measureDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitMeasure">保存措施</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="freezeDialogVisible" title="新增查冻扣" width="620px">
      <el-form :model="freezeForm" label-width="110px">
        <el-form-item label="客户ID"><el-input v-model="freezeForm.customerId" /></el-form-item>
        <el-form-item label="有权机关"><el-input v-model="freezeForm.authorityName" /></el-form-item>
        <el-form-item label="文书编号"><el-input v-model="freezeForm.documentNo" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="freezeForm.actionType" style="width: 100%;">
            <el-option label="查询" value="QUERY" />
            <el-option label="冻结" value="FREEZE" />
            <el-option label="查封" value="SEIZURE" />
            <el-option label="扣划" value="DEDUCTION" />
          </el-select>
        </el-form-item>
        <el-form-item label="金额"><el-input v-model="freezeForm.amount" /></el-form-item>
        <el-form-item label="起止日期">
          <el-date-picker v-model="freezeDates" type="daterange" start-placeholder="生效日期" end-placeholder="到期日期" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="经办人"><el-input v-model="freezeForm.handler" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="freezeForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="freezeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitFreeze">保存记录</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import request from '@/utils/request'

const activeTab = ref('hits')
const submitting = ref(false)
const overview = reactive({ activeMeasures: 0, activeFreezeRecords: 0, pendingScreeningResults: 0, activeRetrospectiveJobs: 0 })

const hits = ref<any[]>([])
const hitStatus = ref('PENDING_REVIEW')
const hitLoading = ref(false)

const updateJobs = ref<any[]>([])
const updateStatus = ref('')
const updateLoading = ref(false)

const retroJobs = ref<any[]>([])
const retroStatus = ref('')
const retroLoading = ref(false)

const measures = ref<any[]>([])
const measureStatus = ref('ACTIVE')
const measureLoading = ref(false)

const freezeRecords = ref<any[]>([])
const freezeStatus = ref('ACTIVE')
const freezeLoading = ref(false)

const syncDialogVisible = ref(false)
const jobDialogVisible = ref(false)
const measureDialogVisible = ref(false)
const freezeDialogVisible = ref(false)

const syncForm = reactive({ sourceId: '', updateMode: 'MANUAL' })
const retroForm = reactive({ jobName: '', scopeType: 'ALL_CUSTOMERS', customerIds: '', remark: '' })
const measureForm = reactive({
  customerId: '',
  measureType: 'CUSTOMER_CONTROL',
  triggerType: 'WATCHLIST_HIT',
  controlLevel: 'HIGH',
  measureContent: '',
  decisionReason: ''
})
const measureDates = ref<string[]>([])
const freezeForm = reactive({
  customerId: '',
  authorityName: '',
  documentNo: '',
  actionType: 'FREEZE',
  amount: '',
  handler: '',
  remark: ''
})
const freezeDates = ref<string[]>([])

async function loadOverview() {
  const res: any = await request.get('/special-prevention/overview')
  Object.assign(overview, res.data)
}

async function loadHits() {
  hitLoading.value = true
  try {
    const res: any = await request.get('/screening/results', { params: { page: 1, size: 20, reviewStatus: hitStatus.value || undefined } })
    hits.value = res.data?.list || []
  } finally {
    hitLoading.value = false
  }
}

async function loadUpdateJobs() {
  updateLoading.value = true
  try {
    const res: any = await request.get('/special-prevention/watchlist-update-jobs', { params: { page: 1, size: 20, status: updateStatus.value || undefined } })
    updateJobs.value = res.data?.list || []
  } finally {
    updateLoading.value = false
  }
}

async function loadRetroJobs() {
  retroLoading.value = true
  try {
    const res: any = await request.get('/special-prevention/retrospective-jobs', { params: { page: 1, size: 20, status: retroStatus.value || undefined } })
    retroJobs.value = res.data?.list || []
  } finally {
    retroLoading.value = false
  }
}

async function loadMeasures() {
  measureLoading.value = true
  try {
    const res: any = await request.get('/special-prevention/measures', { params: { page: 1, size: 20, status: measureStatus.value || undefined } })
    measures.value = res.data?.list || []
  } finally {
    measureLoading.value = false
  }
}

async function loadFreezeRecords() {
  freezeLoading.value = true
  try {
    const res: any = await request.get('/special-prevention/freeze-records', { params: { page: 1, size: 20, status: freezeStatus.value || undefined } })
    freezeRecords.value = res.data?.list || []
  } finally {
    freezeLoading.value = false
  }
}

async function submitSync() {
  submitting.value = true
  try {
    await request.post('/special-prevention/watchlist-update-jobs', {
      sourceId: syncForm.sourceId ? Number(syncForm.sourceId) : undefined,
      updateMode: syncForm.updateMode
    })
    ElMessage.success('名单更新任务已记录')
    syncDialogVisible.value = false
    await Promise.all([loadOverview(), loadUpdateJobs()])
  } finally {
    submitting.value = false
  }
}

async function submitRetroJob() {
  submitting.value = true
  try {
    await request.post('/special-prevention/retrospective-jobs', { ...retroForm })
    ElMessage.success('回溯筛查任务已创建')
    jobDialogVisible.value = false
    retroForm.jobName = ''
    retroForm.customerIds = ''
    retroForm.remark = ''
    await Promise.all([loadOverview(), loadRetroJobs()])
  } finally {
    submitting.value = false
  }
}

async function submitMeasure() {
  submitting.value = true
  try {
    await request.post('/special-prevention/measures', {
      ...measureForm,
      customerId: Number(measureForm.customerId),
      startDate: measureDates.value?.[0],
      endDate: measureDates.value?.[1]
    })
    ElMessage.success('管控措施已创建')
    measureDialogVisible.value = false
    measureForm.customerId = ''
    measureForm.measureContent = ''
    measureForm.decisionReason = ''
    measureDates.value = []
    await Promise.all([loadOverview(), loadMeasures()])
  } finally {
    submitting.value = false
  }
}

async function submitFreeze() {
  submitting.value = true
  try {
    await request.post('/special-prevention/freeze-records', {
      ...freezeForm,
      customerId: Number(freezeForm.customerId),
      amount: freezeForm.amount ? Number(freezeForm.amount) : undefined,
      currency: 'CNY',
      effectiveDate: freezeDates.value?.[0],
      expiryDate: freezeDates.value?.[1]
    })
    ElMessage.success('查冻扣记录已创建')
    freezeDialogVisible.value = false
    freezeForm.customerId = ''
    freezeForm.authorityName = ''
    freezeForm.documentNo = ''
    freezeForm.amount = ''
    freezeForm.remark = ''
    freezeDates.value = []
    await Promise.all([loadOverview(), loadFreezeRecords()])
  } finally {
    submitting.value = false
  }
}

async function escalateHit(id: string) {
  await ElMessageBox.confirm('确认将该名单命中升级为预警？', '升级预警', { type: 'warning' })
  await request.post(`/special-prevention/screening-results/${id}/escalate-alert`, null, { params: { reason: '特别预防命中处置升级' } })
  ElMessage.success('已升级为预警')
  await Promise.all([loadOverview(), loadHits()])
}

async function createCase(id: string) {
  await ElMessageBox.confirm('确认将该名单命中生成案件？', '生成案件', { type: 'warning' })
  await request.post(`/special-prevention/screening-results/${id}/create-case`, null, { params: { reason: '名单命中确认后生成案件' } })
  ElMessage.success('案件已生成')
  await Promise.all([loadOverview(), loadHits()])
}

async function updateMeasure(id: string, status: string) {
  await request.put(`/special-prevention/measures/${id}/status`, null, { params: { status, reason: status === 'CLOSED' ? '人工关闭' : '人工暂停' } })
  ElMessage.success('措施状态已更新')
  await Promise.all([loadOverview(), loadMeasures()])
}

async function releaseFreeze(id: string) {
  await request.put(`/special-prevention/freeze-records/${id}/status`, null, { params: { status: 'RELEASED', remark: '人工解除' } })
  ElMessage.success('查冻扣记录已解除')
  await Promise.all([loadOverview(), loadFreezeRecords()])
}

function reviewStatusLabel(status: string) {
  return { PENDING_REVIEW: '待复核', CONFIRMED: '已确认', EXCLUDED: '已排除', ESCALATED: '已升级' }[status] || status
}

function reviewStatusType(status: string) {
  return ({ PENDING_REVIEW: 'warning', CONFIRMED: 'success', EXCLUDED: 'info', ESCALATED: 'danger' }[status] || 'info') as any
}

function jobStatusType(status: string) {
  return ({ SUCCESS: 'success', COMPLETED: 'success', RUNNING: 'warning', PENDING: 'info', FAILED: 'danger' }[status] || 'info') as any
}

function levelType(level: string) {
  return ({ LOW: 'info', MEDIUM: 'warning', HIGH: 'danger', CRITICAL: 'danger' }[level] || 'info') as any
}

onMounted(async () => {
  await Promise.all([loadOverview(), loadHits(), loadUpdateJobs(), loadRetroJobs(), loadMeasures(), loadFreezeRecords()])
})
</script>

<style scoped>
.prevention-page {
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

.heading-actions,
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
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

.work-tabs {
  padding: 16px;
  background: var(--bg-surface);
  border: 1px solid var(--border-default);
  border-radius: 8px;
}

.toolbar {
  justify-content: flex-end;
  margin-bottom: 12px;
}

.toolbar .el-select {
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
