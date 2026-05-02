<template>
  <div>
    <el-card>
      <template #header>风险自评估</template>
      <el-table :data="assessments" stripe v-loading="loading" border>
        <el-table-column prop="assessmentYear" label="评估年度" width="100" />
        <el-table-column prop="assessmentPeriod" label="评估周期" width="100" />
        <el-table-column prop="overallScore" label="综合评分" width="100" />
        <el-table-column prop="overallRiskLevel" label="风险等级" width="100">
          <template #default="{ row }">
            <el-tag :type="{ LOW:'success', MEDIUM:'warning', HIGH:'danger' }[row.overallRiskLevel] as any" size="small">
              {{ row.overallRiskLevel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="assessmentStatus" label="状态" width="100" />
        <el-table-column prop="createdTime" label="创建时间" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '@/utils/request'

const loading = ref(false)
const assessments = ref<any[]>([])

async function loadData() {
  loading.value = true
  try {
    const res: any = await request.get('/assessments/list')
    assessments.value = res.data || []
  } catch (e) { /* handled */ } finally { loading.value = false }
}

onMounted(loadData)
</script>
