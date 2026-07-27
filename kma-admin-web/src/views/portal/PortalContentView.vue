<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { openAuthorizedFile } from '../../api/download'
import { getPortalContent, type PartyContent } from '../../api/party'
import { asRecord, errorMessage } from '../../api/client'
import PortalSystemPageFrame from '../../cms/v3/PortalSystemPageFrame.vue'
import DocumentMeta from '../../components/DocumentMeta.vue'
import PageState from '../../components/PageState.vue'
import StatusTag from '../../components/StatusTag.vue'
import { validityMeta } from '../../domain/partyKnowledge'
import { useSiteNavigation } from '../../composables/useSiteNavigation'
import { getActivePortalSiteKey } from '../../app/portalSiteContext'

interface ContentSection {
  chunkId: number
  chunkIndex: number
  content: string
}

interface ContentVersion {
  contentId: number
  sourceVersion: number
  active: boolean
  workflowStatus: string
}

interface RelatedContent {
  contentId: number
  title: string
}

const route = useRoute()
const router = useRouter()
const { sitePath } = useSiteNavigation()
const loading = ref(true)
const sourceLoading = ref(false)
const error = ref('')
const content = ref<PartyContent>()
const activeSection = ref(0)
let controller: AbortController | undefined

const status = computed(() => validityMeta(content.value?.validityStatus))
const sections = computed<ContentSection[]>(() =>
  (content.value?.sections || []).map((value) => {
    const record = asRecord(value)
    return {
      chunkId: Number(record.chunkId ?? record.chunk_id ?? 0),
      chunkIndex: Number(record.chunkIndex ?? record.chunk_index ?? 0),
      content: String(record.content ?? ''),
    }
  }),
)
const versions = computed<ContentVersion[]>(() =>
  (content.value?.versions || []).map((value) => {
    const record = asRecord(value)
    return {
      contentId: Number(record.contentId ?? record.content_id ?? 0),
      sourceVersion: Number(record.sourceVersion ?? record.source_version ?? 0),
      active: Boolean(record.active),
      workflowStatus: String(record.workflowStatus ?? record.workflow_status ?? ''),
    }
  }),
)
const related = computed<RelatedContent[]>(() =>
  (content.value?.related || []).map((value) => {
    const record = asRecord(value)
    return {
      contentId: Number(record.contentId ?? record.content_id ?? 0),
      title: String(record.title ?? ''),
    }
  }),
)
const metadata = computed(() => [
  { label: '发文机关', value: content.value?.issuingAuthority },
  { label: '文号', value: content.value?.documentNumber },
  { label: '发布日期', value: content.value?.publishDate },
  { label: '生效日期', value: content.value?.effectiveDate },
  { label: '失效日期', value: content.value?.expiryDate },
  { label: '访问范围', value: content.value?.spaceName },
  { label: '来源文件', value: content.value?.mimeType },
])

async function load() {
  const contentId = Number(route.params.contentId)
  if (!Number.isFinite(contentId)) {
    error.value = '内容编号无效'
    return
  }

  controller?.abort()
  controller = new AbortController()
  const currentController = controller
  loading.value = true
  error.value = ''
  try {
    content.value = await getPortalContent(
      contentId,
      String(route.query.location || '') || undefined,
      currentController.signal,
    )
    const location = Number(route.query.chunk)
    if (Number.isFinite(location)) {
      await nextTick()
      jump(location)
    } else {
      activeSection.value = 0
    }
  } catch (cause: unknown) {
    if ((cause as Error).name !== 'AbortError') error.value = errorMessage(cause, '内容不可用')
  } finally {
    if (!currentController.signal.aborted) loading.value = false
  }
}

function jump(index: number) {
  activeSection.value = index
  document.getElementById(`section-${index}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function ask() {
  if (!content.value?.contentId) return
  void router.push({
    path: sitePath('/portal/ask'),
    query: {
      docId: content.value.contentId,
      spaceCode: content.value.spaceCode,
      title: content.value.title,
    },
  })
}

async function openSource() {
  if (!content.value?.contentId || sourceLoading.value) return
  sourceLoading.value = true
  try {
    await openAuthorizedFile(
      `/api/v1/portal-sites/${encodeURIComponent(getActivePortalSiteKey())}/contents/${content.value.contentId}/source`,
    )
  } catch (cause: unknown) {
    ElMessage.error(errorMessage(cause, '原始文件打开失败'))
  } finally {
    sourceLoading.value = false
  }
}

watch(
  () => [route.params.contentId, route.query.location, route.query.chunk],
  () => void load(),
  { immediate: true },
)
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <PortalSystemPageFrame core-component="document-reader">
    <PageState :loading="loading" :error="error" :empty="!content">
      <div v-if="content" class="portal-reader">
        <aside class="reader-toc">
          <span class="portal-kicker">章节目录</span>
          <button
            v-for="section in sections"
            :key="section.chunkId"
            :class="{ active: activeSection === section.chunkIndex }"
            @click="jump(section.chunkIndex)"
          >
            第 {{ section.chunkIndex + 1 }} 节
          </button>
          <div class="reader-versions">
            <h3>版本记录</h3>
            <span v-for="version in versions" :key="version.contentId">
              v{{ version.sourceVersion }} · {{ version.active ? '当前版本' : version.workflowStatus }}
            </span>
          </div>
        </aside>

        <article class="reader-document">
          <nav class="reader-breadcrumb" aria-label="面包屑">
            <router-link :to="sitePath('/portal/library')">资料中心</router-link>
            <span>/</span>
            <span>{{ content.title }}</span>
          </nav>
          <div
            v-if="['expired', 'repealed'].includes(content.validityStatus || '')"
            class="reader-danger"
            role="alert"
          >
            该材料{{ status.label }}，不得作为现行工作依据。
          </div>
          <header>
            <div class="portal-result-tags">
              <StatusTag :status="content.validityStatus" />
              <span>版本 v{{ content.sourceVersion }}</span>
            </div>
            <h1>{{ content.title }}</h1>
            <p>{{ content.summary }}</p>
          </header>
          <div class="reader-mode">
            <button class="active">解析正文</button>
            <button :disabled="sourceLoading" @click="openSource">
              {{ sourceLoading ? '正在打开原始文件…' : '打开原始文件' }}
            </button>
          </div>
          <div class="reader-body">
            <section v-for="section in sections" :id="`section-${section.chunkIndex}`" :key="section.chunkId">
              <span>第 {{ section.chunkIndex + 1 }} 节</span>
              <p>{{ section.content }}</p>
            </section>
            <p v-if="!sections.length" class="portal-empty">正文仍在解析，暂不可阅读。</p>
          </div>
        </article>

        <aside class="reader-meta">
          <div>
            <span class="portal-kicker">权威信息</span>
            <DocumentMeta :items="metadata" />
            <el-button type="primary" @click="ask">基于本文提问</el-button>
          </div>
          <div>
            <h3>相关材料</h3>
            <router-link
              v-for="item in related"
              :key="item.contentId"
              :to="sitePath('/portal/content/:contentId', { contentId: item.contentId })"
            >
              {{ item.title }}
            </router-link>
          </div>
        </aside>
      </div>
    </PageState>
  </PortalSystemPageFrame>
</template>
