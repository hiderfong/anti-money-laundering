<template>
  <div class="product-container">
    <!-- 搜索筛选栏 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="filterForm" @submit.prevent="handleSearch">
        <el-form-item label="产品名称">
          <el-input v-model="filterForm.productName" placeholder="请输入产品名称" clearable style="width:200px" />
        </el-form-item>
        <el-form-item label="产品编码">
          <el-input v-model="filterForm.productCode" placeholder="请输入产品编码" clearable style="width:180px" />
        </el-form-item>
        <el-form-item label="产品类型">
          <el-select v-model="filterForm.productType" placeholder="全部类型" clearable style="width:150px">
            <el-option v-for="option in productTypeOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="风险等级">
          <el-select v-model="filterForm.riskLevel" placeholder="全部等级" clearable style="width:120px">
            <el-option label="低风险" value="LOW" />
            <el-option label="中风险" value="MEDIUM" />
            <el-option label="高风险" value="HIGH" />
            <el-option label="极高风险" value="VERY_HIGH" />
            <el-option label="未评估" value="NOT_ASSESSED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" @click="openCreateDialog">新建产品</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card shadow="never" style="margin-top:16px">
      <el-table :data="tableData" stripe border v-loading="loading" style="width:100%">
        <el-table-column prop="productCode" label="产品编码" width="150" fixed />
        <el-table-column label="产品名称" width="220" show-overflow-tooltip>
          <template #default="{ row }">
            {{ displayProductName(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="productType" label="产品类型" width="130">
          <template #default="{ row }">
            {{ productTypeLabel(row.productType) }}
          </template>
        </el-table-column>
        <el-table-column prop="riskLevel" label="风险等级" width="110">
          <template #default="{ row }">
            <el-tag :type="riskLevelTagType(row.riskLevel)" size="small" effect="dark">
              {{ riskLevelLabel(row.riskLevel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="产品简介" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            {{ productDescription(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openDetail(row)">详情</el-button>
            <el-button type="warning" link size="small" @click="openEditDialog(row)">编辑</el-button>
            <el-button type="success" link size="small" @click="handleAssess(row)">风险评估</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        style="margin-top:16px;justify-content:flex-end"
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadData"
        @current-change="loadData"
      />
    </el-card>

    <!-- 创建/编辑产品弹窗 -->
    <el-dialog
      v-model="formVisible"
      :title="isEdit ? '编辑产品' : '新建产品'"
      width="560px"
      destroy-on-close
    >
      <el-form :model="productForm" :rules="formRules" ref="productFormRef" label-width="100px">
        <el-form-item label="产品编码" prop="productCode">
          <el-input v-model="productForm.productCode" placeholder="请输入产品编码" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="产品名称" prop="productName">
          <el-input v-model="productForm.productName" placeholder="请输入产品名称" />
        </el-form-item>
        <el-form-item label="产品类型" prop="productType">
          <el-select v-model="productForm.productType" placeholder="请选择产品类型" style="width:100%">
            <el-option v-for="option in productTypeOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="风险等级" prop="riskLevel">
          <el-select v-model="productForm.riskLevel" placeholder="请选择风险等级" style="width:100%">
            <el-option label="低风险" value="LOW" />
            <el-option label="中风险" value="MEDIUM" />
            <el-option label="高风险" value="HIGH" />
            <el-option label="极高风险" value="VERY_HIGH" />
          </el-select>
        </el-form-item>
        <el-form-item label="产品描述" prop="description">
          <el-input v-model="productForm.description" type="textarea" :rows="4" placeholder="请输入产品描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
          {{ isEdit ? '保存修改' : '确认创建' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 产品详情弹窗 -->
    <el-dialog v-model="detailVisible" title="产品详情" width="800px" destroy-on-close>
      <template v-if="detailData">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="产品编码">{{ detailData.productCode }}</el-descriptions-item>
          <el-descriptions-item label="产品名称">{{ displayProductName(detailData) }}</el-descriptions-item>
          <el-descriptions-item label="产品类型">{{ productTypeLabel(detailData.productType) }}</el-descriptions-item>
          <el-descriptions-item label="风险等级">
            <el-tag :type="riskLevelTagType(detailData.riskLevel)" size="small" effect="dark">
              {{ riskLevelLabel(detailData.riskLevel) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="detailData.status === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ detailData.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detailData.createdTime }}</el-descriptions-item>
          <el-descriptions-item label="产品简介" :span="2">{{ productDescription(detailData) }}</el-descriptions-item>
        </el-descriptions>

        <!-- 评估历史 -->
        <el-divider content-position="left">评估历史</el-divider>
        <el-table :data="assessmentList" border size="small" v-loading="assessmentLoading" style="width:100%">
          <el-table-column prop="riskLevel" label="风险等级" width="110">
            <template #default="{ row }">
              <el-tag :type="riskLevelTagType(row.riskLevel)" size="small" effect="dark">
                {{ riskLevelLabel(row.riskLevel) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="riskScore" label="风险评分" width="100" />
          <el-table-column prop="assessmentFactors" label="评估因素" min-width="200" show-overflow-tooltip />
          <el-table-column prop="assessedBy" label="评估人" width="120" />
          <el-table-column prop="assessedTime" label="评估时间" width="170" />
        </el-table>
        <el-empty v-if="!assessmentLoading && assessmentList.length === 0" description="暂无评估记录" :image-size="60" />
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import request from '@/utils/request'

// ==================== 类型定义 ====================

interface Product {
  id: number
  productCode: string
  productName: string
  productType: string
  productSubType?: string
  paymentMode?: string
  hasCashValue?: boolean
  hasInvestmentFeature?: boolean
  beneficiaryChangeable?: boolean
  riskLevel: string
  riskScore?: number
  description?: string
  riskFactors?: string
  status: string
  createdTime: string
}

interface Assessment {
  id: number
  productId: number
  riskLevel: string
  riskScore: number
  assessmentFactors: string
  assessedBy: string
  assessedTime: string
}

// ==================== 数据定义 ====================

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref<Product[]>([])

const pagination = reactive({ page: 1, size: 20, total: 0 })

const filterForm = reactive({
  productName: '',
  productCode: '',
  productType: '',
  riskLevel: ''
})

// 表单弹窗
const formVisible = ref(false)
const isEdit = ref(false)
const editingId = ref<number | null>(null)
const productFormRef = ref<FormInstance>()
const productForm = reactive({
  productCode: '',
  productName: '',
  productType: '',
  riskLevel: '',
  description: ''
})
const formRules: FormRules = {
  productCode: [{ required: true, message: '请输入产品编码', trigger: 'blur' }],
  productName: [{ required: true, message: '请输入产品名称', trigger: 'blur' }],
  productType: [{ required: true, message: '请选择产品类型', trigger: 'change' }],
  riskLevel: [{ required: true, message: '请选择风险等级', trigger: 'change' }]
}

// 详情弹窗
const detailVisible = ref(false)
const detailData = ref<Product | null>(null)
const assessmentList = ref<Assessment[]>([])
const assessmentLoading = ref(false)

// ==================== 工具函数 ====================

const PRODUCT_TYPE_MAP: Record<string, string> = {
  LIFE: '人寿保险',
  LIFE_INSURANCE: '人寿保险',
  PROPERTY: '财产保险',
  HEALTH: '健康保险',
  MEDICAL: '医疗保险',
  ACCIDENT: '意外伤害保险',
  ANNUITY: '年金保险',
  CRITICAL_ILLNESS: '重大疾病保险',
  UNIVERSAL_LIFE: '万能寿险',
  INVESTMENT_LINKED: '投资连结保险',
  GROUP: '团体保险',
  CROSS_BORDER: '跨境保险',
  HIGH_NET_WORTH: '高净值客户保险',
  RETIREMENT: '退休养老保险',
  OTHER: '其他'
}

const productTypeOptions = Object.entries(PRODUCT_TYPE_MAP).map(([value, label]) => ({ value, label }))

const LEGACY_PRODUCT_NAME_MAP: Record<string, string> = {
  PROD001: '定期寿险A款',
  PROD002: '终身寿险B款',
  PROD003: '万能寿险C款',
  PROD004: '投连寿险D款',
  PROD005: '年金保险E款',
  PROD006: '重大疾病保险F款',
  PROD007: '医疗保险G款',
  PROD008: '意外伤害保险H款',
  PROD009: '团体保险I款',
  PROD010: '跨境保险J款',
  PROD011: '高净值客户保险K款',
  PROD012: '退休养老保险L款'
}

const PAYMENT_MODE_MAP: Record<string, string> = {
  LUMP_SUM: '趸交',
  PERIODIC: '期交',
  FLEXIBLE: '灵活缴费'
}

const RISK_LEVEL_MAP: Record<string, string> = {
  LOW: '低风险',
  MEDIUM: '中风险',
  HIGH: '高风险',
  VERY_HIGH: '极高风险',
  NOT_ASSESSED: '未评估'
}

function productTypeLabel(t: string) {
  return PRODUCT_TYPE_MAP[t] || t
}

function riskLevelLabel(r: string) {
  return RISK_LEVEL_MAP[r] || r
}

function riskLevelTagType(r: string): '' | 'success' | 'warning' | 'info' | 'danger' {
  const map: Record<string, '' | 'success' | 'warning' | 'info' | 'danger'> = {
    LOW: 'success',
    MEDIUM: 'warning',
    HIGH: 'danger',
    VERY_HIGH: 'danger',
    NOT_ASSESSED: 'info'
  }
  return map[r] || 'info'
}

function isMojibakeText(value?: string) {
  return !!value && /[åæèéçä�]/.test(value)
}

function displayProductName(row: Product | null) {
  if (!row) return '-'
  if (row.productName?.startsWith('E2ERBAC产品')) return '稳益终身寿险（权限验证版）'
  const legacyName = LEGACY_PRODUCT_NAME_MAP[row.productCode]
  if (legacyName && (!row.productName || isMojibakeText(row.productName))) return legacyName
  return row.productName || legacyName || '-'
}

function productDescription(row: Product | null) {
  if (!row) return '-'
  if (row.description) return row.description

  const type = productTypeLabel(row.productType) || '保险产品'
  const payment = row.paymentMode ? PAYMENT_MODE_MAP[row.paymentMode] || row.paymentMode : '未配置'
  const cashValue = row.hasCashValue ? '具备现金价值' : '无现金价值'
  const investment = row.hasInvestmentFeature ? '含投资属性' : '不含投资属性'
  const beneficiary = row.beneficiaryChangeable === false ? '受益人不可变更' : '受益人可变更'
  const score = row.riskScore == null ? '未评分' : `${row.riskScore}分`
  return `${type}，缴费方式${payment}，${cashValue}，${investment}，${beneficiary}，当前风险评分${score}。`
}

// ==================== 数据加载 ====================

async function loadData() {
  loading.value = true
  try {
    const params: any = {
      page: pagination.page,
      size: pagination.size
    }
    if (filterForm.productName) params.productName = filterForm.productName
    if (filterForm.productCode) params.productCode = filterForm.productCode
    if (filterForm.productType) params.productType = filterForm.productType
    if (filterForm.riskLevel) params.riskLevel = filterForm.riskLevel

    const res: any = await request.get('/products/page', { params })
    tableData.value = res.data?.list || []
    pagination.total = res.data?.total || 0
  } catch (e) {
    ElMessage.error('加载产品列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  loadData()
}

function handleReset() {
  filterForm.productName = ''
  filterForm.productCode = ''
  filterForm.productType = ''
  filterForm.riskLevel = ''
  handleSearch()
}

// ==================== 创建/编辑产品 ====================

function resetForm() {
  productForm.productCode = ''
  productForm.productName = ''
  productForm.productType = ''
  productForm.riskLevel = ''
  productForm.description = ''
}

function openCreateDialog() {
  isEdit.value = false
  editingId.value = null
  resetForm()
  formVisible.value = true
}

function openEditDialog(row: Product) {
  isEdit.value = true
  editingId.value = row.id
  productForm.productCode = row.productCode
  productForm.productName = displayProductName(row)
  productForm.productType = row.productType
  productForm.riskLevel = row.riskLevel
  productForm.description = row.description || productDescription(row)
  formVisible.value = true
}

async function handleSubmit() {
  const valid = await productFormRef.value?.validate().catch(() => false)
  if (!valid) return

  submitLoading.value = true
  try {
    if (isEdit.value && editingId.value) {
      await request.put(`/products/${editingId.value}`, {
        productCode: productForm.productCode,
        productName: productForm.productName,
        productType: productForm.productType,
        riskLevel: productForm.riskLevel,
        description: productForm.description
      })
      ElMessage.success('产品更新成功')
    } else {
      await request.post('/products', {
        productCode: productForm.productCode,
        productName: productForm.productName,
        productType: productForm.productType,
        riskLevel: productForm.riskLevel,
        description: productForm.description
      })
      ElMessage.success('产品创建成功')
    }
    formVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error(isEdit.value ? '更新产品失败' : '创建产品失败')
  } finally {
    submitLoading.value = false
  }
}

// ==================== 产品详情 ====================

async function openDetail(row: Product) {
  detailVisible.value = true
  detailData.value = null
  assessmentList.value = []

  try {
    const res: any = await request.get(`/products/${row.id}`)
    detailData.value = res.data
  } catch (e) {
    ElMessage.error('加载产品详情失败')
    detailVisible.value = false
    return
  }

  // 加载评估历史
  assessmentLoading.value = true
  try {
    const res: any = await request.get(`/products/${row.id}/assessments`)
    assessmentList.value = res.data?.list || res.data || []
  } catch (e) {
    // 评估历史加载失败不影响详情展示
  } finally {
    assessmentLoading.value = false
  }
}

// ==================== 风险评估 ====================

async function handleAssess(row: Product) {
  try {
    await ElMessageBox.confirm(
      `确认对产品「${displayProductName(row)}」发起风险评估？`,
      '风险评估确认',
      { confirmButtonText: '确认评估', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return // 用户取消
  }

  loading.value = true
  try {
    await request.post(`/products/${row.id}/assess`)
    ElMessage.success('风险评估已触发，正在处理中')
    loadData()
  } catch (e) {
    ElMessage.error('触发风险评估失败')
  } finally {
    loading.value = false
  }
}

// ==================== 初始化 ====================

onMounted(loadData)
</script>

<style scoped>
.product-container {
  padding: 0;
}
.filter-card :deep(.el-form-item) {
  margin-bottom: 0;
}
</style>
