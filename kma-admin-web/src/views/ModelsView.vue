<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { api, asList, errorMessage, unwrap } from '../api/client'
import AppPagination from '../components/AppPagination.vue'
import { useClientPagination } from '../components/listPagination'
import PageState from '../components/PageState.vue'
import type { components } from '../api/generated/schema'
import { useMutationAction } from '../composables/useMutationAction'
import { modelCapabilityMeta } from '../domain/systemCatalog'

type ModelProfile = components['schemas']['ModelProfile']
type ModelProfileForm = components['schemas']['ModelProfileRequest']
const mutation = useMutationAction()

const rows = ref<ModelProfile[]>([]),
  loading = ref(true),
  error = ref(''),
  dialog = ref(false),
  editing = ref<ModelProfile>()
const { page, pageSize, total, pagedItems, resetPage } = useClientPagination(rows)
const form = reactive<ModelProfileForm>({
  profileId: undefined,
  profileCode: '',
  name: '',
  capability: 'llm',
  provider: 'ollama',
  modelName: '',
  baseUrl: '',
  dimension: undefined,
  timeoutSeconds: 60,
  secretAlias: '',
  fallbackProfileCodes: '',
  enabled: true,
  defaultProfile: false,
})
async function load() {
  loading.value = true
  error.value = ''
  try {
    rows.value = asList<ModelProfile>(await unwrap(api.GET('/api/v1/model-profiles')))
    resetPage()
  } catch (e: unknown) {
    error.value = errorMessage(e, '无法读取模型配置')
  } finally {
    loading.value = false
  }
}
function reset() {
  Object.assign(form, {
    profileId: undefined,
    profileCode: '',
    name: '',
    capability: 'llm',
    provider: 'ollama',
    modelName: '',
    baseUrl: '',
    dimension: undefined,
    timeoutSeconds: 60,
    secretAlias: '',
    fallbackProfileCodes: '',
    enabled: true,
    defaultProfile: false,
  })
}
function openCreate() {
  editing.value = undefined
  reset()
  dialog.value = true
}
function openEdit(row: ModelProfile) {
  editing.value = row
  Object.assign(form, row)
  dialog.value = true
}
async function save() {
  const result = await mutation.run(
    () =>
      editing.value
        ? unwrap(api.PUT('/api/v1/model-profiles', { body: form }))
        : unwrap(api.POST('/api/v1/model-profiles', { body: form })),
    '模型 Profile 已保存，新任务将使用最新配置',
  )
  if (!result.ok) return
  dialog.value = false
  await load()
}
onMounted(load)
</script>
<template>
  <section class="panel">
    <div class="toolbar">
      <div>
        <span class="eyebrow">PROVIDER PROFILES</span>
        <h2>模型能力配置</h2>
      </div>
      <el-button v-permission="'model:create'" type="primary" @click="openCreate">添加 Profile</el-button>
    </div>
    <p class="muted">后台只保存密钥别名；真实密钥由环境变量或 Secret Provider 注入。</p>
    <PageState :loading="loading" :error="error" :empty="!rows.length"
      ><el-table :data="pagedItems"
        ><el-table-column prop="profileCode" label="Profile" /><el-table-column label="能力"
          ><template #default="scope"
            ><el-tag :type="modelCapabilityMeta(scope.row.capability).type">{{
              modelCapabilityMeta(scope.row.capability).label
            }}</el-tag></template
          ></el-table-column
        ><el-table-column prop="provider" label="提供商" /><el-table-column
          prop="modelName"
          label="模型"
        /><el-table-column prop="dimension" label="维度" /><el-table-column
          prop="timeoutSeconds"
          label="超时"
        /><el-table-column prop="fallbackProfileCodes" label="降级链" show-overflow-tooltip /><el-table-column
          label="状态"
          ><template #default="s"
            ><el-tag :type="s.row.enabled ? 'success' : 'info'">{{ s.row.enabled ? '启用' : '停用' }}</el-tag
            ><el-tag v-if="s.row.defaultProfile" class="spaced-left-1">默认</el-tag></template
          ></el-table-column
        ><el-table-column label="操作" width="90"
          ><template #default="s"
            ><el-button v-permission="'model:update'" link type="primary" @click="openEdit(s.row)"
              >编辑</el-button
            ></template
          ></el-table-column
        ></el-table
      ><AppPagination v-model:page="page" v-model:page-size="pageSize" :total="total"
    /></PageState>
  </section>
  <el-dialog v-model="dialog" :title="editing ? '编辑模型 Profile' : '新增模型 Profile'" width="680"
    ><el-form label-position="top"
      ><el-row :gutter="12"
        ><el-col :span="12"
          ><el-form-item label="Profile 编码"
            ><el-input v-model="form.profileCode" :disabled="!!editing" /></el-form-item></el-col
        ><el-col :span="12"
          ><el-form-item label="显示名称"><el-input v-model="form.name" /></el-form-item></el-col></el-row
      ><el-row :gutter="12"
        ><el-col :span="12"
          ><el-form-item label="能力"
            ><el-select v-model="form.capability" :disabled="!!editing" class="full-width"
              ><el-option label="LLM" value="llm" /><el-option
                label="Embedding"
                value="embedding" /><el-option label="Rerank" value="rerank" /><el-option
                label="OCR"
                value="ocr" /></el-select></el-form-item></el-col
        ><el-col :span="12"
          ><el-form-item label="Provider"><el-input v-model="form.provider" /></el-form-item></el-col></el-row
      ><el-form-item label="模型名"><el-input v-model="form.modelName" /></el-form-item
      ><el-form-item label="服务地址"><el-input v-model="form.baseUrl" /></el-form-item
      ><el-row :gutter="12"
        ><el-col :span="12"
          ><el-form-item label="维度（仅 Embedding）"
            ><el-input-number v-model="form.dimension" :min="1" /></el-form-item></el-col
        ><el-col :span="12"
          ><el-form-item label="超时秒数"
            ><el-input-number
              v-model="form.timeoutSeconds"
              :min="1"
              :max="600" /></el-form-item></el-col></el-row
      ><el-form-item label="密钥别名"
        ><el-input
          v-model="form.secretAlias"
          placeholder="例如 DEEPSEEK_API_KEY；不填写密钥原文" /></el-form-item
      ><el-form-item label="降级 Profile 编码"
        ><el-input v-model="form.fallbackProfileCodes" placeholder="逗号分隔，按顺序降级"
      /></el-form-item>
      <div class="filter-bar">
        <el-checkbox v-model="form.enabled">启用</el-checkbox
        ><el-checkbox v-model="form.defaultProfile">设为该能力默认 Profile</el-checkbox>
      </div></el-form
    ><template #footer
      ><el-button @click="dialog = false">取消</el-button
      ><el-button
        v-permission="editing ? 'model:update' : 'model:create'"
        type="primary"
        :loading="mutation.pending.value"
        :disabled="mutation.pending.value"
        @click="save"
        >保存</el-button
      ></template
    ></el-dialog
  >
</template>
