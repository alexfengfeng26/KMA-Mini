import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import { beforeEach, describe, expect, it } from 'vitest'
import { permissionDirective } from './permission'
import { useAuthStore } from '../stores/auth'

describe('v-permission', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('hides unauthorized actions and allows a matching permission', () => {
    const auth = useAuthStore()
    auth.applyUser({ permissions: ['document:read'] } as Parameters<typeof auth.applyUser>[0])
    const element = document.createElement('button')
    const mounted = permissionDirective.mounted as unknown as (
      element: HTMLElement,
      binding: { value: string },
    ) => void
    mounted(element, { value: 'document:delete' })
    expect(element.style.display).toBe('none')
    mounted(element, { value: 'document:read' })
    expect(element.style.display).toBe('')
  })

  it('reacts immediately when refreshed permissions change', async () => {
    const auth = useAuthStore()
    auth.applyUser({ permissions: ['document:read'] } as Parameters<typeof auth.applyUser>[0])
    const element = document.createElement('button')
    const mounted = permissionDirective.mounted as unknown as (
      element: HTMLElement,
      binding: { value: string },
    ) => void
    mounted(element, { value: 'document:delete' })
    expect(element.style.display).toBe('none')

    auth.applyUser({ permissions: ['document:delete'] } as Parameters<typeof auth.applyUser>[0])
    await nextTick()

    expect(element.style.display).toBe('')
    expect(element.getAttribute('aria-hidden')).toBe('false')
  })

  it('supports permission arrays, binding updates and scope cleanup', async () => {
    const auth = useAuthStore()
    auth.applyUser({ permissions: ['qa:use'] } as Parameters<typeof auth.applyUser>[0])
    const element = document.createElement('button')
    const mounted = permissionDirective.mounted as unknown as (
      element: HTMLElement,
      binding: { value: string | string[] },
    ) => void
    const updated = permissionDirective.updated as unknown as (
      element: HTMLElement,
      binding: { value: string | string[] },
    ) => void
    const unmounted = permissionDirective.unmounted as unknown as (element: HTMLElement) => void

    mounted(element, { value: ['document:read', 'qa:use'] })
    expect(element.style.display).toBe('')

    updated(element, { value: 'document:delete' })
    await nextTick()
    expect(element.style.display).toBe('none')

    unmounted(element)
    auth.applyUser({ permissions: ['document:delete'] } as Parameters<typeof auth.applyUser>[0])
    await nextTick()
    expect(element.style.display).toBe('none')
  })
})
