<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createPortalDesignProposal,
  getPortalDesignCapability,
  type PortalDesignCapability,
  type PortalDesignProposal,
} from '../../api/portalSites'
import type { LayoutNode, PortalSiteConfigV3 } from '../../cms/siteConfig'
import { walkNodes } from '../../cms/v3/contract'

const model = defineModel<boolean>({ default: false })
const props = defineProps<{
  siteKey: string
  versionId?: number
  lockVersion?: number
  config?: PortalSiteConfigV3
  pageSlug: string
  selectedNode?: LayoutNode
  canEdit: boolean
}>()
const emit = defineEmits<{
  apply: [proposal: PortalDesignProposal]
}>()

const capability = ref<PortalDesignCapability>()
const scope = ref<'page' | 'node'>('page')
const instruction = ref('')
const loading = ref(false)
const proposal = ref<PortalDesignProposal>()
const advancedOpen = ref(false)
const presets = [
  '优化信息层级，让主要入口更突出',
  '减少视觉拥挤并统一卡片间距',
  '增强移动端阅读和操作体验',
  '调整为更适合知识门户的专业布局',
]

const nodeScopeAvailable = computed(() => Boolean(props.selectedNode && !props.selectedNode.locked))
const targetBefore = computed(() => {
  if (!props.config) return undefined
  if (scope.value === 'node') return props.selectedNode
  return props.config.pages[props.pageSlug]
})
const changes = computed(() => summarizeChanges(targetBefore.value, proposal.value?.target))
const capabilityText = computed(() => {
  if (!capability.value) return '正在检查模型配置'
  if (capability.value.available) return `${capability.value.model} 已就绪`
  if (capability.value.reason === 'DEEPSEEK_API_KEY_MISSING') return '尚未配置 KMA_DEEPSEEK_API_KEY'
  if (capability.value.reason === 'AI_DESIGN_DISABLED') return 'AI 设计已被部署配置关闭'
  return capability.value.reason || 'AI 设计当前不可用'
})

watch(model, async (open) => {
  if (!open || !props.siteKey) return
  scope.value = nodeScopeAvailable.value ? 'node' : 'page'
  proposal.value = undefined
  try {
    capability.value = await getPortalDesignCapability(props.siteKey)
  } catch (error) {
    capability.value = {
      available: false,
      provider: 'deepseek',
      model: 'deepseek-v4-flash',
      reason: error instanceof Error ? error.message : '模型状态检查失败',
    }
  }
})

async function generate() {
  if (!props.config || props.versionId == null || props.lockVersion == null || !instruction.value.trim())
    return
  loading.value = true
  proposal.value = undefined
  try {
    proposal.value = await createPortalDesignProposal(props.siteKey, {
      versionId: props.versionId,
      expectedLockVersion: props.lockVersion,
      config: props.config,
      scope: scope.value,
      pageSlug: props.pageSlug,
      nodeId: scope.value === 'node' ? props.selectedNode?.id : undefined,
      instruction: instruction.value.trim(),
    })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'AI 设计提案生成失败')
  } finally {
    loading.value = false
  }
}

function apply() {
  if (!proposal.value) return
  emit('apply', proposal.value)
  proposal.value = undefined
  model.value = false
  ElMessage.success('AI 提案已应用到当前草稿，可通过撤销恢复')
}

function summarizeChanges(before: unknown, after: unknown) {
  const beforeIds = collectNodeIds(before)
  const afterIds = collectNodeIds(after)
  return {
    added: [...afterIds].filter((id) => !beforeIds.has(id)).length,
    removed: [...beforeIds].filter((id) => !afterIds.has(id)).length,
    retained: [...afterIds].filter((id) => beforeIds.has(id)).length,
  }
}

function collectNodeIds(value: unknown) {
  const ids = new Set<string>()
  if (!value || typeof value !== 'object') return ids
  const candidate = value as { root?: LayoutNode; id?: string; type?: string }
  const root = candidate.root || (candidate.id && candidate.type ? (candidate as LayoutNode) : undefined)
  if (root) walkNodes(root, (node) => ids.add(node.id))
  return ids
}
</script>

