<template>
  <div>
    <el-card>
      <template #header>交易列表</template>
      <el-table :data="transactions" stripe v-loading="loading" border>
        <el-table-column prop="transactionNo" label="交易编号" width="180" />
        <el-table-column prop="transactionType" label="类型" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ typeMap[row.transactionType] || row.transactionType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="amount" label="金额" width="120" align="right">
          <template #default="{ row }">{{ Number(row.amount).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="currency" label="币种" width="80" />
        <el-table-column prop="paymentMethod" label="支付方式" width="100" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="transactionTime" label="交易时间" />
      </el-table>
      <el-pagination
        v-model:current-page="page" :total="total" :page-size="10"
        layout="total, prev, pager, next" style="margin-top:16px;justify-content:flex-end"
        @current-change="loadData"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '@/utils/request'

const loading = ref(false)
const transactions = ref<any[]>([])
const page = ref(1)
const total = ref(0)
const typeMap: Record<string, string> = { PREMIUM:'保费缴纳', SURRENDER:'退保', CLAIM:'理赔', LOAN:'保单贷款', REPAYMENT:'还款' }

async function loadData() {
  loading.value = true
  try {
    const res: any = await request.get('/monitoring/transactions/page', { params: { page: page.value, size: 10 } })
    transactions.value = res.data?.list || []
    total.value = res.data?.total || 0
  } catch (e) { /* handled */ } finally { loading.value = false }
}

onMounted(loadData)
</script>
