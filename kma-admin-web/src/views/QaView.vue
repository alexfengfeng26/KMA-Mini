<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, DocumentCopy } from '@element-plus/icons-vue'
import { api, asList, errorMessage, unwrap } from '../api/client'
import { streamQa, type QaStreamEvent } from '../api/sse'
import PageState from '../components/PageState.vue'
import SpaceSelect from '../components/SpaceSelect.vue'
import type { components } from '../api/generated/schema'
import { useAuthStore } from '../stores/auth'

type ChatSession = components['schemas']['KnowledgeChatSession']
type ChatMessage = components['schemas']['KnowledgeChatMessage']
type QaAnswer = components['schemas']['QAResult']
type ChunkHitVO = NonNullable<QaAnswer['citations']>[number]

interface ThreadMessage extends ChatMessage {
  error?: boolean
  citationsList?: QaAnswer['citations']
}

interface CurrentAnswer {
  content: string
  citations: ChunkHitVO[]
  answered: boolean
  reason: string
  loading: boolean
}

const auth = useAuthStore()
const canReadHistory = computed(() => auth.hasAnyPermission(['chat:read']))

const form = reactive({
  spaceCode: 'default',
  query: '',
  topK: 6,
  sessionId: undefined as number | undefined,
})
const loading = ref(false),
  streamMode = ref(true),
  error = ref(''),
  heartbeat = ref('')
const controller = ref<AbortController>()
const sessions = ref<ChatSession[]>([]),
  messages = ref<ThreadMessage[]>([]),
  historyLoading = ref(false),
  historyError = ref('')
const currentAnswer = ref<CurrentAnswer | null>(null)
const sessionSearch = ref('')
const sidebarVisible = ref(true)
const isMobile = ref(false)
const chatContainer = ref<HTMLElement | null>(null)
const expandedCitations = ref<Set<number>>(new Set())

const examples = ['什么是 KMA Mini？', '如何落实好“三会一课”制度？', '主题党日的组织要求是什么？']

const filteredSessions = computed(() => {
  const keyword = sessionSearch.value.trim().toLowerCase()
  if (!keyword) return sessions.value
  return sessions.value.filter(
    (session) =>
      session.title?.toLowerCase().includes(keyword) || session.spaceCode?.toLowerCase().includes(keyword),
  )
})

const thread = computed<ThreadMessage[]>(() => {
  const list = [...messages.value]
  if (currentAnswer.value) {
    list.push({
      role: 'assistant',
      content: currentAnswer.value.content,
      createTime: new Date().toISOString(),
      citationsList: currentAnswer.value.citations,
      error: !currentAnswer.value.answered,
    })
  }
  return list
})

const currentSessionTitle = computed(() => {
  const session = sessions.value.find((s) => s.sessionId === form.sessionId)
  return session?.title || ''
})

function updateViewport() {
  isMobile.value = window.innerWidth < 900
  if (isMobile.value) sidebarVisible.value = false
}

