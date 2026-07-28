<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { FullScreen, MagicStick, MoreFilled, Operation, Setting } from '@element-plus/icons-vue'
import {
  createPortalDraft,
  getPortalVersion,
  listPortalCodePackages,
  listPortalSites,
  listPortalVersions,
  portalVersionAction,
  updatePortalDraft,
} from '../../api/portalSites'
import type { PortalCodePackage, PortalDesignProposal } from '../../api/portalSites'
import type {
  LayoutNode,
  LowCodePage,
  PortalBreakpoint,
  PortalConfigVersion,
  PortalSiteConfigV3,
  PortalSiteSummary,
} from '../../cms/siteConfig'
import { isPortalSiteConfigV2, isPortalSiteConfigV3 } from '../../cms/siteConfig'
import { cmsBlockDefinitions } from '../../cms/blockDefinitions'
import { findNode, findParent, walkNodes } from '../../cms/v3/contract'
import {
  cloneNode,
  duplicateNode,
  insertNodeAt,
  insertNode,
  moveNode,
  moveNodeAt,
  removeNode,
  replaceNode,
  uniqueNodeId,
  type DesignerDropPosition,
} from '../../cms/v3/designerTree'
import { componentPropertyFields } from '../../cms/v3/componentPropertySchema'
import { migratePortalConfigV2ToV3 } from '../../cms/v3/migrateV2'
import {
  PREVIEW_WIDTH_MAX,
  PREVIEW_WIDTH_MIN,
  PREVIEW_WIDTH_PRESETS,
  buildDesignerStructure,
  fitCanvasZoom,
  normalizePreviewWidth,
  previewBreakpoint,
  structureSelectionKey,
  type DesignerStructureNode,
} from '../../cms/v3/designerWorkspace'
import { useAuthStore } from '../../stores/auth'
import DesignerCanvasNode from './DesignerCanvasNode.vue'
import PortalAiDesignDrawer from './PortalAiDesignDrawer.vue'
import PortalCodeEditorDrawer from './PortalCodeEditorDrawer.vue'

type LeftMode = 'structure' | 'components' | 'symbols'
type InspectorMode = 'layout' | 'content' | 'style' | 'data' | 'interaction' | 'advanced'
type ZoomMode = 'fit' | 'manual'
type MoreCommand = 'undo' | 'redo' | 'code' | 'validate' | 'reject'

interface WorkspacePreferences {
  version: 2
  structureOpen: boolean
  inspectorOpen: boolean
  expandedKeys: string[]
  zoomMode: ZoomMode
  zoom: number
  previewWidth: number
}

const auth = useAuthStore()
const sites = ref<PortalSiteSummary[]>([])
const versions = ref<PortalConfigVersion[]>([])
const selectedSiteKey = ref('')
const activeVersion = ref<PortalConfigVersion>()
const config = ref<PortalSiteConfigV3>()
const activePageSlug = ref('home')
const selectedNodeId = ref('')
const leftMode = ref<LeftMode>('structure')
const inspectorMode = ref<InspectorMode>('layout')
const previewWidth = ref(PREVIEW_WIDTH_PRESETS.desktop)
const breakpoint = computed<PortalBreakpoint>({
  get: () => previewBreakpoint(previewWidth.value),
  set: (value) => setPreviewWidth(PREVIEW_WIDTH_PRESETS[value]),
})
const loading = ref(false)
const saving = ref(false)
const dirty = ref(false)
const zoom = ref(90)
const issues = ref<string[]>([])
const history = ref<PortalSiteConfigV3[]>([])
const future = ref<PortalSiteConfigV3[]>([])
const restoring = ref(false)
const codeDrawerOpen = ref(false)
const aiDrawerOpen = ref(false)
const codePackages = ref<PortalCodePackage[]>([])
const advancedJson = ref('')
let propertyBaseline: PortalSiteConfigV3 | undefined
const designerRoot = ref<HTMLElement>()
const stageViewport = ref<HTMLElement>()
const canvasElement = ref<HTMLElement>()
const workspaceWidth = ref(1280)
const canvasContentHeight = ref(720)
const structureOpen = ref(true)
const inspectorOpen = ref(true)
const immersive = ref(false)
const expandedKeys = ref<string[]>(['group:shell', 'group:pages', 'page:home'])
const zoomMode = ref<ZoomMode>('fit')
let designerResizeObserver: ResizeObserver | undefined
let stageResizeObserver: ResizeObserver | undefined
let canvasResizeObserver: ResizeObserver | undefined
let preferencesReady = false

const canEdit = computed(
  () =>
    auth.permissions.has('kma:admin') ||
    (auth.permissions.has('portal-page:edit') && auth.permissions.has('portal-site:update')),
)
const canReview = computed(() => auth.hasAnyPermission(['portal-site:review']))
const canPublish = computed(() => auth.hasAnyPermission(['portal-site:publish']))
const activeSite = computed(() => sites.value.find((site) => site.siteKey === selectedSiteKey.value))
const activePage = computed<LowCodePage | undefined>(() => {
  if (!config.value) return undefined
  if (activePageSlug.value === '$header')
    return {
      slug: '$header',
      title: '全局页头',
      kind: 'custom',
      root: config.value.shell.header,
    }
  if (activePageSlug.value === '$footer')
    return {
      slug: '$footer',
      title: '全局页脚',
      kind: 'custom',
      root: config.value.shell.footer,
    }
  return config.value.pages[activePageSlug.value]
})
const selectedNode = computed(() =>
  activePage.value && selectedNodeId.value
    ? findNode(activePage.value.root, selectedNodeId.value)
    : undefined,
)
const selectedComponentFields = computed(() =>
  selectedNode.value?.type === 'component' ? componentPropertyFields(selectedNode.value.component) : [],
)
const nodeCount = computed(() => {
  let count = 0
  if (activePage.value) walkNodes(activePage.value.root, () => (count += 1))
  return count
})
const canvasWidth = computed(() => previewWidth.value)
const minimumCanvasHeight = computed(() =>
  breakpoint.value === 'desktop' ? 720 : breakpoint.value === 'tablet' ? 768 : 844,
)
const snapshotKey = computed(() => `kma-low-code-v3:${selectedSiteKey.value}`)
const workspacePreferencesKey = computed(() => `kma-low-code-workspace:v2:${selectedSiteKey.value}`)
const legacyWorkspacePreferencesKey = computed(() => `kma-low-code-workspace:v1:${selectedSiteKey.value}`)
const breadcrumbs = computed(() => {
  const result: string[] = []
  let current = selectedNode.value
  while (current && activePage.value) {
    result.unshift(current.name || current.id)
    current = findParent(activePage.value.root, current.id)
  }
  return result
})
const structureData = computed(() => (config.value ? buildDesignerStructure(config.value) : []))
const selectedStructureKey = computed(() =>
  selectedNodeId.value === activePage.value?.root.id
    ? `page:${activePageSlug.value}`
    : structureSelectionKey(activePageSlug.value, selectedNodeId.value),
)
const compactInspector = computed(() => workspaceWidth.value < 1200)
const compactStructure = computed(() => workspaceWidth.value < 900)
const canvasScale = computed(() => zoom.value / 100)
const canvasFrameStyle = computed(() => ({
  width: `${Math.round(canvasWidth.value * canvasScale.value)}px`,
  height: `${Math.round(
    Math.max(canvasContentHeight.value, minimumCanvasHeight.value) * canvasScale.value,
  )}px`,
}))
const canvasStyle = computed(() => ({
  width: `${canvasWidth.value}px`,
  minHeight: `${minimumCanvasHeight.value}px`,
  transform: `scale(${canvasScale.value})`,
}))
const livePagePath = computed(() => {
  const siteKey = encodeURIComponent(selectedSiteKey.value)
  const page = activePage.value
  if (!siteKey || !page || page.slug.startsWith('$') || page.kind === 'content') return ''
  if (['home', 'library', 'ask', 'topics', 'favorites', 'profile'].includes(page.kind))
    return `/p/${siteKey}/${page.kind}`
  return `/p/${siteKey}/page/${encodeURIComponent(page.slug)}`
})

function checkpoint() {
  if (!config.value || restoring.value) return
  history.value.push(cloneNode(config.value))
  if (history.value.length > 100) history.value.shift()
  future.value = []
}

