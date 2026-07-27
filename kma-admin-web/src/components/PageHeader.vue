<script setup lang="ts">
defineProps<{ eyebrow?: string; title: string; description?: string; compact?: boolean }>()
</script>

<template>
  <header class="page-header" :class="{ 'page-header--compact': compact }">
    <div>
      <span v-if="eyebrow" class="eyebrow">{{ eyebrow }}</span>
      <h2>{{ title }}</h2>
      <p v-if="description" class="muted">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="page-header-actions"><slot name="actions" /></div>
  </header>
</template>
