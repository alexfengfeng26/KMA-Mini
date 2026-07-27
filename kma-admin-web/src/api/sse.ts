import { authorizedFetch } from './client'

export type QaStreamEvent = 'citations' | 'message' | 'heartbeat' | 'done' | 'error'
export type QaStreamHandler = (event: QaStreamEvent, data: string) => void

export function parseSseFrame(frame: string): { event: QaStreamEvent; data: string } | null {
  let event = 'message'
  const data: string[] = []
  for (const line of frame.replace(/\r/g, '').split('\n')) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''))
  }
  if (!data.length || !['citations', 'message', 'heartbeat', 'done', 'error'].includes(event)) return null
  return { event: event as QaStreamEvent, data: data.join('\n') }
}

export async function streamQa(
  body: Record<string, unknown>,
  handler: QaStreamHandler,
  signal: AbortSignal,
  endpoint = '/api/v1/qa/stream',
) {
  const response = await authorizedFetch(endpoint, {
    method: 'POST',
    headers: { Accept: 'text/event-stream', 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    signal,
  })
  if (!response.ok || !response.body) throw new Error(`流式问答连接失败 (${response.status})`)

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    buffer += decoder.decode(value, { stream: !done }).replace(/\r\n/g, '\n')
    let boundary = buffer.indexOf('\n\n')
    while (boundary >= 0) {
      const parsed = parseSseFrame(buffer.slice(0, boundary))
      buffer = buffer.slice(boundary + 2)
      if (parsed) handler(parsed.event, parsed.data)
      boundary = buffer.indexOf('\n\n')
    }
    if (done) break
  }
  const tail = parseSseFrame(buffer)
  if (tail) handler(tail.event, tail.data)
}
