<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { editor as MonacoEditor } from 'monaco-editor/editor/editor.api.js'
import {
  createPortalCodePackage,
  importPortalCodeZip,
  listPortalCodePackages,
  portalCodeAction,
  savePortalCodeFiles,
  validatePortalCodeSource,
  type PortalCodePackage,
  type PortalCodeVersion,
} from '../../api/portalSites'
import PortalExtensionFrame from '../../cms/PortalExtensionFrame.vue'
import type { PortalBootstrap, PortalInlineCode } from '../../cms/siteConfig'

const props = defineProps<{
  modelValue: boolean
  inlineCode?: PortalInlineCode
  inlineNodeId?: string
}>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  changed: []
  'save-inline': [value: PortalInlineCode]
}>()

const packages = ref<PortalCodePackage[]>([])
const selectedPackageId = ref<number>()
const currentVersion = ref<PortalCodeVersion>()
const versionLabel = ref(`1.0.${Date.now().toString().slice(-3)}`)
const activeFile = ref('index.html')
const previewWidth = ref(1024)
const files = reactive<Record<string, string>>({
  'index.html': `<main class="kma-widget">
  <h2>站点自定义组件</h2>
  <p id="context">正在读取受控页面上下文…</p>
</main>
<script src="./main.js"></scr${'ipt'}>`,
  'style.css': `.kma-widget {
  padding: 20px;
  color: #123b34;
  background: #f0faf6;
  border: 1px solid #b9dfd3;
  border-radius: 12px;
}`,
  'main.js': `let port;
window.addEventListener('message', (event) => {
  if (event.data?.type !== 'kma-sdk-init' || !event.ports[0]) return;
  port = event.ports[0];
  const id = crypto.randomUUID();
  port.onmessage = (response) => {
    if (response.data?.id === id && response.data.ok) {
      document.querySelector('#context').textContent =
        response.data.value.site.name + ' · ' + response.data.value.page;
    }
  };
  port.postMessage({ type: 'page-context', id });
});`,
})
const editorHost = ref<HTMLElement>()
const syntaxIssues = ref<string[]>([])
const loading = ref(false)
const creating = ref(false)
const newPackage = reactive({ packageKey: '', displayName: '', description: '' })
let editorInstance: MonacoEditor.IStandaloneCodeEditor | undefined
let monaco: typeof import('monaco-editor/editor/editor.api.js') | undefined
let updatingModel = false

const selectedPackage = computed(() =>
  packages.value.find((item) => item.packageId === selectedPackageId.value),
)
const canPublish = computed(() => currentVersion.value?.scanStatus === 'passed')
const editingInline = computed(() => Boolean(props.inlineCode))
const selectedCapabilities = ref<NonNullable<PortalInlineCode['manifest']>['capabilities']>(['page-context'])
const previewCode = computed<PortalInlineCode>(() => ({
  files: { ...files },
  manifest: { capabilities: selectedCapabilities.value },
}))
const previewBootstrap = computed(() => ({
  site: { siteKey: 'designer-preview', name: '设计器即时预览' },
  page: { slug: 'preview' },
  theme: { pack: 'party-authority', mode: 'light' },
  portalData: {},
} as unknown as PortalBootstrap))

function language(file: string) {
  if (file.endsWith('.html')) return 'html'
  if (file.endsWith('.css')) return 'css'
  if (file.endsWith('.js')) return 'javascript'
  return 'plaintext'
}

async function loadPackages() {
  packages.value = await listPortalCodePackages()
  selectedPackageId.value ||= packages.value[0]?.packageId
}

async function mountEditor() {
  if (!editorHost.value || editorInstance) return
  monaco = await import('monaco-editor/editor/editor.api.js')
  const instance = monaco.editor.create(editorHost.value, {
    value: files[activeFile.value],
    language: language(activeFile.value),
    theme: 'vs',
    automaticLayout: true,
    minimap: { enabled: false },
    fontSize: 13,
    lineHeight: 21,
    tabSize: 2,
    scrollBeyondLastLine: false,
  })
  editorInstance = instance
  instance.onDidChangeModelContent(() => {
    if (!updatingModel) files[activeFile.value] = instance.getValue()
  })
}

function switchFile(file: string) {
  activeFile.value = file
  if (!editorInstance || !monaco) return
  updatingModel = true
  const oldModel = editorInstance.getModel()
  const nextModel = monaco.editor.createModel(files[file], language(file))
  editorInstance.setModel(nextModel)
  oldModel?.dispose()
  updatingModel = false
}

async function validateSyntax() {
  syntaxIssues.value = []
  try {
    const esprima = await import('esprima')
    esprima.parseScript(files['main.js'] || '', { loc: true, tolerant: false })
  } catch (error) {
    const location = error as { lineNumber?: number; description?: string; message?: string }
    syntaxIssues.value.push(
      `main.js:${location.lineNumber || '?'} ${location.description || location.message || '语法错误'}`,
    )
  }
  try {
    const result = await validatePortalCodeSource({
      files: { ...files },
      manifest: { capabilities: selectedCapabilities.value },
    })
    syntaxIssues.value.push(...result.issues)
  } catch {
    syntaxIssues.value.push('无法完成服务端安全检查，请确认已登录且后端可用')
  }
  if (!syntaxIssues.value.length) ElMessage.success('客户端语法与能力检查通过')
  return syntaxIssues.value.length === 0
}

