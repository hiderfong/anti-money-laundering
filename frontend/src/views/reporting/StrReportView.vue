<template>
  <div>
    <el-card>
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center;">
          <span>STR可疑交易报告</span>
          <el-button type="primary" @click="openCreateDialog">新建报告</el-button>
        </div>
      </template>

      <!-- 筛选栏 -->
      <div style="margin-bottom:16px;display:flex;gap:12px;">
        <el-select v-model="statusFilter" placeholder="按状态筛选" clearable style="width:180px;" @change="loadData">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="待审核" value="PENDING_REVIEW" />
          <el-option label="已审核" value="APPROVED" />
          <el-option label="已报送" value="SUBMITTED" />
        </el-select>
      </div>

      <!-- 报告列表 -->
      <el-table :data="reports" stripe v-loading="loading" border>
        <el-table-column prop="reportNo" label="报告编号" width="155" show-overflow-tooltip />
        <el-table-column prop="caseId" label="关联案件ID" width="95" />
        <el-table-column prop="reportType" label="报告类型" width="95" />
        <el-table-column prop="reportContent" label="报告内容" min-width="160" show-overflow-tooltip />
        <el-table-column prop="reportStatus" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.reportStatus)" size="small">
              {{ statusLabel(row.reportStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" label="创建时间" width="155" show-overflow-tooltip />
        <el-table-column label="操作" width="170">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.reportStatus === 'DRAFT'" type="primary" size="small" @click="submitReview(row)">提交审核</el-button>
            <el-button v-if="row.reportStatus === 'PENDING_REVIEW'" type="success" size="small" @click="openReviewDialog(row)">审核</el-button>
            <el-button v-if="row.reportStatus === 'APPROVED'" type="warning" size="small" @click="submitRegulator(row)">报送监管</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div style="margin-top:16px;display:flex;justify-content:flex-end;">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 创建报告弹窗 -->
    <el-dialog v-model="createDialogVisible" title="新建STR报告" width="560px" destroy-on-close>
      <el-form :model="createForm" label-width="100px" ref="createFormRef" :rules="createRules">
        <el-form-item label="关联案件ID" prop="caseId">
          <el-input v-model="createForm.caseId" placeholder="请输入案件ID" />
        </el-form-item>
        <el-form-item label="报告类型" prop="reportType">
          <el-select v-model="createForm.reportType" placeholder="请选择报告类型" style="width:100%;">
            <el-option label="可疑交易报告" value="SUSPICIOUS_TRANSACTION" />
            <el-option label="大额交易报告" value="LARGE_TRANSACTION" />
            <el-option label="涉恐融资报告" value="TERROR_FINANCING" />
          </el-select>
        </el-form-item>
        <el-form-item label="报告内容" prop="reportContent">
          <el-input v-model="createForm.reportContent" type="textarea" :rows="6" placeholder="请输入报告内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="submitting" @click="handleCreate">确认创建</el-button>
      </template>
    </el-dialog>

    <!-- 报告详情弹窗 -->
    <el-dialog v-model="detailDialogVisible" title="STR报告详情" width="600px" destroy-on-close>
      <el-descriptions :column="2" border v-if="detailData">
        <el-descriptions-item label="报告编号">{{ detailData.reportNo }}</el-descriptions-item>
        <el-descriptions-item label="关联案件ID">{{ detailData.caseId }}</el-descriptions-item>
        <el-descriptions-item label="报告类型">{{ detailData.reportType }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(detailData.reportStatus)" size="small">
            {{ statusLabel(detailData.reportStatus) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="审核人ID">{{ detailData.reviewerId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="报送时间">{{ detailData.submitTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间" :span="2">{{ detailData.createdTime }}</el-descriptions-item>
        <el-descriptions-item label="审核意见" :span="2">{{ detailData.reviewRemark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="报告内容" :span="2">{{ detailData.reportContent }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 审核弹窗 -->
    <el-dialog v-model="reviewDialogVisible" title="审核STR报告" width="480px" destroy-on-close>
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
        <el-button type="primary" :disabled="submitting" @click="handleReview">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import request from '@/utils/request'

// --- 列表相关 ---
const loading = ref(false)
const reports = ref<any[]>([])
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const statusFilter = ref('')

async function loadData() {
  loading.value = true
  try {
    const res: any = await request.get('/str-reports/page', {
      params: {
        page: currentPage.value,
        size: pageSize.value,
        status: statusFilter.value || undefined
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

// --- 状态映射 ---
const STATUS_MAP: Record<string, string> = {
  DRAFT: '草稿',
  PENDING_REVIEW: '待审核',
  APPROVED: '已审核',
  SUBMITTED: '已报送'
}

function statusLabel(status: string) {
  return STATUS_MAP[status] || status
}

function statusTagType(status: string) {
  const map: Record<string, string> = {
    DRAFT: 'info',
    PENDING_REVIEW: 'warning',
    APPROVED: '',
    SUBMITTED: 'success'
  }
  return (map[status] || 'info') as any
}

// --- 创建报告 ---
const createDialogVisible = ref(false)
const submitting = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive({ caseId: '', reportType: '', reportContent: '' })
const createRules: FormRules = {
  caseId: [{ required: true, message: '请输入案件ID', trigger: 'blur' }],
  reportType: [{ required: true, message: '请选择报告类型', trigger: 'change' }],
  reportContent: [{ required: true, message: '请输入报告内容', trigger: 'blur' }]
}

function openCreateDialog() {
  createForm.caseId = ''
  createForm.reportType = ''
  createForm.reportContent = ''
  createDialogVisible.value = true
}

async function handleCreate() {
  try {
    await createFormRef.value?.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    await request.post('/str-reports', {
      caseId: createForm.caseId,
      reportType: createForm.reportType,
      reportContent: createForm.reportContent
    })
    ElMessage.success('报告创建成功')
    createDialogVisible.value = false
    loadData()
  } catch {
    // handled
  } finally {
    submitting.value = false
  }
}

// --- 详情 ---
const detailDialogVisible = ref(false)
const detailData = ref<any>(null)

async function openDetail(row: any) {
  try {
    const res: any = await request.get(`/str-reports/${row.id}`)
    detailData.value = res.data || res
    detailDialogVisible.value = true
  } catch {
    // handled
  }
}

// --- 提交审核 ---
async function submitReview(row: any) {
  try {
    await ElMessageBox.confirm('确认将该报告提交审核？', '提示', { type: 'info' })
    await request.post(`/str-reports/${row.id}/submit-review`)
    ElMessage.success('已提交审核')
    loadData()
  } catch {
    // cancelled or error
  }
}

// --- 审核 ---
const reviewDialogVisible = ref(false)
const reviewTargetId = ref('')
const reviewForm = reactive({ approved: true, remark: '' })

function openReviewDialog(row: any) {
  reviewTargetId.value = row.id
  reviewForm.approved = true
  reviewForm.remark = ''
  reviewDialogVisible.value = true
}

async function handleReview() {
  submitting.value = true
  try {
    await request.post(`/str-reports/${reviewTargetId.value}/review`, {
      approved: reviewForm.approved,
      remark: reviewForm.remark
    })
    ElMessage.success(reviewForm.approved ? '审核通过' : '已驳回')
    reviewDialogVisible.value = false
    loadData()
  } catch {
    // handled
  } finally {
    submitting.value = false
  }
}

// --- 报送监管 ---
async function submitRegulator(row: any) {
  try {
    await ElMessageBox.confirm('确认将该报告报送至监管机构？', '提示', { type: 'warning' })
    await request.post(`/str-reports/${row.id}/submit-regulator`)
    ElMessage.success('已报送监管')
    loadData()
  } catch {
    // cancelled or error
  }
}

onMounted(loadData)
</script>
