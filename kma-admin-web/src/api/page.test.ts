import { beforeEach, describe, expect, it, vi } from 'vitest'
import { authorizedJson } from './client'
import { getAuthorizedPage } from './page'

vi.mock('./client', () => ({ authorizedJson: vi.fn() }))

describe('getAuthorizedPage', () => {
  beforeEach(() => vi.mocked(authorizedJson).mockReset())

  it('uses the stable page contract and omits empty filters', async () => {
    vi.mocked(authorizedJson).mockResolvedValue({
      list: [{ id: 1 }],
      total: 1,
      pageNum: 2,
      pageSize: 20,
    })

    const result = await getAuthorizedPage<{ id: number }>('/api/items/page', {
      pageNum: 2,
      pageSize: 20,
      keyword: '',
      sortBy: 'createTime',
      sortOrder: 'desc',
      enabled: true,
    })

    expect(result.total).toBe(1)
    const requestUrl = String(vi.mocked(authorizedJson).mock.calls[0][0])
    expect(requestUrl).toContain('pageNum=2')
    expect(requestUrl).toContain('enabled=true')
    expect(requestUrl).not.toContain('keyword=')
  })
})