function formatTime(iso?: string) {
  if (!iso) return ''
  const date = new Date(iso)
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function formatRelative(iso?: string) {
  if (!iso) return ''
  const date = new Date(iso)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  if (diff < 60 * 1000) return '刚刚'
  if (diff < 60 * 60 * 1000) return `${Math.floor(diff / 60000)} 分钟前`
  if (diff < 24 * 60 * 60 * 1000) return `${Math.floor(diff / 3600000)} 小时前`
  if (diff < 7 * 24 * 60 * 60 * 1000)
    return date.toLocaleString('zh-CN', { weekday: 'long', hour: '2-digit', minute: '2-digit' })
  return formatTime(iso)
}

async function loadSessions() {
  if (!canReadHistory.value) return
  historyLoading.value = true
  historyError.value = ''
  try {
    sessions.value = asList(
      await unwrap(
        api.GET('/api/v1/chat/sessions', {
          params: { query: { spaceCode: form.spaceCode || undefined } },
        }),
      ),
    )
  } catch (e: unknown) {
    historyError.value = errorMessage(e, '无法读取会话记录')
  } finally {
    historyLoading.value = false
  }
}

async function loadMessages(sessionId: number) {
  historyLoading.value = true
  historyError.value = ''
  try {
    messages.value = asList(
      await unwrap(
        api.GET('/api/v1/chat/sessions/{sessionId}/messages', {
          params: { path: { sessionId } },
        }),
      ),
    )
  } catch (e: unknown) {
    historyError.value = errorMessage(e, '无法读取会话消息')
  } finally {
    historyLoading.value = false
  }
}

async function selectSession(session: ChatSession) {
  if (!session.sessionId) return
  form.sessionId = session.sessionId
  form.spaceCode = session.spaceCode || form.spaceCode
  currentAnswer.value = null
  error.value = ''
  await loadMessages(session.sessionId)
  if (isMobile.value) sidebarVisible.value = false
  scrollToBottom()
}

function resetThread() {
  controller.value?.abort()
  form.sessionId = undefined
  form.query = ''
  messages.value = []
  currentAnswer.value = null
  error.value = ''
}

function newConversation() {
  resetThread()
  if (isMobile.value) sidebarVisible.value = false
}

function onSpaceChange() {
  resetThread()
  if (canReadHistory.value) loadSessions()
}

function useExample(text: string) {
  form.query = text
}

async function ask() {
  const question = form.query.trim()
  if (!question || loading.value) return
  controller.value?.abort()
  loading.value = true
  error.value = ''
  heartbeat.value = ''
  messages.value.push({
    role: 'user',
    content: question,
    createTime: new Date().toISOString(),
  })
  currentAnswer.value = {
    content: '',
    citations: [],
    answered: true,
    reason: '',
    loading: true,
  }
  form.query = ''
  scrollToBottom()
  try {
    if (streamMode.value) {
      controller.value = new AbortController()
      await streamQa({ ...form, query: question, stream: true }, onEvent, controller.value.signal)
    } else {
      const result = await unwrap(
        api.POST('/api/v1/qa', { body: { ...form, query: question, stream: false } }),
      )
      currentAnswer.value = {
        content: result.answer || '',
        citations: (result.citations || []).filter((item): item is ChunkHitVO => !!item?.docTitle),
        answered: result.answered ?? true,
        reason: result.reason || '',
        loading: false,
      }
      form.sessionId = result.sessionId
    }
    if (canReadHistory.value) await loadSessions()
  } catch (e: unknown) {
    if ((e as Error).name === 'AbortError') {
      currentAnswer.value = null
    } else {
      error.value = errorMessage(e, '问答失败')
      if (currentAnswer.value) {
        currentAnswer.value.answered = false
        currentAnswer.value.reason = 'ERROR'
        currentAnswer.value.loading = false
      }
    }
  } finally {
    loading.value = false
    controller.value = undefined
    if (currentAnswer.value) {
      currentAnswer.value.loading = false
      messages.value.push({
        role: 'assistant',
        content: currentAnswer.value.content,
        createTime: new Date().toISOString(),
        citations: JSON.stringify(currentAnswer.value.citations),
        error: !currentAnswer.value.answered,
      })
      currentAnswer.value = null
      scrollToBottom()
    }
  }
}

function onEvent(event: QaStreamEvent, data: string) {
  const current = currentAnswer.value
  if (!current) return
  if (event === 'message') {
    if (!data || data.trim() === '' || data.trim().toLowerCase() === 'null') return
    current.content = `${current.content || ''}${data}`
  } else if (event === 'citations') {
    try {
      const parsed: QaAnswer['citations'] = JSON.parse(data)
      const seen = new Set<string>()
      current.citations = (parsed || []).filter((item): item is ChunkHitVO => {
        if (!item?.docTitle || seen.has(item.docTitle)) return false
        seen.add(item.docTitle)
        return true
      })
    } catch {
      current.citations = []
    }
  } else if (event === 'heartbeat') {
    heartbeat.value = new Date().toLocaleTimeString()
  } else if (event === 'done') {
    const id = Number(data)
    if (Number.isFinite(id)) form.sessionId = id
    current.loading = false
  } else if (event === 'error') {
    current.answered = false
    current.reason = 'STREAM_ERROR'
    error.value = data
    current.loading = false
  }
}

function retry() {
  const lastUser = [...messages.value].reverse().find((message) => message.role === 'user')
  if (!lastUser?.content) return
  form.query = lastUser.content
  ask()
}

function cancel() {
  controller.value?.abort()
  loading.value = false
}

function parsedCitations(message: ThreadMessage): ChunkHitVO[] {
  if (message.citationsList) return message.citationsList
  if (!message.citations) return []
  try {
    const parsed: QaAnswer['citations'] = JSON.parse(message.citations)
    return (parsed || []).filter((item): item is ChunkHitVO => !!item?.docTitle)
  } catch {
    return []
  }
}

function hasCitations(message: ThreadMessage) {
  return parsedCitations(message).length > 0
}

function toggleCitations(index: number) {
  const next = new Set(expandedCitations.value)
  if (next.has(index)) next.delete(index)
  else next.add(index)
  expandedCitations.value = next
}

async function copyText(text?: string) {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制回答')
  } catch {
    ElMessage.error('复制失败')
  }
}

