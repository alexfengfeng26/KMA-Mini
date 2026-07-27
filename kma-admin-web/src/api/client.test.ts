import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { bindBrowserSession } from '../security/browserSession'
import { asList, asRecord, authorizedFetch, authorizedJson, errorMessage, pageItems, unwrap } from './client'

describe('authorizedFetch', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('adds the current bearer token to protected direct downloads', async () => {
    sessionStorage.setItem('kma_access_token', 'access-token')
    const fetchMock = vi.spyOn(window, 'fetch').mockResolvedValue(new Response('content', { status: 200 }))

    await authorizedFetch('/api/v1/portal/contents/7/source')

    const request = fetchMock.mock.calls[0][0] as Request
    expect(request.headers.get('Authorization')).toBe('Bearer access-token')
  })

  it('keeps trace ids visible in actionable errors', () => {
    const failure = Object.assign(new Error('保存失败'), { traceId: 'trace-42' })
    expect(errorMessage(failure, '请求失败')).toContain('trace-42')
  })

  it('unwraps successful envelopes and rejects business errors', async () => {
    const response = new Response(null, { status: 200 })
    await expect(unwrap(Promise.resolve({ response, data: { code: 0, data: { id: 3 } } }))).resolves.toEqual({
      id: 3,
    })
    await expect(unwrap(Promise.resolve({ response, data: { code: 409, message: '冲突' } }))).rejects.toThrow(
      '冲突',
    )
  })

  it('parses authorized JSON and keeps collection boundaries safe', async () => {
    vi.spyOn(window, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ code: 0, data: { list: [{ id: 1 }] } }), {
        status: 200,
        headers: { 'content-type': 'application/json' },
      }),
    )
    await expect(authorizedJson<{ list: Array<{ id: number }> }>('/api/items')).resolves.toEqual({
      list: [{ id: 1 }],
    })
    expect(asRecord(null)).toEqual({})
    expect(asList('not-a-list')).toEqual([])
    expect(pageItems({ list: [1, 2] })).toEqual([1, 2])
  })

  it('rotates a stale access token once and retries the request', async () => {
    sessionStorage.setItem('kma_access_token', 'stale-token')
    const fetchMock = vi
      .spyOn(window, 'fetch')
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ message: 'AUTHORIZATION_STALE' }), {
          status: 401,
          headers: { 'content-type': 'application/json' },
        }),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ code: 0, data: { accessToken: 'fresh-token' } }), {
          status: 200,
          headers: { 'content-type': 'application/json' },
        }),
      )
      .mockResolvedValueOnce(new Response('ok', { status: 200 }))

    const response = await authorizedFetch('/api/protected')

    expect(response.status).toBe(200)
    expect(fetchMock).toHaveBeenCalledTimes(3)
    const retried = fetchMock.mock.calls[2][0] as Request
    expect(retried.headers.get('Authorization')).toBe('Bearer fresh-token')
  })

  it('binds refresh rotation to the current tab user', async () => {
    sessionStorage.setItem('kma_access_token', 'stale-token')
    bindBrowserSession({ userId: 17, username: 'reader' })
    const fetchMock = vi
      .spyOn(window, 'fetch')
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ message: 'AUTHORIZATION_STALE' }), {
          status: 401,
          headers: { 'content-type': 'application/json' },
        }),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            code: 0,
            data: {
              accessToken: 'fresh-token',
              userId: 17,
              username: 'reader',
            },
          }),
          { status: 200, headers: { 'content-type': 'application/json' } },
        ),
      )
      .mockResolvedValueOnce(new Response('ok', { status: 200 }))

    await authorizedFetch('/api/protected')

    const refreshInit = fetchMock.mock.calls[1][1] as RequestInit
    const headers = new Headers(refreshInit.headers)
    expect(headers.has('X-KMA-Expected-User')).toBe(false)
  })
})
