<script setup lang="ts">
import EmptyState from './EmptyState.vue'

defineProps<{ loading?: boolean; error?: string; empty?: boolean; emptyText?: string }>()
</script>
<template>
  <div v-if="loading" class="skeleton-stack"><i v-for="n in 4" :key="n" /></div>
  <div v-else-if="error" class="state error">
    <strong>加载失败</strong>
    <p>{{ error }}</p>
    <slot name="action" />
  </div>
  <EmptyState v-else-if="empty" :description="emptyText">
    <template #action><slot name="action" /></template>
  </EmptyState>
  <slot v-else />
</template>
