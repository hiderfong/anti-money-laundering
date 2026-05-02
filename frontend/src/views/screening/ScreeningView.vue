<template>
  <div>
    <el-tabs v-model="activeTab" type="border-card">
      <!-- 名单筛查 Tab -->
      <el-tab-pane label="名单筛查" name="screening">
        <el-card style="margin-bottom: 16px">
          <el-form :inline="true">
            <el-form-item label="姓名"><el-input v-model="screenForm.name" placeholder="客户姓名" /></el-form-item>
            <el-form-item label="证件类型">
              <el-select v-model="screenForm.idType" placeholder="选择证件类型">
                <el-option label="身份证" value="ID_CARD" />
                <el-option label="护照" value="PASSPORT" />
                <el-option label="营业执照" value="BUSINESS_LICENSE" />
              </el-select>
            </el-form-item>
            <el-form-item label="证件号码"><el-input v-model="screenForm.idNumber" placeholder="证件号码" /></el-form-item>
            <el-form-item label="国籍"><el-input v-model="screenForm.nationality" placeholder="国籍" /></el-form-item>
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
            <el-table-column prop="matchName" label="命中名单" width="150" />
            <el-table-column prop="matchScore" label="匹配分数" width="100">
              <template #default="{ row }">
                <el-tag :type="row.matchScore >= 95 ? 'danger' : 'warning'" size="small">{{ row.matchScore }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="matchType" label="匹配类型" width="100" />
            <el-table-column prop="sourceList" label="来源名单" width="120" />
            <el-table-column prop="reviewStatus" label="复核状态" width="120">
              <template #default="{ row }">
                <el-tag :type="reviewStatusTagType(row.reviewStatus)" size="small">
                  {{ reviewStatusLabel(row.reviewStatus) }}
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
      </el-tab-pane>

      <!-- 白名单管理 Tab -->
      <el-tab-pane label="白名单管理" name="whitelist">
        <el-card style="margin-bottom: 16px">
          <template #header>添加白名单</template>
          <el-form :model="whitelistForm" :rules="whitelistRules" ref="whitelistFormRef" label-width="80px">
            <el-row :gutter="16">
              <el-col :span="6">
                <el-form-item label="姓名" prop="name">
                  <el-input v-model="whitelistForm.name" placeholder="姓名" />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="证件类型" prop="idType">
                  <el-select v-model="whitelistForm.idType" placeholder="选择证件类型" style="width:100%">
                    <el-option label="身份证" value="ID_CARD" />
                    <el-option label="护照" value="PASSPORT" />
                    <el-option label="营业执照" value="BUSINESS_LICENSE" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="证件号码" prop="idNumber">
                  <el-input v-model="whitelistForm.idNumber" placeholder="证件号码" />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="原因" prop="reason">
                  <el-input v-model="whitelistForm.reason" placeholder="加入白名单原因" />
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
            <el-table-column prop="name" label="姓名" width="120" />
            <el-table-column prop="idType" label="证件类型" width="120">
              <template #default="{ row }">
                {{ idTypeLabel(row.idType) }}
              </template>
            </el-table-column>
            <el-table-column prop="idNumber" label="证件号码" width="180" />
            <el-table-column prop="reason" label="原因" />
            <el-table-column prop="createdTime" label="添加时间" width="180" />
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
        <el-table-column label="姓名" min-width="120">
          <template #default="{ row }">
            <el-input v-model="row.name" size="small" placeholder="姓名" />
          </template>
        </el-table-column>
        <el-table-column label="证件类型" width="150">
          <template #default="{ row }">
            <el-select v-model="row.idType" size="small" placeholder="选择">
              <el-option label="身份证" value="ID_CARD" />
              <el-option label="护照" value="PASSPORT" />
              <el-option label="营业执照" value="BUSINESS_LICENSE" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="证件号码" min-width="150">
          <template #default="{ row }">
            <el-input v-model="row.idNumber" size="small" placeholder="证件号码" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" @click="batchList.splice($index, 1)" :disabled="batchList.length <= 1">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top:12px">
        <el-button size="small" @click="batchList.push({ name:'', idType:'ID_CARD', idNumber:'' })">添加一行</el-button>
      </div>
      <template #footer>
        <el-button @click="batchDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="batchScreening" @click="doBatchScreen">开始批量筛查</el-button>
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
const screenForm = ref({ name: '', idType: 'ID_CARD', idNumber: '', nationality: '' })

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

function idTypeLabel(idType: unknown) {
  return mapLabel({ ID_CARD: '身份证', PASSPORT: '护照', BUSINESS_LICENSE: '营业执照' }, idType)
}

async function loadResults() {
  resultsLoading.value = true
  try {
    const res: any = await request.get('/screening/results', { params: { page: 1, size: 50 } })
    results.value = res.data?.list || []
  } catch (e) { /* handled */ } finally { resultsLoading.value = false }
}

async function doScreen() {
  if (!screenForm.value.name) {
    ElMessage.warning('请输入客户姓名')
    return
  }
  screening.value = true
  try {
    await request.post('/screening/screen', screenForm.value)
    ElMessage.success('筛查完成')
    loadResults()
  } catch (e) { /* handled */ } finally { screening.value = false }
}

async function review(id: number, status: string) {
  try {
    await request.post('/screening/review', { resultId: id, reviewStatus: status, remark: '' })
    ElMessage.success('复核完成')
    loadResults()
  } catch (e) { /* handled */ }
}

// ============ 批量筛查 ============
const batchDialogVisible = ref(false)
const batchScreening = ref(false)
const batchList = ref<{ name: string; idType: string; idNumber: string }[]>([
  { name: '', idType: 'ID_CARD', idNumber: '' }
])

async function doBatchScreen() {
  const validItems = batchList.value.filter(item => item.name && item.idNumber)
  if (validItems.length === 0) {
    ElMessage.warning('请至少填写一条完整的筛查记录（姓名+证件号码）')
    return
  }
  batchScreening.value = true
  try {
    await request.post('/screening/batch-screen', { customers: validItems })
    ElMessage.success(`批量筛查完成，共${validItems.length}条`)
    batchDialogVisible.value = false
    batchList.value = [{ name: '', idType: 'ID_CARD', idNumber: '' }]
    loadResults()
  } catch (e) { /* handled */ } finally { batchScreening.value = false }
}

// ============ 白名单管理 ============
const whitelistLoading = ref(false)
const whitelistSubmitting = ref(false)
const whitelist = ref<any[]>([])
const whitelistFormRef = ref<FormInstance>()
const whitelistForm = reactive({ name: '', idType: 'ID_CARD', idNumber: '', reason: '' })
const whitelistRules: FormRules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  idType: [{ required: true, message: '请选择证件类型', trigger: 'change' }],
  idNumber: [{ required: true, message: '请输入证件号码', trigger: 'blur' }],
  reason: [{ required: true, message: '请输入原因', trigger: 'blur' }]
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
  whitelistSubmitting.value = true
  try {
    await request.post('/screening/whitelist', { ...whitelistForm })
    ElMessage.success('白名单添加成功')
    whitelistForm.name = ''
    whitelistForm.idType = 'ID_CARD'
    whitelistForm.idNumber = ''
    whitelistForm.reason = ''
    loadWhitelist()
  } catch (e) { /* handled */ } finally { whitelistSubmitting.value = false }
}

async function removeWhitelist(id: number) {
  try {
    await request.delete(`/screening/whitelist/${id}`)
    ElMessage.success('删除成功')
    loadWhitelist()
  } catch (e) { /* handled */ }
}

// ============ 初始化 ============
onMounted(() => {
  loadResults()
  loadWhitelist()
})
</script>
