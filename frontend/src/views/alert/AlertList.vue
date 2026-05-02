<template>
  <div class="alert-container">
    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-total">
          <div class="stat-content">
            <div class="stat-info">
              <div class="stat-label">预警总数</div>
              <div class="stat-value">{{ statistics.total ?? 0 }}</div>
            </div>
            <el-icon class="stat-icon"><Bell /></el-icon>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-high">
          <div class="stat-content">
            <div class="stat-info">
              <div class="stat-label">高危预警</div>
              <div class="stat-value">{{ statistics.highRisk ?? 0 }}</div>
            </div>
            <el-icon class="stat-icon"><WarningFilled /></el-icon>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-processing">
          <div class="stat-content">
            <div class="stat-info">
              <div class="stat-label">处理中</div>
              <div class="stat-value">{{ statistics.processing ?? 0 }}</div>
            </div>
            <el-icon class="stat-icon"><Loading /></el-icon>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-done">
          <div class="stat-content">
            <div class="stat-info">
              <div class="stat-label">已完成</div>
              <div class="stat-value">{{ statistics.completed ?? 0 }}</div>
            </div>
            <el-icon class="stat-icon"><CircleCheckFilled /></el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 搜索筛选栏 -->
    <el-card class="filter-card">
      <el-form :inline="true" :model="query" class="filter-form">
        <el-form-item label="预警类型">
          <el-select v-model="query.alertType" clearable placeholder="全部类型" style="width: 160px">
            <el-option label="大额交易" value="LARGE_TXN" />
            <el-option label="可疑交易" value="SUSPICIOUS" />
            <el-option label="制裁命中" value="SANCTIONS_HIT" />
            <el-option label="PEP命中" value="PEP_HIT" />
            <el-option label="人工创建" value="MANUAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="风险等级">
          <el-select v-model="query.riskLevel" clearable placeholder="全部等级" style="width: 140px">
            <el-option label="低" value="LOW" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="高" value="HIGH" />
            <el-option label="极高" value="CRITICAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部状态" style="width: 140px">
            <el-option label="新建" value="NEW" />
            <el-option label="已分配" value="ASSIGNED" />
            <el-option label="处理中" value="PROCESSING" />
            <el-option label="已确认" value="CONFIRMED" />
            <el-option label="已排除" value="EXCLUDED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card class="table-card">
      <template #header>
        <div class="table-header">
          <span class="table-title">预警列表</span>
          <el-button type="warning" :disabled="selectedIds.length === 0" @click="openBatchProcessDialog">
            批量处理 ({{ selectedIds.length }})
          </el-button>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="alertList"
        stripe
        border
        @selection-change="handleSelectionChange"
        row-key="id"
      >
        <el-table-column type="selection" width="50" align="center" />
        <el-table-column prop="alertNo" label="预警编号" width="170" show-overflow-tooltip />
        <el-table-column prop="customerName" label="客户名称" width="120" show-overflow-tooltip />
        <el-table-column prop="alertType" label="预警类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ typeMap[row.alertType] || row.alertType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="riskLevel" label="风险等级" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="riskLevelTagType(row.riskLevel)" size="small">
              {{ riskLevelMap[row.riskLevel] || row.riskLevel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="riskScore" label="风险分" width="80" align="center">
          <template #default="{ row }">
            <span :style="{ color: row.riskScore >= 80 ? '#f56c6c' : row.riskScore >= 50 ? '#e6a23c' : '#67c23a', fontWeight: 'bold' }">
              {{ row.riskScore }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="alertSummary" label="预警摘要" min-width="180" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusMap[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="assignedTo" label="处理人" width="100" show-overflow-tooltip />
        <el-table-column prop="createdTime" label="生成时间" width="170" />
        <el-table-column label="操作" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">查看</el-button>
            <el-button link type="warning" size="small" :disabled="row.status === 'CONFIRMED' || row.status === 'EXCLUDED'" @click="openAssignDialog(row)">指派</el-button>
            <el-button link type="success" size="small" :disabled="row.status === 'CONFIRMED' || row.status === 'EXCLUDED'" @click="openProcessDialog(row)">处理</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @current-change="loadData"
        @size-change="handleSizeChange"
      />
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="预警详情" width="700px" destroy-on-close>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="预警编号">{{ detailData.alertNo }}</el-descriptions-item>
        <el-descriptions-item label="客户名称">{{ detailData.customerName }}</el-descriptions-item>
        <el-descriptions-item label="预警类型">
          <el-tag size="small">{{ typeMap[detailData.alertType] || detailData.alertType }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="风险等级">
          <el-tag :type="riskLevelTagType(detailData.riskLevel)" size="small">
            {{ riskLevelMap[detailData.riskLevel] || detailData.riskLevel }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="风险分数">
          <span :style="{ color: detailData.riskScore >= 80 ? '#f56c6c' : '#606266', fontWeight: 'bold' }">
            {{ detailData.riskScore }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(detailData.status)" size="small">
            {{ statusMap[detailData.status] || detailData.status }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="预警摘要" :span="2">{{ detailData.alertSummary || '-' }}</el-descriptions-item>
        <el-descriptions-item label="处理人">{{ detailData.assignedTo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="指派时间">{{ detailData.assignedTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="处理结果" :span="2">
          <el-tag v-if="detailData.processResult" :type="detailData.processResult === 'CONFIRMED' ? 'danger' : 'success'" size="small">
            {{ processResultMap[detailData.processResult] || detailData.processResult }}
          </el-tag>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="处理备注" :span="2">{{ detailData.processRemark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="处理时间">{{ detailData.processTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="生成时间">{{ detailData.createdTime || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 指派弹窗 -->
    <el-dialog v-model="assignVisible" title="指派预警" width="500px" destroy-on-close>
      <el-form :model="assignForm" label-width="100px">
        <el-form-item label="预警编号">
          <el-input :model-value="assignForm.alertNo" disabled />
        </el-form-item>
        <el-form-item label="指派给" required>
          <el-select v-model="assignForm.assignTo" placeholder="请选择处理人" style="width: 100%">
            <el-option v-for="user in userList" :key="user.id" :label="user.name" :value="user.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleAssign">确认指派</el-button>
      </template>
    </el-dialog>

    <!-- 处理弹窗 -->
    <el-dialog v-model="processVisible" title="处理预警" width="500px" destroy-on-close>
      <el-form :model="processForm" label-width="100px">
        <el-form-item label="预警编号">
          <el-input :model-value="processForm.alertNo" disabled />
        </el-form-item>
        <el-form-item label="处理结果" required>
          <el-radio-group v-model="processForm.processResult">
            <el-radio value="CONFIRMED">确认可疑</el-radio>
            <el-radio value="EXCLUDED">排除误报</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理备注">
          <el-input v-model="processForm.processRemark" type="textarea" :rows="4" placeholder="请输入处理备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="processVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleProcess">确认处理</el-button>
      </template>
    </el-dialog>

    <!-- 批量处理弹窗 -->
    <el-dialog v-model="batchProcessVisible" title="批量处理预警" width="500px" destroy-on-close>
      <el-alert :title="`已选择 ${selectedIds.length} 条预警`" type="info" :closable="false" show-icon style="margin-bottom: 16px" />
      <el-form :model="batchForm" label-width="100px">
        <el-form-item label="处理结果" required>
          <el-radio-group v-model="batchForm.processResult">
            <el-radio value="CONFIRMED">确认可疑</el-radio>
            <el-radio value="EXCLUDED">排除误报</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理备注">
          <el-input v-model="batchForm.processRemark" type="textarea" :rows="4" placeholder="请输入处理备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchProcessVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleBatchProcess">确认处理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Bell, WarningFilled, Loading, CircleCheckFilled } from '@element-plus/icons-vue'
import request from '@/utils/request'

// ===================== 类型定义 =====================
interface AlertItem {
  id: number
  alertNo: string
  customerId: number
  customerName: string
  alertType: string
  riskScore: number
  riskLevel: string
  alertSummary: string
  status: string
  assignedTo: string
  assignedTime: string
  processResult: string
  processRemark: string
  processTime: string
  createdTime: string
}

interface StatisticsData {
  total: number
  highRisk: number
  processing: number
  completed: number
}

interface UserOption {
  id: string | number
  name: string
}

// ===================== 常量映射 =====================
const typeMap: Record<string, string> = {
  LARGE_TXN: '大额交易',
  SUSPICIOUS: '可疑交易',
  SANCTIONS_HIT: '制裁命中',
  PEP_HIT: 'PEP命中',
  MANUAL: '人工创建'
}

const riskLevelMap: Record<string, string> = {
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
  CRITICAL: '极高'
}

const statusMap: Record<string, string> = {
  NEW: '新建',
  ASSIGNED: '已分配',
  PROCESSING: '处理中',
  CONFIRMED: '已确认',
  EXCLUDED: '已排除'
}

const processResultMap: Record<string, string> = {
  CONFIRMED: '确认可疑',
  EXCLUDED: '排除误报'
}

// ===================== 状态 =====================
const loading = ref(false)
const submitting = ref(false)
const alertList = ref<AlertItem[]>([])
const total = ref(0)
const selectedIds = ref<number[]>([])
const statistics = ref<StatisticsData>({ total: 0, highRisk: 0, processing: 0, completed: 0 })

const query = reactive({
  page: 1,
  size: 10,
  alertType: '',
  riskLevel: '',
  status: '',
  assignedTo: ''
})

// 详情弹窗
const detailVisible = ref(false)
const detailData = ref<Partial<AlertItem>>({})

// 指派弹窗
const assignVisible = ref(false)
const assignForm = reactive({ alertId: 0, alertNo: '', assignTo: '' })
const userList = ref<UserOption[]>([
  { id: 'analyst1', name: '分析师A' },
  { id: 'analyst2', name: '分析师B' },
  { id: 'analyst3', name: '分析师C' },
  { id: 'manager1', name: '主管A' }
])

// 处理弹窗
const processVisible = ref(false)
const processForm = reactive({ alertId: 0, alertNo: '', processResult: 'CONFIRMED', processRemark: '' })

// 批量处理弹窗
const batchProcessVisible = ref(false)
const batchForm = reactive({ processResult: 'CONFIRMED', processRemark: '' })

// ===================== Tag类型辅助 =====================
function riskLevelTagType(level: string): '' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<string, '' | 'success' | 'warning' | 'danger' | 'info'> = {
    LOW: 'success',
    MEDIUM: 'warning',
    HIGH: 'danger',
    CRITICAL: 'danger'
  }
  return map[level] || 'info'
}

function statusTagType(status: string): '' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<string, '' | 'success' | 'warning' | 'danger' | 'info'> = {
    NEW: 'danger',
    ASSIGNED: 'warning',
    PROCESSING: '',
    CONFIRMED: 'success',
    EXCLUDED: 'info'
  }
  return map[status] || 'info'
}

// ===================== 数据加载 =====================
async function loadData() {
  loading.value = true
  try {
    const params: Record<string, any> = { page: query.page, size: query.size }
    if (query.alertType) params.alertType = query.alertType
    if (query.riskLevel) params.riskLevel = query.riskLevel
    if (query.status) params.status = query.status
    if (query.assignedTo) params.assignedTo = query.assignedTo

    const res: any = await request.get('/alerts/page', { params })
    alertList.value = res.data?.list || []
    total.value = res.data?.total || 0
  } catch (e) {
    console.error('加载预警列表失败', e)
  } finally {
    loading.value = false
  }
}

async function loadStatistics() {
  try {
    const res: any = await request.get('/alerts/statistics')
    if (res.data) {
      statistics.value = {
        total: res.data.total ?? 0,
        highRisk: res.data.highRisk ?? 0,
        processing: res.data.processing ?? 0,
        completed: res.data.completed ?? 0
      }
    }
  } catch (e) {
    console.error('加载统计数据失败', e)
  }
}

// ===================== 搜索/重置 =====================
function handleSearch() {
  query.page = 1
  loadData()
}

function handleReset() {
  query.alertType = ''
  query.riskLevel = ''
  query.status = ''
  query.assignedTo = ''
  query.page = 1
  loadData()
}

function handleSizeChange() {
  query.page = 1
  loadData()
}

// ===================== 表格选择 =====================
function handleSelectionChange(rows: AlertItem[]) {
  selectedIds.value = rows.map(r => r.id)
}

// ===================== 详情弹窗 =====================
async function openDetail(row: AlertItem) {
  try {
    const res: any = await request.get(`/alerts/${row.id}`)
    detailData.value = res.data || row
  } catch {
    detailData.value = row
  }
  detailVisible.value = true
}

// ===================== 指派弹窗 =====================
function openAssignDialog(row: AlertItem) {
  assignForm.alertId = row.id
  assignForm.alertNo = row.alertNo
  assignForm.assignTo = ''
  assignVisible.value = true
}

async function handleAssign() {
  if (!assignForm.assignTo) {
    ElMessage.warning('请选择处理人')
    return
  }
  submitting.value = true
  try {
    await request.post('/alerts/assign', { alertId: assignForm.alertId, assignTo: assignForm.assignTo })
    ElMessage.success('指派成功')
    assignVisible.value = false
    loadData()
    loadStatistics()
  } catch (e) {
    ElMessage.error('指派失败')
  } finally {
    submitting.value = false
  }
}

// ===================== 处理弹窗 =====================
function openProcessDialog(row: AlertItem) {
  processForm.alertId = row.id
  processForm.alertNo = row.alertNo
  processForm.processResult = 'CONFIRMED'
  processForm.processRemark = ''
  processVisible.value = true
}

async function handleProcess() {
  if (!processForm.processResult) {
    ElMessage.warning('请选择处理结果')
    return
  }
  submitting.value = true
  try {
    await request.post('/alerts/process', {
      alertId: processForm.alertId,
      processResult: processForm.processResult,
      processRemark: processForm.processRemark
    })
    ElMessage.success('处理成功')
    processVisible.value = false
    loadData()
    loadStatistics()
  } catch (e) {
    ElMessage.error('处理失败')
  } finally {
    submitting.value = false
  }
}

// ===================== 批量处理 =====================
function openBatchProcessDialog() {
  batchForm.processResult = 'CONFIRMED'
  batchForm.processRemark = ''
  batchProcessVisible.value = true
}

async function handleBatchProcess() {
  if (!batchForm.processResult) {
    ElMessage.warning('请选择处理结果')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定要批量处理选中的 ${selectedIds.value.length} 条预警吗？`,
      '批量处理确认',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  submitting.value = true
  try {
    await request.post('/alerts/batch-process', {
      alertIds: selectedIds.value,
      processResult: batchForm.processResult,
      processRemark: batchForm.processRemark
    })
    ElMessage.success(`成功处理 ${selectedIds.value.length} 条预警`)
    batchProcessVisible.value = false
    loadData()
    loadStatistics()
  } catch (e) {
    ElMessage.error('批量处理失败')
  } finally {
    submitting.value = false
  }
}

// ===================== 初始化 =====================
onMounted(() => {
  loadData()
  loadStatistics()
})
</script>

<style scoped>
.alert-container {
  padding: 4px;
}

.stat-row {
  margin-bottom: 16px;
}

.stat-card {
  border-radius: 8px;
}

.stat-card :deep(.el-card__body) {
  padding: 20px;
}

.stat-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
}

.stat-icon {
  font-size: 40px;
  opacity: 0.25;
}

.stat-total .stat-value { color: #409eff; }
.stat-total .stat-icon { color: #409eff; }
.stat-high .stat-value { color: #f56c6c; }
.stat-high .stat-icon { color: #f56c6c; }
.stat-processing .stat-value { color: #e6a23c; }
.stat-processing .stat-icon { color: #e6a23c; }
.stat-done .stat-value { color: #67c23a; }
.stat-done .stat-icon { color: #67c23a; }

.filter-card {
  margin-bottom: 16px;
}

.filter-form {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
}

.table-card {
  margin-bottom: 16px;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.table-title {
  font-size: 16px;
  font-weight: 600;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
