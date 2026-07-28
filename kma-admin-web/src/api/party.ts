import type { components, operations, paths } from './generated/schema'
import { api, asList, asRecord, authorizedJson, unwrap } from './client'
import { getActivePortalPreviewVersion, getActivePortalSiteKey } from '../app/portalSiteContext'

export type PartyContent = components['schemas']['PartyContentView']
export type PartyContentRequest = components['schemas']['PartyContentRequest']
export type PartyContentMetadataRequest = components['schemas']['PartyContentMetadataRequest']
export type TopicRequest = components['schemas']['TopicRequest']
export type PortalConfigRequest = components['schemas']['PortalConfigRequest']
export type FavoriteRequest = components['schemas']['FavoriteRequest']
export type PortalContentQuery = NonNullable<operations['contents']['parameters']['query']>
type AdminContentsGet = NonNullable<paths['/api/v1/admin/contents']['get']>
export type AdminContentQuery = NonNullable<AdminContentsGet['parameters']['query']>

export interface PageResult<T> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
}

export interface PortalConfig {
  unitName: string
  helpText: string
  currentTopicCode: string
}

export interface PortalCategory {
  contentType: string
  name: string
  total: number
}

export interface PortalTopic {
  topicId?: number
  topicCode: string
  name: string
  description: string
  coverColor?: string
  sortOrder?: number
  enabled?: boolean
  featured?: boolean
}

export interface PortalListItem {
  favoriteId?: number
  docId?: number
  title: string
  favoriteType?: string
  documentNumber?: string
  issuingAuthority?: string
  readCount?: number
  lastReadAt?: string
}

export interface PortalHome {
  config: PortalConfig
  categories: PortalCategory[]
  recent: PartyContent[]
  topics: PortalTopic[]
  history: PortalListItem[]
  favorites: PortalListItem[]
}

function stringValue(record: Record<string, unknown>, camel: string, snake = camel) {
  const value = record[camel] ?? record[snake]
  return value === undefined || value === null ? '' : String(value)
}

function numberValue(record: Record<string, unknown>, camel: string, snake = camel) {
  const parsed = Number(record[camel] ?? record[snake])
  return Number.isFinite(parsed) ? parsed : 0
}

function toContent(value: unknown): PartyContent {
  return asRecord(value) as PartyContent
}

function toTopic(value: unknown): PortalTopic {
  const record = asRecord(value)
  return {
    topicId: numberValue(record, 'topicId', 'topic_id') || undefined,
    topicCode: stringValue(record, 'topicCode', 'topic_code'),
    name: stringValue(record, 'name'),
    description: stringValue(record, 'description'),
    coverColor: stringValue(record, 'coverColor', 'cover_color') || undefined,
    sortOrder: numberValue(record, 'sortOrder', 'sort_order'),
    enabled: record.enabled === undefined ? undefined : Boolean(record.enabled),
    featured: record.featured === undefined ? undefined : Boolean(record.featured),
  }
}

function toListItem(value: unknown): PortalListItem {
  const record = asRecord(value)
  return {
    favoriteId: numberValue(record, 'favoriteId', 'favorite_id') || undefined,
    docId: numberValue(record, 'docId', 'doc_id') || undefined,
    title: stringValue(record, 'title'),
    favoriteType: stringValue(record, 'favoriteType', 'favorite_type') || undefined,
    documentNumber: stringValue(record, 'documentNumber', 'document_number') || undefined,
    issuingAuthority: stringValue(record, 'issuingAuthority', 'issuing_authority') || undefined,
    readCount: numberValue(record, 'readCount', 'read_count'),
    lastReadAt: stringValue(record, 'lastReadAt', 'last_read_at') || undefined,
  }
}

function pageResult<T>(
  value: unknown,
  map: (item: unknown) => T,
  fallbackPage: number,
  fallbackPageSize: number,
): PageResult<T> {
  const record = asRecord(value)
  const list = asList(record.list).map(map)
  return {
    list,
    total: numberValue(record, 'total') || list.length,
    pageNum: numberValue(record, 'pageNum', 'page_num') || fallbackPage,
    pageSize: numberValue(record, 'pageSize', 'page_size') || fallbackPageSize,
  }
}

export function normalizePortalHome(raw: unknown): PortalHome {
  const value = asRecord(raw)
  const config = asRecord(value.config)
  return {
    config: {
      unitName: stringValue(config, 'unitName', 'unit_name') || '党建知识库',
      helpText: stringValue(config, 'helpText', 'help_text'),
      currentTopicCode: stringValue(config, 'currentTopicCode', 'current_topic_code'),
    },
    categories: asList(value.categories).map((item) => {
      const record = asRecord(item)
      return {
        contentType: stringValue(record, 'contentType', 'content_type'),
        name: stringValue(record, 'name'),
        total: numberValue(record, 'total'),
      }
    }),
    recent: asList(value.recent).map(toContent),
    topics: asList(value.topics).map(toTopic),
    history: asList(value.history).map(toListItem),
    favorites: asList(value.favorites).map(toListItem),
  }
}

