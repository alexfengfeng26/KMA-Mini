import { ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { usePagedQuery } from './usePagedQuery'

describe('usePagedQuery', () => {
  it('prevents an older response from replacing a newer filtered result', async () => {
    const dependency = ref('first')
    const resolvers: Array<
      (value: { list: string[]; total: number; pageNum: number; pageSize: number }) => void
    > = []
    const loader = vi.fn(
      () =>
        new Promise<{ list: string[]; total: number; pageNum: number; pageSize: number }>((resolve) =>
          resolvers.push(resolve),
        ),
    )
    const query = usePagedQuery(loader, [dependency])

    const first = query.load()
    const second = query.load()
    resolvers[1]({ list: ['new'], total: 1, pageNum: 1, pageSize: 20 })
    await second
    resolvers[0]({ list: ['old'], total: 1, pageNum: 1, pageSize: 20 })
    await first

    expect(query.rows.value).toEqual(['new'])
    expect(query.loading.value).toBe(false)
  })
})
