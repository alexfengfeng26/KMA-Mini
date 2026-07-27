<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { api, asList, errorMessage, unwrap } from '../api/client'
import { streamQa, type QaStreamEvent } from '../api/sse'
import PageState from '../components/PageState.vue'
import type { components } from '../api/generated/schema'
import { useAuthStore } from '../stores/auth'

type ChatSession = components['schemas']['KnowledgeChatSession']
type ChatMessage = components['schemas']['KnowledgeChatMessage']
type QaAnswer = components['schemas']['QAResult']
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
  answer = ref<QaAnswer>(),
  error = ref(''),
  heartbeat = ref('')
const controller = ref<AbortController>()
const sessions = ref<ChatSession[]>([]),
  messages = ref<ChatMessage[]>([]),
  historyLoading = ref(false),
  historyError = ref('')

async function loadSessions() {
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

async function selectSession(session: ChatSession) {
  if (!session.sessionId) return
  form.sessionId = session.sessionId
  form.spaceCode = session.spaceCode || form.spaceCode
  historyLoading.value = true
  historyError.value = ''
  try {
    messages.value = asList(
      await unwrap(
        api.GET('/api/v1/chat/sessions/{sessionId}/messages', {
          params: { path: { sessionId: session.sessionId } },
        }),
      ),
    )
  } catch (e: unknown) {
    historyError.value = errorMessage(e, '无法读取会话消息')
  } finally {
    historyLoading.value = false
  }
}

function newConversation() {
  controller.value?.abort()
  form.sessionId = undefined
  form.query = ''
  answer.value = undefined
  messages.value = []
  error.value = ''
}

async function ask() {
  if (!form.query.trim()) return
  controller.value?.abort()
  loading.value = true
  error.value = ''
  heartbeat.value = ''
  answer.value = { answer: '', answered: true, citations: [], reason: '', sessionId: form.sessionId }
  try {
    if (streamMode.value) {
      controller.value = new AbortController()
      await streamQa({ ...form, stream: true }, onEvent, controller.value.signal)
    } else {
      answer.value = await unwrap(api.POST('/api/v1/qa', { body: { ...form, stream: false } }))
      form.sessionId = answer.value.sessionId
    }
    if (canReadHistory.value) await loadSessions()
  } catch (e: unknown) {
    if ((e as Error).name !== 'AbortError') error.value = errorMessage(e, '问答失败')
  } finally {
    loading.value = false
    controller.value = undefined
  }
}

function onEvent(event: QaStreamEvent, data: string) {
  const current = answer.value
  if (!current) return
  if (event === 'message') current.answer = `${current.answer || ''}${data}`
  else if (event === 'citations') {
    try {
      current.citations = JSON.parse(data)
    } catch {
      current.citations = []
    }
  } else if (event === 'heartbeat') heartbeat.value = new Date().toLocaleTimeString()
  else if (event === 'done') {
    const id = Number(data)
    if (Number.isFinite(id)) {
      current.sessionId = id
      form.sessionId = id
    }
  } else if (event === 'error') {
    current.answered = false
    current.reason = 'STREAM_ERROR'
    error.value = data
  }
}

function cancel() {
  controller.value?.abort()
  loading.value = false
}
onMounted(() => {
  if (canReadHistory.value) loadSessions()
})
onBeforeUnmount(() => controller.value?.abort())
</script>

<template>
  <div class="qa-layout">
    <section v-if="canReadHistory" class="panel session-panel">
      <div class="toolbar">
        <div>
          <span class="eyebrow">CONVERSATIONS</span>
          <h2>会话记录</h2>
        </div>
        <el-button link @click="loadSessions">刷新</el-button>
      </div>
      <el-button type="primary" plain class="new-session" @click="newConversation">新建会话</el-button>
      <PageState :loading="historyLoading" :error="historyError" :empty="!sessions.length">
        <div class="session-list">
          <button
            v-for="session in sessions"
            :key="session.sessionId"
            class="session-button"
            :class="{ active: form.sessionId === session.sessionId }"
            @click="selectSession(session)"
          >
            <strong>{{ session.title || `会话 ${session.sessionId}` }}</strong>
            <span>{{ session.spaceCode }} · {{ session.updateTime || session.createTime }}</span>
          </button>
        </div>
      </PageState>
    </section>

    <div class="qa-main">
      <section class="panel">
        <div class="toolbar">
          <div>
            <span class="eyebrow">GROUNDED ANSWER</span>
            <h2>问答实验室</h2>
          </div>
          <el-checkbox v-model="streamMode">流式回答</el-checkbox>
        </div>
        <el-input
          v-model="form.query"
          type="textarea"
          :rows="4"
          placeholder="输入一个需要知识依据的问题"
          @keydown.ctrl.enter="ask"
        />
        <div class="filter-bar spaced-top">
          <el-input v-model="form.spaceCode" placeholder="空间编码" @change="loadSessions" /><el-input-number
            v-model="form.topK"
            :min="1"
            :max="100"
          /><el-button v-if="!loading" type="primary" @click="ask">生成有依据的回答</el-button
          ><el-button v-else type="danger" @click="cancel">停止生成</el-button
          ><span v-if="heartbeat" class="muted">最近心跳 {{ heartbeat }}</span>
        </div>
        <el-alert v-if="error" type="error" :title="error" :closable="false" show-icon />
        <div v-if="answer" class="answer-stream">
          <div>
            <el-tag :type="answer.answered ? 'success' : 'warning'">{{
              answer.answered ? '有依据回答' : answer.reason
            }}</el-tag
            ><el-tag v-if="answer.sessionId" type="info" class="spaced-left-2"
              >会话 {{ answer.sessionId }}</el-tag
            >
          </div>
          <p class="answer-text">{{ answer.answer || (loading ? '正在等待模型输出…' : '未返回内容') }}</p>
          <h3>引用依据</h3>
          <ol v-if="answer.citations?.length">
            <li v-for="item in answer.citations" :key="item.chunkId">
              <strong>{{ item.docTitle }}</strong
              ><span>{{ item.content }}</span>
            </li>
          </ol>
          <p v-else class="muted">暂无引用。</p>
        </div>
      </section>

      <section v-if="messages.length" class="panel message-history">
        <div class="toolbar">
          <div>
            <span class="eyebrow">MESSAGE HISTORY</span>
            <h2>历史消息</h2>
          </div>
          <el-tag>会话 {{ form.sessionId }}</el-tag>
        </div>
        <article v-for="message in messages" :key="message.messageId" :class="['chat-message', message.role]">
          <strong>{{ message.role === 'assistant' ? 'KMA' : '用户' }}</strong>
          <p>{{ message.content }}</p>
          <small>{{ message.createTime }}</small>
        </article>
      </section>
    </div>
  </div>
</template>
