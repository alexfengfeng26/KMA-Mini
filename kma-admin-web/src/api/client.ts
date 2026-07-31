import createClient from 'openapi-fetch'
import type { components, paths } from './generated/schema'
import { bindBrowserSession, clearBrowserSession } from '../security/browserSession'

type ApiEnvelope = { code?: number; message?: string; data?: unknown; traceId?: string }
type RequestResult<T extends ApiEnvelope> = Promise<{ data?: T; error?: unknown; response: Response }>
type Payload<T extends ApiEnvelope> = T extends { data?: infer D } ? NonNullable<D> : never

let refreshPromise: Promise<string | null> | null = null
const terminalAuthorizationErrors = new Set(['ACCOUNT_DISABLED', 'REFRESH_TOKEN_REUSED'])
const publicAuthPaths = new Set(['/api/v1/auth/login', '/api/v1/auth/refresh'])
const PROACTIVE_REFRESH_SECONDS = 60

function isPublicAuthRequest(request: Request) {
  return publicAuthPaths.has(new URL(request.url, window.location.origin).pathname)
}

function tokenExpiresInSeconds(token: string): number | null {
  try {
    const payload = token.split('.')[1]
    if (!payload) return null
    const decoded = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')))
    const exp = decoded?.exp
    if (typeof exp !== 'number') return null
    return Math.max(0, Math.floor(exp - Date.now() / 1000))
  } catch {
    return null
  }
}

function shouldRefreshProactively(token: string): boolean {
  const remaining = tokenExpiresInSeconds(token)
  return remaining !== null && remaining <= PROACTIVE_REFRESH_SECONDS
}

async function refreshAccessToken(): Promise<string | null> {
  const response = await window.fetch('/api/v1/auth/refresh', {
    method: 'POST',
    credentials: 'include',
    headers: { Accept: 'application/json' },
  })
  if (!response.ok) return null
  const envelope = (await response.json()) as components['schemas']['ApiResultAuthTokenResponse']
  const identity = envelope.data
  if (identity?.userId) bindBrowserSession(identity)
  const token = identity?.accessToken
  if (token) {
    sessionStorage.setItem('kma_access_token', token)
    window.dispatchEvent(new CustomEvent('kma-auth-refreshed', { detail: identity }))
  }
  return token || null
}

async function authorizationError(response: Response) {
  try {
    const payload = (await response.clone().json()) as ApiEnvelope
    return payload.message || ''
  } catch {
    return ''
  }
}

function clearSession() {
  clearBrowserSession()
}

export async function authorizedFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  const resolvedInput =
    typeof input === 'string' || input instanceof URL ? new URL(String(input), window.location.origin) : input
  const original = resolvedInput instanceof Request ? resolvedInput : new Request(resolvedInput, init)
  let currentToken = sessionStorage.getItem('kma_access_token')

  // 在 token 即将过期前主动刷新，避免请求过程中出现 401 重试。
  if (currentToken && !isPublicAuthRequest(original) && shouldRefreshProactively(currentToken)) {
    refreshPromise ??= refreshAccessToken().finally(() => {
      refreshPromise = null
    })
    const refreshed = await refreshPromise
    if (refreshed) currentToken = refreshed
  }

  const headers = new Headers(original.headers)
  if (currentToken && !headers.has('Authorization') && !isPublicAuthRequest(original)) {
    headers.set('Authorization', `Bearer ${currentToken}`)
  }
  const request = new Request(original, { headers })
  const retry = request.clone()
  const response = await window.fetch(request)
  if (response.status !== 401 || isPublicAuthRequest(request)) return response

  const authorizationCode = await authorizationError(response)
  if (terminalAuthorizationErrors.has(authorizationCode)) {
    clearBrowserSession()
    if (window.location.pathname !== '/login') window.location.assign('/login')
    return response
  }

  refreshPromise ??= refreshAccessToken().finally(() => {
    refreshPromise = null
  })
  const token = await refreshPromise
  if (!token) {
    clearSession()
    if (window.location.pathname !== '/login') window.location.assign('/login')
    return response
  }
  const retryHeaders = new Headers(retry.headers)
  retryHeaders.set('Authorization', `Bearer ${token}`)
  const retried = await window.fetch(new Request(retry, { headers: retryHeaders }))
  if (retried.status === 401) {
    clearSession()
    if (window.location.pathname !== '/login') window.location.assign('/login')
  }
  return retried
}

export const api = createClient<paths>({ baseUrl: '', credentials: 'include', fetch: authorizedFetch })
api.use({
  onRequest({ request }) {
    const token = sessionStorage.getItem('kma_access_token')
    if (token && !isPublicAuthRequest(request)) request.headers.set('Authorization', `Bearer ${token}`)
    return request
  },
})

export async function unwrap<T extends ApiEnvelope>(request: RequestResult<T>): Promise<Payload<T>> {
  const { data, error, response } = await request
  if (!response.ok || error || !data) {
    const candidate = error as ApiEnvelope | undefined
    const failure = new Error(candidate?.message || `请求失败 (${response.status})`)
    Object.assign(failure, { status: response.status, traceId: candidate?.traceId })
    throw failure
  }
  if (data.code !== undefined && data.code !== 0 && data.code !== 200) {
    const failure = new Error(data.message || '业务请求失败')
    Object.assign(failure, { status: data.code, traceId: data.traceId })
    throw failure
  }
  return data.data as Payload<T>
}

export async function authorizedJson<T = unknown>(
  input: RequestInfo | URL,
  init: RequestInit = {},
): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  const token = sessionStorage.getItem('kma_access_token')
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await authorizedFetch(input, { ...init, headers, credentials: 'include' })
  const isJson = response.headers.get('content-type')?.includes('application/json')
  const envelope = isJson ? ((await response.json()) as ApiEnvelope) : undefined
  if (!response.ok || !envelope) {
    const failure = new Error(envelope?.message || `请求失败 (${response.status})`)
    Object.assign(failure, { status: response.status, traceId: envelope?.traceId })
    throw failure
  }
  if (envelope.code !== undefined && envelope.code !== 0 && envelope.code !== 200) {
    const failure = new Error(envelope.message || '业务请求失败')
    Object.assign(failure, { status: envelope.code, traceId: envelope.traceId })
    throw failure
  }
  return envelope.data as T
}

export function errorMessage(error: unknown, fallback: string) {
  const message = error instanceof Error && error.message ? error.message : fallback
  const traceId = error && typeof error === 'object' && 'traceId' in error ? String(error.traceId || '') : ''
  return traceId ? `${message}（TraceId: ${traceId}）` : message
}

export function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' ? (value as Record<string, unknown>) : {}
}

export function asList<T = unknown>(value: unknown): T[] {
  return Array.isArray(value) ? (value as T[]) : []
}

export function pageItems<T = unknown>(value: unknown): T[] {
  return asList<T>(asRecord(value).list)
}
