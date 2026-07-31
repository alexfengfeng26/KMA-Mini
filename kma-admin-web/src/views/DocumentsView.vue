<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { api, asList, errorMessage, unwrap } from '../api/client'
import AppPagination from '../components/AppPagination.vue'
import PageState from '../components/PageState.vue'
import SpaceSelect from '../components/SpaceSelect.vue'
import { readServerPage, useClientPagination } from '../components/listPagination'
import type { components } from '../api/generated/schema'
import { useMutationAction } from '../composables/useMutationAction'

type DocumentRow = components['schemas']['DocVO']
type DocumentVersion = Record<string, unknown>
const mutation = useMutationAction()

const rows = ref<DocumentRow[]>([]),
  versions = ref<DocumentVersion[]>([]),
  loading = ref(true),
  error = ref('')
const page = ref(1),
  pageSize = ref(10),
  total = ref(0)
const {
  page: versionPage,
  pageSize: versionPageSize,
  total: versionTotal,
  pagedItems: pagedVersions,
  resetPage: resetVersionPage,
} = useClientPagination(versions)
const dialog = ref(false),
  versionsDialog = ref(false),
  file = ref<File>(),
  mode = ref<'file' | 'text'>('file')
const filters = reactive({ title: '', spaceCode: '', parseStatus: '' })
const form = reactive({
  spaceCode: 'default',
  title: '',
  content: '',
  externalRef: '',
  sourceTag: 'manual',
  sourceVersion: 1,
})

async function load(reset = false) {
  if (reset) page.value = 1
  loading.value = true
  error.value = ''
  try {
    const result = readServerPage<DocumentRow>(
      await unwrap(
        api.GET('/api/v1/documents/page', {
          params: {
            query: {
              pageNum: page.value,
              pageSize: pageSize.value,
              spaceCode: filters.spaceCode || undefined,
              parseStatus: filters.parseStatus || undefined,
              title: filters.title || undefined,
            },
          },
        }),
      ),
      page.value,
      pageSize.value,
    )
    rows.value = result.items
    total.value = result.total
  } catch (e: unknown) {
    error.value = errorMessage(e, '无法读取文档')
  } finally {
    loading.value = false
  }
}
function openIngest() {
  mode.value = 'file'
  file.value = undefined
  Object.assign(form, {
    spaceCode: '',
    title: '',
    content: '',
    externalRef: '',
    sourceTag: 'manual',
    sourceVersion: 1,
  })
  dialog.value = true
}
async function ingest() {
  const externalRef = form.externalRef || crypto.randomUUID()
  const result = await mutation.run(async () => {
    if (!form.spaceCode) throw new Error('请选择知识空间')
    if (mode.value === 'file') {
      if (!file.value) throw new Error('请选择需要上传的文件')
      await unwrap(
        api.POST('/api/v1/documents/file', {
          params: {
            query: {
              spaceCode: form.spaceCode,
              externalRef,
              sourceTag: form.sourceTag,
              sourceVersion: form.sourceVersion,
            },
          },
          body: { file: file.value as unknown as string },
          bodySerializer() {
            const body = new FormData()
            body.append('file', file.value!)
            return body
          },
        }),
      )
    } else {
      if (!form.title.trim() || !form.content.trim()) throw new Error('请填写标题和正文')
      await unwrap(
        api.POST('/api/v1/documents/text', {
          body: {
            spaceCode: form.spaceCode,
            title: form.title,
            content: form.content,
            externalRef,
            sourceTag: form.sourceTag,
            sourceVersion: form.sourceVersion,
          },
        }),
      )
    }
  }, '文档已进入可靠入库队列')
  if (!result.ok) return
  dialog.value = false
  await load()
}
async function showVersions(row: DocumentRow) {
  if (!row.docId) return
  versions.value = asList<DocumentVersion>(
    await unwrap(api.GET('/api/v1/documents/{docId}/versions', { params: { path: { docId: row.docId } } })),
  )
  resetVersionPage()
  versionsDialog.value = true
}
async function reindex(row: DocumentRow) {
  if (!row.docId) return
  const result = await mutation.run(
    () =>
      unwrap(
        api.POST('/api/v1/documents/{docId}/reindex', {
          params: { path: { docId: row.docId! } },
        }),
      ),
    '重新解析与索引任务已提交',
  )
  if (result.ok) await load()
}
async function remove(row: DocumentRow) {
  if (!row.docId) return
  try {
    await ElMessageBox.confirm(`确认删除文档版本“${row.title} v${row.sourceVersion || 1}”？`, '删除文档', {
      type: 'warning',
    })
  } catch {
    return
  }
  const result = await mutation.run(
    () =>
      unwrap(
        api.DELETE('/api/v1/documents/{docId}', {
          params: { path: { docId: row.docId! } },
        }),
      ),
    '文档版本已删除',
  )
  if (result.ok) await load()
}
onMounted(load)
</script>

