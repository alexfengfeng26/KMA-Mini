import { describe, expect, it, vi } from 'vitest'
import { authorizedFetch } from './client'
import { parseSseFrame, streamQa } from './sse'

vi.mock('./client', () => ({ authorizedFetch: vi.fn() }))

describe('parseSseFrame', () => {
  it('parses stable named events and multiline payloads', () => {
    expect(parseSseFrame('event: message\ndata: 第一行\ndata: 第二行')).toEqual({
      event: 'message',
      data: '第一行\n第二行',
    })
    expect(parseSseFrame('event: citations\ndata: [{"chunkId":1}]')).toEqual({
      event: 'citations',
      data: '[{"chunkId":1}]',
    })
  })

  it('ignores comments and unknown events', () => {
    expect(parseSseFrame(': heartbeat')).toBeNull()
    expect(parseSseFrame('event: custom\ndata: value')).toBeNull()
  })
})

describe('streamQa', () => {
  it('handles split frames and the final unseparated tail', async () => {
    const encoder = new TextEncoder()
    vi.mocked(authorizedFetch).mockResolvedValue(
      new Response(
        new ReadableStream({
          start(controller) {
            controller.enqueue(encoder.encode('event: message\ndata: 第一'))
            controller.enqueue(encoder.encode('段\n\nevent: heartbeat\ndata: ok\n\nevent: done\ndata: 9'))
            controller.close()
          },
        }),
        { status: 200 },
      ),
    )
    const handler = vi.fn()

    await streamQa({ query: 'test' }, handler, new AbortController().signal)

    expect(handler).toHaveBeenNthCalledWith(1, 'message', '第一段')
    expect(handler).toHaveBeenNthCalledWith(2, 'heartbeat', 'ok')
    expect(handler).toHaveBeenNthCalledWith(3, 'done', '9')
  })

  it('rejects non-stream responses', async () => {
    vi.mocked(authorizedFetch).mockResolvedValue(new Response(null, { status: 503 }))
    await expect(streamQa({}, vi.fn(), new AbortController().signal)).rejects.toThrow('503')
  })
})
