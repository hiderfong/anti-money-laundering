<template>
  <div class="dashboard">
    <!-- 统计卡片 -->
    <el-row :gutter="16" style="margin-bottom: 20px">
      <el-col :span="6" v-for="card in statCards" :key="card.title">
        <el-card shadow="hover">
          <div class="stat-card">
            <div>
              <div class="stat-value">{{ card.value }}</div>
              <div class="stat-title">{{ card.title }}</div>
            </div>
            <el-icon :size="40" :color="card.color"><component :is="card.icon" /></el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区域 -->
    <el-row :gutter="16" style="margin-bottom: 20px">
      <el-col :span="14">
        <el-card header="预警趋势（近30天）">
          <div ref="trendChart" style="height: 300px"></div>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card header="预警类型分布">
          <div ref="pieChart" style="height: 300px"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 最近预警 -->
    <el-card header="最近预警">
      <el-table :data="recentAlerts" stripe v-loading="loading">
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
import { ref, reactive, onMounted } from 'vue'
import * as echarts from 'echarts'
import request from '@/utils/request'

const loading = ref(false)
const trendChart = ref()
const pieChart = ref()
const recentAlerts = ref<any[]>([])

const statCards = reactive([
  { title: '客户总数', value: 0, icon: 'User', color: '#409eff' },
  { title: '活跃预警', value: 0, icon: 'Bell', color: '#e6a23c' },
  { title: '进行中案件', value: 0, icon: 'FolderOpened', color: '#f56c6c' },
  { title: '待报送报告', value: 0, icon: 'Document', color: '#67c23a' }
])

function riskTagType(level: string) {
  return { LOW: 'success', MEDIUM: 'warning', HIGH: 'danger', CRITICAL: 'danger' }[level] || 'info'
}

function statusTagType(status: string) {
  return { NEW: 'danger', ASSIGNED: 'warning', PROCESSING: '', CONFIRMED: 'success', EXCLUDED: 'info' }[status] || 'info'
}

onMounted(async () => {
  // 加载仪表盘数据
  try {
    const res: any = await request.get('/dashboard/overview')
    const data = res.data
    statCards[0].value = data.totalCustomers || 0
    statCards[1].value = data.activeAlerts || 0
    statCards[2].value = data.openCases || 0
    statCards[3].value = data.pendingReports || 0
  } catch (e) { /* 使用默认值 */ }

  // 加载预警趋势
  try {
    const res: any = await request.get('/dashboard/alert-trend', { params: { days: 30 } })
    initTrendChart(res.data || [])
  } catch (e) { initTrendChart([]) }

  // 加载预警统计
  try {
    const res: any = await request.get('/dashboard/alert-statistics')
    initPieChart(res.data || [])
  } catch (e) { initPieChart([]) }

  // 加载最近预警
  try {
    const res: any = await request.get('/alerts/page', { params: { page: 1, size: 10 } })
    recentAlerts.value = res.data?.list || []
  } catch (e) { recentAlerts.value = [] }
})

function initTrendChart(data: any[]) {
  const chart = echarts.init(trendChart.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: data.map((d: any) => d.date) },
    yAxis: { type: 'value' },
    series: [{ name: '预警数', type: 'line', data: data.map((d: any) => d.count), smooth: true, areaStyle: { opacity: 0.3 } }]
  })
  window.addEventListener('resize', () => chart.resize())
}

function initPieChart(data: any[]) {
  const chart = echarts.init(pieChart.value)
  chart.setOption({
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie', radius: '60%',
      data: data.map((d: any) => ({ name: d.alertType, value: d.count })),
      emphasis: { itemStyle: { shadowBlur: 10 } }
    }]
  })
  window.addEventListener('resize', () => chart.resize())
}
</script>

<style scoped>
.stat-card { display: flex; justify-content: space-between; align-items: center; }
.stat-value { font-size: 28px; font-weight: bold; color: #303133; }
.stat-title { font-size: 14px; color: #909399; margin-top: 4px; }
</style>
