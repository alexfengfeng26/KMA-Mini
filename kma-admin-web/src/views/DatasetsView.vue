<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import { api, unwrap, asList, errorMessage } from '../api/client'
import type { components } from '../api/generated/schema'
import AppPagination from '../components/AppPagination.vue'
import PageState from '../components/PageState.vue'
import { readServerPage, useClientPagination } from '../components/listPagination'
import { useAuthStore } from '../stores/auth'
import { useMutationAction } from '../composables/useMutationAction'

type Dataset = components['schemas']['DatasetVO']
type ModelProfile = components['schemas']['ModelProfile']
type Space = components['schemas']['SpaceVO']
type RebuildJob = Record<string, unknown>
type DatasetForm = components['schemas']['DatasetCreateRequest'] & { datasetId?: number }

interface ChunkPreset {
  label: string
  value: string
}

const CHUNK_PRESETS: Record<string, ChunkPreset> = {
  recursive: { label: '递归分块', value: '{"type":"recursive"}' },
  fixed: { label: '固定长度', value: '{"type":"fixed","chunkSize":500,"chunkOverlap":50}' },
  semantic: { label: '语义分块', value: '{"type":"semantic"}' },
  markdown: { label: 'Markdown 分块', value: '{"type":"markdown"}' },
  custom: { label: '自定义 JSON', value: '' },
}

function normalizeJson(json?: string): string | null {
  if (!json || !json.trim()) return null
  try {
    return JSON.stringify(JSON.parse(json))
  } catch {
    return json.trim()
  }
}

function detectChunkMode(strategy?: string): string {
  const normalized = normalizeJson(strategy)
  if (!normalized) return 'custom'
  for (const key of Object.keys(CHUNK_PRESETS)) {
    if (key === 'custom') continue
    if (normalizeJson(CHUNK_PRESETS[key].value) === normalized) return key
  }
  return 'custom'
}

const auth = useAuthStore()
const mutation = useMutationAction()

const loading = ref(true),
  error = ref(''),
  rows = ref<Dataset[]>([]),
  profiles = ref<ModelProfile[]>([]),
  spaces = ref<Space[]>([])
const page = ref(1),
  pageSize = ref(10),
  total = ref(0)
const selected = ref<Dataset>(),
  jobs = ref<RebuildJob[]>([]),
  dialog = ref(false),
  editing = ref(false)
const {
  page: jobPage,
  pageSize: jobPageSize,
  total: jobTotal,
  pagedItems: pagedJobs,
  resetPage: resetJobPage,
} = useClientPagination(jobs)
const form = reactive<DatasetForm>({
  datasetId: undefined,
  name: '',
  description: '',
  chunkStrategy: CHUNK_PRESETS.recursive.value,
  parseConfig: '{}',
  embeddingProfileCode: '',
  rerankEnabled: true,
  rerankModel: '',
  presetQuestions: '',
})
const chunkMode = ref('recursive')
const embeddingProfiles = computed(() =>
  profiles.value.filter(
    (profile): profile is ModelProfile & { profileCode: string } => !!profile.profileCode,
  ),
)

function statusTag(status?: string) {
  switch (status) {
    case 'active':
      return { label: '启用', type: 'success' as const }
    case 'disabled':
      return { label: '停用', type: 'info' as const }
    default:
      return { label: status || '-', type: 'info' as const }
  }
}
function boundSpaces(datasetId?: number) {
  if (!datasetId) return []
  return spaces.value.filter((space) => space.datasetId === datasetId)
}
function jobProgress(row: RebuildJob) {
  const processed = Number(row.processed_chunks) || 0
  const total = Number(row.total_chunks) || 0
  if (!total) return 0
  return Math.min(100, Math.round((processed / total) * 100))
}

async function load(reset = false) {
  if (reset) page.value = 1
  loading.value = true
  error.value = ''
  try {
    const result = readServerPage<Dataset>(
      await unwrap(
        api.GET('/api/v1/datasets/page', {
          params: { query: { pageNum: page.value, pageSize: pageSize.value } },
        }),
      ),
      page.value,
      pageSize.value,
    )
    rows.value = result.items
    total.value = result.total
    const [modelProfiles, spaceData] = await Promise.all([
      auth.hasAnyPermission(['model:read']) ? unwrap(api.GET('/api/v1/model-profiles')) : Promise.resolve([]),
      auth.hasAnyPermission(['space:read'])
        ? unwrap(api.GET('/api/v1/spaces/page', { params: { query: { pageNum: 1, pageSize: 100 } } }))
        : Promise.resolve(null),
    ])
    profiles.value = modelProfiles.filter((profile) => profile.capability === 'embedding' && profile.enabled)
    spaces.value = spaceData ? readServerPage<Space>(spaceData, 1, 100).items : []
  } catch (e: unknown) {
    error.value = errorMessage(e, '无法读取数据集绑定')
  } finally {
    loading.value = false
  }
}

