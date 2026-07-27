export type SiteShell = 'portal' | 'console'

export const SITE_KEY_PATTERN = /^[a-z][a-z0-9_-]{1,63}$/
export const SITE_ROUTE_PARAM = ':siteKey'

export function validSiteKey(value: unknown): value is string {
  return typeof value === 'string' && SITE_KEY_PATTERN.test(value)
}

function interpolate(path: string, params: Record<string, string | number>) {
  return Object.entries(params).reduce(
    (result, [key, value]) => result.replaceAll(`:${key}`, encodeURIComponent(String(value))),
    path,
  )
}

export function consolePath(path: string, params: Record<string, string | number> = {}): string {
  const resolved = interpolate(path, params)
  if (resolved !== '/console' && !resolved.startsWith('/console/')) {
    throw new Error('只允许构造控制台路径')
  }
  return resolved
}

export function portalSitePath(
  siteKey: string,
  path: string,
  params: Record<string, string | number> = {},
): string {
  if (!validSiteKey(siteKey)) throw new Error('站点标识不合法')
  const logical = path.startsWith('/portal/') ? path.slice('/portal'.length) : path
  const resolved = interpolate(logical, params)
  if (resolved !== '' && resolved !== '/' && !resolved.startsWith('/')) {
    throw new Error('站点路径必须以 / 开头')
  }
  return `/p/${encodeURIComponent(siteKey)}${resolved || '/home'}`
}

export function portalHome(siteKey = 'default'): string {
  return portalSitePath(siteKey, '/home')
}

export function portalProfile(siteKey = 'default'): string {
  return portalSitePath(siteKey, '/profile')
}

export function routeSiteKey(value: unknown): string | null {
  const candidate = Array.isArray(value) ? value[0] : value
  return validSiteKey(candidate) ? candidate : null
}

export function stripSitePrefix(path: string): string | null {
  const siteMatch = /^\/p\/([a-z][a-z0-9_-]{1,63})(\/.*)?$/.exec(path)
  if (siteMatch) return `/portal${siteMatch[2] || '/home'}`
  if (path === '/portal' || path.startsWith('/portal/')) return path
  if (path === '/console' || path.startsWith('/console/')) return path
  return null
}

export function isLegacyBusinessPath(path: string) {
  return (
    path === '/portal' || path.startsWith('/portal/') || path === '/console' || path.startsWith('/console/')
  )
}

export function safeRedirect(value: unknown): string | null {
  if (typeof value !== 'string' || value.includes('://') || value.startsWith('//')) return null
  const [pathname] = value.split(/[?#]/, 1)
  return pathname === '/portal' ||
    pathname?.startsWith('/portal/') ||
    pathname?.startsWith('/p/') ||
    pathname === '/console' ||
    pathname?.startsWith('/console/')
    ? value
    : null
}

export function currentPath(path: string): string {
  const siteMatch = /^\/p\/([a-z][a-z0-9_-]{1,63})(\/.*)?$/.exec(path)
  if (siteMatch) return portalSitePath(siteMatch[1], siteMatch[2] || '/home')
  if (path === '/portal' || path.startsWith('/portal/')) return portalSitePath('default', path)
  if (path === '/console' || path.startsWith('/console/')) return path
  return portalHome()
}
