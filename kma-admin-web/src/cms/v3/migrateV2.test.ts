import { describe, expect, it } from 'vitest'
import type { PortalSiteConfigV2 } from '../siteConfig'
import { findNode } from './contract'
import { migratePortalConfigV2ToV3 } from './migrateV2'

const source: PortalSiteConfigV2 = {
  schemaVersion: 2,
  revision: 'v2',
  site: { siteKey: 'default', scenario: 'party', name: '默认门户', locale: 'zh-CN' },
  shell: {
    header: {},
    navigation: [{ id: 'home', label: '首页', target: 'home' }],
    footer: {},
  },
  theme: { preset: 'emerald', mode: 'light', density: 'compact', tokens: {} },
  modules: {},
  contentScope: {
    allSpaces: true,
    spaceCodes: [],
    topicCodes: [],
    contentTypes: [],
    validityStatuses: ['effective'],
  },
  search: { placeholder: '搜索', hotKeywords: [], defaultMode: 'hybrid' },
  assistant: { enabled: true, title: '助手', welcomeText: '您好', suggestedQuestions: [] },
  pages: {
    home: {
      slug: 'home',
      layout: 'twelve-grid',
      regions: {
        main: [{ id: 'hero', type: 'hero-search', enabled: true, span: 12 }],
      },
      extensions: [
        {
          extensionId: 'portal-showcase',
          version: '1.0.0',
          slotKey: 'showcase',
          enabled: true,
        },
      ],
    },
  },
}

describe('CMS V2 to low-code V3 migration', () => {
  it('keeps published structure and creates editable system pages', () => {
    const migrated = migratePortalConfigV2ToV3(source)
    expect(migrated.schemaVersion).toBe(3)
    expect(findNode(migrated.pages.home.root, 'hero')).toMatchObject({
      type: 'component',
      component: 'hero-search',
    })
    expect(findNode(migrated.pages.home.root, 'showcase')).toMatchObject({
      type: 'sandbox',
      packageId: 'portal-showcase',
    })
    expect(migrated.pages.library.kind).toBe('library')
    expect(findNode(migrated.pages.library.root, 'library-core')).toMatchObject({
      component: 'content-results',
      locked: true,
    })
    expect(migrated.packages).toContainEqual({
      packageId: 'portal-showcase',
      version: '1.0.0',
      source: 'platform',
    })
  })
})
