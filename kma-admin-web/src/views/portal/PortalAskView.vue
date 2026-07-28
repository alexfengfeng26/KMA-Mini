<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useRoute, useRouter } from 'vue-router'
import { getPortalTopics, submitQaFeedback } from '../../api/party'
import { ElMessage } from 'element-plus'
import { streamQa, type QaStreamEvent } from '../../api/sse'
import { errorMessage } from '../../api/client'
import PortalSystemPageFrame from '../../cms/v3/PortalSystemPageFrame.vue'
import CitationCard from '../../components/CitationCard.vue'
import PageState from '../../components/PageState.vue'
import { PARTY_CONTENT_CATEGORIES } from '../../domain/partyKnowledge'
import { useSiteNavigation } from '../../composables/useSiteNavigation'
import { getActivePortalPreviewVersion, getActivePortalSiteKey } from '../../app/portalSiteContext'

interface Citation {
  chunkId: number
  docId: number
  docTitle: string
  documentNumber?: string
  issuingAuthority?: string
  validityStatus?: string
  chunkIndex: number
  section?: string
  content: string
  externalRef?: string
}

interface StreamingAnswer {
  answer: string
  citations: Citation[]
  answered: boolean
  reason?: string
}

const route = useRoute()
const router = useRouter()
const { sitePath } = useSiteNavigation()
const form = reactive({
  query: '',
  spaceCode: '*',
  topK: 6,
  contentTypes: [] as string[],
  topicCodes: [] as string[],
  docId: undefined as number | undefined,
  historical: false,
  sessionId: undefined as number | undefined,
})
const answer = ref<StreamingAnswer>()
const loading = ref(false)
const feedback = ref<'helpful' | 'unhelpful'>()
const error = ref('')
const controller = ref<AbortController>()
const topicsQuery = useQuery({
  queryKey: ['portal-topics'],
  queryFn: getPortalTopics,
  staleTime: 5 * 60_000,
})
const topics = computed(() => topicsQuery.data.value || [])
let pendingText = ''
let animationFrame: number | undefined

function applyRouteScope() {
  form.query = String(route.query.query || '')
  form.spaceCode = String(route.query.spaceCode || '*')
  form.docId = route.query.docId ? Number(route.query.docId) : undefined
}

function flushText() {
  if (answer.value && pendingText) {
    answer.value.answer += pendingText
    pendingText = ''
  }
  animationFrame = undefined
}

function queueText(value: string) {
  pendingText += value
  if (animationFrame === undefined) animationFrame = requestAnimationFrame(flushText)
}

async function ask() {
  if (!form.query.trim() || loading.value) return
  controller.value?.abort()
  loading.value = true
  error.value = ''
  answer.value = { answer: '', citations: [], answered: true }
  feedback.value = undefined
  controller.value = new AbortController()
  try {
    const siteKey = encodeURIComponent(getActivePortalSiteKey())
    const previewVersion = getActivePortalPreviewVersion()
    await streamQa(
      { ...form, portalOnly: true, stream: true },
      onEvent,
      controller.value.signal,
      previewVersion
        ? `/api/v1/admin/portal-sites/${siteKey}/versions/${previewVersion}/preview/ask/stream`
        : `/api/v1/portal-sites/${siteKey}/ask/stream`,
    )
    flushText()
  } catch (cause: unknown) {
    if ((cause as Error).name !== 'AbortError') error.value = errorMessage(cause, '问答失败')
  } finally {
    loading.value = false
    controller.value = undefined
  }
}

async function rate(value: 'helpful' | 'unhelpful') {
  if (!answer.value || feedback.value) return
  try {
    await submitQaFeedback({
      rating: value,
      spaceCode: form.spaceCode,
      sessionId: form.sessionId,
      question: form.query,
      answerExcerpt: answer.value.answer.slice(0, 4000),
      citationRefs: answer.value.citations
        .map((item) => String(item.externalRef || ''))
        .filter(Boolean)
        .slice(0, 20),
    })
    feedback.value = value
    ElMessage.success(
      value === 'helpful' ? '感谢反馈，已记录为有效回答。' : '已记录问题，将进入知识质量改进队列。',
    )
  } catch (cause: unknown) {
    ElMessage.error(errorMessage(cause, '反馈提交失败'))
  }
}

function onEvent(event: QaStreamEvent, data: string) {
  if (!answer.value) return
  if (event === 'message') queueText(data)
  else if (event === 'citations') {
    try {
      answer.value.citations = JSON.parse(data) as Citation[]
    } catch {
      answer.value.citations = []
      error.value = '引用信息格式异常，回答正文仍可阅读。'
    }
  } else if (event === 'done') {
    flushText()
    const id = Number(data)
    if (Number.isFinite(id)) form.sessionId = id
  } else if (event === 'error') {
    answer.value.answered = false
    answer.value.reason = 'STREAM_ERROR'
    error.value = data
  }
}

