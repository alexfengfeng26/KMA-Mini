<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createTopic,
  deleteTopic,
  getAdminTopics,
  reorderTopics,
  updateTopic,
  type PortalTopic,
  type TopicRequest,
} from '../api/party'
import PageHeader from '../components/PageHeader.vue'
import PageState from '../components/PageState.vue'
import AppPagination from '../components/AppPagination.vue'
import { useClientPagination } from '../components/listPagination'
import { useMutationAction } from '../composables/useMutationAction'

const queryClient = useQueryClient()
const topicsQuery = useQuery({
  queryKey: ['admin-topics'],
  queryFn: getAdminTopics,
  staleTime: 5 * 60_000,
})
const mutation = useMutationAction()

const keyword = ref('')
const selectedRows = ref<PortalTopic[]>([])
const draggedId = ref<number | undefined>(undefined)
const localRows = ref<PortalTopic[]>([])

watch(
  () => topicsQuery.data.value,
  (data) => {
    localRows.value = [...(data || [])].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
    selectedRows.value = selectedRows.value.filter((s) =>
      localRows.value.some((r) => r.topicId === s.topicId),
    )
  },
  { immediate: true },
)

const filteredRows = computed(() => {
  if (!keyword.value.trim()) return localRows.value
  const q = keyword.value.trim().toLowerCase()
  return localRows.value.filter(
    (r) =>
      r.name.toLowerCase().includes(q) ||
      r.topicCode.toLowerCase().includes(q) ||
      r.description.toLowerCase().includes(q),
  )
})

const { page, pageSize, total, pagedItems } = useClientPagination(filteredRows, 100)

const batchActive = computed(() => selectedRows.value.length > 0)
const batchCanDelete = computed(() =>
  selectedRows.value.every((r) => !r.systemTopic && (r.contentCount ?? 0) === 0),
)

function isSelected(row: PortalTopic) {
  return selectedRows.value.some((r) => r.topicId === row.topicId)
}
function toggleSelect(row: PortalTopic) {
  const index = selectedRows.value.findIndex((r) => r.topicId === row.topicId)
  if (index >= 0) selectedRows.value.splice(index, 1)
  else selectedRows.value.push(row)
}

const dialog = ref(false)
const editing = ref<PortalTopic>()
const form = reactive<TopicRequest & { topicId?: number }>({
  topicCode: '',
  name: '',
  description: '',
  coverColor: '#2f7f76',
  sortOrder: 0,
  enabled: true,
  featured: false,
})

function open(value?: PortalTopic) {
  const row = value
  editing.value = row
  Object.assign(
    form,
    row
      ? {
          topicId: row.topicId,
          topicCode: row.topicCode,
          name: row.name,
          description: row.description,
          coverColor: row.coverColor || '#2f7f76',
          sortOrder: row.sortOrder || 0,
          enabled: row.enabled ?? true,
          featured: row.featured ?? false,
        }
      : {
          topicId: undefined,
          topicCode: '',
          name: '',
          description: '',
          coverColor: '#2f7f76',
          sortOrder: (localRows.value.at(-1)?.sortOrder ?? 0) + 10,
          enabled: true,
          featured: false,
        },
  )
  dialog.value = true
}

async function save() {
  if (!form.topicCode.trim() || !form.name.trim()) return
  const body: TopicRequest = {
    topicCode: form.topicCode.trim(),
    name: form.name.trim(),
    description: (form.description ?? '').trim(),
    coverColor: form.coverColor || '#2f7f76',
    sortOrder: form.sortOrder ?? 0,
    enabled: form.enabled ?? true,
    featured: form.featured ?? false,
  }
  const result = await mutation.run(
    () => (editing.value?.topicId ? updateTopic(editing.value.topicId, body) : createTopic(body)),
    editing.value ? '专题已更新' : '专题已创建',
  )
  if (result.ok) {
    dialog.value = false
    await queryClient.invalidateQueries({ queryKey: ['admin-topics'] })
    await queryClient.invalidateQueries({ queryKey: ['portal-topics'] })
    await queryClient.invalidateQueries({ queryKey: ['portal-home'] })
  }
}

