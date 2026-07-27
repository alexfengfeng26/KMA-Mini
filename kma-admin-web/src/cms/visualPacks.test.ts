import { describe, expect, it } from 'vitest'
import { visualPack, visualPacks } from './visualPacks'

describe('portal visual packs', () => {
  it('keeps three scenario packs distinct and has a safe default', () => {
    expect(new Set(visualPacks.map((pack) => pack.shellLayout)).size).toBe(3)
    expect(visualPack('help-product').scenario).toBe('product-help')
    expect(visualPack('unknown').id).toBe('party-authority')
  })
})
