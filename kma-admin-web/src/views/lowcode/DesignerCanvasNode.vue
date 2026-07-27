<script setup lang="ts">
import { computed, ref } from 'vue'
import type { LayoutNode, PortalBreakpoint, PortalCoreComponent } from '../../cms/siteConfig'
import { blockDefinition } from '../../cms/blockDefinitions'
import { responsiveValue } from '../../cms/v3/contract'
import type { DesignerDropPosition } from '../../cms/v3/designerTree'
import { topicDirectoryColumns } from '../../cms/v3/designerWorkspace'

const coreComponentMeta: Record<PortalCoreComponent, { title: string; description: string; marker: string }> =
  {
    'content-results': {
      title: '资料检索结果',
      description: '运行时加载筛选条件、分页结果和已发布资料。',
      marker: '检索',
    },
    'document-reader': {
      title: '内容阅读器',
      description: '运行时加载正文、附件、关联资料和阅读操作。',
      marker: '阅读',
    },
    'ai-conversation': {
      title: 'AI 问答会话',
      description: '运行时加载对话记录、引用来源和问题输入区。',
      marker: '问答',
    },
    'topic-directory': {
      title: '专题目录',
      description: '运行时加载专题列表、专题进度和关联内容。',
      marker: '专题',
    },
    'favorite-list': {
      title: '收藏与历史',
      description: '运行时加载当前用户的收藏资料和阅读记录。',
      marker: '收藏',
    },
    'profile-card': {
      title: '个人资料',
      description: '运行时加载当前用户资料、账号状态和安全设置。',
      marker: '账号',
    },
    'portal-navigation': {
      title: '门户导航',
      description: '运行时加载当前站点的已启用页面和导航入口。',
      marker: '导航',
    },
    'account-entry': {
      title: '账号入口',
      description: '运行时加载登录状态、个人中心和退出入口。',
      marker: '账户',
    },
  }

const props = defineProps<{
  node: LayoutNode
  selectedId?: string
  breakpoint: PortalBreakpoint
  previewWidth: number
}>()

const emit = defineEmits<{
  select: [nodeId: string]
  drop: [targetId: string, position: DesignerDropPosition, payload: string]
}>()

const children = computed(() => ('children' in props.node ? props.node.children : []))
const acceptsChildren = computed(() => 'children' in props.node)
const topicColumns = computed(() => topicDirectoryColumns(props.previewWidth))
const topicSamples = [
  { index: '01', title: '重点专题学习', description: '聚合权威文件与学习材料' },
  { index: '02', title: '基层实践案例', description: '沉淀可复用的实践经验' },
  { index: '03', title: '政策解读专栏', description: '追踪重点政策与最新动态' },
]
const componentMeta = computed(() => {
  if (props.node.type !== 'component') return undefined
  const core = coreComponentMeta[props.node.component as PortalCoreComponent]
  if (core) return { ...core, core: true, component: props.node.component }
  const definition = blockDefinition(props.node.component as Parameters<typeof blockDefinition>[0])
  return {
    title: definition?.title || props.node.name || props.node.component,
    description: definition
      ? `${definition.category}业务区块，门户运行时会加载真实内容。`
      : '门户运行时加载此业务组件的真实内容。',
    marker: definition?.category || '区块',
    core: false,
    component: props.node.component,
  }
})
const label = computed(() => {
  if (props.node.name) return props.node.name
  if (props.node.type === 'component') return componentMeta.value?.title || props.node.component
  if (props.node.type === 'sandbox') return `沙箱 · ${props.node.packageId}`
  if (props.node.type === 'symbol-ref') return `复用 · ${props.node.symbolId}`
  return props.node.type
})
const gridStyle = computed(() => ({
  '--designer-span': String(
    responsiveValue(
      props.node.layout?.span,
      props.breakpoint,
      props.breakpoint === 'desktop' ? 12 : props.breakpoint === 'tablet' ? 8 : 4,
    ),
  ),
}))
const dropPosition = ref<DesignerDropPosition>()

function drop(event: DragEvent) {
  const payload = event.dataTransfer?.getData('application/x-kma-node')
  if (payload) {
    event.preventDefault()
    event.stopPropagation()
    const position = dropPosition.value || (acceptsChildren.value ? 'inside' : 'after')
    dropPosition.value = undefined
    emit('drop', props.node.id, position, payload)
  }
}

function dragOver(event: DragEvent) {
  if (!event.dataTransfer?.types.includes('application/x-kma-node')) return
  event.preventDefault()
  event.stopPropagation()
  const current = event.currentTarget as HTMLElement
  const bounds = current.getBoundingClientRect()
  const ratio = bounds.height ? (event.clientY - bounds.top) / bounds.height : 0.5
  dropPosition.value =
    ratio < 0.25 ? 'before' : ratio > 0.75 ? 'after' : acceptsChildren.value ? 'inside' : 'after'
}

function dragNode(event: DragEvent) {
  if (props.node.locked) {
    event.preventDefault()
    return
  }
  event.dataTransfer?.setData(
    'application/x-kma-node',
    JSON.stringify({ kind: 'move', nodeId: props.node.id }),
  )
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
}

function forwardDrop(targetId: string, position: DesignerDropPosition, payload: string) {
  emit('drop', targetId, position, payload)
}
</script>

