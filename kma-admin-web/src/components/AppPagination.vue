<script setup lang="ts">
withDefaults(
  defineProps<{
    page: number
    pageSize: number
    total: number
    pageSizes?: number[]
    disabled?: boolean
  }>(),
  {
    pageSizes: () => [10, 20, 50, 100],
    disabled: false,
  },
)

const emit = defineEmits<{
  'update:page': [value: number]
  'update:pageSize': [value: number]
  change: [value: { page: number; pageSize: number }]
}>()

function changePage(value: number, pageSize: number) {
  emit('update:page', value)
  emit('change', { page: value, pageSize })
}

function changePageSize(value: number) {
  emit('update:pageSize', value)
  emit('update:page', 1)
  emit('change', { page: 1, pageSize: value })
}
</script>

<template>
  <div v-if="total > 0" class="app-pagination" aria-label="列表分页">
    <el-pagination
      background
      :current-page="page"
      :page-size="pageSize"
      :page-sizes="pageSizes"
      :total="total"
      :disabled="disabled"
      layout="total, sizes, prev, pager, next, jumper"
      @update:current-page="changePage($event, pageSize)"
      @update:page-size="changePageSize"
    />
  </div>
</template>

<style scoped>
.app-pagination {
  --pagination-green: var(--kma-color-primary);
  --pagination-green-dark: var(--kma-color-primary-strong);
  --pagination-green-soft: var(--kma-color-primary-soft);
  --el-color-primary: var(--pagination-green);
  --el-pagination-hover-color: var(--pagination-green);
  --el-pagination-button-color: var(--pagination-green-dark);
  --el-pagination-button-bg-color: var(--pagination-green-soft);
  display: flex;
  justify-content: flex-end;
  padding-top: 18px;
  overflow-x: auto;
}

.app-pagination :deep(.el-pagination.is-background .el-pager li:not(.is-disabled).is-active) {
  background: var(--pagination-green);
  color: white;
}

.app-pagination :deep(.el-pagination.is-background .btn-prev:not(:disabled):hover),
.app-pagination :deep(.el-pagination.is-background .btn-next:not(:disabled):hover),
.app-pagination :deep(.el-pagination.is-background .el-pager li:not(.is-disabled, .is-active):hover) {
  background: var(--pagination-green-soft);
  color: var(--pagination-green);
}

.app-pagination :deep(.el-select__wrapper.is-focused),
.app-pagination :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--pagination-green) inset;
}

.app-pagination :deep(button:focus-visible),
.app-pagination :deep(.el-pager li:focus-visible) {
  outline: 2px solid var(--pagination-green);
  outline-offset: 2px;
}

@media (width <= 720px) {
  .app-pagination {
    justify-content: flex-start;
    padding-bottom: 4px;
  }
}
</style>
