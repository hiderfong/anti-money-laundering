<template>
  <div>
    <el-card>
      <template #header>
        <span style="font-size: 18px; font-weight: 600;">风险自评估管理</span>
      </template>

      <el-tabs v-model="activeTab">
        <!-- Tab 1: 自评估管理 -->
        <el-tab-pane label="自评估管理" name="assessment">
          <div style="margin-bottom: 16px; display: flex; justify-content: space-between; align-items: center;">
            <span style="font-size: 16px; font-weight: 500;">评估列表</span>
            <el-button type="primary" @click="showCreateAssessment = true">
              <el-icon><Plus /></el-icon> 创建评估
            </el-button>
          </div>

          <el-table :data="assessments" stripe v-loading="assessmentLoading" border>
            <el-table-column prop="assessmentName" label="评估名称" min-width="160" show-overflow-tooltip />
            <el-table-column prop="assessmentType" label="评估类型" width="120" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="assessmentStatusType(row.status)" size="small">
                  {{ assessmentStatusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="totalScore" label="总分" width="80" align="center">
              <template #default="{ row }">
                {{ row.totalScore ?? '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="assessorId" label="评估人" width="100" />
            <el-table-column prop="approverId" label="审批人" width="100" />
            <el-table-column prop="startTime" label="开始时间" width="170" />
            <el-table-column prop="endTime" label="结束时间" width="170" />
            <el-table-column prop="createdTime" label="创建时间" width="170" />
            <el-table-column label="操作" width="260" fixed="right">
              <template #default="{ row }">
                <el-button size="small" @click="viewAssessmentDetail(row.id)">详情</el-button>
                <el-button size="small" type="warning" @click="openScoreDialog(row)" v-if="row.status === 'CREATED'">评分</el-button>
                <el-button size="small" type="success" @click="completeAssessment(row.id)" v-if="row.status === 'SCORED'">完成</el-button>
                <el-button size="small" type="primary" @click="approveAssessment(row.id)" v-if="row.status === 'COMPLETED'">审批</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- Tab 2: 整改任务 -->
        <el-tab-pane label="整改任务" name="rectification">
          <div style="margin-bottom: 16px; display: flex; justify-content: space-between; align-items: center;">
            <span style="font-size: 16px; font-weight: 500;">整改任务列表</span>
            <el-button type="primary" @click="showCreateRectification = true">
              <el-icon><Plus /></el-icon> 创建整改任务
            </el-button>
          </div>

          <el-table :data="rectifications" stripe v-loading="rectLoading" border>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="assessmentId" label="关联评估ID" width="110" />
            <el-table-column prop="issueDescription" label="问题描述" min-width="200" show-overflow-tooltip />
            <el-table-column prop="rectificationMeasure" label="整改措施" min-width="200" show-overflow-tooltip />
            <el-table-column prop="responsiblePerson" label="责任人" width="100" />
            <el-table-column prop="deadline" label="截止日期" width="120" />
            <el-table-column prop="status" label="状态" width="120">
              <template #default="{ row }">
                <el-tag :type="rectStatusType(row.status)" size="small">
                  {{ rectStatusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="verifyResult" label="验证结果" width="120">
              <template #default="{ row }">
                {{ row.verifyResult ?? '-' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="240" fixed="right">
              <template #default="{ row }">
                <el-dropdown @command="(cmd: string) => updateRectStatus(row.id, cmd)" v-if="row.status !== 'VERIFIED'">
                  <el-button size="small" type="warning">
                    更新状态 <el-icon class="el-icon--right"><ArrowDown /></el-icon>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="IN_PROGRESS" v-if="row.status === 'PENDING'">进行中</el-dropdown-item>
                      <el-dropdown-item command="COMPLETED" v-if="row.status === 'IN_PROGRESS'">已完成</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
                <el-button size="small" type="success" @click="verifyRectification(row.id)" v-if="row.status === 'COMPLETED'">验证</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 创建评估弹窗 -->
    <el-dialog v-model="showCreateAssessment" title="创建评估" width="560px" destroy-on-close>
      <el-form :model="assessmentForm" label-width="100px" ref="assessmentFormRef" :rules="assessmentRules">
        <el-form-item label="评估名称" prop="assessmentName">
          <el-input v-model="assessmentForm.assessmentName" placeholder="请输入评估名称" />
        </el-form-item>
        <el-form-item label="评估类型" prop="assessmentType">
          <el-select v-model="assessmentForm.assessmentType" placeholder="请选择评估类型" style="width: 100%;">
            <el-option label="年度评估" value="ANNUAL" />
            <el-option label="季度评估" value="QUARTERLY" />
            <el-option label="专项评估" value="SPECIAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="评估人ID" prop="assessorId">
          <el-input v-model="assessmentForm.assessorId" placeholder="请输入评估人ID" />
        </el-form-item>
        <el-form-item label="审批人ID" prop="approverId">
          <el-input v-model="assessmentForm.approverId" placeholder="请输入审批人ID" />
        </el-form-item>
        <el-form-item label="开始时间" prop="startTime">
          <el-date-picker v-model="assessmentForm.startTime" type="datetime" placeholder="选择开始时间" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="结束时间" prop="endTime">
          <el-date-picker v-model="assessmentForm.endTime" type="datetime" placeholder="选择结束时间" style="width: 100%;" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateAssessment = false">取消</el-button>
        <el-button type="primary" @click="submitAssessment" :loading="submitting">确认创建</el-button>
      </template>
    </el-dialog>

    <!-- 评分弹窗 -->
    <el-dialog v-model="showScoreDialog" title="评估评分" width="500px" destroy-on-close>
      <el-form :model="scoreForm" label-width="100px">
        <el-form-item label="评估名称">
          <el-input :model-value="scoreForm.assessmentName" disabled />
        </el-form-item>
        <el-form-item label="评分" prop="totalScore" :rules="[{ required: true, message: '请输入评分', trigger: 'blur' }]">
          <el-input-number v-model="scoreForm.totalScore" :min="0" :max="100" :step="1" style="width: 100%;" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showScoreDialog = false">取消</el-button>
        <el-button type="primary" @click="submitScore" :loading="submitting">确认评分</el-button>
      </template>
    </el-dialog>

    <!-- 评估详情弹窗 -->
    <el-dialog v-model="showDetailDialog" title="评估详情" width="680px" destroy-on-close>
      <template v-if="detailData">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="评估ID">{{ detailData.id }}</el-descriptions-item>
          <el-descriptions-item label="评估名称">{{ detailData.assessmentName }}</el-descriptions-item>
          <el-descriptions-item label="评估类型">{{ detailData.assessmentType }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="assessmentStatusType(detailData.status)" size="small">
              {{ assessmentStatusLabel(detailData.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="评估人">{{ detailData.assessorId }}</el-descriptions-item>
          <el-descriptions-item label="审批人">{{ detailData.approverId ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ detailData.startTime }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ detailData.endTime }}</el-descriptions-item>
          <el-descriptions-item label="创建时间" :span="2">{{ detailData.createdTime }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">评分结果</el-divider>

        <el-descriptions :column="2" border v-if="detailData.status !== 'CREATED'">
          <el-descriptions-item label="总评分">
            <span style="font-size: 20px; font-weight: bold; color: #409eff;">{{ detailData.totalScore ?? '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="评分等级">
            <el-tag :type="scoreLevelType(detailData.totalScore)" size="default" v-if="detailData.totalScore != null">
              {{ scoreLevelLabel(detailData.totalScore) }}
            </el-tag>
            <span v-else>-</span>
          </el-descriptions-item>
        </el-descriptions>
        <el-empty description="暂无评分结果" :image-size="60" v-else />
      </template>
      <template #footer>
        <el-button @click="showDetailDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 创建整改任务弹窗 -->
    <el-dialog v-model="showCreateRectification" title="创建整改任务" width="560px" destroy-on-close>
      <el-form :model="rectForm" label-width="110px" ref="rectFormRef" :rules="rectRules">
        <el-form-item label="关联评估ID" prop="assessmentId">
          <el-input v-model="rectForm.assessmentId" placeholder="请输入关联的评估ID" />
        </el-form-item>
        <el-form-item label="问题描述" prop="issueDescription">
          <el-input v-model="rectForm.issueDescription" type="textarea" :rows="3" placeholder="请输入问题描述" />
        </el-form-item>
        <el-form-item label="整改措施" prop="rectificationMeasure">
          <el-input v-model="rectForm.rectificationMeasure" type="textarea" :rows="3" placeholder="请输入整改措施" />
        </el-form-item>
        <el-form-item label="责任人" prop="responsiblePerson">
          <el-input v-model="rectForm.responsiblePerson" placeholder="请输入责任人" />
        </el-form-item>
        <el-form-item label="截止日期" prop="deadline">
          <el-date-picker v-model="rectForm.deadline" type="date" placeholder="选择截止日期" style="width: 100%;" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateRectification = false">取消</el-button>
        <el-button type="primary" @click="submitRectification" :loading="submitting">确认创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, ArrowDown } from '@element-plus/icons-vue'
import request from '@/utils/request'
import type { FormInstance } from 'element-plus'

// ==================== State ====================
const activeTab = ref('assessment')
const submitting = ref(false)

// --- Assessment State ---
const assessmentLoading = ref(false)
const assessments = ref<any[]>([])
const showCreateAssessment = ref(false)
const showScoreDialog = ref(false)
const showDetailDialog = ref(false)
const detailData = ref<any>(null)
const assessmentFormRef = ref<FormInstance>()
const assessmentForm = reactive({
  assessmentName: '',
  assessmentType: '',
  assessorId: '',
  approverId: '',
  startTime: '',
  endTime: ''
})
const scoreForm = reactive({ assessmentId: '', assessmentName: '', totalScore: 0 })
const assessmentRules = {
  assessmentName: [{ required: true, message: '请输入评估名称', trigger: 'blur' }],
  assessmentType: [{ required: true, message: '请选择评估类型', trigger: 'change' }],
  assessorId: [{ required: true, message: '请输入评估人ID', trigger: 'blur' }]
}

// --- Rectification State ---
const rectLoading = ref(false)
const rectifications = ref<any[]>([])
const showCreateRectification = ref(false)
const rectFormRef = ref<FormInstance>()
const rectForm = reactive({
  assessmentId: '',
  issueDescription: '',
  rectificationMeasure: '',
  responsiblePerson: '',
  deadline: ''
})
const rectRules = {
  assessmentId: [{ required: true, message: '请输入关联评估ID', trigger: 'blur' }],
  issueDescription: [{ required: true, message: '请输入问题描述', trigger: 'blur' }],
  rectificationMeasure: [{ required: true, message: '请输入整改措施', trigger: 'blur' }],
  responsiblePerson: [{ required: true, message: '请输入责任人', trigger: 'blur' }],
  deadline: [{ required: true, message: '请选择截止日期', trigger: 'change' }]
}

// ==================== Assessment Methods ====================
async function loadAssessments() {
  assessmentLoading.value = true
  try {
    const res: any = await request.get('/assessments/list')
    assessments.value = res.data || []
  } catch { /* handled */ } finally {
    assessmentLoading.value = false
  }
}

async function submitAssessment() {
  const valid = await assessmentFormRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await request.post('/assessments', { ...assessmentForm })
    ElMessage.success('创建评估成功')
    showCreateAssessment.value = false
    Object.assign(assessmentForm, { assessmentName: '', assessmentType: '', assessorId: '', approverId: '', startTime: '', endTime: '' })
    loadAssessments()
  } catch { ElMessage.error('创建评估失败') } finally { submitting.value = false }
}

async function viewAssessmentDetail(id: string | number) {
  try {
    const res: any = await request.get(`/assessments/${id}`)
    detailData.value = res.data || res
    showDetailDialog.value = true
  } catch { ElMessage.error('获取评估详情失败') }
}

function openScoreDialog(row: any) {
  scoreForm.assessmentId = row.id
  scoreForm.assessmentName = row.assessmentName
  scoreForm.totalScore = 0
  showScoreDialog.value = true
}

async function submitScore() {
  if (scoreForm.totalScore <= 0) {
    ElMessage.warning('请输入有效评分')
    return
  }
  submitting.value = true
  try {
    await request.post('/assessments/score', { id: scoreForm.assessmentId, totalScore: scoreForm.totalScore })
    ElMessage.success('评分成功')
    showScoreDialog.value = false
    loadAssessments()
  } catch { ElMessage.error('评分失败') } finally { submitting.value = false }
}

async function completeAssessment(id: string | number) {
  await ElMessageBox.confirm('确认完成该评估？', '提示', { type: 'warning' })
  try {
    await request.post(`/assessments/${id}/complete`)
    ElMessage.success('评估已完成')
    loadAssessments()
  } catch { ElMessage.error('操作失败') }
}

async function approveAssessment(id: string | number) {
  await ElMessageBox.confirm('确认审批通过该评估？', '提示', { type: 'warning' })
  try {
    await request.post(`/assessments/${id}/approve`)
    ElMessage.success('审批通过')
    loadAssessments()
  } catch { ElMessage.error('审批失败') }
}

function assessmentStatusType(status: string) {
  const map: Record<string, string> = { CREATED: 'info', SCORED: 'warning', COMPLETED: '', APPROVED: 'success', REJECTED: 'danger' }
  return (map[status] || 'info') as any
}

function assessmentStatusLabel(status: string) {
  const map: Record<string, string> = { CREATED: '已创建', SCORED: '已评分', COMPLETED: '已完成', APPROVED: '已审批', REJECTED: '已驳回' }
  return map[status] || status
}

function scoreLevelType(score: number | null): any {
  if (score == null) return 'info'
  if (score >= 80) return 'success'
  if (score >= 60) return 'warning'
  return 'danger'
}

function scoreLevelLabel(score: number | null) {
  if (score == null) return '-'
  if (score >= 80) return '优秀'
  if (score >= 60) return '合格'
  return '不合格'
}

// ==================== Rectification Methods ====================
async function loadRectifications() {
  rectLoading.value = true
  try {
    const res: any = await request.get('/assessments/rectifications/list')
    rectifications.value = res.data || []
  } catch { /* handled */ } finally {
    rectLoading.value = false
  }
}

async function submitRectification() {
  const valid = await rectFormRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await request.post('/assessments/rectifications', { ...rectForm })
    ElMessage.success('创建整改任务成功')
    showCreateRectification.value = false
    Object.assign(rectForm, { assessmentId: '', issueDescription: '', rectificationMeasure: '', responsiblePerson: '', deadline: '' })
    loadRectifications()
  } catch { ElMessage.error('创建整改任务失败') } finally { submitting.value = false }
}

async function updateRectStatus(id: string | number, status: string) {
  try {
    await request.put(`/assessments/rectifications/${id}/status`, { status })
    ElMessage.success('状态更新成功')
    loadRectifications()
  } catch { ElMessage.error('状态更新失败') }
}

async function verifyRectification(id: string | number) {
  await ElMessageBox.confirm('确认验证通过该整改任务？', '提示', { type: 'warning' })
  try {
    await request.post(`/assessments/rectifications/${id}/verify`)
    ElMessage.success('验证通过')
    loadRectifications()
  } catch { ElMessage.error('验证失败') }
}

function rectStatusType(status: string) {
  const map: Record<string, string> = { PENDING: 'info', IN_PROGRESS: 'warning', COMPLETED: '', VERIFIED: 'success' }
  return (map[status] || 'info') as any
}

function rectStatusLabel(status: string) {
  const map: Record<string, string> = { PENDING: '待处理', IN_PROGRESS: '进行中', COMPLETED: '已完成', VERIFIED: '已验证' }
  return map[status] || status
}

// ==================== Init ====================
onMounted(() => {
  loadAssessments()
  loadRectifications()
})
</script>
