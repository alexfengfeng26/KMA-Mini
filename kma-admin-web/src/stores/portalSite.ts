import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getPortalBootstrap } from '../api/portalSites'
import type { PortalBootstrap } from '../cms/siteConfig'
import { setActivePortalSiteKey } from '../app/portalSiteContext'

const allowedToken = /^[a-z][a-z0-9-]{1,60}$/i
const tokenVariables: Record<string, string> = {
  colorPrimary: '--kma-color-primary',
  colorPrimaryStrong: '--kma-color-primary-strong',
  colorPrimarySoft: '--kma-color-primary-soft',
  colorBackground: '--kma-color-background',
  colorSurface: '--kma-color-surface',
  colorText: '--kma-color-text',
  colorTextMuted: '--kma-color-text-muted',
  colorBorder: '--kma-color-border',
  fontBody: '--kma-font-body',
  fontSizeBase: '--kma-font-size-base',
  lineHeightBody: '--kma-line-height-body',
  radiusCard: '--kma-radius-card',
  radiusControl: '--kma-radius-control',
  shadowCard: '--kma-shadow-card',
}
const appliedVariables = new Set<string>()

export const usePortalSiteStore = defineStore('portal-site', () => {
  const bootstrap = ref<PortalBootstrap | null>(null)
  const siteKey = ref('default')
  const loading = ref(false)
  const error = ref('')
  const site = computed(() => bootstrap.value?.site)

  function applyTheme(value: PortalBootstrap) {
    const root = document.documentElement
    for (const variable of appliedVariables) root.style.removeProperty(variable)
    appliedVariables.clear()
    root.dataset.kmaSite = value.site.siteKey
    root.dataset.kmaTheme = value.theme.preset || 'emerald'
    root.dataset.kmaPack = value.theme.pack || 'party-authority'
    root.dataset.kmaShell =
      ('layout' in value.shell ? value.shell.layout : undefined) || 'editorial-authority'
    root.dataset.kmaDensity = value.theme.density || 'compact'
    root.dataset.kmaColorMode = value.theme.mode || 'light'
    for (const [key, tokenValue] of Object.entries(value.theme.tokens || {})) {
      if (!allowedToken.test(key) || typeof tokenValue !== 'string' || tokenValue.length > 160) continue
      const variable = tokenVariables[key]
      if (variable) {
        root.style.setProperty(variable, tokenValue)
        appliedVariables.add(variable)
      }
    }
    let style = document.querySelector<HTMLStyleElement>('style[data-kma-site-theme]')
    if (!style) {
      style = document.createElement('style')
      style.dataset.kmaSiteTheme = 'true'
      document.head.append(style)
    }
    style.textContent = value.theme.scopedCss || ''
  }

  async function load(nextSiteKey: string, page = 'home') {
    error.value = ''
    if (bootstrap.value && siteKey.value === nextSiteKey && bootstrap.value.page.slug === page)
      return bootstrap.value
    loading.value = true
    try {
      const result = await getPortalBootstrap(nextSiteKey, page)
      siteKey.value = nextSiteKey
      bootstrap.value = result
      setActivePortalSiteKey(nextSiteKey)
      applyTheme(result)
      return result
    } catch (reason) {
      error.value = reason instanceof Error ? reason.message : '站点配置加载失败'
      throw reason
    } finally {
      loading.value = false
    }
  }

  function reset() {
    bootstrap.value = null
    siteKey.value = 'default'
    error.value = ''
    setActivePortalSiteKey('default')
    delete document.documentElement.dataset.kmaSite
    delete document.documentElement.dataset.kmaPack
    delete document.documentElement.dataset.kmaShell
    for (const variable of appliedVariables) document.documentElement.style.removeProperty(variable)
    appliedVariables.clear()
    document.querySelector('style[data-kma-site-theme]')?.remove()
  }

  function isModuleEnabled(moduleId: string, core = false, defaultEnabled = true) {
    if (core) return true
    return bootstrap.value?.modules[moduleId] ?? defaultEnabled
  }

  return { bootstrap, siteKey, site, loading, error, load, reset, isModuleEnabled }
})
