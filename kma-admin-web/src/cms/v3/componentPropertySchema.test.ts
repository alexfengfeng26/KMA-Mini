import { describe, expect, it } from 'vitest'
import { componentPropertyFields } from './componentPropertySchema'

describe('component property schema', () => {
  it('exposes typed fields for common portal components', () => {
    expect(componentPropertyFields('hero-search')).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ key: 'placeholder', type: 'text' }),
        expect.objectContaining({ key: 'showAsk', type: 'boolean' }),
      ]),
    )
    expect(componentPropertyFields('recent-documents')).toContainEqual(
      expect.objectContaining({ key: 'limit', type: 'number', max: 50 }),
    )
  })

  it('falls back to safe text properties for registered components', () => {
    expect(componentPropertyFields('feedback').map((field) => field.key)).toEqual(['title', 'description'])
  })
})
