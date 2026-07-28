<script setup lang="ts">
import type { editor as MonacoEditor } from 'monaco-editor/editor/editor.api.js'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import {
  getPortalPreviewBootstrap,
  getPortalThemeWorkspace,
  applyPortalTheme,
  exportPortalTheme,
  importPortalTheme,
  listPortalThemes,
  listPortalSites,
  portalVersionAction,
  proposePortalTheme,
  savePortalThemeWorkspace,
  syncPortalThemeLocalSource,
  publishPortalThemeImmediately,
  type PortalThemeDesignProposal,
  type PortalThemeCatalogItem,
  type PortalThemeWorkspace,
} from '../../api/portalSites'
import type { PortalBootstrap, PortalThemeRuntime } from '../../cms/siteConfig'
import { buildThemeDocument } from '../../cms/v4/themeRuntime'
import { useAuthStore } from '../../stores/auth'

const sites = ref<Array<{ siteKey: string; name: string }>>([])
const siteKey = ref('default')
const workspace = ref<PortalThemeWorkspace>()
const themeCatalog = ref<PortalThemeCatalogItem[]>([])
const selectedThemeKey = ref('heritage-red')
const files = reactive<Record<string, string>>({})
const activePath = ref('layout.html')
const routePage = ref('home')
const previewWidth = ref(1024)
const loading = ref(false)
const saving = ref(false)
const publishing = ref(false)
const dirty = ref(false)
const aiOpen = ref(false)
const aiLoading = ref(false)
const aiInstruction = ref('')
const aiProposal = ref<PortalThemeDesignProposal>()
const aiUndoFiles = ref<Record<string, string>>()
const editorHost = ref<HTMLElement>()
const zipInput = ref<HTMLInputElement>()
const previewBootstrap = ref<PortalBootstrap>()
let editorInstance: MonacoEditor.IStandaloneCodeEditor | undefined
let monaco: typeof import('monaco-editor/editor/editor.api.js') | undefined
let switchingModel = false

const pageRoutes = [
  ['home', '首页'],
  ['library', '资料中心'],
  ['topics', '专题目录'],
  ['ask', 'AI 问答'],
  ['content', '正文'],
  ['search', '搜索'],
  ['favorites', '收藏'],
  ['profile', '个人中心'],
] as const

const fileTree = computed(() => {
  const groups = new Map<string, Array<{ label: string; path: string }>>()
  Object.keys(files)
    .sort()
    .forEach((path) => {
      const group = path.includes('/') ? path.split('/')[0] : '全站'
      const entries = groups.get(group) || []
      entries.push({ label: path.split('/').at(-1) || path, path })
      groups.set(group, entries)
    })
  return [...groups].map(([label, children]) => ({ label, children }))
})
const issues = computed(() => workspace.value?.version.scanResult?.issues || [])
const portalStatus = computed(() => workspace.value?.portalVersion.status || 'draft')
const selectedTheme = computed(() =>
  themeCatalog.value.find((theme) => theme.themeKey === selectedThemeKey.value),
)
const auth = useAuthStore()
const canDirectPublish = computed(() =>
  ['portal-site:update', 'portal-page:edit', 'portal-code:edit', 'portal-site:publish'].every(
    (permission) => auth.permissions.has('kma:admin') || auth.permissions.has(permission),
  ),
)
const localSourceChanged = computed(() => {
  const theme = selectedTheme.value
  return Boolean(
    theme?.localSourceAvailable &&
    theme.localSourceChecksum &&
    theme.currentChecksum &&
    theme.localSourceChecksum !== theme.currentChecksum,
  )
})
const previewSource = computed(() => {
  const bootstrap = previewBootstrap.value
  const current = workspace.value
  if (!bootstrap || !current) return ''
  const runtime: PortalThemeRuntime = {
    versionId: current.version.themeVersionId,
    versionNo: current.version.versionNo,
    status: 'draft',
    manifest: current.version.manifest,
    checksum: current.version.checksum,
    themeKey: current.theme.themeKey,
    displayName: current.theme.displayName,
    files: { ...files },
  }
  return buildThemeDocument(runtime, {
    ...bootstrap,
    themeRuntime: runtime,
    page: {
      slug: routePage.value,
      kind: routePage.value,
      title: pageRoutes.find(([key]) => key === routePage.value)?.[1] || routePage.value,
      template: `pages/${routePage.value}.html`,
    },
  })
})

