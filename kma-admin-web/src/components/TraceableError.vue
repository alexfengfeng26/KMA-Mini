<script setup lang="ts">
defineProps<{ message: string; traceId?: string }>()
</script>

<template>
  <div class="state error" role="alert">
    <strong>加载失败</strong>
    <p>{{ message }}</p>
    <small v-if="traceId">追踪编号：{{ traceId }}</small>
    <slot name="action" />
  </div>
</template>