async function createPackage() {
  if (!newPackage.packageKey || !newPackage.displayName) {
    ElMessage.warning('请填写组件编码和名称')
    return
  }
  creating.value = true
  try {
    const created = await createPortalCodePackage(newPackage)
    await loadPackages()
    selectedPackageId.value = created.packageId
    Object.assign(newPackage, { packageKey: '', displayName: '', description: '' })
    ElMessage.success('代码组件目录已创建')
  } finally {
    creating.value = false
  }
}

async function saveAndScan() {
  if (editingInline.value) {
    if (!(await validateSyntax())) return
    emit('save-inline', previewCode.value)
    ElMessage.success('内联代码已应用到当前草稿；保存草稿后可进行真实门户预览')
    return
  }
  if (!selectedPackageId.value || !(await validateSyntax())) return
  loading.value = true
  try {
    currentVersion.value = await savePortalCodeFiles(selectedPackageId.value, {
      version: versionLabel.value,
      manifest: {
        capabilities: selectedCapabilities.value,
        settingsSchema: { type: 'object', additionalProperties: false, properties: {} },
      },
      files,
    })
    currentVersion.value = (await portalCodeAction(
      selectedPackageId.value,
      'scan',
      currentVersion.value.versionId,
    )) as PortalCodeVersion
    if (currentVersion.value.scanStatus === 'passed') ElMessage.success('服务端安全扫描通过')
    else ElMessage.warning('服务端扫描未通过')
  } finally {
    loading.value = false
  }
}

async function publish() {
  if (!selectedPackageId.value || !currentVersion.value) return
  await portalCodeAction(selectedPackageId.value, 'publish', currentVersion.value.versionId)
  ElMessage.success('不可变代码版本已发布')
  await loadPackages()
  emit('changed')
}

async function uploadZip(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || !selectedPackageId.value) return
  loading.value = true
  try {
    currentVersion.value = await importPortalCodeZip(
      selectedPackageId.value,
      versionLabel.value,
      { capabilities: selectedCapabilities.value },
      file,
    )
    currentVersion.value = (await portalCodeAction(
      selectedPackageId.value,
      'scan',
      currentVersion.value.versionId,
    )) as PortalCodeVersion
    ElMessage.success('ZIP 已导入并完成安全扫描')
  } finally {
    input.value = ''
    loading.value = false
  }
}

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return
    if (props.inlineCode) {
      Object.keys(files).forEach((key) => delete files[key])
      Object.assign(files, props.inlineCode.files)
      selectedCapabilities.value = props.inlineCode.manifest?.capabilities || ['page-context']
      activeFile.value = Object.keys(files)[0] || 'index.html'
    }
    await loadPackages()
    await nextTick()
    await mountEditor()
  },
)

onBeforeUnmount(() => editorInstance?.dispose())
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    :title="editingInline ? '页面内联代码区块' : '站点沙箱代码组件'"
    size="min(1440px, 96vw)"
    destroy-on-close
    @close="emit('update:modelValue', false)"
  >
    <div class="code-editor" v-loading="loading">
      <aside v-if="!editingInline" class="code-editor__packages">
        <h3>组件目录</h3>
        <button
          v-for="item in packages"
          :key="item.packageId"
          :class="{ 'is-active': selectedPackageId === item.packageId }"
          @click="selectedPackageId = item.packageId"
        >
          <strong>{{ item.displayName }}</strong>
          <span>{{ item.packageKey }} · {{ item.currentVersion || '未发布' }}</span>
        </button>
        <el-divider />
        <el-input v-model="newPackage.packageKey" placeholder="组件编码，如 policy-card" />
        <el-input v-model="newPackage.displayName" placeholder="组件名称" />
        <el-input v-model="newPackage.description" type="textarea" :rows="2" placeholder="用途说明" />
        <el-button :loading="creating" type="primary" @click="createPackage">创建组件</el-button>
      </aside>

      <section class="code-editor__workspace">
        <header>
          <div class="code-editor__tabs">
            <button
              v-for="(_, file) in files"
              :key="file"
              :class="{ 'is-active': activeFile === file }"
              @click="switchFile(file)"
            >
              {{ file }}
            </button>
          </div>
          <div class="code-editor__actions">
            <el-input v-if="!editingInline" v-model="versionLabel" aria-label="组件版本" />
            <label v-if="!editingInline" class="code-editor__zip">
              ZIP 导入
              <input type="file" accept=".zip,application/zip" @change="uploadZip" />
            </label>
            <el-button @click="validateSyntax">语法检查</el-button>
            <el-button :disabled="editingInline ? false : !selectedPackage" type="primary" @click="saveAndScan">
              {{ editingInline ? '应用到草稿' : '保存并扫描' }}
            </el-button>
            <el-button v-if="!editingInline" :disabled="!canPublish" type="success" @click="publish">发布版本</el-button>
          </div>
          <div class="code-editor__capabilities">
            <span>Portal SDK</span>
            <el-checkbox-group v-model="selectedCapabilities">
              <el-checkbox label="page-context" disabled>页面上下文</el-checkbox>
              <el-checkbox label="contents">资料</el-checkbox>
              <el-checkbox label="search">检索</el-checkbox>
              <el-checkbox label="ask">问答</el-checkbox>
              <el-checkbox label="analytics">埋点</el-checkbox>
            </el-checkbox-group>
          </div>
        </header>
        <div class="code-editor__split">
          <div ref="editorHost" class="code-editor__monaco" />
          <section class="code-editor__preview">
            <header>
              <strong>即时预览</strong>
              <el-segmented v-model="previewWidth" :options="[
                { label: '桌面', value: 1440 },
                { label: '平板', value: 1024 },
                { label: '手机', value: 390 },
              ]" />
            </header>
            <div class="code-editor__preview-stage">
              <PortalExtensionFrame
                :inline="previewCode"
                :node-id="inlineNodeId || 'package-preview'"
                :bootstrap="previewBootstrap"
                :style="{ width: `${previewWidth}px` }"
              />
            </div>
          </section>
        </div>
        <footer>
          <el-alert
            v-if="syntaxIssues.length"
            :title="syntaxIssues.join('；')"
            type="error"
            :closable="false"
          />
          <span v-else> 未保存代码正在隔离 iframe 中预览；网络、Cookie、Storage、父页面 DOM 与顶层跳转均被禁止。 </span>
        </footer>
      </section>
    </div>
  </el-drawer>
