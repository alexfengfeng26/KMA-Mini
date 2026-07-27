import { validSiteKey } from '../security/siteRoute'

let activeSiteKey = 'default'

export function setActivePortalSiteKey(siteKey: string | null | undefined) {
  activeSiteKey = validSiteKey(siteKey) ? siteKey : 'default'
}

export function getActivePortalSiteKey() {
  return activeSiteKey
}
