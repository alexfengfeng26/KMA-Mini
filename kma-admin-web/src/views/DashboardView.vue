<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, unwrap, asRecord, errorMessage } from '../api/client'
import PageState from '../components/PageState.vue'

interface DashboardMetrics {
  docCount?: number
  chunkCount?: number
  pendingTaskCount?: number
}

interface DependencyStatus {
  status?: string
  details?: Record<string, string | number | boolean | null>
}

interface DependencyOverview {
  core?: DependencyStatus
  models?: DependencyStatus
}

const loading = ref(true),
  error = ref(''),
  metrics = ref<DashboardMetrics>({}),
  deps = ref<DependencyOverview>({})
async function load() {
  loading.value = true
  try {
    const [m, d] = await Promise.all([
      unwrap(api.GET('/api/v1/metrics/dashboard')),
      unwrap(api.GET('/api/v1/system/dependencies')),
    ])
    metrics.value = asRecord(m) as DashboardMetrics
    deps.value = asRecord(d) as DependencyOverview
  } catch (e: unknown) {
    error.value = errorMessage(e, '无法读取运行指标')
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>
<template>
  <PageState :loading="loading" :error="error"
    ><div class="stat-grid">
      <div class="stat">
        <span>文档总数</span><strong>{{ metrics.docCount ?? '—' }}</strong>
      </div>
      <div class="stat">
        <span>Chunk 总数</span><strong>{{ metrics.chunkCount ?? '—' }}</strong>
      </div>
      <div class="stat">
        <span>待处理任务</span><strong>{{ metrics.pendingTaskCount ?? '—' }}</strong>
      </div>
      <div class="stat">
        <span>模型依赖</span><strong>{{ deps.models?.status ?? '—' }}</strong>
      </div>
    </div>
    <section class="panel dependency-map">
      <div class="toolbar">
        <div>
          <span class="eyebrow">DEPENDENCY MAP</span>
          <h2>系统依赖状态</h2>
        </div>
        <div>
          <el-tag :type="deps.core?.status === 'UP' ? 'success' : 'danger'"
            >核心 {{ deps.core?.status }}</el-tag
          ><el-button link @click="load">刷新</el-button>
        </div>
      </div>
      <el-descriptions :column="2" border
        ><el-descriptions-item
          v-for="(v, k) in deps.core?.details"
          :key="`core-${String(k)}`"
          :label="`核心 · ${String(k)}`"
          >{{ v }}</el-descriptions-item
        ><el-descriptions-item
          v-for="(v, k) in deps.models?.details"
          :key="`model-${String(k)}`"
          :label="`模型 · ${String(k)}`"
          >{{ v }}</el-descriptions-item
        ></el-descriptions
      ><el-alert
        v-if="deps.models?.status === 'DEGRADED'"
        type="warning"
        title="模型能力降级不会触发应用存活探针重启"
        :closable="false"
        class="spaced-top"
      /></section
  ></PageState>
</template>
