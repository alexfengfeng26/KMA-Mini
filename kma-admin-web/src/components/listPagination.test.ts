import { ref } from 'vue'
import { describe, expect, it } from 'vitest'
import { readServerPage, useClientPagination } from './listPagination'

describe('list pagination helpers', () => {
  it('slices client-side rows and clamps an invalid last page', async () => {
    const rows = ref(Array.from({ length: 25 }, (_, index) => index + 1))
    const pagination = useClientPagination(rows, 10)
    pagination.page.value = 3
    expect(pagination.pagedItems.value).toEqual([21, 22, 23, 24, 25])
    rows.value = rows.value.slice(0, 7)
    await Promise.resolve()
    expect(pagination.page.value).toBe(1)
    expect(pagination.pagedItems.value).toEqual([1, 2, 3, 4, 5, 6, 7])
  })

  it('normalizes a server page envelope', () => {
    expect(readServerPage<number>({ list: [11, 12], total: 42, pageNum: 2, pageSize: 20 }, 1, 10)).toEqual({
      items: [11, 12],
      total: 42,
      page: 2,
      pageSize: 20,
    })
  })
})
