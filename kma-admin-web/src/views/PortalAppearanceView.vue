<script setup lang="ts">
import '../styles/portal-tokens.css'
import '../styles/portal-pages.css'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import {
  cloneRuntimeConfig,
  cmsBlockTypes,
  defaultRuntimeConfig,
  parseRuntimeConfig,
  portalDensities,
  portalTemplates,
  serializeRuntimeConfig,
  type CmsBlockConfig,
  type CmsBlockType,
  type KmaRuntimeConfig,
} from '../app/runtimeConfig'
import { getPortalHome, type PortalHome } from '../api/party'
import PageHeader from '../components/PageHeader.vue'
import PageTemplateRenderer from '../cms/PageTemplateRenderer.vue'
import { frontendModules } from '../modules/registry'
import { useExperienceStore } from '../stores/experience'
import { loadPortalTemplateStyle } from '../templates/registry'

const experience = useExperienceStore()
const sourceConfig = cloneRuntimeConfig(experience.globalConfig)
const loadedDraft = experience.loadDraft()
const persistedConfig = ref<KmaRuntimeConfig | null>(loadedDraft ? cloneRuntimeConfig(loadedDraft) : null)
const draft = reactive<KmaRuntimeConfig>(loadedDraft || cloneRuntimeConfig(experience.activeConfig))
const initialConfig = cloneRuntimeConfig(draft)
const viewport = ref<1440 | 1024 | 390>(1440)
const draggedBlock = ref('')
const importInput = ref<HTMLInputElement>()
const query = ref('')

const homeQuery = useQuery({
  queryKey: ['portal-home', 'appearance-preview'],
  queryFn: getPortalHome,
  staleTime: 5 * 60_000,
  retry: false,
})

const fallbackHome: PortalHome = {
  config: {
    unitName: '党建知识库',
    helpText: '所有回答均应以已发布、有效且有权访问的材料为依据。',
    currentTopicCode: 'party-constitution',
  },
  categories: [
    { contentType: 'party_constitution', name: '党章党规', total: 7 },
    { contentType: 'policy', name: '政策文件', total: 12 },
    { contentType: 'learning_material', name: '学习材料', total: 18 },
    { contentType: 'grassroots_case', name: '基层案例', total: 9 },
    { contentType: 'organization_system', name: '组织工作制度', total: 6 },
  ],
  recent: [],
  topics: [
    {
      topicCode: 'party-constitution',
      name: '党章党规',
      description: '党章、准则、条例和党内法规专题。',
    },
  ],
  history: [],
  favorites: [],
}

const previewData = computed(() => homeQuery.data.value || fallbackHome)
const parsedDraft = computed(() => parseRuntimeConfig(draft, 'appearance-editor', sourceConfig))
const validationIssues = computed(() => parsedDraft.value.issues)
const blockList = computed(() => draft.experience.pages.home.blocks)
const previewPage = computed(() => parsedDraft.value.config.experience.pages.home)
const templates = [
  { id: 'knowledge-classic', name: '经典知识门户', description: '搜索优先、分类卡片和右侧专题。' },
  { id: 'cms-news', name: 'CMS 栏目门户', description: '频道化栏目、专题和密集文件列表。' },
  { id: 'reading-focus', name: '制度阅读模式', description: '突出搜索、现行文件和长文阅读。' },
] as const
const blockLabels: Record<CmsBlockType, string> = {
  'hero-search': '权威资料检索',
  'category-grid': '知识分类',
  'recent-documents': '最近更新',
  'current-topic': '当前专题',
  'reading-history': '最近阅读',
  favorites: '我的收藏',
  announcement: '门户公告',
  'quick-ask': '快速提问',
}
const blockVariants: Record<CmsBlockType, string[]> = {
  'hero-search': ['compact', 'wide'],
  'category-grid': ['cards', 'links'],
  'recent-documents': ['list', 'compact'],
  'current-topic': ['card', 'featured'],
  'reading-history': ['list', 'compact'],
  favorites: ['list', 'compact'],
  announcement: ['standard', 'accent'],
  'quick-ask': ['inline', 'card'],
}
const configurableModules = computed(() => {
  const seen = new Set<string>()
  return frontendModules.filter((module) => {
    if (module.core || seen.has(module.featureKey)) return false
    seen.add(module.featureKey)
    return true
  })
})