function truncateText(value: string | undefined, max: number) {
  if (!value) return ''
  const normalized = value.replace(/\s+/g, ' ').trim()
  if (normalized.length <= max) return normalized
  return normalized.slice(0, max) + '…'
}

function displayContent(text?: string) {
  return (text || '').replace(/^(null)+|(?:null)+$/gi, '').trim()
}

function scrollToBottom() {
  nextTick(() => {
    const container = chatContainer.value
    if (!container) return
    container.scrollTop = container.scrollHeight
  })
}

watch(() => thread.value.length, scrollToBottom)
watch(
  () => currentAnswer.value?.content,
  () => scrollToBottom(),
)

onMounted(() => {
  updateViewport()
  window.addEventListener('resize', updateViewport)
  if (canReadHistory.value) loadSessions()
})
onBeforeUnmount(() => {
  controller.value?.abort()
  window.removeEventListener('resize', updateViewport)
})
</script>

<template>
  <div class="qa-layout" :class="{ 'sidebar-hidden': !sidebarVisible || !canReadHistory }">
    <aside v-if="canReadHistory" class="panel session-panel">
      <div class="toolbar">
        <div>
          <span class="eyebrow">CONVERSATIONS</span>
          <h2>会话记录</h2>
        </div>
        <el-button link @click="loadSessions">刷新</el-button>
      </div>
      <div class="session-search">
        <el-input v-model="sessionSearch" placeholder="搜索会话" clearable />
      </div>
      <el-button type="primary" plain class="new-session" @click="newConversation">
        <el-icon class="icon-left"><plus /></el-icon> 新建会话
      </el-button>
      <PageState :loading="historyLoading" :error="historyError" :empty="!filteredSessions.length">
        <div class="session-list">
          <button
            v-for="session in filteredSessions"
            :key="session.sessionId"
            class="session-button"
            :class="{ active: form.sessionId === session.sessionId }"
            @click="selectSession(session)"
          >
            <strong>{{ session.title || `会话 ${session.sessionId}` }}</strong>
            <span
              >{{ session.spaceCode }} · {{ formatRelative(session.updateTime || session.createTime) }}</span
            >
          </button>
        </div>
      </PageState>
    </aside>

    <div class="qa-main">
      <section class="panel chat-panel">
        <div class="toolbar">
          <div class="chat-toolbar-left">
            <el-button v-if="canReadHistory && isMobile" link @click="sidebarVisible = !sidebarVisible">
              {{ sidebarVisible ? '收起会话' : '会话' }}
            </el-button>
            <div>
              <span class="eyebrow">GROUNDED ANSWER</span>
              <h2>{{ currentSessionTitle || '问答实验室' }}</h2>
            </div>
          </div>
          <div class="toolbar-right">
            <span v-if="heartbeat" class="muted heartbeat">心跳 {{ heartbeat }}</span>
            <el-checkbox v-model="streamMode">流式回答</el-checkbox>
          </div>
        </div>

        <div ref="chatContainer" class="chat-container">
          <div v-if="!thread.length" class="empty-state">
            <div class="empty-title">基于知识文档的问答助手</div>
            <div class="empty-desc">选择知识空间后输入问题，KMA 将检索相关文档并给出带引用依据的回答。</div>
            <div class="example-list">
              <el-button
                v-for="example in examples"
                :key="example"
                link
                type="primary"
                @click="useExample(example)"
                >{{ example }}</el-button
              >
            </div>
          </div>

          <article
            v-for="(message, index) in thread"
            :key="index"
            :class="['chat-message', message.role, { error: message.error }]"
          >
            <div class="message-header">
              <strong>{{ message.role === 'assistant' ? 'KMA' : '用户' }}</strong>
              <span class="message-time">{{ formatTime(message.createTime) }}</span>
            </div>
            <p class="message-content">{{ displayContent(message.content) }}</p>
            <div v-if="message.role === 'assistant'" class="message-actions">
              <el-button v-if="message.error" link type="warning" @click="retry">重试</el-button>
              <el-button link @click="copyText(message.content)">
                <el-icon class="icon-left"><document-copy /></el-icon> 复制
              </el-button>
              <el-button v-if="hasCitations(message)" link @click="toggleCitations(index)">
                {{
                  expandedCitations.has(index) ? '收起引用' : `查看引用 (${parsedCitations(message).length})`
                }}
              </el-button>
            </div>
            <div v-if="message.role === 'assistant' && expandedCitations.has(index)" class="citation-panel">
              <ol class="citation-list">
                <li v-for="(item, idx) in parsedCitations(message)" :key="idx">
                  <strong>{{ item.docTitle }}</strong>
                  <span class="citation-snippet">{{ truncateText(item.content, 200) }}</span>
                </li>
              </ol>
            </div>
          </article>

          <div v-if="loading && !currentAnswer?.content" class="typing-indicator">
            <span class="dot" /><span class="dot" /><span class="dot" />
            KMA 正在思考…
          </div>
        </div>

        <el-alert v-if="error" type="error" :title="error" :closable="false" show-icon class="chat-error" />

        <div class="composer">
          <div class="composer-controls">
            <div class="control-group">
              <label>知识空间</label>
              <SpaceSelect v-model="form.spaceCode" @change="onSpaceChange" />
            </div>
            <div class="control-group narrow">
              <label>Top K</label>
              <el-input-number v-model="form.topK" :min="1" :max="100" />
            </div>
          </div>
          <el-input
            v-model="form.query"
            type="textarea"
            :rows="3"
            placeholder="输入一个需要知识依据的问题，Ctrl + Enter 发送"
            :disabled="loading"
            @keydown.ctrl.enter.prevent="ask"
          />
          <div class="composer-actions">
            <span class="muted">Ctrl + Enter 发送</span>
            <el-button v-if="!loading" type="primary" :disabled="!form.query.trim()" @click="ask"
              >发送</el-button
            >
            <el-button v-else type="danger" @click="cancel">停止生成</el-button>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.qa-layout {
  display: grid;
  grid-template-columns: minmax(260px, 300px) minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}
