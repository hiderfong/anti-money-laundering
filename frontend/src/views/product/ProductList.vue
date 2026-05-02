<template>
  <div>
    <el-card>
      <template #header>产品列表</template>
      <el-table :data="products" stripe v-loading="loading" border>
        <el-table-column prop="productCode" label="产品编码" width="150" />
        <el-table-column prop="productName" label="产品名称" width="200" />
        <el-table-column prop="productType" label="类型" width="100" />
        <el-table-column prop="riskLevel" label="风险等级" width="100">
          <template #default="{ row }">
            <el-tag :type="{ LOW:'success', MEDIUM:'warning', HIGH:'danger' }[row.riskLevel] as any" size="small">
              {{ row.riskLevel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '@/utils/request'

const loading = ref(false)
const products = ref<any[]>([])

async function loadData() {
  loading.value = true
  try {
    const res: any = await request.get('/products/page', { params: { page: 1, size: 20 } })
    products.value = res.data?.list || []
  } catch (e) { /* handled */ } finally { loading.value = false }
}

onMounted(loadData)
</script>
