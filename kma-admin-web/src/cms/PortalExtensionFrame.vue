<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import {
  portalExtensionAsk,
  portalExtensionContents,
  portalExtensionSearch,
  recordPortalEvent,
} from '../api/portalSites'
import { buildInlineSandboxDocument } from './portalSandbox'
import type { PortalBootstrap, PortalInlineCode, ResolvedPortalExtension } from './siteConfig'

const props = defineProps<{
  extension?: ResolvedPortalExtension
  inline?: PortalInlineCode
  bootstrap: PortalBootstrap
  nodeId?: string
  config?: Record<string, string | number | boolean>
}>()

const frame = ref<HTMLIFrameElement>()
const failed = ref(false)
let port: MessagePort | undefined

const title = computed(() => props.extension?.displayName || props.extension?.extensionId || '自定义代码区块')
const capabilities = computed(() => new Set(props.extension?.manifest.capabilities || props.inline?.manifest?.capabilities || ['page-context']))
const srcdoc = computed(() => (props.inline ? buildInlineSandboxDocument(props.inline) : undefined))
const extensionIdentity = computed(() => props.extension
  ? { id: props.extension.extensionId, version: props.extension.version }
  : { id: `inline:${props.nodeId || 'sandbox'}`, version: 'draft' })

function response(id: string, ok: boolean, value?: unknown) {
  port?.postMessage({ type: 'kma-sdk-result', id, ok, value })
}

async function handleRequest(event: MessageEvent<{ id?: string; type?: string; payload?: unknown }>) {
  const id = event.data?.id
  const type = event.data?.type
  if (!id || !type) return
  try {
    if (type === 'page-context' || type === 'portal.context.get') {
      response(id, true, {
        site: props.bootstrap.site,
        page: props.bootstrap.page.slug,
        theme: { pack: props.bootstrap.theme.pack, mode: props.bootstrap.theme.mode },
        config: props.config || props.extension?.config || {},
        data: props.bootstrap.portalData,
      })
      return
    }
    if ((type === 'contents' || type === 'portal.contents.list') && capabilities.value.has('contents')) {
      const keyword = typeof event.data.payload === 'object' && event.data.payload
        ? String((event.data.payload as { keyword?: unknown }).keyword || '').slice(0, 120) : ''
      response(id, true, await portalExtensionContents(props.bootstrap.site.siteKey, keyword))
      return
    }
    if ((type === 'search' || type === 'portal.search.query') && capabilities.value.has('search')) {
      const keyword = typeof event.data.payload === 'string' ? event.data.payload.slice(0, 120) : ''
      response(id, true, await portalExtensionSearch(props.bootstrap.site.siteKey, keyword))
      return
    }
    if ((type === 'ask' || type === 'portal.ask.submit') && capabilities.value.has('ask')) {
      const query = typeof event.data.payload === 'string' ? event.data.payload.slice(0, 500) : ''
      response(id, true, await portalExtensionAsk(props.bootstrap.site.siteKey, query))
      return
    }
    if ((type === 'analytics' || type === 'portal.analytics.track') && capabilities.value.has('analytics')) {
      const targetId = typeof event.data.payload === 'string' ? event.data.payload.slice(0, 128) : undefined
      await recordPortalEvent(props.bootstrap.site.siteKey, {
        eventType: 'article_click',
        pageSlug: props.bootstrap.page.slug,
        targetId,
        metadata: { extension: extensionIdentity.value.id, slot: props.nodeId || props.extension?.slotKey || 'sandbox' },
      })
      response(id, true, { recorded: true })
      return
    }
    response(id, false, { code: 'SDK_CAPABILITY_FORBIDDEN' })
  } catch {
    response(id, false, { code: 'SDK_REQUEST_FAILED' })
  }
}

function initialize() {
  const target = frame.value?.contentWindow
  if (!target) return
  port?.close()
  const channel = new MessageChannel()
  port = channel.port1
  port.onmessage = handleRequest
  target.postMessage(
    {
      type: 'kma-sdk-init',
      protocol: 1,
      extension: extensionIdentity.value,
    },
    '*',
    [channel.port2],
  )
}

function markFailed() {
  failed.value = true
  port?.close()
}

onBeforeUnmount(() => port?.close())
</script>

<template>
  <section class="portal-extension-frame" :data-extension="extensionIdentity.id">
    <header class="portal-extension-frame__label">
      <span>已发布扩展</span>
      <strong>{{ title }}</strong>
    </header>
    <iframe
      v-if="!failed"
      ref="frame"
      :src="extension?.entryUrl"
      :srcdoc="srcdoc"
      :title="title"
      sandbox="allow-scripts"
      referrerpolicy="no-referrer"
      @load="initialize"
      @error="markFailed"
    />
    <div v-else class="portal-extension-frame__fallback" role="status">
      <strong>扩展暂时不可用</strong>
      <span>该区域已安全回退，不影响门户其他内容。</span>
    </div>
  </section>
</template>