function openLivePage() {
  if (!livePagePath.value || !activeVersion.value) return
  const previewWindow = window.open('about:blank', '_blank')
  if (!previewWindow) {
    ElMessage.warning('浏览器阻止了预览窗口，请允许本站打开新窗口后重试')
    return
  }
  previewWindow.opener = null
  previewWindow.location.replace(`${livePagePath.value}?previewVersion=${activeVersion.value.versionId}`)
}

function defaultPreferences(): WorkspacePreferences {
  return {
    version: 2,
    structureOpen: workspaceWidth.value >= 900,
    inspectorOpen: workspaceWidth.value >= 1200,
    expandedKeys: ['group:shell', 'group:pages', `page:${activePageSlug.value}`],
    zoomMode: 'fit',
    zoom: 90,
    previewWidth: PREVIEW_WIDTH_PRESETS.desktop,
  }
}

function persistWorkspacePreferences() {
  if (!preferencesReady || !selectedSiteKey.value) return
  const value: WorkspacePreferences = {
    version: 2,
    structureOpen: structureOpen.value,
    inspectorOpen: inspectorOpen.value,
    expandedKeys: expandedKeys.value,
    zoomMode: zoomMode.value,
    zoom: zoom.value,
    previewWidth: previewWidth.value,
  }
  localStorage.setItem(workspacePreferencesKey.value, JSON.stringify(value))
}

function restoreWorkspacePreferences() {
  preferencesReady = false
  const defaults = defaultPreferences()
  try {
    const stored = JSON.parse(
      localStorage.getItem(workspacePreferencesKey.value) ||
        localStorage.getItem(legacyWorkspacePreferencesKey.value) ||
        'null',
    ) as Partial<WorkspacePreferences> | null
    structureOpen.value =
      typeof stored?.structureOpen === 'boolean' ? stored.structureOpen : defaults.structureOpen
    inspectorOpen.value =
      typeof stored?.inspectorOpen === 'boolean' ? stored.inspectorOpen : defaults.inspectorOpen
    expandedKeys.value = Array.isArray(stored?.expandedKeys)
      ? stored.expandedKeys.filter((key): key is string => typeof key === 'string')
      : defaults.expandedKeys
    zoomMode.value = stored?.zoomMode === 'manual' ? 'manual' : 'fit'
    zoom.value =
      typeof stored?.zoom === 'number' ? Math.max(40, Math.min(110, Math.round(stored.zoom))) : defaults.zoom
    previewWidth.value = normalizePreviewWidth(stored?.previewWidth, defaults.previewWidth)
  } catch {
    localStorage.removeItem(workspacePreferencesKey.value)
    structureOpen.value = defaults.structureOpen
    inspectorOpen.value = defaults.inspectorOpen
    expandedKeys.value = defaults.expandedKeys
    zoomMode.value = defaults.zoomMode
    zoom.value = defaults.zoom
    previewWidth.value = defaults.previewWidth
  }
  preferencesReady = true
}

function setPreviewWidth(value: number) {
  previewWidth.value = normalizePreviewWidth(value)
}

async function fitCanvas() {
  zoomMode.value = 'fit'
  await nextTick()
  zoom.value = fitCanvasZoom(stageViewport.value?.clientWidth || 0, canvasWidth.value)
  persistWorkspacePreferences()
}

function setManualZoom() {
  zoomMode.value = 'manual'
  persistWorkspacePreferences()
}

function toggleImmersive() {
  immersive.value = !immersive.value
  nextTick(fitCanvas)
}

function toggleStructurePanel() {
  structureOpen.value = !structureOpen.value
  if (structureOpen.value && compactStructure.value) inspectorOpen.value = false
}

function toggleInspectorPanel() {
  inspectorOpen.value = !inspectorOpen.value
  if (inspectorOpen.value && compactStructure.value) structureOpen.value = false
}

function handleEscape(event: KeyboardEvent) {
  if (event.key !== 'Escape') return
  if (compactInspector.value && inspectorOpen.value) inspectorOpen.value = false
  else if (compactStructure.value && structureOpen.value) structureOpen.value = false
  else if (immersive.value) immersive.value = false
}

function rememberExpanded(node: DesignerStructureNode) {
  if (!expandedKeys.value.includes(node.key)) expandedKeys.value = [...expandedKeys.value, node.key]
}

function rememberCollapsed(node: DesignerStructureNode) {
  expandedKeys.value = expandedKeys.value.filter((key) => key !== node.key)
}

function selectStructure(node: DesignerStructureNode) {
  if (node.kind === 'group' || !node.pageSlug) return
  activePageSlug.value = node.pageSlug
  const page = activePage.value
  selectedNodeId.value = node.nodeId || page?.root.id || ''
  if (!expandedKeys.value.includes(`page:${node.pageSlug}`))
    expandedKeys.value = [...expandedKeys.value, `page:${node.pageSlug}`]
  if (compactStructure.value) structureOpen.value = false
}

function handleMoreCommand(command: MoreCommand) {
  if (command === 'undo') undo()
  else if (command === 'redo') redo()
  else if (command === 'code') codeDrawerOpen.value = true
  else if (command === 'reject') void reject()
  else validateDraft()
}

function normalizeEditableConfig(value: PortalSiteConfigV3) {
  const normalize = (root: LayoutNode) =>
    walkNodes(root, (node) => {
      node.layout ||= {}
      node.layout.span ||= { desktop: 12, tablet: 8, mobile: 4 }
      node.layout.gap ||= { desktop: 16, tablet: 12, mobile: 12 }
      node.layout.hidden ||= { desktop: false, tablet: false, mobile: false }
      node.style ||= {}
      node.style.padding ||= { desktop: 0, tablet: 0, mobile: 0 }
      if (node.type === 'component') {
        node.props ||= {}
        node.dataSource ||= { source: 'static' }
        node.actions ||= [{ event: 'click', type: 'analytics', config: {} }]
      }
    })
  normalize(value.shell.header)
  normalize(value.shell.footer)
  Object.values(value.pages).forEach((page) => normalize(page.root))
  Object.values(value.symbols).forEach((symbol) => normalize(symbol.root))
  return value
}

function changed() {
  dirty.value = true
  issues.value = []
}

function beginPropertyEdit() {
  if (!propertyBaseline && config.value && canEdit.value) propertyBaseline = cloneNode(config.value)
}

function commitPropertyEdit() {
  if (!config.value || !propertyBaseline) {
    changed()
    return
  }
  if (JSON.stringify(propertyBaseline) !== JSON.stringify(config.value)) {
    history.value.push(propertyBaseline)
    if (history.value.length > 100) history.value.shift()
    future.value = []
    changed()
  }
  propertyBaseline = undefined
}

function mutate(operation: () => void) {
  if (!config.value || !canEdit.value) return
  checkpoint()
  operation()
  changed()
}

function undo() {
  const previous = history.value.pop()
  if (!previous || !config.value) return
  future.value.push(cloneNode(config.value))
  restoring.value = true
  config.value = previous
  restoring.value = false
  changed()
}

function redo() {
  const next = future.value.pop()
  if (!next || !config.value) return
  history.value.push(cloneNode(config.value))
  restoring.value = true
  config.value = next
  restoring.value = false
  changed()
}

async function loadSite(siteKey: string) {
  loading.value = true
  try {
    selectedSiteKey.value = siteKey
    versions.value = await listPortalVersions(siteKey)
    // Versions are returned newest first. Keep following the same version through
    // draft -> reviewing -> published instead of silently reopening the old
    // published V2 and generating another V3 draft after every submission.
    const candidate = versions.value[0]
    if (!candidate) throw new Error('站点尚无配置版本')
    let loaded = await getPortalVersion(siteKey, candidate.versionId)
    if (!loaded.config) throw new Error('配置版本缺少内容')
    if (isPortalSiteConfigV2(loaded.config)) {
      const migrated = migratePortalConfigV2ToV3(loaded.config)
      if (canEdit.value) {
        loaded = await createPortalDraft(
          siteKey,
          migrated,
          `从 V2 版本 ${candidate.versionNo} 自动生成 V3 草稿`,
        )
        versions.value = await listPortalVersions(siteKey)
        ElMessage.success('已保留线上 V2，并创建可编辑的 V3 草稿')
      } else {
        loaded = { ...loaded, config: migrated }
      }
    }
    if (!loaded.config || !isPortalSiteConfigV3(loaded.config))
      throw new Error('当前配置不是可编辑的 V3 布局')
    activeVersion.value = loaded
    config.value = normalizeEditableConfig(cloneNode(loaded.config))
    activePageSlug.value = config.value.pages.home ? 'home' : Object.keys(config.value.pages)[0]
    selectedNodeId.value = config.value.pages[activePageSlug.value]?.root.id || ''
    history.value = []
    future.value = []
    dirty.value = false
    restoreLocalSnapshot()
    restoreWorkspacePreferences()
    await fitCanvas()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '站点配置加载失败')
  } finally {
    loading.value = false
  }
}

