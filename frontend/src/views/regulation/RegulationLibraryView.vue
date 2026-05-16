<template>
  <div class="regulation-page">
    <section class="page-heading">
      <div>
        <h1>法规及资料库</h1>
        <p>统一维护反洗钱法规、制度文件、培训素材与监管行业动态，支持全文检索和分类管理</p>
      </div>
      <div class="heading-actions">
        <el-button @click="openCategoryCreate">
          <el-icon><Plus /></el-icon>新增分类
        </el-button>
        <el-button type="primary" @click="openDocumentCreate">
          <el-icon><Plus /></el-icon>新增资料
        </el-button>
      </div>
    </section>

    <div class="metric-grid">
      <div class="metric-card">
        <span>资料总数</span>
        <strong>{{ overview.totalDocuments }}</strong>
      </div>
      <div class="metric-card">
        <span>法律法规</span>
        <strong>{{ overview.regulationDocuments }}</strong>
      </div>
      <div class="metric-card">
        <span>制度文件</span>
        <strong>{{ overview.policyDocuments }}</strong>
      </div>
      <div class="metric-card">
        <span>培训素材</span>
        <strong>{{ overview.trainingDocuments }}</strong>
      </div>
      <div class="metric-card warning">
        <span>监管动态</span>
        <strong>{{ overview.regulatoryUpdates }}</strong>
      </div>
      <div class="metric-card success">
        <span>已发布</span>
        <strong>{{ overview.publishedDocuments }}</strong>
      </div>
    </div>

    <section class="visual-grid">
      <div class="chart-card">
        <div class="chart-card-header">
          <div>
            <h2>知识库结构分布</h2>
            <p>按法规、制度、培训、监管动态和行业动态展示资料覆盖面</p>
          </div>
          <el-tag type="info" size="small">分类结构</el-tag>
        </div>
        <div ref="docTypeChartRef" class="chart-box"></div>
      </div>

      <div class="chart-card">
        <div class="chart-card-header">
          <div>
            <h2>监管动态热度</h2>
            <p>按发布日期聚合当前检索结果，辅助识别近期制度变化</p>
          </div>
          <el-tag type="warning" size="small">时序动态</el-tag>
        </div>
        <div ref="updateTimelineChartRef" class="chart-box"></div>
      </div>
    </section>

    <div class="content-grid">
      <aside class="category-panel">
        <div class="panel-title">
          <span>分类管理</span>
          <el-button size="small" text @click="openCategoryCreate">新增</el-button>
        </div>
        <el-scrollbar max-height="520px">
          <button
            v-for="category in categories"
            :key="category.id"
            class="category-item"
            :class="{ active: query.categoryId === category.id }"
            @click="selectCategory(category.id)"
          >
            <span>
              <strong>{{ category.categoryName }}</strong>
              <small>{{ optionLabel(categoryTypeOptions, category.categoryType) }}</small>
            </span>
            <el-button size="small" text @click.stop="openCategoryEdit(category)">编辑</el-button>
          </button>
        </el-scrollbar>
      </aside>

      <section class="library-panel">
        <el-tabs v-model="activeTab" @tab-change="handleTabChange">
          <el-tab-pane label="知识库资料" name="documents" />
          <el-tab-pane label="监管及行业动态" name="updates" />
        </el-tabs>

        <div class="toolbar">
          <el-input v-model="query.keyword" placeholder="全文检索：标题 / 摘要 / 正文 / 标签 / 来源" clearable @keyup.enter="loadDocuments" />
          <el-select v-model="query.docType" placeholder="资料类型" clearable @change="loadDocuments">
            <el-option v-for="item in docTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="query.status" placeholder="状态" clearable @change="loadDocuments">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="query.sourceType" placeholder="来源" clearable @change="loadDocuments">
            <el-option v-for="item in sourceTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-button @click="loadDocuments"><el-icon><Refresh /></el-icon>查询</el-button>
        </div>

        <el-table :data="documents" v-loading="loading" stripe border style="width: 100%">
          <el-table-column prop="docCode" label="资料编码" width="150" fixed="left" />
          <el-table-column prop="title" label="标题" min-width="260" show-overflow-tooltip />
          <el-table-column prop="docType" label="类型" width="130">
            <template #default="{ row }">{{ optionLabel(docTypeOptions, row.docType) }}</template>
          </el-table-column>
          <el-table-column prop="categoryName" label="分类" width="120" />
          <el-table-column prop="sourceOrg" label="来源机构" min-width="150" show-overflow-tooltip />
          <el-table-column prop="publishDate" label="发布日期" width="120" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small">{{ optionLabel(statusOptions, row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="importantFlag" label="重点" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.importantFlag" type="danger" size="small">重点</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="tags" label="标签" min-width="180" show-overflow-tooltip />
          <el-table-column prop="viewCount" label="浏览" width="90" />
          <el-table-column prop="updatedTime" label="更新时间" min-width="170" />
          <el-table-column label="操作" width="210" fixed="right">
            <template #default="{ row }">
              <div class="table-actions">
                <el-button link type="primary" size="small" @click="openDocumentEdit(row)">编辑</el-button>
                <el-button link type="success" size="small" @click="publishDocument(row)">发布</el-button>
                <el-button link type="danger" size="small" @click="archiveDocument(row)">归档</el-button>
                <el-button link type="info" size="small" @click="openDetail(row)">查看</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-row">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="total"
            :current-page="query.page"
            :page-size="query.size"
            :page-sizes="[10, 20, 50, 100]"
            @current-change="handlePageChange"
            @size-change="handleSizeChange"
          />
        </div>
      </section>
    </div>

    <el-dialog v-model="categoryDialogVisible" :title="editingCategoryId ? '编辑分类' : '新增分类'" width="560px" destroy-on-close>
      <el-form :model="categoryForm" label-width="100px">
        <el-form-item label="分类编码"><el-input v-model="categoryForm.categoryCode" placeholder="如 REG-LAW" /></el-form-item>
        <el-form-item label="分类名称"><el-input v-model="categoryForm.categoryName" /></el-form-item>
        <el-form-item label="分类类型">
          <el-select v-model="categoryForm.categoryType" style="width: 100%;">
            <el-option v-for="item in categoryTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="categoryForm.sortOrder" :min="0" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="categoryForm.status" style="width: 100%;">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明"><el-input v-model="categoryForm.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCategory">保存分类</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="documentDialogVisible" :title="editingDocumentId ? '编辑资料' : '新增资料'" width="860px" destroy-on-close>
      <el-form :model="documentForm" label-width="110px">
        <div class="form-grid">
          <el-form-item label="资料编码"><el-input v-model="documentForm.docCode" placeholder="如 AML-KB-REG-003" /></el-form-item>
          <el-form-item label="资料标题"><el-input v-model="documentForm.title" /></el-form-item>
          <el-form-item label="资料类型">
            <el-select v-model="documentForm.docType" style="width: 100%;">
              <el-option v-for="item in docTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="分类">
            <el-select v-model="documentForm.categoryId" clearable style="width: 100%;">
              <el-option v-for="item in categories" :key="item.id" :label="item.categoryName" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="来源类型">
            <el-select v-model="documentForm.sourceType" style="width: 100%;">
              <el-option v-for="item in sourceTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="来源机构"><el-input v-model="documentForm.sourceOrg" /></el-form-item>
          <el-form-item label="发布日期"><el-date-picker v-model="documentForm.publishDate" value-format="YYYY-MM-DD" type="date" style="width: 100%;" /></el-form-item>
          <el-form-item label="生效日期"><el-date-picker v-model="documentForm.effectiveDate" value-format="YYYY-MM-DD" type="date" style="width: 100%;" /></el-form-item>
          <el-form-item label="状态">
            <el-select v-model="documentForm.status" style="width: 100%;">
              <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="重点资料">
            <el-switch v-model="documentForm.importantFlag" active-text="是" inactive-text="否" />
          </el-form-item>
        </div>
        <el-form-item label="摘要"><el-input v-model="documentForm.summary" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="正文内容"><el-input v-model="documentForm.content" type="textarea" :rows="6" /></el-form-item>
        <el-form-item label="标签"><el-input v-model="documentForm.tags" placeholder="多个标签用逗号分隔" /></el-form-item>
        <el-form-item label="来源链接"><el-input v-model="documentForm.referenceUrl" /></el-form-item>
        <el-form-item label="附件引用"><el-input v-model="documentForm.attachmentRef" placeholder="可填写内部文件编号或附件路径" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="documentDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitDocument">保存资料</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailDialogVisible" title="资料详情" width="820px">
      <div v-if="currentDocument" class="document-detail">
        <div class="detail-title">
          <h2>{{ currentDocument.title }}</h2>
          <el-tag :type="statusType(currentDocument.status)">{{ optionLabel(statusOptions, currentDocument.status) }}</el-tag>
        </div>
        <div class="detail-meta">
          <span>{{ optionLabel(docTypeOptions, currentDocument.docType) }}</span>
          <span>{{ currentDocument.categoryName || '未分类' }}</span>
          <span>{{ currentDocument.sourceOrg || '未填写来源' }}</span>
          <span>{{ currentDocument.publishDate || '未发布' }}</span>
        </div>
        <p class="detail-summary">{{ currentDocument.summary }}</p>
        <pre class="detail-content">{{ currentDocument.content }}</pre>
        <div class="detail-tags">{{ currentDocument.tags }}</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import type { ECharts } from 'echarts'
