import { computed, ref, watch, type Ref } from 'vue'

export function useClientPagination<T>(source: Ref<T[]>, initialPageSize = 10) {
  const page = ref(1)
  const pageSize = ref(initialPageSize)
  const total = computed(() => source.value.length)
  const pagedItems = computed(() => {
    const start = (page.value - 1) * pageSize.value
    return source.value.slice(start, start + pageSize.value)
  })

  watch([total, pageSize], ([currentTotal, currentSize]) => {
    const lastPage = Math.max(1, Math.ceil(currentTotal / currentSize))
    if (page.value > lastPage) page.value = lastPage
  })

  function resetPage() {
    page.value = 1
  }

  return { page, pageSize, total, pagedItems, resetPage }
}

export function readServerPage<T>(value: unknown, fallbackPage: number, fallbackPageSize: number) {
  const record = value && typeof value === 'object' ? (value as Record<string, unknown>) : {}
  const items = Array.isArray(record.list) ? (record.list as T[]) : []
  const numberValue = (candidate: unknown, fallback: number) => {
    const parsed = Number(candidate)
    return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback
  }
  return {
    items,
    total: numberValue(record.total, items.length),
    page: numberValue(record.pageNum, fallbackPage) || fallbackPage,
    pageSize: numberValue(record.pageSize, fallbackPageSize) || fallbackPageSize,
  }
}
