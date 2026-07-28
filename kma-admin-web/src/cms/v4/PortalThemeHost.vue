<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getPortalContent } from '../../api/party'
import {
  portalBatchData,
  portalExtensionAsk,
  portalExtensionSearch,
  recordPortalEvent,
} from '../../api/portalSites'
import { useAuthStore } from '../../stores/auth'
import type { PortalBootstrap } from '../siteConfig'
import { buildThemeDocument } from './themeRuntime'

const props = defineProps<{ bootstrap: PortalBootstrap }>()
const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const frame = ref<HTMLIFrameElement>()
const currentContent = ref<unknown>()
let port: MessagePort | undefined
const capabilities = computed(
  () => new Set(props.bootstrap.themeRuntime?.manifest.capabilities || ['page-context']),
)

const runtimeBootstrap = computed<PortalBootstrap>(() => ({
  ...props.bootstrap,
  themeData: {
    currentContent: currentContent.value,
    user: auth.user
      ? { userId: auth.user.userId, username: auth.user.username, displayName: auth.user.displayName }
      : undefined,
  },
}))
const documentSource = computed(() =>
  props.bootstrap.themeRuntime
    ? buildThemeDocument(props.bootstrap.themeRuntime, runtimeBootstrap.value)
    : '',
)

function respond(id: string, ok: boolean, value?: unknown) {
  port?.postMessage({ id, ok, value })
}

function portalPath(target: string) {
  const site = encodeURIComponent(props.bootstrap.site.siteKey)
  const [path, query = ''] = target.split('?', 2)
  const system = ['home', 'library', 'topics', 'ask', 'favorites', 'profile', 'search'].includes(path)
  const normalized = system ? path : /^[a-z][a-z0-9-]{1,63}$/.test(path) ? `page/${path}` : 'home'
  const parameters = new URLSearchParams(query)
  if (props.bootstrap.previewVersionId)
    parameters.set('previewVersion', String(props.bootstrap.previewVersionId))
  return `/p/${site}/${normalized}${parameters.size ? `?${parameters}` : ''}`
}

async function handleRequest(event: MessageEvent<{ id?: string; type?: string; payload?: unknown }>) {
  const { id, type, payload } = event.data || {}
  if (!id || !type) return
  const requiredCapability: Record<string, string> = {
    'portal.context.get': 'page-context',
    'portal.data.query': 'contents',
    'portal.navigation.go': 'navigation',
    'portal.navigation.replace': 'navigation',
    'portal.navigation.back': 'navigation',
    'portal.search.query': 'search',
    'portal.ask.submit': 'ask',
    'portal.content.open': 'contents',
    'portal.analytics.track': 'analytics',
  }
  if (!capabilities.value.has(requiredCapability[type] || 'forbidden')) {
    respond(id, false, { code: 'SDK_CAPABILITY_FORBIDDEN' })
    return
  }
  try {
    if (type === 'portal.context.get') {
      respond(id, true, {
        site: props.bootstrap.site,
        page: props.bootstrap.page,
        data: props.bootstrap.portalData,
        user: runtimeBootstrap.value.themeData?.user,
      })
    } else if (type === 'portal.data.query') {
      const query =
        payload && typeof payload === 'object'
          ? (payload as { source?: string; filters?: Record<string, string> })
          : {}
      const source = ['documents', 'categories', 'topics', 'favorites', 'history'].includes(
        query.source || '',
      )
        ? query.source!
        : 'documents'
      respond(
        id,
        true,
        await portalBatchData(props.bootstrap.site.siteKey, [
          { id: 'theme', source, filters: query.filters },
        ]),
      )
    } else if (type === 'portal.navigation.go' || type === 'portal.navigation.replace') {
      const path = portalPath(String(payload || 'home').slice(0, 180))
      if (type.endsWith('replace')) await router.replace(path)
      else await router.push(path)
      respond(id, true, { path: route.fullPath })
    } else if (type === 'portal.navigation.back') {
      router.back()
      respond(id, true, { back: true })
    } else if (type === 'portal.search.query') {
      respond(
        id,
        true,
        await portalExtensionSearch(props.bootstrap.site.siteKey, String(payload || '').slice(0, 120)),
      )
    } else if (type === 'portal.ask.submit') {
      respond(
        id,
        true,
        await portalExtensionAsk(props.bootstrap.site.siteKey, String(payload || '').slice(0, 500)),
      )
    } else if (type === 'portal.content.open') {
      await router.push(
        `/p/${encodeURIComponent(props.bootstrap.site.siteKey)}/content/${encodeURIComponent(String(payload || ''))}`,
      )
      respond(id, true, { opened: true })
    } else if (type === 'portal.analytics.track') {
      await recordPortalEvent(props.bootstrap.site.siteKey, {
        eventType: 'page_view',
        pageSlug: props.bootstrap.page.slug,
        metadata: { source: 'theme-v4' },
      })
      respond(id, true, { recorded: true })
    } else respond(id, false, { code: 'SDK_CAPABILITY_FORBIDDEN' })
  } catch (error) {
    respond(id, false, {
      code: 'SDK_REQUEST_FAILED',
      message: error instanceof Error ? error.message.slice(0, 240) : '受控门户能力调用失败',
    })
  }
}

function initialize() {
  const target = frame.value?.contentWindow
  if (!target) return
  port?.close()
  const channel = new MessageChannel()
  port = channel.port1
  port.onmessage = handleRequest
  target.postMessage({ type: 'kma-theme-init', protocol: 1 }, '*', [channel.port2])
}

onBeforeUnmount(() => port?.close())
watch(
  () => route.params.contentId,
  async (contentId) => {
    const id = Number(contentId)
    currentContent.value =
      Number.isSafeInteger(id) && id > 0
        ? await getPortalContent(
            id,
            typeof route.query.location === 'string' ? route.query.location : undefined,
          )
        : undefined
  },
  { immediate: true },
)
</script>

<template>
  <aside v-if="bootstrap.preview" class="theme-preview-banner">
    <span>主题预览中 · V{{ bootstrap.previewVersion || bootstrap.publishedVersion }}</span>
    <button type="button" @click="router.push(`/p/${bootstrap.site.siteKey}/home`)">返回已发布门户</button>
  </aside>
  <iframe
    ref="frame"
    class="portal-theme-host"
    :srcdoc="documentSource"
    :title="`${bootstrap.site.name} 全站主题`"
    sandbox="allow-scripts"
    referrerpolicy="no-referrer"
    @load="initialize"
  />
</template>

<style scoped>
.portal-theme-host {
  display: block;
  width: 100%;
  min-height: 100vh;
  border: 0;
  background: #f5f8f7;
}

.theme-preview-banner {
  position: sticky;
  z-index: 40;
  top: 0;
  display: flex;
  justify-content: space-between;
  padding: 10px 20px;
  color: #173a5e;
  background: #e7f2ff;
  border-bottom: 1px solid #b8d9fa;
}

.theme-preview-banner button {
  color: #0d4f8b;
  background: transparent;
  border: 0;
  cursor: pointer;
}
</style>
