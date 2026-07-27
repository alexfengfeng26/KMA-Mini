import { describe, expect, it } from 'vitest'
import { inlineKmaTags, parseKmaMarkup } from './kmaMarkup'

describe('KMA safe markup directives', () => {
  it('parses only registered callout, steps and faq structures', () => {
    const blocks = parseKmaMarkup(
      `:::callout{tone="warning"}\n请核对现行制度。\n:::\n:::steps\n- 查阅文件\n- 留存记录\n:::\n:::faq\n如何搜索::输入关键词\n:::`,
    )
    expect(blocks).toEqual([
      { type: 'callout', tone: 'warning', text: '请核对现行制度。' },
      { type: 'steps', items: ['查阅文件', '留存记录'] },
      { type: 'faq', items: [{ question: '如何搜索', answer: '输入关键词' }] },
    ])
  })

  it('keeps inline tags as data instead of generating HTML', () => {
    expect(inlineKmaTags(':badge[现行有效]{tone="success"}<script>alert(1)</script>')).toEqual([
      { type: 'badge', value: '现行有效', tone: 'success', asset: undefined },
      { type: 'text', value: '<script>alert(1)</script>' },
    ])
  })
})
