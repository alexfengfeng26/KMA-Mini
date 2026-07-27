import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const api = vi.hoisted(() => ({
  getPortalBootstrap: vi.fn(),
}))
const context = vi.hoisted(() => ({
  setActivePortalSiteKey: vi.fn(),
}))

vi.mock('../api/portalSites', () => api)
vi.mock('../app/portalSiteContext', () => context)

import type { PortalBootstrap } from '../cms/siteConfig'
import { usePortalSiteStore } from './portalSite'

const bootstrap: PortalBootstrap = {
  site: {
    siteId: 1,
    siteKey: 'policy',
    name: '制度中心',
    scenario: 'internal-policy',
    status: 'active',
    defaultSite: false,
  },
  publishedVersion: 2,
  revision: 'published-2',
  shell: { header: {}, navigation: [], footer: {} },
  theme: {
    preset: 'emerald',
    mode: 'light',
    density: 'compact',
    tokens: {
      colorPrimary: '#0b766e',
      invalid_token: 'ignored',
    },
    scopedCss: '[data-kma-site="policy"] .card { color: #0b766e; }',
  },
  modules: { 'portal.library': false },
  search: { placeholder: '搜索制度', hotKeywords: [], defaultMode: 'hybrid' },
  assistant: { enabled: true, title: '制度助手', welcomeText: '', suggestedQuestions: [] },
  page: { slug: 'home', layout: 'single', regions: { main: [] } },
  portalData: {
    config: { unitName: '制度中心', helpText: '', currentTopicCode: '' },
    categories: [],
    recent: [],
    topics: [],
    history: [],
    favorites: [],
  },
}

describe('portal site store', () => {
  beforeEach(() => {
    document.documentElement.removeAttribute('style')
    delete document.documentElement.dataset.kmaSite
    document.querySelector('style[data-kma-site-theme]')?.remove()
    api.getPortalBootstrap.mockReset().mockResolvedValue(structuredClone(bootstrap))
    context.setActivePortalSiteKey.mockReset()
    setActivePinia(createPinia())
  })

  it('loads bootstrap once and applies scoped site tokens and module rules', async () => {
    const store = usePortalSiteStore()

    await store.load('policy')
    await store.load('policy')

    expect(api.getPortalBootstrap).toHaveBeenCalledOnce()
    expect(store.site?.name).toBe('制度中心')
    expect(store.siteKey).toBe('policy')
    expect(context.setActivePortalSiteKey).toHaveBeenCalledWith('policy')
    expect(document.documentElement.dataset.kmaSite).toBe('policy')
    expect(document.documentElement.style.getPropertyValue('--kma-color-primary')).toBe('#0b766e')
    expect(document.documentElement.style.getPropertyValue('--invalid_token')).toBe('')
    expect(document.querySelector('style[data-kma-site-theme]')?.textContent).toContain(
      '[data-kma-site="policy"]',
    )
    expect(store.isModuleEnabled('portal.library', false, true)).toBe(false)
    expect(store.isModuleEnabled('portal.profile', true, false)).toBe(true)
    expect(store.isModuleEnabled('portal.topics', false, true)).toBe(true)
  })

  it('reloads another page, reports request errors and resets site state', async () => {
    const store = usePortalSiteStore()
    await store.load('policy')
    await store.load('policy', 'guide')
    expect(api.getPortalBootstrap).toHaveBeenLastCalledWith('policy', 'guide')

    api.getPortalBootstrap.mockRejectedValueOnce(new Error('bootstrap unavailable'))
    await expect(store.load('broken')).rejects.toThrow('bootstrap unavailable')
    expect(store.error).toBe('bootstrap unavailable')
    expect(store.loading).toBe(false)

    store.reset()
    expect(store.bootstrap).toBeNull()
    expect(store.siteKey).toBe('default')
    expect(document.documentElement.dataset.kmaSite).toBeUndefined()
    expect(document.documentElement.style.getPropertyValue('--kma-color-primary')).toBe('')
    expect(document.querySelector('style[data-kma-site-theme]')).toBeNull()
    expect(context.setActivePortalSiteKey).toHaveBeenLastCalledWith('default')
  })

  it('clears a failed page error when returning to an already cached page', async () => {
    const store = usePortalSiteStore()
    await store.load('policy', 'home')

    api.getPortalBootstrap.mockRejectedValueOnce(new Error('PORTAL_PAGE_NOT_FOUND'))
    await expect(store.load('policy', 'missing')).rejects.toThrow('PORTAL_PAGE_NOT_FOUND')
    expect(store.error).toBe('PORTAL_PAGE_NOT_FOUND')

    await store.load('policy', 'home')

    expect(store.error).toBe('')
    expect(store.bootstrap?.page.slug).toBe('home')
    expect(api.getPortalBootstrap).toHaveBeenCalledTimes(2)
  })
})
