<script setup lang="ts">
import { computed } from 'vue'
import type { PortalHome } from '../api/party'
import type { CmsBlockConfig } from '../app/runtimeConfig'
import { cmsBlockRegistry } from './blockRegistry'

const props = defineProps<{
  block: CmsBlockConfig
  data: PortalHome
  query: string
}>()

const emit = defineEmits<{
  'update:query': [value: string]
  search: []
  ask: []
  category: [contentType: string]
}>()

const component = computed(() => cmsBlockRegistry[props.block.type])
</script>

<template>
  <div
    v-if="block.enabled"
    class="cms-block"
    :class="[`cms-block--${block.type}`, `cms-block--${block.variant || 'default'}`]"
    :data-cms-block="block.type"
  >
    <component
      :is="component"
      :config="block"
      :data="data"
      :query="query"
      @update:query="emit('update:query', $event)"
      @search="emit('search')"
      @ask="emit('ask')"
      @category="emit('category', $event)"
    />
  </div>
</template>
