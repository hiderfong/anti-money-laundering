<template>
  <div>
    <el-card>
      <template #header>大额交易报告</template>
      <el-table :data="reports" stripe v-loading="loading" border>
        <el-table-column prop="reportNo" label="报告编号" width="180" />
        <el-table-column prop="customerName" label="客户" width="120" />
        <el-table-column prop="amount" label="金额" width="120" align="right">
          <template #default="{ row }">{{ Number(row.amount).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="reportStatus" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="{ DRAFT:'info', REVIEWED:'', SUBMITTED:'success', FAILED:'danger' }[row.reportStatus] as any" size="small">
              {{ row.reportStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="submittedTime" label="报送时间" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '@/utils/request'

const loading = ref(false)
const reports = ref<any[]>([])

async function loadData() {
  loading.value = true
  try {
    const res: any = await request.get('/reporting/large-txn/page', { params: { page: 1, size: 20 } })
    reports.value = res.data?.list || []
  } catch (e) { /* handled */ } finally { loading.value = false }
}

onMounted(loadData)
</script>
