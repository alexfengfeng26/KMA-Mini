<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '../components/PageHeader.vue'
import StatusTag from '../components/StatusTag.vue'
import {
  createPortalDraft,
  createPortalSite,
  deletePortalAsset,
  getPortalAnalytics,
  getPortalVersion,
  listPortalAssets,
  listPortalExtensionCatalog,
  listPortalSites,
  listPortalVersions,
  portalVersionAction,
  rollbackPortalVersion,
  updatePortalDraft,
  uploadPortalAsset,
  type PortalAsset,
  type PortalAnalyticsSummary,
  type PortalExtensionCatalogItem,
} from '../api/portalSites'
import { cmsBlockDefinitions } from '../cms/blockDefinitions'
import { visualPacks } from '../cms/visualPacks'
import {
  isPortalSiteConfigV2,
  type CmsBlockConfigV2,
  type CmsPageConfigV2,
  type PortalConfigVersion,
  type PortalExtensionBinding,
  type PortalScenario,
  type PortalSiteConfigV2,
  type PortalSiteSummary,
} from '../cms/siteConfig'

const sites = ref<PortalSiteSummary[]>([])
const versions = ref<PortalConfigVersion[]>([])
const selectedSite = ref<PortalSiteSummary | null>(null)
const selectedVersion = ref<PortalConfigVersion | null>(null)
const draft = ref<PortalSiteConfigV2 | null>(null)
const assets = ref<PortalAsset[]>([])
const analytics = ref<PortalAnalyticsSummary | null>(null)
const extensionCatalog = ref<PortalExtensionCatalogItem[]>([])
const assetType = ref('logo')
const assetFile = ref<File | null>(null)
const loading = ref(false)
const actionLoading = ref('')
const viewport = ref<1440 | 1024 | 390>(1440)
const activeSection = ref<DesignerSection>('pages')
const selectedBlockId = ref('')
const previewOpen = ref(false)
const previewFullscreen = ref(false)
const inspectorOpen = ref(false)
const compactViewport = ref(false)
const recoverySnapshot = ref<DesignerRecoverySnapshot | null>(null)
const baselineDraft = ref('')
let snapshotTimer: number | undefined
const createOpen = ref(false)
const activePageSlug = ref('home')
const newPageSlug = ref('')
const createForm = reactive({
  siteKey: '',
  name: '',
  scenario: 'party' as PortalScenario,
  defaultSite: false,
})

const editable = computed(() => selectedVersion.value?.status === 'draft')
const pages = computed(() => Object.values(draft.value?.pages || {}))
const activePage = computed<CmsPageConfigV2 | null>(() => draft.value?.pages[activePageSlug.value] || null)
const mainBlocks = computed(() => activePage.value?.regions.main || [])
const configurableModules = [
  { key: 'portal.library', label: '资料中心' },
  { key: 'portal.qa', label: 'AI 问答' },
  { key: 'portal.topics', label: '专题栏目' },
  { key: 'portal.custom-pages', label: '自定义页面' },
  { key: 'portal.favorites', label: '收藏历史' },
]

type DesignerSection =
  | 'versions'
  | 'site'
  | 'navigation'
  | 'modules'
  | 'pages'
  | 'blocks'
  | 'scope'
  | 'appearance'
  | 'assets'
  | 'extensions'
  | 'analytics'
type DesignerRecoverySnapshot = {
  lockVersion: number
  config: PortalSiteConfigV2
  savedAt: string
}

const designerSections: Array<{
  id: DesignerSection
  label: string
  hint: string
}> = [
  { id: 'versions', label: '版本', hint: '草稿与发布' },
  { id: 'site', label: '站点', hint: '基本信息' },
  { id: 'navigation', label: '导航', hint: '门户入口' },
  { id: 'modules', label: '模块', hint: '功能可见性' },
  { id: 'pages', label: '页面', hint: '结构与布局' },
  { id: 'blocks', label: '区块', hint: '内容编排' },
  { id: 'scope', label: '范围', hint: '内容边界' },
  { id: 'appearance', label: '外观', hint: '视觉包与主题' },
  { id: 'assets', label: '资产', hint: '品牌资源' },
  { id: 'extensions', label: '扩展', hint: '已签名组件' },
  { id: 'analytics', label: '分析', hint: '访问表现' },
]
const selectedBlock = computed(
  () => mainBlocks.value.find((block) => block.id === selectedBlockId.value) || null,
)
const hasUnsavedChanges = computed(
  () => Boolean(draft.value) && JSON.stringify(draft.value) !== baselineDraft.value,
)
const workspaceKey = computed(
  () => `kma:portal-designer:workspace:v1:${selectedSite.value?.siteKey || 'none'}`,
)
const draftSnapshotKey = computed(
  () =>
    `kma:portal-designer:recovery:v1:${selectedSite.value?.siteKey || 'none'}:${selectedVersion.value?.versionId || 'none'}`,
)
const previewDrawerSize = computed(() => (compactViewport.value || previewFullscreen.value ? '100%' : '42%'))

function restoreWorkspace() {
  const raw = localStorage.getItem(workspaceKey.value)
  if (!raw) return
  try {
    const value = JSON.parse(raw) as Partial<{
      section: DesignerSection
      pageSlug: string
      blockId: string
      previewOpen: boolean
      previewFullscreen: boolean
      inspectorOpen: boolean
      viewport: 1440 | 1024 | 390
    }>
    if (designerSections.some((section) => section.id === value.section)) activeSection.value = value.section!
    if (value.pageSlug && draft.value?.pages[value.pageSlug]) activePageSlug.value = value.pageSlug
    if (value.blockId && mainBlocks.value.some((block) => block.id === value.blockId))
      selectedBlockId.value = value.blockId
    previewOpen.value = Boolean(value.previewOpen)
    previewFullscreen.value = Boolean(value.previewFullscreen)
    inspectorOpen.value = Boolean(value.inspectorOpen && selectedBlockId.value)
    if ([1440, 1024, 390].includes(value.viewport || 0)) viewport.value = value.viewport!
  } catch {
    localStorage.removeItem(workspaceKey.value)
  }
}

function persistWorkspace() {
  if (!selectedSite.value) return
  localStorage.setItem(
    workspaceKey.value,
    JSON.stringify({
      section: activeSection.value,
      pageSlug: activePageSlug.value,
      blockId: selectedBlockId.value,
      previewOpen: previewOpen.value,
      previewFullscreen: previewFullscreen.value,
      inspectorOpen: inspectorOpen.value,
      viewport: viewport.value,
    }),
  )
}