function language(path: string) {
  if (path.endsWith('.html')) return 'html'
  if (path.endsWith('.css')) return 'css'
  if (path.endsWith('.js')) return 'javascript'
  if (path.endsWith('.json')) return 'json'
  return 'plaintext'
}

async function mountEditor() {
  if (!editorHost.value || editorInstance) return
  monaco = await import('monaco-editor/editor/editor.api.js')
  editorInstance = monaco.editor.create(editorHost.value, {
    value: files[activePath.value] || '',
    language: language(activePath.value),
    theme: 'vs-dark',
    automaticLayout: true,
    minimap: { enabled: true },
    fontSize: 13,
    lineHeight: 21,
    tabSize: 2,
    wordWrap: 'on',
    scrollBeyondLastLine: false,
  })
  editorInstance.onDidChangeModelContent(() => {
    if (switchingModel || !editorInstance) return
    files[activePath.value] = editorInstance.getValue()
    dirty.value = true
  })
}

function openFile(path: string) {
  if (!files[path] || path === activePath.value) return
  activePath.value = path
  if (!editorInstance || !monaco) return
  switchingModel = true
  const previous = editorInstance.getModel()
  editorInstance.setModel(monaco.editor.createModel(files[path], language(path)))
  previous?.dispose()
  switchingModel = false
}

function replaceFiles(nextFiles: Record<string, string>) {
  Object.keys(files).forEach((path) => delete files[path])
  Object.assign(files, nextFiles)
  if (!(activePath.value in files)) activePath.value = Object.keys(files)[0] || 'layout.html'
  if (editorInstance && monaco) {
    switchingModel = true
    const previous = editorInstance.getModel()
    editorInstance.setModel(
      monaco.editor.createModel(files[activePath.value] || '', language(activePath.value)),
    )
    previous?.dispose()
    switchingModel = false
  }
}

async function loadThemeCatalog() {
  themeCatalog.value = await listPortalThemes(siteKey.value)
  if (!themeCatalog.value.some((theme) => theme.themeKey === selectedThemeKey.value)) {
    selectedThemeKey.value =
      themeCatalog.value.find((theme) => theme.recommended)?.themeKey || themeCatalog.value[0]?.themeKey || ''
  }
}

async function loadWorkspace() {
  loading.value = true
  try {
    const result = await getPortalThemeWorkspace(siteKey.value, selectedThemeKey.value || undefined)
    workspace.value = result
    replaceFiles(result.files)
    dirty.value = false
    await loadPreview()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '主题工作区加载失败')
  } finally {
    loading.value = false
  }
}

async function switchTheme(themeKey: string) {
  if (themeKey === selectedThemeKey.value) return
  if (dirty.value) {
    try {
      await ElMessageBox.confirm('当前主题有未保存修改。保存后切换，或放弃这些修改。', '切换主题', {
        confirmButtonText: '保存并切换',
        cancelButtonText: '放弃修改',
        distinguishCancelAndClose: true,
        type: 'warning',
      })
      if (!(await save())) return
    } catch (action) {
      if (action !== 'cancel') return
      dirty.value = false
    }
  }
  selectedThemeKey.value = themeKey
  await loadWorkspace()
}

async function applyTheme() {
  const current = workspace.value
  if (!current) return
  if (dirty.value && !(await save())) return
  try {
    const result = await applyPortalTheme(siteKey.value, current.version.themeVersionId)
    workspace.value = result
    replaceFiles(result.files)
    dirty.value = false
    await loadThemeCatalog()
    await loadPreview()
    ElMessage.success('主题已应用到门户草稿；请继续真实预览并按流程送审发布')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '应用主题失败')
  }
}