import { ElMessage } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { regulationApi } from '@/api/modules'
import type { RegulationCategory, RegulationDocument } from '@/api/types'
import { disposeEchart, getEcharts } from '@/utils/echarts'

type TabName = 'documents' | 'updates'

const activeTab = ref<TabName>('documents')
const loading = ref(false)
const submitting = ref(false)
const total = ref(0)
const categories = ref<RegulationCategory[]>([])
const documents = ref<RegulationDocument[]>([])
const currentDocument = ref<RegulationDocument | null>(null)
const docTypeChartRef = ref<HTMLElement | null>(null)
const updateTimelineChartRef = ref<HTMLElement | null>(null)
let docTypeChart: ECharts | null = null
let updateTimelineChart: ECharts | null = null
let resizeHandler: (() => void) | null = null

const overview = reactive({
  totalDocuments: 0,
  regulationDocuments: 0,
  policyDocuments: 0,
  trainingDocuments: 0,
  regulatoryUpdates: 0,
  industryUpdates: 0,
  publishedDocuments: 0,
  importantDocuments: 0,
  categories: 0,
})

const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  docType: '',
  categoryId: '',
  status: '',
  sourceType: '',
})

const categoryDialogVisible = ref(false)
const documentDialogVisible = ref(false)
const detailDialogVisible = ref(false)
const editingCategoryId = ref('')
const editingDocumentId = ref('')

