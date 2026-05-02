<template>
  <div>
    <el-page-header @back="$router.push('/kyc')" content="客户详情" style="margin-bottom: 20px" />
    <el-tabs v-model="activeTab">
      <el-tab-pane label="基本信息" name="basic">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="客户编号">{{ customer.customerNo }}</el-descriptions-item>
          <el-descriptions-item label="姓名/名称">{{ customer.name }}</el-descriptions-item>
          <el-descriptions-item label="客户类型">{{ customer.customerType === 'INDIVIDUAL' ? '个人' : '法人' }}</el-descriptions-item>
          <el-descriptions-item label="风险等级">
            <el-tag :type="{ LOW:'success', MEDIUM:'warning', HIGH:'danger' }[customer.riskLevel] as any">
              {{ { LOW:'低', MEDIUM:'中', HIGH:'高' }[customer.riskLevel as string] || '-' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="KYC状态">{{ customer.kycStatus }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ customer.status }}</el-descriptions-item>
          <el-descriptions-item label="国籍">{{ customer.nationality }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ customer.phone }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ customer.email }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ customer.createdTime }}</el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>
      <el-tab-pane label="受益所有人" name="owners">
        <el-table :data="beneficialOwners" border>
          <el-table-column prop="ownerName" label="姓名" />
          <el-table-column prop="ownershipPercentage" label="持股比例" />
          <el-table-column prop="controlType" label="控制方式" />
          <el-table-column prop="relationship" label="关系" />
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="风险评级历史" name="risk">
        <el-table :data="riskHistory" border>
          <el-table-column prop="oldRiskLevel" label="原等级" />
          <el-table-column prop="newRiskLevel" label="新等级" />
          <el-table-column prop="changeReason" label="变更原因" />
          <el-table-column prop="changedTime" label="变更时间" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import request from '@/utils/request'

const route = useRoute()
const activeTab = ref('basic')
const customer = ref<any>({})
const beneficialOwners = ref<any[]>([])
const riskHistory = ref<any[]>([])

onMounted(async () => {
  const id = route.params.id
  try {
    const res: any = await request.get(`/kyc/customers/${id}`)
    customer.value = res.data || {}
  } catch (e) { /* handled */ }
  try {
    const res: any = await request.get(`/kyc/customers/${id}/360`)
    beneficialOwners.value = res.data?.beneficialOwners || []
    riskHistory.value = res.data?.riskRatingHistory || []
  } catch (e) { /* handled */ }
})
</script>
