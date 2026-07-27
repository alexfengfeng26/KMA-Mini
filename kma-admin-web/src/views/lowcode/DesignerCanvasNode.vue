<script setup lang="ts">
import { useDragAndDrop } from '@formkit/drag-and-drop/vue'
import { computed, watch } from 'vue'
import type { LayoutNode, PortalBreakpoint } from '../../cms/siteConfig'
import { blockDefinition } from '../../cms/blockDefinitions'
import { responsiveValue } from '../../cms/v3/contract'

const props = defineProps<{
  node: LayoutNode
  selectedId?: string
  breakpoint: PortalBreakpoint
}>()

const emit = defineEmits<{
  select: [nodeId: string]
  drop: [parentId: string, payload: string]
  reorder: [parentId: string, children: LayoutNode[]]
}>()

const children = computed(() => ('children' in props.node ? props.node.children : []))
const acceptsChildren = computed(() => 'children' in props.node)
const label = computed(() => {
  if (props.node.name) return props.node.name
  if (props.node.type === 'component')
    return (
      blockDefinition(props.node.component as Parameters<typeof blockDefinition>[0])?.title ||
      props.node.component
    )
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
const [childrenContainer, draggableChildren] = useDragAndDrop<LayoutNode>(children.value, {
  group: 'kma-low-code-layout',
  sortable: true,
  dropZone: true,
  dragHandle: '.designer-node__label',
})

watch(
  () => children.value,
  (next) => {
    if (next.map((item) => item.id).join('|') !== draggableChildren.value.map((item) => item.id).join('|'))
      draggableChildren.value = [...next]
  },
)

watch(draggableChildren, (next) => {
  if (
    acceptsChildren.value &&
    next.map((item) => item.id).join('|') !== children.value.map((item) => item.id).join('|')
  )
    emit('reorder', props.node.id, [...next])
})

function drop(event: DragEvent) {
  if (!acceptsChildren.value) return
  const payload = event.dataTransfer?.getData('application/x-kma-node')
  if (payload) {
    event.preventDefault()
    event.stopPropagation()
    emit('drop', props.node.id, payload)
  }
}

function dragOver(event: DragEvent) {
  if (event.dataTransfer?.types.includes('application/x-kma-node')) event.preventDefault()
}

function forwardDrop(parentId: string, payload: string) {
  emit('drop', parentId, payload)
}

function forwardReorder(parentId: string, nextChildren: LayoutNode[]) {
  emit('reorder', parentId, nextChildren)
}
</script>

<template>
  <article
    class="designer-node"
    :class="[
      `designer-node--${node.type}`,
      { 'is-selected': selectedId === node.id, 'accepts-children': acceptsChildren },
    ]"
    :style="gridStyle"
    :data-node-id="node.id"
    tabindex="0"
    @click.stop="emit('select', node.id)"
    @focus="emit('select', node.id)"
    @dragover="dragOver"
    @drop="drop"
  >
    <header class="designer-node__label">
      <span>{{ label }}</span>
      <small>{{ node.type }}</small>
    </header>
    <div v-if="acceptsChildren" ref="childrenContainer" class="designer-node__children">
      <DesignerCanvasNode
        v-for="child in draggableChildren"
        :key="child.id"
        :node="child"
        :selected-id="selectedId"
        :breakpoint="breakpoint"
        @select="emit('select', $event)"
        @drop="forwardDrop"
        @reorder="forwardReorder"
      />
      <div v-if="children.length === 0" class="designer-node__empty">拖入组件或布局</div>
    </div>
    <div v-else class="designer-node__preview">
      <template v-if="node.type === 'sandbox'">独立来源 iframe · 受控 Portal SDK</template>
      <template v-else-if="node.type === 'symbol-ref'">共享区块实例</template>
      <template v-else>实时业务组件预览区</template>
    </div>
  </article>
</template>