async function syncLocalSource() {
  const theme = selectedTheme.value
  if (!theme || !theme.localSourceAvailable) {
    return ElMessage.warning(theme?.localSourceMessage || '当前主题没有可用的本地源码包')
  }
  if (dirty.value) {
    try {
      await ElMessageBox.confirm('先保存或放弃当前未保存修改后，才能从本地源码创建新草稿。', '同步本地源码', {
        confirmButtonText: '保存后同步',
        cancelButtonText: '取消',
        type: 'warning',
      })
      if (!(await save())) return
    } catch {
      return
    }
  }
  try {
    const result = await syncPortalThemeLocalSource(siteKey.value, theme.themeKey)
    workspace.value = result
    replaceFiles(result.files)
    dirty.value = false
    await loadThemeCatalog()
    await loadPreview()
    ElMessage.success('已从本地主题源码创建新的草稿版本，尚未应用或发布')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '本地源码同步失败')
  }
}

async function publishImmediately() {
  const current = workspace.value
  const theme = selectedTheme.value
  if (!current || !theme) return
  const publishEditorChanges = dirty.value
  if (publishEditorChanges && !(await save())) return
  const refreshed = workspace.value
  if (!refreshed) return
  publishing.value = true
  try {
    const result = await publishPortalThemeImmediately(siteKey.value, theme.themeKey, {
      themeVersionId: refreshed.version.themeVersionId,
      // An online edit is the explicit source of truth for this operation.  Otherwise snapshot
      // a changed checked-out package before applying it.
      syncLocalSource: !publishEditorChanges && localSourceChanged.value,
    })
    workspace.value = result
    replaceFiles(result.files)
    dirty.value = false
    await loadThemeCatalog()
    await loadPreview()
    ElMessage.success('主题已原子发布，门户访客已看到新版本')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '立即发布失败；当前已发布门户未改变')
  } finally {
    publishing.value = false
  }
}

async function loadPreview() {
  if (!workspace.value) return
  try {
    previewBootstrap.value = await getPortalPreviewBootstrap(
      siteKey.value,
      workspace.value.portalVersion.versionId,
      routePage.value,
    )
  } catch {
    previewBootstrap.value = undefined
  }
}

async function save() {
  const current = workspace.value
  if (!current) return false
  saving.value = true
  try {
    const result = await savePortalThemeWorkspace(siteKey.value, current.version.themeVersionId, {
      files: { ...files },
      manifest: current.version.manifest || {
        capabilities: ['page-context', 'contents', 'search', 'ask', 'navigation'],
        entry: 'layout.html',
      },
      expectedLockVersion: current.version.lockVersion,
    })
    workspace.value = result
    dirty.value = false
    await loadThemeCatalog()
    await loadPreview()
    if (result.version.scanStatus !== 'passed') {
      ElMessage.warning('草稿已保存，但安全扫描未通过')
      return false
    }
    ElMessage.success('主题草稿已保存并通过安全扫描')
    return true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
    return false
  } finally {
    saving.value = false
  }
}

async function act(action: 'submit' | 'approve' | 'publish') {
  if (action === 'submit' && !(await save())) return
  const current = workspace.value
  if (!current) return
  try {
    await portalVersionAction(siteKey.value, action, current.portalVersion.versionId)
    current.portalVersion.status = action === 'submit' || action === 'approve' ? 'reviewing' : 'published'
    if (action === 'approve') current.portalVersion.reviewedAt = new Date().toISOString()
    ElMessage.success(
      action === 'submit' ? '已送审' : action === 'approve' ? '审核已通过' : '门户主题已原子发布',
    )
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  }
}

async function previewSaved() {
  if (dirty.value && !(await save())) return
  const current = workspace.value
  if (!current) return
  const previewWindow = window.open(
    `/p/${encodeURIComponent(siteKey.value)}/${routePage.value}?previewVersion=${current.portalVersion.versionId}`,
    '_blank',
  )
  if (previewWindow) previewWindow.opener = null
}

async function generateAiTheme() {
  const current = workspace.value
  if (!current || !aiInstruction.value.trim()) return
  aiLoading.value = true
  try {
    aiProposal.value = await proposePortalTheme(siteKey.value, current.version.themeVersionId, {
      expectedLockVersion: current.version.lockVersion,
      files: { ...files },
      instruction: aiInstruction.value.trim(),
    })
    ElMessage.success('DeepSeek 已生成多文件主题提案，请先检查差异与即时预览')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'AI 主题生成失败')
  } finally {
    aiLoading.value = false
  }
}

