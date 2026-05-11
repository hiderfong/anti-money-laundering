<script setup lang="ts">
/**
 * 案件管理页面
 * 案件列表查询、状态变更、调查记录
 */
import { ref, reactive, onMounted } from 'vue'
import { caseApi } from '@/api/modules'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const searchForm = reactive({ status: '', caseType: '', keyword: '' })
const tableData = ref<any[]>([])
const pagination = reactive({ current: 1, size: 10, total: 0 })

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await caseApi.pageQuery({ page: pagination.current, size: pagination.size, ...searchForm })
    const data = res.data || res
    tableData.value = data.records || data.list || []
    pagination.total = data.total || 0
  } catch (e) {
    tableData.value = Array.from({ length: 8 }, (_, i) => ({
      id: `CASE-${3000 + i}`, caseNo: `C-2024-${String(1000 + i).slice(1)}`,
      caseType: ['可疑交易', '反恐融资', '逃税'][i % 3], title: `案件${i + 1}调查`,
      status: ['OPEN', 'INVESTIGATING', 'CLOSED', 'SUBMITTED'][i % 4],
      priority: ['HIGH', 'MEDIUM', 'LOW'][i % 3], assignee: '张经理', customerName: `客户${i + 1}`,
      createdTime: '2024-06-10 09:00:00'
    }))
    pagination.total = 30
  } finally { loading.value = false }
}

const handleSearch = () => { pagination.current = 1; loadData() }
const handleReset = () => { Object.assign(searchForm, { status: '', caseType: '', keyword: '' }); handleSearch() }

const statusLabel = (s: string) => ({ OPEN: '已创建', INVESTIGATING: '调查中', CLOSED: '已关闭', SUBMITTED: '已上报' }[s] || s)
const statusType = (s: string) => ({ OPEN: 'info', INVESTIGATING: 'primary', CLOSED: 'success', SUBMITTED: 'warning' }[s] || 'info') as any
const priorityLabel = (p: string) => ({ HIGH: '高', MEDIUM: '中', LOW: '低' }[p] || p)
const priorityType = (p: string) => ({ HIGH: 'danger', MEDIUM: 'warning', LOW: 'success' }[p] || 'info') as any

const handleCreateCase = () => { ElMessage.info('创建案件功能开发中') }
const handleView = (row: any) => { ElMessage.info(`查看案件 ${row.id}`) }
const handleClose = async (row: any) => {
  try {
    await ElMessageBox.confirm('确认关闭此案件吗？', '关闭案件', { type: 'warning' })
    await caseApi.close(row.id, { reason: '调查完成' })
    ElMessage.success('案件已关闭')
    loadData()
  } catch (e: any) { if (e !== 'cancel') ElMessage.error('操作失败') }
}

onMounted(() => { loadData() })
</script>

<template>
  <div class="case-list-page">
    <el-card shadow="never" class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="案件编号/标题" clearable style="width: 180px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 130px">
            <el-option label="已创建" value="OPEN" /><el-option label="调查中" value="INVESTIGATING" />
            <el-option label="已关闭" value="CLOSED" /><el-option label="已上报" value="SUBMITTED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="handleReset"><el-icon><Refresh /></el-icon>重置</el-button>
          <el-button type="success" @click="handleCreateCase"><el-icon><Plus /></el-icon>创建案件</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe border>
        <el-table-column prop="caseNo" label="案件编号" width="140" />
        <el-table-column prop="title" label="案件标题" min-width="160" show-overflow-tooltip />
        <el-table-column prop="caseType" label="案件类型" width="110" />
        <el-table-column prop="customerName" label="关联客户" width="120" />
        <el-table-column prop="priority" label="优先级" width="80" align="center">
          <template #default="{ row }"><el-tag :type="priorityType(row.priority)" size="small">{{ priorityLabel(row.priority) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }"><el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="assignee" label="负责人" width="100" />
        <el-table-column prop="createdTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleView(row)">查看</el-button>
            <el-button type="warning" link size="small">调查</el-button>
            <el-button type="danger" link size="small" @click="handleClose(row)" v-if="row.status !== 'CLOSED'">关闭</el-button>
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
.case-list-page { min-height: 100%; }
.search-card { margin-bottom: 16px; border-radius: 8px; }
.table-card { border-radius: 8px; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
