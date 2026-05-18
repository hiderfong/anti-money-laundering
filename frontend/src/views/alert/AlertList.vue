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
    <el-dialog v-model="detailVisible" title="预警详情" width="880px" destroy-on-close>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="预警编号">{{ detailData.alertNo }}</el-descriptions-item>
        <el-descriptions-item label="客户名称">{{ detailData.customerName }}</el-descriptions-item>
        <el-descriptions-item label="预警类型">
          <el-tag size="small">{{ mapLabel(typeMap, detailData.alertType) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="风险等级">
          <el-tag :type="riskLevelTagType(detailData.riskLevel)" size="small">
            {{ mapLabel(riskLevelMap, detailData.riskLevel) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="风险分数">
          <span :style="{ color: alertRiskScore(detailData) >= 80 ? '#f56c6c' : '#606266', fontWeight: 'bold' }">
            {{ alertRiskScore(detailData) }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(detailData.status)" size="small">
            {{ mapLabel(statusMap, detailData.status) }}
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
      <section class="alert-chain-panel" v-loading="chainLoading">
        <div class="chain-header">
          <div>
            <div class="chain-title">预警处置链路图</div>
            <div class="chain-subtitle">从真实交易或规则命中到人工处置、案件升级与监管报送</div>
          </div>
          <el-tag :type="riskLevelTagType(detailData.riskLevel)" size="small">
            {{ mapLabel(riskLevelMap, detailData.riskLevel) }}
          </el-tag>
        </div>
        <div class="alert-chain">
          <div
            v-for="(node, index) in alertChainNodes(detailData)"
            :key="node.key"
            class="chain-step-wrap"
          >
            <div :class="['chain-step', `chain-step-${node.state}`]">
              <div class="chain-step-dot">{{ index + 1 }}</div>
              <div class="chain-step-body">
                <div class="chain-step-title">{{ node.title }}</div>
                <div class="chain-step-subtitle">{{ node.subtitle }}</div>
                <div v-if="node.meta" class="chain-step-meta">{{ node.meta }}</div>
              </div>
            </div>
            <div v-if="index < alertChainNodes(detailData).length - 1" class="chain-arrow"></div>
          </div>
        </div>
        <div v-if="chainEvidence.length" class="chain-evidence-list">
          <div v-for="item in chainEvidence" :key="item.key" class="chain-evidence-card">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
            <em>{{ item.note }}</em>
          </div>
        </div>
      </section>
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
/**
 * 预警列表视图
 * 展示反洗钱系统生成的各类预警，支持筛选、指派、处理、批量操作
 * 核心流程：预警生成 → 分派 → 人工复核 → 确认可疑/排除误报 → 案件升级
 */
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Bell, WarningFilled, Loading, CircleCheckFilled } from '@element-plus/icons-vue'
import request from '@/utils/request'

// ===================== 类型定义 =====================
/** 预警数据项 */
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
  sourceRuleCodes?: string
  relatedTransactionIds?: string
  ruleDetails?: any[]
}

/** 预警统计卡片数据 */
interface StatisticsData {
  total: number
  highRisk: number
  processing: number
  completed: number
}

/** 用户选项 */
interface UserOption {
  id: string | number
  name: string
}

/** 处置链路步骤 */
interface ChainStep {
  key?: string
  title?: string
  subtitle?: string
  meta?: string
  time?: string
  state?: string
}

/** 预警处置链路完整数据 */
interface DispositionChain {
  summary?: {
    transactionCount?: number
    caseCount?: number
    strReportCount?: number
    submittedToRegulator?: boolean
  }
  transactions?: Array<{ transactionNo?: string; transactionType?: string; amount?: number; currency?: string }>
  cases?: Array<{ caseNo?: string; caseStatus?: string }>
  strReports?: Array<{ reportNo?: string; reportStatus?: string; submitResult?: string }>
  steps?: ChainStep[]
}

// ===================== 常量映射 =====================
/** 预警类型中文映射 */
const typeMap: Record<string, string> = {
  LARGE_TXN: '大额交易',
  SUSPICIOUS: '可疑交易',
  SANCTIONS_HIT: '制裁命中',
  PEP_HIT: 'PEP命中',
  MANUAL: '人工创建'
}

/** 风险等级中文映射 */
const riskLevelMap: Record<string, string> = {
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
  CRITICAL: '极高'
}

/** 预警状态中文映射 */
const statusMap: Record<string, string> = {
  NEW: '新建',
  ASSIGNED: '已分配',
  PROCESSING: '处理中',
  CONFIRMED: '已确认',
  EXCLUDED: '已排除',
  ESCALATED: '已升级'
}

/** 处理结果中文映射 */
const processResultMap: Record<string, string> = {
  CONFIRMED: '确认可疑',
  CONFIRMED_SUSPICIOUS: '确认可疑',
  EXCLUDED: '排除误报',
  ESCALATED: '升级处理'
}

/** 案件状态中文映射 */
const caseStatusMap: Record<string, string> = {
  DRAFT: '草稿',
  INVESTIGATING: '调查中',
  PENDING_APPROVAL: '待审批',
  SUBMITTED: '已报送',
  CLOSED: '已结案'
}

/** 报告状态中文映射 */
const reportStatusMap: Record<string, string> = {
  DRAFT: '草稿',
  PENDING_REVIEW: '待审核',
  APPROVED: '已审核',
  REJECTED: '已拒绝',
  SUBMITTED: '已报送'
}

/** 报送结果中文映射 */
const submitResultMap: Record<string, string> = {
  SUBMIT_SUCCESS: '报送成功',
  SUCCESS: '成功',
  E2E_MOCK_ACCEPTED: '模拟报送成功'
}

// ===================== 响应式状态 =====================
/** 列表加载状态 */
const loading = ref(false)
/** 表单提交中状态 */
const submitting = ref(false)
/** 预警数据列表 */
const alertList = ref<AlertItem[]>([])
/** 分页总条数 */
const total = ref(0)
/** 表格选中行的ID列表 */
const selectedIds = ref<number[]>([])
/** 统计卡片数据 */
const statistics = ref<StatisticsData>({ total: 0, highRisk: 0, processing: 0, completed: 0 })
const route = useRoute()

/** 查询条件 */
const query = reactive({
  page: 1,
  size: 10,
  alertType: '',
  riskLevel: '',
  status: '',
  assignedTo: ''
})

// ===================== 详情弹窗状态 =====================
/** 详情弹窗显示状态 */
const detailVisible = ref(false)
/** 当前查看的预警详情 */
const detailData = ref<Partial<AlertItem>>({})
/** 处置链路加载状态 */
const chainLoading = ref(false)
/** 处置链路数据 */
const dispositionChain = ref<DispositionChain | null>(null)
/** 处置链路证据卡片数据 */
const chainEvidence = computed(() => {
  const chain = dispositionChain.value
  if (!chain) return []
  const transactions = chain.transactions || []
  const cases = chain.cases || []
  const reports = chain.strReports || []
  return [
    {
      key: 'transactions',
      label: '关联交易',
      value: transactions.length ? joinText(transactions.map(item => item.transactionNo), '、', 3) : '未关联',
      note: transactions.length ? `${transactions.length} 笔交易进入本次预警链路` : '预警未记录真实交易ID'
    },
    {
      key: 'cases',
      label: '关联案件',
      value: cases.length ? joinText(cases.map(item => item.caseNo), '、', 3) : '未生成',
      note: cases.length ? joinText(cases.map(item => chainStatusLabel(item.caseStatus)), '、', 3) : '当前预警尚未形成案件'
    },
    {
      key: 'strReports',
      label: 'STR报告',
      value: reports.length ? joinText(reports.map(item => item.reportNo), '、', 3) : '未生成',
      note: reports.length
        ? joinText(reports.map(item => [chainStatusLabel(item.reportStatus), chainStatusLabel(item.submitResult)].filter(Boolean).join('/')), '、', 3)
        : '当前预警尚未进入可疑交易报告'
    }
  ]
})

// ===================== 指派弹窗状态 =====================
/** 指派弹窗显示状态 */
const assignVisible = ref(false)
/** 指派表单数据 */
const assignForm = reactive({ alertId: 0, alertNo: '', assignTo: '' })
/** 可选处理人列表 */
const userList = ref<UserOption[]>([
  { id: 'analyst1', name: '分析师A' },
  { id: 'analyst2', name: '分析师B' },
  { id: 'analyst3', name: '分析师C' },
  { id: 'manager1', name: '主管A' }
])

// ===================== 处理弹窗状态 =====================
/** 处理弹窗显示状态 */
const processVisible = ref(false)
/** 处理表单数据 */
const processForm = reactive({ alertId: 0, alertNo: '', processResult: 'CONFIRMED', processRemark: '' })

// ===================== 批量处理弹窗状态 =====================
/** 批量处理弹窗显示状态 */
const batchProcessVisible = ref(false)
/** 批量处理表单数据 */
const batchForm = reactive({ processResult: 'CONFIRMED', processRemark: '' })

// ===================== 辅助函数 =====================
/** 从映射表中获取中文标签 */
function mapLabel(map: Record<string, string>, value: unknown) {
  const key = typeof value === 'string' ? value : ''
  return map[key] || key || '-'
}

/** 获取预警风险分数 */
function alertRiskScore(alert: Partial<AlertItem>) {
  return alert.riskScore ?? 0
}

/** 从对象中安全获取数值 */
function countFrom(map: Record<string, unknown> | undefined, key: string) {
  return Number(map?.[key] ?? 0)
}

/** 根据风险等级返回标签样式类型 */
function riskLevelTagType(level: unknown): '' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<string, '' | 'success' | 'warning' | 'danger' | 'info'> = {
    LOW: 'success',
    MEDIUM: 'warning',
    HIGH: 'danger',
    CRITICAL: 'danger'
  }
  const key = typeof level === 'string' ? level : ''
  return map[key] || 'info'
}

/** 根据状态返回标签样式类型 */
function statusTagType(status: unknown): '' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<string, '' | 'success' | 'warning' | 'danger' | 'info'> = {
    NEW: 'danger',
    ASSIGNED: 'warning',
    PROCESSING: '',
    CONFIRMED: 'success',
    EXCLUDED: 'info'
  }
  const key = typeof status === 'string' ? status : ''
  return map[key] || 'info'
}

/** 将逗号/空格/分号分隔的ID字符串拆分为数组 */
function splitIds(value: unknown) {
  return String(value || '')
    .split(/[,\s;]+/)
    .map(item => item.trim())
    .filter(Boolean)
}

/** 将文本数组拼接为字符串，超出limit显示"等N项" */
function joinText(values: unknown[], separator = '、', limit = 3) {
  const filtered = values.map(item => String(item || '').trim()).filter(Boolean)
  if (!filtered.length) return '-'
  const text = filtered.slice(0, limit).join(separator)
  return filtered.length > limit ? `${text} 等${filtered.length}项` : text
}

/** 统一的状态码中文解析（按优先级查找各映射表） */
function chainStatusLabel(value: unknown) {
  const key = String(value || '').trim()
  return processResultMap[key] || statusMap[key] || caseStatusMap[key] || reportStatusMap[key] || submitResultMap[key] || key
}

/**
 * 构建预警处置链路节点
 * 优先使用后端返回的steps，否则基于预警状态本地生成
 */
function alertChainNodes(alert: Partial<AlertItem> & Record<string, any>) {
  const serverSteps = dispositionChain.value?.steps || []
  if (serverSteps.length) {
    return serverSteps.map((step, index) => ({
      key: step.key || `step-${index}`,
      title: step.title || '-',
      subtitle: chainStatusLabel(step.subtitle) || '-',
      meta: [step.meta, step.time].filter(Boolean).join(' · '),
      state: step.state || 'done'
    }))
  }

  const transactionIds = splitIds(alert.relatedTransactionIds)
  const ruleCodes = splitIds(alert.sourceRuleCodes)
  const ruleDetailCount = Array.isArray(alert.ruleDetails) ? alert.ruleDetails.length : 0
  const isFinal = ['CONFIRMED', 'EXCLUDED'].includes(String(alert.status || ''))
  const confirmed = String(alert.processResult || '').includes('CONFIRMED') || alert.status === 'CONFIRMED'
  const excluded = String(alert.processResult || '').includes('EXCLUDED') || alert.status === 'EXCLUDED'
  return [
    {
      key: 'source',
      title: transactionIds.length ? '关联交易命中' : '规则/名单命中',
      subtitle: transactionIds.length ? `${transactionIds.length} 笔交易触发预警` : '由筛查、规则或人工线索触发',
      meta: ruleCodes.length ? `规则：${ruleCodes.join('、')}` : ruleDetailCount ? `命中规则明细 ${ruleDetailCount} 条` : '规则明细待补充',
      state: 'done'
    },
    {
      key: 'alert',
      title: '预警生成',
      subtitle: alert.alertNo || '-',
      meta: `${mapLabel(typeMap, alert.alertType)} / 风险分 ${alert.riskScore ?? 0}`,
      state: alert.riskLevel === 'CRITICAL' || alert.riskLevel === 'HIGH' ? 'warn' : 'done'
    },
    {
      key: 'assign',
      title: '分派处理',
      subtitle: alert.assignedTo ? `处理人：${alert.assignedTo}` : '尚未指派处理人',
      meta: alert.assignedTime || '等待分派',
      state: alert.assignedTo ? 'done' : 'current'
    },
    {
      key: 'review',
      title: '人工复核',
      subtitle: alert.processResult ? mapLabel(processResultMap, alert.processResult) : mapLabel(statusMap, alert.status),
      meta: alert.processTime || alert.processRemark || '等待处理结论',
      state: excluded ? 'muted' : isFinal ? 'done' : 'current'
    },
    {
      key: 'case',
      title: confirmed ? '案件/STR准备' : '处置闭环',
      subtitle: confirmed ? '可升级案件并形成可疑交易报告' : excluded ? '误报排除，保留复核留痕' : '等待处置结果进入后续链路',
      meta: confirmed ? '建议核查案件与 STR 生成状态' : '',
      state: confirmed ? 'warn' : excluded ? 'muted' : 'pending'
    }
  ]
}

// ===================== 数据加载 =====================
/** 加载预警分页列表 */
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

/** 加载预警统计数据 */
async function loadStatistics() {
  try {
    const res: any = await request.get('/alerts/statistics')
    if (res.data) {
      const status = res.data.countByStatus || {}
      const riskLevel = res.data.countByRiskLevel || {}
      statistics.value = {
        total: Number(res.data.total ?? res.data.totalCount ?? 0),
        highRisk: Number(res.data.highRisk ?? (countFrom(riskLevel, 'HIGH') + countFrom(riskLevel, 'CRITICAL'))),
        processing: Number(res.data.processing ?? (
          countFrom(status, 'ASSIGNED') + countFrom(status, 'PROCESSING') + countFrom(status, 'ESCALATED')
        )),
        completed: Number(res.data.completed ?? (countFrom(status, 'CONFIRMED') + countFrom(status, 'EXCLUDED')))
      }
    }
  } catch (e) {
    console.error('加载统计数据失败', e)
  }
}

// ===================== 搜索/重置 =====================
/** 执行搜索 */
function handleSearch() {
  query.page = 1
  loadData()
}

/** 重置查询条件 */
function handleReset() {
  query.alertType = ''
  query.riskLevel = ''
  query.status = ''
  query.assignedTo = ''
  query.page = 1
  loadData()
}

/** 分页大小变化 */
function handleSizeChange() {
  query.page = 1
  loadData()
}

// ===================== 表格选择 =====================
/** 表格多选变化事件 */
function handleSelectionChange(rows: AlertItem[]) {
  selectedIds.value = rows.map(r => r.id)
}

// ===================== 详情弹窗 =====================
/** 打开预警详情弹窗 */
async function openDetail(row: AlertItem) {
  detailVisible.value = true
  detailData.value = row
  dispositionChain.value = null
  chainLoading.value = true
  try {
    const [detailResult, chainResult] = await Promise.allSettled([
      request.get(`/alerts/${row.id}`),
      request.get(`/alerts/${row.id}/disposition-chain`)
    ])
    if (detailResult.status === 'fulfilled') {
      const res: any = detailResult.value
      detailData.value = res.data || row
    }
    if (chainResult.status === 'fulfilled') {
      const res: any = chainResult.value
      dispositionChain.value = res.data || null
    }
  } catch (e) {
    console.error('加载预警详情失败', e)
  } finally {
    chainLoading.value = false
  }
}

/** 从路由参数打开详情（用于其他页面跳转） */
async function openDetailFromRoute() {
  const alertId = route.query.alertId
  const id = Array.isArray(alertId) ? alertId[0] : alertId
  const numericId = Number(id)
  if (!id || Number.isNaN(numericId)) return
  await openDetail({
    id: numericId,
    alertNo: String(route.query.alertNo || ''),
    customerId: 0,
    customerName: '',
    alertType: '',
    riskScore: 0,
    riskLevel: '',
    alertSummary: '',
    status: '',
    assignedTo: '',
    assignedTime: '',
    processResult: '',
    processRemark: '',
    processTime: '',
    createdTime: ''
  })
}

// ===================== 指派弹窗 =====================
/** 打开指派弹窗 */
function openAssignDialog(row: AlertItem) {
  assignForm.alertId = row.id
  assignForm.alertNo = row.alertNo
  assignForm.assignTo = ''
  assignVisible.value = true
}

/** 提交指派 */
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
/** 打开处理弹窗 */
function openProcessDialog(row: AlertItem) {
  processForm.alertId = row.id
  processForm.alertNo = row.alertNo
  processForm.processResult = 'CONFIRMED'
  processForm.processRemark = ''
  processVisible.value = true
}

/** 提交处理结果 */
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
/** 打开批量处理弹窗 */
function openBatchProcessDialog() {
  batchForm.processResult = 'CONFIRMED'
  batchForm.processRemark = ''
  batchProcessVisible.value = true
}

/** 提交批量处理 */
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

// ===================== 生命周期钩子 =====================
/** 组件挂载：加载列表、统计和路由详情 */
onMounted(async () => {
  await loadData()
  await openDetailFromRoute()
  loadStatistics()
})

/** 监听路由alertId变化，自动打开对应详情 */
watch(
  () => route.query.alertId,
  () => {
    openDetailFromRoute()
  }
)
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

.alert-chain-panel {
  margin-top: 16px;
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fbfdff;
}

.chain-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.chain-title {
  color: #111827;
  font-size: 15px;
  font-weight: 700;
}

.chain-subtitle {
  margin-top: 3px;
  color: #64748b;
  font-size: 12px;
}

.alert-chain {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(132px, 1fr));
  gap: 10px;
  align-items: stretch;
}