function cancel() {
  controller.value?.abort()
  flushText()
}

function openCitation(item: Citation) {
  void router.push({
    path: sitePath('/portal/content/:contentId', { contentId: item.docId }),
    query: { chunk: item.chunkIndex, location: `chunk:${item.chunkIndex}` },
  })
}

watch(() => [route.query.query, route.query.spaceCode, route.query.docId], applyRouteScope, {
  immediate: true,
})
onBeforeUnmount(() => {
  controller.value?.abort()
  if (animationFrame !== undefined) cancelAnimationFrame(animationFrame)
})
</script>

<template>
  <PortalSystemPageFrame core-component="ai-conversation">
    <div class="portal-ask-layout">
      <aside class="portal-ask-scope">
        <span class="portal-kicker">回答范围</span>
        <h2>选择知识依据</h2>
        <label>知识空间<el-input v-model="form.spaceCode" placeholder="* 表示全部可访问空间" /></label>
        <p class="ask-scope-hint">输入 <strong>*</strong> 使用全部可访问知识空间，或填写一个空间编码。</p>
        <label>
          内容分类
          <el-select v-model="form.contentTypes" multiple clearable placeholder="全类别">
            <el-option
              v-for="item in PARTY_CONTENT_CATEGORIES"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </label>
        <label>
          专题
          <el-select
            v-model="form.topicCodes"
            multiple
            clearable
            placeholder="全专题"
            :loading="topicsQuery.isPending.value"
          >
            <el-option
              v-for="item in topics"
              :key="item.topicCode"
              :label="item.name"
              :value="item.topicCode"
            />
          </el-select>
        </label>
        <p v-if="topicsQuery.error.value" class="form-error">专题暂时无法加载，仍可使用其他范围问答。</p>
        <div v-if="form.docId" class="ask-doc-scope">
          <span>单篇文档</span>
          <strong>{{ route.query.title || `内容 ${form.docId}` }}</strong>
          <button @click="form.docId = undefined">取消限定</button>
        </div>
        <el-checkbox v-model="form.historical">允许引用历史失效资料</el-checkbox>
        <p>默认只使用已发布、在线、现行有效且你有权限访问的内容。</p>
      </aside>

      <section class="portal-ask-main">
        <header>
          <span class="portal-kicker">AI 引用问答</span>
          <h1>问党建知识</h1>
          <p>答案必须附带可定位的权威来源；证据不足时系统会明确拒答。</p>
        </header>
        <div class="portal-question-box">
          <textarea
            v-model="form.query"
            placeholder="例如：基层党组织应如何落实“三会一课”制度？"
            @keydown.ctrl.enter="ask"
          ></textarea>
          <div>
            <span>Ctrl + Enter 快速发送</span>
            <button v-if="!loading" :disabled="!form.query.trim()" @click="ask">生成有依据的回答</button>
            <button v-else class="danger" @click="cancel">停止生成</button>
          </div>
        </div>
        <PageState :loading="false" :error="error" :empty="false">
          <article v-if="answer" class="portal-answer">
            <div class="portal-answer-state">
              <el-tag :type="answer.answered ? 'success' : 'warning'">
                {{ answer.answered ? '基于资料回答' : '无证据拒答' }}
              </el-tag>
              <span v-if="loading">正在检索并组织答案…</span>
            </div>
            <p class="portal-answer-text">
              {{ answer.answer || (loading ? '正在生成…' : '未返回内容') }}
            </p>
            <div v-if="!loading && answer.answer" class="portal-answer-feedback">
              <span>这条回答是否解决了你的问题？</span>
              <el-button
                size="small"
                :type="feedback === 'helpful' ? 'success' : 'default'"
                @click="rate('helpful')"
                >有帮助</el-button
              >
              <el-button
                size="small"
                :type="feedback === 'unhelpful' ? 'danger' : 'default'"
                @click="rate('unhelpful')"
                >需改进</el-button
              >
            </div>
            <section>
              <h2>
                引用依据 <span>{{ answer.citations.length }}</span>
              </h2>
              <CitationCard
                v-for="item in answer.citations"
                :key="item.chunkId"
                :citation="item"
                @open="openCitation"
              />
              <p v-if="!answer.citations.length" class="portal-empty">没有可用引用。</p>
            </section>
          </article>
          <div v-else class="portal-ask-placeholder">
            <span>问</span>
            <h2>从权威资料中寻找答案</h2>
            <p>每条引用都可以跳转到对应文件版本和正文位置。</p>
          </div>
        </PageState>
      </section>
    </div>
  </PortalSystemPageFrame>
</template>
