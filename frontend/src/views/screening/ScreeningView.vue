<template>
  <div>
    <el-tabs v-model="activeTab" type="border-card">
      <!-- 名单筛查 Tab -->
      <el-tab-pane label="名单筛查" name="screening">
        <el-card style="margin-bottom: 16px">
          <el-form :inline="true">
            <el-form-item label="客户ID">
              <el-input v-model="screenForm.customerId" placeholder="输入客户ID" style="width: 180px" />
            </el-form-item>
            <el-form-item label="筛查类型">
              <el-select v-model="screenForm.screeningType" placeholder="选择筛查类型" style="width: 180px">
                <el-option label="客户准入" value="CUSTOMER_ONBOARD" />
                <el-option label="信息变更" value="INFO_CHANGE" />
                <el-option label="交易触发" value="TRANSACTION" />
                <el-option label="定期复核" value="PERIODIC" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="screening" @click="doScreen">触发筛查</el-button>
              <el-button type="success" @click="batchDialogVisible = true">批量筛查</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card>
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span>筛查结果</span>
              <el-button size="small" @click="loadResults">刷新</el-button>
            </div>
          </template>
          <el-table :data="results" stripe v-loading="resultsLoading" border>
            <el-table-column prop="customerName" label="客户" width="120" />
            <el-table-column label="命中名单项" min-width="190" show-overflow-tooltip>
              <template #default="{ row }">
                <div class="watchlist-hit-cell">
                  <span class="watchlist-hit-name">{{ row.watchlistName || '-' }}</span>
                  <el-tag v-if="row.watchlistEntryId" size="small" type="info" effect="plain">
                    ID {{ row.watchlistEntryId }}
                  </el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="matchScore" label="匹配分数" width="100">
              <template #default="{ row }">
                <el-tag :type="row.matchScore >= 95 ? 'danger' : 'warning'" size="small">{{ row.matchScore }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="matchType" label="匹配类型" width="100" />
            <el-table-column prop="matchField" label="匹配字段" width="120">
              <template #default="{ row }">{{ matchFieldLabel(row.matchField) }}</template>
            </el-table-column>
            <el-table-column prop="reviewStatus" label="复核状态" width="120">
              <template #default="{ row }">
                <el-tag :type="reviewStatusTagType(row.reviewStatus)" size="small">
                  {{ reviewStatusLabel(row.reviewStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdTime" label="筛查时间" />
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openMatchExplain(row)">解释</el-button>
                <template v-if="row.reviewStatus === 'PENDING_REVIEW'">
                  <el-button link type="danger" size="small" @click="review(row.id, 'CONFIRMED')">确认命中</el-button>
                  <el-button link type="info" size="small" @click="review(row.id, 'EXCLUDED')">排除</el-button>
                </template>
                <span v-else>-</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 白名单管理 Tab -->
      <el-tab-pane label="白名单管理" name="whitelist">
        <el-card style="margin-bottom: 16px">
          <template #header>添加白名单</template>
          <el-form :model="whitelistForm" :rules="whitelistRules" ref="whitelistFormRef" label-width="80px">
            <el-row :gutter="16">
              <el-col :span="6">
                <el-form-item label="客户ID" prop="customerId">
                  <el-input v-model="whitelistForm.customerId" placeholder="客户ID" />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="客户姓名" prop="customerName">
                  <el-input v-model="whitelistForm.customerName" placeholder="客户姓名" />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="名单项ID" prop="watchlistEntryId">
                  <el-input v-model="whitelistForm.watchlistEntryId" placeholder="名单项ID" />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="排除原因" prop="excludeReason">
                  <el-input v-model="whitelistForm.excludeReason" placeholder="加入白名单原因" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row>
              <el-col :span="24" style="text-align:right">
                <el-button type="primary" :loading="whitelistSubmitting" @click="addWhitelist">添加白名单</el-button>
              </el-col>
            </el-row>
          </el-form>
        </el-card>

        <el-card>
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span>白名单列表</span>
              <el-button size="small" @click="loadWhitelist">刷新</el-button>
            </div>
          </template>
          <el-table :data="whitelist" stripe v-loading="whitelistLoading" border>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="customerId" label="客户ID" width="140" />
            <el-table-column prop="customerName" label="客户姓名" width="140" />
            <el-table-column prop="watchlistEntryId" label="名单项ID" width="140" />
            <el-table-column prop="watchlistName" label="命中名单" min-width="180" show-overflow-tooltip />
            <el-table-column prop="excludeReason" label="排除原因" min-width="200" show-overflow-tooltip />
            <el-table-column prop="reviewStatus" label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" type="success">{{ row.reviewStatus || 'ACTIVE' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="expiryDate" label="失效日期" width="120" />
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-popconfirm title="确认删除该白名单？" @confirm="removeWhitelist(row.id)">
                  <template #reference>
                    <el-button link type="danger" size="small">删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 批量筛查对话框 -->
    <el-dialog v-model="batchDialogVisible" title="批量筛查" width="700px" destroy-on-close>
      <el-table :data="batchList" border size="small">
        <el-table-column label="客户ID" min-width="180">
          <template #default="{ row }">
            <el-input v-model="row.customerId" size="small" placeholder="客户ID" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" @click="batchList.splice($index, 1)" :disabled="batchList.length <= 1">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top:12px">
        <el-button size="small" @click="batchList.push({ customerId: '' })">添加一行</el-button>
      </div>
      <template #footer>
        <el-button @click="batchDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="batchScreening" @click="doBatchScreen">开始批量筛查</el-button>
      </template>
    </el-dialog>

    <!-- 命中解释图 -->
    <el-dialog v-model="explainVisible" title="名单命中解释" width="780px" destroy-on-close>
      <template v-if="explainData">
        <div class="match-explain-summary">
          <div class="explain-stat">
            <span>匹配分数</span>
            <strong>{{ explainData.matchScore ?? 0 }}</strong>
          </div>
          <div class="explain-stat">
            <span>匹配类型</span>
            <strong>{{ explainData.matchType || '-' }}</strong>
          </div>
          <div class="explain-stat">
            <span>复核状态</span>
            <el-tag :type="reviewStatusTagType(explainData.reviewStatus)" size="small">
              {{ reviewStatusLabel(explainData.reviewStatus) }}
            </el-tag>
          </div>
        </div>

        <div class="match-explain-graph">
          <div class="match-party-card customer-card">
            <div class="party-label">客户侧</div>
            <div class="party-name">{{ explainData.customerName || '-' }}</div>
            <div class="party-meta">证件号：{{ explainData.customerIdNumber || '-' }}</div>
          </div>
          <div class="match-score-bridge">
            <div class="score-ring">{{ Math.round(Number(explainData.matchScore || 0)) }}</div>
            <div class="score-line"></div>
            <div class="score-caption">{{ matchFieldLabel(explainData.matchField) }}</div>
          </div>
          <div class="match-party-card watchlist-card">
            <div class="party-label">名单侧</div>
            <div class="party-name">{{ explainData.watchlistName || '-' }}</div>
            <div class="party-meta">名单项ID：{{ explainData.watchlistEntryId || '-' }}</div>
          </div>
        </div>

        <div class="field-match-list">
          <div v-for="field in matchExplainFields(explainData)" :key="field.key" class="field-match-row">
            <div class="field-side">
              <span>{{ field.customerLabel }}</span>
              <strong>{{ field.customerValue }}</strong>
            </div>
            <div class="field-middle">
              <el-progress
                :percentage="field.score"
                :stroke-width="8"
                :show-text="false"
                :color="field.score >= 95 ? '#dc2626' : '#d97706'"
              />
              <em>{{ field.reason }}</em>
            </div>
            <div class="field-side right">
              <span>{{ field.watchlistLabel }}</span>
              <strong>{{ field.watchlistValue }}</strong>
            </div>
          </div>
        </div>

        <el-alert
          class="match-explain-alert"
          :title="matchExplainConclusion(explainData)"
          type="info"
          show-icon
          :closable="false"
        />
      </template>
      <template #footer>
        <el-button @click="explainVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import request from '@/utils/request'

// ============ 通用 ============
const activeTab = ref('screening')

// ============ 名单筛查 ============
const resultsLoading = ref(false)
const screening = ref(false)
const results = ref<any[]>([])
const screenForm = ref({ customerId: '', screeningType: 'CUSTOMER_ONBOARD' })
const explainVisible = ref(false)
const explainData = ref<any>(null)

function mapLabel(map: Record<string, string>, value: unknown) {
  const key = typeof value === 'string' ? value : ''
  return map[key] || key || '-'
}

function reviewStatusTagType(status: unknown): '' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<string, '' | 'success' | 'warning' | 'danger' | 'info'> = {
    PENDING_REVIEW: 'warning',
    CONFIRMED: 'danger',
    EXCLUDED: 'info'
  }
  const key = typeof status === 'string' ? status : ''
  return map[key] || 'info'
}

function reviewStatusLabel(status: unknown) {
  return mapLabel({ PENDING_REVIEW: '待复核', CONFIRMED: '已确认', EXCLUDED: '已排除' }, status)
}

function matchFieldLabel(matchField: unknown) {
  const value = typeof matchField === 'string' ? matchField : ''
  if (!value) return '-'
  const fieldMap: Record<string, string> = {
    name: '姓名',
    alias: '别名',
    id_number: '证件号'
  }
  return value.split(',').map(item => fieldMap[item.trim()] || item.trim()).filter(Boolean).join('、')
}

function openMatchExplain(row: any) {
  explainData.value = row
  explainVisible.value = true
}

function matchExplainFields(row: any) {
  const fields = String(row?.matchField || '')
    .split(',')
    .map(item => item.trim())
    .filter(Boolean)
  const score = Math.max(0, Math.min(100, Math.round(Number(row?.matchScore || 0))))
  const fieldMap: Record<string, { customerLabel: string; watchlistLabel: string; customerValue: string; watchlistValue: string }> = {
    name: {
      customerLabel: '客户姓名',
      watchlistLabel: '名单姓名',
      customerValue: row?.customerName || '-',
      watchlistValue: row?.watchlistName || '-'
    },
    alias: {
      customerLabel: '客户姓名/别名',
      watchlistLabel: '名单别名',
      customerValue: row?.customerName || '-',
      watchlistValue: row?.watchlistName || '-'
    },
    id_number: {
      customerLabel: '客户证件号',
      watchlistLabel: '名单证件号',
      customerValue: row?.customerIdNumber || '-',
      watchlistValue: row?.matchDetail || row?.customerIdNumber || '-'
    }
  }
  const normalizedFields = fields.length ? fields : ['name']
  return normalizedFields.map(field => {
    const item = fieldMap[field] || {
      customerLabel: field,
      watchlistLabel: field,
      customerValue: row?.customerName || '-',
      watchlistValue: row?.watchlistName || '-'
    }
    return {
      key: field,
      ...item,
      score,
      reason: row?.matchType === 'EXACT' ? '精确匹配' : row?.matchType === 'FUZZY' ? '模糊匹配' : '综合匹配'
    }
  })
}

function matchExplainConclusion(row: any) {
  const score = Number(row?.matchScore || 0)
  const fields = matchFieldLabel(row?.matchField)
  if (score >= 95) return `高置信命中：${fields} 与名单项高度一致，建议优先人工复核身份材料。`
  if (score >= 80) return `中高置信命中：${fields} 存在相似特征，建议结合证件、国籍和出生日期排除误报。`
  return `低置信命中：${fields} 仅存在弱相似特征，建议作为一般复核线索处理。`
}

async function loadResults() {
  resultsLoading.value = true
  try {
    const res: any = await request.get('/screening/results', { params: { page: 1, size: 50 } })
    results.value = res.data?.list || []
  } catch (e) { /* handled */ } finally { resultsLoading.value = false }
}

async function doScreen() {
  const customerId = Number(screenForm.value.customerId)
  if (!Number.isFinite(customerId) || customerId <= 0) {
    ElMessage.warning('请输入有效客户ID')
    return
  }
  screening.value = true
  try {
    await request.post('/screening/screen', {
      customerId,
      screeningType: screenForm.value.screeningType
    })
    ElMessage.success('筛查完成')
    await loadResults()
  } catch (e) { /* handled */ } finally { screening.value = false }
}

async function review(id: number, status: string) {
  try {
    await request.post('/screening/review', { resultId: id, reviewStatus: status, reviewReason: '' })
    ElMessage.success('复核完成')
    await loadResults()
  } catch (e) { /* handled */ }
}

// ============ 批量筛查 ============
const batchDialogVisible = ref(false)
const batchScreening = ref(false)
const batchList = ref<{ customerId: string }[]>([
  { customerId: '' }
])

async function doBatchScreen() {
  const customerIds = batchList.value
    .map(item => Number(item.customerId))
    .filter(id => Number.isFinite(id) && id > 0)
  if (customerIds.length === 0) {
    ElMessage.warning('请至少填写一个有效客户ID')
    return
  }
  batchScreening.value = true
  try {
    await request.post('/screening/batch-screen', customerIds)
    ElMessage.success(`批量筛查完成，共${customerIds.length}条`)
    batchDialogVisible.value = false
    batchList.value = [{ customerId: '' }]
    await loadResults()
  } catch (e) { /* handled */ } finally { batchScreening.value = false }
}

// ============ 白名单管理 ============
const whitelistLoading = ref(false)
const whitelistSubmitting = ref(false)
const whitelist = ref<any[]>([])
const whitelistFormRef = ref<FormInstance>()
const whitelistForm = reactive({
  customerId: '',
  customerName: '',
  watchlistEntryId: '',
  watchlistName: '',
  excludeReason: ''
})
const whitelistRules: FormRules = {
  customerId: [{ required: true, message: '请输入客户ID', trigger: 'blur' }],
  customerName: [{ required: true, message: '请输入客户姓名', trigger: 'blur' }],
  watchlistEntryId: [{ required: true, message: '请输入名单项ID', trigger: 'blur' }],
  excludeReason: [{ required: true, message: '请输入排除原因', trigger: 'blur' }]
}

async function loadWhitelist() {
  whitelistLoading.value = true
  try {
    const res: any = await request.get('/screening/whitelist')
    whitelist.value = res.data?.list || res.data || []
  } catch (e) { /* handled */ } finally { whitelistLoading.value = false }
}

async function addWhitelist() {
  if (!whitelistFormRef.value) return
  await whitelistFormRef.value.validate()
  const customerId = Number(whitelistForm.customerId)
  const watchlistEntryId = Number(whitelistForm.watchlistEntryId)
  if (!Number.isFinite(customerId) || customerId <= 0 || !Number.isFinite(watchlistEntryId) || watchlistEntryId <= 0) {
    ElMessage.warning('请输入有效的客户ID和名单项ID')
    return
  }
  whitelistSubmitting.value = true
  try {
    const today = new Date()
    const expiry = new Date(today)
    expiry.setFullYear(today.getFullYear() + 1)
    await request.post('/screening/whitelist', {
      customerId,
      customerName: whitelistForm.customerName,
      watchlistEntryId,
      watchlistName: whitelistForm.watchlistName || whitelistForm.customerName,
      excludeReason: whitelistForm.excludeReason,
      evidence: '人工复核确认同名不同人',
      effectiveDate: today.toISOString().slice(0, 10),
      expiryDate: expiry.toISOString().slice(0, 10),
      approvedBy: 'admin',
      approvedTime: today.toISOString().slice(0, 19),
      reviewStatus: 'ACTIVE'
    })
    ElMessage.success('白名单添加成功')
    whitelistForm.customerId = ''
    whitelistForm.customerName = ''
    whitelistForm.watchlistEntryId = ''
    whitelistForm.watchlistName = ''
    whitelistForm.excludeReason = ''
    await loadWhitelist()
  } catch (e) { /* handled */ } finally { whitelistSubmitting.value = false }
}

async function removeWhitelist(id: number) {
  try {
    await request.delete(`/screening/whitelist/${id}`)
    ElMessage.success('删除成功')
    await loadWhitelist()
  } catch (e) { /* handled */ }
}

// ============ 初始化 ============
onMounted(() => {
  loadResults()
  loadWhitelist()
})
</script>

<style scoped>
.watchlist-hit-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.watchlist-hit-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.match-explain-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.explain-stat {
  min-height: 70px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #f8fafc;
}

.explain-stat span {
  display: block;
  margin-bottom: 8px;
  color: #64748b;
  font-size: 12px;
}

.explain-stat strong {
  color: #111827;
  font-size: 22px;
  line-height: 1.2;
}

.match-explain-graph {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 190px minmax(0, 1fr);
  gap: 14px;
  align-items: stretch;
}

.match-party-card {
  min-height: 132px;
  padding: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
}

.customer-card {
  border-left: 4px solid #2563eb;
}

.watchlist-card {
  border-left: 4px solid #dc2626;
}

.party-label {
  color: #64748b;
  font-size: 12px;
}

.party-name {
  margin-top: 10px;
  color: #111827;
  font-size: 18px;
  font-weight: 700;
  word-break: break-word;
}

.party-meta {
  margin-top: 8px;
  color: #64748b;
  font-size: 12px;
  word-break: break-word;
}

.match-score-bridge {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 132px;
}

.score-ring {
  width: 66px;
  height: 66px;
  border: 6px solid #dc2626;
  border-radius: 50%;
  color: #111827;
  font-size: 20px;
  font-weight: 800;
  line-height: 54px;
  text-align: center;
  background: #fff;
  box-shadow: 0 8px 22px rgba(220, 38, 38, 0.16);
}

.score-line {
  width: 100%;
  height: 2px;
  margin: 12px 0 8px;
  background: linear-gradient(90deg, #2563eb, #dc2626);
}

.score-caption {
  color: #475569;
  font-size: 12px;
  text-align: center;
}

.field-match-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 14px;
}

.field-match-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 180px minmax(0, 1fr);
  gap: 12px;
  align-items: center;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fbfdff;
}

.field-side span {
  display: block;
  color: #64748b;
  font-size: 12px;
}

.field-side strong {
  display: block;
  margin-top: 5px;
  color: #111827;
  font-size: 13px;
  word-break: break-word;
}

.field-side.right {
  text-align: right;
}

.field-middle em {
  display: block;
  margin-top: 6px;
  color: #64748b;
  font-size: 12px;
  font-style: normal;
  text-align: center;
}

.match-explain-alert {
  margin-top: 14px;
}

@media (max-width: 900px) {
  .match-explain-summary,
  .match-explain-graph,
  .field-match-row {
    grid-template-columns: 1fr;
  }

  .field-side.right {
    text-align: left;
  }
}
</style>
