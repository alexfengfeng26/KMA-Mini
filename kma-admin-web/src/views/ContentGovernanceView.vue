<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type TableInstance } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
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
import PageHeader from '../components/PageHeader.vue'
import PageState from '../components/PageState.vue'
import SpaceSelect from '../components/SpaceSelect.vue'
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
  router = useRouter(),
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
const selectedRows = ref<PartyContent[]>([])
const tableRef = ref<TableInstance | null>(null)
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
const hasGovernanceSignals = computed(
  () =>
    Object.keys(governanceInsights.value).length > 0 &&
    Object.values(governanceInsights.value).some((v) => (v as number) > 0),
)
const signalDefinitions: { key: string; label: string; severity: 'info' | 'warning' | 'danger' }[] = [
  { key: 'scheduledOnline', label: '待上线', severity: 'info' },
  { key: 'scheduledOffline', label: '待下线', severity: 'info' },
  { key: 'expiringSoon', label: '30 天内到期', severity: 'warning' },
  { key: 'parsePending', label: '待处理解析', severity: 'warning' },
  { key: 'withoutTopics', label: '未归专题', severity: 'warning' },
  { key: 'duplicateReferences', label: '疑似重复文号', severity: 'danger' },
  { key: 'unhelpfulAnswers', label: '低评价问答', severity: 'warning' },
  { key: 'searchWithoutResult', label: '无结果搜索', severity: 'info' },
]
const signalList = computed(() =>
  signalDefinitions
    .map((s, index) => ({ ...s, index, value: Number(governanceInsights.value[s.key] || 0) }))
    .filter((s) => s.value > 0)
    .sort((a, b) => b.value - a.value || a.index - b.index),
)
function signalClass(severity: string) {
  return `signal--${severity}`
}
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
  } else {
    filters.workflowStatus = ''
    filters.reviewDecision = ''
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
function statusTagType(row: PartyContent) {
  if (row.workflowStatus === 'draft') return row.reviewDecision === 'rejected' ? 'danger' : 'info'
  if (row.workflowStatus === 'reviewing') return row.reviewDecision === 'approved' ? 'warning' : 'warning'
  return row.online ? 'success' : 'info'
}
function workflowStepIndex(row: PartyContent): number {
  if (row.workflowStatus === 'draft') return 0
  if (row.workflowStatus === 'reviewing') return row.reviewDecision === 'approved' ? 2 : 1
  return row.online ? 3 : 3
}
interface ActionItem {
  name: 'submit' | 'approve' | 'reject' | 'publish' | 'offline' | 'restore' | 'edit'
  label: string
  disabled: boolean
}
function availableActions(row: PartyContent): ActionItem[] {
  const actions: ActionItem[] = []
  const busy = actionPendingId.value === row.contentId || Boolean(actionPendingId.value)
  if (row.workflowStatus === 'draft') {
    if (can('content:update')) actions.push({ name: 'edit', label: '编辑', disabled: busy })
    if (can('content:submit'))
      actions.push({
        name: 'submit',
        label: '提交审核',
        disabled: row.parseStatus !== 'completed' || busy,
      })
  } else if (
    row.workflowStatus === 'reviewing' &&
    row.reviewDecision !== 'approved' &&
    can('content:review')
  ) {
    actions.push({ name: 'approve', label: '通过', disabled: busy })
    actions.push({ name: 'reject', label: '驳回', disabled: busy })
  } else if (
    row.reviewDecision === 'approved' &&
    row.workflowStatus !== 'published' &&
    can('content:publish')
  ) {
    actions.push({ name: 'publish', label: '发布', disabled: busy })
  } else if (row.workflowStatus === 'published' && row.online && can('content:publish')) {
    actions.push({ name: 'offline', label: '下线', disabled: busy })
  } else if (row.workflowStatus === 'published' && !row.online && can('content:publish')) {
    actions.push({ name: 'restore', label: '恢复上线', disabled: busy })
  }
  return actions
}
function handleActionCommand(
  command: 'submit' | 'approve' | 'reject' | 'publish' | 'offline' | 'restore' | 'edit',
  row: PartyContent,
) {
  if (command === 'edit') return openEdit(row)
  action(row, command)
}
function selectionChange(rows: PartyContent[]) {
  selectedRows.value = rows
}
function clearSelection() {
  selectedRows.value = []
  tableRef.value?.clearSelection()
}
function eligibleForBatch(actionName: 'submit' | 'approve' | 'reject' | 'publish' | 'offline' | 'restore') {
  return selectedRows.value.filter((row) => {
    if (actionName === 'submit') return row.workflowStatus === 'draft' && row.parseStatus === 'completed'
    if (actionName === 'approve')
      return row.workflowStatus === 'reviewing' && row.reviewDecision !== 'approved'
    if (actionName === 'reject')
      return row.workflowStatus === 'reviewing' && row.reviewDecision !== 'approved'
    if (actionName === 'publish')
      return row.reviewDecision === 'approved' && row.workflowStatus !== 'published'
    if (actionName === 'offline') return row.workflowStatus === 'published' && row.online
    if (actionName === 'restore') return row.workflowStatus === 'published' && !row.online
    return false
  })
}
async function batchAction(actionName: 'submit' | 'approve' | 'reject' | 'publish' | 'offline' | 'restore') {
  const targets = eligibleForBatch(actionName)
  if (!targets.length) {
    ElMessage.warning('没有符合批量操作条件的内容')
    return
  }
  const label = actionLabel(actionName)
  try {
    await ElMessageBox.confirm(`确认对选中的 ${targets.length} 项内容执行“${label}”？`, '批量操作确认', {
      type: 'warning',
    })
  } catch {
    return
  }
  let success = 0
  let failed = 0
  for (const row of targets) {
    try {
      await applyContentAction(row.contentId!, actionName)
      success++
    } catch {
      failed++
    }
  }
  ElMessage.success(`批量${label}完成：成功 ${success} 条${failed ? `，失败 ${failed} 条` : ''}`)
  clearSelection()
  await load()
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
    <PageHeader
      eyebrow="PARTY CONTENT GOVERNANCE"
      :title="reviewMode ? '审核中心' : publicationMode ? '发布管理' : '党建内容库'"
      :description="
        reviewMode
          ? '核对正文、元数据和版本后决定通过或驳回。'
          : publicationMode
            ? '管理审核通过内容的发布、下线、恢复与新版本切换。'
            : '草稿、审核、发布与版本切换使用同一条可审计流程。'
      "
    >
      <template #actions>
        <div v-if="hasGovernanceSignals" class="governance-signals governance-signals--inline">
          <div
            v-for="s in signalList"
            :key="s.key"
            class="signal"
            :class="[signalClass(s.severity), { 'signal--zero': s.value === 0 }]"
          >
            <span>{{ s.label }}</span><strong>{{ s.value }}</strong>
          </div>
          <div class="signal signal--toggle">
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
        </div>
        <el-button
          v-if="!reviewMode && !publicationMode"
          v-permission="'content:create'"
          type="primary"
          @click="openCreate"
          >新增内容</el-button
        >
      </template>
    </PageHeader>
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
      ><SpaceSelect v-model="filters.spaceCode" placeholder="空间编码" clearable /><el-button
        @click="load(true)"
        >查询</el-button
      >
    </div>
    <div v-if="selectedRows.length" class="batch-bar">
      <span
        >已选择 <strong>{{ selectedRows.length }}</strong> 项</span
      >
      <div class="batch-actions">
        <el-button
          v-if="reviewMode && can('content:review')"
          type="success"
          size="small"
          :disabled="!eligibleForBatch('approve').length"
          @click="batchAction('approve')"
          >批量通过</el-button
        ><el-button
          v-if="reviewMode && can('content:review')"
          type="danger"
          size="small"
          :disabled="!eligibleForBatch('reject').length"
          @click="batchAction('reject')"
          >批量驳回</el-button
        ><el-button
          v-if="publicationMode && can('content:publish')"
          type="primary"
          size="small"
          :disabled="!eligibleForBatch('publish').length"
          @click="batchAction('publish')"
          >批量发布</el-button
        ><el-button
          v-if="publicationMode && can('content:publish')"
          type="danger"
          size="small"
          :disabled="!eligibleForBatch('offline').length"
          @click="batchAction('offline')"
          >批量下线</el-button
        ><el-button
          v-if="!reviewMode && !publicationMode && can('content:submit')"
          type="primary"
          size="small"
          :disabled="!eligibleForBatch('submit').length"
          @click="batchAction('submit')"
          >批量提交</el-button
        ><el-button link size="small" @click="clearSelection">取消选择</el-button>
      </div>
    </div>
    <PageState :loading="loading" :error="error" :empty="!rows.length"
      ><el-table ref="tableRef" :data="rows" row-key="contentId" @selection-change="selectionChange"
        ><el-table-column type="selection" width="45" /><el-table-column label="内容" min-width="250"
          ><template #default="s"
            ><strong>{{ s.row.title }}</strong>
            <div class="muted">
              {{ s.row.documentNumber || '无文号' }} · {{ s.row.issuingAuthority || '机关待补充' }}
            </div>
            <div v-if="!reviewMode && !publicationMode" class="workflow-link">
              <el-button
                v-if="s.row.workflowStatus === 'reviewing' && can('content:review')"
                link
                type="primary"
                size="small"
                @click="router.push('/console/reviews')"
                >去审核中心处理 →</el-button
              ><el-button
                v-else-if="
                  s.row.reviewDecision === 'approved' &&
                  s.row.workflowStatus !== 'published' &&
                  can('content:publish')
                "
                link
                type="primary"
                size="small"
                @click="router.push('/console/publications')"
                >去发布管理发布 →</el-button
              >
            </div></template
          ></el-table-column
        ><el-table-column label="分类" width="120"
          ><template #default="s">{{ categoryLabel(s.row.contentType) }}</template></el-table-column
        ><el-table-column prop="sourceVersion" label="版本" width="70" /><el-table-column
          prop="parseStatus"
          label="解析"
          width="100"
        /><el-table-column label="流程" width="110"
          ><template #default="s"
            ><el-tag :type="statusTagType(s.row)" size="small">{{ status(s.row) }}</el-tag></template
          ></el-table-column
        ><el-table-column prop="validityStatus" label="效力" width="90" /><el-table-column
          prop="updateTime"
          label="更新时间"
          min-width="150"
        /><el-table-column label="操作" width="150"
          ><template #default="s"
            ><div class="action-cell">
              <el-button link type="primary" size="small" @click="inspect(s.row)">详情</el-button
              ><el-dropdown
                v-if="availableActions(s.row).length"
                trigger="click"
                placement="bottom-end"
                @command="(cmd) => handleActionCommand(cmd, s.row)"
                ><el-button link size="small"
                  >更多<el-icon class="el-icon--right"><arrow-down /></el-icon></el-button
                ><template #dropdown
                  ><el-dropdown-menu
                    ><el-dropdown-item
                      v-for="item in availableActions(s.row)"
                      :key="item.name"
                      :command="item.name"
                      :disabled="item.disabled"
                      >{{ item.label }}</el-dropdown-item
                    ></el-dropdown-menu
                  ></template
                ></el-dropdown
              >
            </div></template
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
            ><SpaceSelect v-model="form.spaceCode" :disabled="Boolean(editingId)" /></el-form-item></el-col
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
  <el-drawer
    v-model="detailDialog"
    title="内容详情"
    size="680"
    direction="rtl"
    destroy-on-close
    class="content-detail-drawer"
    ><template v-if="detail"
      ><div class="detail-header">
        <h3>{{ detail.title }}</h3>
        <el-tag :type="statusTagType(detail)" size="small">{{ status(detail) }}</el-tag>
      </div>
      <div class="workflow-steps">
        <div
          v-for="(step, idx) in [
            { key: 'draft', label: '草稿' },
            { key: 'reviewing', label: '审核中' },
            { key: 'approved', label: '待发布' },
            { key: 'published', label: '已发布' },
          ]"
          :key="step.key"
          class="workflow-step"
          :class="{
            active: workflowStepIndex(detail) >= idx,
            current: workflowStepIndex(detail) === idx,
          }"
        >
          {{ step.label }}
        </div>
      </div>
      <el-descriptions :column="2" border
        ><el-descriptions-item label="文号">{{ detail.documentNumber || '—' }}</el-descriptions-item
        ><el-descriptions-item label="发文机关">{{ detail.issuingAuthority || '—' }}</el-descriptions-item
        ><el-descriptions-item label="分类">{{ categoryLabel(detail.contentType) }}</el-descriptions-item
        ><el-descriptions-item label="效力">{{ detail.validityStatus }}</el-descriptions-item
        ><el-descriptions-item label="版本">v{{ detail.sourceVersion }}</el-descriptions-item
        ><el-descriptions-item label="解析">{{ detail.parseStatus }}</el-descriptions-item
        ><el-descriptions-item label="计划上线">{{
          (detail as PartyContent & { scheduledOnlineAt?: string }).scheduledOnlineAt || '—'
        }}</el-descriptions-item>
        <el-descriptions-item label="计划下线">{{
          (detail as PartyContent & { scheduledOfflineAt?: string }).scheduledOfflineAt || '—'
        }}</el-descriptions-item>
        ></el-descriptions
      >
      <div v-if="detail.topicCodes?.length" class="detail-topics">
        <el-tag v-for="code in detail.topicCodes" :key="code" size="small">{{ code }}</el-tag>
      </div>
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
      <h4>版本记录</h4>
      <el-table :data="pagedVersions" size="small"
        ><el-table-column prop="source_version" label="版本" /><el-table-column
          prop="parse_status"
          label="解析" /><el-table-column prop="workflow_status" label="流程" /><el-table-column
          prop="review_decision"
          label="审核" /><el-table-column prop="active" label="当前" /></el-table
      ><AppPagination
        v-model:page="versionPage"
        v-model:page-size="versionPageSize"
        :total="detail.versions?.length || 0"
      />
      <div class="drawer-actions">
        <el-button
          v-for="item in availableActions(detail).filter((a) => a.name !== 'edit')"
          :key="item.name"
          :type="item.name === 'offline' || item.name === 'reject' ? 'danger' : 'primary'"
          :disabled="item.disabled"
          @click="handleActionCommand(item.name, detail)"
          >{{ item.label }}</el-button
        ><el-button v-if="availableActions(detail).some((a) => a.name === 'edit')" @click="openEdit(detail)"
          >编辑</el-button
        >
      </div></template
    ></el-drawer
  >
