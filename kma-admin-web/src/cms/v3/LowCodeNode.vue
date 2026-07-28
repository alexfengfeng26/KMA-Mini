<script setup lang="ts">
import { computed } from 'vue'
import PortalExtensionFrame from '../PortalExtensionFrame.vue'
import { cmsV2BlockRegistry } from '../blockDefinitions'
import type {
  LayoutNode,
  PortalBootstrap,
  PortalBreakpoint,
  PortalCoreComponent,
  ReusableSection,
  ResolvedPortalExtension,
} from '../siteConfig'
import { responsiveValue } from './contract'

const props = withDefaults(
  defineProps<{
    node: LayoutNode
    breakpoint?: PortalBreakpoint
    bootstrap: PortalBootstrap
    symbols?: Record<string, ReusableSection>
    query: string
    depth?: number
    symbolTrail?: string[]
    coreComponent?: PortalCoreComponent
  }>(),
  { breakpoint: 'desktop', symbols: () => ({}), depth: 1, symbolTrail: () => [] },
)

const emit = defineEmits<{
  'update:query': [value: string]
  search: []
  ask: []
  category: [contentType: string]
  action: [payload: { nodeId: string; action: string; value?: unknown }]
}>()

const children = computed(() => ('children' in props.node ? props.node.children : []))
const isLayout = computed(() => ['section', 'container', 'grid', 'stack'].includes(props.node.type))
const symbol = computed(() =>
  props.node.type === 'symbol-ref' ? props.symbols[props.node.symbolId] : undefined,
)
const symbolCycle = computed(
  () => props.node.type === 'symbol-ref' && props.symbolTrail.includes(props.node.symbolId),
)
const blockComponent = computed(() => {
  if (props.node.type !== 'component') return undefined
  return cmsV2BlockRegistry[props.node.component as keyof typeof cmsV2BlockRegistry]
})
const extension = computed<ResolvedPortalExtension | undefined>(() => {
  const node = props.node
  if (node.type !== 'sandbox' || node.source === 'inline') return undefined
  return props.bootstrap.extensions.find(
    (item) => item.extensionId === node.packageId && item.version === node.version,
  )
})

const nodeStyle = computed(() => {
  const layout = props.node.layout
  const style = props.node.style
  return {
    '--lc-span-desktop': String(responsiveValue(layout?.span, 'desktop', 12)),
    '--lc-span-tablet': String(responsiveValue(layout?.span, 'tablet', 8)),
    '--lc-span-mobile': String(responsiveValue(layout?.span, 'mobile', 4)),
    '--lc-order-desktop': String(responsiveValue(layout?.order, 'desktop', 0)),
    '--lc-order-tablet': String(responsiveValue(layout?.order, 'tablet', 0)),
    '--lc-order-mobile': String(responsiveValue(layout?.order, 'mobile', 0)),
    '--lc-gap-desktop': `${responsiveValue(layout?.gap, 'desktop', 16)}px`,
    '--lc-gap-tablet': `${responsiveValue(layout?.gap, 'tablet', 12)}px`,
    '--lc-gap-mobile': `${responsiveValue(layout?.gap, 'mobile', 12)}px`,
    '--lc-padding-desktop': `${responsiveValue(style?.padding, 'desktop', 0)}px`,
    '--lc-padding-tablet': `${responsiveValue(style?.padding, 'tablet', 0)}px`,
    '--lc-padding-mobile': `${responsiveValue(style?.padding, 'mobile', 0)}px`,
    '--lc-max-width': layout?.maxWidth || 'none',
    '--lc-background': style?.background || 'transparent',
    '--lc-color': style?.color || 'inherit',
    '--lc-border-color': style?.borderColor || 'transparent',
    '--lc-radius': style?.radius || '0px',
    '--lc-direction-desktop': responsiveValue(layout?.direction, 'desktop', 'row'),
    '--lc-direction-tablet': responsiveValue(layout?.direction, 'tablet', 'row'),
    '--lc-direction-mobile': responsiveValue(layout?.direction, 'mobile', 'column'),
  }
})
const visibilityClasses = computed(() => ({
  'low-code-node--hidden-desktop': responsiveValue(props.node.layout?.hidden, 'desktop', false),
  'low-code-node--hidden-tablet': responsiveValue(props.node.layout?.hidden, 'tablet', false),
  'low-code-node--hidden-mobile': responsiveValue(props.node.layout?.hidden, 'mobile', false),
}))
</script>

