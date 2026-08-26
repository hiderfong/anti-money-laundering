<template>
  <div class="reporting-page">
    <section class="page-heading">
      <div>
        <h1>监管报送</h1>
        <p>统一管理大额交易报告、报送版本、数字签名、监管回执和退回重报</p>
      </div>
      <div class="heading-actions">
        <el-button v-if="activeView === 'reports'" type="primary" @click="openGenerateDialog">
          <el-icon><Plus /></el-icon>生成报告
        </el-button>
        <el-button v-if="activeView === 'reports' && canSubmit" type="danger" :loading="retryingFailed" @click="handleRetryFailed">
          <el-icon><RefreshLeft /></el-icon>重试历史失败
        </el-button>
      </div>
    </section>

    <div class="view-switcher">
      <el-segmented v-model="activeView" :options="viewOptions" @change="handleViewChange" />
    </div>

    <template v-if="activeView === 'reports'">
      <section class="workspace">
        <div class="section-heading">
          <div><h2>大额交易报告</h2><span>报告审核通过后进入统一报送流水线</span></div>
        </div>
        <div class="toolbar report-toolbar">
          <el-select v-model="statusFilter" placeholder="报告状态" clearable @change="loadData">
            <el-option v-for="item in reportStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-date-picker v-model="dateRangeFilter" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" @change="loadData" />
          <el-button type="primary" @click="loadData"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="resetFilters"><el-icon><RefreshLeft /></el-icon>重置</el-button>
        </div>

        <el-table :data="reports" stripe v-loading="loading" border height="560" style="width:100%">
          <el-table-column prop="reportNo" label="报告编号" min-width="190" fixed="left" show-overflow-tooltip />
          <el-table-column label="客户" min-width="170" fixed="left" show-overflow-tooltip>
            <template #default="{ row }">{{ row.customerName || `客户ID ${row.customerId || '-'}` }}</template>
          </el-table-column>
          <el-table-column prop="transactionType" label="交易类型" width="110"><template #default="{ row }">{{ transactionTypeLabel(row.transactionType) }}</template></el-table-column>
          <el-table-column label="交易金额" width="160" align="right"><template #default="{ row }">{{ formatAmount(row.amount) }} {{ row.currency || '' }}</template></el-table-column>
          <el-table-column prop="paymentMethod" label="支付方式" width="110"><template #default="{ row }">{{ paymentMethodLabel(row.paymentMethod) }}</template></el-table-column>
          <el-table-column prop="reportDate" label="报告日期" width="120" align="center" />
          <el-table-column prop="transactionTime" label="交易时间" width="170" />
          <el-table-column prop="reportStatus" label="报告状态" width="110"><template #default="{ row }"><el-tag :type="reportStatusTagType(row.reportStatus)" size="small">{{ reportStatusLabel(row.reportStatus) }}</el-tag></template></el-table-column>
          <el-table-column prop="reviewedBy" label="审核人" width="130"><template #default="{ row }">{{ formatReportReviewer(row) }}</template></el-table-column>
          <el-table-column prop="submittedTime" label="报送时间" width="170"><template #default="{ row }">{{ row.submittedTime || '-' }}</template></el-table-column>
          <el-table-column label="操作" width="270" fixed="right">
            <template #default="{ row }">
              <div class="table-actions">
                <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
                <el-button v-if="row.reportStatus === 'DRAFT' && canSubmit" link type="success" size="small" @click="openReviewDialog(row)">审核</el-button>
                <el-button v-if="row.reportStatus === 'REVIEWED' && canSubmit" link type="warning" size="small" @click="handleSubmitReport(row)">提交报送</el-button>
                <el-button v-if="['REVIEWED','SUBMITTED','RESUBMITTED'].includes(row.reportStatus)" link type="info" size="small" @click="handleExportXml(row)">导出XML</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
        <div class="pagination-row"><el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" :page-sizes="[10,20,50,100]" :total="total" layout="total, sizes, prev, pager, next" @size-change="loadData" @current-change="loadData" /></div>
      </section>
    </template>

    <template v-else>
      <section class="metric-grid" aria-label="监管报送概览">
        <div class="metric-item"><span>报送版本</span><strong>{{ submissionOverview.totalSubmissions }}</strong></div>
        <div class="metric-item warning"><span>待回执</span><strong>{{ submissionOverview.pendingReceipts }}</strong></div>
        <div class="metric-item success"><span>已受理</span><strong>{{ submissionOverview.acceptedSubmissions }}</strong></div>
        <div class="metric-item danger"><span>已退回</span><strong>{{ submissionOverview.rejectedSubmissions }}</strong></div>
        <div class="metric-item danger"><span>传输失败</span><strong>{{ submissionOverview.failedSubmissions }}</strong></div>
        <div class="metric-item"><span>重报版本</span><strong>{{ submissionOverview.resubmissions }}</strong></div>
        <div class="metric-item success"><span>受理率</span><strong>{{ submissionOverview.acceptanceRate }}%</strong></div>
      </section>

      <section class="workspace">
        <div class="section-heading">
          <div><h2>报送工作台</h2><span>每次提交和重报均保留独立版本、签名及回执证据</span></div>
        </div>
        <div class="toolbar submission-toolbar">
          <el-input v-model="submissionQuery.keyword" clearable placeholder="报送流水号 / 报告编号" @keyup.enter="loadSubmissions"><template #prefix><el-icon><Search /></el-icon></template></el-input>
          <el-select v-model="submissionQuery.reportType" clearable placeholder="报告类型" @change="loadSubmissions"><el-option v-for="item in reportTypeOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select>
          <el-select v-model="submissionQuery.status" clearable placeholder="报送状态" @change="loadSubmissions"><el-option v-for="item in submissionStatusOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select>
          <el-select v-model="submissionQuery.receiptStatus" clearable placeholder="回执状态" @change="loadSubmissions"><el-option v-for="item in receiptStatusOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select>
          <el-button type="primary" @click="loadSubmissions"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="resetSubmissionQuery"><el-icon><RefreshLeft /></el-icon>重置</el-button>
        </div>

        <el-table :data="submissions" stripe border v-loading="submissionLoading" height="560" style="width:100%">
          <el-table-column prop="submissionNo" label="报送流水号" min-width="220" fixed="left" show-overflow-tooltip />
          <el-table-column prop="reportNo" label="报告编号" min-width="190" fixed="left" show-overflow-tooltip />
          <el-table-column prop="reportType" label="报告类型" width="120"><template #default="{ row }">{{ optionLabel(reportTypeOptions, row.reportType) }}</template></el-table-column>
          <el-table-column prop="versionNo" label="版本" width="76"><template #default="{ row }">V{{ row.versionNo }}</template></el-table-column>
          <el-table-column prop="connectorName" label="报送通道" min-width="190" show-overflow-tooltip />
          <el-table-column prop="status" label="报送状态" width="110"><template #default="{ row }"><el-tag :type="submissionTagType(row.status)" size="small">{{ optionLabel(submissionStatusOptions, row.status) }}</el-tag></template></el-table-column>
          <el-table-column prop="receiptStatus" label="回执状态" width="110"><template #default="{ row }"><el-tag :type="receiptTagType(row.receiptStatus)" effect="plain" size="small">{{ optionLabel(receiptStatusOptions, row.receiptStatus) }}</el-tag></template></el-table-column>
          <el-table-column prop="signatureAlgorithm" label="签名算法" width="135"><template #default="{ row }">{{ row.signatureAlgorithm || '-' }}</template></el-table-column>
          <el-table-column prop="submittedBy" label="报送人" width="120"><template #default="{ row }">{{ row.submittedBy || '-' }}</template></el-table-column>
          <el-table-column prop="submittedTime" label="发送时间" width="170"><template #default="{ row }">{{ row.submittedTime || '-' }}</template></el-table-column>
          <el-table-column prop="returnMessage" label="监管说明" min-width="230" show-overflow-tooltip><template #default="{ row }">{{ row.returnMessage || row.errorMessage || '-' }}</template></el-table-column>
          <el-table-column label="操作" width="170" fixed="right">
            <template #default="{ row }">
              <div class="table-actions">
                <el-button link type="primary" size="small" @click="openSubmissionDetail(row)">详情</el-button>
                <el-button v-if="row.status === 'SUBMITTED' && canSubmit" link type="warning" size="small" :loading="actionId === row.id" @click="pollReceipt(row)">查回执</el-button>
                <el-button v-if="['REJECTED','FAILED'].includes(row.status) && canSubmit" link type="danger" size="small" @click="openResubmit(row)">修正重报</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
        <div class="pagination-row"><el-pagination v-model:current-page="submissionQuery.page" v-model:page-size="submissionQuery.size" :page-sizes="[10,20,50]" :total="submissionTotal" layout="total, sizes, prev, pager, next" @size-change="changeSubmissionSize" @current-change="loadSubmissions" /></div>
      </section>
    </template>

    <el-dialog v-model="generateDialogVisible" title="生成大额交易报告" width="min(500px, calc(100vw - 32px))" destroy-on-close>
      <el-form ref="generateFormRef" :model="generateForm" :rules="generateRules" label-width="100px"><el-form-item label="交易ID" prop="transactionId"><el-input v-model="generateForm.transactionId" placeholder="请输入需要生成报告的交易ID" /></el-form-item></el-form>
      <template #footer><el-button @click="generateDialogVisible = false">取消</el-button><el-button type="primary" :loading="generating" @click="handleGenerate">确认生成</el-button></template>
    </el-dialog>

    <el-dialog v-model="detailDialogVisible" title="大额交易报告详情" width="min(680px, calc(100vw - 32px))" destroy-on-close>
      <el-descriptions v-if="detailData" :column="2" border>
        <el-descriptions-item label="报告编号">{{ detailData.reportNo }}</el-descriptions-item><el-descriptions-item label="客户">{{ detailData.customerName || `客户ID ${detailData.customerId || '-'}` }}</el-descriptions-item>
        <el-descriptions-item label="报告日期">{{ detailData.reportDate || '-' }}</el-descriptions-item><el-descriptions-item label="状态"><el-tag :type="reportStatusTagType(detailData.reportStatus)" size="small">{{ reportStatusLabel(detailData.reportStatus) }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="交易类型">{{ transactionTypeLabel(detailData.transactionType) }}</el-descriptions-item><el-descriptions-item label="交易金额">{{ formatAmount(detailData.amount) }} {{ detailData.currency || '' }}</el-descriptions-item>
        <el-descriptions-item label="支付方式">{{ paymentMethodLabel(detailData.paymentMethod) }}</el-descriptions-item><el-descriptions-item label="交易时间">{{ detailData.transactionTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ formatReportReviewer(detailData) }}</el-descriptions-item><el-descriptions-item label="报送时间">{{ detailData.submittedTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="交易对手" :span="2">{{ formatCounterparty(detailData.counterpartyInfo) }}</el-descriptions-item><el-descriptions-item label="创建时间" :span="2">{{ detailData.createdTime }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <el-dialog v-model="reviewDialogVisible" title="审核大额交易报告" width="min(480px, calc(100vw - 32px))" destroy-on-close>
      <el-form :model="reviewForm" label-width="80px"><el-form-item label="审核结果"><el-radio-group v-model="reviewForm.approved"><el-radio :value="true">通过</el-radio><el-radio :value="false">驳回</el-radio></el-radio-group></el-form-item><el-form-item label="审核意见"><el-input v-model="reviewForm.remark" type="textarea" :rows="4" placeholder="请输入审核意见" /></el-form-item></el-form>
      <template #footer><el-button @click="reviewDialogVisible = false">取消</el-button><el-button type="primary" :loading="reviewing" @click="handleReview">确认</el-button></template>
    </el-dialog>

    <el-drawer v-model="submissionDrawerVisible" size="min(760px, 94vw)" destroy-on-close>
      <template #header><div class="drawer-heading"><div><h2>报送证据链</h2><span>{{ submissionDetail?.submission.submissionNo }}</span></div><el-tag v-if="submissionDetail" :type="submissionTagType(submissionDetail.submission.status)">{{ optionLabel(submissionStatusOptions, submissionDetail.submission.status) }}</el-tag></div></template>
      <template v-if="submissionDetail">
        <el-alert v-if="['REJECTED','FAILED'].includes(submissionDetail.submission.status)" :title="submissionDetail.submission.returnMessage || submissionDetail.submission.errorMessage || '报送未完成'" type="error" show-icon :closable="false" class="drawer-alert" />
        <el-descriptions :column="2" border>
          <el-descriptions-item label="报告">{{ optionLabel(reportTypeOptions, submissionDetail.submission.reportType) }} / {{ submissionDetail.submission.reportNo }}</el-descriptions-item><el-descriptions-item label="版本">V{{ submissionDetail.submission.versionNo }}</el-descriptions-item>
          <el-descriptions-item label="报送通道">{{ submissionDetail.submission.connectorName || '-' }}</el-descriptions-item><el-descriptions-item label="规范版本">{{ submissionDetail.submission.schemaVersion }}</el-descriptions-item>
          <el-descriptions-item label="签名算法">{{ submissionDetail.submission.signatureAlgorithm || '-' }}</el-descriptions-item><el-descriptions-item label="报文摘要"><span class="mono-text">{{ shortHash(submissionDetail.submission.payloadHash) }}</span></el-descriptions-item>
          <el-descriptions-item label="监管请求号" :span="2"><span class="mono-text">{{ submissionDetail.submission.externalRequestId || '-' }}</span></el-descriptions-item>
          <el-descriptions-item label="回执号">{{ submissionDetail.submission.receiptNo || '-' }}</el-descriptions-item><el-descriptions-item label="返回码">{{ submissionDetail.submission.returnCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="修正说明" :span="2">{{ submissionDetail.submission.correctionNote || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div class="detail-block"><h3>回执时间线</h3><el-timeline v-if="submissionDetail.receipts.length"><el-timeline-item v-for="item in submissionDetail.receipts" :key="item.id" :timestamp="item.receivedTime" :type="receiptTimelineType(item.receiptStatus)"><strong>{{ optionLabel(receiptStatusOptions, item.receiptStatus) }}</strong><p>{{ item.receiptMessage || item.receiptCode || '监管平台状态更新' }}</p><small>{{ receiptSourceLabel(item.receiptSource) }} · {{ item.receiptNo || '暂无回执号' }}</small></el-timeline-item></el-timeline><el-empty v-else description="尚未收到监管回执" :image-size="64" /></div>
        <div class="detail-block"><h3>签名报文快照</h3><pre class="payload-preview">{{ submissionDetail.submission.payloadContent || '暂无报文内容' }}</pre></div>
      </template>
    </el-drawer>

    <el-dialog v-model="resubmitDialogVisible" title="修正并重报" width="min(600px, calc(100vw - 32px))" destroy-on-close>
      <el-alert title="重报会生成新的报送版本，原退回记录和回执将永久保留。" type="warning" :closable="false" show-icon class="resubmit-alert" />
      <el-form :model="resubmitForm" label-width="100px">
        <el-form-item label="报送通道"><el-select v-model="resubmitForm.connectorId" style="width:100%"><el-option v-for="item in regulatoryConnectors" :key="item.id" :label="item.connectorName" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="修正说明" required><el-input v-model="resubmitForm.correctionNote" type="textarea" :rows="5" maxlength="1000" show-word-limit placeholder="说明已修正的数据项、依据和复核结论" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="resubmitDialogVisible = false">取消</el-button><el-button type="danger" :loading="resubmitting" @click="submitResubmission">生成V{{ (resubmitTarget?.versionNo || 0) + 1 }}并重报</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import request from '@/utils/request'
import { integrationApi, regulatorySubmissionApi } from '@/api/modules'
import type { IntegrationConnector, RegulatoryReceipt, RegulatorySubmission, RegulatorySubmissionDetail, RegulatorySubmissionOverview } from '@/api/types'
import { useUserStore } from '@/stores/user'
import { currentOperatorName, formatOperatorName } from '@/utils/operatorDisplay'

const userStore = useUserStore()
const canSubmit = computed(() => userStore.isAdmin || userStore.hasPermission('report:submit'))
const activeView = ref<'reports' | 'submissions'>('reports')
const viewOptions = [{ label: '大额报告', value: 'reports' }, { label: '报送工作台', value: 'submissions' }]

const loading = ref(false)
const reports = ref<any[]>([])
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const statusFilter = ref('')
const dateRangeFilter = ref<string[] | null>(null)

const reportStatusOptions = [{ label: '草稿', value: 'DRAFT' }, { label: '已审核', value: 'REVIEWED' }, { label: '已报送', value: 'SUBMITTED' }, { label: '报送失败', value: 'FAILED' }, { label: '已重报', value: 'RESUBMITTED' }]
const reportTypeOptions = [{ label: '大额交易报告', value: 'LARGE_TXN' }, { label: '可疑交易报告', value: 'SUSPICIOUS' }]
const submissionStatusOptions = [{ label: '准备中', value: 'PREPARING' }, { label: '已发送', value: 'SUBMITTED' }, { label: '已受理', value: 'ACCEPTED' }, { label: '已退回', value: 'REJECTED' }, { label: '失败', value: 'FAILED' }]
const receiptStatusOptions = [{ label: '待回执', value: 'PENDING' }, { label: '已受理', value: 'ACCEPTED' }, { label: '已退回', value: 'REJECTED' }]

const submissionLoading = ref(false)
const submissions = ref<RegulatorySubmission[]>([])
const submissionTotal = ref(0)
const submissionQuery = reactive({ page: 1, size: 20, keyword: '', reportType: '', status: '', receiptStatus: '' })
const submissionOverview = reactive<RegulatorySubmissionOverview>({ totalSubmissions: 0, pendingReceipts: 0, acceptedSubmissions: 0, rejectedSubmissions: 0, failedSubmissions: 0, resubmissions: 0, acceptanceRate: 0 })
const regulatoryConnectors = ref<IntegrationConnector[]>([])
const submissionDrawerVisible = ref(false)
const submissionDetail = ref<RegulatorySubmissionDetail | null>(null)
const actionId = ref('')
const resubmitDialogVisible = ref(false)
const resubmitTarget = ref<RegulatorySubmission | null>(null)
const resubmitting = ref(false)
const resubmitForm = reactive({ connectorId: '', correctionNote: '' })

async function loadData() {
  loading.value = true
  try {
    const res: any = await request.get('/reporting/large-txn/page', { params: { page: currentPage.value, size: pageSize.value, reportStatus: statusFilter.value || undefined, startDate: dateRangeFilter.value?.[0] || undefined, endDate: dateRangeFilter.value?.[1] || undefined } })
    const pageData = res.data || res
    reports.value = pageData.list || pageData.records || pageData.content || []
    total.value = Number(pageData.total || 0)
  } finally { loading.value = false }
}

async function loadSubmissionOverview() { const res: any = await regulatorySubmissionApi.getOverview(); Object.assign(submissionOverview, res.data || {}) }
async function loadSubmissions() {
  submissionLoading.value = true
  try {
    const res: any = await regulatorySubmissionApi.getPage({ page: submissionQuery.page, size: submissionQuery.size, keyword: submissionQuery.keyword || undefined, reportType: submissionQuery.reportType || undefined, status: submissionQuery.status || undefined, receiptStatus: submissionQuery.receiptStatus || undefined })
    submissions.value = res.data?.list || []
    submissionTotal.value = Number(res.data?.total || 0)
  } finally { submissionLoading.value = false }
}
async function loadRegulatoryConnectors() { const res: any = await integrationApi.getEnabledConnectors(); regulatoryConnectors.value = (res.data || []).filter((item: IntegrationConnector) => item.businessType === 'REGULATORY_REPORTING') }
async function loadWorkbench() { await Promise.all([loadSubmissionOverview(), loadSubmissions(), loadRegulatoryConnectors()]) }
function handleViewChange() { if (activeView.value === 'reports') loadData(); else loadWorkbench() }
function resetFilters() { statusFilter.value = ''; dateRangeFilter.value = null; currentPage.value = 1; loadData() }
function resetSubmissionQuery() { Object.assign(submissionQuery, { page: 1, keyword: '', reportType: '', status: '', receiptStatus: '' }); loadSubmissions() }
function changeSubmissionSize() { submissionQuery.page = 1; loadSubmissions() }

function optionLabel(options: { label: string; value: string }[], value?: string) { return options.find(item => item.value === value)?.label || value || '-' }
function reportStatusLabel(status: string) { return optionLabel(reportStatusOptions, status) }
function reportStatusTagType(status: string) { return ({ DRAFT: 'warning', REVIEWED: 'primary', SUBMITTED: 'success', FAILED: 'danger', RESUBMITTED: 'success' }[status] || 'info') as any }
function submissionTagType(status?: string) { return status === 'ACCEPTED' ? 'success' : status === 'REJECTED' || status === 'FAILED' ? 'danger' : status === 'SUBMITTED' ? 'warning' : 'info' }
function receiptTagType(status?: string) { return status === 'ACCEPTED' ? 'success' : status === 'REJECTED' ? 'danger' : status === 'PENDING' ? 'warning' : 'info' }
function receiptTimelineType(status: string) { return receiptTagType(status) as any }
function receiptSourceLabel(source: string) { return ({ GATEWAY: '网关即时回执', POLL: '主动查询', CALLBACK: '监管回调' }[source] || source) }
function shortHash(value?: string) { return value && value.length > 28 ? `${value.slice(0, 14)}…${value.slice(-10)}` : value || '-' }
function formatAmount(amount: number | string | undefined) { return amount == null ? '-' : Number(amount).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }
function transactionTypeLabel(type?: string) { return type ? ({ PREMIUM: '保费缴纳', CLAIM: '理赔', SURRENDER: '退保', LOAN: '保单贷款', REFUND: '退款' }[type] || type) : '-' }
function paymentMethodLabel(method?: string) { return method ? ({ CASH: '现金', TRANSFER: '转账', BANK_TRANSFER: '银行转账', ONLINE: '线上支付', COUNTER: '柜面' }[method] || method) : '-' }
function formatReportReviewer(row: any) { return formatOperatorName(row?.reviewedBy, row?.reportStatus === 'DRAFT' ? '待审核' : '待补录') }
function formatCounterparty(value?: string) { if (!value) return '-'; try { const data = JSON.parse(value); return [data.name || data.counterparty, data.account, data.bank].filter(Boolean).join(' / ') || value } catch { return value } }
function normalizePositiveId(value: string | number | undefined | null) { const text = String(value ?? '').trim(); return /^\d+$/.test(text) ? text : '' }

const generateDialogVisible = ref(false)
const generating = ref(false)
const generateFormRef = ref<FormInstance>()
const generateForm = reactive({ transactionId: '' })
const generateRules: FormRules = { transactionId: [{ required: true, message: '请输入交易ID', trigger: 'blur' }] }
function openGenerateDialog() { generateForm.transactionId = ''; generateDialogVisible.value = true }
async function handleGenerate() { try { await generateFormRef.value?.validate() } catch { return }; const transactionId = normalizePositiveId(generateForm.transactionId); if (!transactionId) { ElMessage.warning('请输入有效的交易ID'); return } generating.value = true; try { await request.post('/reporting/large-txn/generate', undefined, { params: { transactionId } }); ElMessage.success('报告生成成功'); generateDialogVisible.value = false; loadData() } finally { generating.value = false } }

const detailDialogVisible = ref(false)
const detailData = ref<any>(null)
function openDetail(row: any) { detailData.value = row; detailDialogVisible.value = true }
const reviewDialogVisible = ref(false)
const reviewTargetId = ref<number | string>('')
const reviewing = ref(false)
const reviewForm = reactive({ approved: true, remark: '' })
function openReviewDialog(row: any) { reviewTargetId.value = row.id; reviewForm.approved = true; reviewForm.remark = ''; reviewDialogVisible.value = true }
async function handleReview() { if (!reviewForm.approved) { ElMessage.warning('当前大额交易报告接口暂不支持驳回'); return } reviewing.value = true; try { await request.post(`/reporting/large-txn/${reviewTargetId.value}/review`, undefined, { params: { reviewedBy: currentOperatorName(userStore.userInfo) } }); ElMessage.success('审核通过'); reviewDialogVisible.value = false; loadData() } finally { reviewing.value = false } }
async function handleSubmitReport(row: any) { try { await ElMessageBox.confirm('系统将生成XML、完成校验与签名并通过默认监管连接器提交。确认继续？', '提交监管报送', { type: 'warning' }); await request.post(`/reporting/large-txn/${row.id}/submit`); ElMessage.success('已进入监管报送流水线'); await Promise.all([loadData(), loadSubmissionOverview(), loadSubmissions()]) } catch { /* cancelled or handled */ } }
async function handleExportXml(row: any) { try { const res: any = await request.get(`/reporting/large-txn/${row.id}/xml`, { responseType: 'blob' as any }); const blob = res instanceof Blob ? res : new Blob([res], { type: 'application/xml' }); const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = `${row.reportNo || 'report'}.xml`; link.click(); URL.revokeObjectURL(url); ElMessage.success('XML导出成功') } catch { ElMessage.error('XML导出失败') } }
const retryingFailed = ref(false)
async function handleRetryFailed() { try { await ElMessageBox.confirm('确认重试历史失败报送？新流程建议在报送工作台逐条修正重报。', '重试历史失败', { type: 'warning' }); retryingFailed.value = true; await request.post('/reporting/large-txn/retry-failed'); ElMessage.success('历史失败重试已处理'); await Promise.all([loadData(), loadSubmissionOverview(), loadSubmissions()]) } catch { /* cancelled or handled */ } finally { retryingFailed.value = false } }

async function openSubmissionDetail(row: RegulatorySubmission) { const res: any = await regulatorySubmissionApi.getDetail(row.id); submissionDetail.value = res.data; submissionDrawerVisible.value = true }
async function pollReceipt(row: RegulatorySubmission) { actionId.value = row.id; try { const res: any = await regulatorySubmissionApi.pollReceipt(row.id); const status = res.data?.receiptStatus; ElMessage({ type: status === 'ACCEPTED' ? 'success' : status === 'REJECTED' ? 'error' : 'warning', message: status === 'ACCEPTED' ? '监管回执已受理' : status === 'REJECTED' ? '监管回执已退回' : '回执仍在处理中' }); await Promise.all([loadSubmissionOverview(), loadSubmissions()]) } finally { actionId.value = '' } }
function openResubmit(row: RegulatorySubmission) { resubmitTarget.value = row; resubmitForm.connectorId = regulatoryConnectors.value.find(item => item.healthStatus === 'HEALTHY')?.id || row.connectorId || ''; resubmitForm.correctionNote = ''; resubmitDialogVisible.value = true }
async function submitResubmission() { if (!resubmitTarget.value) return; if (!resubmitForm.correctionNote.trim()) { ElMessage.warning('请填写退回修正说明'); return } resubmitting.value = true; try { const res: any = await regulatorySubmissionApi.resubmit(resubmitTarget.value.id, { connectorId: resubmitForm.connectorId || undefined, correctionNote: resubmitForm.correctionNote.trim() }); const accepted = res.data?.status === 'ACCEPTED'; ElMessage({ type: accepted ? 'success' : 'warning', message: accepted ? `V${res.data.versionNo} 重报已受理` : `V${res.data?.versionNo || ''} 重报已发送，请关注回执` }); resubmitDialogVisible.value = false; await Promise.all([loadSubmissionOverview(), loadSubmissions(), loadData()]) } finally { resubmitting.value = false } }

onMounted(() => Promise.all([loadData(), loadWorkbench()]))
</script>

<style scoped>
.reporting-page { display: flex; flex-direction: column; gap: 16px; min-width: 0; }
.page-heading,.section-heading,.drawer-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.page-heading h1 { margin: 0; color: var(--text-primary); font-size: 24px; line-height: 1.35; letter-spacing: 0; }
.page-heading p { margin: 6px 0 0; color: var(--text-secondary); font-size: 14px; }
.heading-actions,.table-actions { display: flex; align-items: center; gap: 6px; white-space: nowrap; }
.view-switcher { display: flex; justify-content: flex-start; }
.workspace { min-width: 0; padding: 16px; border: 1px solid var(--border-color); border-radius: 6px; background: var(--bg-card); }
.section-heading { align-items: center; margin-bottom: 16px; }
.section-heading h2 { margin: 0; color: var(--text-primary); font-size: 17px; letter-spacing: 0; }
.section-heading span { display: block; margin-top: 4px; color: var(--text-secondary); font-size: 13px; }
.toolbar { display: grid; gap: 10px; margin-bottom: 14px; }
.report-toolbar { grid-template-columns: 170px minmax(280px, 1fr) auto auto; }
.submission-toolbar { grid-template-columns: minmax(220px, 1fr) 150px 140px 130px auto auto; }
.pagination-row { display: flex; justify-content: flex-end; margin-top: 16px; }
.metric-grid { display: grid; grid-template-columns: repeat(7, minmax(108px, 1fr)); border: 1px solid var(--border-color); border-radius: 6px; background: var(--bg-card); overflow: hidden; }
.metric-item { min-width: 0; padding: 15px 17px; border-top: 3px solid #3b82f6; border-right: 1px solid var(--border-color); }
.metric-item:last-child { border-right: 0; }.metric-item span { display: block; color: var(--text-secondary); font-size: 13px; white-space: nowrap; }.metric-item strong { display: block; margin-top: 6px; color: var(--text-primary); font-size: 24px; line-height: 1; }
.metric-item.success { border-top-color: #16a34a; }.metric-item.warning { border-top-color: #d97706; }.metric-item.danger { border-top-color: #dc2626; }
.drawer-heading { align-items: center; width: 100%; padding-right: 24px; }.drawer-heading h2 { margin: 0; color: var(--text-primary); font-size: 20px; letter-spacing: 0; }.drawer-heading span { display: block; margin-top: 4px; color: var(--text-secondary); font-size: 13px; }
.drawer-alert,.resubmit-alert { margin-bottom: 16px; }.detail-block { margin-top: 20px; }.detail-block h3 { margin: 0 0 12px; color: var(--text-primary); font-size: 15px; letter-spacing: 0; }.detail-block p { margin: 5px 0; color: var(--text-primary); }.detail-block small { color: var(--text-secondary); }
.mono-text { font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; font-size: 12px; }.payload-preview { max-height: 260px; margin: 0; padding: 14px; overflow: auto; border: 1px solid var(--border-color); border-radius: 4px; background: var(--bg-page); color: var(--text-secondary); font-size: 12px; line-height: 1.6; white-space: pre-wrap; word-break: break-all; }
@media (max-width: 1280px) { .metric-grid { grid-template-columns: repeat(4, 1fr); }.submission-toolbar { grid-template-columns: 1fr 1fr 1fr; } }
@media (max-width: 900px) { .metric-grid { grid-template-columns: repeat(2, 1fr); }.report-toolbar,.submission-toolbar { grid-template-columns: 1fr 1fr; } }
@media (max-width: 640px) { .page-heading { flex-direction: column; }.heading-actions { width: 100%; flex-wrap: wrap; }.report-toolbar,.submission-toolbar { grid-template-columns: 1fr; }.workspace { padding: 12px; } }
</style>