async function load() {
  loading.value = true
  try {
    ;[sites.value, codePackages.value] = await Promise.all([
      listPortalSites(),
      auth.hasAnyPermission(['portal-code:read']) ? listPortalCodePackages() : Promise.resolve([]),
    ])
    if (!sites.value.length) throw new Error('当前系统没有门户站点')
    await loadSite(selectedSiteKey.value || sites.value[0].siteKey)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '门户设计中心加载失败')
  } finally {
    loading.value = false
  }
}

async function refreshCodePackages() {
  codePackages.value = await listPortalCodePackages()
}

function dragPalette(event: DragEvent, kind: 'layout' | 'component', value: string) {
  event.dataTransfer?.setData('application/x-kma-node', JSON.stringify({ kind, value }))
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'copy'
}

function makeNode(kind: 'layout' | 'component', value: string): LayoutNode | undefined {
  const root = activePage.value?.root
  if (!root) return undefined
  const id = uniqueNodeId(root, value)
  if (kind === 'component') {
    return {
      id,
      type: 'component',
      component: value as LayoutNode & never,
      name: cmsBlockDefinitions.find((item) => item.type === value)?.title || value,
      layout: { span: { desktop: 12, tablet: 8, mobile: 4 } },
      props: {},
    } as LayoutNode
  }
  return {
    id,
    type: value as 'section' | 'container' | 'grid' | 'stack',
    name: value === 'grid' ? '响应式网格' : value === 'stack' ? '堆叠容器' : '内容容器',
    columns: value === 'grid' ? { desktop: 12, tablet: 8, mobile: 4 } : undefined,
    layout: { gap: { desktop: 16, tablet: 12, mobile: 12 } },
    children: [],
  } as LayoutNode
}

function handleDrop(targetId: string, position: DesignerDropPosition, raw: string) {
  if (!activePage.value || !canEdit.value) return
  try {
    const payload = JSON.parse(raw) as {
      kind: 'layout' | 'component' | 'move'
      value?: string
      nodeId?: string
    }
    if (payload.kind === 'move' && payload.nodeId) {
      moveNodeWithHistory(payload.nodeId, targetId, position)
      return
    }
    if (!payload.value || (payload.kind !== 'layout' && payload.kind !== 'component')) return
    const node = makeNode(payload.kind, payload.value)
    if (!node) return
    const nextPage = cloneNode(activePage.value)
    if (!insertNodeAt(nextPage.root, targetId, position, node)) {
      ElMessage.warning('目标位置不接受该节点')
      return
    }
    checkpoint()
    activePage.value.root = nextPage.root
    selectedNodeId.value = node.id
    changed()
  } catch {
    ElMessage.warning('无法识别拖拽内容')
  }
}

function moveNodeWithHistory(nodeId: string, targetId: string, position: DesignerDropPosition) {
  if (!activePage.value || !canEdit.value) return
  const nextPage = cloneNode(activePage.value)
  const result = moveNodeAt(nextPage, nodeId, targetId, position)
  if (!result.moved) {
    ElMessage.warning(result.reason || '当前节点无法移动到目标位置')
    return
  }
  checkpoint()
  activePage.value.root = nextPage.root
  selectedNodeId.value = nodeId
  changed()
}

function structureNodeData(node: unknown) {
  return (node as { data: DesignerStructureNode }).data
}

function allowStructureDrag(node: unknown) {
  const data = structureNodeData(node)
  return data.kind === 'node' && data.pageSlug === activePageSlug.value && !data.locked
}

function allowStructureDrop(dragging: unknown, dropping: unknown, type: 'prev' | 'inner' | 'next') {
  const source = structureNodeData(dragging)
  const target = structureNodeData(dropping)
  if (
    source.kind !== 'node' ||
    target.kind !== 'node' ||
    source.pageSlug !== activePageSlug.value ||
    target.pageSlug !== activePageSlug.value
  )
    return false
  if (type === 'inner') {
    const node = activePage.value ? findNode(activePage.value.root, target.nodeId || '') : undefined
    return Boolean(node && 'children' in node)
  }
  return true
}

function handleStructureDrop(dragging: unknown, dropping: unknown, type: 'before' | 'after' | 'inner') {
  const sourceId = structureNodeData(dragging).nodeId
  const targetId = structureNodeData(dropping).nodeId
  if (!sourceId || !targetId) return
  moveNodeWithHistory(sourceId, targetId, type === 'inner' ? 'inside' : type)
}

function addToSelected(kind: 'layout' | 'component', value: string) {
  const parent =
    selectedNode.value && 'children' in selectedNode.value ? selectedNode.value : activePage.value?.root
  if (!parent) return
  mutate(() => {
    const node = makeNode(kind, value)
    if (node && insertNode(activePage.value!.root, parent.id, node)) selectedNodeId.value = node.id
  })
}

function addSandbox(item: PortalCodePackage) {
  if (!item.currentVersion || !activePage.value || !config.value) {
    ElMessage.warning('代码组件必须先通过扫描并发布版本')
    return
  }
  const parent =
    selectedNode.value && 'children' in selectedNode.value ? selectedNode.value : activePage.value.root
  mutate(() => {
    const node: LayoutNode = {
      id: uniqueNodeId(activePage.value!.root, item.packageKey),
      type: 'sandbox',
      name: item.displayName,
      packageId: item.packageKey,
      version: item.currentVersion!,
      height: 360,
      capabilities: ['page-context'],
      config: {},
      layout: {
        span: { desktop: 12, tablet: 8, mobile: 4 },
        hidden: { desktop: false, tablet: false, mobile: false },
      },
    }
    if (!insertNode(activePage.value!.root, parent.id, node)) return
    selectedNodeId.value = node.id
    if (
      !config.value!.packages.some(
        (reference) => reference.packageId === item.packageKey && reference.version === item.currentVersion,
      )
    )
      config.value!.packages.push({
        packageId: item.packageKey,
        version: item.currentVersion!,
        source: 'site',
      })
  })
}

function removeSelected() {
  if (!activePage.value || !selectedNode.value) return
  mutate(() => {
    const parent = findParent(activePage.value!.root, selectedNode.value!.id)
    if (!removeNode(activePage.value!, selectedNode.value!.id)) {
      ElMessage.warning('根节点、锁定节点和系统必需组件不能删除')
      return
    }
    selectedNodeId.value = parent?.id || activePage.value!.root.id
  })
}

function duplicateSelected() {
  if (!activePage.value || !selectedNode.value) return
  mutate(() => {
    const copy = duplicateNode(activePage.value!.root, selectedNode.value!.id)
    if (copy) selectedNodeId.value = copy.id
    else ElMessage.warning('锁定节点或已满容器不能复制')
  })
}

function moveSelected(direction: -1 | 1) {
  if (!activePage.value || !selectedNode.value) return
  mutate(() => {
    if (!moveNode(activePage.value!.root, selectedNode.value!.id, direction))
      ElMessage.warning('当前节点无法继续移动')
  })
}

async function createSymbol() {
  if (!config.value || !selectedNode.value || !('children' in selectedNode.value)) {
    ElMessage.warning('请选择一个容器节点')
    return
  }
  let name = ''
  try {
    const result = await ElMessageBox.prompt('输入可复用区块名称', '保存可复用区块', {
      inputPattern: /^.{1,80}$/,
      inputErrorMessage: '名称为 1–80 个字符',
    })
    name = result.value
  } catch {
    return
  }
  if (!name.trim()) return
  const symbolId = `symbol-${Date.now().toString(36)}`.slice(0, 64)
  mutate(() => {
    config.value!.symbols[symbolId] = {
      id: symbolId,
      name: name.trim().slice(0, 80),
      revision: 1,
      root: cloneNode(selectedNode.value!),
    }
  })
}

