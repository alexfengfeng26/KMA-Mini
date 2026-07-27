export type KmaMarkupBlock =
  | { type: 'paragraph'; text: string }
  | { type: 'callout'; tone: 'info' | 'success' | 'warning'; text: string }
  | { type: 'steps'; items: string[] }
  | { type: 'faq'; items: Array<{ question: string; answer: string }> }

const tones = new Set(['info', 'success', 'warning'])

/** Parses a deliberately small Markdown-directive subset. It never returns HTML or executable attributes. */
export function parseKmaMarkup(source: string): KmaMarkupBlock[] {
  const blocks: KmaMarkupBlock[] = []
  const lines = source.replace(/\r/g, '').split('\n')
  let cursor = 0
  while (cursor < lines.length) {
    const line = lines[cursor].trim()
    const callout = line.match(/^:::callout(?:\{tone="(info|success|warning)"\})?$/)
    if (callout) {
      const content = consume(lines, cursor + 1)
      blocks.push({
        type: 'callout',
        tone: tones.has(callout[1] || '') ? (callout[1] as 'info') : 'info',
        text: content.text,
      })
      cursor = content.next
      continue
    }
    if (line === ':::steps') {
      const content = consume(lines, cursor + 1)
      blocks.push({
        type: 'steps',
        items: content.text
          .split('\n')
          .map((item) => item.replace(/^[-*]\s*/, '').trim())
          .filter(Boolean),
      })
      cursor = content.next
      continue
    }
    if (line === ':::faq') {
      const content = consume(lines, cursor + 1)
      blocks.push({
        type: 'faq',
        items: content.text
          .split('\n')
          .map((item) => item.split('::', 2))
          .filter(([question, answer]) => Boolean(question && answer))
          .map(([question, answer]) => ({ question: question.trim(), answer: answer.trim() })),
      })
      cursor = content.next
      continue
    }
    if (line) blocks.push({ type: 'paragraph', text: line })
    cursor += 1
  }
  return blocks
}

function consume(lines: string[], start: number) {
  const values: string[] = []
  let cursor = start
  while (cursor < lines.length && lines[cursor].trim() !== ':::') {
    values.push(lines[cursor])
    cursor += 1
  }
  return { text: values.join('\n').trim(), next: cursor < lines.length ? cursor + 1 : cursor }
}

export function inlineKmaTags(text: string) {
  const segments: Array<{
    type: 'text' | 'badge' | 'download'
    value: string
    tone?: string
    asset?: string
  }> = []
  const matcher = /:(badge|download)\[([^\]]{1,120})\]\{([^}]*)\}/g
  let cursor = 0
  for (const match of text.matchAll(matcher)) {
    const index = match.index || 0
    if (index > cursor) segments.push({ type: 'text', value: text.slice(cursor, index) })
    const attributes = Object.fromEntries(
      [...match[3].matchAll(/(tone|asset)="([^"<>]{1,160})"/g)].map((item) => [item[1], item[2]]),
    )
    segments.push({
      type: match[1] as 'badge' | 'download',
      value: match[2],
      tone: attributes.tone,
      asset: attributes.asset,
    })
    cursor = index + match[0].length
  }
  if (cursor < text.length) segments.push({ type: 'text', value: text.slice(cursor) })
  return segments.length ? segments : [{ type: 'text' as const, value: text }]
}
