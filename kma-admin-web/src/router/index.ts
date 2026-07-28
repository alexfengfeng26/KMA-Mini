import { createRouter, createWebHistory, type RouteLocationNormalized } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import AdminLayout from '../layouts/AdminLayout.vue'
import PortalLayout from '../layouts/PortalLayout.vue'
import ForbiddenView from '../views/ForbiddenView.vue'
import FeatureUnavailableView from '../views/FeatureUnavailableView.vue'
import { useAuthStore } from '../stores/auth'
import { consoleNavigation, firstAuthorizedPath } from '../security/navigation'
import { useExperienceStore } from '../stores/experience'
import { usePortalSiteStore } from '../stores/portalSite'
import {
  consoleModules,
  getFrontendModule,
  isFrontendModuleEnabled,
  portalModules,
} from '../modules/registry'
import {
  consolePath,
  currentPath,
  portalHome,
  portalProfile,
  portalSitePath,
  routeSiteKey,
  safeRedirect,
  SITE_ROUTE_PARAM,
} from '../security/siteRoute'
import { recordPortalEvent } from '../api/portalSites'

const EmptyRouteView = { render: () => null }

function childPath(path: string, shell: 'portal' | 'console') {
  const prefix = `/${shell}/`
  return path === `/${shell}` ? '' : path.startsWith(prefix) ? path.slice(prefix.length) : path
}

function portalPageSlug(route: RouteLocationNormalized) {
  if (route.name === 'portal-custom-page') return String(route.params.pageSlug || 'home')
  const byRouteName: Record<string, string> = {
    'portal-home': 'home',
    'portal-library': 'library',
    'portal-content': 'content',
    'portal-ask': 'ask',
    'portal-topics': 'topics',
    'portal-favorites': 'favorites',
    'portal-profile': 'profile',
  }
  return byRouteName[String(route.name || '')] || 'home'
}

const portalRoutes = portalModules.flatMap((module) =>
  module.routes.map((route) => ({
    path: childPath(route.path, 'portal'),
    name: route.name,
    component: route.component,
    meta: {
      title: module.title,
      permissions: route.permissions || module.permissions,
      moduleId: module.id,
      ...route.meta,
    },
  })),
)

const consoleRoutes = consoleModules.flatMap((module) =>
  module.routes.map((route) => ({
    path: childPath(route.path, 'console'),
    name: route.name,
    component: route.component,
    meta: {
      title: module.title,
      permissions: route.permissions || module.permissions,
      moduleId: module.id,
      ...route.meta,
    },
  })),
)

const legacyRedirects = consoleNavigation
  .filter((item) => item.section !== 'account')
  .map((item) => ({
    path: item.path.replace('/console', ''),
    redirect: item.path,
  }))

function loginRedirect(to: RouteLocationNormalized) {
  return {
    path: '/login',
    query: {
      redirect: to.fullPath,
    },
  }
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: LoginView },
    { path: '/', component: EmptyRouteView },
    {
      path: `/p/${SITE_ROUTE_PARAM}`,
      component: PortalLayout,
      children: [{ path: '', component: EmptyRouteView }, ...portalRoutes],
    },
    {
      path: '/console',
      component: AdminLayout,
      children: [
        { path: '', component: EmptyRouteView },
        ...consoleRoutes,
        { path: 'access', component: EmptyRouteView },
      ],
    },
    { path: '/portal/:pathMatch(.*)*', component: EmptyRouteView, meta: { legacyBusiness: true } },
    { path: '/console/:pathMatch(.*)*', component: EmptyRouteView, meta: { legacyBusiness: true } },
    ...legacyRedirects,
    { path: '/profile', redirect: '/portal/profile' },
    { path: '/403', component: ForbiddenView, meta: { title: '无权访问', permissions: [] } },
    {
      path: '/unavailable',
      component: FeatureUnavailableView,
      meta: { title: '功能未启用', permissions: [] },
    },
  ],
})

