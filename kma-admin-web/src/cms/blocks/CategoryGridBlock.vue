<script setup lang="ts">
import { computed } from 'vue'
import type { PortalHome } from '../../api/party'
import type { CmsBlockConfig } from '../../app/runtimeConfig'
import { PARTY_CONTENT_CATEGORIES } from '../../domain/partyKnowledge'
import { useSiteNavigation } from '../../composables/useSiteNavigation'

const props = defineProps<{ config: CmsBlockConfig; data: PortalHome }>()
const { sitePath } = useSiteNavigation()
const emit = defineEmits<{ category: [contentType: string] }>()
const descriptions = new Map<string, string>(
  PARTY_CONTENT_CATEGORIES.map((item) => [item.value, item.description]),
)
const columns = computed(() => Number(props.config.props?.columns || 5))
</script>

<template>
  <section class="portal-section cms-section">
    <div class="portal-section-title">
      <div>
        <span>知识分类</span>
        <h2>五类权威资料入口</h2>
      </div>
      <router-link :to="sitePath('/portal/library')">查看全部资料 →</router-link>
    </div>
    <div class="portal-category-grid" :style="{ '--cms-category-columns': columns }">
      <button
        v-for="item in data.categories"
        :key="item.contentType"
        class="portal-category-card"
        @click="emit('category', item.contentType)"
      >
        <span>{{ String(item.total).padStart(2, '0') }}</span>
        <h3>{{ item.name }}</h3>
        <p>{{ descriptions.get(item.contentType) }}</p>
        <i>进入分类 →</i>
      </button>
    </div>
  </section>
</template>
