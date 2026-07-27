import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { cloneRuntimeConfig, defaultRuntimeConfig } from '../app/runtimeConfig'
import { useExperienceStore } from './experience'

describe('experience store', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  it('keeps core features enabled and applies preview configuration', () => {
    const store = useExperienceStore()
    store.initialize({ config: cloneRuntimeConfig(), issues: [], source: 'test' })
    expect(store.isFeatureEnabled('portal.home', true, false)).toBe(true)

    const preview = cloneRuntimeConfig()
    preview.experience.modules['portal.qa'] = false
    preview.experience.tokens.colorPrimary = '#8f1730'
    store.setPreview(preview)

    expect(store.isFeatureEnabled('portal.qa')).toBe(false)
    expect(store.experience.tokens.colorPrimary).toBe('#8f1730')
  })

  it('persists the global draft and ignores damaged local data', () => {
    const store = useExperienceStore()
    store.initialize({ config: cloneRuntimeConfig(), issues: [], source: 'test' })
    const draft = cloneRuntimeConfig()
    draft.revision = 'draft-one'

    store.saveDraft(draft)
    store.setPreview(null)
    expect(store.loadDraft()?.revision).toBe('draft-one')

    localStorage.setItem('kma:portal-experience:draft', '{')
    expect(store.loadDraft()).toBeNull()
    expect(store.issues[0]).toContain('草稿损坏')
  })

  it('resets previews on logout without replacing the global configuration', () => {
    const store = useExperienceStore()
    store.initialize({ config: cloneRuntimeConfig(), issues: [], source: 'test' })
    const preview = cloneRuntimeConfig()
    preview.revision = 'preview'
    store.setPreview(preview)

    store.resetPreview()
    expect(store.previewConfig).toBeNull()
    expect(store.globalConfig).toEqual(defaultRuntimeConfig)
  })

  it('restores the global draft explicitly', () => {
    const draft = cloneRuntimeConfig()
    draft.experience.template = 'reading-focus'
    draft.experience.pages.home.template = 'reading-focus'
    localStorage.setItem('kma:portal-experience:draft', JSON.stringify(draft))
    const store = useExperienceStore()
    store.initialize({ config: cloneRuntimeConfig(), issues: [], source: 'test' })

    store.loadDraft()

    expect(store.experience.template).toBe('reading-focus')
    expect(store.previewConfig?.revision).toBe(draft.revision)
  })
})
