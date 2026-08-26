<template>
  <div class="integration-page">
    <section class="page-heading">
      <div>
        <h1>集成中心</h1>
        <p>统一管理外部系统连接、数据同步任务、运行监控和失败重试</p>
      </div>
      <div class="heading-actions">
        <el-button v-if="canManage && activeView === 'connectors'" type="primary" @click="openConnectorCreate">
          <el-icon><Plus /></el-icon>新建连接器
        </el-button>
        <el-button v-if="canManage && activeView === 'jobs'" type="primary" @click="openJobCreate">
          <el-icon><Plus /></el-icon>新建任务
        </el-button>
      </div>
    </section>

    <section class="metric-grid" aria-label="集成运行概览">
      <div class="metric-item"><span>连接器</span><strong>{{ overview.totalConnectors }}</strong></div>
      <div class="metric-item success"><span>健康连接</span><strong>{{ overview.healthyConnectors }}</strong></div>
      <div class="metric-item danger"><span>异常连接</span><strong>{{ overview.unhealthyConnectors }}</strong></div>
      <div class="metric-item"><span>启用任务</span><strong>{{ overview.enabledJobs }}</strong></div>
      <div class="metric-item warning"><span>运行中</span><strong>{{ overview.runningJobs }}</strong></div>
      <div class="metric-item danger"><span>今日失败</span><strong>{{ overview.failedRunsToday }}</strong></div>
      <div class="metric-item success"><span>今日成功率</span><strong>{{ overview.successRateToday }}%</strong></div>
    </section>

    <section class="workspace">
      <div class="workspace-nav">
        <el-segmented v-model="activeView" :options="viewOptions" @change="handleViewChange" />
        <span class="written-count">今日写入 {{ overview.recordsWrittenToday }} 条</span>
      </div>

      <template v-if="activeView === 'connectors'">
        <div class="toolbar connector-toolbar">
          <el-input v-model="connectorQuery.keyword" clearable placeholder="连接器名称 / 编码" @keyup.enter="loadConnectors">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="connectorQuery.businessType" clearable placeholder="业务类型" @change="loadConnectors">
            <el-option v-for="item in businessTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="connectorQuery.healthStatus" clearable placeholder="健康状态" @change="loadConnectors">
            <el-option v-for="item in healthOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-button type="primary" @click="loadConnectors"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="resetConnectorQuery"><el-icon><RefreshLeft /></el-icon>重置</el-button>
        </div>

        <el-table :data="connectors" v-loading="loading" stripe border height="540" style="width:100%">
          <el-table-column prop="connectorCode" label="连接器编码" min-width="160" fixed="left" />
          <el-table-column prop="connectorName" label="连接器名称" min-width="230" fixed="left" show-overflow-tooltip />
          <el-table-column prop="businessType" label="业务类型" width="150"><template #default="{ row }">{{ optionLabel(businessTypeOptions, row.businessType) }}</template></el-table-column>
          <el-table-column prop="transportType" label="传输" width="90"><template #default="{ row }"><el-tag type="info" size="small">{{ row.transportType }}</el-tag></template></el-table-column>
          <el-table-column prop="endpointUrl" label="端点地址" min-width="260" show-overflow-tooltip />
          <el-table-column prop="healthStatus" label="健康状态" width="110">
            <template #default="{ row }"><el-tag :type="healthTagType(row.healthStatus)" size="small">{{ optionLabel(healthOptions, row.healthStatus) }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="lastHealthCheckTime" label="最近检查" min-width="170"><template #default="{ row }">{{ row.lastHealthCheckTime || '-' }}</template></el-table-column>
          <el-table-column prop="status" label="状态" width="90"><template #default="{ row }"><el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">{{ row.status === 'ENABLED' ? '启用' : '停用' }}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="170" fixed="right">
            <template #default="{ row }">
              <div class="table-actions">
                <el-button v-if="canExecute" link type="success" size="small" :loading="actionId === row.id" @click="testConnector(row)">连接测试</el-button>
                <el-button v-if="canManage" link type="primary" size="small" @click="openConnectorEdit(row)">编辑</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
        <PaginationRow :total="connectorTotal" :page="connectorQuery.page" :size="connectorQuery.size" @page="changeConnectorPage" @size="changeConnectorSize" />
      </template>

      <template v-else-if="activeView === 'jobs'">
        <div class="toolbar job-toolbar">
          <el-input v-model="jobQuery.keyword" clearable placeholder="任务名称 / 编码" @keyup.enter="loadJobs">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="jobQuery.connectorId" clearable placeholder="连接器" @change="loadJobs">
            <el-option v-for="item in enabledConnectors" :key="item.id" :label="item.connectorName" :value="item.id" />
          </el-select>
          <el-select v-model="jobQuery.executionStatus" clearable placeholder="执行状态" @change="loadJobs">
            <el-option v-for="item in runStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-button type="primary" @click="loadJobs"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="resetJobQuery"><el-icon><RefreshLeft /></el-icon>重置</el-button>
        </div>

        <el-table :data="jobs" v-loading="loading" stripe border height="540" style="width:100%">
          <el-table-column prop="jobCode" label="任务编码" min-width="160" fixed="left" />
          <el-table-column prop="jobName" label="任务名称" min-width="220" fixed="left" show-overflow-tooltip />
          <el-table-column prop="connectorName" label="连接器" min-width="210" show-overflow-tooltip />
          <el-table-column prop="businessObject" label="业务对象" width="140"><template #default="{ row }">{{ optionLabel(businessObjectOptions, row.businessObject) }}</template></el-table-column>
          <el-table-column prop="direction" label="方向" width="100"><template #default="{ row }">{{ optionLabel(directionOptions, row.direction) }}</template></el-table-column>
          <el-table-column prop="cronExpression" label="调度表达式" min-width="160" />
          <el-table-column prop="nextRunTime" label="下次运行" min-width="170"><template #default="{ row }">{{ row.enabled ? row.nextRunTime || '-' : '-' }}</template></el-table-column>
          <el-table-column prop="executionStatus" label="执行状态" width="100"><template #default="{ row }"><el-tag :type="runTagType(row.executionStatus)" size="small">{{ optionLabel(runStatusOptions, row.executionStatus) }}</el-tag></template></el-table-column>
          <el-table-column prop="enabled" label="启用" width="80"><template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '是' : '否' }}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <div class="table-actions">
                <el-button v-if="canExecute && row.enabled" link type="success" size="small" :loading="actionId === row.id" @click="runJob(row)">立即执行</el-button>
                <el-button v-if="canManage && row.executionStatus !== 'RUNNING'" link type="primary" size="small" @click="openJobEdit(row)">编辑</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
        <PaginationRow :total="jobTotal" :page="jobQuery.page" :size="jobQuery.size" @page="changeJobPage" @size="changeJobSize" />
      </template>

      <template v-else>
        <div class="toolbar run-toolbar">
          <el-select v-model="runQuery.connectorId" clearable placeholder="连接器" @change="loadRuns">
            <el-option v-for="item in enabledConnectors" :key="item.id" :label="item.connectorName" :value="item.id" />
          </el-select>
          <el-select v-model="runQuery.status" clearable placeholder="运行状态" @change="loadRuns">
            <el-option v-for="item in finalRunStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="runQuery.triggerType" clearable placeholder="触发方式" @change="loadRuns">
            <el-option v-for="item in triggerOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-button type="primary" @click="loadRuns"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="resetRunQuery"><el-icon><RefreshLeft /></el-icon>重置</el-button>
        </div>

        <el-table :data="runs" v-loading="loading" stripe border height="540" style="width:100%">
          <el-table-column prop="runNo" label="运行编号" min-width="230" fixed="left" show-overflow-tooltip />
          <el-table-column prop="jobName" label="任务" min-width="210" fixed="left" show-overflow-tooltip />
          <el-table-column prop="connectorName" label="连接器" min-width="200" show-overflow-tooltip />
          <el-table-column prop="triggerType" label="触发方式" width="110"><template #default="{ row }">{{ optionLabel(triggerOptions, row.triggerType) }}</template></el-table-column>
          <el-table-column prop="status" label="状态" width="100"><template #default="{ row }"><el-tag :type="runTagType(row.status)" size="small">{{ optionLabel(finalRunStatusOptions, row.status) }}</el-tag></template></el-table-column>
          <el-table-column label="尝试/重试" width="100"><template #default="{ row }">{{ row.attemptCount }} / {{ row.retryCount }}</template></el-table-column>
          <el-table-column prop="recordsRead" label="读取" width="90" />
          <el-table-column prop="recordsWritten" label="写入" width="90" />
          <el-table-column prop="recordsSkipped" label="跳过" width="90" />
          <el-table-column prop="durationMs" label="耗时" width="100"><template #default="{ row }">{{ formatDuration(row.durationMs) }}</template></el-table-column>
          <el-table-column prop="startedTime" label="开始时间" min-width="170" />
          <el-table-column label="操作" width="130" fixed="right">
            <template #default="{ row }">
              <div class="table-actions">
                <el-button link type="primary" size="small" @click="openRunDetail(row)">详情</el-button>
                <el-button v-if="canExecute && row.status === 'FAILED' && row.jobId" link type="danger" size="small" :loading="actionId === row.id" @click="retryRun(row)">重试</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
        <PaginationRow :total="runTotal" :page="runQuery.page" :size="runQuery.size" @page="changeRunPage" @size="changeRunSize" />
      </template>
    </section>

    <el-dialog v-model="connectorDialogVisible" :title="editingConnectorId ? '编辑连接器' : '新建连接器'" width="min(760px, calc(100vw - 32px))" top="7vh" class="integration-form-dialog" destroy-on-close>
      <el-form ref="connectorFormRef" :model="connectorForm" :rules="connectorRules" label-width="112px">
        <div class="form-grid">
          <el-form-item label="连接器编码" prop="connectorCode"><el-input v-model="connectorForm.connectorCode" placeholder="如 CORE_TXN_GATEWAY" /></el-form-item>
          <el-form-item label="连接器名称" prop="connectorName"><el-input v-model="connectorForm.connectorName" /></el-form-item>
          <el-form-item label="业务类型" prop="businessType"><el-select v-model="connectorForm.businessType" style="width:100%"><el-option v-for="item in businessTypeOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
          <el-form-item label="传输类型" prop="transportType"><el-select v-model="connectorForm.transportType" style="width:100%"><el-option label="模拟适配器" value="MOCK" /></el-select></el-form-item>
          <el-form-item class="full-row" label="端点地址" prop="endpointUrl"><el-input v-model="connectorForm.endpointUrl" placeholder="mock://success?read=500&written=498&skipped=2" /></el-form-item>
          <el-form-item label="认证类型" prop="authType"><el-select v-model="connectorForm.authType" style="width:100%"><el-option v-for="item in authTypeOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
          <el-form-item label="凭据引用" :prop="connectorForm.authType === 'NONE' ? '' : 'credentialRef'"><el-input v-model="connectorForm.credentialRef" :disabled="connectorForm.authType === 'NONE'" placeholder="如 AML_CORE_API_KEY" /></el-form-item>
          <el-form-item label="超时（秒）"><el-input-number v-model="connectorForm.timeoutSeconds" :min="1" :max="300" controls-position="right" style="width:100%" /></el-form-item>
          <el-form-item label="最大重试"><el-input-number v-model="connectorForm.maxRetries" :min="0" :max="10" controls-position="right" style="width:100%" /></el-form-item>
          <el-form-item label="重试间隔"><el-input-number v-model="connectorForm.retryIntervalSeconds" :min="0" :max="3600" controls-position="right" style="width:100%" /></el-form-item>
          <el-form-item label="状态"><el-select v-model="connectorForm.status" style="width:100%"><el-option label="启用" value="ENABLED" /><el-option label="停用" value="DISABLED" /></el-select></el-form-item>
        </div>
        <el-form-item label="说明"><el-input v-model="connectorForm.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="connectorDialogVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submitConnector">保存连接器</el-button></template>
    </el-dialog>

    <el-dialog v-model="jobDialogVisible" :title="editingJobId ? '编辑同步任务' : '新建同步任务'" width="min(720px, calc(100vw - 32px))" top="8vh" class="integration-form-dialog" destroy-on-close>
      <el-form ref="jobFormRef" :model="jobForm" :rules="jobRules" label-width="108px">
        <div class="form-grid">
          <el-form-item label="任务编码" prop="jobCode"><el-input v-model="jobForm.jobCode" placeholder="如 SYNC_CORE_TXN" /></el-form-item>
          <el-form-item label="任务名称" prop="jobName"><el-input v-model="jobForm.jobName" /></el-form-item>
          <el-form-item label="连接器" prop="connectorId"><el-select v-model="jobForm.connectorId" style="width:100%"><el-option v-for="item in enabledConnectors" :key="item.id" :label="item.connectorName" :value="item.id" /></el-select></el-form-item>
          <el-form-item label="业务对象" prop="businessObject"><el-select v-model="jobForm.businessObject" style="width:100%"><el-option v-for="item in businessObjectOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
          <el-form-item label="数据方向" prop="direction"><el-select v-model="jobForm.direction" style="width:100%"><el-option v-for="item in directionOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
          <el-form-item label="Cron表达式" prop="cronExpression"><el-input v-model="jobForm.cronExpression" placeholder="0 0/30 * * * ?" /></el-form-item>
          <el-form-item label="批量大小"><el-input-number v-model="jobForm.batchSize" :min="1" :max="100000" controls-position="right" style="width:100%" /></el-form-item>
          <el-form-item label="最大重试"><el-input-number v-model="jobForm.maxRetries" :min="0" :max="10" controls-position="right" style="width:100%" /></el-form-item>
          <el-form-item label="启用任务"><el-switch v-model="jobForm.enabled" /></el-form-item>
        </div>
        <el-form-item label="说明"><el-input v-model="jobForm.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="jobDialogVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submitJob">保存任务</el-button></template>
    </el-dialog>

    <el-drawer v-model="runDrawerVisible" size="min(680px, 92vw)" destroy-on-close>
      <template #header><div class="drawer-heading"><div><h2>运行详情</h2><span>{{ selectedRun?.runNo }}</span></div><el-tag v-if="selectedRun" :type="runTagType(selectedRun.status)">{{ optionLabel(finalRunStatusOptions, selectedRun.status) }}</el-tag></div></template>
      <template v-if="selectedRun">
        <el-alert v-if="selectedRun.status === 'FAILED'" type="error" show-icon :closable="false" :title="selectedRun.errorMessage || '任务执行失败'" class="drawer-alert" />
        <el-descriptions :column="2" border>
          <el-descriptions-item label="任务">{{ selectedRun.jobName || '连接检查' }}</el-descriptions-item>
          <el-descriptions-item label="连接器">{{ selectedRun.connectorName }}</el-descriptions-item>
          <el-descriptions-item label="触发方式">{{ optionLabel(triggerOptions, selectedRun.triggerType) }}</el-descriptions-item>
          <el-descriptions-item label="执行人">{{ selectedRun.executedBy || '-' }}</el-descriptions-item>
          <el-descriptions-item label="尝试次数">{{ selectedRun.attemptCount }}</el-descriptions-item>
          <el-descriptions-item label="重试次数">{{ selectedRun.retryCount }}</el-descriptions-item>
          <el-descriptions-item label="读取/写入">{{ selectedRun.recordsRead }} / {{ selectedRun.recordsWritten }}</el-descriptions-item>
          <el-descriptions-item label="跳过/错误">{{ selectedRun.recordsSkipped }} / {{ selectedRun.errorCount }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ selectedRun.startedTime }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ selectedRun.completedTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="耗时">{{ formatDuration(selectedRun.durationMs) }}</el-descriptions-item>
          <el-descriptions-item label="Trace ID">{{ selectedRun.traceId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="请求摘要" :span="2">{{ selectedRun.requestSummary || '-' }}</el-descriptions-item>
          <el-descriptions-item label="响应摘要" :span="2">{{ selectedRun.responseSummary || '-' }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElPagination, type FormInstance, type FormRules } from 'element-plus'
import { integrationApi } from '@/api/modules'
import { useUserStore } from '@/stores/user'
import type { IntegrationConnector, IntegrationJob, IntegrationOverview, IntegrationRun } from '@/api/types'

const PaginationRow = defineComponent({
  props: { total: { type: Number, required: true }, page: { type: Number, required: true }, size: { type: Number, required: true } },
  emits: ['page', 'size'],
  setup(props, { emit }) {
    return () => h('div', { class: 'pagination-row' }, [h(ElPagination, { background: true, layout: 'total, sizes, prev, pager, next', total: props.total, currentPage: props.page, pageSize: props.size, pageSizes: [10, 20, 50], 'onUpdate:currentPage': (value: number) => emit('page', value), 'onUpdate:pageSize': (value: number) => emit('size', value) })])
  },
})

const userStore = useUserStore()
const canManage = computed(() => userStore.isAdmin || userStore.hasPermission('integration:manage'))
const canExecute = computed(() => userStore.isAdmin || userStore.hasPermission('integration:execute'))
const activeView = ref('connectors')
const viewOptions = [{ label: '连接器', value: 'connectors' }, { label: '同步任务', value: 'jobs' }, { label: '运行记录', value: 'runs' }]
const overview = reactive<IntegrationOverview>({ totalConnectors: 0, healthyConnectors: 0, unhealthyConnectors: 0, enabledJobs: 0, runningJobs: 0, failedRunsToday: 0, recordsWrittenToday: 0, successRateToday: 0 })
const connectors = ref<IntegrationConnector[]>([])
const enabledConnectors = ref<IntegrationConnector[]>([])
const jobs = ref<IntegrationJob[]>([])
const runs = ref<IntegrationRun[]>([])
const selectedRun = ref<IntegrationRun | null>(null)
const connectorTotal = ref(0)
const jobTotal = ref(0)
const runTotal = ref(0)
const loading = ref(false)
const submitting = ref(false)
const actionId = ref('')

const connectorQuery = reactive({ page: 1, size: 20, keyword: '', businessType: '', healthStatus: '' })
const jobQuery = reactive({ page: 1, size: 20, keyword: '', connectorId: '', executionStatus: '' })
const runQuery = reactive({ page: 1, size: 20, connectorId: '', status: '', triggerType: '' })
const connectorDialogVisible = ref(false)
const jobDialogVisible = ref(false)
const runDrawerVisible = ref(false)
const editingConnectorId = ref('')
const editingJobId = ref('')
const connectorFormRef = ref<FormInstance>()
const jobFormRef = ref<FormInstance>()

const connectorForm = reactive({ connectorCode: '', connectorName: '', businessType: 'CORE_BUSINESS', transportType: 'MOCK', endpointUrl: 'mock://success?read=500&written=498&skipped=2', authType: 'NONE', credentialRef: '', status: 'ENABLED', timeoutSeconds: 30, maxRetries: 2, retryIntervalSeconds: 30, description: '' })
const jobForm = reactive({ jobCode: '', jobName: '', connectorId: '', businessObject: 'TRANSACTION', direction: 'INBOUND', cronExpression: '0 0/30 * * * ?', batchSize: 1000, maxRetries: 2, enabled: true, description: '' })

const businessTypeOptions = [{ label: '核心业务数据', value: 'CORE_BUSINESS' }, { label: '制裁名单', value: 'WATCHLIST' }, { label: '监管报送', value: 'REGULATORY_REPORTING' }, { label: '身份核验', value: 'IDENTITY_VERIFICATION' }, { label: 'KYC数据', value: 'KYC_DATA' }, { label: '文件交换', value: 'DOCUMENT_EXCHANGE' }]
const businessObjectOptions = [{ label: '交易数据', value: 'TRANSACTION' }, { label: '客户数据', value: 'CUSTOMER' }, { label: '名单数据', value: 'WATCHLIST' }, { label: '监管报告', value: 'REGULATORY_REPORT' }, { label: '身份核验', value: 'IDENTITY_RESULT' }, { label: '业务文件', value: 'BUSINESS_FILE' }]
const directionOptions = [{ label: '入站', value: 'INBOUND' }, { label: '出站', value: 'OUTBOUND' }, { label: '双向', value: 'BIDIRECTIONAL' }]
const authTypeOptions = [{ label: '无认证', value: 'NONE' }, { label: '基础认证', value: 'BASIC' }, { label: 'API Key', value: 'API_KEY' }, { label: 'OAuth2', value: 'OAUTH2' }]
const healthOptions = [{ label: '未知', value: 'UNKNOWN' }, { label: '健康', value: 'HEALTHY' }, { label: '异常', value: 'UNHEALTHY' }]
const runStatusOptions = [{ label: '空闲', value: 'IDLE' }, { label: '运行中', value: 'RUNNING' }, { label: '成功', value: 'SUCCESS' }, { label: '失败', value: 'FAILED' }, { label: '已停用', value: 'DISABLED' }]
const finalRunStatusOptions = [{ label: '运行中', value: 'RUNNING' }, { label: '成功', value: 'SUCCESS' }, { label: '失败', value: 'FAILED' }]
const triggerOptions = [{ label: '连接检查', value: 'HEALTH_CHECK' }, { label: '手动', value: 'MANUAL' }, { label: '定时', value: 'SCHEDULED' }, { label: '重试', value: 'RETRY' }]

const connectorRules: FormRules = { connectorCode: [{ required: true, message: '请输入连接器编码', trigger: 'blur' }], connectorName: [{ required: true, message: '请输入连接器名称', trigger: 'blur' }], businessType: [{ required: true, message: '请选择业务类型', trigger: 'change' }], transportType: [{ required: true, message: '请选择传输类型', trigger: 'change' }], endpointUrl: [{ required: true, message: '请输入端点地址', trigger: 'blur' }], authType: [{ required: true, message: '请选择认证类型', trigger: 'change' }] }
const jobRules: FormRules = { jobCode: [{ required: true, message: '请输入任务编码', trigger: 'blur' }], jobName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }], connectorId: [{ required: true, message: '请选择连接器', trigger: 'change' }], businessObject: [{ required: true, message: '请选择业务对象', trigger: 'change' }], direction: [{ required: true, message: '请选择数据方向', trigger: 'change' }], cronExpression: [{ required: true, message: '请输入Cron表达式', trigger: 'blur' }] }

