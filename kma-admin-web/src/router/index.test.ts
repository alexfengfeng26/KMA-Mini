import { beforeEach, describe, expect, it, vi } from 'vitest'

const auth = vi.hoisted(() => ({
  ensureUser: vi.fn(),
  hasAnyPermission: vi.fn<(permissions?: string[]) => boolean>(),
  user: { userId: 1, username: 'admin' },
}))
const experience = vi.hoisted(() => ({
  isFeatureEnabled: vi.fn<(featureKey: string, core?: boolean, defaultEnabled?: boolean) => boolean>(),
}))
const portalSite = vi.hoisted(() => ({
  load: vi.fn(),
  isModuleEnabled: vi.fn<(featureKey: string, core?: boolean, defaultEnabled?: boolean) => boolean>(),
}))

vi.mock('../stores/auth', () => ({ useAuthStore: () => auth }))
vi.mock('../stores/experience', () => ({ useExperienceStore: () => experience }))
vi.mock('../stores/portalSite', () => ({ usePortalSiteStore: () => portalSite }))

import router from './index'

describe('router authorization guard', () => {
  beforeEach(async () => {
    sessionStorage.clear()
    auth.user = { userId: 1, username: 'admin' }
    auth.ensureUser.mockReset().mockResolvedValue(auth.user)
    auth.hasAnyPermission.mockReset().mockReturnValue(true)
    experience.isFeatureEnabled.mockReset().mockReturnValue(true)
    portalSite.load.mockReset().mockResolvedValue(undefined)
    portalSite.isModuleEnabled.mockReset().mockReturnValue(true)
    await router.replace('/login')
  })

  it('redirects anonymous users to login', async () => {
    await router.push('/portal/home')
    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('forces the password-change flow before business routes', async () => {
    sessionStorage.setItem('kma_access_token', 'token')
    sessionStorage.setItem('kma_must_change_password', 'true')
    await router.push('/portal/library')
    expect(router.currentRoute.value.path).toBe('/p/default/profile')
  })

  it('returns 403 for an authenticated user without route permission', async () => {
    sessionStorage.setItem('kma_access_token', 'token')
    auth.hasAnyPermission.mockImplementation((permissions) => !permissions?.length)
    await router.push('/portal/library')
    expect(router.currentRoute.value.path).toBe('/403')
  })

  it('redirects the console root to the first authorized menu', async () => {
    sessionStorage.setItem('kma_access_token', 'token')
    auth.hasAnyPermission.mockImplementation(
      (permissions) => permissions?.includes('dashboard:read') === true,
    )
    await router.push('/console')
    expect(router.currentRoute.value.path).toBe('/console/dashboard')
  })

  it('routes an authenticated user to the unavailable page when a feature is closed', async () => {
    sessionStorage.setItem('kma_access_token', 'token')
    portalSite.isModuleEnabled.mockImplementation(
      (featureKey, core) => core || featureKey !== 'portal.library',
    )
    await router.push('/portal/library')
    expect(router.currentRoute.value.path).toBe('/unavailable')
    expect(router.currentRoute.value.query.module).toBe('portal.library')
  })

  it('loads the selected site bootstrap for a canonical multi-site route', async () => {
    sessionStorage.setItem('kma_access_token', 'token')
    await router.push('/p/policy/home')
    expect(router.currentRoute.value.path).toBe('/p/policy/home')
    expect(portalSite.load).toHaveBeenCalledWith('policy', 'home')
  })

  it('isolates a site bootstrap failure on the unavailable page', async () => {
    sessionStorage.setItem('kma_access_token', 'token')
    portalSite.load.mockRejectedValueOnce(new Error('site disabled'))
    await router.push('/p/policy/home')
    expect(router.currentRoute.value.path).toBe('/unavailable')
    expect(router.currentRoute.value.query.site).toBe('policy')
    expect(router.currentRoute.value.query.reason).toBe('site')
  })

  it('redirects a legacy portal route to the default site', async () => {
    sessionStorage.setItem('kma_access_token', 'token')
    await router.push('/portal/topics')
    expect(router.currentRoute.value.path).toBe('/p/default/topics')
  })
})
