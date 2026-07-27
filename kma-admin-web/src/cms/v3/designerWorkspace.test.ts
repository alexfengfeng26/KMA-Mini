import { describe, expect, it } from 'vitest'
import type { PortalSiteConfigV3 } from '../siteConfig'
import {
  buildDesignerStructure,
  fitCanvasZoom,
  normalizePreviewWidth,
  orderedPageSlugs,
  previewBreakpoint,
  structureSelectionKey,
  topicDirectoryColumns,
} from './designerWorkspace'

function config(): PortalSiteConfigV3 {
  return {
    schemaVersion: 3,
    revision: 'test',
    site: { siteKey: 'default', scenario: 'party', name: 'KMA Mini', locale: 'zh-CN' },
    shell: {
      header: { id: 'shared-root', type: 'section', children: [] },
      footer: { id: 'footer-root', type: 'section', children: [] },
      navigation: [
        { id: 'library', label: '资料中心', target: 'library' },
        { id: 'home', label: '首页', target: 'home' },
      ],
    },
    theme: { preset: 'emerald', mode: 'light', density: 'compact', tokens: {} },
    modules: {},
    contentScope: {
      allSpaces: true,
      spaceCodes: [],
      topicCodes: [],
      contentTypes: [],
      validityStatuses: [],
    },
    search: { placeholder: '搜索', hotKeywords: [], defaultMode: 'hybrid' },
    assistant: { enabled: true, title: '助手', welcomeText: '您好', suggestedQuestions: [] },
    pages: {
      home: {
        slug: 'home',
        title: '首页',
        kind: 'home',
        root: { id: 'shared-root', type: 'section', children: [] },
      },
      library: {
        slug: 'library',
        title: '资料中心',
        kind: 'library',
        root: {
          id: 'library-root',
          type: 'section',
          children: [
            {
              id: 'library-core',
              name: '资料检索结果',
              type: 'component',
              component: 'content-results',
              locked: true,
            },
          ],
        },
      },
      ask: {
        slug: 'ask',
        title: 'AI 问答',
        kind: 'ask',
        root: { id: 'ask-root', type: 'section', children: [] },
      },
    },
    symbols: {},
    packages: [],
  }
}

describe('low-code designer workspace', () => {
  it('orders pages by navigation and appends pages outside navigation', () => {
    expect(orderedPageSlugs(config())).toEqual(['library', 'home', 'ask'])
  })

  it('builds unique page-scoped keys for shell, pages and nested nodes', () => {
    const structure = buildDesignerStructure(config())
    expect(structure.map((item) => item.label)).toEqual(['全局壳层', '站点页面'])
    expect(structure[1].children?.map((item) => item.label)).toEqual(['资料中心', '首页', 'AI 问答'])
    expect(structureSelectionKey('$header', 'shared-root')).not.toBe(
      structureSelectionKey('home', 'shared-root'),
    )
    expect(structure[1].children?.[0].children?.[0].children?.[0]).toMatchObject({
      key: 'node:library:library-core',
      locked: true,
      nodeType: 'component',
    })
  })

  it('fits the canvas inside the available width and clamps zoom limits', () => {
    expect(fitCanvasZoom(780, 1440)).toBe(50)
    expect(fitCanvasZoom(200, 1440)).toBe(40)
    expect(fitCanvasZoom(1800, 390)).toBe(110)
    expect(fitCanvasZoom(0, 1440)).toBe(40)
  })

  it('normalizes continuous preview widths and derives breakpoints', () => {
    expect(normalizePreviewWidth(240)).toBe(320)
    expect(normalizePreviewWidth(2048)).toBe(1920)
    expect(normalizePreviewWidth(undefined)).toBe(1440)
    expect(previewBreakpoint(639)).toBe('mobile')
    expect(previewBreakpoint(640)).toBe('tablet')
    expect(previewBreakpoint(1199)).toBe('tablet')
    expect(previewBreakpoint(1200)).toBe('desktop')
  })

  it('maps topic directory previews to three, two and one columns', () => {
    expect(topicDirectoryColumns(1440)).toBe(3)
    expect(topicDirectoryColumns(1024)).toBe(2)
    expect(topicDirectoryColumns(390)).toBe(1)
  })
})
