<template>
  <div class="dashboard">
    <section class="dashboard-header">
      <div>
        <h1>仪表盘</h1>
        <div class="dashboard-meta">反洗钱运营态势</div>
      </div>
      <el-tag size="large" type="primary" effect="plain">近 30 天</el-tag>
    </section>

    <el-row :gutter="16" class="stat-grid">
      <el-col v-for="card in statCards" :key="card.title" :xs="24" :sm="12" :lg="6">
        <el-card shadow="never" class="metric-card">
          <div class="metric-content">
            <div>
              <div class="metric-label">{{ card.title }}</div>
              <div class="metric-value">{{ card.value }}</div>
            </div>
            <div class="metric-icon" :style="{ color: card.color, background: card.bg }">
              <el-icon :size="22"><component :is="card.icon" /></el-icon>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="chart-grid">
      <el-col :xs="24" :xl="15">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="panel-title">
              <span>预警趋势（近30天）</span>
              <span class="panel-subtitle">按生成日期汇总</span>
            </div>
          </template>
          <div ref="trendChart" class="chart chart-large"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :xl="9">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="panel-title">
              <span>预警类型分布</span>
              <span class="panel-subtitle">命中类型结构</span>
            </div>
          </template>
          <div ref="pieChart" class="chart chart-small"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="panel-card">
      <template #header>
        <div class="table-header">
          <span>最近预警</span>
          <el-button link type="primary" @click="$router.push('/alerts')">查看全部</el-button>
        </div>
      </template>
      <el-table
        :data="recentAlerts"
        stripe
        v-loading="loading"
        row-key="id"
        empty-text="暂无预警数据"
        :scrollbar-always-on="true"
        style="width: 100%"
      >
        <el-table-column prop="alertNo" label="预警编号" width="180" />
        <el-table-column prop="customerName" label="客户" width="120" />
        <el-table-column prop="alertType" label="类型" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ row.alertType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="riskLevel" label="风险等级" width="100">
          <template #default="{ row }">
            <el-tag :type="riskTagType(row.riskLevel)" size="small">{{ row.riskLevel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" label="生成时间" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref, reactive, onMounted, onUnmounted } from 'vue'
import request from '@/utils/request'

type EchartsModule = typeof import('echarts/core')
type EChartInstance = import('echarts/core').ECharts

const loading = ref(false)
const charts: EChartInstance[] = []
const resizeHandlers: (() => void)[] = []
const trendChart = ref<HTMLElement | null>(null)
const pieChart = ref<HTMLElement | null>(null)
const recentAlerts = ref<any[]>([])
let echartsModule: EchartsModule | null = null
let isMounted = false

const statCards = reactive([
  { title: '客户总数', value: 0, icon: 'User', color: '#2563eb', bg: 'rgba(37, 99, 235, 0.1)' },
  { title: '活跃预警', value: 0, icon: 'Bell', color: '#d97706', bg: 'rgba(217, 119, 6, 0.12)' },
  { title: '进行中案件', value: 0, icon: 'FolderOpened', color: '#dc2626', bg: 'rgba(220, 38, 38, 0.1)' },
  { title: '待报送报告', value: 0, icon: 'Document', color: '#059669', bg: 'rgba(5, 150, 105, 0.11)' }
])

function riskTagType(level: string) {
  return { LOW: 'success', MEDIUM: 'warning', HIGH: 'danger', CRITICAL: 'danger' }[level] || 'info'
}

function statusTagType(status: string) {
  return { NEW: 'danger', ASSIGNED: 'warning', PROCESSING: '', CONFIRMED: 'success', EXCLUDED: 'info' }[status] || 'info'
}

function normalizeTrendData(data: any[]) {
  return data.map((d: any) => ({
    date: d.date || d.day || d.createdDate || '',
    count: Number(d.count ?? d.total ?? 0)
  }))
}

function normalizePieData(data: any[]) {
  return data.map((d: any) => ({
    name: d.alertType || d.name || d.type || '未知类型',
    value: Number(d.count ?? d.value ?? 0)
  }))
}

function toNumber(value: unknown) {
  return Number(value ?? 0)
}

function cssVar(name: string, fallback: string) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback
}

function chartTheme() {
  return {
    accent: cssVar('--accent-primary', '#2563eb'),
    muted: cssVar('--accent-muted', '#0f766e'),
    text: cssVar('--text-tertiary', '#64748b'),
    border: cssVar('--border-subtle', '#e5e7eb')
  }
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
      charts.LineChart,
      charts.PieChart,
      components.GridComponent,
      components.TooltipComponent,
      components.LegendComponent,
      renderers.CanvasRenderer
    ])
    echartsModule = core
  }
  return echartsModule
}

