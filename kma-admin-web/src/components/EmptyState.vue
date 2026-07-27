<script setup lang="ts">
withDefaults(
  defineProps<{
    title?: string
    description?: string
  }>(),
  {
    title: '暂时没有数据',
    description: '创建第一条记录后，这里会展示运行详情。',
  },
)
</script>

<template>
  <div class="state empty-state" role="status">
    <strong>{{ title }}</strong>
    <p>{{ description }}</p>
    <slot name="action" />
  </div>
</template>
