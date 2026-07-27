import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { consolePath, portalSitePath, routeSiteKey } from '../security/siteRoute'

export function useSiteNavigation() {
  const route = useRoute()
  const siteKey = computed(() => routeSiteKey(route.params.siteKey) || 'default')
  return {
    siteKey,
    sitePath: (path: string, params: Record<string, string | number> = {}) =>
      path === '/portal' || path.startsWith('/portal/')
        ? portalSitePath(siteKey.value, path, params)
        : consolePath(path, params),
  }
}