</template>

<style scoped>
.code-editor {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 12px;
  height: calc(100vh - 110px);
}

.code-editor:has(.code-editor__workspace:first-child) {
  grid-template-columns: minmax(0, 1fr);
}

.code-editor__packages {
  display: grid;
  align-content: start;
  gap: 8px;
  padding-right: 12px;
  overflow: auto;
  border-right: 1px solid #dce7e3;
}

.code-editor__packages h3 {
  margin: 0 0 4px;
  font-size: 14px;
}

.code-editor__packages button {
  display: grid;
  gap: 3px;
  padding: 9px;
  color: #173b34;
  text-align: left;
  background: #f8faf9;
  border: 1px solid #dce7e3;
  border-radius: 8px;
}

.code-editor__packages button.is-active {
  background: #edf9f5;
  border-color: #35a789;
}

.code-editor__packages span {
  color: #758580;
  font-size: 11px;
}

.code-editor__workspace {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  min-width: 0;
  min-height: 0;
  border: 1px solid #dce7e3;
  border-radius: 8px;
}

.code-editor__split {
  display: grid;
  grid-template-columns: minmax(320px, 1fr) minmax(320px, 0.9fr);
  min-height: 0;
}

.code-editor__workspace > header {
  display: grid;
  gap: 8px;
  padding: 8px;
  border-bottom: 1px solid #dce7e3;
}

.code-editor__tabs,
.code-editor__actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.code-editor__tabs button {
  padding: 5px 9px;
  color: #56706a;
  background: transparent;
  border: 0;
  border-radius: 6px;
}

.code-editor__tabs button.is-active {
  color: #006d59;
  background: #eaf7f3;
}

.code-editor__actions :deep(.el-input) {
  width: 100px;
}

.code-editor__zip {
  padding: 7px 10px;
  color: #31554d;
  font-size: 12px;
  border: 1px solid #cddbd7;
  border-radius: 6px;
  cursor: pointer;
}

.code-editor__zip input {
  display: none;
}

.code-editor__monaco {
  min-height: 0;
  border-right: 1px solid #dce7e3;
}

.code-editor__preview {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  min-width: 0;
  background: #f3f7f5;
}

.code-editor__preview > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px;
  border-bottom: 1px solid #dce7e3;
}

.code-editor__preview-stage {
  display: grid;
  place-items: start center;
  min-height: 0;
  padding: 16px;
  overflow: auto;
}

.code-editor__preview-stage :deep(.portal-extension-frame) {
  max-width: 100%;
  background: white;
  box-shadow: 0 3px 14px rgb(18 58 50 / 0.12);
}

.code-editor__capabilities {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #56706a;
  font-size: 12px;
}

@media (width <= 980px) {
  .code-editor__split {
    grid-template-columns: 1fr;
    grid-template-rows: minmax(280px, 1fr) minmax(280px, 1fr);
  }

  .code-editor__monaco {
    border-right: 0;
    border-bottom: 1px solid #dce7e3;
  }
}

.code-editor__workspace > footer {
  min-height: 38px;
  padding: 8px;
  color: #6d807b;
  font-size: 11px;
  border-top: 1px solid #dce7e3;
}
</style>