function resetForm() {
  Object.assign(form, {
    datasetId: undefined,
    name: '',
    description: '',
    chunkStrategy: CHUNK_PRESETS.recursive.value,
    parseConfig: '{}',
    embeddingProfileCode: '',
    rerankEnabled: true,
    rerankModel: '',
    presetQuestions: '',
  })
  chunkMode.value = 'recursive'
}
function applyFirstProfile() {
  const first = embeddingProfiles.value[0]
  if (first) form.embeddingProfileCode = first.profileCode
}
function openCreate() {
  selected.value = undefined
  jobs.value = []
  editing.value = false
  resetForm()
  applyFirstProfile()
  dialog.value = true
}
async function edit(row: Dataset) {
  selected.value = row
  editing.value = true
  Object.assign(form, row, { datasetId: row.datasetId || 0, name: row.name || '' })
  chunkMode.value = detectChunkMode(form.chunkStrategy)
  jobs.value = asList(
    await unwrap(api.GET('/api/v1/embedding-rebuilds', { params: { query: { datasetId: row.datasetId! } } })),
  )
  resetJobPage()
  dialog.value = true
}
async function save() {
  const result = await mutation.run(
    async () => {
      if (editing.value) {
        if (!form.datasetId) throw new Error('数据集编号缺失')
        await unwrap(api.PUT('/api/v1/datasets', { body: { ...form, datasetId: form.datasetId } }))
      } else await unwrap(api.POST('/api/v1/datasets', { body: form }))
    },
    editing.value ? '数据集配置已保存' : '数据集已创建',
  )
  if (!result.ok) return
  dialog.value = false
  await load()
}
async function toggle(row: Dataset) {
  const result = await mutation.run(
    () =>
      unwrap(
        api.PUT('/api/v1/datasets/{datasetId}/status', {
          params: {
            path: { datasetId: row.datasetId! },
            query: { status: row.status === 'active' ? 'disabled' : 'active' },
          },
        }),
      ),
    '数据集状态已更新',
  )
  if (result.ok) await load()
}
async function remove(row: Dataset) {
  const confirmed = await ElMessageBox.confirm(
    `确认删除数据集“${row.name}”？已绑定空间时后端将拒绝。`,
    '删除数据集',
    { type: 'warning' },
  ).then(
    () => true,
    () => false,
  )
  if (!confirmed) return
  const result = await mutation.run(
    () =>
      unwrap(
        api.DELETE('/api/v1/datasets/{datasetId}', {
          params: { path: { datasetId: row.datasetId! } },
        }),
      ),
    '数据集已删除',
  )
  if (result.ok) await load()
}
async function rebuild(profileCode?: string) {
  if (!selected.value?.datasetId || !profileCode) return
  const dataset = selected.value
  const bound = boundSpaces(dataset.datasetId)
  const boundText = bound.length
    ? `该数据集当前被以下 ${bound.length} 个空间绑定：\n${bound.map((space) => `• ${space.name || space.spaceCode}`).join('\n')}\n\n重建期间这些空间的检索可能受到影响。`
    : '当前没有空间绑定该数据集。'
  try {
    await ElMessageBox.confirm(
      `确认将数据集“${dataset.name}”重建到 ${profileCode}？\n\n${boundText}`,
      '确认向量重建',
      { type: 'warning' },
    )
  } catch {
    return
  }
  const result = await mutation.run(
    () =>
      unwrap(
        api.POST('/api/v1/embedding-rebuilds', {
          params: {
            query: { datasetId: dataset.datasetId!, targetProfileCode: profileCode },
          },
        }),
      ),
    '向量重建任务已创建',
  )
  if (result.ok) await edit(dataset)
}
async function activate(jobId: number) {
  const result = await mutation.run(
    () =>
      unwrap(
        api.POST('/api/v1/embedding-rebuilds/{jobId}/activate', {
          params: { path: { jobId } },
        }),
      ),
    '新向量版本已原子切换',
  )
  if (!result.ok) return
  if (selected.value) await edit(selected.value)
  await load()
}

watch(chunkMode, (mode) => {
  if (mode !== 'custom' && CHUNK_PRESETS[mode]) {
    form.chunkStrategy = CHUNK_PRESETS[mode].value
  }
})

onMounted(load)
</script>

