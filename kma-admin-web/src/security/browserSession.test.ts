import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  bindBrowserSession,
  browserSessionStorageKeys,
  clearBrowserSession,
  getBrowserSession,
  sessionMatches,
} from './browserSession'

describe('browser session', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  it('binds a tab to a user while preserving the nonce for the same identity', () => {
    const first = bindBrowserSession({ userId: 12, username: 'reader' })
    const second = bindBrowserSession({ userId: 12, username: 'reader' })
    expect(second.sessionNonce).toBe(first.sessionNonce)
    expect(sessionMatches({ userId: '12' })).toBe(true)
    expect(getBrowserSession()).toEqual(second)
  })

  it('rejects incomplete identities and clears all tab state', () => {
    expect(() => bindBrowserSession({ userId: null })).toThrow()
    bindBrowserSession({ userId: 1 })
    sessionStorage.setItem('kma_access_token', 'token')
    const listener = vi.fn()
    window.addEventListener('kma-auth-cleared', listener, { once: true })
    clearBrowserSession()
    expect(sessionStorage.getItem(browserSessionStorageKeys.user)).toBeNull()
    expect(sessionStorage.getItem('kma_access_token')).toBeNull()
    expect(listener).toHaveBeenCalledOnce()
  })
})
