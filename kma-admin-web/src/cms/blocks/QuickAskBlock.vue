<script setup lang="ts">
import type { CmsBlockConfig } from '../../app/runtimeConfig'
defineProps<{ config: CmsBlockConfig; query: string }>()
const emit = defineEmits<{ 'update:query': [value: string]; ask: [] }>()
</script>

<template>
  <section class="cms-quick-ask">
    <div>
      <span>AI 知识助手</span>
      <h2>基于权威资料提问</h2>
    </div>
    <input
      :value="query"
      :placeholder="String(config.props?.placeholder || '输入党建知识问题')"
      aria-label="快速提问"
      @input="emit('update:query', ($event.target as HTMLInputElement).value)"
      @keydown.enter="emit('ask')"
    />
    <button @click="emit('ask')">开始提问</button>
  </section>
</template>
