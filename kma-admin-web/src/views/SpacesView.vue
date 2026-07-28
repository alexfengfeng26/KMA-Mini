<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { api, asList, authorizedJson, errorMessage, unwrap } from '../api/client'
import AppPagination from '../components/AppPagination.vue'
import PageState from '../components/PageState.vue'
import { readServerPage, useClientPagination } from '../components/listPagination'
import { useAuthStore } from '../stores/auth'
import type { components } from '../api/generated/schema'
import { useMutationAction } from '../composables/useMutationAction'

type Space = components['schemas']['SpaceVO']
type Dataset = components['schemas']['DatasetVO']
type ModelProfile = components['schemas']['ModelProfile']
type SpaceAcl = components['schemas']['SpaceAclView']
type SpaceForm = components['schemas']['SpaceCreateRequest']
interface PrincipalOption {
  value: string
  label?: string
  secondary?: string
  status?: string
}

const auth = useAuthStore()
const mutation = useMutationAction()
const rows = ref<Space[]>([]),
  datasets = ref<Dataset[]>([]),
  profiles = ref<ModelProfile[]>([]),
  loading = ref(true),
  error = ref('')
const page = ref(1),
  pageSize = ref(10),
  total = ref(0)
const dialog = ref(false),
  aclDialog = ref(false),
  editing = ref<Space>(),
  selected = ref<Space>(),
  acls = ref<SpaceAcl[]>([])
const {
  page: aclPage,
  pageSize: aclPageSize,
  total: aclTotal,
  pagedItems: pagedAcls,
  resetPage: resetAclPage,
} = useClientPagination(acls)
const principalOptions = ref<PrincipalOption[]>([])
const aclImpact = ref<Record<string, unknown>>()
const form = reactive<SpaceForm>({
  spaceCode: '',
  name: '',
  description: '',
  datasetId: undefined,
  embeddingProvider: 'local-bge-m3',
  embeddingModel: 'bge-m3',
  embeddingDim: 1024,
  distanceMetric: 'cosine',
  chunkStrategy: '{}',
  defaultTopK: 6,
  scoreThreshold: 0.35,
})
const aclForm = reactive({ principalType: 'role', principalValue: '', permission: 'read' })
const validDatasets = computed(() =>
  datasets.value.filter((dataset): dataset is Dataset & { datasetId: number } => !!dataset.datasetId),
)
const validProfiles = computed(() =>
  profiles.value.filter(
    (profile): profile is ModelProfile & { profileCode: string } => !!profile.profileCode,
  ),
)

