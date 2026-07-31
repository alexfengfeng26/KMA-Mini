<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { api, unwrap } from '../api/client'
import AppPagination from '../components/AppPagination.vue'
import { useClientPagination } from '../components/listPagination'
import SpaceSelect from '../components/SpaceSelect.vue'
import type { components } from '../api/generated/schema'

type RetrieveDebugResult = components['schemas']['RetrieveDebugResult']
type ChunkHit = components['schemas']['ChunkHitVO']

type StageKey = 'final' | 'vector' | 'fullText' | 'rerank'

const STAGES: { key: StageKey; label: string }[] = [
  { key: 'final', label: '最终结果' },
  { key: 'vector', label: '向量召回' },
  { key: 'fullText', label: '全文召回' },
  { key: 'rerank', label: '重排结果' },
]

const STAGE_META: Record<StageKey, { color: string; hint: string }> = {
  final: { color: 'var(--el-color-primary)', hint: '经 RRF 融合与重排后返回的最终结果' },
  vector: { color: '#e6a23c', hint: '仅由向量相似度召回的原始结果' },
  fullText: { color: '#67c23a', hint: '仅由全文检索召回的原始结果' },
  rerank: { color: '#f56c6c', hint: '经重排模型打分后的结果' },
}

const form = reactive({ spaceCode: 'default', query: '', topK: 8 })
const loading = ref(false)
const result = ref<RetrieveDebugResult>()
const activeStage = ref<StageKey>('final')
const expandedIds = ref<Set<string>>(new Set())

const stageHits = computed<ChunkHit[]>(() => {
  if (!result.value) return []
  switch (activeStage.value) {
    case 'vector':
      return result.value.vectorHits || []
    case 'fullText':
      return result.value.fullTextHits || []
    case 'rerank':
      return result.value.rerankedHits || []
    default:
      return result.value.finalHits || []
  }
})

const { page, pageSize, total, pagedItems, resetPage } = useClientPagination(stageHits)

const stageCounts = computed(() => ({
  vector: result.value?.vectorHits?.length ?? 0,
  fullText: result.value?.fullTextHits?.length ?? 0,
  rerank: result.value?.rerankedHits?.length ?? 0,
  final: result.value?.finalHits?.length ?? 0,
}))

const latencyItems = computed(() => {
  const latency = result.value?.latency
  if (!latency) return []
  return Object.entries(latency).map(([label, value]) => ({ label, value }))
})

function formatScore(value: number | undefined) {
  if (value === undefined || value === null || Number.isNaN(value)) return '-'
  return value.toFixed(3)
}

function hitKey(hit: ChunkHit, index: number) {
  return `${hit.chunkId ?? index}-${hit.docId ?? 0}-${hit.section ?? ''}`
}

function isExpanded(hit: ChunkHit, index: number) {
  return expandedIds.value.has(hitKey(hit, index))
}

function toggleExpand(hit: ChunkHit, index: number) {
  const key = hitKey(hit, index)
  const next = new Set(expandedIds.value)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  expandedIds.value = next
}

function switchStage(stage: string | number | boolean | undefined) {
  const allowed: StageKey[] = ['final', 'vector', 'fullText', 'rerank']
  if (typeof stage === 'string' && allowed.includes(stage as StageKey)) {
    activeStage.value = stage as StageKey
    resetPage()
  }
}

