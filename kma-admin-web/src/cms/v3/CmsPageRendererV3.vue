<script setup lang="ts">
import type { LowCodePage, PortalBootstrap, PortalBreakpoint, PortalCoreComponent } from '../siteConfig'
import LowCodeNode from './LowCodeNode.vue'

withDefaults(
  defineProps<{
    page: LowCodePage
    bootstrap: PortalBootstrap
    query: string
    breakpoint?: PortalBreakpoint
    coreComponent?: PortalCoreComponent
  }>(),
  { breakpoint: 'desktop' },
)

const emit = defineEmits<{
  'update:query': [value: string]
  search: []
  ask: []
  category: [contentType: string]
  action: [payload: { nodeId: string; action: string; value?: unknown }]
}>()
</script>

<template>
  <div class="cms-page-v3" :data-page-slug="page.slug">
    <LowCodeNode
      :node="page.root"
      :breakpoint="breakpoint"
      :bootstrap="bootstrap"
      :symbols="bootstrap.symbols"
      :query="query"
      :core-component="coreComponent"
      @update:query="emit('update:query', $event)"
      @search="emit('search')"
      @ask="emit('ask')"
      @category="emit('category', $event)"
      @action="emit('action', $event)"
    >
      <template #core>
        <slot name="core" />
      </template>
    </LowCodeNode>
  </div>
</template>
