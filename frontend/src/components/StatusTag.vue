<script setup lang="ts">
/**
 * 统一状态标签组件
 * 内置告警/案件/风险/报告/评估等业务域的状态映射，自动选择 Tag 颜色。
 */
import { computed } from 'vue'
import { ElTag } from 'element-plus'

type TagType = 'primary' | 'success' | 'warning' | 'danger' | 'info'

interface StatusItem {
  label: string
  type: TagType
}

interface Props {
  /** 状态值，如 'PENDING', 'HIGH' 等 */
  status: string
  /**
   * 映射类型:
   * alert          - 告警状态
   * alert-risk     - 告警风险等级
   * alert-process  - 告警处理结果
   * case           - 案件状态
   * case-priority  - 案件优先级
   * risk           - 风险等级(通用)
   * report         - 报告状态
   * assessment     - 评估状态
   * 也可传入自定义映射对象
   */
  type: string | Record<string, StatusItem>
  size?: '' | 'large' | 'default' | 'small'
}

const props = withDefaults(defineProps<Props>(), {
  size: 'small',
})

// ==================== 内置映射表 ====================
const STATUS_MAPS: Record<string, Record<string, StatusItem>> = {
  // 告警状态
  alert: {
    NEW:           { label: '新建',   type: 'danger' },
    PENDING:       { label: '待处理', type: 'warning' },
    ASSIGNED:      { label: '已分配', type: 'warning' },
    INVESTIGATING: { label: '调查中', type: 'primary' },
    PROCESSING:    { label: '处理中', type: 'primary' },
    RESOLVED:      { label: '已解决', type: 'success' },
    CONFIRMED:     { label: '已确认', type: 'success' },
    EXCLUDED:      { label: '已排除', type: 'info' },
    CLOSED:        { label: '已关闭', type: 'info' },
  },
  // 告警风险等级
  'alert-risk': {
    LOW:      { label: '低',   type: 'success' },
    MEDIUM:   { label: '中',   type: 'warning' },
    HIGH:     { label: '高',   type: 'danger' },
    CRITICAL: { label: '极高', type: 'danger' },
  },
  // 告警处理结果
  'alert-process': {
    CONFIRMED: { label: '确认可疑', type: 'danger' },
    EXCLUDED:  { label: '排除误报', type: 'success' },
  },
  // 案件状态
  case: {
    OPEN:          { label: '已创建', type: 'info' },
    INVESTIGATING: { label: '调查中', type: 'primary' },
    SUBMITTED:     { label: '已上报', type: 'warning' },
    CLOSED:        { label: '已关闭', type: 'success' },
  },
  // 案件优先级
  'case-priority': {
    HIGH:   { label: '高', type: 'danger' },
    MEDIUM: { label: '中', type: 'warning' },
    LOW:    { label: '低', type: 'success' },
  },
  // 通用风险等级
  risk: {
    LOW:      { label: '低',   type: 'success' },
    MEDIUM:   { label: '中',   type: 'warning' },
    HIGH:     { label: '高',   type: 'danger' },
    CRITICAL: { label: '极高', type: 'danger' },
  },
  // 报告状态
  report: {
    DRAFT:     { label: '草稿',   type: 'info' },
    PENDING:   { label: '待审核', type: 'warning' },
    APPROVED:  { label: '已批准', type: 'success' },
    REJECTED:  { label: '已驳回', type: 'danger' },
    SUBMITTED: { label: '已提交', type: 'primary' },
  },
  // 评估状态
  assessment: {
    NOT_STARTED: { label: '未开始', type: 'info' },
    IN_PROGRESS: { label: '进行中', type: 'primary' },
    COMPLETED:   { label: '已完成', type: 'success' },
    OVERDUE:     { label: '已逾期', type: 'danger' },
  },
}

const statusConfig = computed<StatusItem>(() => {
  // 支持直接传入自定义映射对象
  if (typeof props.type === 'object') {
    return props.type[props.status] || { label: props.status, type: 'info' }
  }
  const map = STATUS_MAPS[props.type]
  if (!map) {
    return { label: props.status, type: 'info' }
  }
  return map[props.status] || { label: props.status, type: 'info' }
})

const label = computed(() => statusConfig.value.label)
const tagType = computed(() => statusConfig.value.type)
</script>

<template>
  <ElTag :type="tagType" :size="size">{{ label }}</ElTag>
</template>
