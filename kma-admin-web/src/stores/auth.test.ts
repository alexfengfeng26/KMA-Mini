import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  unwrap: vi.fn(),
}))

vi.mock('../api/client', () => ({
  api: { GET: vi.fn(), POST: vi.fn() },
  unwrap: mocks.unwrap,
}))

import { useAuthStore } from './auth'

describe('auth store', () => {
  beforeEach(() => {
    sessionStorage.clear()
    vi.restoreAllMocks()
    mocks.unwrap.mockReset()
    setActivePinia(createPinia())
  })

  it('updates token, identity and permissions reactively', () => {
    const auth = useAuthStore()
    auth.setAccessToken('token')
    auth.applyUser({ username: 'reader', permissions: ['document:read'] })
    expect(auth.authenticated).toBe(true)
    expect(auth.hasAnyPermission(['document:read'])).toBe(true)
    expect(auth.hasAnyPermission(['document:delete'])).toBe(false)

    window.dispatchEvent(
      new CustomEvent('kma-auth-refreshed', {
        detail: { username: 'admin', permissions: ['kma:admin'] },
      }),
    )
    expect(auth.hasAnyPermission(['document:delete'])).toBe(true)

    window.dispatchEvent(new Event('kma-auth-cleared'))
    expect(auth.authenticated).toBe(false)
    expect(auth.user).toBeNull()
  })

  it('logs in, loads the current user and always clears logout state', async () => {
    mocks.unwrap
      .mockResolvedValueOnce({
        accessToken: 'login-token',
        mustChangePassword: true,
        userId: 1,
        username: 'admin',
        permissions: ['kma:admin'],
      })
      .mockResolvedValueOnce({
        userId: 1,
        username: 'admin',
        permissions: ['kma:admin'],
      })
      .mockResolvedValueOnce(undefined)
    const auth = useAuthStore()
    await auth.login('admin', 'password')
    expect(sessionStorage.getItem('kma_access_token')).toBe('login-token')
    auth.applyUser(null)
    await expect(auth.ensureUser()).resolves.toMatchObject({ username: 'admin' })
    await auth.logout()
    expect(auth.authenticated).toBe(false)
  })

  it('does not retain a local login response without user id', async () => {
    const logout = vi.spyOn(window, 'fetch').mockResolvedValue(new Response(null, { status: 200 }))
    mocks.unwrap.mockResolvedValueOnce({
      accessToken: 'unexpected-token',
      userId: null,
      username: 'reader',
    })
    const auth = useAuthStore()

    await expect(auth.login('reader', 'password')).rejects.toThrow('INVALID_LOGIN_RESPONSE')
    expect(auth.authenticated).toBe(false)
    expect(sessionStorage.getItem('kma_access_token')).toBeNull()
    expect(logout).toHaveBeenCalledOnce()
  })
})
