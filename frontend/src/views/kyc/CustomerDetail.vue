<template>
  <div class="customer-detail" v-loading="loading">
    <!-- 页面头部：客户名称 + 风险等级 -->
    <el-page-header @back="$router.push('/kyc')" style="margin-bottom: 20px">
      <template #content>
        <div class="header-content">
          <span class="customer-name">{{ customer.name || '客户详情' }}</span>
          <el-tag
            v-if="customer.riskLevel"
            :type="riskTagType"
            size="large"
            style="margin-left: 12px"
          >
            {{ riskLevelText }}
          </el-tag>
        </div>
      </template>
    </el-page-header>

    <!-- 4个标签页 -->
    <el-tabs v-model="activeTab">
      <!-- Tab 1: 基本信息 -->
      <el-tab-pane label="基本信息" name="basic">
        <el-row :gutter="16" class="profile-section">
          <el-col :xs="24" :xl="14">
            <el-card shadow="never" class="profile-card">
              <template #header>
                <div class="section-title">
                  <span>客户画像</span>
                  <span class="section-subtitle">基于当前客户资料与风险线索生成</span>
                </div>
              </template>
              <div class="profile-summary">
                <div
                  v-for="item in profileStats"
                  :key="item.label"
                  class="profile-stat"
                >
                  <span class="profile-stat-label">{{ item.label }}</span>
                  <strong>{{ item.value }}</strong>
                </div>
              </div>
              <div class="profile-dimensions">
                <div
                  v-for="dimension in profileDimensions"
                  :key="dimension.name"
                  class="dimension-row"
                >
                  <div class="dimension-meta">
                    <span>{{ dimension.name }}</span>
                    <span>{{ dimension.value }}</span>
                  </div>
                  <el-progress
                    :percentage="dimension.value"
                    :stroke-width="8"
                    :show-text="false"
                    :color="dimension.color"
                  />
                  <div class="dimension-desc">{{ dimension.description }}</div>
                </div>
              </div>
            </el-card>
          </el-col>
          <el-col :xs="24" :xl="10">
            <el-card shadow="never" class="profile-card chart-card">
              <template #header>
                <div class="section-title">
                  <span>画像雷达图</span>
                  <span class="section-subtitle">数值越高代表风险或关注度越高</span>
                </div>
              </template>
              <div ref="profileRadarChart" class="profile-radar"></div>
            </el-card>
          </el-col>
        </el-row>

        <el-descriptions :column="2" border>
          <el-descriptions-item label="客户编号">{{ customer.customerNo || customer.id || '-' }}</el-descriptions-item>
          <el-descriptions-item label="姓名/名称">{{ customer.name || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户类型">{{ customerTypeText }}</el-descriptions-item>
          <el-descriptions-item label="证件类型">{{ idTypeText }}</el-descriptions-item>
          <el-descriptions-item label="证件号码">{{ customer.idNumber || '-' }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ customer.phone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ customer.email || '-' }}</el-descriptions-item>
          <el-descriptions-item label="国籍">{{ customer.nationality || '-' }}</el-descriptions-item>
          <el-descriptions-item label="风险等级">
            <el-tag :type="riskTagType">{{ riskLevelText }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="风险评分">{{ customer.riskScore ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="KYC状态">{{ kycStatusText }}</el-descriptions-item>
          <el-descriptions-item label="客户状态">{{ statusText }}</el-descriptions-item>
          <el-descriptions-item label="地址">{{ customer.address || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ customer.createdTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ customer.updatedTime || '-' }}</el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>

      <!-- Tab 2: 客户关系图谱 -->
      <el-tab-pane label="关系图谱" name="relationship">
        <section class="relationship-panel">
          <div class="relationship-header">
            <div>
              <div class="section-title">
                <span>客户关系图谱</span>
                <span class="section-subtitle">客户、受益人、保单、产品、交易、预警、案件、STR 与名单风险的业务链路</span>
              </div>
            </div>
            <el-button type="primary" :loading="relationshipLoading" @click="fetchRelationshipGraph">
              刷新图谱
            </el-button>
          </div>

          <div class="relationship-summary">
            <div
              v-for="item in relationshipSummaryCards"
              :key="item.label"
              class="relationship-stat"
            >
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
              <em>{{ item.note }}</em>
            </div>
          </div>

          <div class="relationship-content">
            <div ref="relationshipGraphChart" v-loading="relationshipLoading" class="relationship-graph">
              <el-empty
                v-if="!relationshipHasData && !relationshipLoading"
                description="暂无可展示的客户关系链路"
              />
            </div>
            <aside class="relationship-side">
              <div class="relationship-side-title">图谱解读</div>
              <div v-if="relationshipInsights.length" class="relationship-insight-list">
                <div
                  v-for="item in relationshipInsights"
                  :key="item"
                  class="relationship-insight"
                >
                  {{ item }}
                </div>
              </div>
              <el-empty v-else description="暂无图谱解读" :image-size="72" />

              <div class="relationship-side-title legend-title">节点类型</div>
              <div class="relationship-legend">
                <span
                  v-for="item in relationshipLegend"
                  :key="item.name"
                  class="legend-item"
                >
                  <i :style="{ backgroundColor: item.color }"></i>
                  {{ item.name }}
                </span>
              </div>
            </aside>
          </div>
        </section>
      </el-tab-pane>

      <!-- Tab 2: 受益所有人 -->
      <el-tab-pane label="受益所有人" name="owners">
        <el-table :data="beneficialOwners" border stripe empty-text="暂无受益所有人数据">
          <el-table-column type="index" label="序号" width="60" />
          <el-table-column prop="name" label="姓名" min-width="120" />
          <el-table-column prop="idNumber" label="证件号码" min-width="160" />
          <el-table-column prop="relationship" label="关系" min-width="100" />
        </el-table>
      </el-tab-pane>

      <!-- Tab 3: 认证记录 -->
      <el-tab-pane label="认证记录" name="verification">
        <el-table :data="verificationRecords" border stripe empty-text="暂无认证记录">
          <el-table-column type="index" label="序号" width="60" />
          <el-table-column prop="verificationType" label="认证类型" min-width="120">
            <template #default="{ row }">
              {{ verificationTypeText(row.verificationType) }}
            </template>
          </el-table-column>
          <el-table-column prop="verificationResult" label="认证结果" min-width="100">
            <template #default="{ row }">
              <el-tag :type="row.verificationResult === 'PASS' ? 'success' : 'danger'" size="small">
                {{ row.verificationResult === 'PASS' ? '通过' : '未通过' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="verifiedBy" label="认证人" min-width="100" />
          <el-table-column prop="verifiedTime" label="认证时间" min-width="170" />
        </el-table>
      </el-tab-pane>

      <!-- Tab 4: 风险评估 -->
      <el-tab-pane label="风险评估" name="risk">
        <!-- 当前风险状态卡片 -->
        <el-card shadow="never" style="margin-bottom: 20px">
          <template #header>
            <div class="risk-card-header">
              <span>当前风险状态</span>
              <el-button
                type="primary"
                :loading="assessing"
                @click="triggerRiskAssessment"
              >
                触发重新评估
              </el-button>
            </div>
          </template>
          <el-row :gutter="20">
            <el-col :span="8">
              <div class="risk-stat">
                <div class="risk-stat-label">风险等级</div>
                <el-tag :type="riskTagType" size="large">{{ riskLevelText }}</el-tag>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="risk-stat">
                <div class="risk-stat-label">风险评分</div>
                <div class="risk-stat-value">{{ customer.riskScore ?? '-' }}</div>
              </div>
            </el-col>
          </el-row>
        </el-card>

        <el-card shadow="never" class="ai-risk-card" v-loading="aiRiskLoading">
          <template #header>
            <div class="risk-card-header">
              <div>
                <span>AI辅助风险识别</span>
                <span class="section-subtitle">基于身份、交易、名单、预警、案件和图谱特征的可解释评分</span>
              </div>
              <el-tag :type="aiRiskTagType(aiRisk?.riskLevel)" size="large">
                {{ aiRiskLevelText(aiRisk?.riskLevel) }}
              </el-tag>
            </div>
          </template>
          <div v-if="aiRisk" class="ai-risk-grid">
            <div class="ai-risk-score">
              <el-progress
                type="dashboard"
                :percentage="aiRisk.score || 0"
                :color="aiRiskProgressColor(aiRisk.score || 0)"
              />
              <div class="ai-risk-score-meta">
                <strong>{{ aiRisk.score ?? 0 }}</strong>
                <span>置信度 {{ aiRisk.confidence ?? 0 }}%</span>
                <span>v{{ aiRisk.modelVersion || '1.0.0' }}</span>
              </div>
            </div>
            <div class="ai-risk-content">
              <div class="ai-risk-factors">
                <div
                  v-for="factor in topAiRiskFactors"
                  :key="factor.factorCode"
                  class="ai-risk-factor"
                >
                  <div class="ai-risk-factor-main">
                    <span>{{ factor.factorName }}</span>
                    <strong>+{{ factor.contribution }}</strong>
                  </div>
                  <p>{{ factor.evidence }}</p>
                </div>
              </div>
              <div class="ai-risk-suggestions">
                <div v-for="item in aiRisk.recommendations || []" :key="item" class="ai-risk-suggestion">
                  {{ item }}
                </div>
              </div>
              <div v-if="aiRiskHistory.length" class="ai-risk-history">
                <div class="ai-risk-history-title">最近评分记录</div>
                <div v-for="record in aiRiskHistory" :key="record.id" class="ai-risk-history-item">
                  <span>{{ formatAiRiskTime(record.scoredAt) }}</span>
                  <strong>{{ record.score }}</strong>
                  <em>{{ aiRiskLevelText(record.riskLevel) }} · v{{ record.modelVersion || '-' }}</em>
                </div>
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无AI风险评分" :image-size="72" />
        </el-card>

        <!-- 历史变更记录 -->
        <el-divider content-position="left">历史变更记录</el-divider>
        <el-table :data="riskRatingLogs" border stripe empty-text="暂无风险评级变更记录">
          <el-table-column type="index" label="序号" width="60" />
          <el-table-column prop="oldLevel" label="原等级" min-width="100">
            <template #default="{ row }">
              <el-tag v-if="riskLogOldLevel(row)" :type="levelTagType(riskLogOldLevel(row))" size="small">
                {{ levelText(riskLogOldLevel(row)) }}
              </el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="newLevel" label="新等级" min-width="100">
            <template #default="{ row }">
              <el-tag :type="levelTagType(riskLogNewLevel(row))" size="small">
                {{ levelText(riskLogNewLevel(row)) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="评分" min-width="80">
            <template #default="{ row }">{{ row.newRiskScore ?? row.score ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="变更原因" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ row.changeReason || row.reason || '-' }}</template>
          </el-table-column>
          <el-table-column label="变更时间" min-width="170">
            <template #default="{ row }">{{ row.changedTime || row.createdTime || '-' }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

type EchartsModule = typeof import('echarts/core')
type EChartInstance = import('echarts/core').ECharts

const route = useRoute()
const customerId = computed(() => route.params.id as string)

const activeTab = ref('basic')
const loading = ref(false)
const assessing = ref(false)
const profileRadarChart = ref<HTMLElement | null>(null)
const relationshipGraphChart = ref<HTMLElement | null>(null)
const customer360 = ref<any>({})
const relationshipGraph = ref<any>({ nodes: [], links: [], summary: {}, insights: [] })
const relationshipLoading = ref(false)
const aiRisk = ref<any>(null)
const aiRiskLoading = ref(false)
const aiRiskHistory = ref<any[]>([])
let radarChart: EChartInstance | null = null
let relationChart: EChartInstance | null = null
let echartsModule: EchartsModule | null = null
let resizeHandler: (() => void) | null = null
let isMounted = false

// 页面基础数据：客户主档、360视图、AI评分和关系图谱分开保存，
// 便于详情、画像、图谱三个区域独立刷新。
const customer = ref<any>({})
const beneficialOwners = ref<any[]>([])
const verificationRecords = ref<any[]>([])
const riskRatingLogs = ref<any[]>([])

// 展示层枚举映射：后端保留稳定代码值，前端负责转换成业务人员可读文本。
const riskTagType = computed(() => {
  const map: Record<string, string> = { LOW: 'success', MEDIUM: 'warning', HIGH: 'danger', CRITICAL: 'danger' }
  return (map[customer.value.riskLevel] || 'info') as any
})

const riskLevelText = computed(() => {
  const map: Record<string, string> = { LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险', CRITICAL: '极高风险' }
  return map[customer.value.riskLevel] || customer.value.riskLevel || '-'
})

const customerTypeText = computed(() => {
  const map: Record<string, string> = { INDIVIDUAL: '个人', CORPORATE: '法人' }
  return map[customer.value.customerType] || customer.value.customerType || '-'
})

const idTypeText = computed(() => {
  const map: Record<string, string> = {
    ID_CARD: '身份证',
    PASSPORT: '护照',
    BUSINESS_LICENSE: '营业执照',
    OTHER: '其他'
  }
  return map[customer.value.idType] || customer.value.idType || '-'
})

const kycStatusText = computed(() => {
  const map: Record<string, string> = {
    NOT_STARTED: '未开始',
    IN_PROGRESS: '进行中',
    COMPLETED: '已完成',
    EXPIRED: '已过期'
  }
  return map[customer.value.kycStatus] || customer.value.kycStatus || '-'
})

const statusText = computed(() => {
  const map: Record<string, string> = { ACTIVE: '正常', INACTIVE: '停用', SUSPENDED: '冻结' }
  return map[customer.value.status] || customer.value.status || '-'
})

const profileDimensions = computed(() => {
  // 客户画像雷达图不是新的自动评级模型，而是把已有 AI 分、KYC、名单、
  // 交易预警和受益关系归一化到 0-100，辅助人工快速发现风险来源。
  const riskScore = clampScore(aiRisk.value?.score ?? customer.value.riskScore ?? riskLevelScore(customer.value.riskLevel))
  const identitySensitivity = clampScore(
    (customer.value.isSanctioned ? 100 : 0)
    || (customer.value.isPep ? 82 : 0)
    || (customer.value.customerType === 'CORPORATE' ? 56 : 36)
  )
  const kycCompleteness = clampScore(100 - calculateKycCompleteness())
  const listExposure = clampScore(customer.value.isSanctioned ? 100 : customer.value.isPep ? 68 : 20)
  const transactionAttention = clampScore((customer360.value.alertCount || 0) * 18 + riskScore * 0.35)
  const relationComplexity = clampScore(
    customer.value.customerType === 'CORPORATE'
      ? 46 + beneficialOwners.value.length * 16
      : 22 + beneficialOwners.value.length * 12
  )

  return [
    {
      name: '综合风险',
      value: riskScore,
      color: profileColor(riskScore),
      description: `当前风险评分 ${riskScore}，由客户评级结果归一化展示`
    },
    {
      name: '身份敏感度',
      value: identitySensitivity,
      color: profileColor(identitySensitivity),
      description: customer.value.isSanctioned
        ? '客户存在制裁名单命中标记'
        : customer.value.isPep
          ? '客户具备 PEP 或敏感身份特征'
          : '基于客户类型与身份属性评估'
    },
    {
      name: '资料缺口',
      value: kycCompleteness,
      color: profileColor(kycCompleteness),
      description: `KYC 资料完整度约 ${100 - kycCompleteness}%`
    },
    {
      name: '名单暴露',
      value: listExposure,
      color: profileColor(listExposure),
      description: customer.value.isSanctioned
        ? '已标记为名单命中客户'
        : customer.value.isPep
          ? '需保持 PEP 持续关注'
          : '当前未见明显名单暴露'
    },
    {
      name: '交易关注',
      value: transactionAttention,
      color: profileColor(transactionAttention),
      description: `关联预警 ${customer360.value.alertCount ?? 0} 条，结合综合风险估算`
    },
    {
      name: '关系复杂度',
      value: relationComplexity,
      color: profileColor(relationComplexity),
      description: `受益所有人 ${beneficialOwners.value.length} 位，结合客户类型评估`
    }
  ]
})

const profileStats = computed(() => [
  { label: '客户类型', value: customerTypeText.value },
  { label: '风险等级', value: riskLevelText.value },
  { label: 'AI风险分', value: aiRisk.value ? `${aiRisk.value.score ?? 0}` : '-' },
  { label: '关联预警', value: `${customer360.value.alertCount ?? 0} 条` }
])

const topAiRiskFactors = computed(() => {
  return Array.isArray(aiRisk.value?.factors) ? aiRisk.value.factors.slice(0, 5) : []
})

const relationshipHasData = computed(() => {
  return Array.isArray(relationshipGraph.value?.nodes) && relationshipGraph.value.nodes.length > 0
})

const relationshipInsights = computed(() => {
  return Array.isArray(relationshipGraph.value?.insights) ? relationshipGraph.value.insights : []
})

const relationshipSummaryCards = computed(() => {
  const summary = relationshipGraph.value?.summary || {}
  return [
    {
      label: '图谱规模',
      value: `${summary.nodeCount ?? 0} / ${summary.linkCount ?? 0}`,
      note: '节点 / 关系'
    },
    {
      label: '保单产品',
      value: `${summary.policyCount ?? 0} / ${summary.productCount ?? 0}`,
      note: '保单 / 产品'
    },
    {
      label: '交易金额',
      value: formatMoney(summary.totalTransactionAmount),
      note: `${summary.transactionCount ?? 0} 笔交易`
    },
    {
      label: '风险处置',
      value: `${summary.alertCount ?? 0} / ${summary.caseCount ?? 0} / ${summary.strReportCount ?? 0}`,
      note: '预警 / 案件 / STR'
    }
  ]
})

const relationshipLegend = [
  { name: '客户', color: '#2563eb' },
  { name: '受益人', color: '#0f766e' },
  { name: '保单', color: '#4f46e5' },
  { name: '产品', color: '#7c3aed' },
  { name: '交易', color: '#d97706' },
  { name: '预警', color: '#dc2626' },
  { name: '案件', color: '#ea580c' },
  { name: 'STR', color: '#be123c' },
  { name: 'PEP/制裁名单', color: '#c026d3' },
  { name: '交易对手', color: '#475569' }
]

// Helpers
function levelTagType(level: string): string {
  const map: Record<string, string> = { LOW: 'success', MEDIUM: 'warning', HIGH: 'danger', CRITICAL: 'danger' }
  return (map[level] || 'info') as any
}

function levelText(level: string): string {
  const map: Record<string, string> = { LOW: '低', MEDIUM: '中', HIGH: '高', CRITICAL: '极高' }
  return map[level] || level || '-'
}

function verificationTypeText(type: string): string {
  const map: Record<string, string> = {
    ID_CARD: '身份证验证',
    FACE: '人脸识别',
    BANK_CARD: '银行卡验证',
    PHONE: '手机号验证',
    ADDRESS: '地址验证'
  }
  return map[type] || type || '-'
}

function riskLogOldLevel(row: any): string {
  return row.oldRiskLevel || row.oldLevel || ''
}

function riskLogNewLevel(row: any): string {
  return row.newRiskLevel || row.newLevel || ''
}

function clampScore(value: unknown): number {
  const num = Number(value ?? 0)
  if (Number.isNaN(num)) return 0
  return Math.max(0, Math.min(100, Math.round(num)))
}

function riskLevelScore(level: string): number {
  const map: Record<string, number> = { LOW: 25, MEDIUM: 55, HIGH: 82, CRITICAL: 96 }
  return map[level] ?? 0
}

function calculateKycCompleteness(): number {
  const fields = [
    customer.value.name,
    customer.value.idType,
    customer.value.idNumber,
    customer.value.phone,
    customer.value.email,
    customer.value.address || customer.value.residenceAddress,
    customer.value.occupation || customer.value.enterpriseType,
    customer.value.employer || customer.value.businessScope
  ]
  const filled = fields.filter(Boolean).length
  const base = Math.round((filled / fields.length) * 100)
  const statusBonus = ['COMPLETE', 'COMPLETED'].includes(customer.value.kycStatus) ? 10 : 0
  return clampScore(base + statusBonus)
}

function profileColor(value: number) {
  if (value >= 80) return '#dc2626'
  if (value >= 60) return '#d97706'
  if (value >= 40) return '#2563eb'
  return '#059669'
}

function aiRiskTagType(level: string): string {
  const map: Record<string, string> = { LOW: 'success', MEDIUM: 'warning', HIGH: 'danger', CRITICAL: 'danger' }
  return (map[level] || 'info') as any
}

function aiRiskLevelText(level: string): string {
  const map: Record<string, string> = { LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险', CRITICAL: '极高风险' }
  return map[level] || level || '待评分'
}

function aiRiskProgressColor(score: number) {
  if (score >= 85) return '#991b1b'
  if (score >= 65) return '#dc2626'
  if (score >= 35) return '#d97706'
  return '#059669'
}

function formatAiRiskTime(value: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-'
}

function cssVar(name: string, fallback: string) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback
}

function chartTheme() {
  return {
    accent: cssVar('--accent-primary', '#2563eb'),
    danger: '#dc2626',
    warning: '#d97706',
    success: '#059669',
    text: cssVar('--text-tertiary', '#64748b'),
    primaryText: cssVar('--text-primary', '#1f2937'),
    border: cssVar('--border-subtle', '#e5e7eb')
  }
}

function ensureResizeHandler() {
  if (resizeHandler) return
  resizeHandler = () => {
    radarChart?.resize()
    relationChart?.resize()
  }
  window.addEventListener('resize', resizeHandler)
}

function waitForPaint() {
  return new Promise<void>(resolve => {
    requestAnimationFrame(() => requestAnimationFrame(() => resolve()))
  })
}

function wait(ms: number) {
  return new Promise<void>(resolve => window.setTimeout(resolve, ms))
}

async function waitForChartContainer(container: HTMLElement) {
  for (let attempt = 0; attempt < 10; attempt += 1) {
    await nextTick()
    await waitForPaint()
    if (!isMounted || !container.isConnected) {
      return false
    }
    if (container.clientWidth > 0 && container.clientHeight > 0) {
      return true
    }
    await wait(50)
  }
  return false
}

async function getEcharts() {
  if (!echartsModule) {
    const [core, charts, components, renderers] = await Promise.all([
      import('echarts/core'),
      import('echarts/charts'),
      import('echarts/components'),
      import('echarts/renderers')
    ])
    core.use([
      charts.RadarChart,
      charts.GraphChart,
      components.RadarComponent,
      components.TooltipComponent,
      components.LegendComponent,
      renderers.CanvasRenderer
    ])
    echartsModule = core
  }
  return echartsModule
}

async function renderProfileRadar() {
  await nextTick()
  const container = profileRadarChart.value
  if (!isMounted || !container || !container.isConnected) return
  if (!(await waitForChartContainer(container))) return
  const echarts = await getEcharts()
  const theme = chartTheme()
  if (!radarChart) {
    radarChart = echarts.init(container)
    ensureResizeHandler()
  }

  const dimensions = profileDimensions.value
  // 雷达图每个维度都有解释文案，避免用户把单一维度误读为最终风险等级。
  radarChart.setOption({
    color: [theme.accent],
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(15, 23, 42, 0.92)',
      borderWidth: 0,
      textStyle: { color: '#fff' },
      formatter: () => dimensions.map(item => `${item.name}: ${item.value}`).join('<br/>')
    },
    radar: {
      radius: '68%',
      center: ['50%', '52%'],
      splitNumber: 4,
      indicator: dimensions.map(item => ({ name: item.name, max: 100 })),
      axisName: {
        color: theme.primaryText,
        fontSize: 12,
        fontWeight: 600
      },
      axisLine: { lineStyle: { color: theme.border } },
      splitLine: { lineStyle: { color: theme.border } },
      splitArea: {
        areaStyle: { color: ['rgba(37, 99, 235, 0.04)', 'rgba(37, 99, 235, 0.08)'] }
      }
    },
    series: [{
      name: '客户画像',
      type: 'radar',
      data: [{
        value: dimensions.map(item => item.value),
        name: customer.value.name || '客户画像',
        symbolSize: 5,
        lineStyle: { width: 3, color: theme.accent },
        areaStyle: { color: 'rgba(37, 99, 235, 0.18)' },
        itemStyle: { color: theme.accent }
      }]
    }]
  })
  radarChart.resize()
}

function relationshipCategoryIndex(category: string, type: string) {
  const categoryName = category || type
  const index = relationshipLegend.findIndex(item => item.name === categoryName)
  if (index >= 0) return index
  if (type === 'WATCHLIST' || type === 'PEP' || type === 'SANCTION') return 8
  if (type === 'COUNTERPARTY') return 9
  return 0
}

function relationshipNodeSize(node: any) {
  if (node.type === 'CUSTOMER') return 64
  if (node.type === 'ALERT' || node.type === 'CASE' || node.type === 'STR') return 48
  if (node.type === 'TRANSACTION') {
    const amount = Number(node.amount || 0)
    return Math.min(56, 30 + Math.log10(amount + 1) * 5)
  }
  if (node.type === 'WATCHLIST' || node.type === 'SANCTION' || node.type === 'PEP') return 50
  return 40
}

function riskLineColor(riskLevel: string) {
  if (riskLevel === 'CRITICAL') return '#be123c'
  if (riskLevel === 'HIGH') return '#dc2626'
  if (riskLevel === 'MEDIUM') return '#d97706'
  return '#94a3b8'
}

function formatMoney(value: unknown) {
  const amount = Number(value || 0)
  if (!Number.isFinite(amount) || amount === 0) return '¥0.00'
  if (Math.abs(amount) >= 10000) {
    return `¥${(amount / 10000).toLocaleString('zh-CN', { maximumFractionDigits: 1 })}万`
  }
  return `¥${amount.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function escapeHtml(value: unknown) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function truncateLabel(value: string, max = 14) {
  const text = String(value || '')
  return text.length > max ? `${text.slice(0, max)}...` : text
}

async function renderRelationshipGraph() {
  await nextTick()
  const container = relationshipGraphChart.value
  if (!isMounted || activeTab.value !== 'relationship' || !container || !container.isConnected) return
  const rect = container.getBoundingClientRect()
  if (rect.width === 0 || rect.height === 0) return
  const nodes = Array.isArray(relationshipGraph.value?.nodes) ? relationshipGraph.value.nodes : []
  const links = Array.isArray(relationshipGraph.value?.links) ? relationshipGraph.value.links : []
  const echarts = await getEcharts()
  if (!relationChart) {
    relationChart = echarts.init(container)
    ensureResizeHandler()
  }

  // 后端返回的是业务图谱语义，前端在这里补足 ECharts 所需的分类、大小和高风险高亮。
  const data = nodes.map((node: any) => {
    const categoryIndex = relationshipCategoryIndex(node.category, node.type)
    return {
      id: node.id,
      name: node.label,
      category: categoryIndex,
      symbolSize: relationshipNodeSize(node),
      value: node.amount || node.riskScore || 0,
      draggable: true,
      itemStyle: {
        color: relationshipLegend[categoryIndex]?.color || '#2563eb',
        borderColor: '#fff',
        borderWidth: 2,
        shadowBlur: node.riskLevel === 'CRITICAL' || node.riskLevel === 'HIGH' ? 12 : 4,
        shadowColor: node.riskLevel === 'CRITICAL' || node.riskLevel === 'HIGH'
          ? 'rgba(220, 38, 38, 0.28)'
          : 'rgba(15, 23, 42, 0.12)'
      },
      raw: node
    }
  })

  const graphLinks = links.map((link: any) => ({
    source: link.source,
    target: link.target,
    value: link.value || 0,
    label: { formatter: link.label || '' },
    lineStyle: {
      color: riskLineColor(link.riskLevel),
      width: link.riskLevel === 'CRITICAL' || link.riskLevel === 'HIGH' ? 2.6 : 1.6,
      curveness: 0.12,
      opacity: 0.78
    },
    raw: link
  }))

  relationChart.setOption({
    tooltip: {
      trigger: 'item',
      confine: true,
      backgroundColor: 'rgba(15, 23, 42, 0.94)',
      borderWidth: 0,
      textStyle: { color: '#fff' },
      formatter(params: any) {
        const raw = params.data?.raw || {}
        if (params.dataType === 'edge') {
          return [
            `<strong>${escapeHtml(raw.label || '关系')}</strong>`,
            raw.value ? `数值：${escapeHtml(formatMoney(raw.value))}` : '',
            raw.riskLevel ? `风险等级：${escapeHtml(raw.riskLevel)}` : ''
          ].filter(Boolean).join('<br/>')
        }
        return [
          `<strong>${escapeHtml(raw.label || raw.name || '节点')}</strong>`,
          `类型：${escapeHtml(raw.category || raw.type || '-')}`,
          raw.status ? `状态：${escapeHtml(raw.status)}` : '',
          raw.riskLevel ? `风险等级：${escapeHtml(raw.riskLevel)}` : '',
          raw.riskScore != null ? `风险分：${escapeHtml(raw.riskScore)}` : '',
          raw.amount ? `金额/分值：${escapeHtml(formatMoney(raw.amount))}` : ''
        ].filter(Boolean).join('<br/>')
      }
    },
    legend: {
      show: false,
      data: relationshipLegend.map(item => item.name)
    },
    series: [{
      type: 'graph',
      layout: 'force',
      roam: true,
      data,
      links: graphLinks,
      categories: relationshipLegend.map(item => ({ name: item.name })),
      edgeSymbol: ['none', 'arrow'],
      edgeSymbolSize: [0, 8],
      force: {
        repulsion: 320,
        gravity: 0.08,
        edgeLength: [92, 190],
        friction: 0.45
      },
      label: {
        show: true,
        position: 'right',
        color: '#1f2937',
        fontSize: 12,
        fontWeight: 600,
        backgroundColor: 'rgba(255, 255, 255, 0.88)',
        borderColor: 'rgba(148, 163, 184, 0.28)',
        borderWidth: 1,
        borderRadius: 4,
        padding: [2, 5],
        formatter(params: any) {
          return truncateLabel(params.name)
        }
      },
      emphasis: {
        focus: 'adjacency',
        lineStyle: { width: 3.5 }
      }
    }]
  }, true)
  relationChart.resize()
}

// API 调用：详情页首次加载并行取客户主档和 360 视图，AI评分和关系图谱单独刷新。
async function fetchCustomerDetail() {
  loading.value = true
  try {
    // 并行请求基本信息和360视图
    const [detailRes, view360Res]: any[] = await Promise.all([
      request.get(`/kyc/customers/${customerId.value}`),
      request.get(`/kyc/customers/${customerId.value}/360`)
    ])

    // 基本信息
    customer.value = detailRes.data || view360Res.data?.customer || {}

    // 360视图数据
    const data = view360Res.data || {}
    customer360.value = data
    // 如果360返回了customer且比detail更完整，用360的覆盖
    if (data.customer && Object.keys(data.customer).length > Object.keys(customer.value).length) {
      customer.value = data.customer
    }
    beneficialOwners.value = data.beneficialOwners || []
    verificationRecords.value = data.verificationHistory || data.verificationRecords || []
    riskRatingLogs.value = data.riskRatingHistory || data.riskRatingLogs || []
    await renderProfileRadar()
    await Promise.allSettled([
      fetchAiRiskScore(),
      fetchRelationshipGraph()
    ])
  } catch (e: any) {
    ElMessage.error('获取客户信息失败：' + (e.message || '未知错误'))
  } finally {
    loading.value = false
    if (activeTab.value === 'basic') {
      await renderProfileRadar()
    }
  }
}

async function fetchAiRiskScore() {
  aiRiskLoading.value = true
  try {
    const res: any = await request.get(`/ai/risk/customers/${customerId.value}`)
    aiRisk.value = res.data || null
    const historyRes: any = await request.get(`/ai/risk/customers/${customerId.value}/history`, { params: { limit: 5 } })
    aiRiskHistory.value = historyRes.data || []
  } catch (e) {
    aiRisk.value = null
    aiRiskHistory.value = []
  } finally {
    aiRiskLoading.value = false
  }
}

async function fetchRelationshipGraph() {
  relationshipLoading.value = true
  try {
    const res: any = await request.get(`/kyc/customers/${customerId.value}/relationship-graph`)
    relationshipGraph.value = res.data || { nodes: [], links: [], summary: {}, insights: [] }
    await renderRelationshipGraph()
  } catch (e: any) {
    relationshipGraph.value = { nodes: [], links: [], summary: {}, insights: [] }
    ElMessage.error('获取客户关系图谱失败：' + (e.message || '未知错误'))
  } finally {
    relationshipLoading.value = false
  }
}

async function triggerRiskAssessment() {
  assessing.value = true
  try {
    await request.post(`/kyc/customers/${customerId.value}/risk-assessment`)
    ElMessage.success('风险评估已触发，请稍后刷新查看结果')
    // 刷新数据
    await fetchCustomerDetail()
  } catch (e: any) {
    ElMessage.error('触发风险评估失败：' + (e.message || '未知错误'))
  } finally {
    assessing.value = false
  }
}

watch(activeTab, (tab) => {
  if (tab === 'basic') {
    renderProfileRadar()
  }
  if (tab === 'relationship') {
    renderRelationshipGraph()
  }
})

watch(profileDimensions, () => {
  renderProfileRadar()
})

watch(relationshipGraph, () => {
  if (activeTab.value === 'relationship') {
    renderRelationshipGraph()
  }
})

onMounted(() => {
  isMounted = true
  fetchCustomerDetail()
})

onUnmounted(() => {
  isMounted = false
  if (resizeHandler) {
    window.removeEventListener('resize', resizeHandler)
  }
  radarChart?.dispose()
  relationChart?.dispose()
})
</script>

<style scoped>
.customer-detail {
  padding: 20px;
}

.profile-section {
  margin-bottom: 16px;
  row-gap: 16px;
}

.profile-card {
  height: 100%;
  border: 1px solid var(--border-subtle, #e5e7eb);
}

.profile-card :deep(.el-card__header) {
  padding: 14px 16px;
}

.profile-card :deep(.el-card__body) {
  padding: 16px;
}

.section-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  color: var(--text-primary, #1f2937);
  font-size: 15px;
  font-weight: 650;
}

.section-subtitle {
  color: var(--text-tertiary, #64748b);
  font-size: 12px;
  font-weight: 500;
}

.profile-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}

.profile-stat {
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--border-subtle, #e5e7eb);
  border-radius: 8px;
  background: #f8fafc;
}

.profile-stat-label {
  display: block;
  margin-bottom: 6px;
  color: var(--text-tertiary, #64748b);
  font-size: 12px;
}

.profile-stat strong {
  display: block;
  overflow: hidden;
  color: var(--text-primary, #1f2937);
  font-size: 16px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-dimensions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 16px;
}

.dimension-row {
  min-width: 0;
}

.dimension-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
  color: var(--text-primary, #1f2937);
  font-size: 13px;
  font-weight: 620;
}

.dimension-desc {
  margin-top: 6px;
  overflow: hidden;
  color: var(--text-tertiary, #64748b);
  font-size: 12px;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chart-card :deep(.el-card__body) {
  min-height: 304px;
}

.profile-radar {
  width: 100%;
  height: 300px;
}

.relationship-panel {
  border: 1px solid var(--border-subtle, #e5e7eb);
  border-radius: 8px;
  background: #fff;
}

.relationship-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
  border-bottom: 1px solid var(--border-subtle, #e5e7eb);
}

.relationship-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  padding: 16px;
  border-bottom: 1px solid var(--border-subtle, #e5e7eb);
  background: #f8fafc;
}

.relationship-stat {
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--border-subtle, #e5e7eb);
  border-radius: 8px;
  background: #fff;
}

.relationship-stat span,
.relationship-stat em {
  display: block;
  overflow: hidden;
  color: var(--text-tertiary, #64748b);
  font-size: 12px;
  font-style: normal;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.relationship-stat strong {
  display: block;
  margin: 4px 0;
  overflow: hidden;
  color: var(--text-primary, #1f2937);
  font-size: 20px;
  font-weight: 750;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.relationship-content {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  min-height: 520px;
}

.relationship-graph {
  position: relative;
  min-width: 0;
  height: 520px;
  border-right: 1px solid var(--border-subtle, #e5e7eb);
}

.relationship-side {
  min-width: 0;
  padding: 16px;
  background: #fff;
}

.relationship-side-title {
  margin-bottom: 10px;
  color: var(--text-primary, #1f2937);
  font-size: 14px;
  font-weight: 700;
}

.relationship-insight-list {
  display: grid;
  gap: 10px;
}

.relationship-insight {
  padding: 10px 12px;
  border-left: 3px solid var(--accent-primary, #2563eb);
  border-radius: 6px;
  background: #f8fafc;
  color: var(--text-secondary, #475569);
  font-size: 13px;
  line-height: 1.55;
}

.legend-title {
  margin-top: 18px;
}

.relationship-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 8px;
  border: 1px solid var(--border-subtle, #e5e7eb);
  border-radius: 6px;
  color: var(--text-secondary, #475569);
  font-size: 12px;
  line-height: 1;
}

.legend-item i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.header-content {
  display: flex;
  align-items: center;
}

.customer-name {
  font-size: 18px;
  font-weight: 600;
}

.risk-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.risk-stat {
  text-align: center;
  padding: 16px 0;
}

.risk-stat-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}

.risk-stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
}

.ai-risk-card {
  margin-bottom: 20px;
  border: 1px solid var(--border-subtle, #e5e7eb);
}

.ai-risk-card :deep(.el-card__header) {
  padding: 14px 16px;
}

.ai-risk-grid {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 18px;
  align-items: center;
}

.ai-risk-score {
  position: relative;
  display: grid;
  justify-items: center;
}

.ai-risk-score-meta {
  position: absolute;
  top: 52px;
  display: grid;
  justify-items: center;
  gap: 2px;
  pointer-events: none;
}

.ai-risk-score-meta strong {
  color: #111827;
  font-size: 28px;
  line-height: 1;
}

.ai-risk-score-meta span {
  color: #64748b;
  font-size: 12px;
}

.ai-risk-content {
  min-width: 0;
}

.ai-risk-factors {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.ai-risk-factor {
  min-width: 0;
  padding: 10px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #f8fafc;
}

.ai-risk-factor-main {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 6px;
  color: #111827;
  font-size: 13px;
  font-weight: 700;
}

.ai-risk-factor-main strong {
  color: #dc2626;
}

.ai-risk-factor p {
  margin: 0;
  color: #475569;
  font-size: 12px;
  line-height: 1.45;
}

.ai-risk-suggestions {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}

.ai-risk-suggestion {
  padding: 9px 11px;
  border-left: 3px solid #2563eb;
  border-radius: 4px;
  background: #eff6ff;
  color: #334155;
  font-size: 12px;
  line-height: 1.5;
}

.ai-risk-history {
  display: grid;
  gap: 6px;
  margin-top: 12px;
}

.ai-risk-history-title {
  color: #334155;
  font-size: 12px;
  font-weight: 700;
}

.ai-risk-history-item {
  display: grid;
  grid-template-columns: 120px 48px minmax(0, 1fr);
  gap: 8px;
  align-items: center;
  padding: 7px 9px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
  color: #64748b;
  font-size: 12px;
}

.ai-risk-history-item strong {
  color: #111827;
  font-size: 14px;
}

.ai-risk-history-item em {
  color: #475569;
  font-style: normal;
}

@media (max-width: 1200px) {
  .profile-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .relationship-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .relationship-content {
    grid-template-columns: 1fr;
  }

  .ai-risk-grid {
    grid-template-columns: 1fr;
  }

  .relationship-graph {
    border-right: 0;
    border-bottom: 1px solid var(--border-subtle, #e5e7eb);
  }
}

@media (max-width: 768px) {
  .customer-detail {
    padding: 12px;
  }

  .section-title {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }

  .profile-summary,
  .profile-dimensions,
  .relationship-summary,
  .ai-risk-factors {
    grid-template-columns: 1fr;
  }

  .relationship-header {
    align-items: stretch;
    flex-direction: column;
  }

  .dimension-desc {
    white-space: normal;
  }

  .profile-radar {
    height: 280px;
  }

  .relationship-graph {
    height: 440px;
  }
}
</style>
