<script setup lang="ts">
/**
 * 告警管理页面
 * 告警列表查询、处理、分配
 */
import { ref, reactive, onMounted } from 'vue'
import { alertApi } from '@/api/modules'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const searchForm = reactive({ alertType: '', riskLevel: '', status: '', keyword: '' })
const tableData = ref<any[]>([])
const pagination = reactive({ current: 1, size: 10, total: 0 })
const selectedRows = ref<any[]>([])

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await alertApi.pageQuery({ page: pagination.current, size: pagination.size, ...searchForm })
    const data = res.data || res
    tableData.value = data.records || data.list || []
    pagination.total = data.total || 0
  } catch (e) {
    tableData.value = Array.from({ length: 10 }, (_, i) => ({
      id: `ALT-${2000 + i}`, alertType: ['大额交易', '频繁交易', '黑名单命中', '可疑交易', '关联交易'][i % 5],
      riskLevel: ['HIGH', 'MEDIUM', 'LOW'][i % 3], status: ['PENDING', 'INVESTIGATING', 'RESOLVED', 'CLOSED'][i % 4],
      customerName: `客户${i + 1}`, assignee: i < 5 ? '张经理' : '', createdTime: '2024-06-15 10:00:00',
      ruleName: '规则-' + (i + 1)
    }))
    pagination.total = 50
  } finally { loading.value = false }
}

const handleSearch = () => { pagination.current = 1; loadData() }
const handleReset = () => { Object.assign(searchForm, { alertType: '', riskLevel: '', status: '', keyword: '' }); handleSearch() }
const handleSelectionChange = (rows: any[]) => { selectedRows.value = rows }

const riskLevelType = (l: string) => ({ HIGH: 'danger', MEDIUM: 'warning', LOW: 'success' }[l] || 'info') as any
const riskLevelLabel = (l: string) => ({ HIGH: '高', MEDIUM: '中', LOW: '低' }[l] || l)
const statusLabel = (s: string) => ({ PENDING: '待处理', INVESTIGATING: '调查中', RESOLVED: '已解决', CLOSED: '已关闭' }[s] || s)
const statusType = (s: string) => ({ PENDING: 'warning', INVESTIGATING: 'primary', RESOLVED: 'success', CLOSED: 'info' }[s] || 'info') as any

const handleAssign = async (row: any) => {
  ElMessage.info('分配功能开发中')
}

const handleProcess = async (row: any) => {
  try {
    await ElMessageBox.confirm('确认要处理此告警吗？', '处理告警', { type: 'info' })
    await alertApi.process(row.id, { result: 'RESOLVED', comment: '已排查无异常' })
    ElMessage.success('处理成功')
    loadData()
  } catch (e: any) { if (e !== 'cancel') ElMessage.error('处理失败') }
}

const handleBatchProcess = async () => {
  if (!selectedRows.value.length) { ElMessage.warning('请先选择告警'); return }
  ElMessage.info('批量处理功能开发中')
}

onMounted(() => { loadData() })
</script>

<template>
  <div class="alert-list-page">
    <el-card shadow="never" class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="告警编号/客户" clearable style="width: 160px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="告警类型">
          <el-select v-model="searchForm.alertType" placeholder="全部" clearable style="width: 140px">
            <el-option label="大额交易" value="LARGE_TXN" /><el-option label="频繁交易" value="FREQUENT" />
            <el-option label="黑名单命中" value="BLACKLIST" /><el-option label="可疑交易" value="SUSPICIOUS" />
          </el-select>
        </el-form-item>
        <el-form-item label="风险等级">
          <el-select v-model="searchForm.riskLevel" placeholder="全部" clearable style="width: 120px">
            <el-option label="高" value="HIGH" /><el-option label="中" value="MEDIUM" /><el-option label="低" value="LOW" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="待处理" value="PENDING" /><el-option label="调查中" value="INVESTIGATING" />
            <el-option label="已解决" value="RESOLVED" /><el-option label="已关闭" value="CLOSED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="handleReset"><el-icon><Refresh /></el-icon>重置</el-button>
          <el-button type="warning" @click="handleBatchProcess"><el-icon><Operation /></el-icon>批量处理</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe border @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="50" />
        <el-table-column prop="id" label="告警编号" width="130" />
        <el-table-column prop="alertType" label="告警类型" width="110" />
        <el-table-column prop="ruleName" label="触发规则" width="120" show-overflow-tooltip />
        <el-table-column prop="customerName" label="客户" width="120" />
        <el-table-column prop="riskLevel" label="风险等级" width="90" align="center">
          <template #default="{ row }"><el-tag :type="riskLevelType(row.riskLevel)" size="small">{{ riskLevelLabel(row.riskLevel) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }"><el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="assignee" label="处理人" width="100" />
        <el-table-column prop="createdTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small">查看</el-button>
            <el-button type="warning" link size="small" @click="handleAssign(row)">分配</el-button>
            <el-button type="success" link size="small" @click="handleProcess(row)" v-if="row.status === 'PENDING'">处理</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="pagination.current" v-model:page-size="pagination.size" :total="pagination.total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next" @current-change="loadData" @size-change="loadData" />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.alert-list-page { min-height: 100%; }
.search-card { margin-bottom: 16px; border-radius: 8px; }
.table-card { border-radius: 8px; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
