<script setup lang="ts">
/**
 * 封装分页表格组件
 * 整合 el-table + el-pagination，统一布局和事件处理。
 * 通过 slots 支持自定义列渲染和顶部操作区。
 */
import { ElTable, ElTableColumn, ElPagination, ElCard } from 'element-plus'

export interface ColumnConfig {
  /** 对应字段 prop */
  prop: string
  /** 列标题 */
  label: string
  /** 列宽 */
  width?: number | string
  /** 最小列宽 */
  minWidth?: number | string
  /** 固定列: true / 'left' / 'right' */
  fixed?: boolean | 'left' | 'right'
  /** 对齐方式 */
  align?: 'left' | 'center' | 'right'
  /** 是否显示溢出提示 */
  showOverflowTooltip?: boolean
  /** 是否可排序 */
  sortable?: boolean | 'custom'
  /** 自定义 slot 名(对应父组件的 slot: #col-xxx) */
  slot?: string
}

interface Props {
  /** 表格数据 */
  data: any[]
  /** 列配置 */
  columns: ColumnConfig[]
  /** 是否加载中 */
  loading?: boolean
  /** 总条数 */
  total?: number
  /** 当前页码 */
  page?: number
  /** 每页条数 */
  size?: number
  /** 可选的每页条数列表 */
  pageSizes?: number[]
  /** 是否显示斑马纹 */
  stripe?: boolean
  /** 是否显示边框 */
  border?: boolean
  /** 是否显示分页 */
  showPagination?: boolean
  /** 表格最大高度 */
  maxHeight?: string | number
  /** 行 key 字段 */
  rowKey?: string
  /** 是否显示选择列 */
  showSelection?: boolean
  /** 分页布局 */
  paginationLayout?: string
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  total: 0,
  page: 1,
  size: 10,
  pageSizes: () => [10, 20, 50],
  stripe: true,
  border: true,
  showPagination: true,
  rowKey: 'id',
  showSelection: false,
  paginationLayout: 'total, sizes, prev, pager, next',
})

const emit = defineEmits<{
  (e: 'update:page', val: number): void
  (e: 'update:size', val: number): void
  (e: 'page-change', page: number): void
  (e: 'size-change', size: number): void
  (e: 'selection-change', rows: any[]): void
}>()

const handleCurrentChange = (page: number) => {
  emit('update:page', page)
  emit('page-change', page)
}

const handleSizeChange = (size: number) => {
  emit('update:size', size)
  emit('size-change', size)
}

const handleSelectionChange = (rows: any[]) => {
  emit('selection-change', rows)
}
</script>

<template>
  <ElCard shadow="never" class="page-table-card">
    <!-- 顶部操作区 slot -->
    <template v-if="$slots.header" #header>
      <slot name="header" />
    </template>

    <ElTable
      :data="data"
      v-loading="loading"
      :stripe="stripe"
      :border="border"
      :max-height="maxHeight"
      :row-key="rowKey"
      @selection-change="handleSelectionChange"
    >
      <!-- 选择列 -->
      <ElTableColumn v-if="showSelection" type="selection" width="50" align="center" />

      <!-- 动态列 -->
      <ElTableColumn
        v-for="col in columns"
        :key="col.prop"
        :prop="col.prop"
        :label="col.label"
        :width="col.width"
        :min-width="col.minWidth"
        :fixed="col.fixed"
        :align="col.align"
        :show-overflow-tooltip="col.showOverflowTooltip"
        :sortable="col.sortable"
      >
        <!-- 自定义列内容：优先使用 slot，否则显示默认文本 -->
        <template v-if="col.slot" #default="scope">
          <slot :name="col.slot" v-bind="scope" />
        </template>
      </ElTableColumn>

      <!-- 兜底：操作列等完全自定义列 -->
      <slot />
    </ElTable>

    <!-- 分页 -->
    <div v-if="showPagination" class="pagination-wrapper">
      <ElPagination
        :current-page="page"
        :page-size="size"
        :total="total"
        :page-sizes="pageSizes"
        :layout="paginationLayout"
        @current-change="handleCurrentChange"
        @size-change="handleSizeChange"
      />
    </div>
  </ElCard>
</template>

<style scoped>
.page-table-card {
  border-radius: 8px;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
