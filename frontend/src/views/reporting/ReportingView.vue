<template>
  <div>
    <el-card>
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center;">
          <span>大额交易报告</span>
          <div style="display:flex;gap:8px;">
            <el-button type="primary" @click="generateDialogVisible = true">生成报告</el-button>
            <el-button type="danger" :loading="retryingFailed" @click="handleRetryFailed">重试失败</el-button>
          </div>
        </div>
      </template>

      <!-- 搜索筛选栏 -->
      <div style="margin-bottom:16px;display:flex;gap:12px;flex-wrap:wrap;">
        <el-select v-model="statusFilter" placeholder="报告状态" clearable style="width:160px;" @change="loadData">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已审核" value="REVIEWED" />
          <el-option label="已报送" value="SUBMITTED" />
          <el-option label="报送失败" value="FAILED" />
          <el-option label="已重报" value="RESUBMITTED" />
        </el-select>
        <el-date-picker
          v-model="dateRangeFilter"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          style="width:260px;"
          @change="loadData"
        />
        <el-button type="primary" @click="loadData">搜索</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>

      <!-- 数据表格 -->
      <el-table :data="reports" stripe v-loading="loading" border>
        <el-table-column prop="reportNo" label="报告编号" width="180" />
        <el-table-column label="客户" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.customerName || `客户ID ${row.customerId || '-'}` }}</template>
        </el-table-column>
        <el-table-column prop="transactionType" label="交易类型" width="110">
          <template #default="{ row }">{{ transactionTypeLabel(row.transactionType) }}</template>
        </el-table-column>
        <el-table-column label="交易金额" width="150" align="right">
          <template #default="{ row }">
            {{ formatAmount(row.amount) }} {{ row.currency || '' }}
          </template>
        </el-table-column>
        <el-table-column prop="paymentMethod" label="支付方式" width="110">
          <template #default="{ row }">{{ paymentMethodLabel(row.paymentMethod) }}</template>
        </el-table-column>
        <el-table-column prop="reportDate" label="报告日期" width="120" align="center" />
        <el-table-column prop="transactionTime" label="交易时间" width="170" />
        <el-table-column prop="reportStatus" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.reportStatus)" size="small">
              {{ statusLabel(row.reportStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reviewedBy" label="审核人" width="120">
          <template #default="{ row }">{{ formatReportReviewer(row) }}</template>
        </el-table-column>
        <el-table-column prop="createdTime" label="创建时间" width="170" />
        <el-table-column prop="submittedTime" label="报送时间" width="170">
          <template #default="{ row }">{{ row.submittedTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.reportStatus === 'DRAFT'" type="success" size="small" @click="openReviewDialog(row)">审核</el-button>
            <el-button v-if="row.reportStatus === 'REVIEWED'" type="warning" size="small" @click="handleSubmitReport(row)">提交报送</el-button>
            <el-button v-if="row.reportStatus === 'REVIEWED' || row.reportStatus === 'SUBMITTED' || row.reportStatus === 'RESUBMITTED'" type="info" size="small" @click="handleExportXml(row)">导出XML</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div style="margin-top:16px;display:flex;justify-content:flex-end;">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 生成报告弹窗 -->
    <el-dialog v-model="generateDialogVisible" title="生成大额交易报告" width="500px" destroy-on-close>
      <el-form :model="generateForm" label-width="100px" ref="generateFormRef" :rules="generateRules">
        <el-form-item label="日期范围" prop="dateRange">
          <el-date-picker
            v-model="generateForm.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width:100%;"
          />
        </el-form-item>
        <el-form-item label="报告类型" prop="reportType">
          <el-select v-model="generateForm.reportType" placeholder="请选择报告类型" style="width:100%;">
            <el-option label="大额交易报告" value="LARGE_TRANSACTION" />
            <el-option label="大额现金交易报告" value="LARGE_CASH_TRANSACTION" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="generateDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="generating" @click="handleGenerate">确认生成</el-button>
      </template>
    </el-dialog>

    <!-- 报告详情弹窗 -->
    <el-dialog v-model="detailDialogVisible" title="大额交易报告详情" width="650px" destroy-on-close>
      <el-descriptions :column="2" border v-if="detailData">
        <el-descriptions-item label="报告编号">{{ detailData.reportNo }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ detailData.customerName || `客户ID ${detailData.customerId || '-'}` }}</el-descriptions-item>
        <el-descriptions-item label="报告日期">{{ detailData.reportDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(detailData.reportStatus)" size="small">
            {{ statusLabel(detailData.reportStatus) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="交易类型">{{ transactionTypeLabel(detailData.transactionType) }}</el-descriptions-item>
        <el-descriptions-item label="交易金额">{{ formatAmount(detailData.amount) }} {{ detailData.currency || '' }}</el-descriptions-item>
        <el-descriptions-item label="支付方式">{{ paymentMethodLabel(detailData.paymentMethod) }}</el-descriptions-item>
        <el-descriptions-item label="交易时间">{{ detailData.transactionTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ formatReportReviewer(detailData) }}</el-descriptions-item>
        <el-descriptions-item label="报送时间">{{ detailData.submittedTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="交易对手" :span="2">{{ formatCounterparty(detailData.counterpartyInfo) }}</el-descriptions-item>
        <el-descriptions-item label="创建时间" :span="2">{{ detailData.createdTime }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 审核弹窗 -->
    <el-dialog v-model="reviewDialogVisible" title="审核大额交易报告" width="480px" destroy-on-close>
      <el-form :model="reviewForm" label-width="80px">
        <el-form-item label="审核结果">
          <el-radio-group v-model="reviewForm.approved">
            <el-radio :value="true">通过</el-radio>
            <el-radio :value="false">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审核意见">
          <el-input v-model="reviewForm.remark" type="textarea" :rows="4" placeholder="请输入审核意见" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="reviewing" @click="handleReview">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'
import { currentOperatorName, formatOperatorName } from '@/utils/operatorDisplay'

// ==================== 列表 ====================
const userStore = useUserStore()
const loading = ref(false)
const reports = ref<any[]>([])
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const statusFilter = ref('')
const dateRangeFilter = ref<string[] | null>(null)

async function loadData() {
  loading.value = true
  try {
    const res: any = await request.get('/reporting/large-txn/page', {
      params: {
        page: currentPage.value,
        size: pageSize.value,
        reportStatus: statusFilter.value || undefined,
        startDate: dateRangeFilter.value?.[0] || undefined,
        endDate: dateRangeFilter.value?.[1] || undefined
      }
    })
    const pageData = res.data || res
    reports.value = pageData.list || pageData.records || pageData.content || []
    total.value = pageData.total || 0
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  statusFilter.value = ''
  dateRangeFilter.value = null
  currentPage.value = 1
  loadData()
}

// ==================== 状态映射 ====================
const STATUS_MAP: Record<string, string> = {
  DRAFT: '草稿',
  REVIEWED: '已审核',
  SUBMITTED: '已报送',
  FAILED: '报送失败',
  RESUBMITTED: '已重报'
}

function statusLabel(status: string) {
  return STATUS_MAP[status] || status
}

function statusTagType(status: string) {
  const map: Record<string, string> = {
    DRAFT: 'warning',
    REVIEWED: '',
    SUBMITTED: 'success',
    FAILED: 'danger',
    RESUBMITTED: 'success'
  }
  return (map[status] || 'info') as any
}

function formatAmount(amount: number | string | undefined) {
  if (amount == null) return '-'
  return Number(amount).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function transactionTypeLabel(type: string | undefined) {
  const map: Record<string, string> = {
    PREMIUM: '保费缴纳',
    CLAIM: '理赔',
    SURRENDER: '退保',
    LOAN: '保单贷款',
    REFUND: '退款'
  }
  return type ? (map[type] || type) : '-'
}

function paymentMethodLabel(method: string | undefined) {
  const map: Record<string, string> = {
    CASH: '现金',
    TRANSFER: '转账',
    BANK_TRANSFER: '银行转账',
    ONLINE: '线上支付',
    COUNTER: '柜面'
  }
  return method ? (map[method] || method) : '-'
}

function formatReportReviewer(row: any) {
  return formatOperatorName(row?.reviewedBy, row?.reportStatus === 'DRAFT' ? '待审核' : '待补录')
}

function formatCounterparty(counterpartyInfo: string | undefined) {
  if (!counterpartyInfo) return '-'
  try {
    const data = JSON.parse(counterpartyInfo)
    return [data.name, data.account, data.bank].filter(Boolean).join(' / ') || counterpartyInfo
  } catch {
    return counterpartyInfo
  }
}

// ==================== 生成报告 ====================
const generateDialogVisible = ref(false)
const generating = ref(false)
const generateFormRef = ref<FormInstance>()
const generateForm = reactive({
  dateRange: [] as string[],
  reportType: 'LARGE_TRANSACTION'
})
const generateRules: FormRules = {
  dateRange: [{ required: true, message: '请选择日期范围', trigger: 'change' }],
  reportType: [{ required: true, message: '请选择报告类型', trigger: 'change' }]
}

async function handleGenerate() {
  try {
    await generateFormRef.value?.validate()
  } catch {
    return
  }
  generating.value = true
  try {
    await request.post('/reporting/large-txn/generate', {
      startDate: generateForm.dateRange[0],
      endDate: generateForm.dateRange[1],
      reportType: generateForm.reportType
    })
    ElMessage.success('报告生成成功')
    generateDialogVisible.value = false
    loadData()
  } catch {
    // handled
  } finally {
    generating.value = false
  }
}

// ==================== 详情 ====================
const detailDialogVisible = ref(false)
const detailData = ref<any>(null)

async function openDetail(row: any) {
  detailData.value = row
  detailDialogVisible.value = true
}

// ==================== 审核 ====================
const reviewDialogVisible = ref(false)
const reviewTargetId = ref<number | string>('')
const reviewing = ref(false)
const reviewForm = reactive({ approved: true, remark: '' })

function openReviewDialog(row: any) {
  reviewTargetId.value = row.id
  reviewForm.approved = true
  reviewForm.remark = ''
  reviewDialogVisible.value = true
}

async function handleReview() {
  if (!reviewForm.approved) {
    ElMessage.warning('当前大额交易报告接口暂不支持驳回，请选择通过或由后续审批流处理')
    return
  }
  reviewing.value = true
  try {
    const reviewedBy = currentOperatorName(userStore.userInfo)
    await request.post(`/reporting/large-txn/${reviewTargetId.value}/review`, undefined, { params: { reviewedBy } })
    ElMessage.success('审核通过')
    reviewDialogVisible.value = false
    loadData()
  } catch {
    // handled
  } finally {
    reviewing.value = false
  }
}

// ==================== 提交报送 ====================
async function handleSubmitReport(row: any) {
  try {
    await ElMessageBox.confirm('确认将该报告提交报送？', '提示', { type: 'warning' })
    await request.post(`/reporting/large-txn/${row.id}/submit`)
    ElMessage.success('提交报送成功')
    loadData()
  } catch {
    // cancelled or error
  }
}

// ==================== 导出XML ====================
async function handleExportXml(row: any) {
  try {
    const res: any = await request.get(`/reporting/large-txn/${row.id}/xml`, {
      responseType: 'blob' as any
    })
    const blob = res instanceof Blob ? res : new Blob([res], { type: 'application/xml' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${row.reportNo || 'report'}.xml`
    link.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success('XML导出成功')
  } catch {
    ElMessage.error('XML导出失败')
  }
}

// ==================== 重试失败 ====================
const retryingFailed = ref(false)

async function handleRetryFailed() {
  try {
    await ElMessageBox.confirm('确认重试所有失败的报告？', '提示', { type: 'info' })
    retryingFailed.value = true
    await request.post('/reporting/large-txn/retry-failed')
    ElMessage.success('重试指令已发送')
    loadData()
  } catch {
    // cancelled or error
  } finally {
    retryingFailed.value = false
  }
}

onMounted(loadData)
</script>
