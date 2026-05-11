/**
 * 搜索表单逻辑 composable
 * 封装搜索表单的初始化、搜索、重置逻辑，配合 usePagination 使用。
 */
import { reactive, type UnwrapNestedRefs } from 'vue'

export interface UseTableSearchOptions<T extends Record<string, any>> {
  /** 搜索表单初始值 */
  defaultValues: T
  /**
   * 搜索回调，接收当前表单值
   * 通常传入 usePagination 返回的 refresh 或 loadData
   */
  onSearch: (formValues: T) => void | Promise<void>
  /** 额外的重置后回调 */
  onReset?: () => void
}

export interface UseTableSearchReturn<T extends Record<string, any>> {
  /** 搜索表单(响应式) */
  searchForm: UnwrapNestedRefs<T>
  /** 执行搜索(会先将页码重置为1) */
  handleSearch: () => void | Promise<void>
  /** 重置搜索表单并搜索 */
  resetSearch: () => void | Promise<void>
}

export function useTableSearch<T extends Record<string, any>>(
  options: UseTableSearchOptions<T>
): UseTableSearchReturn<T> {
  const { defaultValues, onSearch, onReset } = options

  // 创建响应式表单副本
  const searchForm = reactive({ ...defaultValues }) as UnwrapNestedRefs<T>

  const handleSearch = () => {
    onSearch({ ...searchForm } as T)
  }

  const resetSearch = () => {
    // 恢复默认值
    Object.keys(defaultValues).forEach((key) => {
      ;(searchForm as any)[key] = (defaultValues as any)[key]
    })
    handleSearch()
    onReset?.()
  }

  return {
    searchForm,
    handleSearch,
    resetSearch,
  }
}