const advancedKeys = ['props', 'layout', 'style', 'dataSource', 'actions', 'visibleWhen'] as const

function refreshAdvancedJson() {
  const node = selectedNode.value
  if (!node) {
    advancedJson.value = ''
    return
  }
  const editable = node as unknown as Record<string, unknown>
  advancedJson.value = JSON.stringify(
    Object.fromEntries(
      advancedKeys
        .filter((key) => key in editable && editable[key] !== undefined)
        .map((key) => [key, editable[key]]),
    ),
    null,
    2,
  )
}

function componentProperty(key: string) {
  return selectedNode.value?.type === 'component' ? selectedNode.value.props?.[key] : undefined
}

function setComponentProperty(key: string, value: unknown) {
  if (selectedNode.value?.type !== 'component' || !['string', 'number', 'boolean'].includes(typeof value))
    return
  selectedNode.value.props ||= {}
  selectedNode.value.props[key] = value as string | number | boolean
}

function applyAdvancedJson() {
  if (!selectedNode.value || !canEdit.value) return
  try {
    const parsed = JSON.parse(advancedJson.value) as Record<string, unknown>
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object')
      throw new Error('高级属性必须是 JSON 对象')
    const unknownKeys = Object.keys(parsed).filter(
      (key) => !advancedKeys.includes(key as (typeof advancedKeys)[number]),
    )
    if (unknownKeys.length) throw new Error(`不允许修改字段：${unknownKeys.join('、')}`)
    if (containsDangerousKey(parsed)) throw new Error('属性中不能包含 API、SQL、脚本或事件处理器字段')
    mutate(() => {
      const node = selectedNode.value as LayoutNode & Record<string, unknown>
      Object.entries(parsed).forEach(([key, value]) => {
        node[key] = cloneNode(value)
      })
    })
    refreshAdvancedJson()
    ElMessage.success('高级属性已应用到画布')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '高级属性 JSON 无效')
  }
}

function containsDangerousKey(value: unknown): boolean {
  if (!value || typeof value !== 'object') return false
  if (Array.isArray(value)) return value.some(containsDangerousKey)
  return Object.entries(value).some(
    ([key, child]) =>
      /^(api|apiUrl|endpoint|sql|script|javascript|eventHandler|componentUrl)$/i.test(key) ||
      containsDangerousKey(child),
  )
}

function applyAiProposal(proposal: PortalDesignProposal) {
  if (!config.value || proposal.pageSlug !== activePageSlug.value) return
  mutate(() => {
    if (proposal.scope === 'page') {
      config.value!.pages[proposal.pageSlug] = cloneNode(proposal.target) as unknown as LowCodePage
    } else {
      const page = config.value!.pages[proposal.pageSlug]
      const next = cloneNode(proposal.target) as unknown as LayoutNode
      if (page.root.id === proposal.nodeId) page.root = next
      else if (!replaceNode(page.root, next)) throw new Error('AI 提案目标节点已发生变化')
      selectedNodeId.value = next.id
    }
    normalizeEditableConfig(config.value!)
  })
  refreshAdvancedJson()
}

async function save() {
  if (!config.value || !activeVersion.value || !canEdit.value) return
  saving.value = true
  try {
    if (activeVersion.value.status !== 'draft') {
      activeVersion.value = await createPortalDraft(selectedSiteKey.value, config.value, '低代码设计器新草稿')
    } else {
      activeVersion.value = await updatePortalDraft(
        selectedSiteKey.value,
        activeVersion.value,
        config.value,
        '低代码布局编辑',
      )
    }
    dirty.value = false
    localStorage.removeItem(snapshotKey.value)
    ElMessage.success('草稿已保存，可预览或继续编辑')
  } finally {
    saving.value = false
  }
}

async function previewChanges() {
  if (!config.value || !activeVersion.value || !canEdit.value) return
  if (activeVersion.value.status === 'draft' && dirty.value) await save()
  else if (activeVersion.value.status === 'published') await save()
  openLivePage()
}

async function validateDraft() {
  await save()
  if (!activeVersion.value) return
  const result = (await portalVersionAction(
    selectedSiteKey.value,
    'validate',
    activeVersion.value.versionId,
  )) as { valid?: boolean; issues?: string[] }
  issues.value = result.issues || []
  if (result.valid) ElMessage.success('布局、权限和数据源校验通过')
  else ElMessage.warning('存在配置问题')
}

async function submit() {
  await validateDraft()
  if (issues.value.length || !activeVersion.value) return
  await portalVersionAction(
    selectedSiteKey.value,
    'submit',
    activeVersion.value.versionId,
    '低代码页面提交审核',
  )
  ElMessage.success('已保存、校验并送审')
  await loadSite(selectedSiteKey.value)
}

async function approve() {
  if (!activeVersion.value) return
  await ElMessageBox.confirm('确认当前门户配置审核通过？', '审核门户')
  await portalVersionAction(
    selectedSiteKey.value,
    'approve',
    activeVersion.value.versionId,
    '低代码门户审核通过',
  )
  ElMessage.success('审核通过，可以发布')
  await loadSite(selectedSiteKey.value)
}

async function reject() {
  if (!activeVersion.value) return
  await ElMessageBox.confirm('确认驳回当前门户配置并退回草稿？', '驳回门户')
  await portalVersionAction(
    selectedSiteKey.value,
    'reject',
    activeVersion.value.versionId,
    '低代码门户审核驳回',
  )
  ElMessage.success('已驳回并退回草稿')
  await loadSite(selectedSiteKey.value)
}

async function publish() {
  if (!activeVersion.value) return
  await ElMessageBox.confirm('发布将原子切换门户，旧版本仍可回滚。确认继续？', '发布门户')
  await portalVersionAction(selectedSiteKey.value, 'publish', activeVersion.value.versionId, '低代码门户发布')
  ElMessage.success('门户已发布')
  await loadSite(selectedSiteKey.value)
}

function saveLocalSnapshot() {
  if (!config.value || !dirty.value) return
  localStorage.setItem(
    snapshotKey.value,
    JSON.stringify({
      versionId: activeVersion.value?.versionId,
      lockVersion: activeVersion.value?.lockVersion,
      savedAt: new Date().toISOString(),
      config: config.value,
    }),
  )
}

function restoreLocalSnapshot() {
  const raw = localStorage.getItem(snapshotKey.value)
  if (!raw || !activeVersion.value) return
  try {
    const snapshot = JSON.parse(raw) as {
      versionId?: number
      lockVersion?: number
      config?: PortalSiteConfigV3
    }
    if (
      snapshot.versionId === activeVersion.value.versionId &&
      snapshot.lockVersion === activeVersion.value.lockVersion &&
      snapshot.config &&
      isPortalSiteConfigV3(snapshot.config)
    ) {
      config.value = normalizeEditableConfig(snapshot.config)
      dirty.value = true
      ElMessage.info('已恢复本机未保存的 V3 编辑副本')
    }
  } catch {
    localStorage.removeItem(snapshotKey.value)
  }
}

watch(config, saveLocalSnapshot, { deep: true })
watch(
  [structureOpen, inspectorOpen, expandedKeys, zoomMode, zoom, previewWidth],
  () => {
    persistWorkspacePreferences()
    if (zoomMode.value === 'fit') nextTick(fitCanvas)
  },
  { deep: true },
)
watch([activePageSlug, breakpoint], () => fitCanvas())
watch([selectedNodeId, inspectorMode], () => {
  propertyBaseline = undefined
  if (inspectorMode.value === 'advanced') refreshAdvancedJson()
})

