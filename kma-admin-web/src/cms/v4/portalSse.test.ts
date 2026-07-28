import { describe, expect, it } from 'vitest'
import { createPortalSseParser, type PortalAskStreamEvent } from './portalSse'

function collect() {
  const events: PortalAskStreamEvent[] = []
  const parser = createPortalSseParser((event) => events.push(event))
  return { events, parser }
}

describe('portalSse parser', () => {
  it('parses citations, message and done events in order', () => {
    const { events, parser } = collect()
    parser.push('event: citations\ndata: [{"docId":1,"docTitle":"办法"}]\n\n')
    parser.push('event: message\ndata: 你好\n\n')
    parser.push('event: message\ndata: 世界\n\n')
    parser.push('event: done\ndata: 42\n\n')
    expect(events).toEqual([
      { kind: 'citations', items: [{ docId: 1, docTitle: '办法' }] },
      { kind: 'delta', text: '你好' },
      { kind: 'delta', text: '世界' },
      { kind: 'done', sessionId: 42 },
    ])
  })

  it('handles event blocks split across chunks', () => {
    const { events, parser } = collect()
    parser.push('event: mes')
    parser.push('sage\ndata: 前半')
    parser.push('后半\n\nevent: done\nda')
    parser.push('ta: 7\n\n')
    expect(events).toEqual([
      { kind: 'delta', text: '前半后半' },
      { kind: 'done', sessionId: 7 },
    ])
  })

  it('joins multi-line data with newlines and ignores heartbeat', () => {
    const { events, parser } = collect()
    parser.push('event: heartbeat\ndata: ok\n\n')
    parser.push('event: message\ndata: 第一行\ndata: 第二行\n\n')
    expect(events).toEqual([{ kind: 'delta', text: '第一行\n第二行' }])
  })

  it('maps error events and tolerates invalid citations json', () => {
    const { events, parser } = collect()
    parser.push('event: citations\ndata: not-json\n\n')
    parser.push('event: error\ndata: AUTHORIZATION_REVOKED\n\n')
    expect(events).toEqual([
      { kind: 'citations', items: [] },
      { kind: 'error', message: 'AUTHORIZATION_REVOKED' },
    ])
  })

  it('flushes a trailing block without a blank line', () => {
    const { events, parser } = collect()
    parser.push('event: message\ndata: 尾巴')
    parser.flush()
    expect(events).toEqual([{ kind: 'delta', text: '尾巴' }])
  })
})
