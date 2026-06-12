<template>
  <div>
    <el-card>
      <template #header>
        <span style="font-size: 18px; font-weight: 600;">风险自评估管理</span>
      </template>

      <section class="assessment-visual-grid">
        <div class="chart-card">
          <div class="chart-card-header">
            <div>
              <h2>自评估风险画像</h2>
              <p>汇总固有风险、控制有效性和综合评分，辅助识别短板维度</p>
            </div>
            <el-tag type="warning" size="small">风险评分</el-tag>
          </div>
          <div ref="assessmentRadarRef" class="chart-box"></div>
        </div>

        <div class="chart-card">
          <div class="chart-card-header">
            <div>
              <h2>整改任务闭环</h2>
              <p>按整改状态展示自评估问题从创建到验证的闭环进度</p>
            </div>
            <el-tag type="info" size="small">整改跟踪</el-tag>
          </div>
          <div ref="assessmentRectChartRef" class="chart-box"></div>
        </div>
      </section>

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
            <el-table-column label="评估名称" min-width="190" show-overflow-tooltip>
              <template #default="{ row }">
                {{ assessmentTitle(row) }}
              </template>
            </el-table-column>
            <el-table-column prop="assessmentYear" label="年度" width="90" align="center" />
            <el-table-column label="周期" width="110">
              <template #default="{ row }">
                {{ assessmentPeriodLabel(row.assessmentPeriod) }}
              </template>
            </el-table-column>
            <el-table-column prop="assessmentStatus" label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="assessmentStatusType(row.assessmentStatus)" size="small">
                  {{ assessmentStatusLabel(row.assessmentStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="overallScore" label="综合分" width="90" align="center">
              <template #default="{ row }">
                {{ displayValue(row.overallScore) }}
              </template>
            </el-table-column>
            <el-table-column prop="overallRiskLevel" label="风险等级" width="110" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.overallRiskLevel" :type="riskLevelType(row.overallRiskLevel)" size="small">
                  {{ riskLevelLabel(row.overallRiskLevel) }}
                </el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="inherentRiskScore" label="固有风险" width="100" align="center">
              <template #default="{ row }">
                {{ displayValue(row.inherentRiskScore) }}
              </template>
            </el-table-column>
            <el-table-column prop="controlEffectivenessScore" label="控制有效性" width="110" align="center">
              <template #default="{ row }">
                {{ displayValue(row.controlEffectivenessScore) }}
              </template>
            </el-table-column>
            <el-table-column prop="assessorId" label="评估人ID" width="120" />
            <el-table-column prop="approvedBy" label="审批人" width="130">
              <template #default="{ row }">
                {{ formatAssessmentApprover(row) }}
              </template>
            </el-table-column>
            <el-table-column prop="approvedTime" label="审批时间" width="170">
              <template #default="{ row }">
                {{ displayValue(row.approvedTime) }}
              </template>
            </el-table-column>
            <el-table-column prop="createdTime" label="创建时间" width="170" />
            <el-table-column label="操作" width="260" fixed="right">
              <template #default="{ row }">
                <el-button size="small" @click="viewAssessmentDetail(row.id)">详情</el-button>
                <el-button size="small" type="warning" @click="openScoreDialog(row)" v-if="isScorable(row)">评分</el-button>
                <el-button size="small" type="success" @click="completeAssessment(row.id)" v-if="row.assessmentStatus === 'IN_PROGRESS'">完成</el-button>
                <el-button size="small" type="primary" @click="approveAssessment(row.id)" v-if="row.assessmentStatus === 'COMPLETED'">审批</el-button>
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
                      <el-dropdown-item command="IN_PROGRESS" v-if="row.status === 'OPEN' || row.status === 'PENDING'">进行中</el-dropdown-item>
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
        <el-form-item label="评估年度" prop="assessmentYear">
          <el-input-number v-model="assessmentForm.assessmentYear" :min="2000" :max="2100" :step="1" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="评估周期" prop="assessmentPeriod">
          <el-select v-model="assessmentForm.assessmentPeriod" placeholder="请选择评估周期" style="width: 100%;">
            <el-option label="年度评估" value="ANNUAL" />
            <el-option label="季度评估" value="QUARTERLY" />
          </el-select>
        </el-form-item>
        <el-form-item label="评估人ID" prop="assessorId">
          <el-input-number v-model="assessmentForm.assessorId" :min="1" :step="1" style="width: 100%;" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateAssessment = false">取消</el-button>
        <el-button type="primary" @click="submitAssessment" :loading="submitting">确认创建</el-button>
      </template>
    </el-dialog>

    <!-- 评分弹窗 -->
    <el-dialog v-model="showScoreDialog" title="评估评分" width="760px" destroy-on-close>
      <el-form :model="scoreForm" label-width="100px">
        <el-form-item label="评估名称">
          <el-input :model-value="scoreForm.assessmentName" disabled />
        </el-form-item>
        <el-table :data="scoreForm.scores" border size="small" v-loading="indicatorLoading">
          <el-table-column label="类别" width="120">
            <template #default="{ row }">
              {{ assessmentCategoryLabel(row.category) }}
            </template>
          </el-table-column>
          <el-table-column prop="indicatorName" label="指标名称" min-width="170" show-overflow-tooltip />
          <el-table-column prop="dimension" label="维度" min-width="140" show-overflow-tooltip />
          <el-table-column label="评分" width="140" align="center">
            <template #default="{ row }">
              <el-input-number v-model="row.score" :min="0" :max="100" :step="1" controls-position="right" style="width: 112px;" />
            </template>
          </el-table-column>
          <el-table-column label="评分依据" min-width="220">
            <template #default="{ row }">
              <el-input v-model="row.evidence" placeholder="请输入评分依据" />
            </template>
          </el-table-column>
        </el-table>
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
          <el-descriptions-item label="评估名称">{{ assessmentTitle(detailData) }}</el-descriptions-item>
          <el-descriptions-item label="评估年度">{{ detailData.assessmentYear }}</el-descriptions-item>
          <el-descriptions-item label="评估周期">{{ assessmentPeriodLabel(detailData.assessmentPeriod) }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="assessmentStatusType(detailData.assessmentStatus)" size="small">
              {{ assessmentStatusLabel(detailData.assessmentStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="评估人ID">{{ displayValue(detailData.assessorId) }}</el-descriptions-item>
          <el-descriptions-item label="审批人">{{ formatAssessmentApprover(detailData) }}</el-descriptions-item>
          <el-descriptions-item label="审批时间">{{ displayValue(detailData.approvedTime) }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ displayValue(detailData.updatedTime) }}</el-descriptions-item>
          <el-descriptions-item label="创建时间" :span="2">{{ detailData.createdTime }}</el-descriptions-item>
          <el-descriptions-item label="评估结论" :span="2">{{ displayValue(detailData.conclusion) }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">评分结果</el-divider>

        <el-descriptions :column="2" border v-if="hasAssessmentScore(detailData)">
          <el-descriptions-item label="综合评分">
            <span style="font-size: 20px; font-weight: bold; color: #409eff;">{{ displayValue(detailData.overallScore) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="综合风险等级">
            <el-tag :type="riskLevelType(detailData.overallRiskLevel)" size="default" v-if="detailData.overallRiskLevel">
              {{ riskLevelLabel(detailData.overallRiskLevel) }}
            </el-tag>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="固有风险评分">{{ displayValue(detailData.inherentRiskScore) }}</el-descriptions-item>
          <el-descriptions-item label="控制有效性评分">{{ displayValue(detailData.controlEffectivenessScore) }}</el-descriptions-item>
        </el-descriptions>
        <el-empty description="暂无评分结果" :image-size="60" v-else />

        <template v-if="detailData.scores?.length">
          <el-divider content-position="left">评分明细</el-divider>
          <el-table :data="detailData.scores" size="small" border>
            <el-table-column prop="indicatorCode" label="指标编码" width="130" show-overflow-tooltip />
            <el-table-column prop="indicatorName" label="指标名称" min-width="160" show-overflow-tooltip />
            <el-table-column label="类别" width="130">
              <template #default="{ row }">
                {{ assessmentCategoryLabel(row.category) }}
              </template>
            </el-table-column>
            <el-table-column prop="dimension" label="维度" min-width="130" show-overflow-tooltip />
            <el-table-column prop="score" label="得分" width="80" align="center" />
            <el-table-column prop="rawValue" label="原始值" width="90" align="center" />
            <el-table-column prop="evidence" label="评分依据" min-width="180" show-overflow-tooltip />
          </el-table>
        </template>
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
        <el-form-item label="严重程度" prop="severity">
          <el-select v-model="rectForm.severity" placeholder="请选择严重程度" style="width: 100%;">
            <el-option label="高" value="HIGH" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="低" value="LOW" />
          </el-select>
        </el-form-item>
        <el-form-item label="整改措施" prop="rectificationMeasure">
          <el-input v-model="rectForm.rectificationMeasure" type="textarea" :rows="3" placeholder="请输入整改措施" />
        </el-form-item>
        <el-form-item label="责任部门" prop="responsibleDept">
          <el-input v-model="rectForm.responsibleDept" placeholder="请输入责任部门" />
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
import { ref, onMounted, onUnmounted, reactive, nextTick, watch } from 'vue'
import type { ECharts } from 'echarts'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, ArrowDown } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'
import { currentOperatorName, formatOperatorName } from '@/utils/operatorDisplay'
import type { FormInstance } from 'element-plus'
import { disposeEchart, getEcharts } from '@/utils/echarts'

// ==================== State ====================
const userStore = useUserStore()
const activeTab = ref('assessment')
const submitting = ref(false)

// --- Assessment State ---
const assessmentLoading = ref(false)
const assessments = ref<any[]>([])
const showCreateAssessment = ref(false)
const showScoreDialog = ref(false)
const showDetailDialog = ref(false)
const detailData = ref<any>(null)
const indicatorLoading = ref(false)
const assessmentRadarRef = ref<HTMLElement | null>(null)
const assessmentRectChartRef = ref<HTMLElement | null>(null)
let assessmentRadarChart: ECharts | null = null
let assessmentRectChart: ECharts | null = null
let resizeHandler: (() => void) | null = null
const assessmentFormRef = ref<FormInstance>()
const assessmentForm = reactive({
  assessmentYear: new Date().getFullYear(),
  assessmentPeriod: 'ANNUAL',
  assessorId: null as number | null
})
const scoreForm = reactive({
  assessmentId: '',
  assessmentName: '',
  scores: [] as Array<{
    indicatorId: number
    indicatorName: string
    category: string
    dimension: string
    rawValue: number
    score: number
    evidence: string
  }>
})
const assessmentRules = {
  assessmentYear: [{ required: true, message: '请输入评估年度', trigger: 'blur' }],
  assessmentPeriod: [{ required: true, message: '请选择评估周期', trigger: 'change' }],
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
  severity: 'MEDIUM',
  rectificationMeasure: '',
  responsibleDept: '合规部',
  responsiblePerson: '',
  deadline: ''
})
const rectRules = {
  assessmentId: [{ required: true, message: '请输入关联评估ID', trigger: 'blur' }],
  issueDescription: [{ required: true, message: '请输入问题描述', trigger: 'blur' }],
  severity: [{ required: true, message: '请选择严重程度', trigger: 'change' }],
  rectificationMeasure: [{ required: true, message: '请输入整改措施', trigger: 'blur' }],
  responsiblePerson: [{ required: true, message: '请输入责任人', trigger: 'blur' }],
  deadline: [{ required: true, message: '请选择截止日期', trigger: 'change' }]
}

// ==================== Assessment Methods ====================
async function loadAssessments() {
  assessmentLoading.value = true
  try {
    const res: any = await request.get('/assessments/list')
    assessments.value = Array.isArray(res.data) ? res.data : []
  } catch { /* handled */ } finally {
    assessmentLoading.value = false
  }
}

async function submitAssessment() {
  const valid = await assessmentFormRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await request.post('/assessments', {
      assessmentYear: assessmentForm.assessmentYear,
      assessmentPeriod: assessmentForm.assessmentPeriod,
      assessorId: assessmentForm.assessorId
    })
    ElMessage.success('创建评估成功')
    showCreateAssessment.value = false
    Object.assign(assessmentForm, { assessmentYear: new Date().getFullYear(), assessmentPeriod: 'ANNUAL', assessorId: null })
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

async function openScoreDialog(row: any) {
  scoreForm.assessmentId = row.id
  scoreForm.assessmentName = assessmentTitle(row)
  scoreForm.scores = []
  showScoreDialog.value = true
  indicatorLoading.value = true
  try {
    const res: any = await request.get('/assessments/indicators')
    const indicators = Array.isArray(res.data) ? res.data : []
    scoreForm.scores = indicators.map((indicator: any) => defaultIndicatorScore(indicator))
  } catch {
    ElMessage.error('加载评估指标失败')
  } finally {
    indicatorLoading.value = false
  }
}

async function submitScore() {
  if (!scoreForm.scores.length) {
    ElMessage.warning('暂无可用评估指标')
    return
  }
  if (scoreForm.scores.some(item => item.score === null || item.score === undefined || item.score < 0 || item.score > 100)) {
    ElMessage.warning('评分需在0-100之间')
    return
  }
  submitting.value = true
  try {
    await Promise.all(scoreForm.scores.map(item => request.post('/assessments/score', {
      assessmentId: Number(scoreForm.assessmentId),
      indicatorId: item.indicatorId,
      rawValue: item.rawValue,
      score: item.score,
      evidence: item.evidence,
      dataSource: 'UI_ASSESSMENT',
      remark: '自评估页面评分'
    })))
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
    const approvedBy = currentOperatorName(userStore.userInfo)
    await request.post(`/assessments/${id}/approve`, undefined, { params: { approvedBy } })
    ElMessage.success('审批通过')
    loadAssessments()
  } catch { ElMessage.error('审批失败') }
}

function displayValue(value: unknown) {
  return value === null || value === undefined || value === '' ? '-' : String(value)
}

function formatAssessmentApprover(row: any) {
  return formatOperatorName(row?.approvedBy, row?.assessmentStatus === 'APPROVED' ? '待补录' : '待审批')
}

function assessmentTitle(row: any) {
  if (row?.assessmentName) return row.assessmentName
  if (row?.title) return row.title
  const year = displayValue(row?.assessmentYear)
  const period = assessmentPeriodLabel(row?.assessmentPeriod)
  return `${year}年${period}风险自评估`
}

function assessmentPeriodLabel(period: string) {
  const map: Record<string, string> = { ANNUAL: '年度', QUARTERLY: '季度', SPECIAL: '专项' }
  return map[period] || displayValue(period)
}

function assessmentStatusType(status: string) {
  const map: Record<string, string> = {
    CREATED: 'info',
    IN_PROGRESS: 'warning',
    SCORED: 'warning',
    COMPLETED: '',
    APPROVED: 'success',
    REJECTED: 'danger'
  }
  return (map[status] || 'info') as any
}

function assessmentStatusLabel(status: string) {
  const map: Record<string, string> = {
    CREATED: '已创建',
    IN_PROGRESS: '进行中',
    SCORED: '已评分',
    COMPLETED: '已完成',
    APPROVED: '已审批',
    REJECTED: '已驳回'
  }
  return map[status] || status
}

function riskLevelType(level: string): any {
  const map: Record<string, string> = { LOW: 'success', MEDIUM: 'warning', HIGH: 'danger', CRITICAL: 'danger' }
  return map[level] || 'info'
}

function riskLevelLabel(level: string) {
  const map: Record<string, string> = { LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险', CRITICAL: '极高风险' }
  return map[level] || displayValue(level)
}

function assessmentCategoryLabel(category: string) {
  const map: Record<string, string> = {
    INHERENT_RISK: '固有风险',
    CONTROL_EFFECTIVENESS: '控制有效性'
  }
  return map[category] || displayValue(category)
}

function hasAssessmentScore(row: any) {
  return row?.overallScore !== null && row?.overallScore !== undefined
}

function isScorable(row: any) {
  return row?.assessmentStatus === 'CREATED' || row?.assessmentStatus === 'IN_PROGRESS'
}

function defaultIndicatorScore(indicator: any) {
  const isControl = indicator.category === 'CONTROL_EFFECTIVENESS'
  const score = isControl ? 62 : 82
  return {
    indicatorId: Number(indicator.id),
    indicatorName: indicator.indicatorName,
    category: indicator.category,
    dimension: indicator.dimension,
    rawValue: score,
    score,
    evidence: isControl
      ? '名单筛查、交易监测、案件调查和监管报送控制基本有效，仍需提升自动化复核覆盖率。'
      : '高风险客户、大额现金交易和复杂资金链路占比较高，固有风险处于偏高水平。'
  }
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
    await request.post('/assessments/rectifications', {
      ...rectForm,
      assessmentId: Number(rectForm.assessmentId),
      sourceType: 'SELF_ASSESSMENT',
      sourceId: Number(rectForm.assessmentId),
      deadline: formatDate(rectForm.deadline)
    })
    ElMessage.success('创建整改任务成功')
    showCreateRectification.value = false
    Object.assign(rectForm, {
      assessmentId: '',
      issueDescription: '',
      severity: 'MEDIUM',
      rectificationMeasure: '',
      responsibleDept: '合规部',
      responsiblePerson: '',
      deadline: ''
    })
    loadRectifications()
  } catch { ElMessage.error('创建整改任务失败') } finally { submitting.value = false }
}

async function updateRectStatus(id: string | number, status: string) {
  try {
    await request.put(`/assessments/rectifications/${id}/status`, undefined, { params: { status } })
    ElMessage.success('状态更新成功')
    loadRectifications()
  } catch { ElMessage.error('状态更新失败') }
}

async function verifyRectification(id: string | number) {
  await ElMessageBox.confirm('确认验证通过该整改任务？', '提示', { type: 'warning' })
  try {
    await request.post(`/assessments/rectifications/${id}/verify`, undefined, { params: { verifiedBy: currentOperatorName(userStore.userInfo) } })
    ElMessage.success('验证通过')
    loadRectifications()
  } catch { ElMessage.error('验证失败') }
}

function rectStatusType(status: string) {
  const map: Record<string, string> = { OPEN: 'info', PENDING: 'info', IN_PROGRESS: 'warning', COMPLETED: '', VERIFIED: 'success', OVERDUE: 'danger' }
  return (map[status] || 'info') as any
}

function rectStatusLabel(status: string) {
  const map: Record<string, string> = { OPEN: '待整改', PENDING: '待处理', IN_PROGRESS: '进行中', COMPLETED: '已完成', VERIFIED: '已验证', OVERDUE: '已逾期' }
  return map[status] || status
}

function formatDate(value: string | Date) {
  if (!value) return ''
  if (value instanceof Date) {
    return value.toISOString().slice(0, 10)
  }
  return String(value).slice(0, 10)
}

function averageScore(field: string) {
  const values = assessments.value
    .map(item => Number(item?.[field]))
    .filter(value => Number.isFinite(value))
  if (!values.length) return 0
  return Number((values.reduce((sum, value) => sum + value, 0) / values.length).toFixed(1))
}

function rectificationCount(status: string) {
  return rectifications.value.filter(item => item.status === status).length
}

async function renderAssessmentVisuals() {
  await nextTick()
  await Promise.all([renderAssessmentRadar(), renderAssessmentRectChart()])
}

async function renderAssessmentRadar() {
  const container = assessmentRadarRef.value
  if (!container || !container.isConnected) return
  const echarts = await getEcharts()
  if (!assessmentRadarChart) assessmentRadarChart = echarts.init(container)

  const inherent = averageScore('inherentRiskScore')
  const control = averageScore('controlEffectivenessScore')
  const overall = averageScore('overallScore')

  assessmentRadarChart.setOption({
    tooltip: { trigger: 'item' },
    radar: {
      radius: '66%',
      center: ['50%', '52%'],
      indicator: [
        { name: '固有风险', max: 100 },
        { name: '控制有效性', max: 100 },
        { name: '综合评分', max: 100 },
        { name: '已审批覆盖', max: 100 },
        { name: '评分完整度', max: 100 }
      ],
      axisName: { color: '#475569', fontSize: 12 },
      splitLine: { lineStyle: { color: '#e2e8f0' } },
      splitArea: { areaStyle: { color: ['rgba(37,99,235,0.04)', 'rgba(37,99,235,0.08)'] } },
      axisLine: { lineStyle: { color: '#cbd5e1' } }
    },
    series: [{
      type: 'radar',
      data: [{
        name: '当前评估画像',
        value: [
          inherent,
          control,
          overall,
          assessments.value.length ? Math.round((assessments.value.filter(item => item.assessmentStatus === 'APPROVED').length / assessments.value.length) * 100) : 0,
          assessments.value.length ? Math.round((assessments.value.filter(hasAssessmentScore).length / assessments.value.length) * 100) : 0
        ],
        areaStyle: { color: 'rgba(37, 99, 235, 0.20)' },
        lineStyle: { color: '#2563eb', width: 2 },
        itemStyle: { color: '#2563eb' }
      }]
    }]
  }, true)
  assessmentRadarChart.resize()
}

async function renderAssessmentRectChart() {
  const container = assessmentRectChartRef.value
  if (!container || !container.isConnected) return
  const echarts = await getEcharts()
  if (!assessmentRectChart) assessmentRectChart = echarts.init(container)

  const statuses = ['OPEN', 'IN_PROGRESS', 'COMPLETED', 'VERIFIED']
  assessmentRectChart.setOption({
    color: ['#64748b', '#d97706', '#2563eb', '#16a34a'],
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 34, right: 16, top: 26, bottom: 34 },
    xAxis: {
      type: 'category',
      data: statuses.map(rectStatusLabel),
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
    series: [{
      type: 'bar',
      barMaxWidth: 34,
      data: statuses.map((status, index) => ({
        value: rectificationCount(status),
        itemStyle: { color: ['#64748b', '#d97706', '#2563eb', '#16a34a'][index], borderRadius: [5, 5, 0, 0] }
      })),
      label: { show: true, position: 'top', color: '#334155', fontWeight: 600 }
    }]
  }, true)
  assessmentRectChart.resize()
}

function disposeAssessmentCharts() {
  disposeEchart(assessmentRadarChart)
  disposeEchart(assessmentRectChart)
  assessmentRadarChart = null
  assessmentRectChart = null
}

// ==================== Init ====================
onMounted(() => {
  Promise.all([loadAssessments(), loadRectifications()]).then(renderAssessmentVisuals)
  resizeHandler = () => {
    assessmentRadarChart?.resize()
    assessmentRectChart?.resize()
  }
  window.addEventListener('resize', resizeHandler)
})

watch([assessments, rectifications], () => renderAssessmentVisuals(), { deep: true })

onUnmounted(() => {
  if (resizeHandler) window.removeEventListener('resize', resizeHandler)
  resizeHandler = null
  disposeAssessmentCharts()
})
</script>

<style scoped>
.assessment-visual-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.92fr) minmax(0, 1.08fr);
  gap: 12px;
  margin-bottom: 16px;
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
  height: 286px;
}

@media (max-width: 980px) {
  .assessment-visual-grid {
    grid-template-columns: 1fr;
  }
}
</style>
