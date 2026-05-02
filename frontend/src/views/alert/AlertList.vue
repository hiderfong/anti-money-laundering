<template>
  <div>
    <el-card style="margin-bottom: 16px">
      <el-form :inline="true" :model="query">
        <el-form-item label="类型">
          <el-select v-model="query.alertType" clearable placeholder="全部">
            <el-option label="大额交易" value="LARGE_TXN" /><el-option label="可疑交易" value="SUSPICIOUS" />
            <el-option label="制裁命中" value="SANCTIONS_HIT" /><el-option label="PEP命中" value="PEP_HIT" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部">
            <el-option label="新建" value="NEW" /><el-option label="已分配" value="ASSIGNED" />
            <el-option label="处理中" value="PROCESSING" /><el-option label="已确认" value="CONFIRMED" />
            <el-option label="已排除" value="EXCLUDED" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="loadData">查询</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-card>
      <template #header>预警列表</template>
      <el-table :data="alerts" stripe v-loading="loading" border>
        <el-table-column prop="alertNo" label="预警编号" width="180" />
        <el-table-column prop="customerName" label="客户" width="120" />
        <el-table-column prop="alertType" label="类型" width="100">
          <template #default="{ row }"><el-tag size="small">{{ typeMap[row.alertType] || row.alertType }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="riskLevel" label="风险等级" width="100">
          <template #default="{ row }">
            <el-tag :type="{ LOW:'success', MEDIUM:'warning', HIGH:'danger', CRITICAL:'danger' }[row.riskLevel] as any" size="small">
              {{ { LOW:'低', MEDIUM:'中', HIGH:'高', CRITICAL:'极高' }[row.riskLevel as string] || row.riskLevel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="{ NEW:'danger', ASSIGNED:'warning', PROCESSING:'', CONFIRMED:'success', EXCLUDED:'info' }[row.status] as any" size="small">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="riskScore" label="风险分" width="80" />
        <el-table-column prop="createdTime" label="生成时间" />
      </el-table>
      <el-pagination
        v-model:current-page="query.page" v-model:page-size="query.size" :total="total"
        layout="total, prev, pager, next, sizes" :page-sizes="[10,20,50]"
        style="margin-top:16px;justify-content:flex-end" @current-change="loadData" @size-change="loadData"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import request from '@/utils/request'

const loading = ref(false)
const alerts = ref<any[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, alertType: '', status: '' })
const typeMap: Record<string, string> = { LARGE_TXN:'大额交易', SUSPICIOUS:'可疑交易', SANCTIONS_HIT:'制裁命中', PEP_HIT:'PEP命中', MANUAL:'人工创建' }

async function loadData() {
  loading.value = true
  try {
    const res: any = await request.get('/alerts/page', { params: query })
    alerts.value = res.data?.list || []
    total.value = res.data?.total || 0
  } catch (e) { /* handled */ } finally { loading.value = false }
}

onMounted(loadData)
</script>
