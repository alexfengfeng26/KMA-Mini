<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  applyContentAction,
  createFileContent,
  createTextContent,
  getAdminContent,
  getAdminContents,
  getAdminTopics,
  updateContentMetadata,
  type PartyContent,
  type PartyContentRequest,
  type PortalTopic,
} from '../api/party'
import { authorizedJson, errorMessage } from '../api/client'
import { useAuthStore } from '../stores/auth'
import AppPagination from '../components/AppPagination.vue'
import PageState from '../components/PageState.vue'
import { PARTY_CONTENT_CATEGORIES, categoryLabel } from '../domain/partyKnowledge'
import { useUnsavedChanges } from '../composables/useUnsavedChanges'

interface ContentForm {
  title: string
  spaceCode: string
  content: string
  sourceTag: string
  externalRef: string
  sourceVersion: number
  contentType: string
  documentNumber: string
  issuingAuthority: string
  publishDate: string
  effectiveDate: string
  expiryDate: string
  scheduledOnlineAt: string
  scheduledOfflineAt: string
  scheduleNote: string
  validityStatus: string
  summary: string
  keywords: string
  topicCodes: string[]
}

const route = useRoute(),
  auth = useAuthStore(),
  rows = ref<PartyContent[]>([]),
  topics = ref<PortalTopic[]>([]),
  loading = ref(true),
  error = ref(''),
  total = ref(0),
  page = ref(1),
  pageSize = ref(20),
  dialog = ref(false),
  editingId = ref<number>(),
  detail = ref<PartyContent>(),
  detailDialog = ref(false),
  file = ref<File>(),
  mode = ref<'text' | 'file'>('text'),
  saving = ref(false),
  actionPendingId = ref<number>(),
  governanceInsights = ref<Record<string, number>>({}),
  governancePolicy = ref({ contentSeparationOfDuties: false }),
  policySaving = ref(false),
  contentImpact = ref<Record<string, number>>({})
const versionPage = ref(1),
  versionPageSize = ref(10)