.chain-step-wrap {
  display: flex;
  align-items: center;
  min-width: 0;
}

.chain-step {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  width: 100%;
  min-height: 104px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
}

.chain-step-dot {
  flex: 0 0 28px;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #2563eb;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  line-height: 28px;
  text-align: center;
}

.chain-step-warn .chain-step-dot {
  background: #dc2626;
}

.chain-step-current .chain-step-dot {
  background: #d97706;
}

.chain-step-pending .chain-step-dot,
.chain-step-muted .chain-step-dot {
  background: #94a3b8;
}

.chain-step-body {
  min-width: 0;
}

.chain-step-title {
  color: #111827;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.35;
}

.chain-step-subtitle,
.chain-step-meta {
  margin-top: 5px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.45;
  word-break: break-word;
}

.chain-evidence-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 12px;
}

.chain-evidence-card {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
  padding: 10px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
}

.chain-evidence-card span {
  color: #64748b;
  font-size: 12px;
}

.chain-evidence-card strong {
  overflow: hidden;
  color: #111827;
  font-size: 13px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chain-evidence-card em {
  color: #64748b;
  font-size: 12px;
  font-style: normal;
  line-height: 1.4;
  word-break: break-word;
}

.chain-arrow {
  flex: 0 0 18px;
  height: 2px;
  margin: 0 -2px;
  background: #cbd5e1;
  position: relative;
}

.chain-arrow::after {
  content: '';
  position: absolute;
  right: -1px;
  top: -4px;
  border-left: 7px solid #cbd5e1;
  border-top: 5px solid transparent;
  border-bottom: 5px solid transparent;
}

@media (max-width: 1200px) {
  .alert-chain {
    grid-template-columns: 1fr;
  }

  .chain-evidence-list {
    grid-template-columns: 1fr;
  }

  .chain-step-wrap {
    display: block;
  }

  .chain-arrow {
    width: 2px;
    height: 18px;
    margin: 0 0 0 25px;
  }

  .chain-arrow::after {
    right: -4px;
    top: 12px;
    border-left: 5px solid transparent;
    border-right: 5px solid transparent;
    border-top: 7px solid #cbd5e1;
    border-bottom: 0;
  }
}
</style>
