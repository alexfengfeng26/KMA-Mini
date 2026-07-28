<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { usePortalSiteStore } from '../../stores/portalSite'
import { portalSitePath } from '../../security/siteRoute'
import { isLowCodePage, type PortalCoreComponent } from '../siteConfig'
import CmsPageRendererV3 from './CmsPageRendererV3.vue'

const props = defineProps<{ coreComponent: PortalCoreComponent }>()
const portalSite = usePortalSiteStore()
const router = useRouter()
const lowCodePage = computed(() => {
  const bootstrap = portalSite.bootstrap
  return bootstrap && isLowCodePage(bootstrap.page) ? bootstrap.page : undefined
})
function leavePreview() {
  const bootstrap = portalSite.bootstrap
  const page = lowCodePage.value
  if (!bootstrap || !page) return
  const path = ['home', 'library', 'ask', 'topics', 'favorites', 'profile'].includes(page.kind)
    ? `/portal/${page.kind}`
    : `/portal/page/${page.slug}`
  void router.replace(portalSitePath(bootstrap.site.siteKey, path))
}
</script>

<template>
  <aside v-if="portalSite.bootstrap?.preview" class="portal-preview-banner" role="status">
    <span>预览中 · V{{ portalSite.bootstrap.previewVersion || portalSite.bootstrap.publishedVersion }}</span>
    <button type="button" @click="leavePreview">返回已发布门户</button>
  </aside>
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

<style scoped>
.portal-preview-banner {
  position: sticky;
  z-index: 30;
  top: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px max(20px, calc((100vw - 1480px) / 2));
  color: #173a5e;
  background: #e7f2ff;
  border-bottom: 1px solid #b8d9fa;
  font-size: 14px;
  font-weight: 650;
}

.portal-preview-banner button {
  padding: 5px 10px;
  color: #0d4f8b;
  background: #fff;
  border: 1px solid #8fc0ed;
  border-radius: 6px;
  cursor: pointer;
}

.portal-preview-banner button:focus-visible {
  outline: 2px solid #0d69b3;
  outline-offset: 2px;
}
</style>