const categoryForm = reactive({
  categoryCode: '',
  categoryName: '',
  categoryType: 'REGULATION',
  parentId: '0',
  sortOrder: 0,
  status: 'ENABLED',
  description: '',
})

const documentForm = reactive({
  docCode: '',
  title: '',
  docType: 'REGULATION',
  categoryId: '',
  sourceType: 'REGULATOR',
  sourceOrg: '',
  publishDate: '',
  effectiveDate: '',
  status: 'DRAFT',
  importantFlag: false,
  summary: '',
  content: '',
  tags: '',
  referenceUrl: '',
  attachmentRef: '',
})

const docTypeOptions = [
  { label: '法律法规', value: 'REGULATION' },
  { label: '制度文件', value: 'POLICY' },
  { label: '培训素材', value: 'TRAINING' },
  { label: '监管动态', value: 'REGULATORY_UPDATE' },
  { label: '行业动态', value: 'INDUSTRY_UPDATE' },
]

const categoryTypeOptions = [
  { label: '法规类', value: 'REGULATION' },
  { label: '制度类', value: 'POLICY' },
  { label: '培训类', value: 'TRAINING' },
  { label: '动态类', value: 'UPDATE' },
  { label: '通用类', value: 'GENERAL' },
]

const sourceTypeOptions = [
  { label: '监管机构', value: 'REGULATOR' },
  { label: '行业来源', value: 'INDUSTRY' },
  { label: '内部资料', value: 'INTERNAL' },
]

const statusOptions = [
  { label: '草稿', value: 'DRAFT' },
  { label: '已发布', value: 'PUBLISHED' },
  { label: '已归档', value: 'ARCHIVED' },
]

const defaultUpdateType = computed(() => activeTab.value === 'updates' ? 'REGULATORY_UPDATE' : 'REGULATION')

onMounted(async () => {
  await Promise.all([loadOverview(), loadCategories()])
  await loadDocuments()
  await renderRegulationVisuals()
  resizeHandler = () => {
    docTypeChart?.resize()
    updateTimelineChart?.resize()
  }
  window.addEventListener('resize', resizeHandler)
})

