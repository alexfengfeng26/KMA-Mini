import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  applyExperienceTheme,
  cloneRuntimeConfig,
  defaultRuntimeConfig,
  fetchRuntimeConfig,
  parseRuntimeConfig,
  runtimeDraftKey,
  type KmaRuntimeConfig,
  type PortalExperienceConfig,
  type RuntimeConfigResult,
} from '../app/runtimeConfig'

export const useExperienceStore = defineStore('experience', () => {
  const globalConfig = ref<KmaRuntimeConfig>(cloneRuntimeConfig())
  const previewConfig = ref<KmaRuntimeConfig | null>(null)
  const issues = ref<string[]>([])
  const initialized = ref(false)
  const activeConfig = computed(() => previewConfig.value || globalConfig.value || defaultRuntimeConfig)
  const experience = computed(() => activeConfig.value.experience)

  function apply(result?: RuntimeConfigResult) {
    if (result) issues.value = result.issues
    applyExperienceTheme(experience.value)
  }

  function initialize(result: RuntimeConfigResult) {
    globalConfig.value = result.config
    initialized.value = true
    apply(result)
  }

  function resetPreview() {
    previewConfig.value = null
    issues.value = []
    apply()
  }

  async function loadGlobal() {
    const result = await fetchRuntimeConfig('/config/kma-runtime.json')
    initialize(result)
    return result
  }

  function isFeatureEnabled(featureKey: string, core = false, defaultEnabled = true) {
    if (core) return true
    return experience.value.modules[featureKey] ?? defaultEnabled
  }

  function setPreview(config: KmaRuntimeConfig | null) {
    previewConfig.value = config ? cloneRuntimeConfig(config) : null
    apply()
  }

  function saveDraft(config: KmaRuntimeConfig) {
    localStorage.setItem(runtimeDraftKey(), JSON.stringify(config))
    setPreview(config)
  }

  function loadDraft() {
    const raw = localStorage.getItem(runtimeDraftKey())
    if (!raw) return null
    try {
      const result = parseRuntimeConfig(JSON.parse(raw), 'local-draft', globalConfig.value)
      issues.value = result.issues
      setPreview(result.config)
      return result.config
    } catch {
      issues.value = ['本地外观草稿损坏，已忽略']
      setPreview(null)
      return null
    }
  }

  function clearDraft() {
    localStorage.removeItem(runtimeDraftKey())
    setPreview(null)
  }

  function previewExperience(value: PortalExperienceConfig) {
    setPreview({
      schemaVersion: 1,
      revision: `preview-${Date.now()}`,
      experience: value,
    })
  }

  return {
    globalConfig,
    previewConfig,
    activeConfig,
    experience,
    issues,
    initialized,
    initialize,
    resetPreview,
    loadGlobal,
    isFeatureEnabled,
    setPreview,
    saveDraft,
    loadDraft,
    clearDraft,
    previewExperience,
  }
})
