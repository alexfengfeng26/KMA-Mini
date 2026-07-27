import { afterEach, describe, expect, it, vi } from 'vitest'
import { openAuthorizedFile } from './download'
import { authorizedFetch } from './client'

vi.mock('./client', () => ({ authorizedFetch: vi.fn() }))

describe('openAuthorizedFile', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('opens an authenticated blob and keeps the server filename', async () => {
    vi.mocked(authorizedFetch).mockResolvedValue(
      new Response(new Blob(['document']), {
        status: 200,
        headers: { 'content-disposition': "attachment; filename*=UTF-8''%E5%85%9A%E7%AB%A0.pdf" },
      }),
    )
    const createObjectURL = vi.fn(() => 'blob:kma-document')
    const revokeObjectURL = vi.fn()
    vi.stubGlobal('URL', { ...URL, createObjectURL, revokeObjectURL })
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)

    await openAuthorizedFile('/api/v1/portal/contents/9/source')

    expect(authorizedFetch).toHaveBeenCalledWith(
      '/api/v1/portal/contents/9/source',
      expect.objectContaining({ headers: { Accept: '*/*' } }),
    )
    expect(createObjectURL).toHaveBeenCalled()
    const clickedAnchor = click.mock.contexts[0] as HTMLAnchorElement
    expect(clickedAnchor?.href).toBe('blob:kma-document')
    expect(clickedAnchor?.target).toBe('_blank')
    expect(clickedAnchor?.rel).toBe('noopener noreferrer')
    expect(clickedAnchor?.download).toBe('党章.pdf')
  })

  it('surfaces protected file failures', async () => {
    vi.mocked(authorizedFetch).mockResolvedValue(new Response(null, { status: 401 }))
    await expect(openAuthorizedFile('/protected')).rejects.toThrow('401')
  })
})