.qa-layout.sidebar-hidden {
  grid-template-columns: minmax(0, 1fr);
}
.qa-main {
  display: grid;
  gap: 20px;
  min-width: 0;
}
.session-panel {
  position: sticky;
  top: 136px;
  max-height: calc(100vh - 168px);
  overflow: auto;
  display: flex;
  flex-direction: column;
}
.session-search {
  margin-bottom: 12px;
}
.new-session {
  width: 100%;
  margin-bottom: 14px;
}
.session-list {
  display: grid;
  gap: 8px;
}
.session-button {
  width: 100%;
  border: 1px solid var(--el-border-color);
  border-radius: 10px;
  background: transparent;
  color: var(--el-text-color-primary);
  padding: 12px;
  text-align: left;
  cursor: pointer;
  transition: all 0.2s;
}
.session-button:hover,
.session-button.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.session-button strong,
.session-button span {
  display: block;
}
.session-button span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-top: 5px;
  overflow-wrap: anywhere;
}
.chat-panel {
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - 168px);
}
.chat-toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 16px;
}
.heartbeat {
  font-size: 12px;
}
.toggle-sidebar {
  display: none;
}
.chat-container {
  flex: 1;
  min-height: 320px;
  max-height: calc(100vh - 380px);
  overflow-y: auto;
  padding: 8px 4px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.empty-state {
  margin: auto;
  text-align: center;
  max-width: 520px;
  padding: 40px 20px;
}
.empty-title {
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 8px;
}
.empty-desc {
  color: var(--el-text-color-secondary);
  margin-bottom: 20px;
}
.example-list {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
}
.chat-message {
  max-width: 85%;
  padding: 14px 16px;
  border-radius: 12px;
  background: var(--el-fill-color-light);
  align-self: flex-start;
}
.chat-message.user {
  align-self: flex-end;
  background: var(--el-color-primary-light-8);
}
.chat-message.assistant {
  border-left: 3px solid var(--el-color-primary);
}
.chat-message.error {
  border-left-color: var(--el-color-warning);
  background: var(--el-color-warning-light-9);
}
.message-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  gap: 12px;
}
.message-time {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.message-content {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.7;
  font-size: 15px;
}
.message-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
  flex-wrap: wrap;
}
.citation-panel {
  margin-top: 12px;
  padding: 12px;
  background: var(--el-fill-color);
  border-radius: 8px;
}
.citation-list {
  margin: 0;
  padding-left: 1.25rem;
}
.citation-list li {
  margin-bottom: 0.75rem;
  line-height: 1.5;
}
.citation-list li strong {
  display: block;
  margin-bottom: 0.25rem;
  color: var(--el-text-color-primary);
}
.citation-snippet {
  display: block;
  color: var(--el-text-color-secondary);
  font-size: 0.875rem;
}
.typing-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--el-text-color-secondary);
  font-size: 14px;
  align-self: flex-start;
  margin-left: 4px;
}
.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--el-color-primary);
  animation: bounce 1.2s infinite ease-in-out;
}
.dot:nth-child(2) {
  animation-delay: 0.2s;
}
.dot:nth-child(3) {
  animation-delay: 0.4s;
}
@keyframes bounce {
  0%,
  80%,
  100% {
    transform: translateY(0);
  }
  40% {
    transform: translateY(-4px);
  }
}
.chat-error {
  margin: 12px 0;
}
.composer {
  margin-top: auto;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color);
}
.composer-controls {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
  align-items: flex-end;
}
.control-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
}
.control-group.narrow {
  flex: 0 0 120px;
}
.control-group label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.composer-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
}
.icon-left {
  margin-right: 4px;
}

@media (max-width: 900px) {
  .qa-layout {
    grid-template-columns: 1fr;
  }
  .session-panel {
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    z-index: 100;
    width: 280px;
    max-height: none;
    border-radius: 0;
    margin: 0;
    box-shadow: 2px 0 12px rgba(0, 0, 0, 0.12);
  }
  .qa-layout.sidebar-hidden .session-panel {
    display: none;
  }
  .toggle-sidebar {
    display: inline-flex;
  }
  .chat-container {
    max-height: calc(100vh - 360px);
  }
  .composer-controls {
    flex-direction: column;
    gap: 12px;
  }
  .control-group.narrow {
    flex: 1;
  }
}
</style>