watch(
  draft,
  () => {
    draft.experience.pages.home.template = draft.experience.template
    const result = parseRuntimeConfig(draft, 'appearance-preview', sourceConfig)
    experience.setPreview(result.config)
    void loadPortalTemplateStyle(result.config.experience.template)
  },
  { deep: true, immediate: true },
)

onBeforeUnmount(() => {
  experience.setPreview(persistedConfig.value)
})

function setTemplate(template: (typeof portalTemplates)[number]) {
  draft.experience.template = template
  draft.experience.pages.home.template = template
}

function moduleEnabled(featureKey: string, defaultEnabled: boolean) {
  return draft.experience.modules[featureKey] ?? defaultEnabled
}

function setModule(featureKey: string, enabled: boolean) {
  draft.experience.modules[featureKey] = enabled
}

function moveBlock(index: number, direction: -1 | 1) {
  const next = index + direction
  if (next < 0 || next >= blockList.value.length) return
  const [item] = blockList.value.splice(index, 1)
  blockList.value.splice(next, 0, item)
}

function dropBlock(targetId: string) {
  const source = blockList.value.findIndex((item) => item.id === draggedBlock.value)
  const target = blockList.value.findIndex((item) => item.id === targetId)
  if (source < 0 || target < 0 || source === target) return
  const [item] = blockList.value.splice(source, 1)
  blockList.value.splice(target, 0, item)
  draggedBlock.value = ''
}

function addBlock(type: CmsBlockType) {
  const existingIds = new Set(blockList.value.map((item) => item.id))
  let sequence = 1
  let id: string = type
  while (existingIds.has(id)) id = `${type}-${++sequence}`
  blockList.value.push({
    id,
    type,
    enabled: true,
    variant: blockVariants[type][0],
  })
}

function removeBlock(id: string) {
  const index = blockList.value.findIndex((item) => item.id === id)
  if (index >= 0) blockList.value.splice(index, 1)
}

function saveLocalDraft() {
  const result = parsedDraft.value
  experience.saveDraft(result.config)
  persistedConfig.value = cloneRuntimeConfig(result.config)
  Object.assign(draft, cloneRuntimeConfig(result.config))
  ElMessage.success('外观草稿已保存在当前浏览器')
}

function undoChanges() {
  Object.assign(draft, cloneRuntimeConfig(initialConfig))
  ElMessage.success('已撤销本次修改')
}

function resetDefault() {
  Object.assign(draft, cloneRuntimeConfig(defaultRuntimeConfig))
  ElMessage.success('已恢复内置默认外观')
}

function clearLocalDraft() {
  experience.clearDraft()
  persistedConfig.value = null
  Object.assign(draft, cloneRuntimeConfig(sourceConfig))
  ElMessage.success('已清除本地草稿并恢复已部署配置')
}

function exportConfig() {
  const result = parsedDraft.value
  const content = serializeRuntimeConfig({
    ...result.config,
    revision: `site-${new Date().toISOString()}`,
  })
  const url = URL.createObjectURL(new Blob([content], { type: 'application/json;charset=utf-8' }))
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = 'kma-runtime.json'
  anchor.click()
  URL.revokeObjectURL(url)
  ElMessage.success('配置已导出；替换静态 JSON 后才会对其他用户生效')
}

async function importConfig(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  try {
    const result = parseRuntimeConfig(JSON.parse(await file.text()), file.name, sourceConfig)
    Object.assign(draft, cloneRuntimeConfig(result.config))
    if (result.issues.length) ElMessage.warning(`配置已导入，并修正 ${result.issues.length} 个问题`)
    else ElMessage.success('配置导入成功')
  } catch {
    ElMessage.error('配置文件不是有效 JSON')
  }
}

function updateBlockVariant(block: CmsBlockConfig, value: string) {
  block.variant = value
}
</script>