onMounted(async () => {
  await nextTick()
  designerResizeObserver = new ResizeObserver(([entry]) => {
    const previousWidth = workspaceWidth.value
    workspaceWidth.value = Math.round(entry.contentRect.width)
    if (!preferencesReady) {
      structureOpen.value = workspaceWidth.value >= 900
      inspectorOpen.value = workspaceWidth.value >= 1200
    } else {
      if (previousWidth >= 1200 && workspaceWidth.value < 1200) inspectorOpen.value = false
      if (previousWidth >= 900 && workspaceWidth.value < 900) structureOpen.value = false
    }
    if (zoomMode.value === 'fit') fitCanvas()
  })
  if (designerRoot.value) designerResizeObserver.observe(designerRoot.value)
  stageResizeObserver = new ResizeObserver(() => {
    if (zoomMode.value === 'fit') fitCanvas()
  })
  if (stageViewport.value) stageResizeObserver.observe(stageViewport.value)
  canvasResizeObserver = new ResizeObserver(([entry]) => {
    canvasContentHeight.value = Math.ceil(entry.contentRect.height)
  })
  if (canvasElement.value) canvasResizeObserver.observe(canvasElement.value)
  window.addEventListener('keydown', handleEscape)
  await load()
})

onBeforeUnmount(() => {
  designerResizeObserver?.disconnect()
  stageResizeObserver?.disconnect()
  canvasResizeObserver?.disconnect()
  window.removeEventListener('keydown', handleEscape)
})
</script>