const pagedVersions = computed(
  () =>
    detail.value?.versions?.slice(
      (versionPage.value - 1) * versionPageSize.value,
      versionPage.value * versionPageSize.value,
    ) || [],
)
const filters = reactive({
  keyword: '',
  contentType: '',
  workflowStatus: '',
  reviewDecision: '',
  spaceCode: '',
})
const form = reactive<ContentForm>({
  title: '',
  spaceCode: 'default',
  content: '',
  sourceTag: '党建资料',
  externalRef: '',
  sourceVersion: 1,
  contentType: 'policy',
  documentNumber: '',
  issuingAuthority: '',
  publishDate: new Date().toISOString().slice(0, 10),
  effectiveDate: '',
  expiryDate: '',
  scheduledOnlineAt: '',
  scheduledOfflineAt: '',
  scheduleNote: '',
  validityStatus: 'effective',
  summary: '',
  keywords: '',
  topicCodes: [],
})
const reviewMode = computed(() => route.meta.governanceMode === 'review')
const publicationMode = computed(() => route.meta.governanceMode === 'publication')
const cleanFormSnapshot = ref('')
const currentFormSnapshot = computed(() =>
  JSON.stringify({
    form,
    mode: mode.value,
    fileName: file.value?.name || '',
    fileSize: file.value?.size || 0,
  }),
)
const formDirty = computed(
  () => dialog.value && !!cleanFormSnapshot.value && currentFormSnapshot.value !== cleanFormSnapshot.value,
)
const { confirmDiscard } = useUnsavedChanges(formDirty)
const can = (permission: string) => auth.hasAnyPermission([permission])
function markFormClean() {
  cleanFormSnapshot.value = currentFormSnapshot.value
}
async function requestDialogClose(done?: () => void) {
  if (!(await confirmDiscard())) return
  cleanFormSnapshot.value = ''
  if (done) done()
  else dialog.value = false
}
function handleDialogBeforeClose(done: () => void) {
  void requestDialogClose(done)
}
async function load(reset = false) {
  if (reset) page.value = 1
  loading.value = true
  error.value = ''
  if (reviewMode.value) {
    filters.workflowStatus = 'reviewing'
    filters.reviewDecision = ''
  } else if (publicationMode.value) {
    filters.workflowStatus = ''
    filters.reviewDecision = 'approved'
  }
  try {
    const result = await getAdminContents({
      keyword: filters.keyword || undefined,
      contentType: filters.contentType || undefined,
      workflowStatus: filters.workflowStatus || undefined,
      reviewDecision: filters.reviewDecision || undefined,
      spaceCode: filters.spaceCode || undefined,
      pageNum: page.value,
      pageSize: pageSize.value,
    })
    rows.value = result.list
    total.value = result.total
  } catch (cause: unknown) {
    error.value = errorMessage(cause, '无法读取内容库')
  } finally {
    loading.value = false
  }
}
async function loadGovernanceSignals() {
  if (!can('content:read')) return
  try {
    const [insights, policy] = await Promise.all([
      authorizedJson<Record<string, number>>('/api/v1/admin/governance/insights'),
      authorizedJson<{ contentSeparationOfDuties?: boolean }>('/api/v1/admin/governance/policy'),
    ])
    governanceInsights.value = insights
    governancePolicy.value.contentSeparationOfDuties = Boolean(policy.contentSeparationOfDuties)
  } catch {
    // Content work remains available if an optional governance dashboard is unavailable.
  }
}
async function saveSeparationOfDuties(enabled: boolean) {
  if (!can('content:publish') || policySaving.value) return
  policySaving.value = true
  try {
    await authorizedJson('/api/v1/admin/governance/policy', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ contentSeparationOfDuties: enabled }),
    })
    ElMessage.success(enabled ? '已启用提交者与审核/发布者职责分离' : '已关闭职责分离')
  } catch (cause: unknown) {
    governancePolicy.value.contentSeparationOfDuties = !enabled
    ElMessage.error(errorMessage(cause, '治理策略更新失败'))
  } finally {
    policySaving.value = false
  }
}
function openCreate() {
  editingId.value = undefined
  Object.assign(form, {
    title: '',
    spaceCode: 'default',
    content: '',
    sourceTag: '党建资料',
    externalRef: '',
    sourceVersion: 1,
    contentType: 'policy',
    documentNumber: '',
    issuingAuthority: '',
    publishDate: new Date().toISOString().slice(0, 10),
    effectiveDate: '',
    expiryDate: '',
    scheduledOnlineAt: '',
    scheduledOfflineAt: '',
    scheduleNote: '',
    validityStatus: 'effective',
    summary: '',
    keywords: '',
    topicCodes: [],
  })
  file.value = undefined
  mode.value = 'text'
  dialog.value = true
  markFormClean()
}
function openEdit(row: PartyContent) {
  editingId.value = row.contentId
  Object.assign(form, {
    title: row.title,
    spaceCode: row.spaceCode,
    content: '',
    sourceTag: row.sourceTag || '党建资料',
    externalRef: row.externalRef,
    sourceVersion: row.sourceVersion,
    contentType: row.contentType,
    documentNumber: row.documentNumber || '',
    issuingAuthority: row.issuingAuthority || '',
    publishDate: row.publishDate || new Date().toISOString().slice(0, 10),
    effectiveDate: row.effectiveDate || '',
    expiryDate: row.expiryDate || '',
    scheduledOnlineAt: (row as PartyContent & { scheduledOnlineAt?: string }).scheduledOnlineAt || '',
    scheduledOfflineAt: (row as PartyContent & { scheduledOfflineAt?: string }).scheduledOfflineAt || '',
    scheduleNote: (row as PartyContent & { scheduleNote?: string }).scheduleNote || '',
    validityStatus: row.validityStatus || 'effective',
    summary: row.summary || '',
    keywords: (row.keywords || []).join('，'),
    topicCodes: row.topicCodes || [],
  })
  file.value = undefined
  mode.value = 'text'
  dialog.value = true
  markFormClean()
}
function payload(): PartyContentRequest & {
  scheduledOnlineAt?: string
  scheduledOfflineAt?: string
  scheduleNote?: string
} {
  return {
    title: form.title.trim(),
    spaceCode: form.spaceCode.trim(),
    content: form.content,
    sourceTag: form.sourceTag || undefined,
    externalRef: form.externalRef || `party-${crypto.randomUUID()}`,
    sourceVersion: form.sourceVersion,
    contentType: form.contentType,
    documentNumber: form.documentNumber || undefined,
    issuingAuthority: form.issuingAuthority || undefined,
    publishDate: form.publishDate,
    effectiveDate: form.effectiveDate || undefined,
    expiryDate: form.expiryDate || undefined,
    scheduledOnlineAt: form.scheduledOnlineAt || undefined,
    scheduledOfflineAt: form.scheduledOfflineAt || undefined,
    scheduleNote: form.scheduleNote || undefined,
    validityStatus: form.validityStatus,
    summary: form.summary || undefined,
    keywords: form.keywords.split(/[，,\s]+/).filter(Boolean),
    topicCodes: form.topicCodes,
  }
}
async function create() {
  if (!form.title.trim() || !form.spaceCode.trim() || saving.value) return
  if (mode.value === 'text' && !form.content.trim()) return
  if (mode.value === 'file' && !file.value) return
  saving.value = true
  try {
    const request = payload()
    if (mode.value === 'text') await createTextContent(request)
    else {
      const { content, ...query } = request
      void content
      await createFileContent(query, file.value as File)
    }
    ElMessage.success('内容草稿已创建并进入解析队列')
    cleanFormSnapshot.value = ''
    dialog.value = false
    await load(true)
  } catch (cause: unknown) {
    ElMessage.error(errorMessage(cause, '内容草稿创建失败'))
  } finally {
    saving.value = false
  }
}
async function save() {
  if (!editingId.value) return create()
  if (saving.value || !form.title.trim()) return
  saving.value = true
  try {
    const body = payload()
    await updateContentMetadata(editingId.value, {
      title: body.title,
      contentType: body.contentType,
      documentNumber: body.documentNumber,
      issuingAuthority: body.issuingAuthority,
      publishDate: body.publishDate,
      effectiveDate: body.effectiveDate,
      expiryDate: body.expiryDate,
      validityStatus: body.validityStatus,
      summary: body.summary,
      keywords: body.keywords,
      topicCodes: body.topicCodes,
      scheduledOnlineAt: body.scheduledOnlineAt,
      scheduledOfflineAt: body.scheduledOfflineAt,
      scheduleNote: body.scheduleNote,
    } as Parameters<typeof updateContentMetadata>[1] & {
      scheduledOnlineAt?: string
      scheduledOfflineAt?: string
      scheduleNote?: string
    })
    ElMessage.success('内容元数据已更新')
    cleanFormSnapshot.value = ''
    dialog.value = false
    await load()
  } catch (cause: unknown) {
    ElMessage.error(errorMessage(cause, '内容元数据更新失败'))
  } finally {
    saving.value = false
  }
}
async function inspect(row: PartyContent) {
  if (!row.contentId) return
  const [content, impact] = await Promise.all([
    getAdminContent(row.contentId),
    authorizedJson<Record<string, number>>(`/api/v1/admin/governance/contents/${row.contentId}/impact`).catch(
      () => ({}),
    ),
  ])
  detail.value = content
  contentImpact.value = impact
  versionPage.value = 1
  detailDialog.value = true
}
function actionLabel(name: string) {
  return (
    (
      {
        submit: '提交审核',
        approve: '审核通过',
        reject: '驳回',
        publish: '发布',
        offline: '下线',
        restore: '恢复上线',
      } as Record<string, string>
    )[name] || name
  )
}
async function action(
  row: PartyContent,
  name: 'submit' | 'approve' | 'reject' | 'publish' | 'offline' | 'restore',
) {
  if (!row.contentId || actionPendingId.value) return
  let note = ''
  try {
    if (['reject', 'offline'].includes(name))
      note = await ElMessageBox.prompt(name === 'reject' ? '请输入驳回原因' : '请输入下线原因', '操作确认', {
        inputPattern: /.{2,}/,
        inputErrorMessage: '至少输入 2 个字符',
      }).then((value) => value.value)
    else await ElMessageBox.confirm(`确认执行“${actionLabel(name)}”？`, '内容流程')
  } catch {
    return
  }
  actionPendingId.value = row.contentId
  try {
    await applyContentAction(row.contentId, name, note || undefined)
    ElMessage.success('操作已完成')
    await load()
  } catch (cause: unknown) {
    ElMessage.error(errorMessage(cause, '内容流程操作失败'))
  } finally {
    actionPendingId.value = undefined
  }
}
function status(row: PartyContent) {
  if (row.workflowStatus === 'draft') return row.reviewDecision === 'rejected' ? '已驳回' : '草稿'
  if (row.workflowStatus === 'reviewing')
    return row.reviewDecision === 'approved' ? '审核通过，待发布' : '待审核'
  return row.online ? '已发布' : '已下线'
}
onMounted(async () => {
  try {
    topics.value = await getAdminTopics()
  } catch (cause: unknown) {
    ElMessage.warning(errorMessage(cause, '专题列表暂时不可用'))
  }
  await Promise.all([load(), loadGovernanceSignals()])
})
watch(
  () => route.meta.governanceMode,
  () => load(true),
)
</script>
<template>
  <section class="panel">
    <div class="toolbar">
      <div>
        <span class="eyebrow">PARTY CONTENT GOVERNANCE</span>
        <h2>{{ reviewMode ? '审核中心' : publicationMode ? '发布管理' : '党建内容库' }}</h2>
        <p class="muted">
          {{
            reviewMode
              ? '核对正文、元数据和版本后决定通过或驳回。'
              : publicationMode
                ? '管理审核通过内容的发布、下线、恢复与新版本切换。'
                : '草稿、审核、发布与版本切换使用同一条可审计流程。'
          }}
        </p>
      </div>
      <el-button
        v-if="!reviewMode && !publicationMode"
        v-permission="'content:create'"
        type="primary"
        @click="openCreate"
        >新增内容</el-button
      >
    </div>
    <div v-if="Object.keys(governanceInsights).length" class="governance-signals">
      <div
        v-for="(label, key) in {
          scheduledOnline: '待上线',
          scheduledOffline: '待下线',
          expiringSoon: '30 天内到期',
          parsePending: '待处理解析',
          withoutTopics: '未归专题',
          duplicateReferences: '疑似重复文号',
          unhelpfulAnswers: '低评价问答',
          searchWithoutResult: '无结果搜索',
        }"
        :key="key"
        class="signal"
      >
        <span>{{ label }}</span
        ><strong>{{ governanceInsights[key] || 0 }}</strong>
      </div>
      <el-tooltip content="启用后，内容提交者不能审核或发布自己创建的内容。管理员仍可调整此策略。">
        <el-switch
          v-if="can('content:publish')"
          v-model="governancePolicy.contentSeparationOfDuties"
          inline-prompt
          active-text="职责分离"
          inactive-text="可自审"
          :loading="policySaving"
          @change="saveSeparationOfDuties(Boolean($event))"
        />
      </el-tooltip>
    </div>
    <div class="filter-bar">
      <el-input v-model="filters.keyword" placeholder="标题 / 文号 / 机关" clearable /><el-select
        v-model="filters.contentType"
        placeholder="全部分类"
        clearable
        ><el-option
          v-for="item in PARTY_CONTENT_CATEGORIES"
          :key="item.value"
          :label="item.label"
          :value="item.value" /></el-select
      ><el-select
        v-if="!reviewMode && !publicationMode"
        v-model="filters.workflowStatus"
        placeholder="全部流程"
        clearable
        ><el-option label="草稿" value="draft" /><el-option label="审核中" value="reviewing" /><el-option
          label="已发布"
          value="published" /></el-select
      ><el-input v-model="filters.spaceCode" placeholder="空间编码" clearable /><el-button @click="load(true)"
        >查询</el-button
      >
    </div>
    <PageState :loading="loading" :error="error" :empty="!rows.length"
      ><el-table :data="rows"
        ><el-table-column label="内容" min-width="250"
          ><template #default="s"
            ><strong>{{ s.row.title }}</strong>
            <div class="muted">
              {{ s.row.documentNumber || '无文号' }} · {{ s.row.issuingAuthority || '机关待补充' }}
            </div></template
          ></el-table-column
        ><el-table-column label="分类" width="120"
          ><template #default="s">{{ categoryLabel(s.row.contentType) }}</template></el-table-column
        ><el-table-column prop="sourceVersion" label="版本" width="70" /><el-table-column
          prop="parseStatus"
          label="解析"
          width="100"
        /><el-table-column label="流程" width="140"
          ><template #default="s"
            ><el-tag
              :type="
                s.row.workflowStatus === 'published'
                  ? s.row.online
                    ? 'success'
                    : 'info'
                  : s.row.reviewDecision === 'rejected'
                    ? 'danger'
                    : 'warning'
              "
              >{{ status(s.row) }}</el-tag
            ></template
          ></el-table-column
        ><el-table-column prop="validityStatus" label="效力" width="100" /><el-table-column
          prop="updateTime"
          label="更新时间"
          min-width="160"
        /><el-table-column label="操作" min-width="350" fixed="right"
          ><template #default="s"
            ><el-button link @click="inspect(s.row)">预览</el-button
            ><el-button
              v-if="s.row.workflowStatus === 'draft' && can('content:update')"
              link
              @click="openEdit(s.row)"
              >编辑</el-button
            ><el-button
              v-if="s.row.workflowStatus === 'draft' && can('content:submit')"
              link
              type="primary"
              :disabled="s.row.parseStatus !== 'completed' || actionPendingId === s.row.contentId"
              @click="action(s.row, 'submit')"
              >提交</el-button
            ><template v-if="s.row.workflowStatus === 'reviewing' && s.row.reviewDecision !== 'approved'"
              ><el-button
                v-if="can('content:review')"
                link
                type="success"
                :disabled="Boolean(actionPendingId)"
                @click="action(s.row, 'approve')"
                >通过</el-button
              ><el-button
                v-if="can('content:review')"
                link
                type="danger"
                :disabled="Boolean(actionPendingId)"
                @click="action(s.row, 'reject')"
                >驳回</el-button
              ></template
            ><el-button
              v-if="
                s.row.reviewDecision === 'approved' &&
                s.row.workflowStatus !== 'published' &&
                can('content:publish')
              "
              link
              type="primary"
              :disabled="Boolean(actionPendingId)"
              @click="action(s.row, 'publish')"
              >发布</el-button
            ><el-button
              v-if="s.row.workflowStatus === 'published' && s.row.online && can('content:publish')"
              link
              type="danger"
              :disabled="Boolean(actionPendingId)"
              @click="action(s.row, 'offline')"
              >下线</el-button
            ><el-button
              v-if="s.row.workflowStatus === 'published' && !s.row.online && can('content:publish')"
              link
              type="primary"
              :disabled="Boolean(actionPendingId)"
              @click="action(s.row, 'restore')"
              >恢复</el-button
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
  <el-dialog
    v-model="dialog"
    :title="editingId ? '编辑党建内容' : '新增党建内容'"
    width="820"
    :before-close="handleDialogBeforeClose"
    ><div v-if="!editingId" class="ingest-mode">
      <button :class="{ active: mode === 'text' }" @click="mode = 'text'">粘贴正文</button
      ><button :class="{ active: mode === 'file' }" @click="mode = 'file'">上传文件</button>
    </div>
    <el-form label-position="top"
      ><el-row :gutter="16"
        ><el-col :span="12"
          ><el-form-item label="标题"><el-input v-model="form.title" /></el-form-item></el-col
        ><el-col :span="12"
          ><el-form-item label="知识空间"
            ><el-input v-model="form.spaceCode" :disabled="Boolean(editingId)" /></el-form-item></el-col
        ><el-col :span="8"
          ><el-form-item label="一级分类"
            ><el-select v-model="form.contentType"
              ><el-option
                v-for="item in PARTY_CONTENT_CATEGORIES"
                :key="item.value"
                :label="item.label"
                :value="item.value" /></el-select></el-form-item></el-col
        ><el-col :span="8"
          ><el-form-item label="文号"><el-input v-model="form.documentNumber" /></el-form-item></el-col
        ><el-col :span="8"
          ><el-form-item label="发文机关"><el-input v-model="form.issuingAuthority" /></el-form-item></el-col
        ><el-col :span="8"
          ><el-form-item label="发布日期"
            ><input v-model="form.publishDate" class="native-field" type="date" /></el-form-item></el-col
        ><el-col :span="8"
          ><el-form-item label="生效日期"
            ><input v-model="form.effectiveDate" class="native-field" type="date" /></el-form-item></el-col
        ><el-col :span="8"
          ><el-form-item label="效力状态"
            ><el-select v-model="form.validityStatus"
              ><el-option label="现行有效" value="effective" /><el-option
                label="即将生效"
                value="pending" /><el-option label="已失效" value="expired" /><el-option
                label="已废止"
                value="repealed" /></el-select></el-form-item></el-col></el-row
      ><el-row :gutter="16"
        ><el-col :span="12"
          ><el-form-item label="计划上线时间（可选）"
            ><input
              v-model="form.scheduledOnlineAt"
              class="native-field"
              type="datetime-local" /></el-form-item
        ></el-col>
        <el-col :span="12"
          ><el-form-item label="计划下线时间（可选）"
            ><input
              v-model="form.scheduledOfflineAt"
              class="native-field"
              type="datetime-local" /></el-form-item></el-col
      ></el-row>
      ><el-form-item label="计划说明"
        ><el-input v-model="form.scheduleNote" maxlength="1000" placeholder="例如：专题学习周开始时上线"
      /></el-form-item>
      ><el-form-item label="专题"
        ><el-select v-model="form.topicCodes" multiple
          ><el-option
            v-for="item in topics"
            :key="item.topicCode"
            :label="item.name"
            :value="item.topicCode" /></el-select></el-form-item
      ><el-form-item label="摘要"><el-input v-model="form.summary" type="textarea" :rows="2" /></el-form-item
      ><el-form-item label="关键词（逗号分隔）"><el-input v-model="form.keywords" /></el-form-item
      ><el-form-item v-if="!editingId && mode === 'text'" label="正文"
        ><el-input v-model="form.content" type="textarea" :rows="10" /></el-form-item
      ><el-form-item v-else-if="!editingId" label="源文件"
        ><input
          type="file"
          @change="file = ($event.target as HTMLInputElement).files?.[0]" /></el-form-item></el-form
    ><template #footer
      ><el-button @click="requestDialogClose()">取消</el-button
      ><el-button
        type="primary"
        :loading="saving"
        :disabled="
          !form.title.trim() ||
          !form.spaceCode.trim() ||
          (!editingId && mode === 'text' && !form.content.trim()) ||
          (!editingId && mode === 'file' && !file)
        "
        @click="save"
        >{{ editingId ? '保存更改' : '保存草稿并解析' }}</el-button
      ></template
    ></el-dialog
  >
  <el-dialog v-model="detailDialog" title="内容预览与版本" width="900"
    ><template v-if="detail"
      ><el-descriptions :column="3" border
        ><el-descriptions-item label="标题" :span="3">{{ detail.title }}</el-descriptions-item
        ><el-descriptions-item label="文号">{{ detail.documentNumber || '—' }}</el-descriptions-item
        ><el-descriptions-item label="机关">{{ detail.issuingAuthority || '—' }}</el-descriptions-item
        ><el-descriptions-item label="效力">{{ detail.validityStatus }}</el-descriptions-item
        ><el-descriptions-item label="流程">{{ status(detail) }}</el-descriptions-item
        ><el-descriptions-item label="解析">{{ detail.parseStatus }}</el-descriptions-item
        ><el-descriptions-item label="版本">v{{ detail.sourceVersion }}</el-descriptions-item
        ><el-descriptions-item label="计划上线">{{
          (detail as PartyContent & { scheduledOnlineAt?: string }).scheduledOnlineAt || '—'
        }}</el-descriptions-item>
        <el-descriptions-item label="计划下线">{{
          (detail as PartyContent & { scheduledOfflineAt?: string }).scheduledOfflineAt || '—'
        }}</el-descriptions-item>
        ></el-descriptions
      >
      <div v-if="Object.keys(contentImpact).length" class="impact-summary">
        <el-tag>关联专题 {{ contentImpact.topicCount || 0 }}</el-tag
        ><el-tag>收藏 {{ contentImpact.favorites || 0 }}</el-tag>
        <el-tag>阅读 {{ contentImpact.readers || 0 }}</el-tag
        ><el-tag>检索分块 {{ contentImpact.citations || 0 }}</el-tag>
      </div>
      <div class="governance-preview">
        <p v-for="(section, index) in detail.sections" :key="String(section.chunk_id ?? index)">
          {{ section.content }}
        </p>
      </div>
      <h3>版本记录</h3>
      <el-table :data="pagedVersions"
        ><el-table-column prop="source_version" label="版本" /><el-table-column
          prop="parse_status"
          label="解析" /><el-table-column prop="workflow_status" label="流程" /><el-table-column
          prop="review_decision"
          label="审核" /><el-table-column prop="active" label="当前" /></el-table
      ><AppPagination
        v-model:page="versionPage"
        v-model:page-size="versionPageSize"
        :total="detail.versions?.length || 0" /></template
  ></el-dialog>
</template>
<style scoped>
.governance-signals {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin: 0 0 16px;
}

.signal {
  min-width: 92px;
  padding: 8px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
}

.signal span {
  display: block;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.signal strong {
  font-size: 19px;
  line-height: 1.3;
}

.impact-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 14px 0;
}
</style>