export async function getPortalHome(): Promise<PortalHome> {
  const siteKey = getActivePortalSiteKey()
  const previewVersion = getActivePortalPreviewVersion()
  const bootstrap = asRecord(
    await authorizedJson(
      previewVersion
        ? `/api/v1/admin/portal-sites/${encodeURIComponent(siteKey)}/versions/${previewVersion}/preview/bootstrap?page=home`
        : `/api/v1/portal-sites/${encodeURIComponent(siteKey)}/bootstrap?page=home`,
    ),
  )
  return normalizePortalHome(bootstrap.portalData)
}

export async function getPortalContents(query: PortalContentQuery, signal?: AbortSignal) {
  const fallbackPage = query.pageNum || 1
  const fallbackPageSize = query.pageSize || 20
  const params = new URLSearchParams()
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') params.set(key, String(value))
  })
  const siteKey = encodeURIComponent(getActivePortalSiteKey())
  const previewVersion = getActivePortalPreviewVersion()
  const value = await authorizedJson(
    previewVersion
      ? `/api/v1/admin/portal-sites/${siteKey}/versions/${previewVersion}/preview/contents?${params}`
      : `/api/v1/portal-sites/${siteKey}/contents?${params}`,
    { signal },
  )
  return pageResult(value, toContent, fallbackPage, fallbackPageSize)
}

export function getPortalContent(contentId: number, location?: string, signal?: AbortSignal) {
  const query = location ? `?location=${encodeURIComponent(location)}` : ''
  const siteKey = encodeURIComponent(getActivePortalSiteKey())
  const previewVersion = getActivePortalPreviewVersion()
  return authorizedJson<PartyContent>(
    previewVersion
      ? `/api/v1/admin/portal-sites/${siteKey}/versions/${previewVersion}/preview/contents/${contentId}${query}`
      : `/api/v1/portal-sites/${siteKey}/contents/${contentId}${query}`,
    { signal },
  )
}

export async function getPortalTopics() {
  return asList(await unwrap(api.GET('/api/v1/portal/topics'))).map(toTopic)
}

export async function getAdminTopics() {
  return asList(await unwrap(api.GET('/api/v1/admin/topics'))).map(toTopic)
}

export async function getPortalFavorites(limit = 100) {
  return asList(await unwrap(api.GET('/api/v1/portal/favorites', { params: { query: { limit } } }))).map(
    toListItem,
  )
}

export async function getPortalHistory(limit = 100) {
  return asList(await unwrap(api.GET('/api/v1/portal/history', { params: { query: { limit } } }))).map(
    toListItem,
  )
}

export function addPortalFavorite(body: FavoriteRequest) {
  return unwrap(api.POST('/api/v1/portal/favorites', { body }))
}

export function removePortalFavorite(favoriteId: number) {
  return unwrap(api.DELETE('/api/v1/portal/favorites/{favoriteId}', { params: { path: { favoriteId } } }))
}

export async function getAdminContents(query: AdminContentQuery) {
  const fallbackPage = query.pageNum || 1
  const fallbackPageSize = query.pageSize || 20
  const value = await unwrap(api.GET('/api/v1/admin/contents', { params: { query } }))
  return pageResult(value, toContent, fallbackPage, fallbackPageSize)
}

export function getAdminContent(id: number) {
  return unwrap(api.GET('/api/v1/admin/contents/{id}', { params: { path: { id } } }))
}

export function createTextContent(body: PartyContentRequest) {
  return unwrap(api.POST('/api/v1/admin/contents/text', { body }))
}

export function createFileContent(query: operations['createFile']['parameters']['query'], file: File) {
  return unwrap(
    api.POST('/api/v1/admin/contents/file', {
      params: { query },
      body: { file: file as unknown as string },
      bodySerializer() {
        const body = new FormData()
        body.append('file', file)
        return body
      },
    }),
  )
}

export function updateContentMetadata(id: number, body: PartyContentMetadataRequest) {
  return unwrap(api.PUT('/api/v1/admin/contents/{id}', { params: { path: { id } }, body }))
}

export function applyContentAction(
  id: number,
  action: 'submit' | 'approve' | 'reject' | 'publish' | 'offline' | 'restore',
  note?: string,
) {
  const body = note ? JSON.stringify({ note }) : undefined
  return authorizedJson<void>(`/api/v1/admin/contents/${id}/${action}`, {
    method: 'POST',
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body,
  })
}

export function createTopic(body: TopicRequest) {
  return unwrap(api.POST('/api/v1/admin/topics', { body }))
}

export function updateTopic(id: number, body: TopicRequest) {
  return unwrap(api.PUT('/api/v1/admin/topics/{id}', { params: { path: { id } }, body }))
}

export async function getPortalConfig(): Promise<PortalConfig> {
  const config = asRecord(await unwrap(api.GET('/api/v1/admin/portal-config')))
  return {
    unitName: stringValue(config, 'unitName', 'unit_name'),
    helpText: stringValue(config, 'helpText', 'help_text'),
    currentTopicCode: stringValue(config, 'currentTopicCode', 'current_topic_code'),
  }
}

export function updatePortalConfig(body: PortalConfigRequest) {
  return unwrap(api.PUT('/api/v1/admin/portal-config', { body }))
}