async function loadOverview() {
  const res: any = await regulationApi.getOverview()
  Object.assign(overview, res.data || {})
}

async function loadCategories() {
  const res: any = await regulationApi.getCategories({ status: 'ENABLED' })
  categories.value = res.data || []
}

async function loadDocuments() {
  loading.value = true
  try {
    const params = {
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      docType: query.docType || undefined,
      categoryId: query.categoryId || undefined,
      status: query.status || undefined,
      sourceType: query.sourceType || undefined,
    }
    const res: any = activeTab.value === 'updates'
      ? await regulationApi.getUpdates(params)
      : await regulationApi.getDocuments(params)
    documents.value = res.data?.list || []
    total.value = Number(res.data?.total || 0)
  } finally {
    loading.value = false
  }
}

function handleTabChange() {
  query.page = 1
  query.docType = activeTab.value === 'updates' ? '' : query.docType
  loadDocuments()
}

function selectCategory(categoryId: string) {
  query.categoryId = query.categoryId === categoryId ? '' : categoryId
  query.page = 1
  loadDocuments()
}

function handlePageChange(page: number) {
  query.page = page
  loadDocuments()
}

function handleSizeChange(size: number) {
  query.size = size
  query.page = 1
  loadDocuments()
}

function openCategoryCreate() {
  editingCategoryId.value = ''
  Object.assign(categoryForm, {
    categoryCode: '',
    categoryName: '',
    categoryType: 'REGULATION',
    parentId: '0',
    sortOrder: 0,
    status: 'ENABLED',
    description: '',
  })
  categoryDialogVisible.value = true
}

function openCategoryEdit(row: RegulationCategory) {
  editingCategoryId.value = row.id
  Object.assign(categoryForm, {
    categoryCode: row.categoryCode,
    categoryName: row.categoryName,
    categoryType: row.categoryType,
    parentId: row.parentId || '0',
    sortOrder: row.sortOrder || 0,
    status: row.status || 'ENABLED',
    description: row.description || '',
  })
  categoryDialogVisible.value = true
}

async function submitCategory() {
  submitting.value = true
  try {
    if (editingCategoryId.value) {
      await regulationApi.updateCategory(editingCategoryId.value, { ...categoryForm })
      ElMessage.success('分类已更新')
    } else {
      await regulationApi.createCategory({ ...categoryForm })
      ElMessage.success('分类已创建')
    }
    categoryDialogVisible.value = false
    await Promise.all([loadCategories(), loadOverview()])
    await loadDocuments()
  } finally {
    submitting.value = false
  }
}

function openDocumentCreate() {
  editingDocumentId.value = ''
  Object.assign(documentForm, {
    docCode: '',
    title: '',
    docType: defaultUpdateType.value,
    categoryId: query.categoryId || '',
    sourceType: activeTab.value === 'updates' ? 'REGULATOR' : 'INTERNAL',
    sourceOrg: '',
    publishDate: '',
    effectiveDate: '',
    status: 'DRAFT',
    importantFlag: false,
    summary: '',
    content: '',
    tags: '',
    referenceUrl: '',
    attachmentRef: '',
  })
  documentDialogVisible.value = true
}

function openDocumentEdit(row: RegulationDocument) {
  editingDocumentId.value = row.id
  Object.assign(documentForm, {
    docCode: row.docCode,
    title: row.title,
    docType: row.docType,
    categoryId: row.categoryId || '',
    sourceType: row.sourceType || 'INTERNAL',
    sourceOrg: row.sourceOrg || '',
    publishDate: row.publishDate || '',
    effectiveDate: row.effectiveDate || '',
    status: row.status || 'DRAFT',
    importantFlag: Boolean(row.importantFlag),
    summary: row.summary || '',
    content: row.content || '',
    tags: row.tags || '',
    referenceUrl: row.referenceUrl || '',
    attachmentRef: row.attachmentRef || '',
  })
  documentDialogVisible.value = true
}

async function submitDocument() {
  submitting.value = true
  try {
    const payload = {
      ...documentForm,
      categoryId: documentForm.categoryId || undefined,
      publishDate: documentForm.publishDate || undefined,
      effectiveDate: documentForm.effectiveDate || undefined,
    }
    if (editingDocumentId.value) {
      await regulationApi.updateDocument(editingDocumentId.value, payload)
      ElMessage.success('资料已更新')
    } else {
      await regulationApi.createDocument(payload)
      ElMessage.success('资料已创建')
    }
    documentDialogVisible.value = false
    await Promise.all([loadOverview(), loadDocuments()])
  } finally {
    submitting.value = false
  }
}

