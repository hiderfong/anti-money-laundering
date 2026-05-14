<template>
  <div class="case-container">
    <!-- 搜索筛选栏 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="filterForm" @submit.prevent="handleSearch">
        <el-form-item label="案件状态">
          <el-select v-model="filterForm.caseStatus" placeholder="全部状态" clearable style="width:140px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="调查中" value="INVESTIGATING" />
            <el-option label="待审批" value="PENDING_APPROVAL" />
            <el-option label="已报送" value="SUBMITTED" />
            <el-option label="已结案" value="CLOSED" />
          </el-select>
        </el-form-item>
        <el-form-item label="案件类型">
          <el-select v-model="filterForm.caseType" placeholder="全部类型" clearable style="width:140px">
            <el-option label="可疑交易" value="SUSPICIOUS_TX" />
            <el-option label="大额交易" value="LARGE_TX" />
            <el-option label="异常行为" value="ABNORMAL_BEHAVIOR" />
            <el-option label="黑名单命中" value="BLACKLIST_HIT" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="filterForm.priority" placeholder="全部优先级" clearable style="width:120px">
            <el-option label="高" value="HIGH" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="低" value="LOW" />
          </el-select>
        </el-form-item>
        <el-form-item label="创建时间">
          <el-date-picker
            v-model="filterForm.timeRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width:260px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" @click="showCreateDialog">新建案件</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card shadow="never" style="margin-top:16px">
      <el-table :data="tableData" stripe border v-loading="loading" style="width:100%">
        <el-table-column prop="caseNo" label="案件编号" width="170" fixed />
        <el-table-column prop="customerName" label="客户名称" width="130" />
        <el-table-column prop="caseType" label="案件类型" width="120">
          <template #default="{ row }">
            {{ caseTypeLabel(row.caseType) }}
          </template>
        </el-table-column>
        <el-table-column prop="caseStatus" label="案件状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.caseStatus)" size="small" effect="dark">
              {{ statusLabel(row.caseStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="90">
          <template #default="{ row }">
            <el-tag :type="priorityTagType(row.priority)" size="small">
              {{ priorityLabel(row.priority) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="summary" label="摘要" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createdBy" label="创建人" width="100" />
        <el-table-column prop="createdTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="310" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openDetail(row)">详情</el-button>
            <el-button
              v-for="action in getAvailableActions(row.caseStatus)"
              :key="action.target"
              :type="action.btnType"
              link size="small"
              @click="handleStatusAction(row, action)"
            >
              {{ action.label }}
            </el-button>
            <el-button
              v-if="row.caseStatus !== 'CLOSED'"
              type="warning" link size="small"
              @click="showInvestigationDialog(row)"
            >
              调查记录
            </el-button>
            <el-button
              v-if="row.caseStatus !== 'CLOSED'"
              type="danger" link size="small"
              @click="handleCloseCaseAction(row)"
            >
              关闭案件
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        style="margin-top:16px;justify-content:flex-end"
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadData"
        @current-change="loadData"
      />
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="案件详情" width="800px" destroy-on-close>
      <template v-if="detailData">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="案件编号">{{ detailData.case.caseNo }}</el-descriptions-item>
          <el-descriptions-item label="客户名称">{{ detailData.case.customerName }}</el-descriptions-item>
          <el-descriptions-item label="案件类型">{{ caseTypeLabel(detailData.case.caseType) }}</el-descriptions-item>
          <el-descriptions-item label="案件状态">
            <el-tag :type="statusTagType(detailData.case.caseStatus)" size="small" effect="dark">
              {{ statusLabel(detailData.case.caseStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="优先级">
            <el-tag :type="priorityTagType(detailData.case.priority)" size="small">
              {{ priorityLabel(detailData.case.priority) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="预警编号">{{ detailData.case.alertId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{ detailData.case.createdBy }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detailData.case.createdTime }}</el-descriptions-item>
          <el-descriptions-item label="摘要" :span="2">{{ detailData.case.summary || '-' }}</el-descriptions-item>
        </el-descriptions>

        <!-- STR报告 -->
        <template v-if="detailData.strReport && detailData.strReport.id">
          <el-divider content-position="left">STR 报告</el-divider>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="报告编号">{{ detailData.strReport.reportNo }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ detailData.strReport.status }}</el-descriptions-item>
            <el-descriptions-item label="提交时间" :span="2">{{ detailData.strReport.submitTime }}</el-descriptions-item>
          </el-descriptions>
        </template>

        <!-- 调查记录 -->
        <el-divider content-position="left">调查记录</el-divider>
        <el-timeline v-if="detailData.investigations && detailData.investigations.length">
          <el-timeline-item
            v-for="inv in detailData.investigations"
            :key="inv.id"
            :timestamp="inv.createdTime"
            placement="top"
          >
            <el-card shadow="never">
              <p><strong>调查内容：</strong>{{ inv.content }}</p>
              <p><strong>结论：</strong>{{ inv.conclusion || '-' }}</p>
              <p style="color:#999;font-size:12px">调查人ID：{{ inv.investigatorId }}</p>
            </el-card>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无调查记录" :image-size="60" />

        <!-- 状态变更历史 -->
        <el-divider content-position="left">状态变更历史</el-divider>
        <el-table :data="detailData.statusLogs" border size="small" v-if="detailData.statusLogs && detailData.statusLogs.length">
          <el-table-column prop="fromStatus" label="原状态" width="120">
            <template #default="{ row }">{{ statusLabel(row.fromStatus) }}</template>
          </el-table-column>
          <el-table-column prop="toStatus" label="新状态" width="120">
            <template #default="{ row }">{{ statusLabel(row.toStatus) }}</template>
          </el-table-column>
          <el-table-column prop="changedBy" label="操作人" width="120" />
          <el-table-column prop="changedTime" label="操作时间" width="170" />
          <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
        </el-table>
        <el-empty v-else description="暂无状态变更记录" :image-size="60" />
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 创建案件弹窗 -->
    <el-dialog v-model="createVisible" title="新建案件" width="520px" destroy-on-close>
      <el-form :model="createForm" :rules="createRules" ref="createFormRef" label-width="100px">
        <el-form-item label="预警编号" prop="alertId">
          <el-input v-model="createForm.alertId" placeholder="关联的预警编号（可选）" />
        </el-form-item>
        <el-form-item label="案件类型" prop="caseType">
          <el-select v-model="createForm.caseType" placeholder="请选择案件类型" style="width:100%">
            <el-option label="可疑交易" value="SUSPICIOUS_TX" />
            <el-option label="大额交易" value="LARGE_TX" />
            <el-option label="异常行为" value="ABNORMAL_BEHAVIOR" />
            <el-option label="黑名单命中" value="BLACKLIST_HIT" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级" prop="priority">
          <el-select v-model="createForm.priority" placeholder="请选择优先级" style="width:100%">
            <el-option label="高" value="HIGH" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="低" value="LOW" />
          </el-select>
        </el-form-item>
        <el-form-item label="摘要" prop="summary">
          <el-input v-model="createForm.summary" type="textarea" :rows="3" placeholder="请输入案件摘要" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleCreate">确定创建</el-button>
      </template>
    </el-dialog>

    <!-- 添加调查记录弹窗 -->
    <el-dialog v-model="investigationVisible" title="添加调查记录" width="520px" destroy-on-close>
      <el-form :model="investigationForm" :rules="investigationRules" ref="investigationFormRef" label-width="100px">
        <el-form-item label="调查内容" prop="content">
          <el-input v-model="investigationForm.content" type="textarea" :rows="4" placeholder="请输入调查内容" />
        </el-form-item>
        <el-form-item label="调查结论" prop="conclusion">
          <el-input v-model="investigationForm.conclusion" type="textarea" :rows="3" placeholder="请输入调查结论" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="investigationVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleAddInvestigation">提交</el-button>
      </template>
    </el-dialog>

    <!-- 状态流转确认弹窗 -->
    <el-dialog v-model="statusChangeVisible" title="状态变更" width="460px" destroy-on-close>
      <el-form :model="statusChangeForm" label-width="80px">
        <el-form-item label="当前状态">
          <el-tag :type="statusTagType(statusChangeForm.fromStatus)" effect="dark" size="small">
            {{ statusLabel(statusChangeForm.fromStatus) }}
          </el-tag>
        </el-form-item>
        <el-form-item label="目标状态">
          <el-tag :type="statusTagType(statusChangeForm.toStatus)" effect="dark" size="small">
            {{ statusLabel(statusChangeForm.toStatus) }}
          </el-tag>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="statusChangeForm.remark" type="textarea" :rows="2" placeholder="请输入变更备注（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="statusChangeVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="confirmStatusChange">确认变更</el-button>
      </template>
    </el-dialog>

    <!-- 关闭案件弹窗 -->
    <el-dialog v-model="closeVisible" title="关闭案件" width="460px" destroy-on-close>
      <el-form :model="closeForm" :rules="closeRules" ref="closeFormRef" label-width="100px">
        <el-form-item label="案件编号">
          <el-input :model-value="closeForm.caseNo" disabled />
        </el-form-item>
        <el-form-item label="关闭原因" prop="reason">
          <el-input v-model="closeForm.reason" type="textarea" :rows="4" placeholder="请输入关闭原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitLoading" @click="handleCloseCase">确认关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import request from '@/utils/request'

// ==================== 数据定义 ====================

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref<any[]>([])

const pagination = reactive({ page: 1, size: 20, total: 0 })

const filterForm = reactive({
  caseStatus: '',
  caseType: '',
  priority: '',
  timeRange: null as string[] | null
})

// 详情弹窗
const detailVisible = ref(false)
const detailData = ref<any>(null)

// 创建案件
const createVisible = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive({ alertId: '', caseType: '', priority: '', summary: '' })
const createRules: FormRules = {
  caseType: [{ required: true, message: '请选择案件类型', trigger: 'change' }],
  priority: [{ required: true, message: '请选择优先级', trigger: 'change' }],
  summary: [{ required: true, message: '请输入案件摘要', trigger: 'blur' }]
}

// 调查记录
const investigationVisible = ref(false)
const investigationFormRef = ref<FormInstance>()
const investigationTargetId = ref<number>(0)
const investigationForm = reactive({ content: '', conclusion: '' })
const investigationRules: FormRules = {
  content: [{ required: true, message: '请输入调查内容', trigger: 'blur' }]
}

// 状态变更
const statusChangeVisible = ref(false)
const statusChangeForm = reactive({ caseId: 0, fromStatus: '', toStatus: '', remark: '' })

// 关闭案件
const closeVisible = ref(false)
const closeFormRef = ref<FormInstance>()
const closeForm = reactive({ caseId: 0, caseNo: '', reason: '' })
const closeRules: FormRules = {
  reason: [{ required: true, message: '请输入关闭原因', trigger: 'blur' }]
}

// ==================== 工具函数 ====================

const STATUS_MAP: Record<string, string> = {
  DRAFT: '草稿', INVESTIGATING: '调查中', PENDING_APPROVAL: '待审批',
  SUBMITTED: '已报送', CLOSED: '已结案'
}
const CASE_TYPE_MAP: Record<string, string> = {
  SUSPICIOUS_TX: '可疑交易', LARGE_TX: '大额交易', ABNORMAL_BEHAVIOR: '异常行为',
  BLACKLIST_HIT: '黑名单命中', OTHER: '其他'
}
const PRIORITY_MAP: Record<string, string> = { HIGH: '高', MEDIUM: '中', LOW: '低' }

function statusLabel(s: string) { return STATUS_MAP[s] || s }
function caseTypeLabel(s: string) { return CASE_TYPE_MAP[s] || s }
function priorityLabel(s: string) { return PRIORITY_MAP[s] || s }

function statusTagType(s: string): '' | 'success' | 'warning' | 'info' | 'danger' {
  return { DRAFT: 'info', INVESTIGATING: '', PENDING_APPROVAL: 'warning', SUBMITTED: 'success', CLOSED: 'info' }[s] as any || 'info'
}
function priorityTagType(p: string): '' | 'success' | 'warning' | 'info' | 'danger' {
  return { HIGH: 'danger', MEDIUM: 'warning', LOW: 'info' }[p] as any || 'info'
}

// 状态流转规则：DRAFT->INVESTIGATING->PENDING_APPROVAL->SUBMITTED->CLOSED
const STATUS_FLOW: Record<string, { target: string; label: string; btnType: string }[]> = {
  DRAFT: [{ target: 'INVESTIGATING', label: '开始调查', btnType: 'primary' }],
  INVESTIGATING: [{ target: 'PENDING_APPROVAL', label: '提交审批', btnType: 'warning' }],
  PENDING_APPROVAL: [
    { target: 'SUBMITTED', label: '审批通过', btnType: 'success' },
    { target: 'INVESTIGATING', label: '退回调查', btnType: 'info' }
  ],
  SUBMITTED: []
}

function getAvailableActions(status: string) {
  return STATUS_FLOW[status] || []
}

// ==================== 数据加载 ====================

async function loadData() {
  loading.value = true
  try {
    const params: any = { page: pagination.page, size: pagination.size }
    if (filterForm.caseStatus) params.caseStatus = filterForm.caseStatus
    if (filterForm.caseType) params.caseType = filterForm.caseType
    if (filterForm.priority) params.priority = filterForm.priority
    if (filterForm.timeRange && filterForm.timeRange.length === 2) {
      params.startTime = filterForm.timeRange[0]
      params.endTime = filterForm.timeRange[1]
    }
    const res: any = await request.get('/cases/page', { params })
    tableData.value = res.data?.list || []
    pagination.total = res.data?.total || 0
  } catch (e) {
    ElMessage.error('加载案件列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  loadData()
}

function handleReset() {
  filterForm.caseStatus = ''
  filterForm.caseType = ''
  filterForm.priority = ''
  filterForm.timeRange = null
  handleSearch()
}

// ==================== 详情弹窗 ====================

async function openDetail(row: any) {
  detailVisible.value = true
  detailData.value = null
  try {
    const res: any = await request.get(`/cases/${row.id}`)
    detailData.value = res.data
  } catch (e) {
    ElMessage.error('加载案件详情失败')
    detailVisible.value = false
  }
}

// ==================== 创建案件 ====================

function showCreateDialog() {
  createForm.alertId = ''
  createForm.caseType = ''
  createForm.priority = ''
  createForm.summary = ''
  createVisible.value = true
}

async function handleCreate() {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  try {
    await request.post('/cases', {
      alertId: createForm.alertId || undefined,
      caseType: createForm.caseType,
      priority: createForm.priority,
      summary: createForm.summary
    })
    ElMessage.success('案件创建成功')
    createVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error('创建案件失败')
  } finally {
    submitLoading.value = false
  }
}

// ==================== 调查记录 ====================

function showInvestigationDialog(row: any) {
  investigationTargetId.value = row.id
  investigationForm.content = ''
  investigationForm.conclusion = ''
  investigationVisible.value = true
}

async function handleAddInvestigation() {
  const valid = await investigationFormRef.value?.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  try {
    await request.post(`/cases/${investigationTargetId.value}/investigation`, null, {
      params: { content: investigationForm.content, conclusion: investigationForm.conclusion }
    })
    ElMessage.success('调查记录添加成功')
    investigationVisible.value = false
  } catch (e) {
    ElMessage.error('添加调查记录失败')
  } finally {
    submitLoading.value = false
  }
}

// ==================== 状态流转 ====================

function handleStatusAction(row: any, action: { target: string; label: string }) {
  statusChangeForm.caseId = row.id
  statusChangeForm.fromStatus = row.caseStatus
  statusChangeForm.toStatus = action.target
  statusChangeForm.remark = ''
  statusChangeVisible.value = true
}

async function confirmStatusChange() {
  submitLoading.value = true
  try {
    await request.put(`/cases/${statusChangeForm.caseId}/status`, null, {
      params: { toStatus: statusChangeForm.toStatus, remark: statusChangeForm.remark }
    })
    ElMessage.success('状态变更成功')
    statusChangeVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error('状态变更失败')
  } finally {
    submitLoading.value = false
  }
}

// ==================== 关闭案件 ====================

function handleCloseCaseAction(row: any) {
  closeForm.caseId = row.id
  closeForm.caseNo = row.caseNo
  closeForm.reason = ''
  closeVisible.value = true
}

async function handleCloseCase() {
  const valid = await closeFormRef.value?.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  try {
    await request.post(`/cases/${closeForm.caseId}/close`, null, {
      params: { reason: closeForm.reason }
    })
    ElMessage.success('案件已关闭')
    closeVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error('关闭案件失败')
  } finally {
    submitLoading.value = false
  }
}

// ==================== 初始化 ====================

onMounted(loadData)
</script>

<style scoped>
.case-container {
  padding: 16px;
}
.filter-card :deep(.el-card__body) {
  padding-bottom: 2px;
}

/* 操作按钮文字颜色统一为白色 */
:deep(.el-table .el-button--primary.is-link) {
  color: #fff !important;
}
:deep(.el-table .el-button--warning.is-link) {
  color: #fff !important;
}
:deep(.el-table .el-button--danger.is-link) {
  color: #fff !important;
}
:deep(.el-table .el-button--success.is-link) {
  color: #fff !important;
}
:deep(.el-table .el-button--info.is-link) {
  color: #fff !important;
}

/* 操作按钮悬浮效果 - 与详情按钮悬浮效果一致 */
:deep(.el-table .el-button--warning.is-link:hover) {
  color: rgba(255, 255, 255, 0.8) !important;
}
:deep(.el-table .el-button--danger.is-link:hover) {
  color: rgba(255, 255, 255, 0.8) !important;
}
:deep(.el-table .el-button--success.is-link:hover) {
  color: rgba(255, 255, 255, 0.8) !important;
}
:deep(.el-table .el-button--info.is-link:hover) {
  color: rgba(255, 255, 255, 0.8) !important;
}
</style>