router.beforeEach(async (to) => {
  const publicPath = ['/login'].includes(to.path)
  const hasToken = Boolean(sessionStorage.getItem('kma_access_token'))
  const requestedSite = routeSiteKey(to.params.siteKey)
  if (!publicPath && !hasToken) return loginRedirect(to)
  if (publicPath) return

  const auth = useAuthStore()
  const experience = useExperienceStore()
  const portalSite = usePortalSiteStore()
  try {
    await auth.ensureUser()
  } catch {
    return loginRedirect(to)
  }

  // A transient bootstrap failure can leave the browser parked on this route after
  // the API has recovered. Retry once whenever the failure page is entered directly.
  if (to.path === '/unavailable' && to.query.reason === 'site') {
    const failedSite = routeSiteKey(to.query.site)
    if (failedSite) {
      try {
        await portalSite.load(failedSite, 'home')
        return portalHome(failedSite)
      } catch {
        // Keep rendering the failure page when the site is genuinely unavailable.
      }
    }
  }

  if (to.path === '/') return portalHome('default')
  if (to.meta.legacyBusiness) {
    if (to.path === '/portal' || to.path.startsWith('/portal/')) {
      return {
        path: portalSitePath('default', to.path),
        query: to.query,
        hash: to.hash,
      }
    }
    return { path: consolePath(to.path), query: to.query, hash: to.hash }
  }
  const profilePath = requestedSite ? portalSitePath(requestedSite, '/profile') : portalProfile('default')
  const mustChangePassword = sessionStorage.getItem('kma_must_change_password') === 'true'
  if (mustChangePassword && to.path !== profilePath && to.path !== '/unavailable' && to.path !== '/403')
    return profilePath

  // 首次登录强制改密时，默认门户站点可能尚未发布，直接渲染改密页，避免 bootstrap 失败导致无限重定向。
  if (requestedSite && !(mustChangePassword && to.path === profilePath)) {
    try {
      const requestedPreview = Number(to.query.previewVersion)
      const previewVersion =
        Number.isSafeInteger(requestedPreview) && requestedPreview > 0 ? requestedPreview : undefined
      await (previewVersion
        ? portalSite.load(requestedSite, portalPageSlug(to), previewVersion)
        : portalSite.load(requestedSite, portalPageSlug(to)))
    } catch {
      return {
        path: '/unavailable',
        query: { site: requestedSite, reason: 'site' },
      }
    }
  }
  if (to.path === '/portal') return portalHome('default')
  if (to.path === '/console')
    return consolePath(firstAuthorizedPath(auth.hasAnyPermission, experience.isFeatureEnabled))
  if (to.path === '/console/access') return consolePath('/console/access/users')

  const routeModule = getFrontendModule(String(to.meta.moduleId || ''))
  if (
    routeModule &&
    !isFrontendModuleEnabled(
      routeModule,
      routeModule.shell === 'portal' && requestedSite
        ? portalSite.isModuleEnabled
        : experience.isFeatureEnabled,
    ) &&
    to.path !== '/unavailable'
  )
    return { path: '/unavailable', query: { module: routeModule.id } }
  if (!auth.hasAnyPermission(to.meta.permissions as string[] | undefined)) return { path: '/403' }
})

window.addEventListener('kma-auth-refreshed', () => {
  const auth = useAuthStore()
  const experience = useExperienceStore()
  const portalSite = usePortalSiteStore()
  const currentSite = routeSiteKey(router.currentRoute.value.params.siteKey)
  if (currentSite) {
    const requestedPreview = Number(router.currentRoute.value.query.previewVersion)
    const previewVersion =
      Number.isSafeInteger(requestedPreview) && requestedPreview > 0 ? requestedPreview : undefined
    void (previewVersion
      ? portalSite.load(currentSite, portalPageSlug(router.currentRoute.value), previewVersion)
      : portalSite.load(currentSite, portalPageSlug(router.currentRoute.value)))
  }
  const permissions = router.currentRoute.value.meta.permissions as string[] | undefined
  if (!auth.hasAnyPermission(permissions))
    void router.replace(
      auth.hasAnyPermission(['content:read'])
        ? portalSitePath(currentSite || 'default', '/home')
        : consolePath(firstAuthorizedPath(auth.hasAnyPermission, experience.isFeatureEnabled)),
    )
})

router.afterEach((to) => {
  if (to.path === '/login') {
    document.title = '登录 · KMA 知识管理中心'
    return
  }
  document.title = 'KMA 知识管理中心'
  const siteKey = routeSiteKey(to.params.siteKey)
  if (siteKey)
    void recordPortalEvent(siteKey, {
      eventType: 'page_view',
      pageSlug: String(to.name || to.path.split('/').at(-1) || 'home').slice(0, 64),
    }).catch(() => undefined)
})

export function resolveSafePostLoginPath(redirect?: unknown, fallback?: string) {
  return safeRedirect(redirect) || fallback || portalHome('default')
}

export function routeForCurrentSite(path: string) {
  return currentPath(path)
}

export default router