onMounted(() => loadAll())

async function loadAll() { await Promise.all([loadOverview(), loadEnabledConnectors(), loadConnectors(), loadJobs(), loadRuns()]) }
async function loadOverview() { const res: any = await integrationApi.getOverview(); Object.assign(overview, res.data || {}) }
async function loadEnabledConnectors() { const res: any = await integrationApi.getEnabledConnectors(); enabledConnectors.value = res.data || [] }
async function loadConnectors() { loading.value = true; try { const res: any = await integrationApi.getConnectors({ page: connectorQuery.page, size: connectorQuery.size, keyword: connectorQuery.keyword || undefined, businessType: connectorQuery.businessType || undefined, healthStatus: connectorQuery.healthStatus || undefined }); connectors.value = res.data?.list || []; connectorTotal.value = Number(res.data?.total || 0) } finally { loading.value = false } }
async function loadJobs() { loading.value = true; try { const res: any = await integrationApi.getJobs({ page: jobQuery.page, size: jobQuery.size, keyword: jobQuery.keyword || undefined, connectorId: jobQuery.connectorId || undefined, executionStatus: jobQuery.executionStatus || undefined }); jobs.value = res.data?.list || []; jobTotal.value = Number(res.data?.total || 0) } finally { loading.value = false } }
async function loadRuns() { loading.value = true; try { const res: any = await integrationApi.getRuns({ page: runQuery.page, size: runQuery.size, connectorId: runQuery.connectorId || undefined, status: runQuery.status || undefined, triggerType: runQuery.triggerType || undefined }); runs.value = res.data?.list || []; runTotal.value = Number(res.data?.total || 0) } finally { loading.value = false } }
function handleViewChange() { if (activeView.value === 'connectors') loadConnectors(); else if (activeView.value === 'jobs') loadJobs(); else loadRuns() }