async function publishDocument(row: RegulationDocument) {
  await regulationApi.publishDocument(row.id)
  ElMessage.success('资料已发布')
  await Promise.all([loadOverview(), loadDocuments()])
}

async function archiveDocument(row: RegulationDocument) {
  await regulationApi.archiveDocument(row.id)
  ElMessage.success('资料已归档')
  await Promise.all([loadOverview(), loadDocuments()])
}

async function openDetail(row: RegulationDocument) {
  const res: any = await regulationApi.getDocument(row.id)
  currentDocument.value = res.data
  detailDialogVisible.value = true
  await loadDocuments()
}

function optionLabel(options: { label: string; value: string }[], value?: string) {
  if (!value) return ''
  return options.find(item => item.value === value)?.label || value
}

function statusType(status?: string) {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'ARCHIVED') return 'info'
  return 'warning'
}

function monthLabel(value?: string) {
  if (!value) return '未发布'
  return value.length >= 7 ? value.slice(0, 7) : value
}

async function renderRegulationVisuals() {
  await nextTick()
  await Promise.all([renderDocTypeChart(), renderUpdateTimelineChart()])
}

async function renderDocTypeChart() {
  const container = docTypeChartRef.value
  if (!container || !container.isConnected) return
  const echarts = await getEcharts()
  if (!docTypeChart) docTypeChart = echarts.init(container)

  const data = [
    { name: '法律法规', value: overview.regulationDocuments, itemStyle: { color: '#2563eb' } },
    { name: '制度文件', value: overview.policyDocuments, itemStyle: { color: '#16a34a' } },
    { name: '培训素材', value: overview.trainingDocuments, itemStyle: { color: '#7c3aed' } },
    { name: '监管动态', value: overview.regulatoryUpdates, itemStyle: { color: '#d97706' } },
    { name: '行业动态', value: overview.industryUpdates, itemStyle: { color: '#0891b2' } }
  ].filter(item => Number(item.value) > 0)

  docTypeChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}<br/>资料数：{c} ({d}%)' },
    legend: {
      bottom: 0,
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { color: '#64748b', fontSize: 12 }
    },
    series: [{
      type: 'pie',
      radius: ['48%', '70%'],
      center: ['50%', '44%'],
      avoidLabelOverlap: true,
      label: { formatter: '{b}\\n{c}', color: '#334155', fontSize: 12 },
      labelLine: { length: 10, length2: 8 },
      data: data.length ? data : [{ name: '暂无资料', value: 1, itemStyle: { color: '#cbd5e1' } }]
    }]
  }, true)
  docTypeChart.resize()
}

async function renderUpdateTimelineChart() {
  const container = updateTimelineChartRef.value
  if (!container || !container.isConnected) return
  const echarts = await getEcharts()
  if (!updateTimelineChart) updateTimelineChart = echarts.init(container)

  const monthMap = new Map<string, { total: number; important: number }>()
  documents.value.forEach(item => {
    const key = monthLabel(item.publishDate)
    const current = monthMap.get(key) || { total: 0, important: 0 }
    current.total += 1
    if (item.importantFlag) current.important += 1
    monthMap.set(key, current)
  })
  const months = Array.from(monthMap.keys()).sort().slice(-8)
  const totals = months.map(month => monthMap.get(month)?.total || 0)
  const important = months.map(month => monthMap.get(month)?.important || 0)

  updateTimelineChart.setOption({
    color: ['#2563eb', '#dc2626'],
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: {
      top: 0,
      right: 8,
      textStyle: { color: '#64748b', fontSize: 12 }
    },
    grid: { left: 34, right: 14, top: 36, bottom: 34 },
    xAxis: {
      type: 'category',
      data: months.length ? months : ['暂无'],
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#cbd5e1' } },
      axisLabel: { color: '#475569' }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#eef2f7' } },
      axisLabel: { color: '#64748b' }
    },
    series: [
      { name: '资料数', type: 'bar', data: totals.length ? totals : [0], barMaxWidth: 28, itemStyle: { borderRadius: [5, 5, 0, 0] } },
      { name: '重点资料', type: 'line', data: important.length ? important : [0], symbolSize: 7, smooth: true }
    ]
  }, true)
  updateTimelineChart.resize()
}