<template>
  <el-drawer v-model="model" size="460px" class="portal-ai-drawer" append-to-body>
    <template #header>
      <div class="portal-ai-drawer__title">
        <span class="portal-ai-drawer__mark">AI</span>
        <div>
          <strong>DeepSeek 智能设计</strong>
          <small>{{ capabilityText }}</small>
        </div>
      </div>
    </template>

    <div class="portal-ai-drawer__body">
      <el-alert
        v-if="capability && !capability.available"
        :title="capabilityText"
        description="请在 Mini 后端进程中配置密钥后重启服务。密钥不会发送到浏览器。"
        type="warning"
        :closable="false"
      />

      <section class="portal-ai-section">
        <label>设计范围</label>
        <el-segmented
          v-model="scope"
          :options="[
            { label: '当前页面', value: 'page' },
            { label: '选中节点', value: 'node', disabled: !nodeScopeAvailable },
          ]"
        />
        <small v-if="scope === 'node'">仅优化 {{ selectedNode?.name || selectedNode?.id }}</small>
        <small v-else>只修改当前页面，不触碰导航、主题和其他页面</small>
      </section>

      <section class="portal-ai-section">
        <label for="portal-ai-instruction">描述你想要的效果</label>
        <el-input
          id="portal-ai-instruction"
          v-model="instruction"
          type="textarea"
          :rows="5"
          maxlength="2000"
          show-word-limit
          placeholder="例如：把首页调整成清晰的知识门户，首屏突出搜索，并将最近更新改为双栏卡片。"
        />
        <div class="portal-ai-presets">
          <button v-for="preset in presets" :key="preset" type="button" @click="instruction = preset">
            {{ preset }}
          </button>
        </div>
      </section>

      <el-button
        type="primary"
        class="portal-ai-generate"
        :loading="loading"
        :disabled="!canEdit || !capability?.available || !instruction.trim()"
        @click="generate"
      >
        {{ proposal ? '重新生成提案' : '生成设计提案' }}
      </el-button>

      <section v-if="proposal" class="portal-ai-proposal">
        <header>
          <div>
            <span>提案预览</span>
            <strong>{{ proposal.summary }}</strong>
          </div>
          <small>{{ proposal.model }}</small>
        </header>
        <div class="portal-ai-change-grid">
          <div>
            <strong>+{{ changes.added }}</strong
            ><span>新增节点</span>
          </div>
          <div>
            <strong>-{{ changes.removed }}</strong
            ><span>移除节点</span>
          </div>
          <div>
            <strong>{{ changes.retained }}</strong
            ><span>保留节点</span>
          </div>
        </div>
        <el-alert
          v-for="warning in proposal.warnings"
          :key="warning"
          :title="warning"
          type="warning"
          :closable="false"
        />
        <button class="portal-ai-json-toggle" type="button" @click="advancedOpen = !advancedOpen">
          {{ advancedOpen ? '收起结构差异' : '查看候选结构 JSON' }}
        </button>
        <pre v-if="advancedOpen">{{ JSON.stringify(proposal.target, null, 2) }}</pre>
        <footer>
          <el-button @click="proposal = undefined">放弃提案</el-button>
          <el-button type="primary" @click="apply">应用到草稿</el-button>
        </footer>
      </section>
    </div>
  </el-drawer>
</template>

<style scoped>
.portal-ai-drawer__title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.portal-ai-drawer__title div {
  display: grid;
  gap: 2px;
}

.portal-ai-drawer__title small {
  color: #64766f;
}

.portal-ai-drawer__mark {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  color: #fff;
  font-size: 12px;
  font-weight: 800;
  background: #08735e;
  border-radius: 10px;
}

.portal-ai-drawer__body {
  display: grid;
  gap: 20px;
}

.portal-ai-section {
  display: grid;
  gap: 8px;
}

.portal-ai-section > label {
  color: #24443c;
  font-size: 13px;
  font-weight: 700;
}

.portal-ai-section > small {
  color: #6d7f79;
  line-height: 1.5;
}

.portal-ai-presets {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.portal-ai-presets button,
.portal-ai-json-toggle {
  padding: 6px 10px;
  color: #386158;
  font-size: 12px;
  background: #f2f7f5;
  border: 1px solid #d6e4df;
  border-radius: 999px;
  cursor: pointer;
}

.portal-ai-json-toggle {
  justify-self: start;
}

.portal-ai-presets button:hover,
.portal-ai-json-toggle:hover {
  color: #08735e;
  border-color: #8bc3b4;
}

.portal-ai-generate {
  width: 100%;
}

.portal-ai-proposal {
  display: grid;
  gap: 14px;
  padding: 16px;
  background: #f8fbfa;
  border: 1px solid #d7e6e1;
  border-radius: 12px;
}

.portal-ai-change-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.portal-ai-change-grid div {
  display: grid;
  gap: 2px;
  padding: 10px;
  background: #fff;
  border: 1px solid #e0ebe7;
  border-radius: 8px;
}

.portal-ai-change-grid strong {
  color: #08735e;
  font-size: 18px;
}

.portal-ai-change-grid span {
  color: #70817c;
  font-size: 11px;
}

.portal-ai-proposal header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.portal-ai-proposal header div {
  display: grid;
  gap: 4px;
}

.portal-ai-proposal header span,
.portal-ai-proposal header small {
  color: #6d7f79;
  font-size: 11px;
}

.portal-ai-proposal pre {
  max-height: 260px;
  margin: 0;
  padding: 12px;
  overflow: auto;
  color: #d7eee7;
  font-size: 11px;
  line-height: 1.55;
  background: #17352f;
  border-radius: 8px;
}

.portal-ai-proposal footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