function loadRecoverySnapshot() {
  recoverySnapshot.value = null
  if (!selectedVersion.value || !draft.value) return
  try {
    const value = JSON.parse(
      localStorage.getItem(draftSnapshotKey.value) || 'null',
    ) as DesignerRecoverySnapshot | null
    if (value?.lockVersion === selectedVersion.value.lockVersion && isPortalSiteConfigV2(value.config))
      recoverySnapshot.value = value
  } catch {
    localStorage.removeItem(draftSnapshotKey.value)
  }
}

function persistRecoverySnapshot() {
  if (!editable.value || !draft.value || !selectedVersion.value || !hasUnsavedChanges.value) return
  localStorage.setItem(
    draftSnapshotKey.value,
    JSON.stringify({
      lockVersion: selectedVersion.value.lockVersion,
      config: draft.value,
      savedAt: new Date().toISOString(),
    }),
  )
  recoverySnapshot.value = JSON.parse(
    localStorage.getItem(draftSnapshotKey.value) || 'null',
  ) as DesignerRecoverySnapshot
}

function restoreRecoverySnapshot() {
  if (!recoverySnapshot.value) return
  draft.value = structuredClone(recoverySnapshot.value.config)
  selectedBlockId.value = ''
  inspectorOpen.value = false
  ElMessage.success('已恢复本机未保存编辑；请校验后保存草稿')
}

function discardRecoverySnapshot() {
  localStorage.removeItem(draftSnapshotKey.value)
  recoverySnapshot.value = null
}

function updateViewportMode() {
  compactViewport.value = window.innerWidth < 768
}

function openPreview(fullscreen = false) {
  previewFullscreen.value = fullscreen
  previewOpen.value = true
}

function selectBlock(block: CmsBlockConfigV2) {
  block.props ||= {}
  selectedBlockId.value = block.id
  inspectorOpen.value = true
}

function blockText(block: CmsBlockConfigV2, key: 'title' | 'markdown') {
  return String(block.props?.[key] || '')
}

function setBlockText(block: CmsBlockConfigV2, key: 'title' | 'markdown', value: string) {
  ;(block.props ||= {})[key] = value
}

function removeSelectedBlock() {
  if (!selectedBlock.value) return
  const index = mainBlocks.value.indexOf(selectedBlock.value)
  if (index >= 0) mainBlocks.value.splice(index, 1)
  inspectorOpen.value = false
  selectedBlockId.value = ''
}

function addExtension(extension: PortalExtensionCatalogItem) {
  if (!activePage.value || !editable.value) return
  const bindings = (activePage.value.extensions ||= [])
  const slotKey = `extension-${extension.extensionId}-${bindings.length + 1}`
  bindings.push({
    extensionId: extension.extensionId,
    version: extension.version,
    slotKey,
    region: 'main',
    enabled: true,
    config: { title: extension.displayName, limit: 3 },
  })
}

function removeExtension(index: number) {
  activePage.value?.extensions?.splice(index, 1)
}

type ExtensionSetting = { key: string; label: string; type: 'string' | 'number' | 'boolean' }

function extensionSettings(extensionId: string, version: string): ExtensionSetting[] {
  const manifest = extensionCatalog.value.find(
    (item) => item.extensionId === extensionId && item.version === version,
  )?.manifest
  const schema = manifest?.settingsSchema as
    { properties?: Record<string, { type?: string; title?: string }> } | undefined
  return Object.entries(schema?.properties || {}).flatMap(([key, definition]) => {
    const type = definition.type
    if (type !== 'string' && type !== 'integer' && type !== 'number' && type !== 'boolean') return []
    return [
      {
        key,
        label: definition.title || key,
        type: type === 'integer' || type === 'number' ? 'number' : type,
      },
    ]
  })
}

function setExtensionSetting(
  extension: PortalExtensionBinding,
  key: string,
  value: string | number | boolean,
) {
  ;(extension.config ||= {})[key] = value
}

async function validateConfig(value: PortalSiteConfigV2) {
  const { validatePortalSiteConfig } = await import('../cms/siteConfigValidator')
  return validatePortalSiteConfig(value)
}
const previewWidth = computed(() => `${Math.min(viewport.value, 1040)}px`)
const previewDocument = computed(() => {
  if (!draft.value) return '<!doctype html><html><body>请选择配置版本</body></html>'
  const config = draft.value
  const primary = config.theme.tokens.colorPrimary || '#0b766e'
  const background = config.theme.tokens.colorBackground || '#f7f4eb'
  const surface = config.theme.tokens.colorSurface || '#fffdf8'
  const blocks = mainBlocks.value
    .filter((block) => block.enabled)
    .map(
      (block) =>
        `<section style="grid-column:span ${Math.min(12, Math.max(1, block.span || 12))}"><small>${escapeHtml(
          cmsBlockDefinitions.find((item) => item.type === block.type)?.category || '知识服务',
        )}</small><h2>${escapeHtml(
          String(
            block.props?.title ||
              cmsBlockDefinitions.find((item) => item.type === block.type)?.title ||
              block.type,
          ),
        )}</h2><p>${escapeHtml(blockPreview(block))}</p></section>`,
    )
    .join('')
  return `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width"><style>
    *{box-sizing:border-box}body{margin:0;background:${background};color:#17393b;font:14px/1.6 "DM Sans",sans-serif}
    header{height:64px;padding:0 28px;display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid #d8e2df;background:${surface}}
    header strong{font-size:17px}nav{display:flex;gap:20px;color:#466568}main{max-width:1180px;margin:auto;padding:28px;display:grid;grid-template-columns:repeat(12,minmax(0,1fr));gap:18px}
    section{min-height:120px;padding:18px;border:1px solid #d8e2df;border-radius:12px;background:${surface}}
    section:first-child{min-height:180px;background:${primary};color:white}small{font-weight:700;letter-spacing:.08em}h2{margin:6px 0 10px;font-size:22px}p{margin:0;opacity:.78}
    footer{padding:20px 28px;text-align:center;color:#607477}@media(max-width:700px){header{height:56px;padding:0 16px}nav{display:none}main{padding:16px}section{grid-column:1/-1!important}}
    ${config.theme.customCss || ''}
  </style></head><body><header><strong>${escapeHtml(config.site.name)}</strong><nav>${config.shell.navigation
    .map((item) => `<span>${escapeHtml(item.label)}</span>`)
    .join('')}</nav></header><main>${blocks}</main><footer>${escapeHtml(
    config.shell.footer.text || '内部知识服务',
  )}</footer></body></html>`
})

function escapeHtml(value: string) {
  return value.replace(/[&<>"']/g, (character) => {
    const entities: Record<string, string> = {
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;',
    }
    return entities[character]
  })
}

function blockPreview(block: CmsBlockConfigV2) {
  if (block.type === 'hero-search') return '搜索标题、正文或文号，也可以直接向 AI 知识助手提问。'
  if (block.type.includes('category')) return '按栏目、业务领域和适用对象组织知识内容。'
  if (block.type.includes('ai') || block.type.includes('question')) return '提供带引用、可核对的知识回答。'
  return '区块数据将在门户运行时按站点内容范围和用户权限加载。'
}

