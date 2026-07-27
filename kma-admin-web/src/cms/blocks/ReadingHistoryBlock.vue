<script setup lang="ts">
import { computed } from 'vue'
import type { PortalHome } from '../../api/party'
import type { CmsBlockConfig } from '../../app/runtimeConfig'
import { useSiteNavigation } from '../../composables/useSiteNavigation'
const props = defineProps<{ config: CmsBlockConfig; data: PortalHome }>()
const { sitePath } = useSiteNavigation()
const items = computed(() => props.data.history.slice(0, Number(props.config.props?.limit || 5)))
</script>

<template>
  <section class="cms-personal-card cms-side-block">
    <h3>最近阅读</h3>
    <router-link
      v-for="item in items"
      :key="item.docId"
      :to="sitePath('/portal/content/:contentId', { contentId: Number(item.docId) })"
    >
      {{ item.title }}
    </router-link>
    <p v-if="!items.length">尚无阅读记录</p>
  </section>
</template>
