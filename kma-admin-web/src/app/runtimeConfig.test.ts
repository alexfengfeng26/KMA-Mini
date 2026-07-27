import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  applyExperienceTheme,
  cloneRuntimeConfig,
  defaultRuntimeConfig,
  fetchRuntimeConfig,
  parseRuntimeConfig,
  runtimeDraftKey,
  serializeRuntimeConfig,
} from './runtimeConfig'

afterEach(() => {
  document.documentElement.removeAttribute('data-kma-theme')
  document.documentElement.removeAttribute('data-kma-template')
  document.documentElement.removeAttribute('data-kma-density')
  document.documentElement.removeAttribute('style')
  document.querySelector('link[data-kma-theme-icon]')?.remove()
})

describe('runtime experience configuration', () => {
  it('falls back safely for unsupported schema versions', () => {
    const result = parseRuntimeConfig({ schemaVersion: 9, experience: {} })
    expect(result.config).toEqual(defaultRuntimeConfig)
    expect(result.issues[0]).toContain('版本不受支持')
  })

  it('merges valid site values and filters unsafe values', () => {
    const result = parseRuntimeConfig({
      schemaVersion: 1,
      revision: 'site-r2',
      experience: {
        template: 'cms-news',
        density: 'comfortable',
        modules: { 'portal.qa': false, '../escape': true },
        tokens: {
          colorPrimary: '#9b1c31',
          colorBackground: 'url(https://bad.example)',
          fontSizeBase: '30px',
        },
        assets: {
          logo: '/themes/red/logo.svg',
          favicon: 'https://bad.example/icon.svg',
        },
        pages: {
          home: {
            template: 'cms-news',
            blocks: [
              {
                id: 'notice',
                type: 'announcement',
                enabled: true,
                variant: 'accent',
                props: { title: '通知', unsafe: '<script>' },
              },
              { id: 'notice', type: 'quick-ask', enabled: true },
              { id: 'bad', type: 'remote-component', enabled: true },
            ],
          },
        },
      },
    })

    expect(result.config.revision).toBe('site-r2')
    expect(result.config.experience.template).toBe('cms-news')
    expect(result.config.experience.modules['portal.qa']).toBe(false)
    expect(result.config.experience.modules['../escape']).toBeUndefined()
    expect(result.config.experience.tokens.colorPrimary).toBe('#9b1c31')
    expect(result.config.experience.tokens.colorBackground).toBe(
      defaultRuntimeConfig.experience.tokens.colorBackground,
    )
    expect(result.config.experience.assets.logo).toBe('/themes/red/logo.svg')
    expect(result.config.experience.assets.favicon).toBeUndefined()
    expect(result.config.experience.pages.home.blocks).toHaveLength(1)
    expect(result.config.experience.pages.home.blocks[0].props).toEqual({ title: '通知' })
    expect(result.issues.length).toBeGreaterThan(4)
  })

  it('loads the global JSON with no-store', async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(JSON.stringify(defaultRuntimeConfig), { status: 200 }))

    const global = await fetchRuntimeConfig('/config/kma-runtime.json', defaultRuntimeConfig, fetcher)

    expect(fetcher).toHaveBeenCalledWith('/config/kma-runtime.json', {
      cache: 'no-store',
      credentials: 'same-origin',
    })
    expect(global.config).toEqual(defaultRuntimeConfig)
  })

  it('applies and clears whitelisted theme resources', () => {
    const config = cloneRuntimeConfig()
    config.experience.assets.favicon = '/themes/emerald/favicon.svg'
    applyExperienceTheme(config.experience)

    expect(document.documentElement.dataset.kmaTheme).toBe('emerald')
    expect(document.documentElement.dataset.kmaDensity).toBe('compact')
    expect(document.documentElement.style.getPropertyValue('--kma-color-primary')).toBe('oklch(48% 0.11 187)')
    expect(document.querySelector<HTMLLinkElement>('link[data-kma-theme-icon]')?.href).toContain(
      '/themes/emerald/favicon.svg',
    )

    delete config.experience.assets.favicon
    applyExperienceTheme(config.experience)
    expect(document.querySelector('link[data-kma-theme-icon]')).toBeNull()
  })

  it('uses one global draft key and emits stable JSON', () => {
    expect(runtimeDraftKey()).toBe('kma:portal-experience:draft')
    expect(JSON.parse(serializeRuntimeConfig(defaultRuntimeConfig))).toEqual(defaultRuntimeConfig)
  })
})