async function loadSites(preferred?: string) {
  loading.value = true
  try {
    sites.value = await listPortalSites()
    const site =
      sites.value.find((item) => item.siteKey === preferred) ||
      selectedSite.value ||
      sites.value.find((item) => item.defaultSite) ||
      sites.value[0]
    if (site) await selectSite(site)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '站点列表加载失败')
  } finally {
    loading.value = false
  }
}

async function selectSite(site: PortalSiteSummary) {
  selectedSite.value = site
  ;[versions.value, assets.value, analytics.value, extensionCatalog.value] = await Promise.all([
    listPortalVersions(site.siteKey),
    listPortalAssets(site.siteKey),
    getPortalAnalytics(site.siteKey).catch(() => null),
    listPortalExtensionCatalog().catch(() => []),
  ])
  activePageSlug.value = 'home'
  const version =
    versions.value.find((item) => item.status === 'draft') ||
    versions.value.find((item) => item.status === 'published') ||
    versions.value[0]
  if (version) await selectVersion(version)
  else {
    selectedVersion.value = null
    draft.value = null
  }
  restoreWorkspace()
}

function selectAssetFile(event: Event) {
  assetFile.value = (event.target as HTMLInputElement).files?.[0] || null
}

async function uploadAsset() {
  if (!selectedSite.value || !assetFile.value) return
  actionLoading.value = 'asset'
  try {
    const created = await uploadPortalAsset(selectedSite.value.siteKey, assetType.value, assetFile.value)
    assets.value = await listPortalAssets(selectedSite.value.siteKey)
    assetFile.value = null
    ElMessage.success(`资源已上传，可在配置中引用 ${created.url}`)
  } finally {
    actionLoading.value = ''
  }
}

async function removeAsset(asset: PortalAsset) {
  if (!selectedSite.value) return
  await ElMessageBox.confirm(`删除资源“${asset.originalName}”？`, '删除门户资源')
  await deletePortalAsset(selectedSite.value.siteKey, asset.assetId)
  assets.value = await listPortalAssets(selectedSite.value.siteKey)
  ElMessage.success('资源已删除')
}

async function selectVersion(version: PortalConfigVersion) {
  if (!selectedSite.value) return
  const detail = await getPortalVersion(selectedSite.value.siteKey, version.versionId)
  selectedVersion.value = detail
  if (isPortalSiteConfigV2(detail.config)) draft.value = structuredClone(detail.config)
  else draft.value = null
  if (draft.value && !draft.value.pages[activePageSlug.value]) activePageSlug.value = 'home'
  selectedBlockId.value = ''
  inspectorOpen.value = false
  baselineDraft.value = draft.value ? JSON.stringify(draft.value) : ''
  loadRecoverySnapshot()
}

async function createSite() {
  if (!createForm.siteKey || !createForm.name) {
    ElMessage.warning('请填写站点编码和名称')
    return
  }
  actionLoading.value = 'create'
  try {
    await createPortalSite(createForm)
    createOpen.value = false
    ElMessage.success('站点和场景草稿已创建')
    await loadSites(createForm.siteKey)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '创建站点失败')
  } finally {
    actionLoading.value = ''
  }
}

async function createDraft() {
  if (!selectedSite.value) return
  actionLoading.value = 'draft'
  try {
    const version = await createPortalDraft(selectedSite.value.siteKey)
    await selectSite(selectedSite.value)
    await selectVersion(version)
    ElMessage.success('已从最近发布版本创建新草稿')
  } finally {
    actionLoading.value = ''
  }
}

async function saveDraft() {
  if (!selectedSite.value || !selectedVersion.value || !draft.value) return
  const validation = await validateConfig(draft.value)
  if (!validation.valid) {
    ElMessage.error(validation.issues.slice(0, 3).join('；'))
    return
  }
  actionLoading.value = 'save'
  try {
    const version = await updatePortalDraft(
      selectedSite.value.siteKey,
      selectedVersion.value,
      draft.value,
      '设计中心保存',
    )
    selectedVersion.value = version
    baselineDraft.value = JSON.stringify(draft.value)
    discardRecoverySnapshot()
    ElMessage.success('草稿已保存到服务器')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    actionLoading.value = ''
  }
}

async function runAction(action: 'validate' | 'submit' | 'approve' | 'reject' | 'publish') {
  if (!selectedSite.value || !selectedVersion.value) return
  if (action === 'validate' && draft.value) {
    const local = await validateConfig(draft.value)
    if (!local.valid) {
      ElMessage.warning(local.issues.slice(0, 5).join('；'))
      return
    }
  }
  if (action !== 'validate') {
    await ElMessageBox.confirm(`确定要执行“${actionLabel(action)}”吗？`, '配置版本操作')
  }
  actionLoading.value = action
  try {
    const result = (await portalVersionAction(
      selectedSite.value.siteKey,
      action,
      selectedVersion.value.versionId,
    )) as { valid?: boolean; issues?: string[] } | undefined
    if (action === 'validate') {
      if (result?.valid) ElMessage.success('配置契约、内容范围和资源引用校验通过')
      else ElMessage.warning(result?.issues?.join('；') || '配置校验未通过')
    } else {
      ElMessage.success(`${actionLabel(action)}成功`)
      await selectSite(selectedSite.value)
    }
  } finally {
    actionLoading.value = ''
  }
}

async function rollback(version: PortalConfigVersion) {
  if (!selectedSite.value) return
  await ElMessageBox.confirm(`将版本 V${version.versionNo} 重新发布为新版本？`, '回滚门户')
  await rollbackPortalVersion(selectedSite.value.siteKey, version.versionId)
  ElMessage.success('回滚完成，所有门户节点将读取新的发布版本')
  await selectSite(selectedSite.value)
}

function addBlock(type: CmsBlockConfigV2['type']) {
  if (!draft.value || !editable.value) return
  const existing = new Set(mainBlocks.value.map((item) => item.id))
  let id: string = type
  let index = 1
  while (existing.has(id)) id = `${type}-${++index}`
  mainBlocks.value.push({ id, type, enabled: true, variant: 'default', span: 12 })
  selectBlock(mainBlocks.value.at(-1)!)
}

function moveBlock(index: number, direction: -1 | 1) {
  const target = index + direction
  if (target < 0 || target >= mainBlocks.value.length) return
  const [block] = mainBlocks.value.splice(index, 1)
  mainBlocks.value.splice(target, 0, block)
}

function addNavigation() {
  if (!draft.value || !editable.value) return
  const index = draft.value.shell.navigation.length + 1
  draft.value.shell.navigation.push({
    id: `navigation-${index}`,
    label: `导航 ${index}`,
    target: 'home',
  })
}