function resetConnectorQuery() { Object.assign(connectorQuery, { page: 1, keyword: '', businessType: '', healthStatus: '' }); loadConnectors() }
function resetJobQuery() { Object.assign(jobQuery, { page: 1, keyword: '', connectorId: '', executionStatus: '' }); loadJobs() }
function resetRunQuery() { Object.assign(runQuery, { page: 1, connectorId: '', status: '', triggerType: '' }); loadRuns() }
function changeConnectorPage(page: number) { connectorQuery.page = page; loadConnectors() }
function changeConnectorSize(size: number) { connectorQuery.size = size; connectorQuery.page = 1; loadConnectors() }
function changeJobPage(page: number) { jobQuery.page = page; loadJobs() }
function changeJobSize(size: number) { jobQuery.size = size; jobQuery.page = 1; loadJobs() }
function changeRunPage(page: number) { runQuery.page = page; loadRuns() }
function changeRunSize(size: number) { runQuery.size = size; runQuery.page = 1; loadRuns() }

function resetConnectorForm() { Object.assign(connectorForm, { connectorCode: '', connectorName: '', businessType: 'CORE_BUSINESS', transportType: 'MOCK', endpointUrl: 'mock://success?read=500&written=498&skipped=2', authType: 'NONE', credentialRef: '', status: 'ENABLED', timeoutSeconds: 30, maxRetries: 2, retryIntervalSeconds: 30, description: '' }) }
function openConnectorCreate() { editingConnectorId.value = ''; resetConnectorForm(); connectorDialogVisible.value = true }
function openConnectorEdit(row: IntegrationConnector) { editingConnectorId.value = row.id; Object.assign(connectorForm, { ...row, credentialRef: row.credentialRef || '', description: row.description || '' }); connectorDialogVisible.value = true }
async function submitConnector() { await connectorFormRef.value?.validate(); if (connectorForm.authType !== 'NONE' && !connectorForm.credentialRef.trim()) { ElMessage.warning('请输入凭据引用名'); return } submitting.value = true; try { const payload = { ...connectorForm, credentialRef: connectorForm.authType === 'NONE' ? undefined : connectorForm.credentialRef || undefined }; if (editingConnectorId.value) await integrationApi.updateConnector(editingConnectorId.value, payload); else await integrationApi.createConnector(payload); ElMessage.success('连接器已保存'); connectorDialogVisible.value = false; await Promise.all([loadOverview(), loadEnabledConnectors(), loadConnectors()]) } finally { submitting.value = false } }