function applyAiProposal() {
  if (!aiProposal.value) return
  aiUndoFiles.value = { ...files }
  replaceFiles(aiProposal.value.files)
  dirty.value = true
  aiOpen.value = false
  ElMessage.success('AI 提案已应用到本地草稿，尚未保存或发布')
}

function undoAiProposal() {
  if (!aiUndoFiles.value) return
  replaceFiles(aiUndoFiles.value)
  aiUndoFiles.value = undefined
  dirty.value = true
  ElMessage.success('已撤销最近一次 AI 主题提案')
}

async function addFile() {
  const result = await ElMessageBox.prompt('相对路径，例如 partials/hero.html', '新增主题文件', {
    inputPattern: /^(?:pages|partials|styles|scripts|assets)\/[a-zA-Z0-9._/-]+$/,
    inputErrorMessage: '文件必须位于允许的主题目录内',
  })
  const path = result.value.trim()
  if (path in files) return ElMessage.warning('文件已存在')
  files[path] = path.endsWith('.html') ? '<section class="theme-section">新区域</section>' : ''
  dirty.value = true
  openFile(path)
}

async function renameFile() {
  if (['layout.html', 'pages/home.html', 'styles/theme.css'].includes(activePath.value))
    return ElMessage.warning('核心文件不能重命名')
  const result = await ElMessageBox.prompt('新相对路径', '重命名文件', {
    inputValue: activePath.value,
  })
  const next = result.value.trim()
  if (!next || next in files) return ElMessage.warning('目标路径无效或已存在')
  files[next] = files[activePath.value]
  delete files[activePath.value]
  activePath.value = next
  dirty.value = true
  openFile(next)
}

async function removeFile() {
  if (['layout.html', 'pages/home.html', 'styles/theme.css'].includes(activePath.value))
    return ElMessage.warning('核心文件不能删除')
  await ElMessageBox.confirm(`删除 ${activePath.value}？`, '删除主题文件', { type: 'warning' })
  delete files[activePath.value]
  activePath.value = Object.keys(files)[0] || 'layout.html'
  dirty.value = true
  openFile(activePath.value)
}

async function importZip(event: Event) {
  const current = workspace.value
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!current || !file) return
  try {
    const result = await importPortalTheme(
      siteKey.value,
      current.version.themeVersionId,
      current.version.lockVersion,
      file,
    )
    workspace.value = result
    replaceFiles(result.files)
    dirty.value = false
    await loadPreview()
    ElMessage.success('ZIP 主题已导入草稿并完成安全扫描')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'ZIP 导入失败')
  }
}

watch(routePage, () => void loadPreview())
watch(
  siteKey,
  () =>
    void (async () => {
      await loadThemeCatalog()
      await loadWorkspace()
    })(),
)
onMounted(async () => {
  sites.value = await listPortalSites()
  if (!sites.value.some((site) => site.siteKey === siteKey.value))
    siteKey.value = sites.value[0]?.siteKey || 'default'
  await loadThemeCatalog()
  await loadWorkspace()
  await nextTick()
  await mountEditor()
})
onBeforeUnmount(() => editorInstance?.dispose())
</script>