<template>
  <div
    ref="designerRoot"
    class="low-code-designer"
    :class="{
      'is-immersive': immersive,
      'is-structure-closed': !structureOpen,
      'is-inspector-closed': !inspectorOpen,
      'is-narrow-workspace': compactStructure,
    }"
    data-testid="portal-designer"
    v-loading="loading"
  >
    <header class="low-code-toolbar">
      <div class="low-code-toolbar__identity">
        <el-select
          v-model="selectedSiteKey"
          aria-label="选择门户站点"
          class="low-code-site-select"
          @change="loadSite"
        >
          <el-option v-for="site in sites" :key="site.siteKey" :label="site.name" :value="site.siteKey" />
        </el-select>
        <div>
          <strong>{{ activeSite?.name || '门户设计中心' }}</strong>
          <span>
            {{ selectedSiteKey }} · V{{ activeVersion?.versionNo || '-' }} ·
            {{ activeVersion?.status || 'loading' }}
          </span>
        </div>
        <el-tag v-if="dirty" type="warning" effect="plain">未保存</el-tag>
      </div>

      <div class="low-code-toolbar__workspace-actions" aria-label="设计工作区">
        <el-button
          :type="structureOpen ? 'primary' : 'default'"
          plain
          :icon="Operation"
          @click="toggleStructurePanel"
        >
          结构
        </el-button>
        <el-button
          :type="inspectorOpen ? 'primary' : 'default'"
          plain
          :icon="Setting"
          @click="toggleInspectorPanel"
        >
          属性
        </el-button>
        <el-button
          :type="immersive ? 'primary' : 'default'"
          plain
          :icon="FullScreen"
          @click="toggleImmersive"
        >
          {{ immersive ? '退出沉浸' : '沉浸设计' }}
        </el-button>
      </div>

      <div class="low-code-toolbar__actions">
        <div class="low-code-toolbar__secondary-actions">
          <el-button :disabled="!history.length" @click="undo">撤销</el-button>
          <el-button :disabled="!future.length" @click="redo">重做</el-button>
          <el-button v-if="auth.hasAnyPermission(['portal-code:read'])" @click="codeDrawerOpen = true">
            代码组件
          </el-button>
          <el-button :disabled="!canEdit" @click="validateDraft">校验</el-button>
        </div>
        <el-dropdown class="low-code-toolbar__more" trigger="click" @command="handleMoreCommand">
          <el-button aria-label="更多设计操作" :icon="MoreFilled">更多</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="undo" :disabled="!history.length">撤销</el-dropdown-item>
              <el-dropdown-item command="redo" :disabled="!future.length">重做</el-dropdown-item>
              <el-dropdown-item v-if="auth.hasAnyPermission(['portal-code:read'])" command="code">
                代码组件
              </el-dropdown-item>
              <el-dropdown-item command="validate" :disabled="!canEdit">校验</el-dropdown-item>
              <el-dropdown-item
                v-if="activeVersion?.status === 'reviewing'"
                command="reject"
                :disabled="!canReview"
                divided
              >
                驳回
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button
          v-if="activeVersion?.status === 'draft' || activeVersion?.status === 'published'"
          :loading="saving"
          :disabled="!canEdit"
          @click="save"
        >
          保存草稿
        </el-button>
        <el-button
          v-if="activeVersion?.status === 'draft' || activeVersion?.status === 'published'"
          :disabled="!canEdit"
          @click="previewChanges"
        >
          预览变更
        </el-button>
        <el-button v-if="activeVersion?.status === 'reviewing'" :disabled="!canEdit" @click="previewChanges">
          预览变更
        </el-button>
        <el-button
          v-if="activeVersion?.status === 'draft' || activeVersion?.status === 'published'"
          :disabled="!canEdit"
          type="primary"
          @click="submit"
        >
          保存并送审
        </el-button>
        <el-button
          v-if="activeVersion?.status === 'reviewing' && !activeVersion.reviewedAt"
          :disabled="!canReview"
          type="success"
          @click="approve"
        >
          审核通过
        </el-button>
        <el-button
          v-if="activeVersion?.status === 'reviewing' && activeVersion.reviewedAt"
          :disabled="!canPublish"
          type="success"
          @click="publish"
        >
          发布门户
        </el-button>
      </div>
    </header>

    <section class="low-code-workspace">
      <button
        v-if="compactStructure && structureOpen"
        class="low-code-drawer-backdrop"
        aria-label="关闭结构面板"
        @click="structureOpen = false"
      ></button>
      <button
        v-if="compactInspector && inspectorOpen"
        class="low-code-drawer-backdrop is-inspector"
        aria-label="关闭属性面板"
        @click="inspectorOpen = false"
      ></button>
      <aside class="low-code-left" data-testid="structure-panel">
        <header class="low-code-side-panel__header">
          <strong>页面结构</strong>
          <el-button v-if="compactStructure" link @click="structureOpen = false">关闭</el-button>
        </header>
        <el-segmented
          v-model="leftMode"
          :options="[
            { label: '结构', value: 'structure' },
            { label: '组件', value: 'components' },
            { label: '复用', value: 'symbols' },
          ]"
        />

        <div v-if="leftMode === 'structure'" class="low-code-structure">
          <el-tree
            :key="selectedSiteKey"
            :data="structureData"
            node-key="key"
            :props="{ children: 'children', label: 'label' }"
            :current-node-key="selectedStructureKey"
            :default-expanded-keys="expandedKeys"
            :expand-on-click-node="true"
            draggable
            :allow-drag="allowStructureDrag"
            :allow-drop="allowStructureDrop"
            highlight-current
            @node-click="selectStructure"
            @node-expand="rememberExpanded"
            @node-collapse="rememberCollapsed"
            @node-drop="handleStructureDrop"
          >
            <template #default="{ data }">
              <div class="low-code-structure__row" :class="`is-${(data as DesignerStructureNode).kind}`">
                <span class="low-code-structure__label">{{ (data as DesignerStructureNode).label }}</span>
                <span v-if="(data as DesignerStructureNode).nodeType" class="low-code-structure__type">
                  {{ (data as DesignerStructureNode).nodeType }}
                </span>
                <span v-if="(data as DesignerStructureNode).locked" class="low-code-structure__lock">
                  锁定
                </span>
              </div>
            </template>
          </el-tree>
        </div>

        <div v-else-if="leftMode === 'components'" class="low-code-palette">
          <h3>布局</h3>
          <div class="low-code-palette__grid">
            <button
              v-for="item in [
                ['container', '容器'],
                ['grid', '网格'],
                ['stack', '堆叠'],
                ['section', '区段'],
              ]"
              :key="item[0]"
              draggable="true"
              @dragstart="dragPalette($event, 'layout', item[0])"
              @dblclick="addToSelected('layout', item[0])"
            >
              <strong>{{ item[1] }}</strong>
              <small>{{ item[0] }}</small>
            </button>
          </div>
          <h3>业务组件</h3>
          <div class="low-code-palette__grid">
            <button
              v-for="item in cmsBlockDefinitions"
              :key="item.type"
              draggable="true"
              @dragstart="dragPalette($event, 'component', item.type)"
              @dblclick="addToSelected('component', item.type)"
            >
              <strong>{{ item.title }}</strong>
              <small>{{ item.category }}</small>
            </button>
          </div>
          <template v-if="codePackages.length">
            <h3>站点沙箱组件</h3>
            <div class="low-code-palette__grid">
              <button
                v-for="item in codePackages"
                :key="item.packageId"
                :disabled="!item.currentVersion"
                @dblclick="addSandbox(item)"
              >
                <strong>{{ item.displayName }}</strong>
                <small>{{ item.currentVersion || '待发布' }} · 双击添加</small>
              </button>
            </div>
          </template>
        </div>

        <div v-else class="low-code-symbols">
          <el-button class="low-code-full-button" :disabled="!canEdit" @click="createSymbol">
            将所选容器存为复用区块
          </el-button>
          <button v-for="symbol in config?.symbols" :key="symbol.id">
            <span>{{ symbol.name }}</span>
            <small>修订 {{ symbol.revision }}</small>
          </button>
          <el-empty v-if="!Object.keys(config?.symbols || {}).length" description="暂无复用区块" />
        </div>
      </aside>

      <main class="low-code-stage" data-testid="designer-stage">
        <div class="low-code-stage__meta">
          <span>{{ activePage?.title }} · {{ nodeCount }} 个节点</span>
          <el-segmented
            v-model="breakpoint"
            aria-label="响应式断点"
            :options="[
              { label: '桌面 12 列', value: 'desktop' },
              { label: '平板 8 列', value: 'tablet' },
              { label: '手机 4 列', value: 'mobile' },
            ]"
          />
          <div>
            <el-button
              type="success"
              plain
              size="small"
              :icon="MagicStick"
              :disabled="!canEdit"
              @click="aiDrawerOpen = true"
            >
              AI 设计
            </el-button>
            <el-button v-if="livePagePath" link type="primary" @click="openLivePage">
              打开真实页面
            </el-button>
            <span>{{ canvasWidth }}px · {{ zoom }}%</span>
          </div>
        </div>
        <div ref="stageViewport" class="low-code-stage__viewport" data-testid="canvas-viewport">
          <div class="low-code-stage__canvas-frame" :style="canvasFrameStyle">
            <div
              ref="canvasElement"
              class="low-code-stage__canvas"
              :class="`is-${breakpoint}`"
              :style="canvasStyle"
            >
              <DesignerCanvasNode
                v-if="activePage"
                :node="activePage.root"
                :selected-id="selectedNodeId"
                :breakpoint="breakpoint"
                :preview-width="previewWidth"
                @select="selectedNodeId = $event"
                @drop="handleDrop"
              />
            </div>
          </div>
        </div>
      </main>

      <aside class="low-code-inspector" data-testid="inspector-panel">
        <header class="low-code-inspector__header">
          <div>
            <strong>{{ selectedNode?.name || '未选择节点' }}</strong>
            <span>{{ selectedNode?.id || '从画布或结构树选择' }}</span>
          </div>
          <el-button v-if="compactInspector" link @click="inspectorOpen = false">关闭</el-button>
          <div v-if="selectedNode" class="low-code-inspector__node-actions">
            <el-button text @click="moveSelected(-1)">上移</el-button>
            <el-button text @click="moveSelected(1)">下移</el-button>
            <el-button text @click="duplicateSelected">复制</el-button>
            <el-button text type="danger" @click="removeSelected">删除</el-button>
          </div>
        </header>

        <el-tabs v-model="inspectorMode" stretch>
          <el-tab-pane label="布局" name="layout" />
          <el-tab-pane label="内容" name="content" />
          <el-tab-pane label="样式" name="style" />
          <el-tab-pane label="数据" name="data" />
          <el-tab-pane label="交互" name="interaction" />
          <el-tab-pane label="高级" name="advanced" />
        </el-tabs>

        <el-form
          v-if="selectedNode"
          label-position="top"
          class="low-code-inspector__form"
          @focusin="beginPropertyEdit"
          @pointerdown.capture="beginPropertyEdit"
        >
          <template v-if="inspectorMode === 'layout'">
            <el-form-item label="节点名称">
              <el-input v-model="selectedNode.name" :disabled="!canEdit" @change="commitPropertyEdit" />
            </el-form-item>
            <div class="low-code-field-grid">
              <el-form-item label="桌面列宽">
                <el-input-number
                  v-model="selectedNode.layout!.span!.desktop"
                  :min="1"
                  :max="12"
                  :disabled="!canEdit"
                  @change="commitPropertyEdit"
                />
              </el-form-item>
              <el-form-item label="平板列宽">
                <el-input-number
                  v-model="selectedNode.layout!.span!.tablet"
                  :min="1"
                  :max="8"
                  :disabled="!canEdit"
                  @change="commitPropertyEdit"
                />
              </el-form-item>
              <el-form-item label="手机列宽">
                <el-input-number
                  v-model="selectedNode.layout!.span!.mobile"
                  :min="1"
                  :max="4"
                  :disabled="!canEdit"
                  @change="commitPropertyEdit"
                />
              </el-form-item>
            </div>
            <el-form-item v-if="'children' in selectedNode" label="节点间距">
              <el-slider
                v-model="selectedNode.layout!.gap!.desktop"
                :min="0"
                :max="48"
                :disabled="!canEdit"
                @change="commitPropertyEdit"
              />
            </el-form-item>
            <el-form-item label="当前断点隐藏">
              <el-switch
                v-model="selectedNode.layout!.hidden![breakpoint]"
                :disabled="!canEdit || selectedNode.locked"
                @change="commitPropertyEdit"
              />
            </el-form-item>
          </template>

          <template v-else-if="inspectorMode === 'content'">
            <el-form-item label="组件类型">
              <el-input
                :model-value="selectedNode.type === 'component' ? selectedNode.component : selectedNode.type"
                disabled
              />
            </el-form-item>
            <template v-if="selectedNode.type === 'component'">
              <el-form-item v-for="field in selectedComponentFields" :key="field.key" :label="field.label">
                <el-input
                  v-if="field.type === 'text' || field.type === 'textarea'"
                  :model-value="String(componentProperty(field.key) ?? '')"
                  :type="field.type === 'textarea' ? 'textarea' : 'text'"
                  :rows="field.type === 'textarea' ? 4 : undefined"
                  :placeholder="field.placeholder"
                  :disabled="!canEdit"
                  @update:model-value="setComponentProperty(field.key, $event)"
                  @change="commitPropertyEdit"
                />
                <el-input-number
                  v-else-if="field.type === 'number'"
                  :model-value="Number(componentProperty(field.key) ?? field.min ?? 0)"
                  :min="field.min"
                  :max="field.max"
                  :disabled="!canEdit"
                  @update:model-value="setComponentProperty(field.key, $event)"
                  @change="commitPropertyEdit"
                />
                <el-switch
                  v-else-if="field.type === 'boolean'"
                  :model-value="Boolean(componentProperty(field.key))"
                  :disabled="!canEdit"
                  @update:model-value="setComponentProperty(field.key, $event)"
                  @change="commitPropertyEdit"
                />
                <el-select
                  v-else
                  :model-value="String(componentProperty(field.key) ?? '')"
                  :disabled="!canEdit"
                  @update:model-value="setComponentProperty(field.key, $event)"
                  @change="commitPropertyEdit"
                >
                  <el-option
                    v-for="option in field.options"
                    :key="option.value"
                    :label="option.label"
                    :value="option.value"
                  />
                </el-select>
              </el-form-item>
            </template>
            <el-alert
              title="属性由组件 Schema 约束；未知字段在发布校验时会被拒绝。"
              type="info"
              :closable="false"
            />
          </template>

          <template v-else-if="inspectorMode === 'style'">
            <el-form-item label="背景">
              <el-input
                v-model="selectedNode.style!.background"
                placeholder="var(--kma-color-surface)"
                :disabled="!canEdit"
                @change="commitPropertyEdit"
              />
            </el-form-item>
            <el-form-item label="圆角">
              <el-select
                v-model="selectedNode.style!.radius"
                :disabled="!canEdit"
                @change="commitPropertyEdit"
              >
                <el-option label="无" value="0px" />
                <el-option label="小" value="8px" />
                <el-option label="标准" value="12px" />
                <el-option label="大" value="20px" />
              </el-select>
            </el-form-item>
            <el-form-item label="内边距">
              <el-slider
                v-model="selectedNode.style!.padding!.desktop"
                :min="0"
                :max="64"
                :step="4"
                :disabled="!canEdit"
                @change="commitPropertyEdit"
              />
            </el-form-item>
          </template>

          <template v-else-if="inspectorMode === 'data'">
            <el-form-item v-if="selectedNode.type === 'component'" label="注册数据源">
              <el-select
                v-model="selectedNode.dataSource!.source"
                clearable
                :disabled="!canEdit"
                @change="commitPropertyEdit"
              >
                <el-option
                  v-for="source in [
                    'documents',
                    'categories',
                    'topics',
                    'favorites',
                    'history',
                    'announcements',
                    'static',
                  ]"
                  :key="source"
                  :label="source"
                  :value="source"
                />
              </el-select>
            </el-form-item>
            <el-alert
              title="只能选择注册数据源；站点范围、RBAC 与空间 ACL 仍由后端裁决。"
              type="success"
              :closable="false"
            />
          </template>

          <template v-else-if="inspectorMode === 'interaction'">
            <el-form-item label="点击动作">
              <el-select
                v-if="selectedNode.type === 'component'"
                v-model="selectedNode.actions![0].type"
                clearable
                :disabled="!canEdit"
                @change="commitPropertyEdit"
              >
                <el-option label="页面跳转" value="navigate" />
                <el-option label="执行搜索" value="search" />
                <el-option label="AI 提问" value="ask" />
                <el-option label="打开内容" value="open-content" />
                <el-option label="反馈" value="feedback" />
                <el-option label="分析事件" value="analytics" />
              </el-select>
            </el-form-item>
            <el-alert
              title="交互使用受控动作，不执行 JavaScript、任意 API 或 SQL。"
              type="warning"
              :closable="false"
            />
          </template>
          <template v-else>
            <el-alert
              title="仅允许编辑安全属性；节点 ID、类型、锁定状态和子节点结构受到保护。"
              type="info"
              :closable="false"
            />
            <el-form-item label="高级属性 JSON">
              <el-input
                v-model="advancedJson"
                type="textarea"
                :rows="18"
                spellcheck="false"
                :disabled="!canEdit"
              />
            </el-form-item>
            <el-button type="primary" plain :disabled="!canEdit" @click="applyAdvancedJson">
              应用高级属性
            </el-button>
          </template>
        </el-form>
        <el-empty v-else description="选择节点后编辑属性" />
      </aside>
    </section>

    <footer class="low-code-statusbar">
      <div class="low-code-statusbar__width">
        <span>画布</span>
        <el-slider
          v-model="previewWidth"
          aria-label="预览宽度"
          :min="PREVIEW_WIDTH_MIN"
          :max="PREVIEW_WIDTH_MAX"
          :step="10"
          :show-tooltip="false"
        />
        <small>{{ previewWidth }}px</small>
      </div>
      <div class="low-code-statusbar__zoom">
        <span>缩放</span>
        <el-slider v-model="zoom" :min="40" :max="110" :show-tooltip="false" @input="setManualZoom" />
        <button type="button" @click="fitCanvas">适应</button>
        <small>{{ zoomMode === 'fit' ? '自动' : '手动' }}</small>
      </div>
      <span class="low-code-statusbar__path">{{ breadcrumbs.join(' / ') }}</span>
      <button :class="{ 'has-issues': issues.length }" @click="issues = []">
        {{ issues.length ? `${issues.length} 个校验问题` : '布局状态正常' }}
      </button>
    </footer>
    <PortalAiDesignDrawer
      v-model="aiDrawerOpen"
      :site-key="selectedSiteKey"
      :version-id="activeVersion?.versionId"
      :lock-version="activeVersion?.lockVersion"
      :config="config"
      :page-slug="activePageSlug"
      :selected-node="selectedNode"
      :can-edit="canEdit"
      @apply="applyAiProposal"
    />
    <PortalCodeEditorDrawer v-model="codeDrawerOpen" @changed="refreshCodePackages" />
  </div>
