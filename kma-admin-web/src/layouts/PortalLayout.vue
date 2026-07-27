<script setup lang="ts">
import '../styles/portal-tokens.css'
import '../styles/portal-layout.css'
import '../styles/portal-pages.css'
import '../styles/portal-visual-packs.css'
import '../styles/portal-low-code.css'
import { computed, watchEffect } from 'vue'
import { useExperienceStore } from '../stores/experience'
import { usePortalSiteStore } from '../stores/portalSite'
import { loadPortalTemplateStyle } from '../templates/registry'
import type { PortalSiteConfigV3 } from '../cms/siteConfig'
import LowCodeNode from '../cms/v3/LowCodeNode.vue'
import PortalNavigationWidget from '../cms/v3/PortalNavigationWidget.vue'
import PortalAccountWidget from '../cms/v3/PortalAccountWidget.vue'

const experience = useExperienceStore()
const portalSite = usePortalSiteStore()
watchEffect(() => {
  void loadPortalTemplateStyle(experience.experience.template)
})
const v3Bootstrap = computed(() => {
  const bootstrap = portalSite.bootstrap
  return bootstrap?.schemaVersion === 3 ? bootstrap : undefined
})
const v3Shell = computed(() => v3Bootstrap.value?.shell as PortalSiteConfigV3['shell'] | undefined)
</script>

<template>
  <div class="portal-shell">
    <header class="portal-header">
      <LowCodeNode
        v-if="v3Bootstrap && v3Shell"
        :node="v3Shell.header"
        :bootstrap="v3Bootstrap"
        :symbols="v3Bootstrap.symbols"
        :query="''"
        core-component="portal-navigation"
      >
        <template #core><PortalNavigationWidget /></template>
      </LowCodeNode>
      <PortalNavigationWidget v-else />
    </header>
    <main class="portal-main"><router-view /></main>
    <LowCodeNode
      v-if="v3Bootstrap && v3Shell"
      :node="v3Shell.footer"
      :bootstrap="v3Bootstrap"
      :symbols="v3Bootstrap.symbols"
      :query="''"
      core-component="account-entry"
    >
      <template #core><PortalAccountWidget /></template>
    </LowCodeNode>
    <PortalAccountWidget v-else />
  </div>
</template>
