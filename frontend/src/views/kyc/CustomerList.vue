<template>
  <div>
    <!-- 搜索表单 -->
    <el-card style="margin-bottom: 16px">
      <el-form :inline="true" :model="query">
        <el-form-item label="客户名称"><el-input v-model="query.name" placeholder="请输入" clearable /></el-form-item>
        <el-form-item label="客户类型">
          <el-select v-model="query.customerType" clearable placeholder="全部">
            <el-option label="个人" value="INDIVIDUAL" /><el-option label="法人" value="CORPORATE" />
          </el-select>
        </el-form-item>
        <el-form-item label="风险等级">
          <el-select v-model="query.riskLevel" clearable placeholder="全部">
            <el-option label="低" value="LOW" /><el-option label="中" value="MEDIUM" /><el-option label="高" value="HIGH" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 客户列表 -->
    <el-card>
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>客户列表</span>
          <el-button type="primary" @click="showCreate = true">新增客户</el-button>
        </div>
      </template>
      <el-table :data="customers" stripe v-loading="loading" border>
        <el-table-column prop="customerNo" label="客户编号" width="180" />
        <el-table-column prop="name" label="姓名/名称" width="120" />
        <el-table-column prop="customerType" label="类型" width="80">
          <template #default="{ row }">{{ row.customerType === 'INDIVIDUAL' ? '个人' : '法人' }}</template>
        </el-table-column>
        <el-table-column prop="riskLevel" label="风险等级" width="100">
          <template #default="{ row }">
            <el-tag :type="riskLevelTagType(row.riskLevel)" size="small">
              {{ riskLevelLabel(row.riskLevel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="kycStatus" label="KYC状态" width="100">
          <template #default="{ row }">
            <el-tag :type="kycStatusTagType(row.kycStatus)" size="small">
              {{ kycStatusLabel(row.kycStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ row.status === 'ACTIVE' ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="$router.push(`/kyc/${row.id}`)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        layout="total, prev, pager, next, sizes"
        :page-sizes="[10, 20, 50]"
        style="margin-top: 16px; justify-content: flex-end"
        @current-change="loadData"
        @size-change="loadData"
      />
    </el-card>

    <!-- 新增客户对话框 -->
    <el-dialog v-model="showCreate" title="新增客户" width="600px">
      <el-form :model="createForm" label-width="100px">
        <el-form-item label="客户类型">
          <el-select v-model="createForm.customerType">
            <el-option label="个人" value="INDIVIDUAL" /><el-option label="法人" value="CORPORATE" />
          </el-select>
        </el-form-item>
        <el-form-item label="姓名/名称"><el-input v-model="createForm.name" /></el-form-item>
        <el-form-item label="证件类型">
          <el-select v-model="createForm.idType">
            <el-option label="身份证" value="IDCARD" /><el-option label="护照" value="PASSPORT" />
          </el-select>
        </el-form-item>
        <el-form-item label="证件号码"><el-input v-model="createForm.idNumber" /></el-form-item>
        <el-form-item label="手机号码"><el-input v-model="createForm.phone" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="createForm.email" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const loading = ref(false)
const creating = ref(false)
const showCreate = ref(false)
const customers = ref<any[]>([])
const total = ref(0)

const query = reactive({ page: 1, size: 10, name: '', customerType: '', riskLevel: '' })
const createForm = reactive({ customerType: 'INDIVIDUAL', name: '', idType: 'IDCARD', idNumber: '', phone: '', email: '' })

function mapLabel(map: Record<string, string>, value: unknown) {
  const key = typeof value === 'string' ? value : ''
  return map[key] || key || '-'
}

function riskLevelTagType(level: unknown): '' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<string, '' | 'success' | 'warning' | 'danger' | 'info'> = {
    LOW: 'success',
    MEDIUM: 'warning',
    HIGH: 'danger'
  }
  const key = typeof level === 'string' ? level : ''
  return map[key] || 'info'
}

function riskLevelLabel(level: unknown) {
  return mapLabel({ LOW: '低', MEDIUM: '中', HIGH: '高' }, level)
}

function kycStatusTagType(status: unknown): '' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<string, '' | 'success' | 'warning' | 'danger' | 'info'> = {
    COMPLETE: 'success',
    INCOMPLETE: 'warning',
    REVIEWING: 'info'
  }
  const key = typeof status === 'string' ? status : ''
  return map[key] || 'info'
}

function kycStatusLabel(status: unknown) {
  return mapLabel({ COMPLETE: '已完成', INCOMPLETE: '未完成', REVIEWING: '审核中' }, status)
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await request.get('/kyc/customers/page', { params: query })
    customers.value = res.data?.list || []
    total.value = res.data?.total || 0
  } catch (e) { /* handled by interceptor */ } finally { loading.value = false }
}

function resetQuery() {
  Object.assign(query, { name: '', customerType: '', riskLevel: '', page: 1 })
  loadData()
}

async function handleCreate() {
  creating.value = true
  try {
    await request.post('/kyc/customers', createForm)
    ElMessage.success('客户创建成功')
    showCreate.value = false
    loadData()
  } catch (e) { /* handled */ } finally { creating.value = false }
}

onMounted(loadData)
</script>