</template>

<style scoped>
.low-code-designer {
  --designer-border: #d9e5e1;
  position: relative;
  container-name: designer;
  container-type: inline-size;
  display: grid;
  grid-template-rows: 58px minmax(0, 1fr) 42px;
  height: calc(100vh - 40px);
  min-height: 680px;
  overflow: hidden;
  color: #17342f;
  background: #f3f6f4;
  border: 1px solid var(--designer-border);
  border-radius: 12px;
}

.low-code-designer.is-immersive {
  position: fixed;
  z-index: 100;
  inset: 8px;
  height: calc(100dvh - 16px);
  min-height: 0;
  border-radius: 10px;
  box-shadow: 0 20px 80px rgb(4 38 32 / 0.3);
}

.low-code-designer.is-narrow-workspace {
  grid-template-rows: 96px minmax(0, 1fr) 42px;
  height: calc(100dvh - 24px);
}

.low-code-designer.is-narrow-workspace.is-immersive {
  height: calc(100dvh - 16px);
}

.low-code-toolbar {
  z-index: 5;
  display: grid;
  grid-template-columns: minmax(240px, 1fr) auto minmax(460px, 1fr);
  align-items: center;
  gap: 16px;
  padding: 8px 12px;
  background: #fff;
  border-bottom: 1px solid var(--designer-border);
}

.low-code-toolbar__identity,
.low-code-toolbar__actions,
.low-code-toolbar__workspace-actions,
.low-code-toolbar__secondary-actions,
.low-code-inspector__node-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.low-code-toolbar__identity > div {
  display: grid;
  min-width: 0;
}

.low-code-toolbar__identity span,
.low-code-inspector header span,
.low-code-stage__meta,
.low-code-symbols small {
  color: #71837f;
  font-size: 11px;
}

.low-code-site-select {
  width: 160px;
}

.low-code-toolbar__actions {
  justify-content: flex-end;
}

.low-code-toolbar__more {
  display: none;
}

.low-code-workspace {
  position: relative;
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr) 320px;
  min-height: 0;
  overflow: hidden;
  transition: grid-template-columns 160ms ease;
}

.is-structure-closed .low-code-workspace {
  grid-template-columns: 0 minmax(0, 1fr) 320px;
}

.is-inspector-closed .low-code-workspace {
  grid-template-columns: 240px minmax(0, 1fr) 0;
}

.is-structure-closed.is-inspector-closed .low-code-workspace {
  grid-template-columns: 0 minmax(0, 1fr) 0;
}

.low-code-left,
.low-code-inspector {
  position: relative;
  z-index: 2;
  min-height: 0;
  padding: 12px;
  overflow: auto;
  background: #fff;
  transition:
    transform 160ms ease,
    opacity 160ms ease;
}

.low-code-left {
  border-right: 1px solid var(--designer-border);
}

.low-code-inspector {
  border-left: 1px solid var(--designer-border);
}

.is-structure-closed .low-code-left,
.is-inspector-closed .low-code-inspector {
  visibility: hidden;
  padding-inline: 0;
  opacity: 0;
}

.low-code-side-panel__header {
  display: none;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.low-code-left > :deep(.el-segmented) {
  width: 100%;
}

.low-code-left > :deep(.el-segmented__group) {
  width: 100%;
}

.low-code-left > :deep(.el-segmented__item) {
  flex: 1;
}

.low-code-structure,
.low-code-symbols {
  margin-top: 12px;
}

.low-code-structure :deep(.el-tree) {
  --el-tree-node-hover-bg-color: #edf8f4;
  color: inherit;
  background: transparent;
}

.low-code-structure :deep(.el-tree-node__content) {
  height: 34px;
  margin: 1px 0;
  border: 1px solid transparent;
  border-radius: 7px;
}

.low-code-structure :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: #e5f5ef;
  border-color: #9dcebf;
}

.low-code-structure__row {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-width: 0;
  padding-right: 6px;
}

.low-code-structure__row.is-group {
  color: #41635b;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.06em;
}

.low-code-structure__label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.low-code-structure__type,
.low-code-structure__lock {
  flex: 0 0 auto;
  color: #788c87;
  font-size: 9px;
}

.low-code-structure__lock {
  padding: 1px 4px;
  color: #875a00;
  background: #fff4d6;
  border-radius: 4px;
}

.low-code-symbols {
  display: grid;
  gap: 6px;
}

.low-code-symbols button {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  padding: 9px 10px;
  color: inherit;
  text-align: left;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 8px;
}

.low-code-symbols :where(button:hover) {
  background: #edf8f4;
  border-color: #a6d5c8;
}