function addPage() {
  if (!draft.value || !editable.value) return
  const slug = newPageSlug.value.trim().toLowerCase()
  if (!/^[a-z][a-z0-9-]{1,63}$/.test(slug)) {
    ElMessage.warning('页面 Slug 必须以字母开头，只能包含小写字母、数字和连字符')
    return
  }
  if (draft.value.pages[slug]) {
    ElMessage.warning('页面 Slug 已存在')
    return
  }
  draft.value.pages[slug] = {
    slug,
    layout: 'twelve-grid',
    regions: { main: [] },
  }
  activePageSlug.value = slug
  newPageSlug.value = ''
}

function removePage(page: CmsPageConfigV2) {
  if (!draft.value || !editable.value || page.slug === 'home') return
  delete draft.value.pages[page.slug]
  draft.value.shell.navigation = draft.value.shell.navigation.filter(
    (item) => item.target !== `page/${page.slug}`,
  )
  activePageSlug.value = 'home'
}

function setModule(key: string, enabled: boolean) {
  if (!draft.value || !editable.value) return
  draft.value.modules[key] = enabled
}

function moduleEnabled(key: string) {
  return draft.value?.modules[key] ?? true
}

function actionLabel(action: string) {
  return (
    {
      validate: '校验',
      submit: '提交审核',
      approve: '审核通过',
      reject: '驳回',
      publish: '发布',
    }[action] || action
  )
}

watch(
  [activeSection, activePageSlug, selectedBlockId, previewOpen, previewFullscreen, inspectorOpen, viewport],
  persistWorkspace,
)
watch(
  draft,
  () => {
    if (snapshotTimer) window.clearTimeout(snapshotTimer)
    snapshotTimer = window.setTimeout(persistRecoverySnapshot, 450)
  },
  { deep: true },
)

onMounted(() => {
  updateViewportMode()
  window.addEventListener('resize', updateViewportMode)
  void loadSites()
})
onBeforeUnmount(() => {
  if (snapshotTimer) window.clearTimeout(snapshotTimer)
  window.removeEventListener('resize', updateViewportMode)
})
</script>

