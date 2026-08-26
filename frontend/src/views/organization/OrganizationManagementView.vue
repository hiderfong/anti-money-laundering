<template>
  <div class="organization-page">
    <section class="page-heading">
      <div>
        <h1>机构治理</h1>
        <p>统一管理机构档案、分支体系、反洗钱人员、股权结构及监管登记审批</p>
      </div>
      <el-button v-if="canManage" type="primary" @click="openOrganizationCreate">
        <el-icon><Plus /></el-icon>新建机构
      </el-button>
    </section>

    <section class="metric-grid" aria-label="机构治理概览">
      <div class="metric-item"><span>机构总数</span><strong>{{ overview.totalOrganizations }}</strong></div>
      <div class="metric-item"><span>总机构</span><strong>{{ overview.headOffices }}</strong></div>
      <div class="metric-item"><span>分支机构</span><strong>{{ overview.branches }}</strong></div>
      <div class="metric-item"><span>营业网点</span><strong>{{ overview.outlets }}</strong></div>
      <div class="metric-item warning"><span>待审批</span><strong>{{ overview.pendingReviews }}</strong></div>
      <div class="metric-item danger"><span>待补正</span><strong>{{ overview.rejectedRegistrations }}</strong></div>
      <div class="metric-item success"><span>反洗钱人员</span><strong>{{ overview.amlOfficers }}</strong></div>
    </section>

    <section class="workspace">
      <aside class="tree-panel">
        <div class="panel-heading">
          <div><h2>机构层级</h2><span>本机构及下级范围</span></div>
          <el-button text circle title="刷新机构树" @click="loadTree"><el-icon><Refresh /></el-icon></el-button>
        </div>
        <el-tree
          :data="organizationTree"
          node-key="id"
          default-expand-all
          highlight-current
          :expand-on-click-node="false"
          :props="{ label: 'orgName', children: 'children' }"
          @node-click="filterByTree"
        >
          <template #default="{ data }">
            <div class="tree-node">
              <span class="node-type">{{ shortOrgType(data.orgType) }}</span>
              <span class="node-label">{{ data.orgName }}</span>
            </div>
          </template>
        </el-tree>
        <el-empty v-if="organizationTree.length === 0" description="暂无机构" :image-size="72" />
      </aside>

      <div class="list-panel">
        <div class="toolbar">
          <el-input v-model="query.keyword" clearable placeholder="机构名称 / 编码 / 统一社会信用代码" @keyup.enter="loadOrganizations">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="query.orgType" clearable placeholder="机构类型" @change="loadOrganizations">
            <el-option v-for="item in orgTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="query.registrationStatus" clearable placeholder="登记状态" @change="loadOrganizations">
            <el-option v-for="item in registrationStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-button type="primary" @click="loadOrganizations"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="resetQuery"><el-icon><RefreshLeft /></el-icon>重置</el-button>
        </div>

        <el-table :data="organizations" v-loading="loading" stripe border height="540" style="width: 100%">
          <el-table-column prop="orgCode" label="机构编码" min-width="140" fixed="left" />
          <el-table-column prop="orgName" label="机构名称" min-width="240" fixed="left" show-overflow-tooltip />
          <el-table-column prop="orgType" label="类型" width="110">
            <template #default="{ row }">{{ optionLabel(orgTypeOptions, row.orgType) }}</template>
          </el-table-column>
          <el-table-column prop="unifiedCreditCode" label="统一社会信用代码" min-width="190" />
          <el-table-column prop="legalRepresentative" label="法定代表人/负责人" min-width="150" />
          <el-table-column prop="regulatorName" label="主管监管机构" min-width="190" show-overflow-tooltip />
          <el-table-column prop="registrationStatus" label="登记状态" width="120">
            <template #default="{ row }">
              <el-tag :type="registrationTagType(row.registrationStatus)" size="small">
                {{ optionLabel(registrationStatusOptions, row.registrationStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="运营状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">
                {{ row.status === 'ENABLED' ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="updatedTime" label="更新时间" min-width="170" />
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <div class="table-actions">
                <el-button link type="primary" size="small" @click="openDetail(row)">查看</el-button>
                <el-button v-if="canManage && row.registrationStatus !== 'PENDING_REVIEW'" link type="success" size="small" @click="openOrganizationEdit(row)">编辑</el-button>
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
            :page-sizes="[10, 20, 50]"
            @current-change="handlePageChange"
            @size-change="handleSizeChange"
          />
        </div>
      </div>
    </section>

    <el-dialog
      v-model="organizationDialogVisible"
      :title="editingOrganizationId ? '编辑机构档案' : '新建机构档案'"
      width="min(820px, calc(100vw - 32px))"
      top="5vh"
      class="organization-form-dialog"
      destroy-on-close
    >
      <el-form ref="organizationFormRef" :model="organizationForm" :rules="organizationRules" label-width="132px">
        <div class="form-grid">
          <el-form-item label="机构编码" prop="orgCode"><el-input v-model="organizationForm.orgCode" placeholder="如 HQ-001" /></el-form-item>
          <el-form-item label="机构名称" prop="orgName"><el-input v-model="organizationForm.orgName" /></el-form-item>
          <el-form-item label="机构类型" prop="orgType">
            <el-select v-model="organizationForm.orgType" style="width:100%" @change="handleOrgTypeChange">
              <el-option v-for="item in orgTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="上级机构" :prop="organizationForm.orgType === 'HEAD_OFFICE' ? '' : 'parentId'">
            <el-tree-select
              v-model="organizationForm.parentId"
              :data="organizationTree"
              :props="{ label: 'orgName', value: 'id', children: 'children' }"
              check-strictly clearable default-expand-all
              :disabled="organizationForm.orgType === 'HEAD_OFFICE'"
              style="width:100%"
            />
          </el-form-item>
          <el-form-item label="统一社会信用代码" prop="unifiedCreditCode"><el-input v-model="organizationForm.unifiedCreditCode" /></el-form-item>
          <el-form-item label="LEI编码"><el-input v-model="organizationForm.leiCode" /></el-form-item>
          <el-form-item label="法定代表人/负责人" prop="legalRepresentative"><el-input v-model="organizationForm.legalRepresentative" /></el-form-item>
          <el-form-item label="注册资本（万元）"><el-input-number v-model="organizationForm.registeredCapital" :min="0" :precision="2" controls-position="right" style="width:100%" /></el-form-item>
          <el-form-item label="主管监管机构"><el-input v-model="organizationForm.regulatorName" /></el-form-item>
          <el-form-item v-if="editingOrganizationId" label="运营状态">
            <el-select v-model="organizationForm.status" style="width:100%"><el-option label="启用" value="ENABLED" /><el-option label="停用" value="DISABLED" /></el-select>
          </el-form-item>
        </div>
        <el-form-item label="注册地址" prop="registeredAddress"><el-input v-model="organizationForm.registeredAddress" /></el-form-item>
        <el-form-item label="经营地址" prop="businessAddress"><el-input v-model="organizationForm.businessAddress" /></el-form-item>
        <el-form-item label="经营范围"><el-input v-model="organizationForm.businessScope" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="organizationDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitOrganization">保存机构</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailDrawerVisible" size="88%" destroy-on-close class="organization-drawer">
      <template #header>
        <div v-if="detail" class="drawer-heading">
          <div><h2>{{ detail.organization.orgName }}</h2><span>{{ detail.organization.orgCode }} · {{ optionLabel(orgTypeOptions, detail.organization.orgType) }}</span></div>
          <el-tag :type="registrationTagType(detail.organization.registrationStatus)">{{ optionLabel(registrationStatusOptions, detail.organization.registrationStatus) }}</el-tag>
        </div>
      </template>

      <template v-if="detail">
        <el-alert
          v-if="detail.organization.registrationStatus === 'REJECTED'"
          type="error" show-icon :closable="false"
          title="登记申请已被驳回，请根据审批意见补正资料后重新提交。"
          class="drawer-alert"
        />
        <el-tabs v-model="detailTab">
          <el-tab-pane label="机构档案" name="profile">
            <el-descriptions :column="3" border>
              <el-descriptions-item label="统一社会信用代码">{{ detail.organization.unifiedCreditCode }}</el-descriptions-item>
              <el-descriptions-item label="LEI编码">{{ detail.organization.leiCode || '-' }}</el-descriptions-item>
              <el-descriptions-item label="法定代表人/负责人">{{ detail.organization.legalRepresentative || '-' }}</el-descriptions-item>
              <el-descriptions-item label="注册地址" :span="3">{{ detail.organization.registeredAddress || '-' }}</el-descriptions-item>
              <el-descriptions-item label="经营地址" :span="3">{{ detail.organization.businessAddress || '-' }}</el-descriptions-item>
              <el-descriptions-item label="主管监管机构">{{ detail.organization.regulatorName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="注册资本">{{ detail.organization.registeredCapital == null ? '-' : `${detail.organization.registeredCapital} 万元` }}</el-descriptions-item>
              <el-descriptions-item label="运营状态">{{ detail.organization.status === 'ENABLED' ? '启用' : '停用' }}</el-descriptions-item>
              <el-descriptions-item label="经营范围" :span="3">{{ detail.organization.businessScope || '-' }}</el-descriptions-item>
            </el-descriptions>
          </el-tab-pane>

          <el-tab-pane name="persons">
            <template #label>治理人员 <el-badge :value="detail.persons.length" type="primary" /></template>
            <div class="tab-command"><el-button v-if="canManage && detail.organization.registrationStatus !== 'PENDING_REVIEW'" type="primary" @click="openPersonCreate"><el-icon><Plus /></el-icon>新增人员</el-button></div>
            <el-table :data="detail.persons" border stripe>
              <el-table-column prop="personType" label="人员类型" width="140"><template #default="{ row }">{{ optionLabel(personTypeOptions, row.personType) }}</template></el-table-column>
              <el-table-column prop="personName" label="姓名" width="120" />
              <el-table-column prop="title" label="职务" min-width="150" />
              <el-table-column prop="department" label="部门" min-width="140" />
              <el-table-column prop="phone" label="联系电话" min-width="140" />
              <el-table-column prop="email" label="电子邮箱" min-width="180" />
              <el-table-column label="主要负责人" width="100"><template #default="{ row }"><el-tag v-if="row.primaryFlag" type="success" size="small">是</el-tag><span v-else>-</span></template></el-table-column>
              <el-table-column label="操作" width="80" fixed="right"><template #default="{ row }"><el-button v-if="canManage && detail.organization.registrationStatus !== 'PENDING_REVIEW'" link type="primary" @click="openPersonEdit(row)">编辑</el-button></template></el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane name="shareholders">
            <template #label>股权结构 <el-badge :value="detail.shareholders.length" type="primary" /></template>
            <div class="tab-command"><el-button v-if="canManage && detail.organization.registrationStatus !== 'PENDING_REVIEW'" type="primary" @click="openShareholderCreate"><el-icon><Plus /></el-icon>新增股东</el-button></div>
            <el-table :data="detail.shareholders" border stripe>
              <el-table-column prop="shareholderName" label="股东名称" min-width="220" />
              <el-table-column prop="shareholderType" label="股东类型" width="120"><template #default="{ row }">{{ row.shareholderType === 'ORGANIZATION' ? '机构' : '个人' }}</template></el-table-column>
              <el-table-column prop="registrationCode" label="登记编码" min-width="170" />
              <el-table-column prop="ownershipPercentage" label="持股比例" width="120"><template #default="{ row }">{{ row.ownershipPercentage }}%</template></el-table-column>
              <el-table-column label="控股股东" width="100"><template #default="{ row }"><el-tag v-if="row.controllingFlag" type="warning" size="small">是</el-tag><span v-else>-</span></template></el-table-column>
              <el-table-column label="操作" width="80" fixed="right"><template #default="{ row }"><el-button v-if="canManage && detail.organization.registrationStatus !== 'PENDING_REVIEW'" link type="primary" @click="openShareholderEdit(row)">编辑</el-button></template></el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane name="registration">
            <template #label>登记审批 <el-badge :value="detail.registrations.length" type="warning" /></template>
            <div class="tab-command">
              <el-button v-if="canManage && canCreateRegistration" type="primary" @click="createRegistration"><el-icon><DocumentAdd /></el-icon>新建登记申请</el-button>
            </div>
            <el-table :data="detail.registrations" border stripe>
              <el-table-column prop="registrationNo" label="申请编号" min-width="220" fixed="left" />
              <el-table-column prop="registrationType" label="申请类型" width="100"><template #default="{ row }">{{ row.registrationType === 'INITIAL' ? '首次登记' : '变更登记' }}</template></el-table-column>
              <el-table-column prop="version" label="版本" width="80" />
              <el-table-column prop="status" label="状态" width="120"><template #default="{ row }"><el-tag :type="registrationTagType(row.status)" size="small">{{ optionLabel(registrationStatusOptions, row.status) }}</el-tag></template></el-table-column>
              <el-table-column prop="submittedBy" label="提交人" width="110" />
              <el-table-column prop="submittedAt" label="提交时间" min-width="170" />
              <el-table-column prop="reviewedBy" label="审批人" width="110" />
              <el-table-column prop="reviewOpinion" label="审批意见" min-width="220" show-overflow-tooltip />
              <el-table-column label="操作" width="160" fixed="right">
                <template #default="{ row }">
                  <div class="table-actions">
                    <el-button v-if="canManage && ['DRAFT', 'REJECTED'].includes(row.status)" link type="success" @click="submitRegistration(row)">提交</el-button>
                    <el-button v-if="canReview && row.status === 'PENDING_REVIEW'" link type="primary" @click="openReview(row)">审批</el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>

            <h3 class="subsection-title">审批轨迹</h3>
            <el-timeline class="review-timeline">
              <el-timeline-item v-for="log in detail.reviewLogs" :key="log.id" :timestamp="log.operatedAt" placement="top" :type="timelineType(log.actionType)">
                <strong>{{ actionLabel(log.actionType) }}</strong>
                <span>{{ log.operator || 'system' }} · {{ log.fromStatus ? `${optionLabel(registrationStatusOptions, log.fromStatus)} → ` : '' }}{{ optionLabel(registrationStatusOptions, log.toStatus) }}</span>
                <p v-if="log.opinion">{{ log.opinion }}</p>
              </el-timeline-item>
            </el-timeline>
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>

    <el-dialog v-model="personDialogVisible" :title="editingPersonId ? '编辑治理人员' : '新增治理人员'" width="680px" destroy-on-close>
      <el-form :model="personForm" label-width="112px">
        <div class="form-grid">
          <el-form-item label="人员类型"><el-select v-model="personForm.personType" style="width:100%"><el-option v-for="item in personTypeOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
          <el-form-item label="姓名"><el-input v-model="personForm.personName" /></el-form-item>
          <el-form-item label="职务"><el-input v-model="personForm.title" /></el-form-item>
          <el-form-item label="部门"><el-input v-model="personForm.department" /></el-form-item>
          <el-form-item label="联系电话"><el-input v-model="personForm.phone" /></el-form-item>
          <el-form-item label="电子邮箱"><el-input v-model="personForm.email" /></el-form-item>
          <el-form-item label="任职开始"><el-date-picker v-model="personForm.startDate" value-format="YYYY-MM-DD" type="date" style="width:100%" /></el-form-item>
          <el-form-item label="主要负责人"><el-switch v-model="personForm.primaryFlag" /></el-form-item>
        </div>
        <el-form-item label="履历摘要"><el-input v-model="personForm.financialExperience" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="personDialogVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submitPerson">保存人员</el-button></template>
    </el-dialog>

    <el-dialog v-model="shareholderDialogVisible" :title="editingShareholderId ? '编辑股东' : '新增股东'" width="620px" destroy-on-close>
      <el-form :model="shareholderForm" label-width="110px">
        <el-form-item label="股东名称"><el-input v-model="shareholderForm.shareholderName" /></el-form-item>
        <el-form-item label="股东类型"><el-segmented v-model="shareholderForm.shareholderType" :options="[{ label: '机构', value: 'ORGANIZATION' }, { label: '个人', value: 'INDIVIDUAL' }]" /></el-form-item>
        <el-form-item v-if="shareholderForm.shareholderType === 'ORGANIZATION'" label="登记编码"><el-input v-model="shareholderForm.registrationCode" /></el-form-item>
        <el-form-item label="持股比例"><el-input-number v-model="shareholderForm.ownershipPercentage" :min="0.0001" :max="100" :precision="4" controls-position="right" /><span class="input-suffix">%</span></el-form-item>
        <el-form-item label="控股股东"><el-switch v-model="shareholderForm.controllingFlag" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="shareholderDialogVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submitShareholder">保存股东</el-button></template>
    </el-dialog>

    <el-dialog v-model="reviewDialogVisible" title="机构登记审批" width="560px" destroy-on-close>
      <el-form :model="reviewForm" label-width="90px">
        <el-form-item label="审批结论"><el-radio-group v-model="reviewForm.approved"><el-radio-button :value="true">批准</el-radio-button><el-radio-button :value="false">驳回补正</el-radio-button></el-radio-group></el-form-item>
        <el-form-item label="审批意见"><el-input v-model="reviewForm.opinion" type="textarea" :rows="4" :placeholder="reviewForm.approved ? '可填写审批说明' : '驳回时必须说明补正事项'" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="reviewDialogVisible = false">取消</el-button><el-button :type="reviewForm.approved ? 'success' : 'danger'" :loading="submitting" @click="submitReview">确认{{ reviewForm.approved ? '批准' : '驳回' }}</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { organizationApi } from '@/api/modules'
import { useUserStore } from '@/stores/user'
import type {
  AmlOrganization,
  OrganizationDetail,
  OrganizationOverview,
  OrganizationPerson,
  OrganizationRegistration,
  OrganizationShareholder,
  OrganizationTreeNode,
} from '@/api/types'

const userStore = useUserStore()
const canManage = computed(() => userStore.isAdmin || userStore.hasPermission('organization:manage'))
const canReview = computed(() => userStore.isAdmin || userStore.hasPermission('organization:review'))

const overview = reactive<OrganizationOverview>({ totalOrganizations: 0, headOffices: 0, branches: 0, outlets: 0, pendingReviews: 0, rejectedRegistrations: 0, amlOfficers: 0 })
const organizations = ref<AmlOrganization[]>([])
const organizationTree = ref<OrganizationTreeNode[]>([])
const detail = ref<OrganizationDetail | null>(null)
const total = ref(0)
const loading = ref(false)
const submitting = ref(false)
const query = reactive({ page: 1, size: 20, keyword: '', orgType: '', registrationStatus: '', treeRootId: '' })

const organizationDialogVisible = ref(false)
const detailDrawerVisible = ref(false)
const personDialogVisible = ref(false)
const shareholderDialogVisible = ref(false)
const reviewDialogVisible = ref(false)
const editingOrganizationId = ref('')
const editingPersonId = ref('')
const editingShareholderId = ref('')
const reviewingRegistrationId = ref('')
const detailTab = ref('profile')
const organizationFormRef = ref<FormInstance>()

const organizationForm = reactive({ orgCode: '', orgName: '', unifiedCreditCode: '', leiCode: '', orgType: 'HEAD_OFFICE', parentId: '', registeredAddress: '', businessAddress: '', legalRepresentative: '', registeredCapital: undefined as number | undefined, businessScope: '', regulatorName: '', status: 'DISABLED' })
const personForm = reactive({ personType: 'AML_OFFICER' as OrganizationPerson['personType'], personName: '', title: '', department: '', phone: '', email: '', startDate: '', endDate: '', financialExperience: '', primaryFlag: false, status: 'ENABLED' })
const shareholderForm = reactive({ shareholderName: '', shareholderType: 'ORGANIZATION' as OrganizationShareholder['shareholderType'], registrationCode: '', ownershipPercentage: 0.0001, controllingFlag: false, status: 'ENABLED' })
const reviewForm = reactive({ approved: true, opinion: '' })

const orgTypeOptions = [{ label: '总机构', value: 'HEAD_OFFICE' }, { label: '分支机构', value: 'BRANCH' }, { label: '营业网点', value: 'OUTLET' }]
const registrationStatusOptions = [{ label: '草稿', value: 'DRAFT' }, { label: '待审批', value: 'PENDING_REVIEW' }, { label: '已驳回', value: 'REJECTED' }, { label: '已批准', value: 'APPROVED' }]
const personTypeOptions = [{ label: '高级管理人员', value: 'SENIOR_MANAGER' }, { label: '反洗钱人员', value: 'AML_OFFICER' }, { label: '机构联络人', value: 'CONTACT' }]

const organizationRules: FormRules = {
  orgCode: [{ required: true, message: '请输入机构编码', trigger: 'blur' }],
  orgName: [{ required: true, message: '请输入机构名称', trigger: 'blur' }],
  orgType: [{ required: true, message: '请选择机构类型', trigger: 'change' }],
  unifiedCreditCode: [{ required: true, message: '请输入统一社会信用代码', trigger: 'blur' }],
  legalRepresentative: [{ required: true, message: '请输入法定代表人或负责人', trigger: 'blur' }],
  registeredAddress: [{ required: true, message: '请输入注册地址', trigger: 'blur' }],
  businessAddress: [{ required: true, message: '请输入经营地址', trigger: 'blur' }],
}

const canCreateRegistration = computed(() => detail.value != null && !detail.value.registrations.some(item => ['DRAFT', 'PENDING_REVIEW', 'REJECTED'].includes(item.status)))

onMounted(() => loadAll())

async function loadAll() {
  await Promise.all([loadOverview(), loadTree(), loadOrganizations()])
}

async function loadOverview() {
  const res: any = await organizationApi.getOverview()
  Object.assign(overview, res.data || {})
}

async function loadTree() {
  const res: any = await organizationApi.getTree()
  organizationTree.value = res.data || []
}

async function loadOrganizations() {
  loading.value = true
  try {
    const res: any = await organizationApi.getOrganizations({ page: query.page, size: query.size, keyword: query.keyword || undefined, orgType: query.orgType || undefined, registrationStatus: query.registrationStatus || undefined, treeRootId: query.treeRootId || undefined })
    organizations.value = res.data?.list || []
    total.value = Number(res.data?.total || 0)
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  Object.assign(query, { page: 1, keyword: '', orgType: '', registrationStatus: '', treeRootId: '' })
  loadOrganizations()
}

function filterByTree(node: OrganizationTreeNode) {
  query.treeRootId = node.id
  query.page = 1
  loadOrganizations()
}

function handlePageChange(page: number) { query.page = page; loadOrganizations() }
function handleSizeChange(size: number) { query.size = size; query.page = 1; loadOrganizations() }

function resetOrganizationForm() {
  Object.assign(organizationForm, { orgCode: '', orgName: '', unifiedCreditCode: '', leiCode: '', orgType: 'HEAD_OFFICE', parentId: '', registeredAddress: '', businessAddress: '', legalRepresentative: '', registeredCapital: undefined, businessScope: '', regulatorName: '', status: 'DISABLED' })
}

function openOrganizationCreate() { editingOrganizationId.value = ''; resetOrganizationForm(); organizationDialogVisible.value = true }
function openOrganizationEdit(row: AmlOrganization) {
  editingOrganizationId.value = row.id
  Object.assign(organizationForm, { ...row, parentId: row.parentId || '', registeredCapital: row.registeredCapital == null ? undefined : Number(row.registeredCapital) })
  organizationDialogVisible.value = true
}
function handleOrgTypeChange(value: string) { if (value === 'HEAD_OFFICE') organizationForm.parentId = '' }

async function submitOrganization() {
  await organizationFormRef.value?.validate()
  if (organizationForm.orgType !== 'HEAD_OFFICE' && !organizationForm.parentId) { ElMessage.warning('请选择上级机构'); return }
  submitting.value = true
  try {
    const payload = { ...organizationForm, parentId: organizationForm.parentId || undefined }
    if (editingOrganizationId.value) await organizationApi.updateOrganization(editingOrganizationId.value, payload)
    else await organizationApi.createOrganization(payload)
    ElMessage.success(editingOrganizationId.value ? '机构档案已更新' : '机构档案已创建')
    organizationDialogVisible.value = false
    await loadAll()
  } finally { submitting.value = false }
}

async function openDetail(row: AmlOrganization) {
  detailDrawerVisible.value = true
  detailTab.value = 'profile'
  await refreshDetail(row.id)
}
async function refreshDetail(id = detail.value?.organization.id) {
  if (!id) return
  const res: any = await organizationApi.getDetail(id)
  detail.value = res.data
}

function resetPersonForm() { Object.assign(personForm, { personType: 'AML_OFFICER', personName: '', title: '', department: '', phone: '', email: '', startDate: '', endDate: '', financialExperience: '', primaryFlag: false, status: 'ENABLED' }) }
function openPersonCreate() { editingPersonId.value = ''; resetPersonForm(); personDialogVisible.value = true }
function openPersonEdit(row: OrganizationPerson) { editingPersonId.value = row.id; Object.assign(personForm, { ...row, startDate: row.startDate || '', endDate: row.endDate || '' }); personDialogVisible.value = true }
async function submitPerson() {
  if (!detail.value || !personForm.personName.trim()) { ElMessage.warning('请输入人员姓名'); return }
  submitting.value = true
  try {
    const payload = { ...personForm, startDate: personForm.startDate || undefined, endDate: personForm.endDate || undefined }
    if (editingPersonId.value) await organizationApi.updatePerson(editingPersonId.value, payload)
    else await organizationApi.createPerson(detail.value.organization.id, payload)
    ElMessage.success('治理人员已保存'); personDialogVisible.value = false; await refreshAfterDetailChange()
  } finally { submitting.value = false }
}

function resetShareholderForm() { Object.assign(shareholderForm, { shareholderName: '', shareholderType: 'ORGANIZATION', registrationCode: '', ownershipPercentage: 0.0001, controllingFlag: false, status: 'ENABLED' }) }
function openShareholderCreate() { editingShareholderId.value = ''; resetShareholderForm(); shareholderDialogVisible.value = true }
function openShareholderEdit(row: OrganizationShareholder) { editingShareholderId.value = row.id; Object.assign(shareholderForm, row); shareholderDialogVisible.value = true }
async function submitShareholder() {
  if (!detail.value || !shareholderForm.shareholderName.trim()) { ElMessage.warning('请输入股东名称'); return }
  submitting.value = true
  try {
    if (editingShareholderId.value) await organizationApi.updateShareholder(editingShareholderId.value, { ...shareholderForm })
    else await organizationApi.createShareholder(detail.value.organization.id, { ...shareholderForm })
    ElMessage.success('股东信息已保存'); shareholderDialogVisible.value = false; await refreshAfterDetailChange()
  } finally { submitting.value = false }
}

async function createRegistration() {
  if (!detail.value) return
  await ElMessageBox.confirm('创建申请即表示确认当前机构资料真实、完整。创建后仍需点击“提交”进入审批。', '新建登记申请', { confirmButtonText: '确认创建', cancelButtonText: '取消', type: 'warning' })
  await organizationApi.createRegistration(detail.value.organization.id)
  ElMessage.success('登记申请草稿已创建')
  await refreshAfterDetailChange()
}

async function submitRegistration(row: OrganizationRegistration) {
  await ElMessageBox.confirm('提交后机构资料将锁定，直至审批完成。确认提交当前完整档案？', '提交登记审批', { confirmButtonText: '提交审批', cancelButtonText: '取消', type: 'warning' })
  await organizationApi.submitRegistration(row.id)
  ElMessage.success('登记申请已提交')
  await refreshAfterDetailChange()
}

function openReview(row: OrganizationRegistration) { reviewingRegistrationId.value = row.id; Object.assign(reviewForm, { approved: true, opinion: '' }); reviewDialogVisible.value = true }
async function submitReview() {
  if (!reviewForm.approved && !reviewForm.opinion.trim()) { ElMessage.warning('驳回时必须填写补正意见'); return }
  submitting.value = true
  try {
    await organizationApi.reviewRegistration(reviewingRegistrationId.value, reviewForm.approved, reviewForm.opinion || undefined)
    ElMessage.success(reviewForm.approved ? '登记申请已批准' : '登记申请已驳回补正')
    reviewDialogVisible.value = false
    await refreshAfterDetailChange()
  } finally { submitting.value = false }
}

async function refreshAfterDetailChange() { await Promise.all([refreshDetail(), loadOverview(), loadTree(), loadOrganizations()]) }
function optionLabel(options: { label: string; value: string }[], value?: string) { return options.find(item => item.value === value)?.label || value || '-' }
function shortOrgType(type: string) { return type === 'HEAD_OFFICE' ? '总' : type === 'BRANCH' ? '分' : '网' }
function registrationTagType(status: string) { if (status === 'APPROVED') return 'success'; if (status === 'REJECTED') return 'danger'; if (status === 'PENDING_REVIEW') return 'warning'; return 'info' }
function timelineType(action: string) { if (action === 'APPROVE') return 'success'; if (action === 'REJECT') return 'danger'; if (action === 'SUBMIT') return 'warning'; return 'primary' }
function actionLabel(action: string) { return ({ CREATE: '创建申请', SUBMIT: '提交审批', APPROVE: '审批通过', REJECT: '驳回补正' } as Record<string, string>)[action] || action }
</script>

<style scoped>
.organization-page { display: flex; flex-direction: column; gap: 16px; min-width: 0; }
.page-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.page-heading h1 { margin: 0; color: var(--text-primary); font-size: 24px; line-height: 1.35; letter-spacing: 0; }
.page-heading p { margin: 6px 0 0; color: var(--text-secondary); font-size: 14px; }
.metric-grid { display: grid; grid-template-columns: repeat(7, minmax(110px, 1fr)); border: 1px solid var(--border-color); border-radius: 6px; background: var(--bg-card); overflow: hidden; }
.metric-item { min-width: 0; padding: 16px 18px; border-right: 1px solid var(--border-color); border-top: 3px solid #3b82f6; }
.metric-item:last-child { border-right: 0; }
.metric-item span { display: block; color: var(--text-secondary); font-size: 13px; white-space: nowrap; }
.metric-item strong { display: block; margin-top: 6px; color: var(--text-primary); font-size: 26px; line-height: 1; }
.metric-item.warning { border-top-color: #d97706; }.metric-item.danger { border-top-color: #dc2626; }.metric-item.success { border-top-color: #16a34a; }
.workspace { display: grid; grid-template-columns: 260px minmax(0, 1fr); min-height: 620px; border: 1px solid var(--border-color); border-radius: 6px; background: var(--bg-card); overflow: hidden; }
.tree-panel { min-width: 0; padding: 16px 12px; border-right: 1px solid var(--border-color); background: var(--bg-card); }
.panel-heading { display: flex; align-items: center; justify-content: space-between; margin: 0 4px 14px; }
.panel-heading h2 { margin: 0; color: var(--text-primary); font-size: 16px; letter-spacing: 0; }.panel-heading span { color: var(--text-secondary); font-size: 12px; }
.tree-panel :deep(.el-tree) { background: transparent; color: var(--text-primary); }.tree-panel :deep(.el-tree-node__content) { height: 38px; border-radius: 4px; }
.tree-node { display: flex; align-items: center; min-width: 0; gap: 8px; }.node-type { display: inline-grid; place-items: center; flex: 0 0 24px; height: 24px; border-radius: 4px; color: #1d4ed8; background: #dbeafe; font-size: 12px; font-weight: 700; }.node-label { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.list-panel { min-width: 0; padding: 16px; }.toolbar { display: grid; grid-template-columns: minmax(240px, 1fr) 140px 140px auto auto; gap: 10px; margin-bottom: 14px; }
.table-actions { display: flex; align-items: center; gap: 4px; white-space: nowrap; }.pagination-row { display: flex; justify-content: flex-end; margin-top: 16px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); column-gap: 18px; }.drawer-heading { display: flex; align-items: center; justify-content: space-between; width: 100%; padding-right: 24px; }.drawer-heading h2 { margin: 0; color: var(--text-primary); font-size: 20px; letter-spacing: 0; }.drawer-heading span { display: block; margin-top: 4px; color: var(--text-secondary); font-size: 13px; }
.drawer-alert { margin-bottom: 12px; }.tab-command { display: flex; justify-content: flex-end; min-height: 32px; margin-bottom: 12px; }.subsection-title { margin: 24px 0 16px; color: var(--text-primary); font-size: 16px; letter-spacing: 0; }.review-timeline strong { display: block; color: var(--text-primary); }.review-timeline span { display: block; margin-top: 4px; color: var(--text-secondary); }.review-timeline p { margin: 6px 0 0; color: var(--text-primary); }.input-suffix { margin-left: 8px; color: var(--text-secondary); }
:deep(.organization-form-dialog .el-dialog__body) { max-height: calc(90vh - 132px); overflow-y: auto; }
@media (max-width: 1280px) { .metric-grid { grid-template-columns: repeat(4, 1fr); }.metric-item:nth-child(4) { border-right: 0; }.metric-item:nth-child(n+5) { border-top-width: 1px; } }
@media (max-width: 960px) { .workspace { grid-template-columns: 1fr; }.tree-panel { border-right: 0; border-bottom: 1px solid var(--border-color); }.toolbar { grid-template-columns: 1fr 1fr; }.metric-grid { grid-template-columns: repeat(2, 1fr); }.form-grid { grid-template-columns: 1fr; } }
@media (max-width: 640px) { .page-heading { align-items: stretch; flex-direction: column; }.toolbar { grid-template-columns: 1fr; }.metric-grid { grid-template-columns: 1fr 1fr; } }
</style>