async function load(reset = false) {
  if (reset) page.value = 1
  loading.value = true
  error.value = ''
  try {
    const spaceData = readServerPage<Space>(
      await unwrap(
        api.GET('/api/v1/spaces/page', {
          params: { query: { pageNum: page.value, pageSize: pageSize.value } },
        }),
      ),
      page.value,
      pageSize.value,
    )
    rows.value = spaceData.items
    total.value = spaceData.total
    datasets.value = auth.hasAnyPermission(['dataset:read'])
      ? asList<Dataset>(await unwrap(api.GET('/api/v1/datasets/list')))
      : []
    profiles.value = auth.hasAnyPermission(['model:read'])
      ? asList<ModelProfile>(
          await unwrap(api.GET('/api/v1/model-profiles', { params: { query: { capability: 'embedding' } } })),
        )
      : []
  } catch (e: unknown) {
    error.value = errorMessage(e, '无法读取知识空间')
  } finally {
    loading.value = false
  }
}
function defaults() {
  Object.assign(form, {
    spaceCode: '',
    name: '',
    description: '',
    datasetId: undefined,
    embeddingProvider: 'local-bge-m3',
    embeddingModel: 'bge-m3',
    embeddingDim: 1024,
    distanceMetric: 'cosine',
    chunkStrategy: '{}',
    defaultTopK: 6,
    scoreThreshold: 0.35,
  })
}
function openCreate() {
  editing.value = undefined
  defaults()
  dialog.value = true
}
function openEdit(row: Space) {
  editing.value = row
  Object.assign(form, row)
  dialog.value = true
}
function useProfile(code: string) {
  const p = profiles.value.find((item) => item.profileCode === code)
  if (p)
    Object.assign(form, {
      embeddingProvider: p.provider,
      embeddingModel: p.modelName,
      embeddingDim: p.dimension,
    })
}
async function save() {
  const result = await mutation.run(async () => {
    if (editing.value) {
      if (!editing.value.spaceId) throw new Error('空间编号缺失')
      await unwrap(
        api.PUT('/api/v1/spaces', {
          body: {
            spaceId: editing.value.spaceId,
            datasetId: form.datasetId,
            name: form.name,
            description: form.description,
            embeddingModel: form.embeddingModel,
            distanceMetric: form.distanceMetric,
            chunkStrategy: form.chunkStrategy,
            defaultTopK: form.defaultTopK,
            scoreThreshold: form.scoreThreshold,
          },
        }),
      )
    } else {
      await unwrap(api.POST('/api/v1/spaces', { body: form }))
    }
  }, '空间已保存')
  if (!result.ok) return
  dialog.value = false
  await load()
}
async function remove(row: Space) {
  if (!row.spaceId) return
  const confirmed = await ElMessageBox.confirm(`确认删除空间“${row.name}”及其关联数据？`, '高风险操作', {
    type: 'warning',
  }).then(
    () => true,
    () => false,
  )
  if (!confirmed) return
  const result = await mutation.run(
    () =>
      unwrap(
        api.DELETE('/api/v1/spaces/{spaceId}', {
          params: { path: { spaceId: row.spaceId! } },
        }),
      ),
    '空间已删除',
  )
  if (result.ok) await load()
}
async function reindex(row: Space) {
  if (!row.spaceCode) return
  await mutation.run(
    () =>
      unwrap(
        api.POST('/api/v1/spaces/{spaceCode}/reindex', {
          params: { path: { spaceCode: row.spaceCode! } },
        }),
      ),
    '空间重建任务已提交',
  )
}
async function toggle(row: Space) {
  if (!row.spaceId) return
  const result = await mutation.run(
    () =>
      unwrap(
        api.PUT('/api/v1/spaces/{spaceId}/status', {
          params: {
            path: { spaceId: row.spaceId! },
            query: { status: row.status === 'active' ? 'disabled' : 'active' },
          },
        }),
      ),
    '空间状态已更新',
  )
  if (result.ok) await load()
}
async function loadPrincipals(type = aclForm.principalType) {
  aclForm.principalValue = ''
  aclImpact.value = undefined
  principalOptions.value = asList<PrincipalOption>(
    await unwrap(api.GET('/api/v1/admin/access/principals', { params: { query: { type, keyword: '' } } })),
  )
}
async function loadAclImpact() {
  if (!aclForm.principalValue) {
    aclImpact.value = undefined
    return
  }
  try {
    const query = new URLSearchParams({ type: aclForm.principalType, value: aclForm.principalValue })
    aclImpact.value = await authorizedJson<Record<string, unknown>>(
      `/api/v1/admin/access/principals/impact?${query}`,
    )
  } catch (cause: unknown) {
    aclImpact.value = undefined
    ElMessageBox.alert(errorMessage(cause, '无法计算授权影响范围'), '授权影响提示')
  }
}
async function openAcl(row: Space) {
  if (!row.spaceCode) return
  selected.value = row
  const [aclData] = await Promise.all([
    unwrap(api.GET('/api/v1/spaces/{spaceCode}/acl', { params: { path: { spaceCode: row.spaceCode } } })),
    loadPrincipals(),
  ])
  acls.value = asList<SpaceAcl>(aclData)
  resetAclPage()
  aclDialog.value = true
}
async function addAcl() {
  const current = selected.value
  if (!current?.spaceCode || !current.spaceId) return
  const result = await mutation.run(
    () =>
      unwrap(
        api.POST('/api/v1/spaces/{spaceCode}/acl', {
          params: { path: { spaceCode: current.spaceCode! } },
          body: { spaceId: current.spaceId!, ...aclForm },
        }),
      ),
    '空间授权已添加',
  )
  if (!result.ok) return
  Object.assign(aclForm, { principalType: 'role', principalValue: '', permission: 'read' })
  aclImpact.value = undefined
  await openAcl(current)
}
async function removeAcl(acl: SpaceAcl) {
  const current = selected.value
  if (!current?.spaceCode || !acl.aclId) return
  const result = await mutation.run(
    () =>
      unwrap(
        api.DELETE('/api/v1/spaces/{spaceCode}/acl/{aclId}', {
          params: { path: { spaceCode: current.spaceCode!, aclId: acl.aclId! } },
        }),
      ),
    '空间授权已移除',
  )
  if (result.ok) await openAcl(current)
}
onMounted(load)
</script>

