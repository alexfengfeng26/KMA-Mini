import { describe, expect, it } from 'vitest'
import { categoryLabel, validityMeta } from './partyKnowledge'

describe('party knowledge dictionaries', () => {
  it('centralizes category and validity labels with safe fallbacks', () => {
    expect(categoryLabel('policy')).toBe('政策文件')
    expect(categoryLabel('custom')).toBe('custom')
    expect(categoryLabel()).toBe('未分类')
    expect(validityMeta('effective')).toMatchObject({ label: '现行有效', type: 'success' })
    expect(validityMeta('custom')).toMatchObject({ label: '效力待确认', type: 'info' })
  })
})
