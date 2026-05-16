<template>
  <div class="monitoring-container">
    <el-tabs v-model="activeTab" type="border-card">
      <!-- ========== 交易监测 ========== -->
      <el-tab-pane label="交易监测" name="transactions">
        <!-- 搜索筛选区 -->
        <el-form :inline="true" :model="txQuery" style="margin-bottom:16px">
          <el-form-item label="交易编号">
            <el-input v-model="txQuery.transactionNo" placeholder="交易编号" clearable style="width:180px" />
          </el-form-item>
          <el-form-item label="交易类型">
            <el-select v-model="txQuery.transactionType" placeholder="全部" clearable style="width:140px">
              <el-option v-for="(label, key) in typeMap" :key="key" :label="label" :value="key" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="txQuery.status" placeholder="全部" clearable style="width:120px">
              <el-option label="成功" value="SUCCESS" />
              <el-option label="失败" value="FAILED" />
              <el-option label="处理中" value="PENDING" />
            </el-select>
          </el-form-item>
          <el-form-item label="日期范围">
            <el-date-picker v-model="txQuery.dateRange" type="daterange" start-placeholder="开始日期"
              end-placeholder="结束日期" value-format="YYYY-MM-DD" style="width:260px" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="txPage = 1; loadTransactions()">查询</el-button>
            <el-button @click="resetTxQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <!-- 交易关系图谱 -->
        <section class="network-graph-panel">
          <div class="graph-toolbar">
            <div class="graph-heading">
              <div class="graph-title">交易关系图谱</div>
              <div class="graph-subtitle">围绕客户、账户、交易对手展示资金路径和异常网络关系</div>
            </div>
            <el-form :inline="true" :model="graphQuery" class="graph-form">
              <el-form-item label="客户ID">
                <el-input v-model="graphQuery.customerId" placeholder="选择或输入客户ID" clearable style="width:150px" />
              </el-form-item>
              <el-form-item label="分析场景">
                <el-select v-model="graphQuery.mode" style="width:150px">
                  <el-option
                    v-for="item in graphModeOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item v-if="graphQuery.mode === 'trace'" label="追踪层级">
                <el-input-number
                  v-model="graphQuery.depth"
                  :min="1"
                  :max="6"
                  controls-position="right"
                  style="width:110px"
                />
              </el-form-item>
              <el-form-item v-if="graphQuery.mode === 'density'" label="密度阈值">
                <el-input-number
                  v-model="graphQuery.densityThreshold"
                  :min="1"
                  :max="50"
                  controls-position="right"
                  style="width:110px"
                />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="graphLoading" @click="refreshGraph()">生成图谱</el-button>
              </el-form-item>
            </el-form>
          </div>

          <el-alert
            v-if="graphNotice"
            :title="graphNotice"
            :type="graphNoticeType"
            show-icon
            :closable="false"
            class="graph-alert"
          />

          <div class="graph-summary">
            <div class="graph-stat">
              <span>节点</span>
              <strong>{{ graphStats.nodes }}</strong>
            </div>
            <div class="graph-stat">
              <span>关系</span>
              <strong>{{ graphStats.links }}</strong>
            </div>
            <div class="graph-stat">
              <span>交易笔数</span>
              <strong>{{ graphStats.transactionCount }}</strong>
            </div>
            <div class="graph-stat">
              <span>涉及金额</span>
              <strong>{{ formatCurrency(graphStats.totalAmount) }}</strong>
            </div>
            <div class="graph-stat">
              <span>风险判断</span>
              <el-tag :type="graphRiskTagType(graphStats.riskLabel)" size="small">{{ graphStats.riskLabel }}</el-tag>
            </div>
            <div class="graph-stat">
              <span>数据来源</span>
              <strong>{{ graphStats.source }}</strong>
            </div>
          </div>

          <div class="graph-content">
            <div ref="graphChartRef" v-loading="graphLoading" class="graph-chart">
              <el-empty v-if="!graphHasData && !graphLoading" description="请选择客户并生成交易关系图谱" />
            </div>
            <aside class="graph-insights">
              <div class="insight-title">图谱解读</div>
              <div v-if="graphInsights.length" class="insight-list">
                <div v-for="item in graphInsights" :key="item" class="insight-item">{{ item }}</div>
              </div>
              <el-empty v-else description="暂无图谱解读" :image-size="80" />
            </aside>
          </div>
        </section>

        <!-- 交易列表 -->
        <el-table :data="transactions" stripe v-loading="txLoading" border>
          <el-table-column prop="transactionNo" label="交易编号" width="180" />
          <el-table-column prop="customerId" label="客户ID" width="120" />
          <el-table-column prop="transactionType" label="类型" width="120">
            <template #default="{ row }">
              <el-tag size="small">{{ typeMap[row.transactionType] || row.transactionType }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="amount" label="金额" width="130" align="right">
            <template #default="{ row }">{{ Number(row.amount).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="currency" label="币种" width="80" />
          <el-table-column prop="paymentMethod" label="支付方式" width="100" />
          <el-table-column prop="status" label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="transactionTime" label="交易时间" min-width="170" />
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openTxDetail(row)">详情</el-button>
              <el-button link type="warning" size="small" @click="openGraphFromTx(row)">图谱</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-model:current-page="txPage"
          v-model:page-size="txSize"
          :total="txTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          style="margin-top:16px;justify-content:flex-end"
          @current-change="loadTransactions"
          @size-change="txPage = 1; loadTransactions()"
        />
      </el-tab-pane>

      <!-- ========== 规则管理 ========== -->
      <el-tab-pane label="规则管理" name="rules">
        <div style="margin-bottom:16px;display:flex;justify-content:space-between;align-items:center">
          <el-form :inline="true" :model="ruleQuery" style="margin-bottom:0">
            <el-form-item label="规则名称">
              <el-input v-model="ruleQuery.keyword" placeholder="编码/名称" clearable style="width:180px" />
            </el-form-item>
            <el-form-item label="规则类型">
              <el-select v-model="ruleQuery.ruleCategory" placeholder="全部" clearable style="width:140px">
                <el-option label="大额交易" value="LARGE_TXN" />
                <el-option label="可疑交易" value="SUSPICIOUS" />
                <el-option label="频率检测" value="VELOCITY" />
                <el-option label="阈值检测" value="THRESHOLD" />
                <el-option label="关联分析" value="CORRELATION" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="rulePage = 1; loadRules()">查询</el-button>
              <el-button @reset="resetRuleQuery" @click="resetRuleQuery">重置</el-button>
            </el-form-item>
          </el-form>
          <el-button type="primary" @click="openRuleDialog('create')">+ 新建规则</el-button>
        </div>

        <el-table :data="rules" stripe v-loading="ruleLoading" border>
          <el-table-column prop="ruleCode" label="规则编码" width="150" />
          <el-table-column prop="ruleName" label="规则名称" min-width="160" />
          <el-table-column prop="ruleCategory" label="规则类型" width="120">
            <template #default="{ row }">
              <el-tag size="small">{{ ruleCategoryLabel(row.ruleCategory) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="configJson" label="规则配置" min-width="220" show-overflow-tooltip />
          <el-table-column prop="riskWeight" label="风险权重" width="100" align="center" />
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-switch
                :model-value="isRuleEnabled(row)"
                active-text="启用"
                inactive-text="禁用"
                inline-prompt
                :before-change="() => toggleRule(row)"
              />
            </template>
          </el-table-column>
          <el-table-column prop="createdTime" label="创建时间" width="170" />
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openRuleDialog('edit', row)">编辑</el-button>
              <el-button link type="primary" size="small" @click="openVersionHistory(row)">版本历史</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-model:current-page="rulePage"
          v-model:page-size="ruleSize"
          :total="ruleTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          style="margin-top:16px;justify-content:flex-end"
          @current-change="loadRules"
          @size-change="rulePage = 1; loadRules()"
        />
      </el-tab-pane>
    </el-tabs>

    <!-- ========== 交易详情弹窗 ========== -->
    <el-dialog v-model="txDetailVisible" title="交易详情" width="760px" destroy-on-close>
      <el-descriptions :column="2" border v-if="txDetail">
        <el-descriptions-item label="交易编号">{{ txDetail.transactionNo }}</el-descriptions-item>
        <el-descriptions-item label="客户ID">{{ txDetail.customerId }}</el-descriptions-item>
        <el-descriptions-item label="交易类型">{{ typeMap[txDetail.transactionType] || txDetail.transactionType }}</el-descriptions-item>
        <el-descriptions-item label="金额">{{ txDetail.currency }} {{ Number(txDetail.amount).toFixed(2) }}</el-descriptions-item>
        <el-descriptions-item label="支付方式">{{ txDetail.paymentMethod }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(txDetail.status)" size="small">{{ statusLabel(txDetail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="交易时间" :span="2">{{ txDetail.transactionTime }}</el-descriptions-item>
      </el-descriptions>
      <section v-if="txDetail" class="tx-flow-panel">
        <div class="flow-section-header">
          <div>
            <div class="flow-section-title">资金流向图</div>
            <div class="flow-section-subtitle">从客户、保单、交易、账户到交易对手展示资金路径</div>
          </div>
          <el-tag :type="transactionFlowRisk(txDetail).type" size="small">
            {{ transactionFlowRisk(txDetail).label }}
          </el-tag>
        </div>
        <div ref="txFlowChartRef" class="tx-flow-chart"></div>
        <div class="tx-flow-insights">
          <div v-for="item in transactionFlowInsights(txDetail)" :key="item" class="tx-flow-insight">
            {{ item }}
          </div>
        </div>
      </section>
    </el-dialog>

    <!-- ========== 规则创建/编辑弹窗 ========== -->
    <el-dialog
      v-model="ruleDialogVisible"
      :title="ruleDialogMode === 'create' ? '新建规则' : '编辑规则'"
      width="640px"
      destroy-on-close
    >
      <el-form ref="ruleFormRef" :model="ruleForm" :rules="ruleFormRules" label-width="100px">
        <el-form-item label="规则编码" prop="ruleCode">
          <el-input v-model="ruleForm.ruleCode" placeholder="如 RULE_001" :disabled="ruleDialogMode === 'edit'" />
        </el-form-item>
        <el-form-item label="规则名称" prop="ruleName">
          <el-input v-model="ruleForm.ruleName" placeholder="请输入规则名称" />
        </el-form-item>
        <el-form-item label="规则类型" prop="ruleCategory">
          <el-select v-model="ruleForm.ruleCategory" placeholder="请选择规则类型" style="width:100%">
            <el-option label="大额交易" value="LARGE_TXN" />
            <el-option label="可疑交易" value="SUSPICIOUS" />
            <el-option label="频率检测" value="VELOCITY" />
            <el-option label="阈值检测" value="THRESHOLD" />
            <el-option label="关联分析" value="CORRELATION" />
          </el-select>
        </el-form-item>
        <el-form-item label="规则配置" prop="configJson">
          <el-input
            v-model="ruleForm.configJson"
            type="textarea"
            :rows="4"
            placeholder='如: {"threshold":50000,"paymentMethod":"CASH"}'
          />
        </el-form-item>
        <el-form-item label="风险权重" prop="riskWeight">
          <el-slider v-model="ruleForm.riskWeight" :min="1" :max="100" :step="1" show-input />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="ruleSubmitting" @click="submitRule">确定</el-button>
      </template>
    </el-dialog>

    <!-- ========== 版本历史弹窗 ========== -->
    <el-dialog v-model="versionDialogVisible" title="规则版本历史" width="720px" destroy-on-close>
      <el-table :data="versions" stripe v-loading="versionLoading" border>
        <el-table-column prop="id" label="版本ID" width="80" />
        <el-table-column prop="ruleCode" label="规则编码" width="150" />
        <el-table-column prop="executionDetail" label="执行详情" min-width="220" show-overflow-tooltip />
        <el-table-column prop="matchScore" label="匹配分" width="90" align="center" />
        <el-table-column prop="durationMs" label="耗时(ms)" width="100" align="center" />
        <el-table-column prop="executionTime" label="执行时间" width="170" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref, reactive, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import request from '@/utils/request'

type EchartsModule = typeof import('echarts/core')
type EChartInstance = import('echarts/core').ECharts
type GraphMode = 'density' | 'trace' | 'ring' | 'shared'

const activeTab = ref('transactions')
let isMounted = false

// ==================== 交易监测 ====================
const txLoading = ref(false)
const transactions = ref<any[]>([])
const txPage = ref(1)
const txSize = ref(10)
const txTotal = ref(0)
const typeMap: Record<string, string> = {
  PREMIUM: '保费缴纳',
  SURRENDER: '退保',
  CLAIM: '理赔',
  LOAN: '保单贷款',
  REPAYMENT: '还款',
  PARTIAL_WITHDRAWAL: '部分领取'
}

const txQuery = reactive({
  transactionNo: '',
  transactionType: '',
  status: '',
  dateRange: null as [string, string] | null
})

const graphModeOptions: Array<{ label: string; value: GraphMode }> = [
  { label: '异常网络密度', value: 'density' },
  { label: '多层转账追踪', value: 'trace' },
  { label: '环形交易检测', value: 'ring' },
  { label: '共同账户检测', value: 'shared' }
]

const graphQuery = reactive({
  customerId: '',
  mode: 'density' as GraphMode,
  depth: 3,
  densityThreshold: 10
})

const graphLoading = ref(false)
const graphNotice = ref('')
const graphNoticeType = ref<'success' | 'warning' | 'info' | 'error'>('info')
const graphHasData = ref(false)
const graphInsights = ref<string[]>([])
const graphStats = reactive({
  nodes: 0,
  links: 0,
  transactionCount: 0,
  totalAmount: 0,
  riskLabel: '待分析',
  source: '未生成'
})
const graphChartRef = ref<HTMLElement | null>(null)
const txFlowChartRef = ref<HTMLElement | null>(null)
let graphChart: EChartInstance | null = null
let txFlowChart: EChartInstance | null = null
let graphEchartsModule: EchartsModule | null = null
let graphResizeHandler: (() => void) | null = null
let graphInitialized = false

interface GraphChartNode {
  id: string
  name: string
  category: number
  symbolSize: number
  value?: number
  itemStyle?: Record<string, unknown>
  raw?: Record<string, unknown>
}

interface GraphChartLink {
  source: string
  target: string
  label?: string
  value?: number
  lineStyle?: Record<string, unknown>
  raw?: Record<string, unknown>
}

interface NormalizedGraph {
  nodes: GraphChartNode[]
  links: GraphChartLink[]
  stats: {
    transactionCount: number
    totalAmount: number
    riskLabel: string
    source: string
  }
  insights: string[]
}

function resetTxQuery() {
  txQuery.transactionNo = ''
  txQuery.transactionType = ''
  txQuery.status = ''
  txQuery.dateRange = null
  txPage.value = 1
  loadTransactions()
}

function statusTagType(status: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

function statusLabel(status: string) {
  const map: Record<string, string> = { SUCCESS: '成功', FAILED: '失败', PENDING: '处理中' }
  return map[status] || status
}

async function loadTransactions() {
  txLoading.value = true
  try {
    const params: any = { page: txPage.value, size: txSize.value }
    if (txQuery.transactionNo) params.transactionNo = txQuery.transactionNo
    if (txQuery.transactionType) params.transactionType = txQuery.transactionType
    if (txQuery.status) params.status = txQuery.status
    if (txQuery.dateRange) {
      params.startDate = txQuery.dateRange[0]
      params.endDate = txQuery.dateRange[1]
    }
    const res: any = await request.get('/monitoring/transactions/page', { params })
    transactions.value = res.data?.list || []
    txTotal.value = res.data?.total || 0
    prepareDefaultGraphCustomer()
  } catch (e) { /* handled */ } finally { txLoading.value = false }
}

// 交易详情
const txDetailVisible = ref(false)
const txDetail = ref<any>(null)

function openTxDetail(row: any) {
  txDetail.value = row
  txDetailVisible.value = true
}

function disposeTransactionFlowChart() {
  txFlowChart?.dispose()
  txFlowChart = null
}

function scheduleTransactionFlowRender() {
  requestAnimationFrame(() => {
    requestAnimationFrame(() => renderTransactionFlow())
  })
}

watch(txDetailVisible, (visible) => {
  if (visible) {
    scheduleTransactionFlowRender()
  } else {
    disposeTransactionFlowChart()
  }
})

watch(txDetail, () => {
  if (txDetailVisible.value) {
    disposeTransactionFlowChart()
    scheduleTransactionFlowRender()
  }
})

function transactionFlowRisk(row: any): { label: string; type: 'success' | 'warning' | 'danger' | 'info' } {
  const amount = toAmount(row?.amount)
  if (row?.status === 'FAILED') return { label: '失败交易', type: 'danger' }
  if (row?.isCrossBorder || amount >= 200000) return { label: '高风险资金流', type: 'danger' }
  if (amount >= 50000 || row?.paymentMethod === 'CASH') return { label: '重点关注', type: 'warning' }
  return { label: '常规交易', type: 'success' }
}

function transactionFlowInsights(row: any) {
  const amount = toAmount(row?.amount)
  const insights = [
    `交易金额 ${formatCurrency(amount)}，类型为 ${typeMap[row?.transactionType] || row?.transactionType || '未标注'}。`
  ]
  if (row?.policyId) {
    insights.push(`该笔资金与保单 ${row.policyId} 关联，可继续核查保费、退保或理赔场景。`)
  }
  if (row?.counterpartyName || row?.counterpartyAccount) {
    insights.push(`交易对手为 ${safeText(row.counterpartyName || row.counterpartyAccount, '待补充')}，开户行 ${safeText(row.counterpartyBank, '待补充')}。`)
  }
  if (row?.isCrossBorder) {
    insights.push('该笔交易带有跨境标记，建议结合客户税收居民身份和资金来源材料复核。')
  } else if (amount >= 50000 || row?.paymentMethod === 'CASH') {
    insights.push('金额或支付方式达到关注区间，建议结合历史频率和交易目的确认合理性。')
  }
  return insights
}

function compactLabel(value: unknown, fallback: string) {
  const text = safeText(value, fallback)
  return text.length > 18 ? `${text.slice(0, 18)}...` : text
}

async function renderTransactionFlow() {
  await nextTick()
  const row = txDetail.value
  const container = txFlowChartRef.value
  if (!row || !container || !container.isConnected) return
  const rect = container.getBoundingClientRect()
  if (rect.width === 0 || rect.height === 0) return
  const echarts = await getGraphEcharts()
  if (!txFlowChart) {
    txFlowChart = echarts.init(container)
  }

  const amount = toAmount(row.amount)
  const customer = compactLabel(row.customerName || row.customerId, '客户')
  const policy = row.policyId ? `保单 ${row.policyId}` : ''
  const transaction = compactLabel(row.transactionNo, '交易')
  const account = compactLabel(row.counterpartyAccount, '对手账户')
  const counterparty = compactLabel(row.counterpartyName || row.counterpartyAccount, '交易对手')
  const bank = compactLabel(row.counterpartyBank, '开户行')
  const hasAccount = Boolean(row.counterpartyAccount)
  const risk = transactionFlowRisk(row)
  const nodes = [
    { name: customer, itemStyle: { color: '#2563eb' } },
    ...(policy ? [{ name: policy, itemStyle: { color: '#4f46e5' } }] : []),
    { name: transaction, itemStyle: { color: '#d97706' } },
    ...(hasAccount ? [{ name: account, itemStyle: { color: '#0f766e' } }] : []),
    { name: counterparty, itemStyle: { color: '#475569' } },
    { name: bank, itemStyle: { color: '#64748b' } },
    ...(risk.type !== 'success' ? [{ name: risk.label, itemStyle: { color: risk.type === 'danger' ? '#dc2626' : '#d97706' } }] : [])
  ]
  const links = policy
    ? [
        { source: customer, target: policy, value: Math.max(amount, 1), label: { show: true, formatter: '保单关联' } },
        { source: policy, target: transaction, value: Math.max(amount, 1), label: { show: true, formatter: typeMap[row.transactionType] || row.transactionType || '交易' } }
      ]
    : [
        { source: customer, target: transaction, value: Math.max(amount, 1), label: { show: true, formatter: typeMap[row.transactionType] || row.transactionType || '交易' } }
      ]
  if (hasAccount) {
    links.push(
      { source: transaction, target: account, value: Math.max(amount, 1), label: { show: true, formatter: formatCurrency(amount) } },
      { source: account, target: counterparty, value: Math.max(amount, 1), label: { show: true, formatter: '收付款账户' } }
    )
  } else {
    links.push({ source: transaction, target: counterparty, value: Math.max(amount, 1), label: { show: true, formatter: formatCurrency(amount) } })
  }
  links.push({ source: counterparty, target: bank, value: Math.max(amount, 1), label: { show: true, formatter: '开户行' } })
  if (risk.type !== 'success') {
    links.push({ source: transaction, target: risk.label, value: Math.max(amount * 0.45, 1), label: { show: true, formatter: '风险信号' } })
  }

  txFlowChart.setOption({
    tooltip: {
      trigger: 'item',
      confine: true,
      backgroundColor: 'rgba(15, 23, 42, 0.92)',
      borderWidth: 0,
      textStyle: { color: '#fff' },
      formatter: (params: any) => {
        if (params.dataType === 'edge') {
          return `${params.data.source} → ${params.data.target}<br/>金额：${formatCurrency(params.data.value)}`
        }
        return params.data.name
      }
    },
    series: [{
      type: 'sankey',
      left: 12,
      right: 32,
      top: 18,
      bottom: 18,
      nodeWidth: 16,
      nodeGap: 14,
      draggable: true,
      data: nodes,
      links,
      label: {
        color: '#1f2937',
        fontSize: 12,
        fontWeight: 600
      },
      lineStyle: {
        color: 'gradient',
        opacity: 0.38,
        curveness: 0.48
      },
      emphasis: {
        focus: 'adjacency'
      }
    }]
  }, true)
  txFlowChart.resize()
}

function openGraphFromTx(row: any) {
  if (!row?.customerId) {
    ElMessage.warning('该交易缺少客户ID，无法生成图谱')
    return
  }
  graphQuery.customerId = String(row.customerId)
  refreshGraph()
}

function prepareDefaultGraphCustomer() {
  const firstCustomerId = transactions.value.find(row => row?.customerId)?.customerId
  if (!graphQuery.customerId && firstCustomerId) {
    graphQuery.customerId = String(firstCustomerId)
  }
  if (!graphInitialized && graphQuery.customerId) {
    graphInitialized = true
    refreshGraph(true)
  }
}

async function getGraphEcharts() {
  if (!graphEchartsModule) {
    const [core, charts, components, renderers] = await Promise.all([
      import('echarts/core'),
      import('echarts/charts'),
      import('echarts/components'),
      import('echarts/renderers')
    ])
    core.use([
      charts.GraphChart,
      charts.SankeyChart,
      components.TooltipComponent,
      components.LegendComponent,
      renderers.CanvasRenderer
    ])
    graphEchartsModule = core
  }
  return graphEchartsModule
}

function graphModeLabel(mode: GraphMode) {
  return graphModeOptions.find(item => item.value === mode)?.label || '图谱分析'
}

function formatCurrency(value: unknown) {
  const amount = Number(value || 0)
  if (!Number.isFinite(amount)) return '¥0.00'
  return `¥${amount.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function graphRiskTagType(label: string) {
  if (label.includes('高') || label.includes('异常') || label.includes('可疑')) return 'danger'
  if (label.includes('关注') || label.includes('中')) return 'warning'
  if (label.includes('未')) return 'info'
  return 'success'
}

function categoryIndex(type?: string) {
  const normalized = String(type || '').toLowerCase()
  if (normalized.includes('account')) return 1
  if (normalized.includes('transaction') || normalized.includes('交易')) return 2
  if (normalized.includes('risk') || normalized.includes('alert')) return 3
  return 0
}

function safeText(value: unknown, fallback: string) {
  const text = String(value ?? '').trim()
  return text || fallback
}

function toAmount(value: unknown) {
  const amount = Number(value ?? 0)
  return Number.isFinite(amount) ? amount : 0
}

function createGraphBuilder() {
  const nodes = new Map<string, GraphChartNode>()
  const links: GraphChartLink[] = []
  const linkSet = new Set<string>()

  function addNode(node: GraphChartNode) {
    if (!node.id) return
    const existing = nodes.get(node.id)
    if (existing) {
      existing.symbolSize = Math.max(existing.symbolSize, node.symbolSize)
      existing.value = Math.max(Number(existing.value || 0), Number(node.value || 0))
      existing.raw = { ...(existing.raw || {}), ...(node.raw || {}) }
      return
    }
    nodes.set(node.id, node)
  }

  function addLink(link: GraphChartLink) {
    if (!link.source || !link.target) return
    const key = `${link.source}->${link.target}:${link.label || ''}`
    if (linkSet.has(key)) return
    linkSet.add(key)
    links.push(link)
  }

  return {
    addNode,
    addLink,
    toGraph() {
      return { nodes: Array.from(nodes.values()), links }
    }
  }
}

function amountNodeSize(amount: unknown, base = 34) {
  const value = Math.abs(toAmount(amount))
  return Math.min(74, base + Math.log10(value + 1) * 7)
}

function normalizeGraphPayload(payload: any, mode: GraphMode, customerId: string): NormalizedGraph {
  if (Array.isArray(payload?.nodes) && Array.isArray(payload?.edges || payload?.links)) {
    return normalizeGenericGraph(payload, mode)
  }
  if (mode === 'ring') return normalizeRingGraph(payload, customerId)
  if (mode === 'trace') return normalizeTraceGraph(payload, customerId)
  if (mode === 'shared') return normalizeSharedAccountGraph(payload, customerId)
  return normalizeDensityGraph(payload, customerId)
}

function normalizeGenericGraph(payload: any, mode: GraphMode): NormalizedGraph {
  const builder = createGraphBuilder()
  const edges = payload.edges || payload.links || []
  const totalAmount = edges.reduce((sum: number, edge: any) => sum + toAmount(edge?.properties?.amount || edge?.amount), 0)
  payload.nodes.forEach((node: any) => {
    const type = node.type || node.label || 'Customer'
    builder.addNode({
      id: safeText(node.id, `node-${Math.random()}`),
      name: safeText(node.label || node.name, safeText(node.id, '未知节点')),
      category: categoryIndex(type),
      symbolSize: amountNodeSize(node?.properties?.amount, categoryIndex(type) === 2 ? 30 : 42),
      value: toAmount(node?.properties?.amount),
      raw: node
    })
  })
  edges.forEach((edge: any) => {
    builder.addLink({
      source: safeText(edge.source, ''),
      target: safeText(edge.target, ''),
      label: safeText(edge.label || edge.type, ''),
      value: toAmount(edge?.properties?.amount || edge?.amount),
      raw: edge
    })
  })
  const graph = builder.toGraph()
  return {
    ...graph,
    stats: {
      transactionCount: graph.links.length,
      totalAmount,
      riskLabel: graph.links.length ? '需关注' : '未发现异常',
      source: graphModeLabel(mode)
    },
    insights: graph.links.length
      ? [`当前图谱包含 ${graph.nodes.length} 个节点和 ${graph.links.length} 条关系。`]
      : ['图分析接口未返回有效关系。']
  }
}

function normalizeRingGraph(payload: any, customerId: string): NormalizedGraph {
  const builder = createGraphBuilder()
  const pathNodes = Array.isArray(payload?.pathNodes) ? payload.pathNodes : []
  let totalAmount = 0
  pathNodes.forEach((node: any, index: number) => {
    const type = node.type || 'Customer'
    const id = `${type}:${safeText(node.id, node.name || 'unknown')}`
    const amount = toAmount(node.amount)
    totalAmount += amount
    builder.addNode({
      id,
      name: safeText(node.name, safeText(node.id, '未知节点')),
      category: categoryIndex(type),
      symbolSize: categoryIndex(type) === 2 ? amountNodeSize(amount, 30) : 46,
      value: amount,
      raw: node
    })
    const next = pathNodes[index + 1]
    if (next) {
      builder.addLink({
        source: id,
        target: `${next.type || 'Customer'}:${safeText(next.id, next.name || 'unknown')}`,
        label: '路径',
        value: amount
      })
    }
  })
  if (!pathNodes.length) {
    builder.addNode({
      id: `Customer:${customerId}`,
      name: `客户 ${customerId}`,
      category: 0,
      symbolSize: 48
    })
  }
  const graph = builder.toGraph()
  return {
    ...graph,
    stats: {
      transactionCount: graph.links.length,
      totalAmount,
      riskLabel: payload?.detected ? '高风险环形交易' : '未发现环形交易',
      source: '环形交易检测'
    },
    insights: payload?.detected
      ? [`检测到 ${pathNodes.length} 个路径节点构成资金回流链路。`, '建议结合交易时间和金额拆分情况进一步核查。']
      : ['未检测到客户资金回流路径。']
  }
}

function normalizeTraceGraph(payload: any, customerId: string): NormalizedGraph {
  const builder = createGraphBuilder()
  const startId = `Customer:${safeText(payload?.startCustomerId, customerId)}`
  builder.addNode({
    id: startId,
    name: safeText(payload?.startCustomerName, `客户 ${customerId}`),
    category: 0,
    symbolSize: 54,
    itemStyle: { borderWidth: 3 }
  })

  const chains = Array.isArray(payload?.chains) ? payload.chains : []
  let totalAmount = 0
  chains.forEach((chain: any) => {
    const fromId = `Customer:${safeText(chain.fromCustomerId, customerId)}`
    const toId = `Customer:${safeText(chain.toCustomerId, chain.toCustomerName || 'unknown')}`
    const amount = toAmount(chain.amount)
    totalAmount += amount
    builder.addNode({
      id: fromId,
      name: safeText(chain.fromCustomerName, safeText(chain.fromCustomerId, '发起客户')),
      category: 0,
      symbolSize: 48,
      raw: chain
    })
    builder.addNode({
      id: toId,
      name: safeText(chain.toCustomerName, safeText(chain.toCustomerId, '接收客户')),
      category: 0,
      symbolSize: 42,
      raw: chain
    })
    if (chain.viaAccount) {
      const accountId = `Account:${chain.viaAccount}`
      builder.addNode({
        id: accountId,
        name: safeText(chain.viaAccount, '中转账户'),
        category: 1,
        symbolSize: 38,
        raw: chain
      })
      builder.addLink({ source: fromId, target: accountId, label: `第${chain.depth || 1}层`, value: amount })
      builder.addLink({ source: accountId, target: toId, label: formatCurrency(amount), value: amount })
    } else {
      builder.addLink({ source: fromId, target: toId, label: formatCurrency(amount), value: amount })
    }
  })

  const graph = builder.toGraph()
  return {
    ...graph,
    stats: {
      transactionCount: chains.length,
      totalAmount,
      riskLabel: payload?.suspicious ? '可疑多层转账' : '未发现多层异常',
      source: '多层转账追踪'
    },
    insights: chains.length
      ? [`追踪到 ${chains.length} 条资金链路，最大层级 ${payload?.maxDepth || graphQuery.depth}。`, payload?.suspicious ? '存在三层及以上转账链路，建议进入案件调查。' : '暂未达到多层转账可疑阈值。']
      : ['未追踪到多层资金转移链路。']
  }
}

function normalizeSharedAccountGraph(payload: any, customerId: string): NormalizedGraph {
  const builder = createGraphBuilder()
  const sourceId = `Customer:${safeText(payload?.customerId, customerId)}`
  builder.addNode({
    id: sourceId,
    name: `客户 ${safeText(payload?.customerId, customerId)}`,
    category: 0,
    symbolSize: 54,
    itemStyle: { borderWidth: 3 }
  })
  const sharedAccounts = Array.isArray(payload?.sharedAccounts) ? payload.sharedAccounts : []
  sharedAccounts.forEach((account: any) => {
    const accountId = `Account:${safeText(account.accountNo, 'unknown')}`
    const relatedId = `Customer:${safeText(account.relatedCustomerId, account.relatedCustomerName || 'unknown')}`
    builder.addNode({
      id: accountId,
      name: safeText(account.accountNo, '共享账户'),
      category: 1,
      symbolSize: 42,
      raw: account
    })
    builder.addNode({
      id: relatedId,
      name: safeText(account.relatedCustomerName, safeText(account.relatedCustomerId, '关联客户')),
      category: 0,
      symbolSize: 42,
      raw: account
    })
    builder.addLink({ source: sourceId, target: accountId, label: '持有' })
    builder.addLink({ source: accountId, target: relatedId, label: account.bank || '共享' })
  })
  const graph = builder.toGraph()
  return {
    ...graph,
    stats: {
      transactionCount: sharedAccounts.length,
      totalAmount: 0,
      riskLabel: payload?.detected ? '共同账户异常' : '未发现共同账户',
      source: '共同账户检测'
    },
    insights: sharedAccounts.length
      ? [`发现 ${sharedAccounts.length} 个共享账户关系。`, '建议核查关联客户身份、账户授权及真实受益人关系。']
      : ['未发现该客户与其他客户共用账户。']
  }
}

function normalizeDensityGraph(payload: any, customerId: string): NormalizedGraph {
  const builder = createGraphBuilder()
  const sourceId = `Customer:${safeText(payload?.customerId, customerId)}`
  builder.addNode({
    id: sourceId,
    name: safeText(payload?.customerName, `客户 ${customerId}`),
    category: 0,
    symbolSize: 56,
    itemStyle: { borderWidth: 3 }
  })
  const counterparties = Array.isArray(payload?.counterparties) ? payload.counterparties : []
  counterparties.forEach((counterparty: any) => {
    const amount = toAmount(counterparty.totalAmount)
    const targetId = `Counterparty:${safeText(counterparty.counterpartyId, counterparty.counterpartyName || 'unknown')}`
    builder.addNode({
      id: targetId,
      name: safeText(counterparty.counterpartyName, safeText(counterparty.counterpartyId, '交易对手')),
      category: 0,
      symbolSize: amountNodeSize(amount, 38),
      value: amount,
      raw: counterparty
    })
    builder.addLink({
      source: sourceId,
      target: targetId,
      label: `${counterparty.transactionCount || 0}笔`,
      value: amount,
      raw: counterparty
    })
  })
  const graph = builder.toGraph()
  const totalAmount = toAmount(payload?.totalAmount) || counterparties.reduce((sum: number, item: any) => sum + toAmount(item.totalAmount), 0)
  return {
    ...graph,
    stats: {
      transactionCount: Number(payload?.transactionCount || counterparties.reduce((sum: number, item: any) => sum + Number(item.transactionCount || 0), 0)),
      totalAmount,
      riskLabel: payload?.densityAlert ? '异常高密度网络' : '网络密度正常',
      source: '异常网络密度'
    },
    insights: counterparties.length
      ? [`识别到 ${counterparties.length} 个直接交易对手，阈值为 ${graphQuery.densityThreshold}。`, payload?.densityAlert ? '交易网络超过阈值，建议结合交易目的和关联关系核查。' : '当前关联方数量未超过阈值。']
      : ['图数据库暂未返回直接交易对手。']
  }
}

function parseChainParties(row: any) {
  const remark = String(row?.remark || '')
  const match = remark.match(/复杂关联链路(?:第\d+跳|回流跳)?[:：]\s*(.+?)\s*->\s*(.+?)(?:\s+\d{8,}|\s*$)/)
  if (!match || !match[1] || !match[2]) return null
  return {
    sourceName: match[1].trim(),
    targetName: match[2].trim(),
    isComplexChain: true
  }
}

function partyNodeId(name: string) {
  return `Party:${name}`
}

function txSourceNode(row: any, parties: ReturnType<typeof parseChainParties>) {
  if (parties?.sourceName) {
    return { id: partyNodeId(parties.sourceName), name: parties.sourceName }
  }
  const customerId = safeText(row.customerId, 'unknown')
  return { id: `Customer:${customerId}`, name: `客户 ${customerId}` }
}

function txTargetNode(row: any, parties: ReturnType<typeof parseChainParties>, index: number) {
  const name = safeText(parties?.targetName || row.counterpartyName || row.counterpartyAccount, '交易对手待补充')
  return { id: partyNodeId(name || String(index)), name }
}

function pickFallbackRows(customerId: string) {
  const rowContexts = transactions.value.slice(0, 30).map((row, index) => ({
    row,
    index,
    parties: parseChainParties(row)
  }))
  const selectedCustomerRows = rowContexts.filter(item => String(item.row.customerId || '') === String(customerId))
  const seedNames = new Set<string>()
  selectedCustomerRows.forEach(item => {
    if (item.parties?.sourceName) seedNames.add(item.parties.sourceName)
    if (item.parties?.targetName) seedNames.add(item.parties.targetName)
  })

  if (!seedNames.size) {
    return selectedCustomerRows.slice(0, 20)
  }

  const included = new Set<number>()
  let expanded = true
  while (expanded) {
    expanded = false
    rowContexts.forEach(item => {
      if (!item.parties) return
      const touchesGraph = seedNames.has(item.parties.sourceName) || seedNames.has(item.parties.targetName)
      if (!touchesGraph) return
      if (!included.has(item.index)) {
        included.add(item.index)
        expanded = true
      }
      seedNames.add(item.parties.sourceName)
      seedNames.add(item.parties.targetName)
    })
  }

  return rowContexts
    .filter(item => included.has(item.index) || String(item.row.customerId || '') === String(customerId))
    .slice(0, 24)
}

function buildFallbackGraphFromTransactions(customerId: string): NormalizedGraph {
  const builder = createGraphBuilder()
  const relatedRows = pickFallbackRows(customerId)
  if (!relatedRows.length) {
    return {
      nodes: [],
      links: [],
      stats: { transactionCount: 0, totalAmount: 0, riskLabel: '暂无数据', source: '当前交易列表' },
      insights: ['当前交易列表中没有该客户的交易记录。']
    }
  }

  const accountCounts = new Map<string, number>()
  relatedRows.forEach(({ row }) => {
    const account = String(row.counterpartyAccount || '').trim()
    if (account) accountCounts.set(account, (accountCounts.get(account) || 0) + 1)
  })
  let totalAmount = 0
  relatedRows.forEach(({ row, index, parties }) => {
    const amount = toAmount(row.amount)
    totalAmount += amount
    const source = txSourceNode(row, parties)
    const target = txTargetNode(row, parties, index)
    const txId = `Transaction:${safeText(row.id || row.transactionNo, String(index))}`
    const account = String(row.counterpartyAccount || '').trim()
    const showAccount = Boolean(account && (accountCounts.get(account) || 0) > 1)
    const sourceHighlight = String(row.customerId || '') === String(customerId) || Boolean(parties?.isComplexChain)
    builder.addNode({
      id: txId,
      name: safeText(row.transactionNo, `交易 ${index + 1}`),
      category: 2,
      symbolSize: amountNodeSize(amount, 30),
      value: amount,
      raw: row
    })
    builder.addNode({
      id: source.id,
      name: source.name,
      category: 0,
      symbolSize: sourceHighlight ? 52 : 42,
      itemStyle: sourceHighlight ? { borderWidth: 3 } : undefined,
      raw: row
    })
    builder.addNode({
      id: target.id,
      name: target.name,
      category: 0,
      symbolSize: row.isCrossBorder ? 50 : 42,
      itemStyle: row.isCrossBorder ? { borderColor: '#d97706', borderWidth: 2 } : undefined,
      raw: row
    })

    builder.addLink({ source: source.id, target: txId, label: typeMap[row.transactionType] || row.transactionType || '交易', value: amount })
    if (showAccount) {
      const accountId = `Account:${account}`
      builder.addNode({
        id: accountId,
        name: account,
        category: 1,
        symbolSize: 42,
        itemStyle: { borderColor: '#d97706', borderWidth: 2 },
        raw: row
      })
      builder.addLink({ source: txId, target: accountId, label: formatCurrency(amount), value: amount })
      builder.addLink({ source: accountId, target: target.id, label: '共用账户', value: amount })
    } else {
      builder.addLink({ source: txId, target: target.id, label: formatCurrency(amount), value: amount })
    }
  })

  const graph = builder.toGraph()
  const highAmountCount = relatedRows.filter(({ row }) => toAmount(row.amount) >= 50000).length
  const crossBorderCount = relatedRows.filter(({ row }) => row.isCrossBorder).length
  const complexChainCount = relatedRows.filter(item => item.parties?.isComplexChain).length
  const sharedAccountCount = Array.from(accountCounts.values()).filter(count => count > 1).length
  return {
    ...graph,
    stats: {
      transactionCount: relatedRows.length,
      totalAmount,
      riskLabel: highAmountCount || crossBorderCount ? '需关注' : '业务测试图谱',
      source: '当前交易列表'
    },
    insights: [
      `基于当前交易列表生成 ${relatedRows.length} 笔交易关系。`,
      complexChainCount ? `识别到 ${complexChainCount} 笔复杂链路交易，可展示多跳资金转移和回流路径。` : '当前页未识别到显式复杂链路交易。',
      highAmountCount ? `其中 ${highAmountCount} 笔交易金额达到重点关注区间。` : '当前页未发现大额交易节点。',
      crossBorderCount ? `其中 ${crossBorderCount} 笔交易涉及跨境标记。` : '当前页未发现跨境交易标记。',
      sharedAccountCount ? `发现 ${sharedAccountCount} 个重复出现的交易账户，可用于展示共同账户风险。` : '当前页未发现共用账户关系。'
    ]
  }
}

function resetGraphState() {
  graphStats.nodes = 0
  graphStats.links = 0
  graphStats.transactionCount = 0
  graphStats.totalAmount = 0
  graphStats.riskLabel = '待分析'
  graphStats.source = '未生成'
  graphInsights.value = []
  graphHasData.value = false
  graphChart?.clear()
}

async function fetchGraphPayload() {
  const customerId = graphQuery.customerId.trim()
  const mode = graphQuery.mode
  const params: Record<string, unknown> = { customerId }
  let url = '/monitoring/graph/network-density'
  if (mode === 'ring') {
    url = '/monitoring/graph/ring-detection'
  } else if (mode === 'trace') {
    url = '/monitoring/graph/multi-layer-transfer'
    params.maxDepth = graphQuery.depth
  } else if (mode === 'shared') {
    url = '/monitoring/graph/shared-accounts'
  } else {
    params.densityThreshold = graphQuery.densityThreshold
  }
  const res: any = await request.get(url, { params })
  return res.data || {}
}

async function refreshGraph(silent = false) {
  const customerId = graphQuery.customerId.trim()
  if (!customerId) {
    if (!silent) ElMessage.warning('请先选择或输入客户ID')
    resetGraphState()
    return
  }

  graphLoading.value = true
  graphNotice.value = ''
  graphNoticeType.value = 'info'
  try {
    let normalized = normalizeGraphPayload(await fetchGraphPayload(), graphQuery.mode, customerId)
    if (!normalized.links.length) {
      const fallback = buildFallbackGraphFromTransactions(customerId)
      if (fallback.nodes.length) {
        normalized = fallback
        graphNotice.value = '图数据库暂未返回有效关系，已使用当前交易列表生成业务测试图谱。'
        graphNoticeType.value = 'warning'
      } else if (!silent) {
        graphNotice.value = '未找到可用于生成图谱的交易关系。'
        graphNoticeType.value = 'info'
      }
    }
    await renderGraph(normalized)
  } catch (e) {
    const fallback = buildFallbackGraphFromTransactions(customerId)
    if (fallback.nodes.length) {
      graphNotice.value = '图分析接口暂不可用，已使用当前交易列表生成业务测试图谱。'
      graphNoticeType.value = 'warning'
      await renderGraph(fallback)
    } else {
      resetGraphState()
      graphNotice.value = '图分析接口暂不可用，且当前列表没有可生成图谱的交易。'
      graphNoticeType.value = 'error'
    }
  } finally {
    graphLoading.value = false
  }
}

async function renderGraph(normalized: NormalizedGraph) {
  await nextTick()
  const container = graphChartRef.value
  if (!isMounted || !container || !container.isConnected) return
  const echarts = await getGraphEcharts()
  if (!graphChart) {
    graphChart = echarts.init(container)
    graphResizeHandler = () => graphChart?.resize()
    window.addEventListener('resize', graphResizeHandler)
  }

  graphStats.nodes = normalized.nodes.length
  graphStats.links = normalized.links.length
  graphStats.transactionCount = normalized.stats.transactionCount
  graphStats.totalAmount = normalized.stats.totalAmount
  graphStats.riskLabel = normalized.stats.riskLabel
  graphStats.source = normalized.stats.source
  graphInsights.value = normalized.insights
  graphHasData.value = normalized.nodes.length > 0

  if (!normalized.nodes.length) {
    graphChart.clear()
    return
  }

  graphChart.setOption({
    color: ['#2563eb', '#0f766e', '#d97706', '#dc2626'],
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(15, 23, 42, 0.92)',
      borderWidth: 0,
      textStyle: { color: '#fff' },
      formatter: (params: any) => {
        if (params.dataType === 'edge') {
          return [
            `<strong>${params.data.label || '交易关系'}</strong>`,
            params.data.value ? `金额：${formatCurrency(params.data.value)}` : ''
          ].filter(Boolean).join('<br/>')
        }
        const raw = params.data.raw || {}
        return [
          `<strong>${params.data.name}</strong>`,
          params.data.value ? `金额：${formatCurrency(params.data.value)}` : '',
          raw.bank ? `银行：${raw.bank}` : '',
          raw.counterpartyBank ? `开户行：${raw.counterpartyBank}` : ''
        ].filter(Boolean).join('<br/>')
      }
    },
    legend: {
      top: 8,
      data: ['客户/交易对手', '账户', '交易', '风险'],
      textStyle: { color: '#475569' }
    },
    series: [{
      type: 'graph',
      layout: 'force',
      top: 44,
      bottom: 18,
      left: 12,
      right: 12,
      roam: true,
      draggable: true,
      categories: [
        { name: '客户/交易对手' },
        { name: '账户' },
        { name: '交易' },
        { name: '风险' }
      ],
      data: normalized.nodes,
      links: normalized.links,
      label: {
        show: true,
        position: 'right',
        color: '#1f2937',
        fontSize: 12,
        formatter: (params: any) => params.data.name
      },
      edgeSymbol: ['none', 'arrow'],
      edgeSymbolSize: [0, 8],
      edgeLabel: {
        show: true,
        color: '#64748b',
        fontSize: 11,
        formatter: (params: any) => params.data.label || ''
      },
      force: {
        repulsion: 280,
        edgeLength: [92, 170],
        gravity: 0.06
      },
      itemStyle: {
        borderColor: '#fff',
        borderWidth: 2,
        shadowBlur: 8,
        shadowColor: 'rgba(15, 23, 42, 0.16)'
      },
      lineStyle: {
        width: 1.8,
        color: 'source',
        opacity: 0.72,
        curveness: 0.08
      },
      emphasis: {
        focus: 'adjacency',
        lineStyle: { width: 3 },
        label: { fontWeight: 700 }
      }
    }]
  }, true)
}

// ==================== 规则管理 ====================
const ruleLoading = ref(false)
const rules = ref<any[]>([])
const rulePage = ref(1)
const ruleSize = ref(10)
const ruleTotal = ref(0)

const ruleQuery = reactive({ keyword: '', ruleCategory: '' })

function resetRuleQuery() {
  ruleQuery.keyword = ''
  ruleQuery.ruleCategory = ''
  rulePage.value = 1
  loadRules()
}

function ruleCategoryLabel(type: string) {
  const map: Record<string, string> = {
    LARGE_TXN: '大额交易',
    SUSPICIOUS: '可疑交易',
    VELOCITY: '频率检测',
    THRESHOLD: '阈值检测',
    CORRELATION: '关联分析'
  }
  return map[type] || type
}

function isRuleEnabled(row: any) {
  return row.status === 'ENABLED'
}

async function loadRules() {
  ruleLoading.value = true
  try {
    const params: any = { page: rulePage.value, size: ruleSize.value }
    if (ruleQuery.keyword) params.keyword = ruleQuery.keyword
    if (ruleQuery.ruleCategory) params.ruleCategory = ruleQuery.ruleCategory
    const res: any = await request.get('/monitoring/rules/page', { params })
    rules.value = res.data?.list || []
    ruleTotal.value = res.data?.total || 0
  } catch (e) { /* handled */ } finally { ruleLoading.value = false }
}

// 启用/禁用规则
async function toggleRule(row: any) {
  const nextEnabled = !isRuleEnabled(row)
  const action = nextEnabled ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确定要${action}规则「${row.ruleName}」吗？`, '确认', { type: 'warning' })
    const url = `/monitoring/rules/${row.id}/${nextEnabled ? 'enable' : 'disable'}`
    await request.post(url)
    row.status = nextEnabled ? 'ENABLED' : 'DISABLED'
    ElMessage.success(`已${action}`)
    loadRules()
    return true
  } catch (e) {
    return false
  }
}

// 规则创建/编辑弹窗
const ruleDialogVisible = ref(false)
const ruleDialogMode = ref<'create' | 'edit'>('create')
const ruleSubmitting = ref(false)
const ruleFormRef = ref<FormInstance>()
const ruleForm = reactive({
  id: null as number | null,
  ruleCode: '',
  ruleName: '',
  ruleCategory: '',
  configJson: '',
  riskWeight: 50,
  priority: 100,
  status: 'DISABLED'
})

const ruleFormRules: FormRules = {
  ruleCode: [{ required: true, message: '请输入规则编码', trigger: 'blur' }],
  ruleName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  ruleCategory: [{ required: true, message: '请选择规则类型', trigger: 'change' }],
  configJson: [{ required: true, message: '请输入规则配置', trigger: 'blur' }],
  riskWeight: [{ required: true, message: '请设置风险权重', trigger: 'change' }]
}

function openRuleDialog(mode: 'create' | 'edit', row?: any) {
  ruleDialogMode.value = mode
  if (mode === 'edit' && row) {
    ruleForm.id = row.id
    ruleForm.ruleCode = row.ruleCode
    ruleForm.ruleName = row.ruleName
    ruleForm.ruleCategory = row.ruleCategory
    ruleForm.configJson = row.configJson || ''
    ruleForm.riskWeight = row.riskWeight ?? 50
    ruleForm.priority = row.priority ?? 100
    ruleForm.status = row.status || 'DISABLED'
  } else {
    ruleForm.id = null
    ruleForm.ruleCode = ''
    ruleForm.ruleName = ''
    ruleForm.ruleCategory = ''
    ruleForm.configJson = ''
    ruleForm.riskWeight = 50
    ruleForm.priority = 100
    ruleForm.status = 'DISABLED'
  }
  ruleDialogVisible.value = true
}

async function submitRule() {
  if (!ruleFormRef.value) return
  await ruleFormRef.value.validate()
  ruleSubmitting.value = true
  try {
    const payload = {
      id: ruleForm.id,
      ruleCode: ruleForm.ruleCode,
      ruleName: ruleForm.ruleName,
      ruleCategory: ruleForm.ruleCategory,
      configJson: ruleForm.configJson,
      riskWeight: ruleForm.riskWeight,
      priority: ruleForm.priority,
      status: ruleForm.status
    }
    if (ruleDialogMode.value === 'create') {
      await request.post('/monitoring/rules', payload)
      ElMessage.success('规则创建成功')
    } else {
      await request.put('/monitoring/rules', payload)
      ElMessage.success('规则更新成功')
    }
    ruleDialogVisible.value = false
    loadRules()
  } catch (e) { /* handled */ } finally { ruleSubmitting.value = false }
}

// 版本历史
const versionDialogVisible = ref(false)
const versionLoading = ref(false)
const versions = ref<any[]>([])

async function openVersionHistory(row: any) {
  versionDialogVisible.value = true
  versionLoading.value = true
  try {
    const res: any = await request.get(`/monitoring/rules/${row.id}/versions`)
    versions.value = res.data || []
  } catch (e) { /* handled */ } finally { versionLoading.value = false }
}

// ==================== 初始化 ====================
onMounted(() => {
  isMounted = true
  loadTransactions()
  loadRules()
})

onUnmounted(() => {
  isMounted = false
  if (graphResizeHandler) {
    window.removeEventListener('resize', graphResizeHandler)
    graphResizeHandler = null
  }
  graphChart?.dispose()
  graphChart = null
  disposeTransactionFlowChart()
})
</script>

<style scoped>
.monitoring-container {
  padding: 4px;
}

.network-graph-panel {
  margin-bottom: 18px;
  padding: 16px;
  border: 1px solid var(--border-subtle, #e5e7eb);
  border-radius: 6px;
  background: #fff;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
}

.graph-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.graph-heading {
  min-width: 220px;
}

.graph-title {
  color: #111827;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.4;
}

.graph-subtitle {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

.graph-form {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  row-gap: 4px;
}

.graph-alert {
  margin-top: 12px;
}

.graph-summary {
  display: grid;
  grid-template-columns: repeat(6, minmax(116px, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.graph-stat {
  min-height: 58px;
  padding: 10px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #f8fafc;
}

.graph-stat span {
  display: block;
  margin-bottom: 6px;
  color: #64748b;
  font-size: 12px;
}

.graph-stat strong {
  display: block;
  color: #111827;
  font-size: 17px;
  line-height: 1.2;
}

.graph-content {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 260px;
  gap: 14px;
  margin-top: 14px;
}

.graph-chart {
  position: relative;
  min-height: 380px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background:
    linear-gradient(rgba(37, 99, 235, 0.045) 1px, transparent 1px),
    linear-gradient(90deg, rgba(37, 99, 235, 0.045) 1px, transparent 1px),
    #ffffff;
  background-size: 24px 24px;
}

.graph-insights {
  min-height: 380px;
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fbfdff;
}

.insight-title {
  margin-bottom: 10px;
  color: #111827;
  font-size: 14px;
  font-weight: 700;
}

.insight-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.insight-item {
  padding: 10px 12px;
  border-left: 3px solid #2563eb;
  border-radius: 4px;
  background: #eff6ff;
  color: #1f2937;
  font-size: 13px;
  line-height: 1.55;
}

.tx-flow-panel {
  margin-top: 16px;
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fbfdff;
}

.flow-section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.flow-section-title {
  color: #111827;
  font-size: 15px;
  font-weight: 700;
}

.flow-section-subtitle {
  margin-top: 3px;
  color: #64748b;
  font-size: 12px;
}

.tx-flow-chart {
  height: 260px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
}

.tx-flow-insights {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-top: 10px;
}

.tx-flow-insight {
  padding: 8px 10px;
  border-left: 3px solid #2563eb;
  border-radius: 4px;
  background: #eff6ff;
  color: #334155;
  font-size: 12px;
  line-height: 1.5;
}

@media (max-width: 1180px) {
  .graph-toolbar {
    display: block;
  }

  .graph-form {
    justify-content: flex-start;
    margin-top: 12px;
  }

  .graph-summary {
    grid-template-columns: repeat(3, minmax(116px, 1fr));
  }

  .graph-content {
    grid-template-columns: 1fr;
  }

  .graph-insights {
    min-height: auto;
  }
}

@media (max-width: 720px) {
  .graph-summary {
    grid-template-columns: repeat(2, minmax(116px, 1fr));
  }

  .graph-chart {
    min-height: 320px;
  }
}
</style>
