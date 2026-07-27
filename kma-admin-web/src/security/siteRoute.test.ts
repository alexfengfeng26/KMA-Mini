import { describe, expect, it } from 'vitest'
import {
  consolePath,
  currentPath,
  isLegacyBusinessPath,
  portalHome,
  portalSitePath,
  routeSiteKey,
  safeRedirect,
  stripSitePrefix,
} from './siteRoute'

describe('site route contract', () => {
  it('validates site identifiers and builds canonical paths', () => {
    expect(portalSitePath('default', '/portal/content/:contentId', { contentId: 7 })).toBe(
      '/p/default/content/7',
    )
    expect(portalSitePath('policy-center', '/portal/content/:contentId', { contentId: 7 })).toBe(
      '/p/policy-center/content/7',
    )
    expect(() => portalSitePath('../other', '/home')).toThrow()
    expect(() => portalSitePath('default', 'home')).toThrow()
  })

  it('builds console and portal home paths', () => {
    expect(consolePath('/console/documents')).toBe('/console/documents')
    expect(() => consolePath('/portal/home')).toThrow()
    expect(portalHome('default')).toBe('/p/default/home')
    expect(portalHome('policy-center')).toBe('/p/policy-center/home')
  })

  it('parses site routes and legacy paths', () => {
    expect(routeSiteKey(['default'])).toBe('default')
    expect(routeSiteKey('invalid site')).toBeNull()
    expect(stripSitePrefix('/p/default/console/documents')).toBe('/portal/console/documents')
    expect(isLegacyBusinessPath('/portal/home')).toBe(true)
    expect(isLegacyBusinessPath('/console/users')).toBe(true)
  })

  it('accepts only local portal and console redirects', () => {
    expect(safeRedirect('/portal/library?q=x')).toBe('/portal/library?q=x')
    expect(safeRedirect('/p/default/home')).toBe('/p/default/home')
    expect(safeRedirect('/console/home')).toBe('/console/home')
    expect(safeRedirect('https://evil.example/p/default/home')).toBeNull()
    expect(safeRedirect('/evil')).toBeNull()
  })

  it('resolves current path for site and legacy paths', () => {
    expect(currentPath('/p/default/library')).toBe('/p/default/library')
    expect(currentPath('/portal/library')).toBe('/p/default/library')
    expect(currentPath('/console/documents')).toBe('/console/documents')
    expect(currentPath('/unknown')).toBe('/p/default/home')
  })
})