async function run() {
  loading.value = true
  try {
    result.value = await unwrap(api.POST('/api/v1/retrieval/debug', { body: form }))
    activeStage.value = 'final'
    expandedIds.value = new Set()
    resetPage()
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="panel">
    <div class="toolbar">
      <div>
        <span class="eyebrow">RRF TRACE</span>
        <h2>检索分数调试</h2>
      </div>
    </div>

    <el-form class="retrieval-form" label-position="top">
      <el-row :gutter="16">
        <el-col :xs="24" :sm="6">
          <el-form-item label="知识空间"><SpaceSelect v-model="form.spaceCode" /></el-form-item>
        </el-col>
        <el-col :xs="24" :sm="14">
          <el-form-item label="检索问题">
            <el-input v-model="form.query" placeholder="输入一个问题" @keyup.enter="run" />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="4">
          <el-form-item label="候选数">
            <el-input-number v-model="form.topK" :min="1" :max="100" class="full-width" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-button type="primary" :loading="loading" @click="run">运行混合检索</el-button>
    </el-form>

    <section v-if="result" class="retrieval-summary">
      <div class="summary-head">
        <span class="summary-query">“{{ result.query }}”</span>
        <span class="summary-meta">空间：{{ result.spaceCode }}</span>
        <span class="summary-meta">topK：{{ form.topK }}</span>
      </div>
      <div class="summary-pipeline">
        <span class="pipeline-step">向量召回 <strong>{{ stageCounts.vector }}</strong> 条</span>
        <span class="pipeline-arrow">→</span>
        <span class="pipeline-step">全文召回 <strong>{{ stageCounts.fullText }}</strong> 条</span>
        <span class="pipeline-arrow">→</span>
        <span class="pipeline-step">RRF 融合</span>
        <span class="pipeline-arrow">→</span>
        <span class="pipeline-step">重排 <strong>{{ stageCounts.rerank }}</strong> 条</span>
        <span class="pipeline-arrow">→</span>
        <span class="pipeline-step">最终返回 <strong>{{ stageCounts.final }}</strong> 条</span>
      </div>
      <div v-if="latencyItems.length" class="summary-latency">
        <span v-for="item in latencyItems" :key="item.label" class="latency-item">
          {{ item.label }}：{{ item.value.toFixed(1) }}ms
        </span>
      </div>
    </section>

    <el-radio-group v-if="result" v-model="activeStage" size="small" @change="switchStage">
      <el-radio-button v-for="stage in STAGES" :key="stage.key" :label="stage.key" :value="stage.key">
        {{ stage.label }} ({{ stageCounts[stage.key] }})
      </el-radio-button>
    </el-radio-group>

    <div v-if="result && !stageHits.length" class="empty-stage">该阶段没有命中结果</div>

    <div v-if="stageHits.length" class="hit-list">
      <div
        v-for="(hit, index) in pagedItems"
        :key="hitKey(hit, (page - 1) * pageSize + index)"
        class="hit-card"
      >
        <div class="hit-card__header">
          <span class="hit-rank">#{{ (page - 1) * pageSize + index + 1 }}</span>
          <div class="hit-source">
            <div class="hit-source__title">
              <strong>{{ hit.docTitle || '未命名文档' }}</strong>
              <el-tag v-if="hit.chunkIndex !== undefined" size="small" type="warning" effect="plain">
                第 {{ hit.chunkIndex + 1 }} 段
              </el-tag>
              <el-tag v-if="hit.section" size="small" type="info" effect="plain">{{ hit.section }}</el-tag>
            </div>
            <div class="hit-source__meta">
              <el-tag v-if="hit.sourceTag" size="small">{{ hit.sourceTag }}</el-tag>
              <el-tag v-if="hit.documentNumber" size="small" type="info">{{ hit.documentNumber }}</el-tag>
              <el-tag v-if="hit.externalRef" size="small" type="success" effect="plain" title="外部引用">
                {{ hit.externalRef.slice(0, 16) }}{{ hit.externalRef.length > 16 ? '…' : '' }}
              </el-tag>
              <span v-if="hit.issuingAuthority" class="meta-text">{{ hit.issuingAuthority }}</span>
            </div>
          </div>
        </div>
        <div class="hit-card__body">
          <p :class="['hit-content', { 'is-expanded': isExpanded(hit, (page - 1) * pageSize + index) }]">
            {{ hit.content || '（无内容）' }}
          </p>
          <el-button
            v-if="hit.content && hit.content.length > 180"
            link
            size="small"
            @click="toggleExpand(hit, (page - 1) * pageSize + index)"
          >
            {{ isExpanded(hit, (page - 1) * pageSize + index) ? '收起' : '展开全文' }}
          </el-button>
        </div>
        <div class="hit-card__footer">
          <div class="score-bars">
            <div v-if="hit.rrfScore !== undefined" class="score-bar" title="RRF 融合分数：综合向量与全文召回的排序分数">
              <span class="score-label">RRF</span>
              <el-progress :percentage="Math.round((hit.rrfScore ?? 0) * 100)" :show-text="false" />
              <span class="score-value">{{ formatScore(hit.rrfScore) }}</span>
            </div>
            <div v-if="hit.rerankScore !== undefined" class="score-bar" title="重排模型分数：由重排模型给出的相关性分数">
              <span class="score-label">重排</span>
              <el-progress :percentage="Math.round((hit.rerankScore ?? 0) * 100)" :show-text="false" />
              <span class="score-value">{{ formatScore(hit.rerankScore) }}</span>
            </div>
          </div>
          <div class="score-tags">
            <el-tooltip content="向量相似度分数">
              <el-tag size="small" type="info" effect="plain">向量 {{ formatScore(hit.vectorScore) }}</el-tag>
            </el-tooltip>
            <el-tooltip content="全文检索分数（BM25）">
              <el-tag size="small" type="info" effect="plain">全文 {{ formatScore(hit.fullTextScore) }}</el-tag>
            </el-tooltip>
            <el-tag
              v-if="hit.sourceStage"
              size="small"
              effect="plain"
              :style="{ color: STAGE_META[hit.sourceStage as StageKey]?.color, borderColor: STAGE_META[hit.sourceStage as StageKey]?.color }"
            >
              来源：{{ STAGES.find((s) => s.key === hit.sourceStage)?.label || hit.sourceStage }}
            </el-tag>
          </div>
        </div>
      </div>
    </div>

    <AppPagination
      v-if="stageHits.length"
      v-model:page="page"
      v-model:page-size="pageSize"
      :total="total"
      class="spaced-top"
    />
  </div>
</template>

<style scoped>
.retrieval-summary {
  margin: 16px 0;
  padding: 14px 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
}

.summary-head {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: baseline;
  margin-bottom: 10px;
}

.summary-query {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.summary-meta {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.summary-pipeline {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
  font-size: 13px;
}

.pipeline-step {
  padding: 4px 10px;
  border-radius: 999px;
  background: #fff;
  color: var(--el-text-color-regular);
}

.pipeline-step strong {
  color: var(--el-color-primary);
}

.pipeline-arrow {
  color: var(--el-text-color-secondary);
}

.summary-latency {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.empty-stage {
  margin: 24px 0;
  text-align: center;
  color: var(--el-text-color-secondary);
}

.hit-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 16px;
}

.hit-card {
  padding: 14px 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: #fff;
  transition: box-shadow 0.2s;
}

.hit-card:hover {
  box-shadow: var(--el-box-shadow-light);
}

.hit-card__header {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 10px;
}

.hit-rank {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 32px;
  height: 24px;
  border-radius: 4px;
  background: var(--el-color-primary);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}

.hit-source {
  flex: 1;
  min-width: 0;
}

.hit-source__title {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 6px;
}

.hit-source__title strong {
  font-size: 15px;
}

.hit-source__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.meta-text {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.hit-card__body {
  margin-bottom: 12px;
}

.hit-content {
  margin: 0;
  color: var(--el-text-color-regular);
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.hit-content:not(.is-expanded) {
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
}

.hit-card__footer {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.score-bars {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
  min-width: 220px;
}

.score-bar {
  display: grid;
  grid-template-columns: 38px 1fr 48px;
  gap: 10px;
  align-items: center;
}

.score-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.score-value {
  font-size: 12px;
  color: var(--el-text-color-primary);
  text-align: right;
}

.score-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.spaced-top {
  margin-top: 16px;
}
</style>