async function remove(row: PortalTopic) {
  if (row.systemTopic) {
    ElMessage.warning('系统分类不可删除')
    return
  }
  if ((row.contentCount ?? 0) > 0) {
    ElMessage.warning('该专题下还有内容，请先移除内容再删除')
    return
  }
  try {
    await ElMessageBox.confirm(`确定删除专题「${row.name}」吗？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  if (!row.topicId) return
  const result = await mutation.run(() => deleteTopic(row.topicId!), '专题已删除')
  if (result.ok) {
    selectedRows.value = selectedRows.value.filter((r) => r.topicId !== row.topicId)
    await queryClient.invalidateQueries({ queryKey: ['admin-topics'] })
    await queryClient.invalidateQueries({ queryKey: ['portal-topics'] })
    await queryClient.invalidateQueries({ queryKey: ['portal-home'] })
  }
}

async function toggleField(row: PortalTopic, field: 'enabled' | 'featured') {
  if (!row.topicId) return
  const next = { ...row, [field]: !(row[field] ?? false) }
  const body: TopicRequest = {
    topicCode: next.topicCode,
    name: next.name,
    description: next.description,
    coverColor: next.coverColor,
    sortOrder: next.sortOrder,
    enabled: next.enabled,
    featured: next.featured,
  }
  const result = await mutation.run(() => updateTopic(row.topicId!, body), '状态已更新')
  if (result.ok) {
    await queryClient.invalidateQueries({ queryKey: ['admin-topics'] })
    await queryClient.invalidateQueries({ queryKey: ['portal-topics'] })
    await queryClient.invalidateQueries({ queryKey: ['portal-home'] })
  }
}

async function updateSortOrder(row: PortalTopic) {
  if (!row.topicId) return
  const body: TopicRequest = {
    topicCode: row.topicCode,
    name: row.name,
    description: row.description,
    coverColor: row.coverColor,
    sortOrder: row.sortOrder,
    enabled: row.enabled,
    featured: row.featured,
  }
  const result = await mutation.run(() => updateTopic(row.topicId!, body), '排序已更新')
  if (result.ok) {
    await queryClient.invalidateQueries({ queryKey: ['admin-topics'] })
    await queryClient.invalidateQueries({ queryKey: ['portal-topics'] })
  }
}

function onDragStart(row: PortalTopic) {
  draggedId.value = row.topicId
}
function onDragOver(event: DragEvent) {
  event.preventDefault()
}
function onDrop(target: PortalTopic) {
  const sourceId = draggedId.value
  draggedId.value = undefined
  if (!sourceId || sourceId === target.topicId || keyword.value.trim()) return
  const sourceIndex = localRows.value.findIndex((r) => r.topicId === sourceId)
  const targetIndex = localRows.value.findIndex((r) => r.topicId === target.topicId)
  if (sourceIndex < 0 || targetIndex < 0) return
  const [moved] = localRows.value.splice(sourceIndex, 1)
  localRows.value.splice(targetIndex, 0, moved)
  const order = localRows.value.map((r, index) => ({ topicId: r.topicId!, sortOrder: (index + 1) * 10 }))
  commitReorder(order)
}

async function commitReorder(order: { topicId: number; sortOrder: number }[]) {
  const result = await mutation.run(() => reorderTopics(order), '排序已保存')
  if (result.ok) {
    await queryClient.invalidateQueries({ queryKey: ['admin-topics'] })
    await queryClient.invalidateQueries({ queryKey: ['portal-topics'] })
  }
}

async function batchAction(action: 'enable' | 'disable' | 'delete') {
  const targets = [...selectedRows.value]
  if (!targets.length) return
  if (action === 'delete') {
    const deletable = targets.filter((r) => !r.systemTopic && (r.contentCount ?? 0) === 0)
    if (!deletable.length) {
      ElMessage.warning('选中的专题均不可删除（系统分类或仍有内容）')
      return
    }
    try {
      await ElMessageBox.confirm(`确定删除选中的 ${deletable.length} 个专题吗？`, '批量删除', {
        type: 'warning',
      })
    } catch {
      return
    }
    for (const row of deletable) {
      await deleteTopic(row.topicId!)
    }
    ElMessage.success('批量删除完成')
  } else {
    const enabled = action === 'enable'
    for (const row of targets) {
      const body: TopicRequest = {
        topicCode: row.topicCode,
        name: row.name,
        description: row.description,
        coverColor: row.coverColor,
        sortOrder: row.sortOrder,
        enabled,
        featured: row.featured,
      }
      await updateTopic(row.topicId!, body)
    }
    ElMessage.success(enabled ? '批量启用完成' : '批量禁用完成')
  }
  selectedRows.value = []
  await queryClient.invalidateQueries({ queryKey: ['admin-topics'] })
  await queryClient.invalidateQueries({ queryKey: ['portal-topics'] })
  await queryClient.invalidateQueries({ queryKey: ['portal-home'] })
}
</script>

<template>
  <section class="panel">
    <PageHeader
      eyebrow="CONTENT TAXONOMY"
      title="分类与专题"
      description="维护专题、推荐状态和展示顺序；系统分类不可删除。"
    >
      <template #actions>
        <el-button v-permission="'topic:manage'" type="primary" @click="open()">新增专题</el-button>
      </template>
    </PageHeader>

    <div class="topic-toolbar">
      <el-input v-model="keyword" placeholder="搜索专题名称 / 编码 / 说明" clearable class="topic-search" />
      <div v-if="batchActive" class="topic-batch">
        <span>已选 {{ selectedRows.length }} 项</span>
        <el-button size="small" @click="batchAction('enable')">批量启用</el-button>
        <el-button size="small" @click="batchAction('disable')">批量禁用</el-button>
        <el-button size="small" type="danger" :disabled="!batchCanDelete" @click="batchAction('delete')">
          批量删除
        </el-button>
        <el-button size="small" text @click="selectedRows = []">清空</el-button>
      </div>
    </div>

    <PageState
      :loading="topicsQuery.isPending.value"
      :error="topicsQuery.error.value instanceof Error ? topicsQuery.error.value.message : ''"
      :empty="!filteredRows.length"
    >
      <div class="topic-grid">
        <article
          v-for="row in pagedItems"
          :key="row.topicId"
          class="topic-card"
          :class="{
            'topic-card--disabled': !(row.enabled ?? true),
            'topic-card--dragging': draggedId === row.topicId,
          }"
          :style="{ '--topic-color': row.coverColor || '#2f7f76' }"
          draggable="true"
          @dragstart="onDragStart(row)"
          @dragover="onDragOver($event)"
          @drop="onDrop(row)"
        >
          <div class="topic-card__head">
            <el-checkbox :model-value="isSelected(row)" @change="toggleSelect(row)" />
            <span class="topic-swatch" aria-hidden="true"></span>
            <strong class="topic-name" :title="row.name">{{ row.name }}</strong>
            <el-tag v-if="row.systemTopic" size="small" type="info">系统</el-tag>
          </div>
          <div class="topic-card__body">
            <div class="topic-meta">
              <span class="topic-code">{{ row.topicCode }}</span>
              <span class="topic-count">内容 {{ row.contentCount ?? 0 }}</span>
            </div>
            <p class="topic-desc">{{ row.description || '暂无说明' }}</p>
          </div>
          <div class="topic-card__foot">
            <el-input-number
              v-model="row.sortOrder"
              :min="0"
              :step="10"
              size="small"
              controls-position="right"
              class="topic-sort"
              @change="updateSortOrder(row)"
            />
            <div class="topic-toggles">
              <el-switch
                :model-value="row.enabled ?? true"
                inline-prompt
                active-text="启用"
                inactive-text="禁用"
                @change="toggleField(row, 'enabled')"
              />
              <el-switch
                :model-value="row.featured ?? false"
                inline-prompt
                active-text="推荐"
                inactive-text="普通"
                @change="toggleField(row, 'featured')"
              />
            </div>
            <div class="topic-actions">
              <el-button v-permission="'topic:manage'" link size="small" @click="open(row)">编辑</el-button>
              <el-button
                v-permission="'topic:manage'"
                link
                size="small"
                type="danger"
                :disabled="row.systemTopic || (row.contentCount ?? 0) > 0"
                @click="remove(row)"
              >
                删除
              </el-button>
            </div>
          </div>
        </article>
      </div>
      <AppPagination v-model:page="page" v-model:page-size="pageSize" :total="total" />
    </PageState>
  </section>

  <el-dialog v-model="dialog" :title="editing ? '编辑专题' : '新增专题'" width="560">
    <el-form label-position="top">
      <el-form-item label="专题编码" required>
        <el-input v-model="form.topicCode" :disabled="!!editing" maxlength="64" />
      </el-form-item>
      <el-form-item label="名称" required><el-input v-model="form.name" maxlength="128" /></el-form-item>
      <el-form-item label="说明">
        <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" />
      </el-form-item>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="封面色">
            <el-color-picker v-model="form.coverColor" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="排序"><el-input-number v-model="form.sortOrder" /></el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item>
            <el-checkbox v-model="form.enabled">启用</el-checkbox>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item>
            <el-checkbox v-model="form.featured">门户推荐</el-checkbox>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="dialog = false">取消</el-button>
      <el-button
        type="primary"
        :loading="mutation.pending.value"
        :disabled="!form.topicCode.trim() || !form.name.trim()"
        @click="save"
      >
        保存专题
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.topic-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;
}
.topic-search {
  width: 280px;
}
.topic-batch {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
  padding: 6px 10px;
  border-radius: 6px;
  background: var(--el-color-primary-light-9);
  font-size: 12px;
}
.topic-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}
.topic-card {
  position: relative;
  display: grid;
  gap: 10px;
  padding: 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: white;
  box-shadow: 0 1px 2px oklch(20% 0.01 195 / 0.04);
  transition: 150ms ease;
}
.topic-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 4px;
  height: 100%;
  border-radius: 10px 0 0 10px;
  background: var(--topic-color);
}
.topic-card:hover {
  box-shadow: 0 4px 12px oklch(20% 0.02 195 / 0.08);
  transform: translateY(-1px);
}
.topic-card--disabled {
  opacity: 0.7;
  background: var(--el-fill-color-lighter);
}
.topic-card--dragging {
  opacity: 0.4;
}
.topic-card__head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.topic-swatch {
  width: 18px;
  height: 18px;
  border-radius: 5px;
  background: var(--topic-color);
  border: 1px solid oklch(0% 0 0 / 0.08);
}
.topic-name {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 15px;
}
.topic-card__body {
  display: grid;
  gap: 6px;
}
.topic-meta {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.topic-code {
  font-family: ui-monospace, monospace;
}
.topic-count {
  color: var(--el-text-color-regular);
  font-weight: 600;
}
.topic-desc {
  margin: 0;
  min-height: 2.6em;
  color: var(--el-text-color-regular);
  font-size: 12px;
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.topic-card__foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.topic-sort {
  width: 90px;
}
.topic-toggles {
  display: flex;
  gap: 8px;
  margin-left: auto;
}
.topic-actions {
  display: flex;
  gap: 4px;
  width: 100%;
  justify-content: flex-end;
}
</style>
