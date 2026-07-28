import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { consolePath, portalSitePath, routeSiteKey } from '../security/siteRoute'

export function useSiteNavigation() {
  const route = useRoute()
  const siteKey = computed(() => routeSiteKey(route.params.siteKey) || 'default')
  const previewVersion = computed(() => {
    const value = Number(route.query.previewVersion)
    return Number.isSafeInteger(value) && value > 0 ? value : undefined
  })
  const previewQuery = computed(() =>
    previewVersion.value ? { previewVersion: String(previewVersion.value) } : {},
  )
  const addPreview = (path: string) => {
    if (!previewVersion.value) return path
    return `${path}${path.includes('?') ? '&' : '?'}previewVersion=${previewVersion.value}`
  }
  return {
    siteKey,
    previewVersion,
    previewQuery,
    sitePath: (path: string, params: Record<string, string | number> = {}) =>
      path === '/portal' || path.startsWith('/portal/')
        ? addPreview(portalSitePath(siteKey.value, path, params))
        : consolePath(path, params),
  }
}
