<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, asList, authorizedJson, errorMessage, unwrap } from '../api/client'
import AppPagination from '../components/AppPagination.vue'
import { useClientPagination } from '../components/listPagination'
import PageState from '../components/PageState.vue'
import type { components } from '../api/generated/schema'
import { useMutationAction } from '../composables/useMutationAction'
import { modelCapabilityMeta } from '../domain/systemCatalog'

type ModelProfile = components['schemas']['ModelProfile']
type ModelProfileForm = components['schemas']['ModelProfileRequest']
type LlmTemplate = Pick<
  ModelProfileForm,
  'profileCode' | 'name' | 'provider' | 'modelName' | 'baseUrl' | 'secretAlias'
>
type ProbeResult = {
  profileCode: string
  modelName: string
  success: boolean
  nonStreamingSupported: boolean
  streamingSupported: boolean
  durationMillis: number
  message: string
}

const llmTemplates: LlmTemplate[] = [
  {
    profileCode: 'deepseek-v4-flash',
    name: 'DeepSeek V4 Flash',
    provider: 'deepseek',
    modelName: 'deepseek-v4-flash',
    baseUrl: 'https://api.deepseek.com',
    secretAlias: 'KMA_DEEPSEEK_API_KEY',
  },
  {
    profileCode: 'kimi-k2-5',
    name: 'Kimi K2.5',
    provider: 'kimi',
    modelName: 'kimi-k2.5',
    baseUrl: 'https://api.moonshot.cn/v1',
    secretAlias: 'KMA_KIMI_API_KEY',
  },
  {
    profileCode: 'glm-4-7',
    name: '智谱 GLM',
    provider: 'zhipu',
    modelName: 'glm-4.7',
    baseUrl: 'https://open.bigmodel.cn/api/paas/v4',
    secretAlias: 'KMA_ZHIPU_API_KEY',
  },
  {
    profileCode: 'qwen-flash',
    name: 'Qwen Flash',
    provider: 'qwen',
    modelName: 'qwen-flash',
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    secretAlias: 'KMA_DASHSCOPE_API_KEY',
  },
  {
    profileCode: 'minimax-m2-7',
    name: 'MiniMax M2.7',
    provider: 'minimax',
    modelName: 'MiniMax-M2.7',
    baseUrl: 'https://api.minimaxi.com/v1',
    secretAlias: 'KMA_MINIMAX_API_KEY',
  },
]