<template>
  <section class="panel">
    <div class="toolbar">
      <div>
        <span class="eyebrow">IMMUTABLE EMBEDDING PROFILE</span>
        <h2>数据集与向量版本</h2>
      </div>
      <div>
        <el-button link @click="load()">刷新</el-button
        ><el-button v-permission="'dataset:create'" type="primary" @click="openCreate">创建数据集</el-button>
      </div>
    </div>
    <PageState :loading="loading" :error="error" :empty="!rows.length"
      ><el-table :data="rows"
        ><el-table-column prop="datasetId" label="ID" width="75" /><el-table-column
          prop="name"
          label="数据集"
        /><el-table-column prop="embeddingProfileCode" label="Embedding Profile" /><el-table-column
          prop="chunkStrategy"
          label="分块策略"
        /><el-table-column label="重排" width="90"
          ><template #default="{ row }"
            ><el-tag :type="row.rerankEnabled ? 'success' : 'info'">{{
              row.rerankEnabled ? '启用' : '禁用'
            }}</el-tag></template
          ></el-table-column
        ><el-table-column label="状态" width="100"
          ><template #default="{ row }"
            ><el-tag :type="statusTag(row.status).type">{{ statusTag(row.status).label }}</el-tag></template
          ></el-table-column
        ><el-table-column label="操作" width="260"
          ><template #default="{ row }"
            ><el-button v-permission="'dataset:update'" link type="primary" @click="edit(row)"
              >绑定与重建</el-button
            ><el-button v-permission="'dataset:status:update'" link @click="toggle(row)">{{
              row.status === 'active' ? '停用' : '启用'
            }}</el-button
            ><el-button v-permission="'dataset:delete'" link type="danger" @click="remove(row)"
              >删除</el-button
            ></template
          ></el-table-column
        ></el-table
      ><AppPagination
        v-model:page="page"
        v-model:page-size="pageSize"
        :total="total"
        :disabled="loading"
        @change="load()"
    /></PageState>
  </section>
  <el-dialog v-model="dialog" :title="editing ? '数据集 Profile 与向量版本' : '创建数据集'" width="820">
    <el-form label-position="top"
      ><el-row :gutter="16"
        ><el-col :span="12"
          ><el-form-item label="数据集名称"><el-input v-model="form.name" /></el-form-item></el-col
        ><el-col :span="12"
          ><el-form-item label="Embedding Profile"
            ><el-select v-model="form.embeddingProfileCode" clearable class="full-width"
              ><el-option
                v-for="profile in embeddingProfiles"
                :key="profile.profileCode"
                :label="`${profile.profileCode} · ${profile.modelName} (${profile.dimension})`"
                :value="profile.profileCode" /></el-select></el-form-item></el-col></el-row
      ><el-form-item label="描述"><el-input v-model="form.description" type="textarea" /></el-form-item
      ><el-row :gutter="16"
        ><el-col :span="12"
          ><el-form-item label="分块策略"
            ><el-select v-model="chunkMode" class="full-width"
              ><el-option
                v-for="[key, preset] in Object.entries(CHUNK_PRESETS)"
                :key="key"
                :label="preset.label"
                :value="key" /></el-select></el-form-item></el-col
        ><el-col :span="12"
          ><el-form-item label="解析配置 JSON"
            ><el-input v-model="form.parseConfig" /></el-form-item></el-col></el-row
      ><el-form-item v-if="chunkMode === 'custom'" label="自定义分块策略 JSON"
        ><el-input v-model="form.chunkStrategy" type="textarea" :rows="3" /></el-form-item
      ><el-row :gutter="16"
        ><el-col :span="12"
          ><el-form-item label="Rerank 模型"><el-input v-model="form.rerankModel" /></el-form-item></el-col
        ><el-col :span="12"
          ><el-form-item label="预设问题"
            ><el-input v-model="form.presetQuestions" /></el-form-item></el-col></el-row
      ><el-checkbox v-model="form.rerankEnabled">启用重排</el-checkbox></el-form
    >
    <template #footer
      ><el-button @click="dialog = false">取消</el-button
      ><el-button
        v-permission="editing ? 'dataset:update' : 'dataset:create'"
        type="primary"
        :loading="mutation.pending.value"
        :disabled="!form.name || mutation.pending.value"
        @click="save"
        >保存</el-button
      ></template
    >
    <template v-if="editing"
      ><el-divider>向量重建与原子切换</el-divider>
      <div class="toolbar">
        <span>目标 Profile</span>
        <div>
          <el-button
            v-permission="'embedding:rebuild'"
            v-for="profile in profiles"
            :key="profile.profileCode"
            :loading="mutation.pending.value"
            :disabled="mutation.pending.value"
            @click="rebuild(profile.profileCode)"
            >重建到 {{ profile.profileCode }}</el-button
          >
        </div>
      </div>
      <el-table :data="pagedJobs"
        ><el-table-column prop="job_id" label="任务" /><el-table-column
          prop="target_profile_code"
          label="目标 Profile"
        /><el-table-column prop="status" label="状态" width="110" /><el-table-column label="进度" width="200"
          ><template #default="{ row }"
            ><el-progress
              :percentage="jobProgress(row)"
              :status="row.status === 'failed' ? 'exception' : row.status === 'ready' ? 'success' : ''"
              :stroke-width="8" /></template></el-table-column
        ><el-table-column prop="error_message" label="错误" show-overflow-tooltip /><el-table-column
          label="操作"
          ><template #default="{ row }"
            ><el-button
              v-permission="'embedding:activate'"
              link
              type="primary"
              :loading="mutation.pending.value"
              :disabled="row.status !== 'ready' || mutation.pending.value"
              @click="activate(row.job_id)"
              >激活版本</el-button
            ></template
          ></el-table-column
        ></el-table
      ><AppPagination v-model:page="jobPage" v-model:page-size="jobPageSize" :total="jobTotal"
    /></template>
  </el-dialog>
</template>
