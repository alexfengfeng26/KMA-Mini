<script setup lang="ts">
interface DocumentMetaItem {
  label: string
  value?: string | number | null
}

defineProps<{ items: DocumentMetaItem[] }>()
</script>

<template>
  <dl class="document-meta">
    <template v-for="item in items" :key="item.label">
      <dt>{{ item.label }}</dt>
      <dd>{{ item.value ?? '—' }}</dd>
    </template>
  </dl>
</template>