<template>
  <article
    class="designer-node"
    :class="[
      `designer-node--${node.type}`,
      {
        'is-selected': selectedId === node.id,
        'accepts-children': acceptsChildren,
        [`is-drop-${dropPosition}`]: dropPosition,
      },
    ]"
    :style="gridStyle"
    :data-node-id="node.id"
    tabindex="0"
    @click.stop="emit('select', node.id)"
    @focus="emit('select', node.id)"
    @dragover="dragOver"
    @dragleave.self="dropPosition = undefined"
    @drop="drop"
  >
    <header
      class="designer-node__label"
      :draggable="!node.locked"
      :title="node.locked ? '锁定节点不能移动' : '拖拽调整位置'"
      @dragstart.stop="dragNode"
      @dragend="dropPosition = undefined"
    >
      <span>{{ label }}</span>
      <small>{{ node.type }}</small>
    </header>
    <div v-if="acceptsChildren" class="designer-node__children">
      <DesignerCanvasNode
        v-for="child in children"
        :key="child.id"
        :node="child"
        :selected-id="selectedId"
        :breakpoint="breakpoint"
        :preview-width="previewWidth"
        @select="emit('select', $event)"
        @drop="forwardDrop"
      />
      <div v-if="children.length === 0" class="designer-node__empty">拖入组件或布局</div>
    </div>
    <div v-else class="designer-node__preview" :class="{ 'has-component': node.type === 'component' }">
      <template v-if="node.type === 'sandbox'">独立来源 iframe · 受控 Portal SDK</template>
      <template v-else-if="node.type === 'symbol-ref'">共享区块实例</template>
      <div
        v-else-if="node.type === 'component' && node.component === 'topic-directory'"
        class="designer-topic-preview"
        :class="`columns-${topicColumns}`"
        :data-columns="topicColumns"
        data-testid="topic-directory-preview"
      >
        <article v-for="topic in topicSamples" :key="topic.index">
          <span>{{ topic.index }}</span>
          <strong>{{ topic.title }}</strong>
          <p>{{ topic.description }}</p>
          <small>浏览专题资料 →</small>
        </article>
      </div>
      <div
        v-else-if="node.type === 'component' && componentMeta"
        class="designer-component-preview"
        :class="{ 'is-core': componentMeta.core }"
      >
        <div class="designer-component-preview__marker">{{ componentMeta.marker }}</div>
        <div class="designer-component-preview__content">
          <div class="designer-component-preview__heading">
            <strong>{{ componentMeta.title }}</strong>
            <span>{{ componentMeta.component }}</span>
          </div>
          <p>{{ componentMeta.description }}</p>
          <small v-if="componentMeta.core">系统核心组件 · 真实数据在门户运行时加载</small>
          <small v-else>业务区块 · 真实数据在门户运行时加载</small>
        </div>
      </div>
    </div>
  </article>
</template>

<style scoped>
.designer-node {
  position: relative;
}

.designer-node.is-drop-before::before,
.designer-node.is-drop-after::after {
  position: absolute;
  right: 4px;
  left: 4px;
  z-index: 4;
  height: 3px;
  content: '';
  background: #08735e;
  border-radius: 999px;
  box-shadow: 0 0 0 3px rgb(8 115 94 / 0.14);
}

.designer-node.is-drop-before::before {
  top: -3px;
}

.designer-node.is-drop-after::after {
  bottom: -3px;
}

.designer-node.is-drop-inside {
  outline: 2px solid #0b8f73;
  outline-offset: 2px;
  background: rgb(11 143 115 / 0.05);
}

.designer-node__label[draggable='true'] {
  cursor: grab;
}

.designer-node__label[draggable='true']:active {
  cursor: grabbing;
}

.designer-component-preview {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr);
  gap: 12px;
  width: 100%;
  min-height: 92px;
  padding: 14px;
  color: #435c56;
  text-align: left;
  background: linear-gradient(135deg, #f7faf9, #eef5f2);
  border: 1px solid #d4e2dd;
  border-radius: 8px;
}

.designer-component-preview.is-core {
  background: linear-gradient(135deg, #f2faf7, #e5f3ee);
  border-color: #a9d4c7;
}

.designer-component-preview__marker {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  color: #08735e;
  font-size: 12px;
  font-weight: 800;
  background: #d7eee7;
  border-radius: 12px;
}

.designer-component-preview__content {
  display: grid;
  align-content: center;
  gap: 5px;
  min-width: 0;
}

.designer-component-preview__heading {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px;
}

.designer-component-preview__heading strong {
  color: #173f36;
  font-size: 14px;
}

.designer-component-preview__heading span {
  color: #6f8580;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 10px;
}

.designer-component-preview p {
  margin: 0;
  color: #526b65;
  font-size: 12px;
  line-height: 1.5;
}

.designer-component-preview small {
  color: #16816a;
  font-size: 10px;
  font-weight: 700;
}

.designer-topic-preview {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  width: 100%;
}

.designer-topic-preview.columns-2 {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.designer-topic-preview.columns-1 {
  grid-template-columns: minmax(0, 1fr);
}

.designer-topic-preview article {
  display: grid;
  gap: 8px;
  min-width: 0;
  min-height: 132px;
  padding: 16px;
  text-align: left;
  background: linear-gradient(145deg, #fff, #edf7f3);
  border: 1px solid #c9ded7;
  border-radius: 12px;
}

.designer-topic-preview span,
.designer-topic-preview small {
  color: #08735e;
  font-size: 10px;
  font-weight: 700;
}

.designer-topic-preview strong,
.designer-topic-preview p {
  overflow-wrap: anywhere;
}

.designer-topic-preview strong {
  color: #173f36;
  font-size: 14px;
}

.designer-topic-preview p {
  margin: 0;
  color: #526b65;
  font-size: 11px;
  line-height: 1.5;
}
</style>
