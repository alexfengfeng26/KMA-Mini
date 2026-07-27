import { queryClient } from '../app/queryClient'

export interface BrowserSessionContext {
  userId: string
  username: string
  sessionNonce: string
}

interface SessionIdentity {
  userId?: string | number | null
  username?: string | null
}

interface SessionMessage {
  type: 'login' | 'logout'
  context: BrowserSessionContext
  sentAt: number
}

const USER_KEY = 'kma_session_user_id'
const USERNAME_KEY = 'kma_session_username'
const NONCE_KEY = 'kma_session_nonce'
const NOTICE_KEY = 'kma_auth_notice'
const FALLBACK_EVENT_KEY = 'kma_auth_session_event'
const CHANNEL_NAME = 'kma-auth-session'
const listeners = new Set<(message: SessionMessage) => void>()
let initialized = false
let channel: BroadcastChannel | null = null

function nonce() {
  return crypto.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`
}

export function getBrowserSession(): BrowserSessionContext | null {
  const userId = sessionStorage.getItem(USER_KEY) || ''
  const username = sessionStorage.getItem(USERNAME_KEY) || ''
  const sessionNonce = sessionStorage.getItem(NONCE_KEY) || ''
  return userId && sessionNonce ? { userId, username, sessionNonce } : null
}

export function sessionMatches(identity: SessionIdentity) {
  const current = getBrowserSession()
  if (!current) return false
  return current.userId === String(identity.userId ?? '')
}

export function bindBrowserSession(identity: SessionIdentity, broadcast = false) {
  const userId = String(identity.userId ?? '')
  if (!userId) throw new Error('登录响应缺少有效用户身份')
  const existing = getBrowserSession()
  const context: BrowserSessionContext = {
    userId,
    username: String(identity.username || ''),
    sessionNonce: existing?.userId === userId ? existing.sessionNonce : nonce(),
  }
  sessionStorage.setItem(USER_KEY, context.userId)
  sessionStorage.setItem(USERNAME_KEY, context.username)
  sessionStorage.setItem(NONCE_KEY, context.sessionNonce)
  if (broadcast) publishSession(context)
  return context
}

export function clearBrowserSession(notice?: string) {
  sessionStorage.removeItem('kma_access_token')
  sessionStorage.removeItem('kma_must_change_password')
  sessionStorage.removeItem(USER_KEY)
  sessionStorage.removeItem(USERNAME_KEY)
  sessionStorage.removeItem(NONCE_KEY)
  if (notice) sessionStorage.setItem(NOTICE_KEY, notice)
  queryClient.clear()
  window.dispatchEvent(new CustomEvent('kma-auth-cleared', { detail: { notice } }))
}

export function takeSessionNotice() {
  const notice = sessionStorage.getItem(NOTICE_KEY) || ''
  sessionStorage.removeItem(NOTICE_KEY)
  return notice
}

function receive(raw: unknown) {
  if (!raw || typeof raw !== 'object') return
  const message = raw as SessionMessage
  if (message.type !== 'login' || !message.context) return
  listeners.forEach((listener) => listener(message))
}

function initializeCoordination() {
  if (initialized) return
  initialized = true
  if ('BroadcastChannel' in window) {
    channel = new BroadcastChannel(CHANNEL_NAME)
    channel.addEventListener('message', (event) => receive(event.data))
  }
  window.addEventListener('storage', (event) => {
    if (event.key !== FALLBACK_EVENT_KEY || !event.newValue) return
    try {
      receive(JSON.parse(event.newValue))
    } catch {
      // Ignore malformed same-origin coordination messages.
    }
  })
}

function publishSession(context: BrowserSessionContext, type: SessionMessage['type'] = 'login') {
  const message: SessionMessage = { type, context, sentAt: Date.now() }
  channel?.postMessage(message)
  localStorage.setItem(FALLBACK_EVENT_KEY, JSON.stringify(message))
  localStorage.removeItem(FALLBACK_EVENT_KEY)
}

export function broadcastBrowserLogout() {
  const current = getBrowserSession()
  if (current) publishSession(current, 'logout')
}

export function onForeignSession(callback: (context: BrowserSessionContext) => void) {
  initializeCoordination()
  const listener = (message: SessionMessage) => {
    const current = getBrowserSession()
    if (!current) return
    const sameIdentity = current.userId === message.context.userId
    if ((message.type === 'login' && !sameIdentity) || (message.type === 'logout' && sameIdentity))
      callback(message.context)
  }
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export const browserSessionStorageKeys = {
  user: USER_KEY,
  username: USERNAME_KEY,
  nonce: NONCE_KEY,
}