<template>
  <div class="appearance-page">
    <PageHeader
      eyebrow="PORTAL APPEARANCE"
      title="门户外观"
      description="配置只保存在当前浏览器；导出并部署 JSON 后才会影响其他用户，不修改后端。"
    >
      <template #actions>
        <el-button @click="undoChanges">撤销修改</el-button>
        <el-button @click="clearLocalDraft">清除草稿</el-button>
        <el-button type="primary" @click="saveLocalDraft">保存本地草稿</el-button>
        <el-button type="success" @click="exportConfig">导出发布配置</el-button>
      </template>
    </PageHeader>

    <el-alert
      v-if="validationIssues.length"
      type="warning"
      :closable="false"
      :title="`发现 ${validationIssues.length} 个配置问题，预览已使用安全回退值`"
    >
      <ul class="appearance-issues">
        <li v-for="issue in validationIssues" :key="issue">{{ issue }}</li>
      </ul>
    </el-alert>

    <div class="appearance-workspace">
      <section class="appearance-controls panel">
        <el-tabs>
          <el-tab-pane label="模板">
            <div class="appearance-template-grid">
              <button
                v-for="item in templates"
                :key="item.id"
                class="appearance-template-card"
                :class="{ active: draft.experience.template === item.id }"
                @click="setTemplate(item.id)"
              >
                <span :data-template-preview="item.id"></span>
                <strong>{{ item.name }}</strong>
                <small>{{ item.description }}</small>
              </button>
            </div>
          </el-tab-pane>

          <el-tab-pane label="模块">
            <div class="appearance-module-list">
              <label v-for="module in configurableModules" :key="module.featureKey">
                <span>
                  <strong>{{ module.title }}</strong>
                  <small>{{ module.featureKey }}</small>
                </span>
                <el-switch
                  :model-value="moduleEnabled(module.featureKey, module.defaultEnabled)"
                  @update:model-value="setModule(module.featureKey, Boolean($event))"
                />
              </label>
            </div>
          </el-tab-pane>

          <el-tab-pane label="首页区块">
            <div class="appearance-block-actions">
              <el-dropdown @command="addBlock">
                <el-button>添加区块</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-for="type in cmsBlockTypes" :key="type" :command="type">
                      {{ blockLabels[type] }}
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
            <div class="appearance-block-list">
              <article
                v-for="(block, index) in blockList"
                :key="block.id"
                draggable="true"
                @dragstart="draggedBlock = block.id"
                @dragover.prevent
                @drop="dropBlock(block.id)"
              >
                <span class="appearance-drag" aria-hidden="true">⠿</span>
                <div>
                  <strong>{{ blockLabels[block.type] }}</strong>
                  <small>{{ block.id }}</small>
                </div>
                <el-select
                  :model-value="block.variant"
                  aria-label="区块样式"
                  @update:model-value="updateBlockVariant(block, String($event))"
                >
                  <el-option
                    v-for="variant in blockVariants[block.type]"
                    :key="variant"
                    :label="variant"
                    :value="variant"
                  />
                </el-select>
                <el-switch v-model="block.enabled" />
                <div class="appearance-order-buttons">
                  <button :disabled="index === 0" aria-label="上移区块" @click="moveBlock(index, -1)">
                    ↑
                  </button>
                  <button
                    :disabled="index === blockList.length - 1"
                    aria-label="下移区块"
                    @click="moveBlock(index, 1)"
                  >
                    ↓
                  </button>
                  <button aria-label="移除区块" @click="removeBlock(block.id)">×</button>
                </div>
              </article>
            </div>
          </el-tab-pane>

          <el-tab-pane label="主题">
            <el-form label-position="top">
              <div class="appearance-form-grid">
                <el-form-item label="主题标识">
                  <el-input v-model="draft.experience.theme" />
                </el-form-item>
                <el-form-item label="界面密度">
                  <el-select v-model="draft.experience.density">
                    <el-option v-for="item in portalDensities" :key="item" :label="item" :value="item" />
                  </el-select>
                </el-form-item>
                <el-form-item label="品牌主色">
                  <el-input v-model="draft.experience.tokens.colorPrimary" />
                </el-form-item>
                <el-form-item label="品牌深色">
                  <el-input v-model="draft.experience.tokens.colorPrimaryStrong" />
                </el-form-item>
                <el-form-item label="品牌浅色">
                  <el-input v-model="draft.experience.tokens.colorPrimarySoft" />
                </el-form-item>
                <el-form-item label="页面背景">
                  <el-input v-model="draft.experience.tokens.colorBackground" />
                </el-form-item>
                <el-form-item label="卡片背景">
                  <el-input v-model="draft.experience.tokens.colorSurface" />
                </el-form-item>
                <el-form-item label="正文颜色">
                  <el-input v-model="draft.experience.tokens.colorText" />
                </el-form-item>
                <el-form-item label="基础字号">
                  <el-input v-model="draft.experience.tokens.fontSizeBase" />
                </el-form-item>
                <el-form-item label="正文行高">
                  <el-input v-model="draft.experience.tokens.lineHeightBody" />
                </el-form-item>
                <el-form-item label="卡片圆角">
                  <el-input v-model="draft.experience.tokens.radiusCard" />
                </el-form-item>
                <el-form-item label="控件圆角">
                  <el-input v-model="draft.experience.tokens.radiusControl" />
                </el-form-item>
              </div>
              <el-form-item label="字体族">
                <el-input v-model="draft.experience.tokens.fontBody" />
              </el-form-item>
              <el-form-item label="卡片阴影">
                <el-input v-model="draft.experience.tokens.shadowCard" />
              </el-form-item>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="品牌资源">
            <el-alert
              type="info"
              :closable="false"
              title="资源必须部署在当前站点的 /themes/ 目录，不能填写远程 URL。"
            />
            <el-form label-position="top" class="spaced-top">
              <el-form-item label="Logo">
                <el-input v-model="draft.experience.assets.logo" placeholder="/themes/emerald/logo.svg" />
              </el-form-item>
              <el-form-item label="Favicon">
                <el-input
                  v-model="draft.experience.assets.favicon"
                  placeholder="/themes/emerald/favicon.svg"
                />
              </el-form-item>
              <el-form-item label="登录插图">
                <el-input
                  v-model="draft.experience.assets.loginIllustration"
                  placeholder="/themes/emerald/login.webp"
                />
              </el-form-item>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="导入与恢复">
            <div class="appearance-import-panel">
              <input
                ref="importInput"
                class="appearance-file-input"
                type="file"
                accept=".json,application/json"
                @change="importConfig"
              />
              <el-button @click="importInput?.click()">导入 JSON 配置</el-button>
              <el-button @click="resetDefault">恢复内置默认外观</el-button>
              <p>当前配置版本：{{ draft.schemaVersion }} · {{ draft.revision }}</p>
            </div>
          </el-tab-pane>
        </el-tabs>
      </section>

      <section class="appearance-preview-panel">
        <header>
          <div>
            <strong>实时预览</strong>
            <span>{{ draft.experience.template }} · {{ draft.experience.theme }}</span>
          </div>
          <el-radio-group v-model="viewport" size="small">
            <el-radio-button :value="1440">桌面</el-radio-button>
            <el-radio-button :value="1024">平板</el-radio-button>
            <el-radio-button :value="390">手机</el-radio-button>
          </el-radio-group>
        </header>
        <div class="appearance-preview-scroll">
          <div class="appearance-preview-frame" :style="{ width: `${viewport}px` }">
            <div class="portal-shell appearance-preview-shell">
              <header class="appearance-preview-header">
                <span class="brand-mark">K</span>
                <strong>{{ previewData.config.unitName }}</strong>
                <small>权威资料 · 有据可查</small>
              </header>
              <main class="portal-main">
                <PageTemplateRenderer v-model:query="query" :page="previewPage" :data="previewData" />
              </main>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.appearance-page {
  display: grid;
  gap: 20px;
}

