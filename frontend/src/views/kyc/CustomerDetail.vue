<template>
  <div class="customer-detail" v-loading="loading">
    <!-- 页面头部：客户名称 + 风险等级 -->
    <el-page-header @back="$router.push('/kyc')" style="margin-bottom: 20px">
      <template #content>
        <div class="header-content">
          <span class="customer-name">{{ customer.name || '客户详情' }}</span>
          <el-tag
            v-if="customer.riskLevel"
            :type="riskTagType"
            size="large"
            style="margin-left: 12px"
          >
            {{ riskLevelText }}
          </el-tag>
        </div>
      </template>
    </el-page-header>

    <!-- 4个标签页 -->
    <el-tabs v-model="activeTab">
      <!-- Tab 1: 基本信息 -->
      <el-tab-pane label="基本信息" name="basic">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="客户编号">{{ customer.customerNo || customer.id || '-' }}</el-descriptions-item>
          <el-descriptions-item label="姓名/名称">{{ customer.name || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户类型">{{ customerTypeText }}</el-descriptions-item>
          <el-descriptions-item label="证件类型">{{ idTypeText }}</el-descriptions-item>
          <el-descriptions-item label="证件号码">{{ customer.idNumber || '-' }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ customer.phone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ customer.email || '-' }}</el-descriptions-item>
          <el-descriptions-item label="国籍">{{ customer.nationality || '-' }}</el-descriptions-item>
          <el-descriptions-item label="风险等级">
            <el-tag :type="riskTagType">{{ riskLevelText }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="风险评分">{{ customer.riskScore ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="KYC状态">{{ kycStatusText }}</el-descriptions-item>
          <el-descriptions-item label="客户状态">{{ statusText }}</el-descriptions-item>
          <el-descriptions-item label="地址">{{ customer.address || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ customer.createdTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ customer.updatedTime || '-' }}</el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>

      <!-- Tab 2: 受益所有人 -->
      <el-tab-pane label="受益所有人" name="owners">
        <el-table :data="beneficialOwners" border stripe empty-text="暂无受益所有人数据">
          <el-table-column type="index" label="序号" width="60" />
          <el-table-column prop="name" label="姓名" min-width="120" />
          <el-table-column prop="idNumber" label="证件号码" min-width="160" />
          <el-table-column prop="relationship" label="关系" min-width="100" />
        </el-table>
      </el-tab-pane>

      <!-- Tab 3: 认证记录 -->
      <el-tab-pane label="认证记录" name="verification">
        <el-table :data="verificationRecords" border stripe empty-text="暂无认证记录">
          <el-table-column type="index" label="序号" width="60" />
          <el-table-column prop="verificationType" label="认证类型" min-width="120">
            <template #default="{ row }">
              {{ verificationTypeText(row.verificationType) }}
            </template>
          </el-table-column>
          <el-table-column prop="verificationResult" label="认证结果" min-width="100">
            <template #default="{ row }">
              <el-tag :type="row.verificationResult === 'PASS' ? 'success' : 'danger'" size="small">
                {{ row.verificationResult === 'PASS' ? '通过' : '未通过' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="verifiedBy" label="认证人" min-width="100" />
          <el-table-column prop="verifiedTime" label="认证时间" min-width="170" />
        </el-table>
      </el-tab-pane>

      <!-- Tab 4: 风险评估 -->
      <el-tab-pane label="风险评估" name="risk">
        <!-- 当前风险状态卡片 -->
        <el-card shadow="never" style="margin-bottom: 20px">
          <template #header>
            <div class="risk-card-header">
              <span>当前风险状态</span>
              <el-button
                type="primary"
                :loading="assessing"
                @click="triggerRiskAssessment"
              >
                触发重新评估
              </el-button>
            </div>
          </template>
          <el-row :gutter="20">
            <el-col :span="8">
              <div class="risk-stat">
                <div class="risk-stat-label">风险等级</div>
                <el-tag :type="riskTagType" size="large">{{ riskLevelText }}</el-tag>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="risk-stat">
                <div class="risk-stat-label">风险评分</div>
                <div class="risk-stat-value">{{ customer.riskScore ?? '-' }}</div>
              </div>
            </el-col>
          </el-row>
        </el-card>

        <!-- 历史变更记录 -->
        <el-divider content-position="left">历史变更记录</el-divider>
        <el-table :data="riskRatingLogs" border stripe empty-text="暂无风险评级变更记录">
          <el-table-column type="index" label="序号" width="60" />
          <el-table-column prop="oldLevel" label="原等级" min-width="100">
            <template #default="{ row }">
              <el-tag v-if="row.oldLevel" :type="levelTagType(row.oldLevel)" size="small">
                {{ levelText(row.oldLevel) }}
              </el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="newLevel" label="新等级" min-width="100">
            <template #default="{ row }">
              <el-tag :type="levelTagType(row.newLevel)" size="small">
                {{ levelText(row.newLevel) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="score" label="评分" min-width="80" />
          <el-table-column prop="reason" label="变更原因" min-width="200" show-overflow-tooltip />
          <el-table-column prop="createdTime" label="变更时间" min-width="170" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const route = useRoute()
const customerId = computed(() => route.params.id as string)

const activeTab = ref('basic')
const loading = ref(false)
const assessing = ref(false)

// Data
const customer = ref<any>({})
const beneficialOwners = ref<any[]>([])
const verificationRecords = ref<any[]>([])
const riskRatingLogs = ref<any[]>([])

// Computed
const riskTagType = computed(() => {
  const map: Record<string, string> = { LOW: 'success', MEDIUM: 'warning', HIGH: 'danger', CRITICAL: 'danger' }
  return (map[customer.value.riskLevel] || 'info') as any
})

const riskLevelText = computed(() => {
  const map: Record<string, string> = { LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险', CRITICAL: '极高风险' }
  return map[customer.value.riskLevel] || customer.value.riskLevel || '-'
})

const customerTypeText = computed(() => {
  const map: Record<string, string> = { INDIVIDUAL: '个人', CORPORATE: '法人' }
  return map[customer.value.customerType] || customer.value.customerType || '-'
})

const idTypeText = computed(() => {
  const map: Record<string, string> = {
    ID_CARD: '身份证',
    PASSPORT: '护照',
    BUSINESS_LICENSE: '营业执照',
    OTHER: '其他'
  }
  return map[customer.value.idType] || customer.value.idType || '-'
})

const kycStatusText = computed(() => {
  const map: Record<string, string> = {
    NOT_STARTED: '未开始',
    IN_PROGRESS: '进行中',
    COMPLETED: '已完成',
    EXPIRED: '已过期'
  }
  return map[customer.value.kycStatus] || customer.value.kycStatus || '-'
})

const statusText = computed(() => {
  const map: Record<string, string> = { ACTIVE: '正常', INACTIVE: '停用', SUSPENDED: '冻结' }
  return map[customer.value.status] || customer.value.status || '-'
})

// Helpers
function levelTagType(level: string): string {
  const map: Record<string, string> = { LOW: 'success', MEDIUM: 'warning', HIGH: 'danger', CRITICAL: 'danger' }
  return (map[level] || 'info') as any
}

function levelText(level: string): string {
  const map: Record<string, string> = { LOW: '低', MEDIUM: '中', HIGH: '高', CRITICAL: '极高' }
  return map[level] || level || '-'
}

function verificationTypeText(type: string): string {
  const map: Record<string, string> = {
    ID_CARD: '身份证验证',
    FACE: '人脸识别',
    BANK_CARD: '银行卡验证',
    PHONE: '手机号验证',
    ADDRESS: '地址验证'
  }
  return map[type] || type || '-'
}

// API calls
async function fetchCustomerDetail() {
  loading.value = true
  try {
    // 并行请求基本信息和360视图
    const [detailRes, view360Res]: any[] = await Promise.all([
      request.get(`/kyc/customers/${customerId.value}`),
      request.get(`/kyc/customers/${customerId.value}/360`)
    ])

    // 基本信息
    customer.value = detailRes.data || view360Res.data?.customer || {}

    // 360视图数据
    const data = view360Res.data || {}
    // 如果360返回了customer且比detail更完整，用360的覆盖
    if (data.customer && Object.keys(data.customer).length > Object.keys(customer.value).length) {
      customer.value = data.customer
    }
    beneficialOwners.value = data.beneficialOwners || []
    verificationRecords.value = data.verificationRecords || []
    riskRatingLogs.value = data.riskRatingLogs || []
  } catch (e: any) {
    ElMessage.error('获取客户信息失败：' + (e.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

async function triggerRiskAssessment() {
  assessing.value = true
  try {
    await request.post(`/kyc/customers/${customerId.value}/risk-assessment`)
    ElMessage.success('风险评估已触发，请稍后刷新查看结果')
    // 刷新数据
    await fetchCustomerDetail()
  } catch (e: any) {
    ElMessage.error('触发风险评估失败：' + (e.message || '未知错误'))
  } finally {
    assessing.value = false
  }
}

onMounted(() => {
  fetchCustomerDetail()
})
</script>

<style scoped>
.customer-detail {
  padding: 20px;
}

.header-content {
  display: flex;
  align-items: center;
}

.customer-name {
  font-size: 18px;
  font-weight: 600;
}

.risk-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.risk-stat {
  text-align: center;
  padding: 16px 0;
}

.risk-stat-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}

.risk-stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
}
</style>
