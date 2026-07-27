<script setup lang="ts">
import { computed } from 'vue'
import type { PortalHome } from '../../api/party'
import type { CmsBlockConfig } from '../../app/runtimeConfig'
import { useSiteNavigation } from '../../composables/useSiteNavigation'
const props = defineProps<{ config: CmsBlockConfig; data: PortalHome }>()
const { sitePath } = useSiteNavigation()
const items = computed(() => props.data.favorites.slice(0, Number(props.config.props?.limit || 5)))
</script>

<template>
  <section class="cms-personal-card cms-side-block">
    <h3>我的收藏</h3>
    <router-link
      v-for="item in items"
      :key="item.favoriteId"
      :to="
        item.docId
          ? sitePath('/portal/content/:contentId', { contentId: item.docId })
          : sitePath('/portal/favorites')
      "
    >
      {{ item.title }}
    </router-link>
    <p v-if="!items.length">尚无收藏内容</p>
  </section>
</template>
