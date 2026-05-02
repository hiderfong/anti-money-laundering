<template>
  <div>
    <el-card>
      <template #header>案件列表</template>
      <el-table :data="cases" stripe v-loading="loading" border>
        <el-table-column prop="caseNo" label="案件编号" width="180" />
        <el-table-column prop="customerName" label="客户" width="120" />
        <el-table-column prop="caseStatus" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.caseStatus)" size="small">{{ statusLabel(row.caseStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="80" />
        <el-table-column prop="createdBy" label="创建人" width="100" />
        <el-table-column prop="createdTime" label="创建时间" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '@/utils/request'

const loading = ref(false)
const cases = ref<any[]>([])

function statusType(s: string) {
  return { DRAFT: 'info', INVESTIGATING: '', PENDING_APPROVAL: 'warning', SUBMITTED: 'success', CLOSED: 'info' }[s] || 'info'
}
function statusLabel(s: string) {
  return { DRAFT: '草稿', INVESTIGATING: '调查中', PENDING_APPROVAL: '待审批', SUBMITTED: '已报送', CLOSED: '已结案' }[s] || s
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await request.get('/cases/page', { params: { page: 1, size: 20 } })
    cases.value = res.data?.list || []
  } catch (e) { /* handled */ } finally { loading.value = false }
}

onMounted(loadData)
</script>
