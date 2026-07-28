import { defineStore } from 'pinia'
import { computed, onScopeDispose, ref } from 'vue'
import { api, unwrap } from '../api/client'
import type { components } from '../api/generated/schema'
import {
  bindBrowserSession,
  broadcastBrowserLogout,
  clearBrowserSession,
  onForeignSession,
} from '../security/browserSession'

export type UserInfo = components['schemas']['AuthTokenResponse'] & components['schemas']['KmaPrincipal']

export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserInfo | null>(null)
  const accessToken = ref(sessionStorage.getItem('kma_access_token') || '')
  const authenticated = computed(() => Boolean(accessToken.value))
  const permissions = computed(() => new Set(user.value?.permissions || []))
  function hasAnyPermission(required?: string[]) {
    if (!required?.length) return true
    return permissions.value.has('kma:admin') || required.some((value) => permissions.value.has(value))
  }
  function applyUser(value: UserInfo | null) {
    user.value = value
  }
  function setAccessToken(value?: string) {
    accessToken.value = value || ''
    if (value) sessionStorage.setItem('kma_access_token', value)
    else sessionStorage.removeItem('kma_access_token')
  }
  window.addEventListener('kma-auth-refreshed', (event) => {
    const detail = (event as CustomEvent<UserInfo>).detail
    if (detail) {
      const previous = user.value
      const authorizationChanged =
        Boolean(previous) &&
        (previous?.authorizationVersion !== detail.authorizationVersion ||
          JSON.stringify([...(previous?.permissions || [])].sort()) !==
            JSON.stringify([...(detail.permissions || [])].sort()) ||
          JSON.stringify([...(previous?.organizationCodes || [])].sort()) !==
            JSON.stringify([...(detail.organizationCodes || [])].sort()))
      accessToken.value = sessionStorage.getItem('kma_access_token') || ''
      applyUser(detail)
      if (authorizationChanged)
        window.dispatchEvent(
          new CustomEvent('kma-authorization-changed', {
            detail: { previous, current: detail },
          }),
        )
    }
  })
  window.addEventListener('kma-auth-cleared', () => {
    accessToken.value = ''
    user.value = null
  })
  const stopForeignSession = onForeignSession(() => {
    clearBrowserSession('当前浏览器已登录其他账号，本页面会话已退出。')
    if (window.location.pathname !== '/login') window.location.assign('/login')
  })
  onScopeDispose(stopForeignSession)
  async function login(username: string, password: string) {
    clearBrowserSession()
    user.value = null
    const data = await unwrap(api.POST('/api/v1/auth/login', { body: { username, password } }))
    if (!data.accessToken) throw new Error('登录响应缺少访问令牌')
    if (data.userId == null) {
      await revokeUnexpectedToken(data.accessToken)
      clearBrowserSession('登录身份异常，请重新登录。')
      throw new Error('INVALID_LOGIN_RESPONSE')
    }
    bindBrowserSession(data, true)
    setAccessToken(data.accessToken)
    sessionStorage.setItem('kma_must_change_password', String(data.mustChangePassword))
    applyUser(data)
  }
  async function logout() {
    try {
      await unwrap(api.POST('/api/v1/auth/logout'))
    } finally {
      broadcastBrowserLogout()
      clearBrowserSession()
      user.value = null
    }
  }
  async function ensureUser() {
    if (!user.value && authenticated.value) {
      const identity = (await unwrap(api.GET('/api/v1/auth/me'))) as UserInfo
      bindBrowserSession(identity)
      applyUser(identity)
    }
    return user.value
  }
  return {
    user,
    authenticated,
    accessToken,
    permissions,
    login,
    logout,
    ensureUser,
    hasAnyPermission,
    applyUser,
    setAccessToken,
  }
})

async function revokeUnexpectedToken(accessToken: string) {
  await fetch('/api/v1/auth/logout', {
    method: 'POST',
    credentials: 'include',
    headers: { Authorization: `Bearer ${accessToken}` },
  }).catch(() => undefined)
}