.low-code-palette h3 {
  margin: 16px 0 8px;
  font-size: 12px;
}

.low-code-palette__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.low-code-palette__grid button {
  display: grid;
  gap: 4px;
  min-height: 58px;
  padding: 9px;
  color: inherit;
  text-align: left;
  background: #f8faf9;
  border: 1px solid var(--designer-border);
  border-radius: 8px;
  cursor: grab;
}

.low-code-palette__grid :where(button:hover) {
  border-color: #22a785;
  box-shadow: 0 4px 12px rgb(0 68 56 / 0.08);
}

.low-code-palette__grid small {
  color: #7b8d89;
  font-size: 10px;
}

.low-code-stage {
  display: grid;
  grid-template-rows: 42px minmax(0, 1fr);
  min-width: 0;
  min-height: 0;
  background-color: #e9efec;
  background-image: radial-gradient(#cbd8d3 0.7px, transparent 0.7px);
  background-size: 16px 16px;
}

.low-code-stage__meta {
  display: grid;
  grid-template-columns: minmax(120px, 1fr) auto minmax(120px, 1fr);
  align-items: center;
  gap: 12px;
  padding: 0 12px;
  background: rgb(255 255 255 / 0.88);
  border-bottom: 1px solid var(--designer-border);
}

.low-code-stage__meta > div {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.low-code-stage__meta > :deep(.el-segmented) {
  --el-segmented-item-selected-bg-color: #dff2ec;
  --el-segmented-item-selected-color: #096c59;
}

.low-code-stage__meta :deep(.el-button) {
  height: auto;
  padding: 0;
  font-size: 11px;
}

.low-code-stage__viewport {
  padding: 28px;
  overflow: auto;
}

.low-code-stage__canvas-frame {
  position: relative;
  margin: 0 auto;
  transition:
    width 160ms ease,
    height 160ms ease;
}

.low-code-stage__canvas {
  position: absolute;
  top: 0;
  left: 0;
  padding: 24px;
  overflow: hidden;
  background: #fffdf8;
  border: 1px solid #cbd8d3;
  border-radius: 4px;
  box-shadow: 0 10px 32px rgb(18 55 48 / 0.12);
  transform-origin: top left;
  transition: transform 160ms ease;
}

.low-code-stage__canvas.is-tablet {
  min-height: 768px;
}

.low-code-stage__canvas.is-mobile {
  min-height: 844px;
  padding: 16px;
}

.low-code-inspector__header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--designer-border);
}

.low-code-inspector__header > div:first-child {
  display: grid;
}

.low-code-inspector__node-actions {
  grid-column: 1 / -1;
  flex-wrap: wrap;
}

.low-code-inspector__form {
  padding-top: 8px;
}

.low-code-field-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.low-code-field-grid :deep(.el-input-number) {
  width: 100%;
}

.low-code-full-button {
  width: 100%;
}

.low-code-statusbar {
  display: grid;
  grid-template-columns: 250px 260px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 0 12px;
  font-size: 11px;
  background: #fff;
  border-top: 1px solid var(--designer-border);
}

.low-code-statusbar__width,
.low-code-statusbar__zoom {
  display: grid;
  align-items: center;
  gap: 8px;
}

.low-code-statusbar__width {
  grid-template-columns: 36px minmax(100px, 1fr) 52px;
}

.low-code-statusbar__zoom {
  grid-template-columns: 36px minmax(80px, 1fr) auto auto;
}

.low-code-statusbar__width small,
.low-code-statusbar__zoom small {
  color: #71837f;
}

.low-code-statusbar__path {
  overflow: hidden;
  color: #6c807b;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.low-code-statusbar button {
  color: #27765f;
  background: transparent;
  border: 0;
}

.low-code-statusbar button.has-issues {
  color: #a15b00;
}

:deep(.designer-node) {
  grid-column: span var(--designer-span, 12);
  min-width: 0;
  padding: 8px;
  background: rgb(255 255 255 / 0.6);
  border: 1px dashed #b9cbc5;
  border-radius: 8px;
}

:deep(.designer-node.is-selected) {
  border-color: #008b70;
  outline: 2px solid rgb(0 139 112 / 0.16);
}

:deep(.designer-node__label) {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
  color: #4c645e;
  font-size: 11px;
}

:deep(.designer-node__label small) {
  color: #8b9a96;
}

:deep(.designer-node__children) {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 10px;
  min-height: 48px;
}

.is-tablet :deep(.designer-node__children) {
  grid-template-columns: repeat(8, minmax(0, 1fr));
}

.is-mobile :deep(.designer-node__children) {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

:deep(.designer-node__empty),
:deep(.designer-node__preview) {
  display: grid;
  grid-column: 1 / -1;
  place-items: center;
  min-height: 54px;
  color: #7b8f89;
  font-size: 12px;
  background: #f3f7f5;
  border-radius: 6px;
}

:deep(.designer-node__preview.has-component) {
  display: block;
  background: transparent;
}

@container designer (max-width: 1199px) {
  .low-code-toolbar {
    grid-template-columns: minmax(220px, 1fr) auto auto;
    gap: 10px;
  }

  .low-code-toolbar__secondary-actions {
    display: none;
  }

  .low-code-toolbar__more {
    display: inline-flex;
  }

  .low-code-workspace {
    grid-template-columns: 220px minmax(0, 1fr);
  }

  .is-inspector-closed .low-code-workspace {
    grid-template-columns: 220px minmax(0, 1fr);
  }

  .is-structure-closed .low-code-workspace,
  .is-structure-closed.is-inspector-closed .low-code-workspace {
    grid-template-columns: 0 minmax(0, 1fr);
  }

  .low-code-inspector {
    position: absolute;
    z-index: 16;
    top: 0;
    right: 0;
    bottom: 0;
    width: 320px;
    visibility: visible;
    padding: 12px;
    opacity: 1;
    box-shadow: -8px 0 24px rgb(14 59 51 / 0.12);
    transform: translateX(0);
  }

  .is-inspector-closed .low-code-inspector {
    visibility: hidden;
    padding: 12px;
    opacity: 0;
    transform: translateX(102%);
  }

  .low-code-drawer-backdrop {
    position: absolute;
    z-index: 14;
    inset: 0;
    display: block;
    padding: 0;
    background: rgb(13 49 42 / 0.18);
    border: 0;
  }
}

@container designer (max-width: 899px) {
  .low-code-toolbar {
    grid-template-columns: minmax(160px, 1fr) auto;
    grid-template-rows: 40px 40px;
    gap: 0 8px;
  }

  .low-code-toolbar__identity > div {
    display: none;
  }

  .low-code-site-select {
    width: min(150px, 32vw);
  }

  .low-code-toolbar__workspace-actions {
    grid-column: 1;
    grid-row: 2;
  }

  .low-code-toolbar__actions {
    grid-column: 2;
    grid-row: 1 / span 2;
    flex-wrap: wrap;
    max-width: 260px;
  }

  .low-code-workspace,
  .is-structure-closed .low-code-workspace,
  .is-inspector-closed .low-code-workspace,
  .is-structure-closed.is-inspector-closed .low-code-workspace {
    grid-template-columns: minmax(0, 1fr);
  }

  .low-code-left {
    position: absolute;
    z-index: 16;
    top: 0;
    bottom: 0;
    left: 0;
    width: min(300px, 86%);
    visibility: visible;
    padding: 12px;
    opacity: 1;
    box-shadow: 8px 0 24px rgb(14 59 51 / 0.12);
    transform: translateX(0);
  }

  .is-structure-closed .low-code-left {
    visibility: hidden;
    padding: 12px;
    opacity: 0;
    transform: translateX(-102%);
  }

  .low-code-side-panel__header {
    display: flex;
  }

  .low-code-stage__meta {
    grid-template-columns: minmax(90px, 1fr) auto minmax(90px, 1fr);
    gap: 8px;
  }

  .low-code-stage__viewport {
    padding: 18px;
  }

  .low-code-statusbar {
    grid-template-columns: minmax(180px, 1fr) minmax(210px, 1fr) auto;
  }

  .low-code-statusbar__path {
    display: none;
  }
}

@container designer (max-width: 620px) {
  .low-code-stage__meta {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .low-code-stage__meta > span:first-child {
    display: none;
  }
}
</style>
