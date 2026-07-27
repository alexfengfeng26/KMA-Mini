import { describe, expect, it } from 'vitest'
import { validatePortalSiteConfig } from './siteConfigValidator'

const validConfig = {
  schemaVersion: 2,
  revision: 'test-1',
  site: { siteKey: 'default', scenario: 'party', name: '知识门户', locale: 'zh-CN' },
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
  search: {},
  assistant: {},
  pages: {
    home: {
      slug: 'home',
      layout: 'twelve-grid',
      regions: {
        main: [{ id: 'hero', type: 'hero-search', enabled: true, span: 12 }],
      },
    },
  },
}

describe('portal site CMS V2 schema', () => {
  it('accepts a controlled site configuration', () => {
    expect(validatePortalSiteConfig(validConfig)).toEqual({ valid: true, issues: [] })
  })

  it('rejects unknown remote block fields and invalid spans', () => {
    const invalid = structuredClone(validConfig) as Record<string, unknown>
    const pages = invalid.pages as typeof validConfig.pages
    Object.assign(pages.home.regions.main[0], { apiUrl: 'https://evil.test', span: 13 })
    const result = validatePortalSiteConfig(invalid)
    expect(result.valid).toBe(false)
    expect(result.issues.join(' ')).toContain('additional properties')
    expect(result.issues.join(' ')).toContain('must be <= 12')
  })
})
