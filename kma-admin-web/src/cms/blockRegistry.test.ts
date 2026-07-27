import { describe, expect, it } from 'vitest'
import { cmsBlockTypes } from '../app/runtimeConfig'
import { cmsBlockRegistry } from './blockRegistry'

describe('CMS block registry', () => {
  it('provides exactly one controlled renderer for every allowed block type', () => {
    expect(Object.keys(cmsBlockRegistry).sort()).toEqual([...cmsBlockTypes].sort())
    for (const type of cmsBlockTypes) expect(cmsBlockRegistry[type]).toBeTruthy()
  })
})