</template>
<style scoped>
.panel {
  padding-top: 8px;
}

.panel :deep(.page-header) {
  align-items: center;
  margin-bottom: 0;
}

.panel :deep(.page-header h2),
.panel :deep(.page-header p) {
  margin: 0;
}

.governance-signals {
  display: flex;
  flex-wrap: nowrap;
  gap: 4px;
  align-items: center;
  margin: 0 0 2px;
  overflow-x: auto;
}

.governance-signals--inline {
  margin: 0;
}

.signal {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
  min-width: auto;
  padding: 1px 6px;
  border: 1px solid var(--el-border-color-lighter);
  border-left: 3px solid transparent;
  border-radius: 6px;
  background: var(--el-fill-color-lighter);
  transition: 150ms ease;
  white-space: nowrap;
}

.signal--zero {
  opacity: 0.5;
  filter: grayscale(0.7);
}

.signal--toggle {
  align-items: center;
  gap: 0;
  padding: 1px 6px;
  border-left: 1px solid var(--el-border-color-lighter);
  cursor: default;
}

.signal--toggle .el-switch {
  --el-switch-core-height: 16px;
}

.signal--info {
  border-left-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.signal--warning {
  border-left-color: var(--el-color-warning);
  background: var(--el-color-warning-light-9);
}

.signal--danger {
  border-left-color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
}

.signal--zero.signal--info,
.signal--zero.signal--warning,
.signal--zero.signal--danger {
  background: var(--el-fill-color-lighter);
}

.signal span {
  color: var(--el-text-color-secondary);
  font-size: 11px;
  line-height: 1;
}

.signal strong {
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
}

.signal--info strong {
  color: var(--el-color-primary);
}

.signal--warning strong {
  color: var(--el-color-warning);
}

.signal--danger strong {
  color: var(--el-color-danger);
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 6px;
}

.filter-bar > * {
  flex: 0 0 auto;
}

.filter-bar .el-input,
.filter-bar .el-select {
  flex: 1 1 130px;
  min-width: 130px;
  max-width: 180px;
}

.batch-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  margin-bottom: 8px;
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: 8px;
}

.batch-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.impact-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 14px 0;
}

.action-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.detail-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.detail-header h3 {
  margin: 0;
  font-size: 18px;
  line-height: 1.4;
}

.workflow-steps {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 20px;
  padding: 12px;
  background: var(--el-fill-color-lighter);
  border-radius: 8px;
}

.workflow-step {
  flex: 1;
  text-align: center;
  padding: 6px 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  background: var(--el-bg-color);
  border-radius: 6px;
  border: 1px solid var(--el-border-color-lighter);
}

.workflow-step.active {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}

.workflow-step.current {
  font-weight: 600;
  box-shadow: 0 0 0 2px var(--el-color-primary-light-7);
}

.workflow-link {
  margin-top: 4px;
}

.workflow-link .el-button {
  padding: 0;
  height: auto;
}

.detail-topics {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 12px 0;
}

.drawer-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}

:deep(.content-detail-drawer .el-drawer__body) {
  padding: 20px;
  overflow-y: auto;
}
</style>