.appearance-issues {
  margin: 8px 0 0;
  padding-left: 18px;
}

.appearance-workspace {
  display: grid;
  grid-template-columns: minmax(420px, 0.75fr) minmax(0, 1.25fr);
  gap: 20px;
  align-items: start;
}

.appearance-controls {
  min-width: 0;
}

.appearance-template-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.appearance-template-card {
  display: grid;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: var(--kma-radius-card);
  background: var(--surface);
  color: var(--ink);
  text-align: left;
  cursor: pointer;
}

.appearance-template-card.active {
  border-color: var(--accent);
  box-shadow: 0 0 0 2px var(--kma-color-primary-soft);
}

.appearance-template-card > span {
  height: 72px;
  border-radius: 6px;
  background:
    linear-gradient(var(--kma-color-primary) 0 18px, transparent 18px),
    linear-gradient(90deg, var(--kma-color-primary-soft) 32%, transparent 32%);
  box-shadow: 0 0 0 1px var(--line) inset;
}

.appearance-template-card [data-template-preview='cms-news'] {
  background:
    linear-gradient(var(--kma-color-primary-strong) 0 14px, transparent 14px),
    repeating-linear-gradient(0deg, var(--line) 0 1px, transparent 1px 13px);
}

.appearance-template-card [data-template-preview='reading-focus'] {
  background:
    linear-gradient(var(--kma-color-primary) 0 16px, transparent 16px),
    linear-gradient(90deg, transparent 20%, var(--line) 20% 80%, transparent 80%);
}

