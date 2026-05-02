<template>
  <div class="system-view">
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <!-- ==================== 用户管理 ==================== -->
      <el-tab-pane label="用户管理" name="users">
        <div class="tab-toolbar">
          <el-button type="primary" @click="openUserDialog('create')">
            <el-icon><Plus /></el-icon>新建用户
          </el-button>
        </div>

        <el-table :data="users" stripe border v-loading="loadingUsers" style="width: 100%">
          <el-table-column prop="username" label="用户名" width="120" />
          <el-table-column prop="realName" label="姓名" width="100" />
          <el-table-column prop="phone" label="手机号" width="130" />
          <el-table-column prop="email" label="邮箱" min-width="160" />
          <el-table-column prop="status" label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
                {{ row.status === 'ACTIVE' ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="角色" min-width="140">
            <template #default="{ row }">
              <el-tag v-for="r in row.roles" :key="r" size="small" class="role-tag">{{ r }}</el-tag>
              <span v-if="!row.roles || row.roles.length === 0" style="color:#999">无</span>
            </template>
          </el-table-column>
          <el-table-column prop="createdTime" label="创建时间" width="170" />
          <el-table-column label="操作" width="300" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openUserDialog('edit', row)">编辑</el-button>
              <el-button link type="warning" size="small" @click="handleResetPassword(row)">重置密码</el-button>
              <el-button link type="success" size="small" @click="openRoleDialog(row)">分配角色</el-button>
              <el-popconfirm title="确定删除该用户？" @confirm="handleDeleteUser(row.id)">
                <template #reference>
                  <el-button link type="danger" size="small">删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrap">
          <el-pagination
            v-model:current-page="userPage.pageNum"
            v-model:page-size="userPage.pageSize"
            :total="userPage.total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="fetchUsers"
            @current-change="fetchUsers"
          />
        </div>
      </el-tab-pane>

      <!-- ==================== 字典管理 ==================== -->
      <el-tab-pane label="字典管理" name="dicts">
        <div class="tab-toolbar">
          <el-button type="primary" @click="handleRefreshCache">
            <el-icon><Refresh /></el-icon>刷新缓存
          </el-button>
        </div>

        <el-table
          :data="dicts"
          stripe
          border
          v-loading="loadingDicts"
          style="width: 100%"
          row-key="id"
          @expand-change="onDictExpand"
        >
          <el-table-column type="expand">
            <template #default="{ row }">
              <div class="dict-items-wrap" v-loading="row._loadingItems">
                <el-table :data="row._items || []" border size="small" style="width: 100%">
                  <el-table-column prop="itemCode" label="字典项编码" width="160" />
                  <el-table-column prop="itemLabel" label="字典项名称" width="200" />
                  <el-table-column prop="sortOrder" label="排序" width="80" />
                  <el-table-column prop="status" label="状态" width="80">
                    <template #default="{ row: item }">
                      <el-tag :type="item.status === 'ACTIVE' ? 'success' : 'info'" size="small">
                        {{ item.status === 'ACTIVE' ? '启用' : '禁用' }}
                      </el-tag>
                    </template>
                  </el-table-column>
                </el-table>
                <el-empty v-if="row._items && row._items.length === 0" description="暂无字典项" :image-size="60" />
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="dictCode" label="字典编码" width="200" />
          <el-table-column prop="dictName" label="字典名称" width="200" />
          <el-table-column prop="description" label="描述" min-width="200" />
          <el-table-column prop="status" label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
                {{ row.status === 'ACTIVE' ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ==================== 审计日志 ==================== -->
      <el-tab-pane label="审计日志" name="audit">
        <div class="tab-toolbar">
          <el-form :inline="true" :model="auditFilter" @submit.prevent="fetchAuditLogs">
            <el-form-item label="用户名">
              <el-input v-model="auditFilter.username" placeholder="用户名" clearable style="width:130px" />
            </el-form-item>
            <el-form-item label="模块">
              <el-input v-model="auditFilter.module" placeholder="模块" clearable style="width:130px" />
            </el-form-item>
            <el-form-item label="操作类型">
              <el-select v-model="auditFilter.operationType" placeholder="全部" clearable style="width:120px">
                <el-option label="CREATE" value="CREATE" />
                <el-option label="UPDATE" value="UPDATE" />
                <el-option label="DELETE" value="DELETE" />
                <el-option label="LOGIN" value="LOGIN" />
                <el-option label="EXPORT" value="EXPORT" />
              </el-select>
            </el-form-item>
            <el-form-item label="时间范围">
              <el-date-picker
                v-model="auditFilter.dateRange"
                type="daterange"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                value-format="YYYY-MM-DD"
                style="width:260px"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="fetchAuditLogs">查询</el-button>
              <el-button @click="resetAuditFilter">重置</el-button>
              <el-button type="success" @click="handleExportAudit">
                <el-icon><Download /></el-icon>导出
              </el-button>
            </el-form-item>
          </el-form>
        </div>

        <el-table :data="auditLogs" stripe border v-loading="loadingAudit" style="width: 100%">
          <el-table-column prop="username" label="用户" width="100" />
          <el-table-column prop="module" label="模块" width="100" />
          <el-table-column prop="operationType" label="操作类型" width="100">
            <template #default="{ row }">
              <el-tag :type="opTypeTag(row.operationType)" size="small">{{ row.operationType }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
          <el-table-column prop="ip" label="IP地址" width="130" />
          <el-table-column prop="requestUrl" label="请求地址" min-width="180" show-overflow-tooltip />
          <el-table-column prop="createdTime" label="时间" width="170" />
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openAuditDetail(row.id)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrap">
          <el-pagination
            v-model:current-page="auditPage.pageNum"
            v-model:page-size="auditPage.pageSize"
            :total="auditPage.total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="fetchAuditLogs"
            @current-change="fetchAuditLogs"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- ==================== 用户 新建/编辑 弹窗 ==================== -->
    <el-dialog
      v-model="userDialogVisible"
      :title="userDialogTitle"
      width="520px"
      destroy-on-close
      @closed="resetUserForm"
    >
      <el-form ref="userFormRef" :model="userForm" :rules="userRules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="userForm.username" :disabled="userMode === 'edit'" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="userForm.realName" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="userForm.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="userForm.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="userForm.status">
            <el-radio value="ACTIVE">启用</el-radio>
            <el-radio value="INACTIVE">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitUser">确定</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 分配角色弹窗 ==================== -->
    <el-dialog v-model="roleDialogVisible" title="分配角色" width="460px" destroy-on-close>
      <el-checkbox-group v-model="selectedRoles">
        <el-checkbox v-for="r in allRoles" :key="r" :value="r" :label="r" />
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitRoles">确定</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 审计日志详情弹窗 ==================== -->
    <el-dialog v-model="auditDetailVisible" title="审计日志详情" width="600px" destroy-on-close>
      <el-descriptions :column="2" border v-if="auditDetail">
        <el-descriptions-item label="ID">{{ auditDetail.id }}</el-descriptions-item>
        <el-descriptions-item label="用户ID">{{ auditDetail.userId }}</el-descriptions-item>
        <el-descriptions-item label="用户名">{{ auditDetail.username }}</el-descriptions-item>
        <el-descriptions-item label="模块">{{ auditDetail.module }}</el-descriptions-item>
        <el-descriptions-item label="操作类型">{{ auditDetail.operationType }}</el-descriptions-item>
        <el-descriptions-item label="IP地址">{{ auditDetail.ip }}</el-descriptions-item>
        <el-descriptions-item label="请求地址" :span="2">{{ auditDetail.requestUrl }}</el-descriptions-item>
        <el-descriptions-item label="描述" :span="2">{{ auditDetail.description }}</el-descriptions-item>
        <el-descriptions-item label="时间" :span="2">{{ auditDetail.createdTime }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Download } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import request from '@/utils/request'

const activeTab = ref('users')

// ========== 用户管理 ==========
const loadingUsers = ref(false)
const users = ref<any[]>([])
const userPage = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const userDialogVisible = ref(false)
const userMode = ref<'create' | 'edit'>('create')
const userDialogTitle = computed(() => (userMode.value === 'create' ? '新建用户' : '编辑用户'))
const submitting = ref(false)
const userFormRef = ref<FormInstance>()
const userForm = reactive({ id: 0, username: '', realName: '', phone: '', email: '', status: 'ACTIVE' })
const userRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
}

const roleDialogVisible = ref(false)
const roleUserId = ref(0)
const selectedRoles = ref<string[]>([])
const allRoles = ref<string[]>(['ADMIN', 'AUDITOR', 'ANALYST', 'VIEWER'])

async function fetchUsers() {
  loadingUsers.value = true
  try {
    const res: any = await request.get('/system/users/page', {
      params: { pageNum: userPage.pageNum, pageSize: userPage.pageSize },
    })
    const data = res.data || res
    users.value = data.records || data.list || []
    userPage.total = data.total || 0
  } catch { /* handled */ } finally { loadingUsers.value = false }
}

function openUserDialog(mode: 'create' | 'edit', row?: any) {
  userMode.value = mode
  if (mode === 'edit' && row) {
    Object.assign(userForm, { id: row.id, username: row.username, realName: row.realName, phone: row.phone, email: row.email, status: row.status })
  } else {
    Object.assign(userForm, { id: 0, username: '', realName: '', phone: '', email: '', status: 'ACTIVE' })
  }
  userDialogVisible.value = true
}

function resetUserForm() {
  userFormRef.value?.resetFields()
}

async function submitUser() {
  const valid = await userFormRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (userMode.value === 'create') {
      await request.post('/system/users', { ...userForm })
      ElMessage.success('用户创建成功')
    } else {
      await request.put(`/system/users/${userForm.id}`, { ...userForm })
      ElMessage.success('用户更新成功')
    }
    userDialogVisible.value = false
    fetchUsers()
  } catch { /* handled */ } finally { submitting.value = false }
}

async function handleDeleteUser(id: number) {
  try {
    await request.delete(`/system/users/${id}`)
    ElMessage.success('删除成功')
    fetchUsers()
  } catch { /* handled */ }
}

async function handleResetPassword(row: any) {
  try {
    await ElMessageBox.confirm(`确定重置用户「${row.username}」的密码？`, '重置密码', { type: 'warning' })
    await request.post(`/system/users/${row.id}/reset-password`)
    ElMessage.success('密码已重置')
  } catch { /* handled */ }
}

function openRoleDialog(row: any) {
  roleUserId.value = row.id
  selectedRoles.value = [...(row.roles || [])]
  roleDialogVisible.value = true
}

async function submitRoles() {
  submitting.value = true
  try {
    await request.post(`/system/users/${roleUserId.value}/roles`, { roles: selectedRoles.value })
    ElMessage.success('角色分配成功')
    roleDialogVisible.value = false
    fetchUsers()
  } catch { /* handled */ } finally { submitting.value = false }
}

// ========== 字典管理 ==========
const loadingDicts = ref(false)
const dicts = ref<any[]>([])

async function fetchDicts() {
  loadingDicts.value = true
  try {
    const res: any = await request.get('/system/dicts')
    dicts.value = (res.data || res || []).map((d: any) => ({ ...d, _items: undefined, _loadingItems: false }))
  } catch { /* handled */ } finally { loadingDicts.value = false }
}

async function onDictExpand(row: any, expanded: boolean[]) {
  if (!expanded || !row._items) {
    row._loadingItems = true
    try {
      const res: any = await request.get(`/system/dicts/${row.dictCode}/items`)
      row._items = res.data || res || []
    } catch { row._items = [] } finally { row._loadingItems = false }
  }
}

async function handleRefreshCache() {
  try {
    await request.post('/system/dicts/refresh-cache')
    ElMessage.success('缓存刷新成功')
  } catch { /* handled */ }
}

// ========== 审计日志 ==========
const loadingAudit = ref(false)
const auditLogs = ref<any[]>([])
const auditPage = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const auditFilter = reactive({ username: '', module: '', operationType: '', dateRange: null as string[] | null })

const auditDetailVisible = ref(false)
const auditDetail = ref<any>(null)

function auditStartTime() {
  return auditFilter.dateRange?.[0] ? `${auditFilter.dateRange[0]}T00:00:00` : undefined
}

function auditEndTime() {
  return auditFilter.dateRange?.[1] ? `${auditFilter.dateRange[1]}T23:59:59` : undefined
}

async function fetchAuditLogs() {
  loadingAudit.value = true
  try {
    const params: any = {
      pageNum: auditPage.pageNum,
      pageSize: auditPage.pageSize,
      username: auditFilter.username || undefined,
      module: auditFilter.module || undefined,
      operationType: auditFilter.operationType || undefined,
      startTime: auditStartTime(),
      endTime: auditEndTime(),
    }
    const res: any = await request.get('/system/audit-logs/page', { params })
    const data = res.data || res
    auditLogs.value = data.records || data.list || []
    auditPage.total = data.total || 0
  } catch { /* handled */ } finally { loadingAudit.value = false }
}

function resetAuditFilter() {
  Object.assign(auditFilter, { username: '', module: '', operationType: '', dateRange: null })
  auditPage.pageNum = 1
  fetchAuditLogs()
}

async function openAuditDetail(id: number) {
  try {
    const res: any = await request.get(`/system/audit-logs/${id}`)
    auditDetail.value = res.data || res
    auditDetailVisible.value = true
  } catch { /* handled */ }
}

async function handleExportAudit() {
  try {
    const res: any = await request.get('/system/audit-logs/export', {
      params: {
        username: auditFilter.username || undefined,
        module: auditFilter.module || undefined,
        operationType: auditFilter.operationType || undefined,
        startTime: auditStartTime(),
        endTime: auditEndTime(),
      },
      responseType: 'blob',
    })
    const blob = new Blob([res.data || res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `audit_logs_${Date.now()}.xlsx`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch { /* handled */ }
}

function opTypeTag(type: string) {
  const map: Record<string, string> = { CREATE: 'success', UPDATE: '', DELETE: 'danger', LOGIN: 'warning', EXPORT: 'info' }
  return (map[type] ?? '') as any
}

// ========== Tab 切换懒加载 ==========
function onTabChange(tab: string) {
  if (tab === 'users' && users.value.length === 0) fetchUsers()
  if (tab === 'dicts' && dicts.value.length === 0) fetchDicts()
  if (tab === 'audit' && auditLogs.value.length === 0) fetchAuditLogs()
}

onMounted(() => {
  fetchUsers()
})
</script>

<style scoped>
.system-view {
  padding: 16px;
}
.tab-toolbar {
  margin-bottom: 16px;
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.role-tag {
  margin-right: 4px;
  margin-bottom: 2px;
}
.dict-items-wrap {
  padding: 12px 24px;
}
</style>
