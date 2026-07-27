<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PortalPageRenderer from '../../cms/PortalPageRenderer.vue'
import PageState from '../../components/PageState.vue'
import { usePortalSiteStore } from '../../stores/portalSite'
import { useSiteNavigation } from '../../composables/useSiteNavigation'
import type { PortalPageConfig } from '../../cms/siteConfig'

const route = useRoute()
const router = useRouter()
const portalSite = usePortalSiteStore()
const { sitePath, siteKey } = useSiteNavigation()
const query = ref('')
const page = ref<PortalPageConfig | null>(null)
const loading = ref(false)
const error = ref('')

watch(
  () => String(route.params.pageSlug || ''),
  async (pageSlug) => {
    if (!pageSlug) return
    loading.value = true
    error.value = ''
    try {
      await portalSite.load(siteKey.value, pageSlug)
      page.value = portalSite.bootstrap?.page || null
    } catch (reason) {
      error.value = reason instanceof Error ? reason.message : '页面加载失败'
    } finally {
      loading.value = false
    }
  },
  { immediate: true },
)

const data = computed(() => portalSite.bootstrap?.portalData)

function search() {
  void router.push({
    path: sitePath('/portal/library'),
    query: query.value.trim() ? { keyword: query.value.trim() } : {},
  })
}
</script>

<template>
  <PageState :loading="loading" :error="error" :empty="!page || !data">
    <PortalPageRenderer
      v-if="page && data && portalSite.bootstrap"
      v-model:query="query"
      :data="data"
      :bootstrap="portalSite.bootstrap"
      @search="search"
      @ask="router.push(sitePath('/portal/ask'))"
      @category="router.push({ path: sitePath('/portal/library'), query: { contentType: $event } })"
    />
  </PageState>
</template>