<template>
  <div class="portal-designer">
    <PageHeader
      eyebrow="KMA CONTROL PLANE"
      title="门户设计中心"
      description="管理站点、页面、主题与内容范围；草稿审核通过后原子发布。"
    >
      <template #actions>
        <el-button @click="loadSites(selectedSite?.siteKey)">刷新</el-button>
        <el-button type="primary" @click="createOpen = true">创建站点</el-button>
      </template>
    </PageHeader>

    <div v-loading="loading" class="designer-shell">
      <aside class="designer-sites panel">
        <div class="designer-section-title">
          <strong>站点列表</strong>
          <span>{{ sites.length }}</span>
        </div>
        <button
          v-for="site in sites"
          :key="site.siteKey"
          class="designer-site"
          :class="{ active: selectedSite?.siteKey === site.siteKey }"
          @click="selectSite(site)"
        >
          <span class="designer-site-copy">
            <span class="designer-site-name">
              <strong>{{ site.name }}</strong>
              <em v-if="site.defaultSite">默认</em>
            </span>
            <small>{{ site.siteKey }} · {{ site.scenario }}</small>
            <small
              >站点导航 · {{ site.publishedVersion ? `V${site.publishedVersion} 已发布` : '待确认' }}</small
            >
          </span>
          <StatusTag :status="site.status" />
        </button>
      </aside>

      <main v-if="selectedSite" class="designer-main panel">
        <div class="designer-toolbar">
          <div class="designer-site-summary">
            <strong>{{ selectedSite.name }}</strong>
            <span>{{ selectedSite.siteKey }} · {{ selectedSite.scenario }}</span>
          </div>
          <div class="designer-actions">
            <el-select
              class="designer-site-switcher"
              :model-value="selectedSite"
              aria-label="切换站点"
              @update:model-value="selectSite"
            >
              <el-option v-for="site in sites" :key="site.siteKey" :label="site.name" :value="site" />
            </el-select>
            <span v-if="hasUnsavedChanges" class="designer-unsaved" role="status">本机有未保存修改</span>
            <el-button aria-label="打开实时预览" @click="openPreview()">打开预览</el-button>
            <el-button aria-label="全屏打开实时预览" @click="openPreview(true)">全屏预览</el-button>
            <el-button aria-label="打开区块属性" :disabled="!selectedBlock" @click="inspectorOpen = true">
              区块属性
            </el-button>
            <el-button :loading="actionLoading === 'draft'" @click="createDraft">新建草稿</el-button>
            <el-button
              :disabled="!editable"
              :loading="actionLoading === 'save'"
              type="primary"
              @click="saveDraft"
            >
              保存草稿
            </el-button>
            <el-button :disabled="!selectedVersion" @click="runAction('validate')">校验</el-button>
            <el-button v-if="selectedVersion?.status === 'draft'" type="warning" @click="runAction('submit')">
              提交审核
            </el-button>
            <el-button
              v-if="selectedVersion?.status === 'reviewing' && !selectedVersion.reviewedAt"
              type="success"
              @click="runAction('approve')"
            >
              审核通过
            </el-button>
            <el-button
              v-if="selectedVersion?.status === 'reviewing'"
              type="danger"
              plain
              @click="runAction('reject')"
            >
              驳回
            </el-button>
            <el-button
              v-if="selectedVersion?.status === 'reviewing' && selectedVersion.reviewedAt"
              type="success"
              @click="runAction('publish')"
            >
              发布
            </el-button>
          </div>
        </div>

        <div v-if="recoverySnapshot" class="designer-recovery" role="status">
          <span>发现 {{ recoverySnapshot.savedAt }} 保存的本机编辑副本；尚未写入服务端。</span>
          <div>
            <el-button link type="primary" @click="restoreRecoverySnapshot">恢复编辑</el-button>
            <el-button link @click="discardRecoverySnapshot">丢弃副本</el-button>
          </div>
        </div>

        <div class="designer-workspace">
          <section class="designer-controls">
            <el-tabs v-model="activeSection" tab-position="left" class="designer-editor-tabs">
              <el-tab-pane label="版本" name="versions">
                <div class="designer-content-heading">
                  <div>
                    <span class="eyebrow">版本管理</span>
                    <h3>站点版本</h3>
                    <p>管理站点版本，切换版本以查看或编辑不同内容。</p>
                  </div>
                  <el-button :disabled="!editable" @click="createDraft">新建版本</el-button>
                </div>
                <button
                  v-for="version in versions"
                  :key="version.versionId"
                  class="designer-version"
                  :class="{ active: selectedVersion?.versionId === version.versionId }"
                  @click="selectVersion(version)"
                >
                  <span>V{{ version.versionNo }}</span>
                  <StatusTag :status="version.status" />
                  <el-button
                    v-if="version.status === 'archived'"
                    link
                    type="primary"
                    @click.stop="rollback(version)"
                  >
                    回滚
                  </el-button>
                </button>
              </el-tab-pane>
              <el-tab-pane label="站点" name="site">
                <template v-if="draft">
                  <el-form label-position="top">
                    <el-form-item label="站点名称">
                      <el-input v-model="draft.site.name" :disabled="!editable" />
                    </el-form-item>
                    <el-form-item label="场景">
                      <el-select v-model="draft.site.scenario" :disabled="!editable">
                        <el-option label="党建知识门户" value="party" />
                        <el-option label="企业制度 / SOP" value="internal-policy" />
                        <el-option label="产品帮助中心" value="product-help" />
                      </el-select>
                    </el-form-item>
                    <el-form-item label="页脚文案">
                      <el-input v-model="draft.shell.footer.text" :disabled="!editable" />
                    </el-form-item>
                  </el-form>
                </template>
              </el-tab-pane>
              <el-tab-pane label="导航" name="navigation">
                <template v-if="draft">
                  <article
                    v-for="(item, index) in draft.shell.navigation"
                    :key="item.id"
                    class="designer-config-row"
                  >
                    <el-input v-model="item.label" :disabled="!editable" placeholder="导航名称" />
                    <el-input
                      v-model="item.target"
                      :disabled="!editable"
                      placeholder="home、library 或 page/about"
                    />
                    <el-button
                      :disabled="!editable"
                      type="danger"
                      plain
                      @click="draft.shell.navigation.splice(index, 1)"
                    >
                      删除
                    </el-button>
                  </article>
                  <el-button :disabled="!editable" @click="addNavigation">添加导航</el-button>
                </template>
              </el-tab-pane>
              <el-tab-pane label="功能模块" name="modules">
                <template v-if="draft">
                  <label v-for="module in configurableModules" :key="module.key" class="designer-module-row">
                    <span>
                      <strong>{{ module.label }}</strong>
                      <small>{{ module.key }}</small>
                    </span>
                    <el-switch
                      :disabled="!editable"
                      :model-value="moduleEnabled(module.key)"
                      @update:model-value="setModule(module.key, Boolean($event))"
                    />
                  </label>
                  <p class="designer-hint">首页与个人中心属于核心模块，不能关闭。</p>
                </template>
              </el-tab-pane>
              <el-tab-pane label="页面" name="pages">
                <template v-if="draft">
                  <button
                    v-for="page in pages"
                    :key="page.slug"
                    class="designer-page-row"
                    :class="{ active: activePageSlug === page.slug }"
                    @click="activePageSlug = page.slug"
                  >
                    <span>
                      <strong>{{ page.slug === 'home' ? '首页' : page.slug }}</strong>
                      <small>{{ page.layout }}</small>
                    </span>
                    <el-button
                      v-if="page.slug !== 'home'"
                      :disabled="!editable"
                      type="danger"
                      link
                      @click.stop="removePage(page)"
                    >
                      删除
                    </el-button>
                  </button>
                  <el-form v-if="activePage" label-position="top" class="designer-page-form">
                    <el-form-item label="布局">
                      <el-select v-model="activePage.layout" :disabled="!editable">
                        <el-option label="单列" value="single" />
                        <el-option label="左侧栏" value="sidebar-left" />
                        <el-option label="右侧栏" value="sidebar-right" />
                        <el-option label="十二列网格" value="twelve-grid" />
                      </el-select>
                    </el-form-item>
                  </el-form>
                  <div class="designer-page-create">
                    <el-input
                      v-model="newPageSlug"
                      :disabled="!editable"
                      placeholder="自定义页面 Slug，如 service-guide"
                    />
                    <el-button :disabled="!editable" @click="addPage">创建页面</el-button>
                  </div>
                  <p class="designer-hint">自定义页面导航目标使用 <code>page/{slug}</code>。</p>
                </template>
              </el-tab-pane>
              <el-tab-pane label="外观" name="appearance">
                <template v-if="draft">
                  <el-form label-position="top">
                    <el-form-item label="视觉包">
                      <el-select v-model="draft.theme.pack" :disabled="!editable">
                        <el-option
                          v-for="pack in visualPacks"
                          :key="pack.id"
                          :label="`${pack.label} · ${pack.description}`"
                          :value="pack.id"
                        />
                      </el-select>
                    </el-form-item>
                    <el-form-item label="主题预设">
                      <el-select v-model="draft.theme.preset" :disabled="!editable">
                        <el-option label="深青绿" value="emerald" />
                        <el-option label="政务蓝" value="government-blue" />
                        <el-option label="暖灰阅读" value="reading-warm" />
                      </el-select>
                    </el-form-item>
                    <el-form-item label="模式">
                      <el-segmented
                        v-model="draft.theme.mode"
                        :disabled="!editable"
                        :options="['light', 'dark', 'system']"
                      />
                    </el-form-item>
                    <el-form-item label="密度">
                      <el-radio-group v-model="draft.theme.density" :disabled="!editable">
                        <el-radio-button value="compact">紧凑</el-radio-button>
                        <el-radio-button value="comfortable">舒适</el-radio-button>
                      </el-radio-group>
                    </el-form-item>
                    <el-form-item label="品牌色">
                      <el-color-picker v-model="draft.theme.tokens.colorPrimary" :disabled="!editable" />
                    </el-form-item>
                    <el-form-item label="受控 CSS（最大 30 KiB）">
                      <el-input
                        v-model="draft.theme.customCss"
                        :disabled="!editable"
                        type="textarea"
                        :rows="8"
                        placeholder=".cms-block--announcement { ... }"
                      />
                    </el-form-item>
                  </el-form>
                </template>
              </el-tab-pane>
              <el-tab-pane label="内容范围" name="scope">
                <template v-if="draft">
                  <el-form label-position="top">
                    <el-form-item label="空间范围">
                      <el-switch
                        v-model="draft.contentScope.allSpaces"
                        :disabled="!editable"
                        active-text="全部有权空间"
                      />
                    </el-form-item>
                    <el-form-item label="指定空间编码">
                      <el-select
                        v-model="draft.contentScope.spaceCodes"
                        :disabled="!editable || draft.contentScope.allSpaces"
                        multiple
                        allow-create
                        filterable
                      />
                    </el-form-item>
                    <el-form-item label="效力状态">
                      <el-checkbox-group v-model="draft.contentScope.validityStatuses" :disabled="!editable">
                        <el-checkbox value="effective">现行有效</el-checkbox>
                        <el-checkbox value="pending">即将生效</el-checkbox>
                        <el-checkbox value="expired">已失效</el-checkbox>
                        <el-checkbox value="repealed">已废止</el-checkbox>
                      </el-checkbox-group>
                    </el-form-item>
                  </el-form>
                </template>
              </el-tab-pane>
              <el-tab-pane label="品牌资产" name="assets">
                <div class="designer-asset-upload">
                  <el-select v-model="assetType">
                    <el-option label="Logo" value="logo" />
                    <el-option label="Favicon" value="favicon" />
                    <el-option label="背景图" value="background" />
                    <el-option label="图标" value="icon" />
                    <el-option label="插图" value="illustration" />
                  </el-select>
                  <input
                    type="file"
                    accept="image/png,image/jpeg,image/gif,image/webp"
                    @change="selectAssetFile"
                  />
                  <el-button
                    type="primary"
                    :disabled="!assetFile"
                    :loading="actionLoading === 'asset'"
                    @click="uploadAsset"
                  >
                    上传资源
                  </el-button>
                </div>
                <article v-for="asset in assets" :key="asset.assetId" class="designer-asset">
                  <div>
                    <strong>{{ asset.originalName }}</strong>
                    <small>{{ asset.assetType }} · {{ asset.assetKey }}</small>
                  </div>
                  <el-button type="danger" link @click="removeAsset(asset)">删除</el-button>
                </article>
              </el-tab-pane>
              <el-tab-pane label="区块" name="blocks">
                <template v-if="activePage">
                  <div class="designer-editor-heading">
                    <div>
                      <span class="eyebrow">{{ activePageSlug === 'home' ? '首页' : activePageSlug }}</span>
                      <h3>区块编排</h3>
                    </div>
                    <span>{{ mainBlocks.length }} 个区块</span>
                  </div>
                  <button
                    v-for="(block, index) in mainBlocks"
                    :key="block.id"
                    class="designer-block-outline"
                    :class="{ active: selectedBlockId === block.id }"
                    @click="selectBlock(block)"
                  >
                    <span>
                      <strong>{{
                        cmsBlockDefinitions.find((item) => item.type === block.type)?.title
                      }}</strong>
                      <small>{{ block.variant || 'default' }} · {{ block.span || 12 }}/12</small>
                    </span>
                    <span class="designer-block-outline-meta">{{ index + 1 }}</span>
                  </button>
                </template>
                <p class="designer-hint">
                  选择区块后，可在“区块属性”面板调整宽度、变体、排序和安全 Markdown 文案。
                </p>
                <div class="designer-block-catalog">
                  <el-button
                    v-for="definition in cmsBlockDefinitions"
                    :key="definition.type"
                    :disabled="!editable"
                    @click="addBlock(definition.type)"
                  >
                    {{ definition.title }}
                  </el-button>
                </div>
              </el-tab-pane>
              <el-tab-pane label="扩展区块" name="extensions">
                <template v-if="activePage">
                  <p class="designer-hint">
                    扩展由平台 CI 签名发布，在沙箱中运行；站点管理员只能启用和填写已声明的配置。
                  </p>
                  <div class="designer-block-catalog">
                    <el-button
                      v-for="extension in extensionCatalog"
                      :key="`${extension.extensionId}@${extension.version}`"
                      :disabled="!editable"
                      @click="addExtension(extension)"
                    >
                      {{ extension.displayName }} · {{ extension.version }}
                    </el-button>
                  </div>
                  <article
                    v-for="(extension, index) in activePage.extensions || []"
                    :key="`${extension.extensionId}@${extension.version}:${extension.slotKey}`"
                    class="designer-asset"
                  >
                    <div>
                      <strong>{{ extension.extensionId }} · {{ extension.version }}</strong>
                      <small>{{ extension.region || 'main' }} · {{ extension.slotKey }}</small>
                    </div>
                    <el-form label-position="top" class="designer-extension-settings">
                      <el-form-item
                        v-for="setting in extensionSettings(extension.extensionId, extension.version)"
                        :key="setting.key"
                        :label="setting.label"
                      >
                        <el-switch
                          v-if="setting.type === 'boolean'"
                          :model-value="Boolean(extension.config?.[setting.key])"
                          :disabled="!editable"
                          @update:model-value="setExtensionSetting(extension, setting.key, Boolean($event))"
                        />
                        <el-input-number
                          v-else-if="setting.type === 'number'"
                          :model-value="Number(extension.config?.[setting.key] || 0)"
                          :disabled="!editable"
                          :min="0"
                          @update:model-value="
                            setExtensionSetting(extension, setting.key, Number($event || 0))
                          "
                        />
                        <el-input
                          v-else
                          :model-value="String(extension.config?.[setting.key] || '')"
                          :disabled="!editable"
                          @update:model-value="
                            setExtensionSetting(extension, setting.key, String($event || ''))
                          "
                        />
                      </el-form-item>
                    </el-form>
                    <el-switch v-model="extension.enabled" :disabled="!editable" active-text="启用" />
                    <el-button type="danger" link :disabled="!editable" @click="removeExtension(index)"
                      >移除</el-button
                    >
                  </article>
                </template>
              </el-tab-pane>
              <el-tab-pane label="访问分析" name="analytics">
                <template v-if="analytics">
                  <div class="designer-metrics">
                    <article v-for="item in analytics.totals" :key="item.eventType">
                      <strong>{{ item.total }}</strong>
                      <span>{{ item.eventType }}</span>
                    </article>
                  </div>
                  <div class="designer-section-title designer-search-title">
                    <strong>热门搜索</strong>
                    <span>近 {{ analytics.days }} 天</span>
                  </div>
                  <ol class="designer-searches">
                    <li v-for="item in analytics.topSearches" :key="item.keyword">
                      <span>{{ item.keyword }}</span>
                      <strong>{{ item.total }}</strong>
                    </li>
                  </ol>
                  <el-empty
                    v-if="!analytics.totals.length && !analytics.topSearches.length"
                    description="暂无访问数据"
                    :image-size="64"
                  />
                </template>
                <el-empty v-else description="暂无访问分析权限或数据" :image-size="64" />
              </el-tab-pane>
            </el-tabs>
          </section>
        </div>

        <el-drawer
          v-model="previewOpen"
          class="designer-preview-drawer"
          :size="previewDrawerSize"
          direction="rtl"
          title="实时预览"
        >
          <template #header>
            <div class="designer-viewport">
              <strong>实时预览</strong>
              <div class="designer-preview-controls">
                <el-segmented v-model="viewport" :options="[1440, 1024, 390]" aria-label="预览视口" />
                <el-button link type="primary" @click="previewFullscreen = !previewFullscreen">
                  {{ previewFullscreen ? '退出全屏' : '全屏' }}
                </el-button>
              </div>
            </div>
          </template>
          <div class="designer-frame-wrap">
            <iframe
              title="门户隔离预览"
              sandbox=""
              :srcdoc="previewDocument"
              :style="{ width: previewWidth }"
            />
          </div>
        </el-drawer>

        <el-drawer
          v-model="inspectorOpen"
          class="designer-inspector-drawer"
          :size="compactViewport ? '86%' : '360px'"
          direction="rtl"
          title="区块属性"
        >
          <template v-if="selectedBlock">
            <div class="designer-section-title">
              <div>
                <strong>{{
                  cmsBlockDefinitions.find((item) => item.type === selectedBlock?.type)?.title
                }}</strong>
                <small>{{ selectedBlock.type }} · {{ activePageSlug }}</small>
              </div>
              <el-switch v-model="selectedBlock.enabled" :disabled="!editable" active-text="启用" />
            </div>
            <el-form label-position="top" class="designer-inspector-form">
              <el-form-item label="展示宽度">
                <el-select v-model="selectedBlock.span" :disabled="!editable">
                  <el-option label="整行" :value="12" />
                  <el-option label="2/3" :value="8" />
                  <el-option label="1/2" :value="6" />
                  <el-option label="1/3" :value="4" />
                </el-select>
              </el-form-item>
              <el-form-item label="标题">
                <el-input
                  :model-value="blockText(selectedBlock, 'title')"
                  :disabled="!editable"
                  placeholder="留空时使用区块默认标题"
                  @update:model-value="setBlockText(selectedBlock, 'title', String($event || ''))"
                />
              </el-form-item>
              <el-form-item label="展示变体">
                <el-select v-model="selectedBlock.variant" :disabled="!editable">
                  <el-option label="默认" value="default" />
                  <el-option label="紧凑" value="compact" />
                  <el-option label="卡片" value="cards" />
                  <el-option label="列表" value="list" />
                  <el-option label="重点" value="featured" />
                </el-select>
              </el-form-item>
              <el-form-item label="安全 Markdown 文案">
                <el-input
                  :model-value="blockText(selectedBlock, 'markdown')"
                  :disabled="!editable"
                  type="textarea"
                  :rows="8"
                  placeholder="支持提示、步骤、FAQ、徽章等受控标签"
                  @update:model-value="setBlockText(selectedBlock, 'markdown', String($event || ''))"
                />
              </el-form-item>
            </el-form>
            <div class="designer-inspector-actions">
              <el-button
                :disabled="!editable || mainBlocks.indexOf(selectedBlock) === 0"
                @click="moveBlock(mainBlocks.indexOf(selectedBlock), -1)"
                >上移</el-button
              >
              <el-button
                :disabled="!editable || mainBlocks.indexOf(selectedBlock) === mainBlocks.length - 1"
                @click="moveBlock(mainBlocks.indexOf(selectedBlock), 1)"
                >下移</el-button
              >
              <el-button :disabled="!editable" type="danger" plain @click="removeSelectedBlock"
                >删除区块</el-button
              >
            </div>
          </template>
          <el-empty v-else description="先在“页面”中选择一个区块" :image-size="72" />
        </el-drawer>
      </main>

      <el-empty v-else description="尚未创建门户站点" />
    </div>

    <el-dialog v-model="createOpen" title="创建场景化门户" width="520px">
      <el-form label-position="top">
        <el-form-item label="站点编码">
          <el-input v-model="createForm.siteKey" placeholder="例如 policy-center" />
        </el-form-item>
        <el-form-item label="站点名称">
          <el-input v-model="createForm.name" placeholder="例如 企业制度与 SOP 中心" />
        </el-form-item>
        <el-form-item label="场景包">
          <el-select v-model="createForm.scenario">
            <el-option label="党建知识门户" value="party" />
            <el-option label="企业制度 / SOP" value="internal-policy" />
            <el-option label="产品帮助中心" value="product-help" />
          </el-select>
        </el-form-item>
        <el-checkbox v-model="createForm.defaultSite">设为默认站点</el-checkbox>
      </el-form>
      <template #footer>
        <el-button @click="createOpen = false">取消</el-button>
        <el-button type="primary" :loading="actionLoading === 'create'" @click="createSite">
          创建站点与草稿
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.designer-shell {
  display: grid;
  grid-template-columns: 304px minmax(0, 1fr);
  gap: 24px;
  align-items: start;
  min-height: 660px;
}

