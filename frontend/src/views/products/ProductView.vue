<script setup lang="ts">
/**
 * 产品管理页面
 * 产品 CRUD 和风险评估
 */
import { ref, reactive, onMounted } from 'vue'
import { productApi } from '@/api/modules'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const searchForm = reactive({ productType: '', riskLevel: '', keyword: '' })
const tableData = ref<any[]>([])
const pagination = reactive({ current: 1, size: 10, total: 0 })

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await productApi.pageQuery({ page: pagination.current, size: pagination.size, ...searchForm })
    const data = res.data || res
    tableData.value = data.records || data.list || []
    pagination.total = data.total || 0
  } catch (e) {
    tableData.value = Array.from({ length: 8 }, (_, i) => ({
      id: `PRD-${5000 + i}`, productCode: `P${String(1000 + i).slice(1)}`,
      productName: ['个人贷款', '企业贷款', '信用卡', '电子银行', '跨境汇款', '托管服务', '理财产品', '保险产品'][i],
      productType: ['信贷', '支付', '投资', '保险'][i % 4],
      riskLevel: ['LOW', 'MEDIUM', 'HIGH'][i % 3], status: 'ACTIVE',
      createdTime: '2024-03-01 10:00:00'
    }))
    pagination.total = 20
  } finally { loading.value = false }
}

const handleSearch = () => { pagination.current = 1; loadData() }
const handleReset = () => { Object.assign(searchForm, { productType: '', riskLevel: '', keyword: '' }); handleSearch() }

const riskLevelType = (l: string) => ({ HIGH: 'danger', MEDIUM: 'warning', LOW: 'success' }[l] || 'info') as any
const riskLevelLabel = (l: string) => ({ HIGH: '高', MEDIUM: '中', LOW: '低' }[l] || l)

const handleAssess = async (row: any) => {
  loading.value = true
  try { await productApi.assess(row.id); ElMessage.success('产品风险评估完成'); loadData() }
  catch (e) { ElMessage.error('评估失败') }
  finally { loading.value = false }
}

onMounted(() => { loadData() })
</script>

<template>
  <div class="product-page">
    <el-card shadow="never" class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="产品名称/编码" clearable style="width: 180px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="产品类型">
          <el-select v-model="searchForm.productType" placeholder="全部" clearable style="width: 130px">
            <el-option label="信贷" value="CREDIT" /><el-option label="支付" value="PAYMENT" />
            <el-option label="投资" value="INVESTMENT" /><el-option label="保险" value="INSURANCE" />
          </el-select>
        </el-form-item>
        <el-form-item label="风险等级">
          <el-select v-model="searchForm.riskLevel" placeholder="全部" clearable style="width: 120px">
            <el-option label="高" value="HIGH" /><el-option label="中" value="MEDIUM" /><el-option label="低" value="LOW" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="handleReset"><el-icon><Refresh /></el-icon>重置</el-button>
          <el-button type="success"><el-icon><Plus /></el-icon>新增产品</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe border>
        <el-table-column prop="productCode" label="产品编码" width="120" />
        <el-table-column prop="productName" label="产品名称" min-width="140" />
        <el-table-column prop="productType" label="产品类型" width="100" />
        <el-table-column prop="riskLevel" label="风险等级" width="90" align="center">
          <template #default="{ row }"><el-tag :type="riskLevelType(row.riskLevel)" size="small">{{ riskLevelLabel(row.riskLevel) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }"><el-tag type="success" size="small">{{ row.status === 'ACTIVE' ? '正常' : '停用' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="createdTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small">编辑</el-button>
            <el-button type="success" link size="small" @click="handleAssess(row)">评估</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="pagination.current" v-model:page-size="pagination.size" :total="pagination.total" layout="total, prev, pager, next" @current-change="loadData" />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.product-page { min-height: 100%; }
.search-card { margin-bottom: 16px; border-radius: 8px; }
.table-card { border-radius: 8px; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
