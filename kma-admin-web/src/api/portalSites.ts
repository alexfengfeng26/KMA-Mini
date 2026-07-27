import { authorizedJson, asList, asRecord } from './client'
import { normalizePortalHome } from './party'
import type {
  PortalBootstrap,
  PortalConfigVersion,
  PortalPageConfig,
  PortalSiteConfig,
  PortalSiteSummary,
} from '../cms/siteConfig'

function sitePath(siteKey: string, suffix = '') {
  return `/api/v1/portal-sites/${encodeURIComponent(siteKey)}${suffix}`
}

function adminPath(siteKey = '', suffix = '') {
  return `/api/v1/admin/portal-sites${siteKey ? `/${encodeURIComponent(siteKey)}` : ''}${suffix}`
}

export async function getPortalBootstrap(siteKey: string, page = 'home'): Promise<PortalBootstrap> {
  const value = asRecord(
    await authorizedJson(sitePath(siteKey, `/bootstrap?page=${encodeURIComponent(page)}`)),
  )
  return {
    schemaVersion: Number(value.schemaVersion || 2) === 3 ? 3 : 2,
    site: asRecord(value.site) as unknown as PortalSiteSummary,
    publishedVersion: Number(value.publishedVersion || 0),
    revision: String(value.revision || ''),
    shell: asRecord(value.shell) as unknown as PortalBootstrap['shell'],
    theme: asRecord(value.theme) as unknown as PortalBootstrap['theme'],
    modules: asRecord(value.modules) as Record<string, boolean>,
    search: asRecord(value.search) as unknown as PortalBootstrap['search'],
    assistant: asRecord(value.assistant) as unknown as PortalBootstrap['assistant'],
    page: asRecord(value.page) as unknown as PortalPageConfig,
    symbols: asRecord(value.symbols) as PortalBootstrap['symbols'],
    packages: asList(value.packages) as unknown as PortalBootstrap['packages'],
    extensions: asList(value.extensions) as unknown as PortalBootstrap['extensions'],
    portalData: normalizePortalHome(value.portalData),
  }
}

export interface PortalExtensionCatalogItem {
  extensionId: string
  version: string
  displayName: string
  entryUrl: string
  integrityHash: string
  manifest: Record<string, unknown>
  minFrontendVersion?: string
}

export async function listPortalExtensionCatalog() {
  return asList(await authorizedJson('/api/v1/admin/portal-extensions')).map(
    (item) => asRecord(item) as unknown as PortalExtensionCatalogItem,
  )
}

export function portalExtensionContents(siteKey: string, keyword = '') {
  return authorizedJson(sitePath(siteKey, `/contents?keyword=${encodeURIComponent(keyword)}`))
}

export function portalExtensionSearch(siteKey: string, keyword: string) {
  return authorizedJson(sitePath(siteKey, '/search'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ keyword }),
  })
}

export function portalExtensionAsk(siteKey: string, query: string, spaceCode = '*') {
  return authorizedJson(sitePath(siteKey, '/ask'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ query, spaceCode, topK: 5, portalOnly: true }),
  })
}

export function getPortalPage(siteKey: string, pageSlug: string) {
  return authorizedJson<PortalPageConfig>(sitePath(siteKey, `/pages/${encodeURIComponent(pageSlug)}`))
}

export async function listPortalSites() {
  return asList(await authorizedJson(adminPath())).map(
    (item) => asRecord(item) as unknown as PortalSiteSummary,
  )
}

export function getPortalSite(siteKey: string) {
  return authorizedJson<PortalSiteSummary & { versions: PortalConfigVersion[] }>(adminPath(siteKey))
}

export function createPortalSite(body: {
  siteKey: string
  name: string
  scenario: string
  defaultSite?: boolean
}) {
  return authorizedJson(adminPath(), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export function updatePortalSite(
  siteKey: string,
  body: { name: string; status: string; defaultSite: boolean },
) {
  return authorizedJson(adminPath(siteKey), {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export function deletePortalSite(siteKey: string) {
  return authorizedJson<void>(adminPath(siteKey), { method: 'DELETE' })
}

export function listPortalVersions(siteKey: string) {
  return authorizedJson<PortalConfigVersion[]>(adminPath(siteKey, '/versions'))
}

export function getPortalVersion(siteKey: string, versionId: number) {
  return authorizedJson<PortalConfigVersion>(adminPath(siteKey, `/versions/${versionId}`))
}

export function createPortalDraft(siteKey: string, config?: PortalSiteConfig, changeNote?: string) {
  return authorizedJson<PortalConfigVersion>(adminPath(siteKey, '/drafts'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ config, changeNote }),
  })
}

export function updatePortalDraft(
  siteKey: string,
  version: PortalConfigVersion,
  config: PortalSiteConfig,
  changeNote?: string,
) {
  return authorizedJson<PortalConfigVersion>(adminPath(siteKey, `/drafts/${version.versionId}`), {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      config,
      expectedLockVersion: version.lockVersion,
      changeNote,
    }),
  })
}

export function portalVersionAction(
  siteKey: string,
  action: 'validate' | 'submit' | 'approve' | 'reject' | 'publish',
  versionId: number,
  note?: string,
) {
  return authorizedJson(adminPath(siteKey, `/${action}`), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ versionId, note }),
  })
}

