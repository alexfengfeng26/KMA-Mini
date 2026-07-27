<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageState from '../../components/PageState.vue'
import PortalPageRenderer from '../../cms/PortalPageRenderer.vue'
import { usePortalSiteStore } from '../../stores/portalSite'
import { useSiteNavigation } from '../../composables/useSiteNavigation'

const router = useRouter()
const portalSite = usePortalSiteStore()
const { sitePath } = useSiteNavigation()
const query = ref('')
const data = computed(() => portalSite.bootstrap?.portalData)
const page = computed(() => portalSite.bootstrap?.page)

function goLibrary(contentType?: string) {
  void router.push({
    path: sitePath('/portal/library'),
    query: {
      ...(query.value.trim() ? { keyword: query.value.trim() } : {}),
      ...(contentType ? { contentType } : {}),
    },
  })
}

function ask() {
  void router.push({
    path: sitePath('/portal/ask'),
    query: query.value.trim() ? { query: query.value.trim() } : {},
  })
}
</script>

<template>
  <PageState :loading="portalSite.loading" :error="portalSite.error" :empty="false">
    <div v-if="data && page" class="portal-home">
      <PortalPageRenderer
        v-if="portalSite.bootstrap"
        v-model:query="query"
        :data="data"
        :bootstrap="portalSite.bootstrap"
        @search="goLibrary()"
        @ask="ask"
        @category="goLibrary"
      />
    </div>
  </PageState>
</template>
