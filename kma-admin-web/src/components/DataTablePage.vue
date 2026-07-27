<script setup lang="ts">
import AppPagination from './AppPagination.vue'
import FilterBar from './FilterBar.vue'
import PageHeader from './PageHeader.vue'
import PageState from './PageState.vue'

withDefaults(
  defineProps<{
    title: string
    eyebrow?: string
    description?: string
    loading?: boolean
    error?: string
    empty?: boolean
    emptyText?: string
    page: number
    pageSize: number
    total: number
  }>(),
  {
    eyebrow: undefined,
    description: undefined,
    loading: false,
    error: '',
    empty: false,
    emptyText: undefined,
  },
)

defineEmits<{
  'update:page': [value: number]
  'update:pageSize': [value: number]
  refresh: []
}>()
</script>

<template>
  <section class="panel data-table-page">
    <PageHeader :eyebrow="eyebrow" :title="title" :description="description">
      <template v-if="$slots.actions" #actions><slot name="actions" /></template>
    </PageHeader>
    <FilterBar v-if="$slots.filters"><slot name="filters" /></FilterBar>
    <PageState :loading="loading" :error="error" :empty="empty" :empty-text="emptyText">
      <template #action><slot name="state-action" /></template>
      <slot />
    </PageState>
    <AppPagination
      :page="page"
      :page-size="pageSize"
      :total="total"
      @update:page="$emit('update:page', $event)"
      @update:page-size="$emit('update:pageSize', $event)"
      @change="$emit('refresh')"
    />
  </section>
</template>
