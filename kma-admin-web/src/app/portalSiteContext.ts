import { validSiteKey } from '../security/siteRoute'

let activeSiteKey = 'default'
let activePreviewVersion: number | undefined

export function setActivePortalSiteKey(siteKey: string | null | undefined) {
  activeSiteKey = validSiteKey(siteKey) ? siteKey : 'default'
}

export function getActivePortalSiteKey() {
  return activeSiteKey
}

export function setActivePortalPreviewVersion(version: number | null | undefined) {
  activePreviewVersion = Number.isSafeInteger(version) && Number(version) > 0 ? Number(version) : undefined
}

export function getActivePortalPreviewVersion() {
  return activePreviewVersion
}