<template>
  <div class="theme-studio" v-loading="loading">
    <header class="studio-toolbar">
      <div class="studio-title">
        <strong>Portal Theme V4</strong>
        <span>全站主题工作台</span>
        <el-tag :type="dirty ? 'warning' : 'success'" size="small">
          {{ dirty ? '未保存' : `V${workspace?.version.versionNo || '-'}` }}
        </el-tag>
      </div>
      <el-select v-model="siteKey" aria-label="站点" style="width: 150px">
        <el-option v-for="site in sites" :key="site.siteKey" :label="site.name" :value="site.siteKey" />
      </el-select>
      <el-select v-model="routePage" aria-label="预览路由" style="width: 130px">
        <el-option v-for="[key, label] in pageRoutes" :key="key" :label="label" :value="key" />
      </el-select>
      <el-button @click="previewSaved">真实预览</el-button>
      <el-button @click="workspace && exportPortalTheme(siteKey, workspace.version.themeVersionId)">
        导出 ZIP
      </el-button>
      <el-button @click="zipInput?.click()">导入 ZIP</el-button>
      <input ref="zipInput" class="visually-hidden" type="file" accept=".zip" @change="importZip" />
      <el-button type="warning" plain @click="aiOpen = true">AI 设计整站</el-button>
      <el-button v-if="aiUndoFiles" @click="undoAiProposal">撤销 AI</el-button>
      <el-button :loading="saving" @click="save">保存草稿</el-button>
      <el-button
        v-if="canDirectPublish"
        type="primary"
        :loading="publishing"
        :disabled="!workspace"
        @click="publishImmediately"
      >
        {{ localSourceChanged && !dirty ? '同步并立即发布' : '立即发布' }}
      </el-button>
      <el-button v-else-if="portalStatus === 'draft'" type="primary" @click="act('submit')">
        保存并送审
      </el-button>
      <el-button
        v-else-if="portalStatus === 'reviewing' && !workspace?.portalVersion.reviewedAt"
        type="primary"
        @click="act('approve')"
      >
        通过审核
      </el-button>
      <el-button
        v-else-if="portalStatus === 'reviewing' && workspace?.portalVersion.reviewedAt"
        type="primary"
        @click="act('publish')"
      >
        发布门户
      </el-button>
    </header>

    <section class="theme-switcher" aria-label="主题快速选择">
      <span class="theme-switcher__label">当前主题</span>
      <el-select
        :model-value="selectedThemeKey"
        aria-label="当前主题"
        class="theme-switcher__select"
        @update:model-value="switchTheme"
      >
        <el-option
          v-for="theme in themeCatalog"
          :key="theme.themeId"
          :label="theme.displayName"
          :value="theme.themeKey"
        >
          <div class="theme-option">
            <span class="theme-swatch" :class="`theme-swatch--${theme.themeKey}`" />
            <span class="theme-option__copy"
              ><strong>{{ theme.displayName }}</strong
              ><small
                >V{{ theme.versionNo }} · {{ theme.scanStatus === 'passed' ? '扫描通过' : '待处理' }}</small
              ></span
            >
            <el-tag v-if="theme.published" size="small" type="success">当前发布</el-tag>
            <el-tag v-else-if="theme.recommended" size="small" type="warning">推荐</el-tag>
          </div>
        </el-option>
      </el-select>
      <span class="theme-switcher__meta" :title="selectedTheme?.description">
        {{ selectedTheme?.description || '选择一个主题进入文件工作区' }}
      </span>
      <el-tag v-if="localSourceChanged" size="small" type="warning">本地有未发布变更</el-tag>
      <el-tag v-else-if="selectedTheme?.localSourceAvailable" size="small" type="success"
        >本地源码已同步</el-tag
      >
      <template v-if="!canDirectPublish">
        <el-tooltip
          :content="selectedTheme?.localSourceMessage || '从仓库资源目录创建新草稿，不覆盖已有版本'"
        >
          <el-button size="small" :disabled="!selectedTheme?.localSourceAvailable" @click="syncLocalSource">
            同步本地源码
          </el-button>
        </el-tooltip>
        <el-button size="small" type="primary" plain :disabled="!workspace" @click="applyTheme">
          应用到门户草稿
        </el-button>
      </template>
    </section>

    <section class="studio-grid">
      <aside class="file-panel">
        <div class="panel-heading">
          <span>主题文件</span>
          <el-button-group>
            <el-button size="small" @click="addFile">＋</el-button>
            <el-button size="small" @click="renameFile">改</el-button>
            <el-button size="small" @click="removeFile">删</el-button>
          </el-button-group>
        </div>
        <el-tree
          :data="fileTree"
          node-key="path"
          default-expand-all
          :expand-on-click-node="false"
          @node-click="(node: { path?: string }) => node.path && openFile(node.path)"
        />
        <div class="tag-guide">
          <strong>KMA 标签</strong>
          <code>&lt;kma-slot name="content" /&gt;</code>
          <code>&lt;kma-widget name="content-list" /&gt;</code>
          <code>&lt;kma-widget name="ai-chat" /&gt;</code>
          <code>&lt;kma-link to="library"&gt;</code>
        </div>
      </aside>

      <main class="editor-panel">
        <div class="panel-heading">
          <span>{{ activePath }}</span>
          <small>HTML · CSS · Liquid · 隔离 ES Module</small>
        </div>
        <div ref="editorHost" class="monaco-host" />
      </main>

      <aside class="preview-panel">
        <div class="panel-heading preview-heading">
          <span>即时全站预览</span>
          <el-button-group>
            <el-button size="small" @click="previewWidth = 1440">桌面</el-button>
            <el-button size="small" @click="previewWidth = 1024">平板</el-button>
            <el-button size="small" @click="previewWidth = 390">手机</el-button>
          </el-button-group>
        </div>
        <el-slider v-model="previewWidth" :min="320" :max="1920" :step="10" show-input />
        <div class="preview-stage">
          <iframe
            :srcdoc="previewSource"
            :style="{ width: `${previewWidth}px` }"
            title="未保存主题即时预览"
            sandbox="allow-scripts"
            referrerpolicy="no-referrer"
          />
        </div>
      </aside>
    </section>

    <footer class="studio-diagnostics">
      <strong>模板编译与安全扫描</strong>
      <span v-if="!issues.length" class="success">未发现安全问题</span>
      <span v-for="issue in issues" v-else :key="issue" class="issue">{{ issue }}</span>
      <span class="policy">CSP connect-src 'none' · SDK 能力二次授权 · 默认 HTML 转义</span>
    </footer>

    <el-dialog v-model="aiOpen" title="DeepSeek V4 Flash · 整站主题 AI" width="720px">
      <p class="ai-note">
        AI 可同时修改 layout、页面模板、partials、CSS 与隔离
        JS。提案只进入本地草稿，不会自动保存、送审或发布。
      </p>
      <el-input
        v-model="aiInstruction"
        type="textarea"
        :rows="5"
        maxlength="2000"
        show-word-limit
        placeholder="例如：把整站改造成权威党建知识门户，首页强化专题与最新资料，移动端使用紧凑导航。"
      />
      <section v-if="aiProposal" class="ai-proposal">
        <strong>{{ aiProposal.summary }}</strong>
        <span>模型：{{ aiProposal.model }}</span>
        <span>修改 {{ aiProposal.changedFiles.length }} 个文件</span>
        <code v-for="path in aiProposal.changedFiles" :key="path">{{ path }}</code>
        <el-alert
          v-for="warning in aiProposal.warnings"
          :key="warning"
          :title="warning"
          type="warning"
          :closable="false"
        />
      </section>
      <template #footer>
        <el-button @click="aiOpen = false">放弃</el-button>
        <el-button :loading="aiLoading" @click="generateAiTheme">
          {{ aiProposal ? '重新生成' : '生成提案' }}
        </el-button>
        <el-button type="primary" :disabled="!aiProposal" @click="applyAiProposal"> 应用到草稿 </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.theme-studio {
  position: fixed;
  z-index: 80;
  inset: 0;
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr) 44px;
  color: #d8e2ef;
  background: #0d1420;
}

