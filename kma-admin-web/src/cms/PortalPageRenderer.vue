<script setup lang="ts">
import type { PortalHome } from '../api/party'
import CmsPageRendererV2 from './CmsPageRendererV2.vue'
import type { PortalBootstrap } from './siteConfig'
import { isLowCodePage } from './siteConfig'
import CmsPageRendererV3 from './v3/CmsPageRendererV3.vue'
import PortalThemeHost from './v4/PortalThemeHost.vue'

defineProps<{
  bootstrap: PortalBootstrap
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
  <PortalThemeHost v-if="bootstrap.schemaVersion === 4 && bootstrap.themeRuntime" :bootstrap="bootstrap" />
  <CmsPageRendererV3
    v-else-if="isLowCodePage(bootstrap.page)"
    :page="bootstrap.page"
    :bootstrap="bootstrap"
    :query="query"
    @update:query="emit('update:query', $event)"
    @search="emit('search')"
    @ask="emit('ask')"
    @category="emit('category', $event)"
  />
  <CmsPageRendererV2
    v-else-if="'regions' in bootstrap.page"
    :page="bootstrap.page"
    :data="data"
    :query="query"
    :bootstrap="bootstrap"
    :extensions="bootstrap.extensions"
    @update:query="emit('update:query', $event)"
    @search="emit('search')"
    @ask="emit('ask')"
    @category="emit('category', $event)"
  />
</template>
