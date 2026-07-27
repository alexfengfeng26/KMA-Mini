import { describe, expect, it } from 'vitest'
import {
  authorizedNavigationSections,
  consoleNavigation,
  firstAuthorizedPath,
  navigationSectionTitles,
} from './navigation'

describe('authorization navigation registry', () => {
  it('keeps paths unique and every business route permission-bound', () => {
    const paths = consoleNavigation.map((item) => item.path)
    expect(new Set(paths).size).toBe(paths.length)
    expect(
      consoleNavigation
        .filter((item) => item.path !== '/console/profile')
        .every((item) => item.permissions.length > 0),
    ).toBe(true)
  })

  it('groups every business route once in workflow order', () => {
    const businessItems = consoleNavigation.filter((item) => item.section !== 'account')
    const groupedItems = authorizedNavigationSections(() => true).flatMap((section) => section.items)
    expect(groupedItems.map((item) => item.path)).toEqual(businessItems.map((item) => item.path))
    expect(navigationSectionTitles.map((section) => section.title)).toEqual([
      '党建内容治理',
      '知识技术治理',
      '检索与 AI 质量',
      '高级运维',
      '组织与权限',
      '平台管理',
    ])
  })

  it('removes empty sections after permission filtering', () => {
    const sections = authorizedNavigationSections((required) => required?.includes('qa:use') || false)
    expect(sections).toHaveLength(1)
    expect(sections[0].title).toBe('检索与 AI 质量')
    expect(sections[0].items.map((item) => item.path)).toEqual(['/console/qa'])
  })

  it('redirects root to first authorized menu and falls back to profile', () => {
    expect(firstAuthorizedPath((required) => required?.includes('qa:use') || false)).toBe('/console/qa')
    expect(firstAuthorizedPath(() => false)).toBe('/console/profile')
  })

  it('removes disabled feature modules without changing permission semantics', () => {
    const sections = authorizedNavigationSections(
      () => true,
      (featureKey) => featureKey !== 'governance.portal-appearance',
    )
    expect(sections.flatMap((section) => section.items).map((item) => item.path)).not.toContain(
      '/console/portal-appearance',
    )
  })
})
