import { describe, expect, it } from 'vitest'
import type { LowCodePage } from '../siteConfig'
import { duplicateNode, moveNode, moveNodeAt, removeNode } from './designerTree'

function page(): LowCodePage {
  return {
    slug: 'library',
    title: '资料中心',
    kind: 'library',
    root: {
      id: 'library-root',
      type: 'container',
      locked: true,
      children: [
        {
          id: 'library-core',
          type: 'component',
          component: 'content-results',
          locked: true,
        },
        { id: 'banner', type: 'component', component: 'image-banner' },
      ],
    },
  }
}

describe('low-code designer tree guards', () => {
  it('protects required widgets and supports ordinary node commands', () => {
    const value = page()
    expect(removeNode(value, 'library-core')).toBe(false)
    expect(moveNode(value.root, 'banner', -1)).toBe(true)
    const copy = duplicateNode(value.root, 'banner')
    expect(copy?.id).not.toBe('banner')
    expect(removeNode(value, 'banner')).toBe(true)
  })

  it('moves ordinary nodes across containers at an exact position', () => {
    const value = page()
    value.root.children.push({
      id: 'target-grid',
      type: 'grid',
      children: [
        { id: 'first-card', type: 'component', component: 'announcement' },
        { id: 'last-card', type: 'component', component: 'rich-text' },
      ],
    })

    expect(moveNodeAt(value, 'banner', 'last-card', 'before')).toEqual({ moved: true })
    const grid = value.root.children.find((node) => node.id === 'target-grid')
    expect(grid && 'children' in grid ? grid.children.map((node) => node.id) : []).toEqual([
      'first-card',
      'banner',
      'last-card',
    ])
  })

  it('rejects required, cyclic and over-depth moves without mutating the page', () => {
    const value = page()
    value.root.children.push({
      id: 'outer',
      type: 'container',
      children: [{ id: 'inner', type: 'container', children: [] }],
    })
    const before = structuredClone(value)

    expect(moveNodeAt(value, 'library-core', 'inner', 'inside').moved).toBe(false)
    expect(moveNodeAt(value, 'outer', 'inner', 'inside').moved).toBe(false)
    expect(value).toEqual(before)
  })
})