.studio-toolbar,
.panel-heading,
.studio-diagnostics {
  display: flex;
  align-items: center;
  gap: 10px;
  border-color: #283448;
  background: #121c2a;
}

.studio-toolbar {
  min-height: 58px;
  flex-wrap: wrap;
  padding: 0 16px;
  border-bottom: 1px solid #283448;
}

.theme-switcher {
  display: flex;
  gap: 8px;
  align-items: center;
  min-height: 46px;
  padding: 6px 16px;
  border-bottom: 1px solid #283448;
  background: #101925;
}

.theme-switcher__label {
  color: #c7d4e4;
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
}

.theme-switcher__select {
  width: 224px;
}

.theme-switcher__meta {
  min-width: 0;
  overflow: hidden;
  color: #8290a6;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.theme-option {
  display: flex;
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.theme-option__copy {
  display: grid;
  flex: 1;
  min-width: 0;
}

.theme-option small {
  color: #8290a6;
}

.theme-swatch {
  width: 22px;
  height: 22px;
  flex: 0 0 auto;
  border: 1px solid rgb(255 255 255 / 0.28);
  border-radius: 7px;
}

.theme-swatch--heritage-red {
  background: linear-gradient(135deg, #6e121a, #d67b44);
}

.theme-swatch--governance-blue {
  background: linear-gradient(135deg, #123b66, #36a6c8);
}

.theme-swatch--ink-night {
  background: linear-gradient(135deg, #111817, #e6bf72);
}

@media (width <= 980px) {
  .theme-switcher {
    flex-wrap: wrap;
  }

  .theme-switcher__meta {
    flex: 1 1 260px;
  }
}

@media (width <= 640px) {
  .theme-switcher__select {
    width: calc(100% - 76px);
  }

  .theme-switcher__meta {
    order: 3;
    flex-basis: 100%;
  }
}

.studio-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-right: auto;
}

.studio-title strong {
  color: #fff;
  font-size: 17px;
}

.studio-title span,
.panel-heading small,
.policy {
  color: #8290a6;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}

.studio-grid {
  display: grid;
  grid-template-columns: 230px minmax(360px, 1fr) minmax(420px, 46vw);
  min-height: 0;
}

.file-panel,
.editor-panel,
.preview-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  border-right: 1px solid #283448;
  background: #111a27;
}

.panel-heading {
  min-height: 42px;
  padding: 0 12px;
  border-bottom: 1px solid #283448;
}

.panel-heading > span:first-child {
  margin-right: auto;
}

.file-panel :deep(.el-tree) {
  overflow: auto;
  flex: 1;
  padding: 8px;
  color: #b9c6d8;
  background: transparent;
}

.file-panel :deep(.el-tree-node__content:hover),
.file-panel :deep(.is-current > .el-tree-node__content) {
  background: #20314a;
}

.tag-guide {
  display: grid;
  gap: 7px;
  padding: 12px;
  border-top: 1px solid #283448;
}

.tag-guide code {
  overflow: hidden;
  color: #76d5c2;
  font-size: 11px;
  text-overflow: ellipsis;
}

.monaco-host {
  min-height: 0;
  flex: 1;
}

.preview-panel {
  overflow: hidden;
  padding-bottom: 8px;
  background: #1b2636;
}

.preview-panel > :deep(.el-slider) {
  margin: 4px 16px;
  width: auto;
}

.preview-stage {
  overflow: auto;
  min-height: 0;
  flex: 1;
  padding: 18px;
  text-align: center;
  background:
    linear-gradient(45deg, #202c3c 25%, transparent 25%) 0 0 / 16px 16px,
    linear-gradient(-45deg, #202c3c 25%, transparent 25%) 0 0 / 16px 16px,
    #182231;
}

.preview-stage iframe {
  display: inline-block;
  min-width: 320px;
  height: calc(100% - 4px);
  max-width: none;
  border: 0;
  border-radius: 5px;
  background: #fff;
  box-shadow: 0 16px 50px rgb(0 0 0 / 0.35);
}

.studio-diagnostics {
  overflow-x: auto;
  padding: 0 16px;
  border-top: 1px solid #283448;
  font-size: 12px;
  white-space: nowrap;
}

.studio-diagnostics .success {
  color: #6dd8a4;
}

.studio-diagnostics .issue {
  color: #ff9b8f;
}

.studio-diagnostics .policy {
  margin-left: auto;
}

.ai-note {
  margin: 0 0 16px;
  color: #526176;
  line-height: 1.7;
}

.ai-proposal {
  display: flex;
  flex-wrap: wrap;
  gap: 9px;
  margin-top: 16px;
  padding: 14px;
  border: 1px solid #d8e3ec;
  border-radius: 8px;
  background: #f7fafc;
}

.ai-proposal strong {
  width: 100%;
  color: #18354d;
}

.ai-proposal code {
  padding: 3px 7px;
  color: #0d6759;
  background: #e4f5f0;
  border-radius: 4px;
}

@media (width <= 1100px) {
  .studio-grid {
    grid-template-columns: 190px minmax(340px, 1fr);
  }

  .preview-panel {
    position: absolute;
    z-index: 2;
    inset: 132px 0 44px 55%;
  }
}
</style>
