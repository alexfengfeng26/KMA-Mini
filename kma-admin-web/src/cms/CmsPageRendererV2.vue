<script setup lang="ts">
import { computed } from 'vue'
import type { PortalHome } from '../api/party'
import PortalExtensionFrame from './PortalExtensionFrame.vue'
import type {
  CmsBlockConfigV2,
  CmsPageConfigV2,
  CmsRegion,
  PortalBootstrap,
  ResolvedPortalExtension,
} from './siteConfig'
import { cmsV2BlockRegistry } from './blockDefinitions'

const props = defineProps<{
  page: CmsPageConfigV2
  data: PortalHome
  query: string
  bootstrap?: PortalBootstrap
  extensions?: ResolvedPortalExtension[]
}>()

const emit = defineEmits<{
  'update:query': [value: string]
  search: []
  ask: []
  category: [contentType: string]
}>()

const orderedRegions = computed(() =>
  (['header', 'main', 'sidebar', 'footer'] as CmsRegion[])
    .map((region) => ({ region, blocks: props.page.regions[region] || [] }))
    .filter((item) => item.blocks.length),
)

function blockStyle(block: CmsBlockConfigV2) {
  return { '--cms-block-span': String(Math.min(12, Math.max(1, block.span || 12))) }
}

function regionExtensions(region: CmsRegion) {
  return (props.extensions || []).filter((extension) => (extension.region || 'main') === region)
}
</script>

<template>
  <div class="cms-page-v2" :class="[`cms-page-v2--${page.layout}`]" :data-page-slug="page.slug">
    <section
      v-for="section in orderedRegions"
      :key="section.region"
      class="cms-region"
      :class="`cms-region--${section.region}`"
      :aria-label="`${section.region} 区域`"
    >
      <div
        v-for="block in section.blocks"
        v-show="block.enabled"
        :key="block.id"
        class="cms-block cms-block-v2"
        :class="[`cms-block--${block.type}`, `cms-block--${block.variant || 'default'}`]"
        :style="blockStyle(block)"
        :data-cms-block="block.type"
      >
        <component
          :is="cmsV2BlockRegistry[block.type]"
          :config="block"
          :data="data"
          :query="query"
          @update:query="emit('update:query', $event)"
          @search="emit('search')"
          @ask="emit('ask')"
          @category="emit('category', $event)"
        />
      </div>
      <template v-if="bootstrap">
        <PortalExtensionFrame
          v-for="extension in regionExtensions(section.region)"
          :key="`${extension.extensionId}@${extension.version}:${extension.slotKey}`"
          class="cms-extension-slot"
          :style="{ '--cms-block-span': '12' }"
          :extension="extension"
          :bootstrap="bootstrap"
        />
      </template>
    </section>
  </div>
</template>