<template>
  <section class="panel">
    <div class="toolbar">
      <div>
        <span class="eyebrow">INGESTION PIPELINE</span>
        <h2>文档与版本</h2>
      </div>
      <el-button v-permission="'document:ingest'" type="primary" @click="openIngest">添加文档</el-button>
    </div>
    <div class="filter-bar">
      <el-input v-model="filters.title" placeholder="文档标题" clearable /><SpaceSelect
        v-model="filters.spaceCode"
        placeholder="空间编码"
        clearable
      /><el-select v-model="filters.parseStatus" placeholder="入库状态" clearable
        ><el-option label="等待" value="pending" /><el-option label="处理中" value="processing" /><el-option
          label="成功"
          value="success" /><el-option label="失败" value="failed" /><el-option
          label="需要 OCR"
          value="needs_ocr" /></el-select
      ><el-button @click="load(true)">查询</el-button>
    </div>
    <PageState :loading="loading" :error="error" :empty="!rows.length"
      ><el-table :data="rows"
        ><el-table-column prop="title" label="文档" min-width="180" /><el-table-column
          prop="spaceCode"
          label="空间"
        /><el-table-column prop="externalRef" label="外部引用" show-overflow-tooltip /><el-table-column
          prop="sourceVersion"
          label="版本"
          width="70"
        /><el-table-column label="激活" width="70"
          ><template #default="s"
            ><el-tag :type="s.row.isActive ? 'success' : 'info'">{{
              s.row.isActive ? '是' : '否'
            }}</el-tag></template
          ></el-table-column
        ><el-table-column prop="parseStatus" label="入库状态" /><el-table-column
          prop="chunkCount"
          label="Chunks"
          width="85"
        /><el-table-column
          prop="errorMessage"
          label="失败原因"
          min-width="180"
          show-overflow-tooltip
        /><el-table-column label="操作" width="210"
          ><template #default="s"
            ><el-button link @click="showVersions(s.row)">版本</el-button
            ><el-button v-permission="'document:reindex'" link type="primary" @click="reindex(s.row)"
              >重建</el-button
            ><el-button v-permission="'document:delete'" link type="danger" @click="remove(s.row)"
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
  <el-dialog v-model="dialog" title="添加文档" width="680"
    ><div class="ingest-mode" role="group" aria-label="入库方式">
      <button
        type="button"
        :class="{ active: mode === 'file' }"
        :aria-pressed="mode === 'file'"
        @click="mode = 'file'"
      >
        上传文件</button
      ><button
        type="button"
        :class="{ active: mode === 'text' }"
        :aria-pressed="mode === 'text'"
        @click="mode = 'text'"
      >
        粘贴文本
      </button>
    </div>
    <el-form label-position="top"
      ><el-row :gutter="12"
        ><el-col :span="12"
          ><el-form-item label="知识空间"><SpaceSelect v-model="form.spaceCode" style="width: 100%" /></el-form-item></el-col
        ><el-col :span="12"
          ><el-form-item label="业务引用"
            ><el-input
              v-model="form.externalRef"
              placeholder="留空自动生成" /></el-form-item></el-col></el-row
      ><el-row :gutter="12"
        ><el-col :span="12"
          ><el-form-item label="来源标签"><el-input v-model="form.sourceTag" /></el-form-item></el-col
        ><el-col :span="12"
          ><el-form-item label="来源版本"
            ><el-input-number v-model="form.sourceVersion" :min="1" /></el-form-item></el-col></el-row
      ><template v-if="mode === 'file'"
        ><el-form-item label="文件"
          ><input
            type="file"
            @change="file = ($event.target as HTMLInputElement).files?.[0]" /></el-form-item></template
      ><template v-else
        ><el-form-item label="标题"><el-input v-model="form.title" /></el-form-item
        ><el-form-item label="正文"
          ><el-input
            v-model="form.content"
            type="textarea"
            :rows="10"
            placeholder="粘贴需要入库的知识正文" /></el-form-item></template></el-form
    ><template #footer
      ><el-button @click="dialog = false">取消</el-button
      ><el-button
        v-permission="'document:ingest'"
        type="primary"
        :loading="mutation.pending.value"
        :disabled="
          mutation.pending.value ||
          !form.spaceCode ||
          (mode === 'file' ? !file : !form.title.trim() || !form.content.trim())
        "
        @click="ingest"
        >提交入库</el-button
      ></template
    ></el-dialog
  >
  <el-dialog v-model="versionsDialog" title="文档版本链" width="820"
    ><el-table :data="pagedVersions"
      ><el-table-column prop="docId" label="文档 ID" /><el-table-column
        prop="sourceVersion"
        label="版本" /><el-table-column prop="isActive" label="激活" /><el-table-column
        prop="parseStatus"
        label="状态" /><el-table-column prop="chunkCount" label="Chunks" /><el-table-column
        prop="errorMessage"
        label="错误"
        show-overflow-tooltip /></el-table
    ><AppPagination v-model:page="versionPage" v-model:page-size="versionPageSize" :total="versionTotal"
  /></el-dialog>
</template>
