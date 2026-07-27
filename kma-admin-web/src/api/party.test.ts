import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
  authorizedJson: vi.fn(),
}))

vi.mock('./client', async () => {
  const actual = await vi.importActual<typeof import('./client')>('./client')
  return {
    ...actual,
    api: {
      GET: mocks.get,
      POST: mocks.post,
      PUT: mocks.put,
      DELETE: mocks.delete,
    },
    unwrap: async (value: unknown) => value,
    authorizedJson: mocks.authorizedJson,
  }
})

import {
  addPortalFavorite,
  applyContentAction,
  createFileContent,
  createTextContent,
  createTopic,
  getAdminContent,
  getAdminContents,
  getAdminTopics,
  getPortalConfig,
  getPortalContent,
  getPortalContents,
  getPortalFavorites,
  getPortalHistory,
  getPortalHome,
  getPortalTopics,
  removePortalFavorite,
  updateContentMetadata,
  updatePortalConfig,
  updateTopic,
} from './party'

describe('party knowledge API adapter', () => {
  beforeEach(() => {
    Object.values(mocks).forEach((mock) => mock.mockReset())
  })

  it('normalizes portal home and page data from compatible snake/camel responses', async () => {
    mocks.authorizedJson.mockResolvedValueOnce({
      portalData: {
        config: { unit_name: '示范党校', help_text: '权威资料', current_topic_code: 'topic-1' },
        categories: [{ content_type: 'policy', name: '政策文件', total: '3' }],
        recent: [{ contentId: 9, title: '文件' }],
        topics: [
          {
            topic_id: 2,
            topic_code: 'topic-1',
            name: '专题',
            description: '说明',
            cover_color: '#fff',
            sort_order: 10,
            enabled: 1,
            featured: true,
          },
        ],
        history: [{ doc_id: 9, title: '历史', read_count: 2 }],
        favorites: [{ favorite_id: 7, title: '收藏', favorite_type: 'document' }],
      },
    })
    const home = await getPortalHome()
    expect(home.config.unitName).toBe('示范党校')
    expect(home.categories[0].total).toBe(3)
    expect(home.topics[0]).toMatchObject({ topicId: 2, topicCode: 'topic-1', enabled: true })
    expect(home.favorites[0].favoriteId).toBe(7)

    mocks.authorizedJson.mockResolvedValueOnce({
      list: [{ contentId: 10, title: '分页文件' }],
      total: 31,
      page_num: 2,
      page_size: 10,
    })
    await expect(getPortalContents({ pageNum: 2, pageSize: 10 })).resolves.toMatchObject({
      total: 31,
      pageNum: 2,
      pageSize: 10,
    })
  })

  it('covers portal and governance command wrappers', async () => {
    mocks.get.mockResolvedValue([])
    mocks.post.mockResolvedValue({ id: 1 })
    mocks.put.mockResolvedValue({})
    mocks.delete.mockResolvedValue({})
    mocks.authorizedJson.mockResolvedValue(undefined)

    await getPortalContent(1, 'p3')
    await getPortalTopics()
    await getAdminTopics()
    await getPortalFavorites(8)
    await getPortalHistory(9)
    await addPortalFavorite({ favoriteType: 'document', targetId: 1 })
    await removePortalFavorite(2)
    mocks.get.mockResolvedValueOnce({ list: [], total: 0 })
    await getAdminContents({ pageNum: 1, pageSize: 20 })
    await getAdminContent(3)

    const request = {
      title: '标题',
      spaceCode: 'default',
      content: '正文',
      externalRef: 'ref-1',
      sourceVersion: 1,
      contentType: 'policy',
      publishDate: '2026-07-23',
    }
    await createTextContent(request)
    const file = new File(['content'], 'document.txt', { type: 'text/plain' })
    await createFileContent(
      {
        title: '文件',
        spaceCode: 'default',
        externalRef: 'ref-2',
        sourceVersion: 1,
        contentType: 'policy',
        publishDate: '2026-07-23',
      },
      file,
    )
    const fileOptions = mocks.post.mock.calls.at(-1)?.[1] as {
      bodySerializer: () => FormData
    }
    expect(fileOptions.bodySerializer().get('file')).toBe(file)
    await updateContentMetadata(3, request)
    await applyContentAction(3, 'approve', '同意')
    expect(mocks.authorizedJson).toHaveBeenCalledWith(
      '/api/v1/admin/contents/3/approve',
      expect.objectContaining({ body: JSON.stringify({ note: '同意' }) }),
    )
    await applyContentAction(3, 'publish')

    const topic = { topicCode: 'topic', name: '专题' }
    await createTopic(topic)
    await updateTopic(2, topic)
    mocks.get.mockResolvedValueOnce({
      unit_name: '单位',
      help_text: '帮助',
      current_topic_code: 'topic',
    })
    await expect(getPortalConfig()).resolves.toMatchObject({ unitName: '单位' })
    await updatePortalConfig({ unitName: '新单位' })
  })
})