export function rollbackPortalVersion(siteKey: string, versionId: number) {
  return authorizedJson<PortalConfigVersion>(adminPath(siteKey, `/rollback/${versionId}`), {
    method: 'POST',
  })
}

export function recordPortalEvent(
  siteKey: string,
  body: {
    eventType: 'page_view' | 'search' | 'search_empty' | 'article_click' | 'ai_ask' | 'feedback'
    pageSlug?: string
    queryText?: string
    targetId?: string
    metadata?: Record<string, unknown>
  },
) {
  return authorizedJson<void>(sitePath(siteKey, '/events'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export interface PortalAnalyticsSummary {
  days: number
  totals: Array<{ eventType: string; total: number }>
  topSearches: Array<{ keyword: string; total: number }>
}

export function getPortalAnalytics(siteKey: string, days = 30) {
  return authorizedJson<PortalAnalyticsSummary>(
    adminPath(siteKey, `/analytics?days=${Math.max(1, Math.min(days, 90))}`),
  )
}

export interface PortalCodeVersion {
  versionId: number
  versionNo: number
  version: string
  status: 'draft' | 'published' | 'revoked'
  sourceMode: 'editor' | 'zip'
  scanStatus: 'pending' | 'passed' | 'failed'
  scanResult?: { issues?: string[] }
  checksum: string
  fileCount: number
}

export interface PortalCodePackage {
  packageId: number
  packageKey: string
  displayName: string
  description?: string
  status: 'draft' | 'active' | 'revoked'
  currentVersionId?: number
  currentVersion?: string
  versions?: PortalCodeVersion[]
}

export async function listPortalCodePackages() {
  return asList(await authorizedJson('/api/v1/admin/portal-code-packages')).map(
    (item) => asRecord(item) as unknown as PortalCodePackage,
  )
}

export function getPortalCodePackage(packageId: number) {
  return authorizedJson<PortalCodePackage>(
    `/api/v1/admin/portal-code-packages/${encodeURIComponent(packageId)}`,
  )
}

export function createPortalCodePackage(body: {
  packageKey: string
  displayName: string
  description?: string
}) {
  return authorizedJson<PortalCodePackage>('/api/v1/admin/portal-code-packages', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export function savePortalCodeFiles(
  packageId: number,
  body: {
    version: string
    manifest: Record<string, unknown>
    files: Record<string, string>
  },
) {
  return authorizedJson<PortalCodeVersion>(
    `/api/v1/admin/portal-code-packages/${encodeURIComponent(packageId)}/versions/${encodeURIComponent(body.version)}/files`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    },
  )
}

export function importPortalCodeZip(
  packageId: number,
  version: string,
  manifest: Record<string, unknown>,
  file: File,
) {
  const body = new FormData()
  body.append('manifest', JSON.stringify(manifest))
  body.append('file', file)
  return authorizedJson<PortalCodeVersion>(
    `/api/v1/admin/portal-code-packages/${encodeURIComponent(packageId)}/versions/${encodeURIComponent(version)}/files`,
    { method: 'POST', body },
  )
}

export function portalCodeAction(
  packageId: number,
  action: 'scan' | 'publish' | 'revoke',
  versionId: number,
) {
  return authorizedJson(
    `/api/v1/admin/portal-code-packages/${encodeURIComponent(packageId)}/${action}/${encodeURIComponent(versionId)}`,
    { method: 'POST' },
  )
}

export function portalBatchData(
  siteKey: string,
  queries: Array<{ id: string; source: string; filters?: Record<string, string> }>,
) {
  return authorizedJson<{ results: Record<string, unknown>; revision: string }>(
    sitePath(siteKey, '/data/batch'),
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ queries }),
    },
  )
}

export interface PortalAsset {
  assetId: number
  assetKey: string
  assetType: string
  originalName: string
  mimeType: string
  sizeBytes: number
  checksum: string
  status: string
}

export function listPortalAssets(siteKey: string) {
  return authorizedJson<PortalAsset[]>(`/api/v1/admin/portal-assets/${encodeURIComponent(siteKey)}`)
}

export function uploadPortalAsset(siteKey: string, assetType: string, file: File) {
  const form = new FormData()
  form.append('file', file)
  return authorizedJson<PortalAsset & { url: string }>(
    `/api/v1/admin/portal-assets/${encodeURIComponent(siteKey)}?assetType=${encodeURIComponent(assetType)}`,
    { method: 'POST', body: form },
  )
}

export function deletePortalAsset(siteKey: string, assetId: number) {
  return authorizedJson<void>(`/api/v1/admin/portal-assets/${encodeURIComponent(siteKey)}/${assetId}`, {
    method: 'DELETE',
  })
}
