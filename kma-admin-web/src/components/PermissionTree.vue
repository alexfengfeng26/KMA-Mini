<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { components } from '../api/generated/schema'
import type { ElTree } from 'element-plus'

type PermissionNode = components['schemas']['PermissionNode'] & { module?: string }

const props = defineProps<{
  data: PermissionNode[]
  modelValue?: string[]
  readonly?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string[]]
}>()

const treeRef = ref<InstanceType<typeof ElTree>>()
const filterText = ref('')
const treeProps = { label: 'name', children: 'children' }

const filteredData = computed(() => props.data)

watch(filterText, (value) => {
  treeRef.value?.filter(value)
})

function filterNode(value: string, data: PermissionNode) {
  if (!value) return true
  const name = data.name || ''
  const code = data.permissionCode || ''
  return (
    name.includes(value) ||
    code.includes(value) ||
    (data.module || '').includes(value) ||
    (data.description || '').includes(value)
  )
}

function syncChecked() {
  const checked = treeRef.value?.getCheckedKeys(false) || []
  emit('update:modelValue', checked.map(String))
}

function setCheckedKeys(keys: string[]) {
  treeRef.value?.setCheckedKeys(keys)
}

function getCheckedKeys() {
  return treeRef.value?.getCheckedKeys(false) || []
}

defineExpose({ setCheckedKeys, getCheckedKeys })
</script>

<template>
  <div class="permission-tree">
    <el-input
      v-model="filterText"
      placeholder="搜索权限名称 / 编码 / 模块"
      clearable
      class="permission-search"
    />
    <el-tree
      ref="treeRef"
      :data="filteredData"
      :props="treeProps"
      node-key="permissionCode"
      :show-checkbox="!readonly"
      check-strictly
      default-expand-all
      :filter-node-method="filterNode"
      @check="syncChecked"
    >
      <template #default="{ data }">
        <span class="permission-node">
          <span class="permission-module">{{ data.module || '系统管理' }}</span>
          <span class="permission-name">{{ data.name }}</span>
          <small class="permission-code">{{ data.permissionCode }}</small>
        </span>
      </template>
    </el-tree>
  </div>
</template>

<style scoped>
.permission-tree {
  display: grid;
  gap: 8px;
}
.permission-search {
  margin-bottom: 4px;
}
.permission-node {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.permission-module {
  flex: 0 0 auto;
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: 10px;
  line-height: 16px;
}
.permission-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.permission-code {
  color: var(--el-text-color-secondary);
  font-size: 10px;
}
</style>