.appearance-template-card small,
.appearance-module-list small,
.appearance-block-list small {
  display: block;
  color: var(--muted);
}

.appearance-module-list {
  display: grid;
  gap: 8px;
  max-height: 520px;
  overflow: auto;
}

.appearance-module-list label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: var(--kma-radius-control);
}

.appearance-block-actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.appearance-block-list {
  display: grid;
  gap: 8px;
}

.appearance-block-list article {
  display: grid;
  grid-template-columns: auto minmax(120px, 1fr) 118px auto auto;
  gap: 10px;
  align-items: center;
  padding: 10px;
  border: 1px solid var(--line);
  border-radius: var(--kma-radius-control);
  background: var(--surface);
}

.appearance-drag {
  color: var(--muted);
  cursor: grab;
}

.appearance-order-buttons {
  display: flex;
  gap: 4px;
}

.appearance-order-buttons button {
  width: 28px;
  height: 28px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--surface);
  color: var(--ink);
  cursor: pointer;
}

.appearance-order-buttons button:disabled {
  cursor: not-allowed;
  opacity: 0.4;
}

.appearance-form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 12px;
}

.appearance-import-panel {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.appearance-import-panel p {
  width: 100%;
  color: var(--muted);
}

.appearance-file-input {
  display: none;
}

.appearance-preview-panel {
  position: sticky;
  top: 132px;
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: var(--kma-radius-card);
  background: oklch(94% 0.01 195deg);
}

.appearance-preview-panel > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--line);
  background: var(--surface);
}

.appearance-preview-panel > header span {
  display: block;
  margin-top: 2px;
  color: var(--muted);
  font-size: 11px;
}

.appearance-preview-scroll {
  max-height: 720px;
  overflow: auto;
  padding: 16px;
}

.appearance-preview-frame {
  max-width: 100%;
  min-height: 620px;
  margin: auto;
  overflow: hidden;
  border-radius: 8px;
  box-shadow: 0 12px 36px rgb(18 48 50 / 0.15);
  pointer-events: none;
}

.appearance-preview-shell {
  min-height: 620px;
}

.appearance-preview-header {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 56px;
  padding: 0 20px;
  border-bottom: 1px solid var(--line);
  background: var(--portal-paper);
}

.appearance-preview-header small {
  margin-left: auto;
  color: var(--muted);
}

.appearance-preview-shell :deep(.portal-main) {
  padding: 20px;
}

.appearance-preview-shell :deep(.portal-section-title h2) {
  font-size: 18px;
}

.appearance-preview-shell :deep(.portal-category-card) {
  min-height: 112px;
}

@media (width <= 1180px) {
  .appearance-workspace {
    grid-template-columns: 1fr;
  }

  .appearance-preview-panel {
    position: static;
  }
}

@media (width <= 720px) {
  .appearance-template-grid,
  .appearance-form-grid {
    grid-template-columns: 1fr;
  }

  .appearance-block-list article {
    grid-template-columns: auto minmax(0, 1fr) auto;
  }

  .appearance-block-list article .el-select {
    grid-column: 2 / -1;
  }
}
</style>