function resetJobForm() { Object.assign(jobForm, { jobCode: '', jobName: '', connectorId: enabledConnectors.value[0]?.id || '', businessObject: 'TRANSACTION', direction: 'INBOUND', cronExpression: '0 0/30 * * * ?', batchSize: 1000, maxRetries: 2, enabled: true, description: '' }) }
function openJobCreate() { editingJobId.value = ''; resetJobForm(); jobDialogVisible.value = true }
function openJobEdit(row: IntegrationJob) { editingJobId.value = row.id; Object.assign(jobForm, { ...row, description: row.description || '' }); jobDialogVisible.value = true }
async function submitJob() { await jobFormRef.value?.validate(); submitting.value = true; try { const payload = { ...jobForm }; if (editingJobId.value) await integrationApi.updateJob(editingJobId.value, payload); else await integrationApi.createJob(payload); ElMessage.success('同步任务已保存'); jobDialogVisible.value = false; await Promise.all([loadOverview(), loadJobs()]) } finally { submitting.value = false } }

async function testConnector(row: IntegrationConnector) { actionId.value = row.id; try { const res: any = await integrationApi.testConnector(row.id); ElMessage({ type: res.data?.status === 'SUCCESS' ? 'success' : 'error', message: res.data?.status === 'SUCCESS' ? '连接检查通过' : res.data?.errorMessage || '连接检查失败' }); await Promise.all([loadOverview(), loadConnectors(), loadRuns()]) } finally { actionId.value = '' } }
async function runJob(row: IntegrationJob) { actionId.value = row.id; try { const res: any = await integrationApi.runJob(row.id); ElMessage({ type: res.data?.status === 'SUCCESS' ? 'success' : 'error', message: res.data?.status === 'SUCCESS' ? `任务完成，写入 ${res.data.recordsWritten} 条` : res.data?.errorMessage || '任务执行失败' }); await Promise.all([loadOverview(), loadJobs(), loadRuns()]) } finally { actionId.value = '' } }
async function retryRun(row: IntegrationRun) { actionId.value = row.id; try { const res: any = await integrationApi.retryRun(row.id); ElMessage({ type: res.data?.status === 'SUCCESS' ? 'success' : 'error', message: res.data?.status === 'SUCCESS' ? '重试执行成功' : res.data?.errorMessage || '重试执行失败' }); await Promise.all([loadOverview(), loadJobs(), loadRuns()]) } finally { actionId.value = '' } }
async function openRunDetail(row: IntegrationRun) { const res: any = await integrationApi.getRun(row.id); selectedRun.value = res.data; runDrawerVisible.value = true }

