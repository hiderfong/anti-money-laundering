<template>
  <div>
    <el-card style="margin-bottom: 16px">
      <el-form :inline="true">
        <el-form-item label="客户ID"><el-input-number v-model="screenForm.customerId" :min="1" /></el-form-item>
        <el-form-item label="筛查类型">
          <el-select v-model="screenForm.screeningType">
            <el-option label="客户准入" value="CUSTOMER_ONBOARD" />
            <el-option label="信息变更" value="INFO_CHANGE" />
            <el-option label="定期筛查" value="PERIODIC" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="screening" @click="doScreen">触发筛查</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    <el-card>
      <template #header>筛查结果</template>
      <el-table :data="results" stripe v-loading="loading" border>
        <el-table-column prop="customerName" label="客户" width="120" />
        <el-table-column prop="watchlistName" label="命中名单" width="150" />
        <el-table-column prop="matchScore" label="匹配分数" width="100">
          <template #default="{ row }">
            <el-tag :type="row.matchScore >= 95 ? 'danger' : 'warning'" size="small">{{ row.matchScore }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="matchType" label="匹配类型" width="100" />
        <el-table-column prop="reviewStatus" label="复核状态" width="120">
          <template #default="{ row }">
            <el-tag :type="{ PENDING_REVIEW:'warning', CONFIRMED:'danger', EXCLUDED:'info' }[row.reviewStatus] as any" size="small">
              {{ row.reviewStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" label="筛查时间" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <template v-if="row.reviewStatus === 'PENDING_REVIEW'">
              <el-button link type="danger" size="small" @click="review(row.id, 'CONFIRMED')">确认命中</el-button>
              <el-button link type="info" size="small" @click="review(row.id, 'EXCLUDED')">排除</el-button>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const loading = ref(false)
const screening = ref(false)
const results = ref<any[]>([])
const screenForm = ref({ customerId: 1, screeningType: 'CUSTOMER_ONBOARD' })

async function loadData() {
  loading.value = true
  try {
    const res: any = await request.get('/screening/results', { params: { page: 1, size: 50 } })
    results.value = res.data?.list || []
  } catch (e) { /* handled */ } finally { loading.value = false }
}

async function doScreen() {
  screening.value = true
  try {
    await request.post('/screening/screen', screenForm.value)
    ElMessage.success('筛查完成')
    loadData()
  } catch (e) { /* handled */ } finally { screening.value = false }
}

async function review(id: number, status: string) {
  try {
    await request.post('/screening/review', { resultId: id, reviewStatus: status, reviewReason: '' })
    ElMessage.success('复核完成')
    loadData()
  } catch (e) { /* handled */ }
}

onMounted(loadData)
</script>