<template>
  <section class="panel">
    <div class="toolbar">
      <div>
        <span class="eyebrow">KNOWLEDGE CATALOG</span>
        <h2>知识空间</h2>
      </div>
      <el-button v-permission="'space:create'" type="primary" @click="openCreate">创建知识空间</el-button>
    </div>
    <PageState :loading="loading" :error="error" :empty="!rows.length"
      ><el-table :data="rows"
        ><el-table-column prop="spaceCode" label="空间编码" /><el-table-column
          prop="name"
          label="名称"
        /><el-table-column prop="embeddingModel" label="Embedding" /><el-table-column
          prop="embeddingDim"
          label="维度"
          width="90"
        /><el-table-column prop="defaultTopK" label="Top K" width="80" /><el-table-column
          prop="status"
          label="状态"
          width="100"
        /><el-table-column label="操作" width="330"
          ><template #default="s"
            ><el-button v-permission="'space:update'" link @click="openEdit(s.row)">编辑</el-button
            ><el-button v-permission="'space:acl:manage'" link @click="openAcl(s.row)">ACL</el-button
            ><el-button v-permission="'space:update'" link @click="toggle(s.row)">{{
              s.row.status === 'active' ? '停用' : '启用'
            }}</el-button
            ><el-button v-permission="'space:reindex'" link @click="reindex(s.row)">重建</el-button
            ><el-button v-permission="'space:delete'" link type="danger" @click="remove(s.row)"
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
  <el-dialog v-model="dialog" :title="editing ? '编辑空间' : '创建空间'" width="680"
    ><el-form label-position="top"
      ><el-form-item label="空间编码"
        ><el-input v-model="form.spaceCode" :disabled="!!editing" /></el-form-item
      ><el-form-item label="名称"><el-input v-model="form.name" /></el-form-item
      ><el-form-item label="描述"><el-input v-model="form.description" type="textarea" /></el-form-item
      ><el-form-item v-if="datasets.length" label="数据集"
        ><el-select v-model="form.datasetId" clearable class="full-width"
          ><el-option
            v-for="d in validDatasets"
            :key="d.datasetId"
            :label="d.name"
            :value="d.datasetId" /></el-select></el-form-item
      ><el-form-item v-if="!editing && profiles.length" label="Embedding Profile"
        ><el-select clearable class="full-width" @change="useProfile"
          ><el-option
            v-for="p in validProfiles"
            :key="p.profileCode"
            :label="`${p.name} · ${p.dimension}`"
            :value="p.profileCode" /></el-select></el-form-item
      ><el-row :gutter="12"
        ><el-col :span="12"
          ><el-form-item label="Embedding 模型"
            ><el-input v-model="form.embeddingModel" /></el-form-item></el-col
        ><el-col :span="12"
          ><el-form-item label="维度"
            ><el-input-number
              v-model="form.embeddingDim"
              :disabled="!!editing" /></el-form-item></el-col></el-row
      ><el-row :gutter="12"
        ><el-col :span="12"
          ><el-form-item label="Top K"
            ><el-input-number v-model="form.defaultTopK" :min="1" :max="100" /></el-form-item></el-col
        ><el-col :span="12"
          ><el-form-item label="分数阈值"
            ><el-input-number
              v-model="form.scoreThreshold"
              :min="0"
              :max="1"
              :step="0.05" /></el-form-item></el-col></el-row></el-form
    ><template #footer
      ><el-button @click="dialog = false">取消</el-button
      ><el-button
        v-permission="editing ? 'space:update' : 'space:create'"
        type="primary"
        :loading="mutation.pending.value"
        :disabled="mutation.pending.value"
        @click="save"
        >保存</el-button
      ></template
    ></el-dialog
  >
  <el-dialog v-model="aclDialog" :title="`${selected?.name || ''} · ACL`" width="820"
    ><el-alert
      type="info"
      :closable="false"
      title="组织 ACL 默认覆盖其全部下级组织成员；系统禁止移除最后一个有效管理授权。"
      class="spaced-bottom" />
    <div class="filter-bar">
      <el-select v-model="aclForm.principalType" aria-label="主体类型" @change="loadPrincipals"
        ><el-option label="角色" value="role" /><el-option label="用户" value="user" /><el-option
          label="组织"
          value="org" /></el-select
      ><el-select
        v-model="aclForm.principalValue"
        aria-label="授权主体"
        filterable
        placeholder="选择授权主体"
        class="min-width-md"
        @change="loadAclImpact"
        ><el-option
          v-for="item in principalOptions"
          :key="item.value"
          :label="`${item.label} · ${item.secondary}`"
          :value="item.value" /></el-select
      ><el-select v-model="aclForm.permission" aria-label="空间权限"
        ><el-option label="读取" value="read" /><el-option label="入库" value="ingest" /><el-option
          label="管理"
          value="admin" /></el-select
      ><el-button
        v-permission="'space:acl:manage'"
        type="primary"
        :loading="mutation.pending.value"
        :disabled="!aclForm.principalValue || mutation.pending.value"
        @click="addAcl"
        >添加</el-button
      >
    </div>
    <el-alert
      v-if="aclImpact"
      type="warning"
      :closable="false"
      class="spaced-bottom"
      :title="`本次授权将实时影响约 ${aclImpact.affectedUsers || 0} 位有效用户；该主体目前已出现在 ${aclImpact.existingAclEntries || 0} 条 ACL 中。`"
      description="范围仅用于变更前提示；保存后服务端仍按最新用户、角色和组织关系实时判定访问。" />
    <el-table :data="pagedAcls"
      ><el-table-column prop="principalType" label="主体类型" /><el-table-column
        prop="principalDisplayName"
        label="主体名称"
      /><el-table-column prop="principalValue" label="主体编码" /><el-table-column
        prop="permission"
        label="权限"
      /><el-table-column label="主体状态" width="110"
        ><template #default="s"
          ><el-tooltip :content="s.row.ineffectiveReason || '授权主体可用'" :disabled="!!s.row.effective"
            ><el-tag :type="s.row.effective ? 'success' : 'danger'">{{
              s.row.effective ? '有效' : s.row.principalStatus || '失效'
            }}</el-tag></el-tooltip
          ></template
        ></el-table-column
      ><el-table-column label="操作" width="90"
        ><template #default="s"
          ><el-button v-permission="'space:acl:manage'" link type="danger" @click="removeAcl(s.row)"
            >移除</el-button
          ></template
        ></el-table-column
      ></el-table
    ><AppPagination v-model:page="aclPage" v-model:page-size="aclPageSize" :total="aclTotal"
  /></el-dialog>
</template>
