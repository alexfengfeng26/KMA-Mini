import { describe, expect, it } from 'vitest'
import type { LowCodePage } from '../siteConfig'
import { duplicateNode, moveNode, removeNode } from './designerTree'

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
})
