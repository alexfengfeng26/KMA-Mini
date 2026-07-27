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
  type PortalCodePackage,
  type PortalCodeVersion,
} from '../../api/portalSites'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  changed: []
}>()

const packages = ref<PortalCodePackage[]>([])
const selectedPackageId = ref<number>()
const currentVersion = ref<PortalCodeVersion>()
const versionLabel = ref(`1.0.${Date.now().toString().slice(-3)}`)
const activeFile = ref('index.html')
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

function language(_file: string) {
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
    esprima.parseScript(files['main.js'], { loc: true, tolerant: false })
  } catch (error) {
    const location = error as { lineNumber?: number; description?: string; message?: string }
    syntaxIssues.value.push(
      `main.js:${location.lineNumber || '?'} ${location.description || location.message || '语法错误'}`,
    )
  }
  const forbidden = [
    'fetch(',
    'XMLHttpRequest',
    'WebSocket',
    'localStorage',
    'sessionStorage',
    'document.cookie',
    'window.parent',
  ]
  forbidden
    .filter((token) => files['main.js'].includes(token))
    .forEach((token) => syntaxIssues.value.push(`main.js 包含禁止能力：${token}`))
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
  if (!selectedPackageId.value || !(await validateSyntax())) return
  loading.value = true
  try {
    currentVersion.value = await savePortalCodeFiles(selectedPackageId.value, {
      version: versionLabel.value,
      manifest: {
        capabilities: ['page-context'],
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
      { capabilities: ['page-context'] },
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
    title="站点沙箱代码组件"
    size="920px"
    destroy-on-close
    @close="emit('update:modelValue', false)"
  >
    <div class="code-editor" v-loading="loading">
      <aside class="code-editor__packages">
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
            <el-input v-model="versionLabel" aria-label="组件版本" />
            <label class="code-editor__zip">
              ZIP 导入
              <input type="file" accept=".zip,application/zip" @change="uploadZip" />
            </label>
            <el-button @click="validateSyntax">语法检查</el-button>
            <el-button :disabled="!selectedPackage" type="primary" @click="saveAndScan">
              保存并扫描
            </el-button>
            <el-button :disabled="!canPublish" type="success" @click="publish">发布版本</el-button>
          </div>
        </header>
        <div ref="editorHost" class="code-editor__monaco" />
        <footer>
          <el-alert
            v-if="syntaxIssues.length"
            :title="syntaxIssues.join('；')"
            type="error"
            :closable="false"
          />
          <span v-else> iframe 不接收令牌；网络、Cookie、Storage、父页面 DOM 与顶层跳转均被禁止。 </span>
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
}

.code-editor__workspace > footer {
  min-height: 38px;
  padding: 8px;
  color: #6d807b;
  font-size: 11px;
  border-top: 1px solid #dce7e3;
}
</style>
