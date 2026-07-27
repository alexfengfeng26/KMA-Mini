import { computed, ref, watch, type Ref } from 'vue'
import type { PageResult } from '../api/page'

export function usePagedQuery<T>(
  loader: (page: number, pageSize: number, signal: AbortSignal) => Promise<PageResult<T>>,
  dependencies: Ref<unknown>[],
  initialPageSize = 20,
) {
  const page = ref(1)
  const pageSize = ref(initialPageSize)
  const total = ref(0)
  const rows = ref<T[]>([])
  const loading = ref(false)
  const error = ref('')
  let controller: AbortController | undefined

  async function load(reset = false) {
    if (reset) page.value = 1
    controller?.abort()
    const requestController = new AbortController()
    controller = requestController
    loading.value = true
    error.value = ''
    try {
      const result = await loader(page.value, pageSize.value, requestController.signal)
      if (requestController.signal.aborted) return
      rows.value = result.list
      total.value = result.total
    } catch (cause: unknown) {
      if ((cause as Error).name !== 'AbortError') {
        error.value = cause instanceof Error ? cause.message : '列表加载失败'
      }
    } finally {
      if (!requestController.signal.aborted) loading.value = false
    }
  }

  watch(dependencies, () => void load(true))

  return {
    page,
    pageSize,
    total,
    rows,
    loading,
    error,
    hasRows: computed(() => rows.value.length > 0),
    load,
    cancel: () => controller?.abort(),
  }
}