.designer-sites {
  align-self: start;
  padding: 16px;
}

.designer-section-title,
.designer-toolbar,
.designer-viewport {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.designer-preview-controls {
  display: flex;
  gap: 8px;
  align-items: center;
}

.designer-section-title {
  margin-bottom: 12px;
}

.designer-section-title span {
  display: inline-grid;
  width: 24px;
  height: 24px;
  place-items: center;
  border-radius: 999px;
  background: color-mix(in oklch, var(--primary-soft) 68%, var(--panel));
  color: var(--muted);
  font-variant-numeric: tabular-nums;
}

.designer-site,
.designer-version {
  display: flex;
  width: 100%;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
  padding: 14px;
  border: 1px solid color-mix(in oklch, var(--line) 82%, transparent);
  border-radius: 10px;
  background: var(--panel);
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.designer-version {
  margin-bottom: 10px;
}

.designer-site:hover,
.designer-site.active,
.designer-version:hover,
.designer-version.active {
  border-color: color-mix(in oklch, var(--primary) 30%, var(--line));
  background: var(--primary-soft);
}

.designer-site-copy,
.designer-site-name {
  display: grid;
  min-width: 0;
  gap: 4px;
}

.designer-site-name {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 8px;
}

.designer-site-name em {
  padding: 2px 6px;
  border: 1px solid color-mix(in oklch, var(--primary) 30%, var(--line));
  border-radius: 999px;
  background: color-mix(in oklch, var(--primary-soft) 72%, var(--panel));
  color: var(--primary);
  font-size: 11px;
  font-style: normal;
  font-weight: 700;
}

.designer-site span,
.designer-site small {
  display: block;
}

.designer-site .designer-site-copy {
  display: grid;
}

.designer-site .designer-site-name {
  display: flex;
}

.designer-asset div,
.designer-asset small {
  display: block;
}

.designer-site small,
.designer-block-row small,
.designer-toolbar span {
  color: var(--muted);
}

.designer-main {
  min-width: 0;
  min-height: 660px;
  padding: 0;
  overflow: hidden;
}

.designer-toolbar {
  padding: 24px;
  border-bottom: 1px solid var(--line);
}

.designer-site-summary {
  display: grid;
  gap: 2px;
}

.designer-site-summary strong {
  font-size: 20px;
}

.designer-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.designer-site-switcher {
  display: none;
  width: 180px;
}

.designer-unsaved {
  padding: 6px 8px;
  border-radius: 999px;
  background: color-mix(in oklch, #d97706 12%, var(--panel));
  color: #925b07;
  font-size: 12px;
  font-weight: 700;
}

.designer-recovery {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  margin: 16px 24px 0;
  padding: 10px 14px;
  border: 1px solid color-mix(in oklch, var(--primary) 32%, var(--line));
  border-radius: 10px;
  background: color-mix(in oklch, var(--primary-soft) 56%, var(--panel));
  color: var(--accent-dark);
  font-size: 13px;
}

.designer-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  align-items: start;
}

.designer-controls {
  min-width: 0;
  min-height: 680px;
  padding: 0;
}

.designer-editor-tabs :deep(.el-tabs__header) {
  width: 184px;
  margin: 0;
  padding: 20px 12px;
  border-right: 1px solid var(--line);
}

.designer-editor-tabs :deep(.el-tabs__nav-wrap) {
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
}

.designer-editor-tabs :deep(.el-tabs__item) {
  height: auto;
  min-height: 42px;
  margin: 2px 0;
  padding: 11px 12px;
  border-radius: 8px;
  color: var(--muted);
  font-weight: 650;
  line-height: 20px;
  text-align: left;
  white-space: normal;
}

.designer-editor-tabs :deep(.el-tabs__item.is-active) {
  background: var(--primary);
  color: white;
}

.designer-editor-tabs :deep(.el-tabs__active-bar) {
  display: none;
}

.designer-editor-tabs :deep(.el-tabs__content) {
  min-width: 0;
  max-width: none;
  padding: 28px 32px 48px;
}

.designer-editor-tabs :deep(.el-form) {
  max-width: 840px;
}

.designer-editor-tabs :deep(.el-form-item) {
  margin-bottom: 20px;
}

.designer-editor-tabs :deep(.el-input),
.designer-editor-tabs :deep(.el-select) {
  width: 100%;
}

.designer-content-heading {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 20px;
}

.designer-content-heading h3 {
  margin: 2px 0 4px;
  font-size: 22px;
  line-height: 1.3;
}

.designer-content-heading p {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
}

.designer-frame-wrap {
  min-height: calc(100vh - 152px);
  padding: 16px;
  overflow: auto;
  border-radius: 12px;
  background: #e9eeec;
}

.designer-frame-wrap iframe {
  display: block;
  height: calc(100vh - 188px);
  min-height: 620px;
  margin: 0 auto;
  border: 0;
  background: white;
  box-shadow: 0 12px 32px rgb(26 54 54 / 0.12);
}

.designer-block-catalog {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.designer-asset-upload {
  display: grid;
  gap: 10px;
}

.designer-asset {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid var(--line);
}

.designer-asset small {
  color: var(--muted);
  overflow-wrap: anywhere;
}

.designer-config-row {
  display: grid;
  grid-template-columns: 1fr;
  gap: 8px;
  padding: 10px 0;
  border-bottom: 1px solid var(--line);
}

.designer-module-row,
.designer-page-row {
  display: flex;
  width: 100%;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  padding: 10px 8px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: inherit;
  text-align: left;
}

.designer-page-row {
  cursor: pointer;
}

.designer-page-row.active {
  border-color: color-mix(in oklch, var(--primary) 30%, var(--line));
  background: var(--primary-soft);
}

.designer-module-row span,
.designer-module-row small,
.designer-page-row span,
.designer-page-row small {
  display: block;
}

.designer-module-row small,
.designer-page-row small,
.designer-hint {
  color: var(--muted);
}

.designer-page-form,
.designer-page-create,
.designer-search-title {
  margin-top: 12px;
}

.designer-page-create {
  display: grid;
  gap: 8px;
}

.designer-hint {
  margin: 10px 0 0;
  font-size: 12px;
}

.designer-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.designer-metrics article {
  display: grid;
  gap: 2px;
  padding: 10px;
  border: 1px solid var(--line);
  border-radius: 8px;
}

.designer-metrics strong {
  font-size: 20px;
}

.designer-metrics span {
  color: var(--muted);
  font-size: 12px;
}

.designer-searches {
  display: grid;
  gap: 6px;
  margin: 0;
  padding-left: 20px;
}

.designer-searches li {
  display: flex;
  gap: 8px;
  justify-content: space-between;
}

.designer-block-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  padding: 10px 0;
  border-bottom: 1px solid var(--line);
}

.designer-editor-heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  margin-bottom: 16px;
}

.designer-editor-heading h3 {
  margin: 2px 0 0;
  font-size: 22px;
}

.designer-block-outline {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--panel);
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.designer-block-outline:hover,
.designer-block-outline.active {
  border-color: color-mix(in oklch, var(--primary) 48%, var(--line));
  background: var(--primary-soft);
}

.designer-block-outline span,
.designer-block-outline small {
  display: block;
}

.designer-block-outline small,
.designer-inspector-drawer small {
  color: var(--muted);
}

.designer-block-outline-meta {
  color: var(--muted);
  font-variant-numeric: tabular-nums;
}

.designer-inspector-form {
  margin-top: 20px;
}

.designer-inspector-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

:global(.designer-preview-drawer .el-drawer__body),
:global(.designer-inspector-drawer .el-drawer__body) {
  padding-top: 8px;
}

.designer-block-row > div:first-child {
  display: grid;
}

.designer-block-row :deep(.el-select),
.designer-order-actions {
  grid-column: 1 / -1;
}

.designer-order-actions {
  display: flex;
  gap: 6px;
}

@media (width <= 1280px) {
  .designer-shell {
    grid-template-columns: minmax(0, 1fr);
  }

  .designer-sites {
    display: none;
  }

  .designer-site-switcher {
    display: block;
  }
}

@media (width <= 860px) {
  .designer-shell {
    grid-template-columns: 1fr;
  }

  .designer-sites {
    display: flex;
    gap: 8px;
    overflow-x: auto;
  }

  .designer-sites .designer-section-title {
    display: none;
  }

  .designer-site {
    min-width: 200px;
  }

  .designer-toolbar,
  .designer-recovery {
    align-items: flex-start;
    flex-direction: column;
  }

  .designer-actions {
    width: 100%;
    justify-content: flex-start;
  }

  .designer-editor-tabs :deep(.el-tabs) {
    display: flex;
    flex-direction: column;
  }

  .designer-editor-tabs :deep(.el-tabs__header) {
    position: static;
    width: 100%;
    margin: 0 0 16px;
    overflow-x: auto;
  }

  .designer-editor-tabs :deep(.el-tabs__nav) {
    display: flex;
  }

  .designer-editor-tabs :deep(.el-tabs__item) {
    min-width: max-content;
    padding: 8px 12px;
  }

  .designer-editor-tabs :deep(.el-tabs__content) {
    padding-right: 0;
  }
}
</style>