<template>
  <component
    :is="node.type === 'section' ? 'section' : 'div'"
    class="low-code-node"
    :class="[`low-code-node--${node.type}`, { 'low-code-node--layout': isLayout }, visibilityClasses]"
    :style="nodeStyle"
    :data-low-code-node="node.id"
  >
    <template v-if="isLayout">
      <LowCodeNode
        v-for="child in children"
        :key="child.id"
        :node="child"
        :breakpoint="breakpoint"
        :bootstrap="bootstrap"
        :symbols="symbols"
        :query="query"
        :depth="depth + 1"
        :symbol-trail="symbolTrail"
        :core-component="coreComponent"
        @update:query="emit('update:query', $event)"
        @search="emit('search')"
        @ask="emit('ask')"
        @category="emit('category', $event)"
        @action="emit('action', $event)"
      >
        <template #core>
          <slot name="core" />
        </template>
      </LowCodeNode>
    </template>

    <component
      :is="blockComponent"
      v-else-if="node.type === 'component' && blockComponent"
      :config="{
        id: node.id,
        type: node.component,
        enabled: true,
        props: node.props,
        dataSource: node.dataSource,
      }"
      :data="bootstrap.portalData"
      :query="query"
      @update:query="emit('update:query', $event)"
      @search="emit('search')"
      @ask="emit('ask')"
      @category="emit('category', $event)"
    />

    <slot
      v-else-if="node.type === 'component' && node.component === coreComponent"
      name="core"
      :node="node"
    />

    <div v-else-if="node.type === 'component'" class="low-code-core-widget" :data-core="node.component">
      <strong>{{ node.name || node.component }}</strong>
      <span>系统业务组件由对应门户页面提供实时数据。</span>
    </div>

    <PortalExtensionFrame
      v-else-if="node.type === 'sandbox' && node.source === 'inline' && node.inline"
      :inline="node.inline"
      :node-id="node.id"
      :config="node.config"
      :bootstrap="bootstrap"
    />

    <PortalExtensionFrame
      v-else-if="node.type === 'sandbox' && extension"
      :extension="extension"
      :node-id="node.id"
      :config="node.config"
      :bootstrap="bootstrap"
    />

    <div v-else-if="node.type === 'sandbox'" class="low-code-node__fallback">
      <strong>沙箱组件不可用</strong>
      <span>{{ node.packageId || '代码区块' }}@{{ node.version || '草稿' }} 未出现在当前发布目录。</span>
    </div>

    <div v-else-if="node.type === 'symbol-ref' && symbolCycle" class="low-code-node__fallback">
      可复用区块存在循环引用：{{ node.symbolId }}
    </div>

    <LowCodeNode
      v-else-if="node.type === 'symbol-ref' && symbol"
      :node="symbol.root"
      :breakpoint="breakpoint"
      :bootstrap="bootstrap"
      :symbols="symbols"
      :query="query"
      :depth="depth + 1"
      :symbol-trail="[...symbolTrail, node.symbolId]"
      :core-component="coreComponent"
      @update:query="emit('update:query', $event)"
      @search="emit('search')"
      @ask="emit('ask')"
      @category="emit('category', $event)"
      @action="emit('action', $event)"
    >
      <template #core>
        <slot name="core" />
      </template>
    </LowCodeNode>

    <div v-else class="low-code-node__fallback">节点无法渲染：{{ node.id }}</div>
  </component>
</template>
