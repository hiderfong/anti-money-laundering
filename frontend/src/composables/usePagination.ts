/**
 * 分页状态管理 composable
 * 封装 page/size/total/loading/tableData 以及通用的数据加载流程。
 */
import { ref, reactive, type Ref } from 'vue'

export interface PaginationOptions<T = any> {
  /**
   * 加载数据函数
   * @param params 包含 { page, size, ...searchParams }
   * @returns { list: T[], total: number }
   */
  fetchFn: (params: Record<string, any>) => Promise<{ list: T[]; total: number }>
  /** 初始每页条数 */
  defaultSize?: number
  /** 额外固定查询参数 */
  extraParams?: Record<string, any>
  /** 是否在 onMounted 时自动加载 */
  autoLoad?: boolean
}

export interface UsePaginationReturn<T = any> {
  page: Ref<number>
  size: Ref<number>
  total: Ref<number>
  loading: Ref<boolean>
  tableData: Ref<T[]>
  /** 加载数据(会自动拼接 page/size/extraParams) */
  loadData: (searchParams?: Record<string, any>) => Promise<void>
  /** 重置到第一页并加载 */
  refresh: (searchParams?: Record<string, any>) => Promise<void>
  /** 改变页码 */
  handlePageChange: (newPage: number) => Promise<void>
  /** 改变每页条数 */
  handleSizeChange: (newSize: number) => Promise<void>
  /** 当前搜索参数(缓存，翻页时自动带上) */
  searchParams: Record<string, any>
}

export function usePagination<T = any>(options: PaginationOptions<T>): UsePaginationReturn<T> {
  const { fetchFn, defaultSize = 10, extraParams = {} } = options

  const page = ref(1)
  const size = ref(defaultSize)
  const total = ref(0)
  const loading = ref(false)
  const tableData = ref<T[]>([]) as Ref<T[]>
  const searchParams: Record<string, any> = reactive({})

  const loadData = async (params?: Record<string, any>) => {
    // 更新搜索参数缓存
    if (params) {
      // 清除旧参数
      Object.keys(searchParams).forEach((k) => delete searchParams[k])
      Object.assign(searchParams, params)
    }

    loading.value = true
    try {
      const queryParams = {
        page: page.value,
        size: size.value,
        ...extraParams,
        ...searchParams,
      }
      const result = await fetchFn(queryParams)
      tableData.value = result.list || []
      total.value = result.total || 0
    } catch (e) {
      console.error('[usePagination] loadData error:', e)
      tableData.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  }

  const refresh = async (params?: Record<string, any>) => {
    page.value = 1
    await loadData(params)
  }

  const handlePageChange = async (newPage: number) => {
    page.value = newPage
    await loadData()
  }

  const handleSizeChange = async (newSize: number) => {
    size.value = newSize
    page.value = 1
    await loadData()
  }

  return {
    page,
    size,
    total,
    loading,
    tableData,
    loadData,
    refresh,
    handlePageChange,
    handleSizeChange,
    searchParams,
  }
}
