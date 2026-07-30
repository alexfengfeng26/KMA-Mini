<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, unwrap } from '../api/client'
import { readServerPage } from './listPagination'
import type { components } from '../api/generated/schema'

type Space = components['schemas']['SpaceVO']

const props = withDefaults(
  defineProps<{
    modelValue: string
    placeholder?: string
    clearable?: boolean
    size?: 'small' | 'default' | 'large'
    disabled?: boolean
  }>(),
  {
    placeholder: '选择知识空间',
    clearable: false,
    size: 'default',
    disabled: false,
  },
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'change', value: string): void
}>()

const spaces = ref<Space[]>([])
const loading = ref(false)

const selected = computed({
  get: () => props.modelValue,
  set: (value: string) => {
    emit('update:modelValue', value)
    emit('change', value)
  },
})

async function loadSpaces() {
  loading.value = true
  try {
    const result = readServerPage<Space>(
      await unwrap(api.GET('/api/v1/spaces/page', { params: { query: { pageNum: 1, pageSize: 100 } } })),
      1,
      100,
    )
    spaces.value = result.items
  } finally {
    loading.value = false
  }
}

onMounted(loadSpaces)
</script>

<template>
  <el-select
    v-model="selected"
    :placeholder="placeholder"
    :clearable="clearable"
    :size="size"
    :loading="loading"
    :disabled="disabled"
    filterable
  >
    <el-option
      v-for="space in spaces"
      :key="space.spaceId"
      :label="space.name || space.spaceCode || ''"
      :value="space.spaceCode || ''"
    >
      <span>{{ space.name }}</span>
      <small class="space-code">{{ space.spaceCode }}</small>
    </el-option>
  </el-select>
</template>

<style scoped>
.space-code {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
}
</style>