function disposeRegulationCharts() {
  disposeEchart(docTypeChart)
  disposeEchart(updateTimelineChart)
  docTypeChart = null
  updateTimelineChart = null
}

watch([documents, () => overview.totalDocuments], () => renderRegulationVisuals(), { deep: true })

onUnmounted(() => {
  if (resizeHandler) window.removeEventListener('resize', resizeHandler)
  resizeHandler = null
  disposeRegulationCharts()
})
</script>

<style scoped>
.regulation-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.page-heading h1 {
  margin: 0;
  color: var(--text-primary);
  font-size: 26px;
  font-weight: 650;
}

.page-heading p {
  margin: 6px 0 0;
  color: var(--text-secondary);
}

.heading-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

.metric-card {
  min-height: 86px;
  padding: 16px 18px;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  background: var(--bg-surface);
  box-shadow: var(--shadow-sm);
}

.metric-card span {
  display: block;
  color: var(--text-secondary);
  font-size: 13px;
}

.metric-card strong {
  display: block;
  margin-top: 8px;
  color: var(--text-primary);
  font-size: 28px;
  line-height: 1;
}

.metric-card.warning strong {
  color: var(--color-warning);
}

.metric-card.success strong {
  color: var(--color-success);
}

.visual-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  gap: 12px;
}

.chart-card {
  min-width: 0;
  padding: 16px;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  background: var(--bg-surface);
  box-shadow: var(--shadow-sm);
}

.chart-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.chart-card-header h2 {
  margin: 0;
  color: var(--text-primary);
  font-size: 16px;
  font-weight: 700;
}

.chart-card-header p {
  margin: 4px 0 0;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.chart-box {
  height: 292px;
}

.content-grid {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}

.category-panel,
.library-panel {
  border: 1px solid var(--border-default);
  border-radius: 8px;
  background: var(--bg-surface);
  box-shadow: var(--shadow-sm);
}

.category-panel {
  padding: 12px;
}

.panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 700;
}

.category-item {
  width: 100%;
  min-height: 58px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: var(--text-primary);
  text-align: left;
  cursor: pointer;
  transition: all 0.16s ease;
}

.category-item:hover {
  border-color: var(--border-default);
  background: var(--bg-hover);
}

.category-item.active {
  border-color: rgba(59, 130, 246, 0.55);
  background: rgba(59, 130, 246, 0.10);
}

.category-item strong,
.category-item small {
  display: block;
}

.category-item strong {
  font-size: 13px;
}

.category-item small {
  margin-top: 4px;
  color: var(--text-tertiary);
  font-size: 11px;
}

.library-panel {
  padding: 16px;
  min-width: 0;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 10px;
  margin: 4px 0 12px;
}

.toolbar .el-input {
  width: 320px;
}

.toolbar .el-select {
  width: 140px;
}

.table-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 8px;
}

.table-actions :deep(.el-button) {
  margin-left: 0;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 12px;
}

.document-detail {
  color: var(--text-primary);
}

.detail-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.detail-title h2 {
  margin: 0;
  font-size: 20px;
  line-height: 1.35;
}

.detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.detail-meta span {
  padding: 4px 8px;
  border-radius: 6px;
  background: var(--bg-muted);
  color: var(--text-secondary);
  font-size: 12px;
}

.detail-summary {
  margin: 16px 0 10px;
  color: var(--text-secondary);
  line-height: 1.7;
}

.detail-content {
  max-height: 320px;
  margin: 0;
  padding: 14px;
  overflow: auto;
  border: 1px solid var(--border-subtle);
  border-radius: 8px;
  background: var(--bg-muted);
  color: var(--text-primary);
  font-family: inherit;
  line-height: 1.75;
  white-space: pre-wrap;
}

.detail-tags {
  margin-top: 12px;
  color: var(--text-tertiary);
  font-size: 12px;
}

@media (max-width: 1180px) {
  .metric-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .visual-grid {
    grid-template-columns: 1fr;
  }

  .content-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 820px) {
  .page-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .heading-actions {
    justify-content: flex-start;
  }

  .metric-grid,
  .visual-grid,
  .form-grid {
    grid-template-columns: 1fr;
  }

  .toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .toolbar .el-input,
  .toolbar .el-select,
  .toolbar .el-button {
    width: 100%;
  }
}
</style>
