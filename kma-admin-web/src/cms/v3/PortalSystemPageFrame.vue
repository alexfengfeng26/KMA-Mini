<script setup lang="ts">
import { computed } from 'vue'
import { usePortalSiteStore } from '../../stores/portalSite'
import { isLowCodePage, type PortalCoreComponent } from '../siteConfig'
import CmsPageRendererV3 from './CmsPageRendererV3.vue'

const props = defineProps<{ coreComponent: PortalCoreComponent }>()
const portalSite = usePortalSiteStore()
const lowCodePage = computed(() => {
  const bootstrap = portalSite.bootstrap
  return bootstrap && isLowCodePage(bootstrap.page) ? bootstrap.page : undefined
})
</script>

<template>
  <CmsPageRendererV3
    v-if="portalSite.bootstrap && lowCodePage"
    :page="lowCodePage"
    :bootstrap="portalSite.bootstrap"
    :query="''"
    :core-component="props.coreComponent"
  >
    <template #core>
      <slot />
    </template>
  </CmsPageRendererV3>
  <slot v-else />
</template>