onMounted(async () => {
  isMounted = true
  loading.value = true
  try {
    const res: any = await request.get('/dashboard/overview')
    const data = res.data || {}
    const activeAlerts = toNumber(data.activeAlerts ?? (toNumber(data.newAlerts) + toNumber(data.processingAlerts)))
    const valuesByTitle: Record<string, number> = {
      客户总数: toNumber(data.totalCustomers),
      活跃预警: activeAlerts,
      进行中案件: toNumber(data.openCases),
      待报送报告: toNumber(data.pendingReports)
    }
    statCards.forEach((card) => {
      card.value = valuesByTitle[card.title] ?? 0
    })
  } catch (e) { /* 使用默认值 */ }

  await nextTick()

  try {
    const res: any = await request.get('/dashboard/alert-trend', { params: { days: 30 } })
    await initTrendChart(normalizeTrendData(res.data || []))
  } catch (e) { await initTrendChart([]) }

  try {
    const res: any = await request.get('/dashboard/alert-statistics')
    await initPieChart(normalizePieData(res.data || []))
  } catch (e) { await initPieChart([]) }

  try {
    const res: any = await request.get('/alerts/page', { params: { page: 1, size: 10 } })
    recentAlerts.value = res.data?.list || []
  } catch (e) { recentAlerts.value = [] }

  loading.value = false
})

async function initTrendChart(data: any[]) {
  const container = trendChart.value
  if (!container) return
  const echarts = await getEcharts()
  if (!isMounted || !container.isConnected) return
  const theme = chartTheme()
  const chart = echarts.init(container)
  charts.push(chart)
  chart.setOption({
    color: [theme.accent],
    grid: { left: 36, right: 20, top: 28, bottom: 32 },
    tooltip: { trigger: 'axis', backgroundColor: 'rgba(15, 23, 42, 0.92)', borderWidth: 0, textStyle: { color: '#fff' } },
    xAxis: {
      type: 'category',
      data: data.map((d: any) => d.date),
      boundaryGap: false,
      axisLine: { lineStyle: { color: theme.border } },
      axisLabel: { color: theme.text },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: theme.border } },
      axisLabel: { color: theme.text }
    },
    series: [{
      name: '预警数',
      type: 'line',
      data: data.map((d: any) => d.count),
      smooth: true,
      symbolSize: 6,
      lineStyle: { width: 3 },
      areaStyle: { opacity: 0.12 }
    }]
  })
  const handler = () => chart.resize()
  resizeHandlers.push(handler)
  window.addEventListener('resize', handler)
}

async function initPieChart(data: any[]) {
  const container = pieChart.value
  if (!container) return
  const echarts = await getEcharts()
  if (!isMounted || !container.isConnected) return
  const theme = chartTheme()
  const chart = echarts.init(container)
  charts.push(chart)
  chart.setOption({
    color: [theme.accent, theme.muted, '#d97706', '#dc2626', '#7c3aed'],
    tooltip: { trigger: 'item', backgroundColor: 'rgba(15, 23, 42, 0.92)', borderWidth: 0, textStyle: { color: '#fff' } },
    legend: { bottom: 0, icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: { color: theme.text } },
    series: [{
      type: 'pie',
      radius: ['42%', '66%'],
      center: ['50%', '44%'],
      data,
      label: { color: theme.text },
      emphasis: { scaleSize: 6, itemStyle: { shadowBlur: 10 } }
    }]
  })
  const handler = () => chart.resize()
  resizeHandlers.push(handler)
  window.addEventListener('resize', handler)
}

onUnmounted(() => {
  isMounted = false
  charts.forEach(c => c.dispose())
  resizeHandlers.forEach(h => window.removeEventListener('resize', h))
})
</script>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.dashboard-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.dashboard-header h1 {
  margin: 0;
  color: var(--text-primary);
  font-size: 24px;
  font-weight: 650;
  line-height: 1.25;
}

.dashboard-meta {
  margin-top: 6px;
  color: var(--text-tertiary);
  font-size: 13px;
}

.stat-grid,
.chart-grid {
  row-gap: 16px;
}

.metric-card :deep(.el-card__body) {
  padding: 18px 20px !important;
}

.metric-content,
.stat-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.metric-label {
  color: var(--text-tertiary);
  font-size: 13px;
  font-weight: 520;
}

.metric-value {
  margin-top: 8px;
  color: var(--text-primary);
  font-size: 30px;
  font-weight: 700;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.metric-icon {
  width: 44px;
  height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  flex-shrink: 0;
}

.panel-card {
  height: 100%;
}

.panel-title,
.table-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--text-primary);
  font-weight: 620;
}

.panel-subtitle {
  color: var(--text-tertiary);
  font-size: 12px;
  font-weight: 400;
}

.chart {
  width: 100%;
  min-width: 280px;
}

.chart-large {
  height: 320px;
}

.chart-small {
  height: 320px;
}

@media (max-width: 767px) {
  .dashboard-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .dashboard-header h1 {
    font-size: 22px;
  }

  .chart-large,
  .chart-small {
    height: 260px;
  }
}
</style>
