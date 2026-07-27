<script setup lang="ts">
import type { PortalHome } from '../api/party'
import type { CmsPageConfig } from '../app/runtimeConfig'
import BlockRenderer from './BlockRenderer.vue'

defineProps<{
  page: CmsPageConfig
  data: PortalHome
  query: string
}>()

const emit = defineEmits<{
  'update:query': [value: string]
  search: []
  ask: []
  category: [contentType: string]
}>()
</script>

<template>
  <div class="cms-template" :class="`cms-template--${page.template}`" :data-template="page.template">
    <BlockRenderer
      v-for="block in page.blocks"
      :key="block.id"
      :block="block"
      :data="data"
      :query="query"
      @update:query="emit('update:query', $event)"
      @search="emit('search')"
      @ask="emit('ask')"
      @category="emit('category', $event)"
    />
  </div>
</template>