function optionLabel(options: { label: string; value: string }[], value?: string) { return options.find(item => item.value === value)?.label || value || '-' }
function healthTagType(status: string) { return status === 'HEALTHY' ? 'success' : status === 'UNHEALTHY' ? 'danger' : 'info' }
function runTagType(status: string) { if (status === 'SUCCESS') return 'success'; if (status === 'FAILED') return 'danger'; if (status === 'RUNNING') return 'warning'; return 'info' }
function formatDuration(value?: number) { if (value == null) return '-'; return value < 1000 ? `${value} ms` : `${(value / 1000).toFixed(1)} s` }
</script>

<style scoped>
.integration-page { display: flex; flex-direction: column; gap: 16px; min-width: 0; }
.page-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }.page-heading h1 { margin: 0; color: var(--text-primary); font-size: 24px; line-height: 1.35; letter-spacing: 0; }.page-heading p { margin: 6px 0 0; color: var(--text-secondary); font-size: 14px; }.heading-actions { display: flex; gap: 8px; }
.metric-grid { display: grid; grid-template-columns: repeat(7, minmax(110px, 1fr)); border: 1px solid var(--border-color); border-radius: 6px; background: var(--bg-card); overflow: hidden; }.metric-item { min-width: 0; padding: 16px 18px; border-right: 1px solid var(--border-color); border-top: 3px solid #3b82f6; }.metric-item:last-child { border-right: 0; }.metric-item span { display: block; color: var(--text-secondary); font-size: 13px; white-space: nowrap; }.metric-item strong { display: block; margin-top: 6px; color: var(--text-primary); font-size: 25px; line-height: 1; }.metric-item.success { border-top-color: #16a34a; }.metric-item.warning { border-top-color: #d97706; }.metric-item.danger { border-top-color: #dc2626; }
.workspace { min-width: 0; padding: 16px; border: 1px solid var(--border-color); border-radius: 6px; background: var(--bg-card); }.workspace-nav { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 16px; }.written-count { color: var(--text-secondary); font-size: 13px; }.toolbar { display: grid; gap: 10px; margin-bottom: 14px; }.connector-toolbar,.job-toolbar { grid-template-columns: minmax(240px, 1fr) 170px 140px auto auto; }.run-toolbar { grid-template-columns: minmax(220px, 1fr) 150px 150px auto auto; }
.table-actions { display: flex; align-items: center; gap: 4px; white-space: nowrap; }.pagination-row { display: flex; justify-content: flex-end; margin-top: 16px; }.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); column-gap: 18px; }.full-row { grid-column: 1 / -1; }.drawer-heading { display: flex; align-items: center; justify-content: space-between; width: 100%; padding-right: 24px; }.drawer-heading h2 { margin: 0; color: var(--text-primary); font-size: 20px; letter-spacing: 0; }.drawer-heading span { display: block; margin-top: 4px; color: var(--text-secondary); font-size: 13px; }.drawer-alert { margin-bottom: 16px; }
:deep(.integration-form-dialog .el-dialog__body) { max-height: calc(86vh - 132px); overflow-y: auto; }
@media (max-width: 1280px) { .metric-grid { grid-template-columns: repeat(4, 1fr); }.metric-item:nth-child(4) { border-right: 0; }.metric-item:nth-child(n+5) { border-top-width: 1px; } }
@media (max-width: 960px) { .connector-toolbar,.job-toolbar,.run-toolbar { grid-template-columns: 1fr 1fr; }.metric-grid { grid-template-columns: repeat(2, 1fr); }.form-grid { grid-template-columns: 1fr; }.full-row { grid-column: auto; } }
@media (max-width: 640px) { .page-heading { align-items: stretch; flex-direction: column; }.connector-toolbar,.job-toolbar,.run-toolbar { grid-template-columns: 1fr; }.workspace-nav { align-items: stretch; flex-direction: column; }.written-count { align-self: flex-end; } }
</style>
