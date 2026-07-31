/** Portal 问答 SSE 事件，桥接层转发给主题沙箱。 */
export type PortalAskStreamEvent =
  | { kind: 'citations'; items: unknown[] }
  | { kind: 'delta'; text: string }
  | { kind: 'done'; sessionId?: number }
  | { kind: 'error'; message: string }

interface RawSseEvent {
  name: string
  data: string
}

function mapEvent(raw: RawSseEvent): PortalAskStreamEvent | undefined {
  if (raw.name === 'citations') {
    try {
      const parsed = JSON.parse(raw.data)
      return { kind: 'citations', items: Array.isArray(parsed) ? parsed : [] }
    } catch {
      return { kind: 'citations', items: [] }
    }
  }
  if (raw.name === 'message') {
    if (!raw.data || raw.data.trim() === '' || raw.data.trim().toLowerCase() === 'null') return undefined
    return { kind: 'delta', text: raw.data }
  }
  if (raw.name === 'done') {
    const sessionId = Number(raw.data)
    return { kind: 'done', sessionId: Number.isSafeInteger(sessionId) ? sessionId : undefined }
  }
  if (raw.name === 'error') return { kind: 'error', message: raw.data || '流式问答失败' }
  // heartbeat 与未知事件忽略
  return undefined
}

/**
 * 增量 SSE 解析器：按块喂入文本，每解析出一个有效事件就回调。
 * 兼容事件块跨 chunk、单个事件多行 data（按 SSE 规范以 \n 连接）。
 */
export function createPortalSseParser(onEvent: (event: PortalAskStreamEvent) => void) {
  let buffer = ''

  const drainBlock = (block: string) => {
    if (!block.trim()) return
    let name = 'message'
    const dataLines: string[] = []
    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) name = line.slice(6).trim()
      else if (line.startsWith('data:')) dataLines.push(line.slice(5).replace(/^ /, ''))
    }
    if (!dataLines.length) return
    const mapped = mapEvent({ name, data: dataLines.join('\n') })
    if (mapped) onEvent(mapped)
  }

  return {
    push(chunk: string) {
      buffer += chunk
      let index = buffer.indexOf('\n\n')
      while (index >= 0) {
        drainBlock(buffer.slice(0, index))
        buffer = buffer.slice(index + 2)
        index = buffer.indexOf('\n\n')
      }
    },
    /** 流结束时冲刷残留缓冲（部分服务端末尾不带空行）。 */
    flush() {
      drainBlock(buffer)
      buffer = ''
    },
  }
}
