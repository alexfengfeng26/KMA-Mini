<script setup lang="ts">
import type { PortalHome } from '../../api/party'
import type { CmsBlockConfig } from '../../app/runtimeConfig'

const props = defineProps<{ config: CmsBlockConfig; data: PortalHome; query: string }>()
const emit = defineEmits<{
  'update:query': [value: string]
  search: []
  ask: []
}>()

const placeholder = String(props.config.props?.placeholder || '输入文件标题、文号、关键词或党建问题')
</script>

<template>
  <section class="portal-hero">
    <span class="portal-kicker">权威资料检索</span>
    <p>{{ data.config.helpText || '从已发布、有效且有权访问的党建资料中检索。' }}</p>
    <div class="portal-hero-search">
      <input
        :value="query"
        :placeholder="placeholder"
        aria-label="权威资料检索"
        @input="emit('update:query', ($event.target as HTMLInputElement).value)"
        @keydown.enter="emit('search')"
      />
      <button class="primary" @click="emit('search')">查权威文件</button>
      <button v-if="config.props?.showAsk !== false" @click="emit('ask')">问党建知识</button>
    </div>
    <div class="portal-search-hint">默认优先展示现行有效与即将生效文件；历史资料需主动筛选。</div>
  </section>
</template>
