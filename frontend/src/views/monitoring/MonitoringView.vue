<template>
  <div class="monitoring-container">
    <el-tabs v-model="activeTab" type="border-card">
      <!-- ========== 交易监测 ========== -->
      <el-tab-pane label="交易监测" name="transactions">
        <!-- 搜索筛选区 -->
        <el-form :inline="true" :model="txQuery" style="margin-bottom:16px">
          <el-form-item label="交易编号">
            <el-input v-model="txQuery.transactionNo" placeholder="交易编号" clearable style="width:180px" />
          </el-form-item>
          <el-form-item label="交易类型">
            <el-select v-model="txQuery.transactionType" placeholder="全部" clearable style="width:140px">
              <el-option v-for="(label, key) in typeMap" :key="key" :label="label" :value="key" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="txQuery.status" placeholder="全部" clearable style="width:120px">
              <el-option label="成功" value="SUCCESS" />
              <el-option label="失败" value="FAILED" />
              <el-option label="处理中" value="PENDING" />
            </el-select>
          </el-form-item>
          <el-form-item label="日期范围">
            <el-date-picker v-model="txQuery.dateRange" type="daterange" start-placeholder="开始日期"
              end-placeholder="结束日期" value-format="YYYY-MM-DD" style="width:260px" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="txPage = 1; loadTransactions()">查询</el-button>
            <el-button @click="resetTxQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <!-- 交易列表 -->
        <el-table :data="transactions" stripe v-loading="txLoading" border>
          <el-table-column prop="transactionNo" label="交易编号" width="180" />
          <el-table-column prop="customerId" label="客户ID" width="120" />
          <el-table-column prop="transactionType" label="类型" width="120">
            <template #default="{ row }">
              <el-tag size="small">{{ typeMap[row.transactionType] || row.transactionType }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="amount" label="金额" width="130" align="right">
            <template #default="{ row }">{{ Number(row.amount).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="currency" label="币种" width="80" />
          <el-table-column prop="paymentMethod" label="支付方式" width="100" />
          <el-table-column prop="status" label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="transactionTime" label="交易时间" min-width="170" />
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openTxDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-model:current-page="txPage"
          v-model:page-size="txSize"
          :total="txTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          style="margin-top:16px;justify-content:flex-end"
          @current-change="loadTransactions"
          @size-change="txPage = 1; loadTransactions()"
        />
      </el-tab-pane>

      <!-- ========== 规则管理 ========== -->
      <el-tab-pane label="规则管理" name="rules">
        <div style="margin-bottom:16px;display:flex;justify-content:space-between;align-items:center">
          <el-form :inline="true" :model="ruleQuery" style="margin-bottom:0">
            <el-form-item label="规则名称">
              <el-input v-model="ruleQuery.ruleName" placeholder="规则名称" clearable style="width:180px" />
            </el-form-item>
            <el-form-item label="规则类型">
              <el-select v-model="ruleQuery.ruleType" placeholder="全部" clearable style="width:140px">
                <el-option label="金额阈值" value="AMOUNT_THRESHOLD" />
                <el-option label="频率检测" value="FREQUENCY" />
                <el-option label="模式匹配" value="PATTERN" />
                <el-option label="组合规则" value="COMPOSITE" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="rulePage = 1; loadRules()">查询</el-button>
              <el-button @reset="resetRuleQuery" @click="resetRuleQuery">重置</el-button>
            </el-form-item>
          </el-form>
          <el-button type="primary" @click="openRuleDialog('create')">+ 新建规则</el-button>
        </div>

        <el-table :data="rules" stripe v-loading="ruleLoading" border>
          <el-table-column prop="ruleCode" label="规则编码" width="150" />
          <el-table-column prop="ruleName" label="规则名称" min-width="160" />
          <el-table-column prop="ruleType" label="规则类型" width="120">
            <template #default="{ row }">
              <el-tag size="small">{{ ruleTypeLabel(row.ruleType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="ruleExpression" label="表达式" min-width="220" show-overflow-tooltip />
          <el-table-column prop="riskScore" label="风险分" width="90" align="center" />
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-switch
                :model-value="row.isEnabled"
                :active-value="1"
                :inactive-value="0"
                active-text="启用"
                inactive-text="禁用"
                inline-prompt
                @change="(val: number) => toggleRule(row, val)"
              />
            </template>
          </el-table-column>
          <el-table-column prop="createdTime" label="创建时间" width="170" />
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openRuleDialog('edit', row)">编辑</el-button>
              <el-button link type="primary" size="small" @click="openVersionHistory(row)">版本历史</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-model:current-page="rulePage"
          v-model:page-size="ruleSize"
          :total="ruleTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          style="margin-top:16px;justify-content:flex-end"
          @current-change="loadRules"
          @size-change="rulePage = 1; loadRules()"
        />
      </el-tab-pane>
    </el-tabs>

    <!-- ========== 交易详情弹窗 ========== -->
    <el-dialog v-model="txDetailVisible" title="交易详情" width="600px" destroy-on-close>
      <el-descriptions :column="2" border v-if="txDetail">
        <el-descriptions-item label="交易编号">{{ txDetail.transactionNo }}</el-descriptions-item>
        <el-descriptions-item label="客户ID">{{ txDetail.customerId }}</el-descriptions-item>
        <el-descriptions-item label="交易类型">{{ typeMap[txDetail.transactionType] || txDetail.transactionType }}</el-descriptions-item>
        <el-descriptions-item label="金额">{{ txDetail.currency }} {{ Number(txDetail.amount).toFixed(2) }}</el-descriptions-item>
        <el-descriptions-item label="支付方式">{{ txDetail.paymentMethod }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(txDetail.status)" size="small">{{ statusLabel(txDetail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="交易时间" :span="2">{{ txDetail.transactionTime }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- ========== 规则创建/编辑弹窗 ========== -->
    <el-dialog
      v-model="ruleDialogVisible"
      :title="ruleDialogMode === 'create' ? '新建规则' : '编辑规则'"
      width="640px"
      destroy-on-close
    >
      <el-form ref="ruleFormRef" :model="ruleForm" :rules="ruleFormRules" label-width="100px">
        <el-form-item label="规则编码" prop="ruleCode">
          <el-input v-model="ruleForm.ruleCode" placeholder="如 RULE_001" :disabled="ruleDialogMode === 'edit'" />
        </el-form-item>
        <el-form-item label="规则名称" prop="ruleName">
          <el-input v-model="ruleForm.ruleName" placeholder="请输入规则名称" />
        </el-form-item>
        <el-form-item label="规则类型" prop="ruleType">
          <el-select v-model="ruleForm.ruleType" placeholder="请选择规则类型" style="width:100%">
            <el-option label="金额阈值" value="AMOUNT_THRESHOLD" />
            <el-option label="频率检测" value="FREQUENCY" />
            <el-option label="模式匹配" value="PATTERN" />
            <el-option label="组合规则" value="COMPOSITE" />
          </el-select>
        </el-form-item>
        <el-form-item label="规则表达式" prop="ruleExpression">
          <el-input
            v-model="ruleForm.ruleExpression"
            type="textarea"
            :rows="4"
            placeholder='如: amount > 50000 && frequency > 5'
          />
        </el-form-item>
        <el-form-item label="风险评分" prop="riskScore">
          <el-slider v-model="ruleForm.riskScore" :min="1" :max="100" :step="1" show-input />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="ruleSubmitting" @click="submitRule">确定</el-button>
      </template>
    </el-dialog>

    <!-- ========== 版本历史弹窗 ========== -->
    <el-dialog v-model="versionDialogVisible" title="规则版本历史" width="720px" destroy-on-close>
      <el-table :data="versions" stripe v-loading="versionLoading" border>
        <el-table-column prop="id" label="版本ID" width="80" />
        <el-table-column prop="ruleName" label="规则名称" width="150" />
        <el-table-column prop="ruleExpression" label="表达式" min-width="200" show-overflow-tooltip />
        <el-table-column prop="riskScore" label="风险分" width="80" align="center" />
        <el-table-column prop="createdTime" label="修改时间" width="170" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import request from '@/utils/request'

const activeTab = ref('transactions')

// ==================== 交易监测 ====================
const txLoading = ref(false)
const transactions = ref<any[]>([])
const txPage = ref(1)
const txSize = ref(10)
const txTotal = ref(0)
const typeMap: Record<string, string> = {
  PREMIUM: '保费缴纳', SURRENDER: '退保', CLAIM: '理赔', LOAN: '保单贷款', REPAYMENT: '还款'
}

const txQuery = reactive({
  transactionNo: '',
  transactionType: '',
  status: '',
  dateRange: null as [string, string] | null
})

function resetTxQuery() {
  txQuery.transactionNo = ''
  txQuery.transactionType = ''
  txQuery.status = ''
  txQuery.dateRange = null
  txPage.value = 1
  loadTransactions()
}

function statusTagType(status: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

function statusLabel(status: string) {
  const map: Record<string, string> = { SUCCESS: '成功', FAILED: '失败', PENDING: '处理中' }
  return map[status] || status
}

async function loadTransactions() {
  txLoading.value = true
  try {
    const params: any = { page: txPage.value, size: txSize.value }
    if (txQuery.transactionNo) params.transactionNo = txQuery.transactionNo
    if (txQuery.transactionType) params.transactionType = txQuery.transactionType
    if (txQuery.status) params.status = txQuery.status
    if (txQuery.dateRange) {
      params.startDate = txQuery.dateRange[0]
      params.endDate = txQuery.dateRange[1]
    }
    const res: any = await request.get('/monitoring/transactions/page', { params })
    transactions.value = res.data?.list || []
    txTotal.value = res.data?.total || 0
  } catch (e) { /* handled */ } finally { txLoading.value = false }
}

// 交易详情
const txDetailVisible = ref(false)
const txDetail = ref<any>(null)

function openTxDetail(row: any) {
  txDetail.value = row
  txDetailVisible.value = true
}

// ==================== 规则管理 ====================
const ruleLoading = ref(false)
const rules = ref<any[]>([])
const rulePage = ref(1)
const ruleSize = ref(10)
const ruleTotal = ref(0)

const ruleQuery = reactive({ ruleName: '', ruleType: '' })

function resetRuleQuery() {
  ruleQuery.ruleName = ''
  ruleQuery.ruleType = ''
  rulePage.value = 1
  loadRules()
}

function ruleTypeLabel(type: string) {
  const map: Record<string, string> = {
    AMOUNT_THRESHOLD: '金额阈值', FREQUENCY: '频率检测', PATTERN: '模式匹配', COMPOSITE: '组合规则'
  }
  return map[type] || type
}

async function loadRules() {
  ruleLoading.value = true
  try {
    const params: any = { page: rulePage.value, size: ruleSize.value }
    if (ruleQuery.ruleName) params.ruleName = ruleQuery.ruleName
    if (ruleQuery.ruleType) params.ruleType = ruleQuery.ruleType
    const res: any = await request.get('/monitoring/rules/page', { params })
    rules.value = res.data?.list || []
    ruleTotal.value = res.data?.total || 0
  } catch (e) { /* handled */ } finally { ruleLoading.value = false }
}

// 启用/禁用规则
async function toggleRule(row: any, val: number) {
  const action = val === 1 ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确定要${action}规则「${row.ruleName}」吗？`, '确认', { type: 'warning' })
    const url = `/monitoring/rules/${row.id}/${val === 1 ? 'enable' : 'disable'}`
    await request.post(url)
    ElMessage.success(`已${action}`)
    loadRules()
  } catch (e) { /* cancelled or error */ }
}

// 规则创建/编辑弹窗
const ruleDialogVisible = ref(false)
const ruleDialogMode = ref<'create' | 'edit'>('create')
const ruleSubmitting = ref(false)
const ruleFormRef = ref<FormInstance>()
const ruleForm = reactive({
  id: null as number | null,
  ruleCode: '',
  ruleName: '',
  ruleType: '',
  ruleExpression: '',
  riskScore: 50
})

const ruleFormRules: FormRules = {
  ruleCode: [{ required: true, message: '请输入规则编码', trigger: 'blur' }],
  ruleName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  ruleType: [{ required: true, message: '请选择规则类型', trigger: 'change' }],
  ruleExpression: [{ required: true, message: '请输入规则表达式', trigger: 'blur' }],
  riskScore: [{ required: true, message: '请设置风险评分', trigger: 'change' }]
}

function openRuleDialog(mode: 'create' | 'edit', row?: any) {
  ruleDialogMode.value = mode
  if (mode === 'edit' && row) {
    ruleForm.id = row.id
    ruleForm.ruleCode = row.ruleCode
    ruleForm.ruleName = row.ruleName
    ruleForm.ruleType = row.ruleType
    ruleForm.ruleExpression = row.ruleExpression
    ruleForm.riskScore = row.riskScore
  } else {
    ruleForm.id = null
    ruleForm.ruleCode = ''
    ruleForm.ruleName = ''
    ruleForm.ruleType = ''
    ruleForm.ruleExpression = ''
    ruleForm.riskScore = 50
  }
  ruleDialogVisible.value = true
}

async function submitRule() {
  if (!ruleFormRef.value) return
  await ruleFormRef.value.validate()
  ruleSubmitting.value = true
  try {
    const payload = { ...ruleForm }
    if (ruleDialogMode.value === 'create') {
      await request.post('/monitoring/rules', payload)
      ElMessage.success('规则创建成功')
    } else {
      await request.put('/monitoring/rules', payload)
      ElMessage.success('规则更新成功')
    }
    ruleDialogVisible.value = false
    loadRules()
  } catch (e) { /* handled */ } finally { ruleSubmitting.value = false }
}

// 版本历史
const versionDialogVisible = ref(false)
const versionLoading = ref(false)
const versions = ref<any[]>([])

async function openVersionHistory(row: any) {
  versionDialogVisible.value = true
  versionLoading.value = true
  try {
    const res: any = await request.get(`/monitoring/rules/${row.id}/versions`)
    versions.value = res.data || []
  } catch (e) { /* handled */ } finally { versionLoading.value = false }
}

// ==================== 初始化 ====================
onMounted(() => {
  loadTransactions()
  loadRules()
})
</script>

<style scoped>
.monitoring-container {
  padding: 4px;
}
</style>