const mutation = useMutationAction()
const rows = ref<ModelProfile[]>([])
const loading = ref(true)
const error = ref('')
const dialog = ref(false)
const editing = ref<ModelProfile>()
const probingCode = ref('')
const probeResults = ref<Record<string, ProbeResult>>({})
const fallbackSelections = ref<string[]>([])
const { page, pageSize, total, pagedItems, resetPage } = useClientPagination(rows)
const llmProfiles = computed(() => rows.value.filter((row) => row.capability === 'llm' && !!row.profileCode))
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
  fallbackProfileCodes: '[]',
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
    fallbackProfileCodes: '[]',
    enabled: true,
    defaultProfile: false,
  })
  fallbackSelections.value = []
}
function openCreate() {
  editing.value = undefined
  reset()
  dialog.value = true
}
function applyTemplate(template: LlmTemplate) {
  editing.value = undefined
  reset()
  Object.assign(form, {
    ...template,
    capability: 'llm',
    timeoutSeconds: 90,
    enabled: true,
    defaultProfile: false,
  })
  dialog.value = true
}
function openEdit(row: ModelProfile) {
  editing.value = row
  Object.assign(form, row)
  try {
    fallbackSelections.value = JSON.parse(row.fallbackProfileCodes || '[]')
  } catch {
    fallbackSelections.value = []
  }
  dialog.value = true
}
async function save() {
  form.fallbackProfileCodes = JSON.stringify(
    fallbackSelections.value.filter((code) => code !== form.profileCode),
  )
  const result = await mutation.run(
    () =>
      editing.value
        ? unwrap(api.PUT('/api/v1/model-profiles', { body: form }))
        : unwrap(api.POST('/api/v1/model-profiles', { body: form })),
    '模型 Profile 已保存；LLM 切换仍需先测试连接',
  )
  if (!result.ok) return
  dialog.value = false
  await load()
}
async function probe(row: ModelProfile, activate = false) {
  if (!row.profileCode) return
  probingCode.value = row.profileCode
  try {
    const result = await authorizedJson<ProbeResult>(
      `/api/v1/model-profiles/${encodeURIComponent(row.profileCode)}/probe`,
      { method: 'POST' },
    )
    probeResults.value = { ...probeResults.value, [row.profileCode]: result }
    if (!result.success) return ElMessage.error(result.message || '连接测试失败；当前默认模型未改变')
    if (!activate) return ElMessage.success(`连接正常（${result.durationMillis} ms）`)
    await authorizedJson(`/api/v1/model-profiles/${encodeURIComponent(row.profileCode)}/activate-default`, {
      method: 'POST',
    })
    ElMessage.success(`已切换默认模型：${row.name}；新问答将立即使用它`)
    await load()
  } catch (e: unknown) {
    ElMessage.error(errorMessage(e, '模型连接测试失败'))
  } finally {
    probingCode.value = ''
  }
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
      <el-dropdown
        v-permission="'model:create'"
        @command="(template: LlmTemplate) => applyTemplate(template)"
      >
        <el-button>LLM 快速模板 ▼</el-button>
        <template #dropdown
          ><el-dropdown-menu
            ><el-dropdown-item
              v-for="template in llmTemplates"
              :key="template.profileCode"
              :command="template"
              >{{ template.name }}</el-dropdown-item
            ></el-dropdown-menu
          ></template
        >
      </el-dropdown>
      <el-button v-permission="'model:create'" type="primary" @click="openCreate">添加 Profile</el-button>
    </div>
    <p class="muted">密钥只由后端进程环境读取。LLM 必须先测试非流式和流式连接，成功后才能切换默认模型。</p>
    <PageState :loading="loading" :error="error" :empty="!rows.length"
      ><el-table :data="pagedItems">
        <el-table-column prop="profileCode" label="Profile" /><el-table-column label="能力"
          ><template #default="scope"
            ><el-tag :type="modelCapabilityMeta(scope.row.capability).type">{{
              modelCapabilityMeta(scope.row.capability).label
            }}</el-tag></template
          ></el-table-column
        >
        <el-table-column prop="provider" label="提供商" /><el-table-column
          prop="modelName"
          label="模型"
        /><el-table-column prop="dimension" label="维度" /><el-table-column
          prop="timeoutSeconds"
          label="超时"
        />
        <el-table-column prop="fallbackProfileCodes" label="降级链" show-overflow-tooltip /><el-table-column
          label="状态"
          ><template #default="s"
            ><el-tag :type="s.row.enabled ? 'success' : 'info'">{{ s.row.enabled ? '启用' : '停用' }}</el-tag
            ><el-tag v-if="s.row.defaultProfile" class="spaced-left-1">默认</el-tag></template
          ></el-table-column
        >
        <el-table-column label="最近测试" min-width="175"
          ><template #default="s"
            ><span v-if="probeResults[s.row.profileCode || '']"
              >{{ probeResults[s.row.profileCode || ''].success ? '通过' : '失败' }} ·
              {{ probeResults[s.row.profileCode || ''].message }}</span
            ><span v-else class="muted">未测试</span></template
          ></el-table-column
        >
        <el-table-column label="操作" width="230"
          ><template #default="s"
            ><el-button v-permission="'model:update'" link type="primary" @click="openEdit(s.row)"
              >编辑</el-button
            ><template v-if="s.row.capability === 'llm'"
              ><el-button
                v-permission="'model:update'"
                link
                :loading="probingCode === s.row.profileCode"
                @click="probe(s.row)"
                >测试</el-button
              ><el-button
                v-permission="'model:update'"
                link
                type="success"
                :loading="probingCode === s.row.profileCode"
                @click="probe(s.row, true)"
                >测试并设为默认</el-button
              ></template
            ></template
          ></el-table-column
        > </el-table
      ><AppPagination v-model:page="page" v-model:page-size="pageSize" :total="total"
    /></PageState>
  </section>
  <el-dialog v-model="dialog" :title="editing ? '编辑模型 Profile' : '新增模型 Profile'" width="680"
    ><el-form label-position="top">
      <el-alert
        v-if="!editing"
        title="快速模板只填模型、地址和环境变量别名，不会保存 API Key。"
        type="info"
        :closable="false"
      />
      <el-row :gutter="12"
        ><el-col :span="12"
          ><el-form-item label="Profile 编码"
            ><el-input v-model="form.profileCode" :disabled="!!editing" /></el-form-item></el-col
        ><el-col :span="12"
          ><el-form-item label="显示名称"><el-input v-model="form.name" /></el-form-item></el-col
      ></el-row>
      <el-row :gutter="12"
        ><el-col :span="12"
          ><el-form-item label="能力"
            ><el-select v-model="form.capability" :disabled="!!editing" class="full-width"
              ><el-option label="LLM" value="llm" /><el-option
                label="Embedding"
                value="embedding" /><el-option label="Rerank" value="rerank" /><el-option
                label="OCR"
                value="ocr" /></el-select></el-form-item></el-col
        ><el-col :span="12"
          ><el-form-item label="Provider"><el-input v-model="form.provider" /></el-form-item></el-col
      ></el-row>
      <el-form-item label="模型名"><el-input v-model="form.modelName" /></el-form-item
      ><el-form-item label="服务地址"><el-input v-model="form.baseUrl" /></el-form-item>
      <el-row :gutter="12"
        ><el-col :span="12"
          ><el-form-item label="维度（仅 Embedding）"
            ><el-input-number v-model="form.dimension" :min="1" /></el-form-item></el-col
        ><el-col :span="12"
          ><el-form-item label="超时秒数"
            ><el-input-number v-model="form.timeoutSeconds" :min="1" :max="600" /></el-form-item></el-col
      ></el-row>
      <el-form-item label="密钥环境变量别名"
        ><el-input v-model="form.secretAlias" placeholder="例如 KMA_DEEPSEEK_API_KEY；不填写密钥原文"
      /></el-form-item>
      <el-form-item label="降级 Profile（可选）"
        ><el-select
          v-model="fallbackSelections"
          multiple
          filterable
          class="full-width"
          placeholder="按选择顺序降级"
          ><el-option
            v-for="profile in llmProfiles"
            :key="profile.profileCode"
            :label="`${profile.name} · ${profile.modelName}`"
            :value="profile.profileCode!"
            :disabled="profile.profileCode === form.profileCode" /></el-select
      ></el-form-item>
      <div class="filter-bar">
        <el-checkbox v-model="form.enabled">启用</el-checkbox
        ><el-checkbox v-if="form.capability !== 'llm'" v-model="form.defaultProfile"
          >设为该能力默认 Profile</el-checkbox
        >
      </div> </el-form
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
