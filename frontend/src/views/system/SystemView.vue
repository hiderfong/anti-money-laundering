<template>
  <div>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="数据字典" name="dicts">
        <el-table :data="dicts" stripe v-loading="loading" border>
          <el-table-column prop="dictCode" label="字典编码" width="200" />
          <el-table-column prop="dictName" label="字典名称" width="200" />
          <el-table-column prop="description" label="描述" />
          <el-table-column prop="status" label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="审计日志" name="audit">
        <el-table :data="auditLogs" stripe v-loading="loadingAudit" border>
          <el-table-column prop="username" label="用户" width="100" />
          <el-table-column prop="operationType" label="操作" width="100" />
          <el-table-column prop="module" label="模块" width="100" />
          <el-table-column prop="targetId" label="对象ID" width="120" />
          <el-table-column prop="ipAddress" label="IP" width="120" />
          <el-table-column prop="createdTime" label="时间" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '@/utils/request'

const activeTab = ref('dicts')
const loading = ref(false)
const loadingAudit = ref(false)
const dicts = ref<any[]>([])
const auditLogs = ref<any[]>([])

onMounted(async () => {
  loading.value = true
  try {
    const res: any = await request.get('/system/dicts')
    dicts.value = res.data || []
  } catch (e) { /* handled */ } finally { loading.value = false }
})
</script>
