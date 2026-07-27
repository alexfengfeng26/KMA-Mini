<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, unwrap, errorMessage } from '../api/client'
import { getAuthorizedPage } from '../api/page'
import AppPagination from '../components/AppPagination.vue'
import PageState from '../components/PageState.vue'
import { readServerPage } from '../components/listPagination'
import { useAuthStore } from '../stores/auth'

interface CallLogRow extends Record<string, unknown> {
  logId?: number
}

type SecurityAuditRow = Record<string, unknown>

const auth = useAuthStore()
const calls = ref<CallLogRow[]>([]),
  security = ref<SecurityAuditRow[]>([]),
  loading = ref(true),
  error = ref(''),
  tab = ref('calls')
const callPage = ref(1),
  callPageSize = ref(10),
  callTotal = ref(0)
const securityPage = ref(1),
  securityPageSize = ref(20),
  securityTotal = ref(0)
const detailVisible = ref(false),
  detailLoading = ref(false),
  selected = ref<Record<string, unknown>>()

async function load(reset = false) {
  if (reset) {
    callPage.value = 1
    securityPage.value = 1
  }
  loading.value = true
  error.value = ''
  try {
    const callData = auth.hasAnyPermission(['audit:call:read'])
      ? await unwrap(
          api.GET('/api/v1/call-logs', {
            params: { query: { pageNum: callPage.value, pageSize: callPageSize.value } },
          }),
        )
      : undefined
    const securityData = auth.hasAnyPermission(['audit:security:read'])
      ? await getAuthorizedPage<SecurityAuditRow>('/api/v1/security-audits/page', {
          pageNum: securityPage.value,
          pageSize: securityPageSize.value,
          sortBy: 'createTime',
          sortOrder: 'desc',
        })
      : undefined
    const callResult = readServerPage<CallLogRow>(callData, callPage.value, callPageSize.value)
    calls.value = callResult.items
    callTotal.value = callResult.total
    security.value = securityData?.list || []
    securityTotal.value = securityData?.total || 0
    if (!auth.hasAnyPermission([tab.value === 'calls' ? 'audit:call:read' : 'audit:security:read'])) {
      tab.value = auth.hasAnyPermission(['audit:call:read']) ? 'calls' : 'security'
    }
  } catch (e: unknown) {
    error.value = errorMessage(e, '无法读取审计日志')
  } finally {
    loading.value = false
  }
}

async function showDetail(row: CallLogRow) {
  if (!row.logId) return
  detailVisible.value = true
  detailLoading.value = true
  try {
    selected.value = (await unwrap(
      api.GET('/api/v1/call-logs/{logId}', { params: { path: { logId: row.logId } } }),
    )) as Record<string, unknown>
  } finally {
    detailLoading.value = false
  }
}
onMounted(load)
</script>

<template>
  <section class="panel">
    <div class="toolbar">
      <div>
        <span class="eyebrow">AUDIT TRAIL</span>
        <h2>调用与内容安全审计</h2>
      </div>
      <el-button link @click="load(true)">刷新</el-button>
    </div>
    <el-tabs v-model="tab"
      ><el-tab-pane v-if="auth.hasAnyPermission(['audit:call:read'])" label="问答调用" name="calls"
        ><PageState :loading="loading" :error="error" :empty="!calls.length"
          ><el-table :data="calls" @row-dblclick="showDetail"
            ><el-table-column prop="createTime" label="时间" /><el-table-column
              prop="username"
              label="用户"
            /><el-table-column prop="spaceCode" label="空间" /><el-table-column
              prop="query"
              label="脱敏问题"
              min-width="260"
              show-overflow-tooltip
            /><el-table-column prop="securityFlags" label="安全标记" /><el-table-column
              prop="llmModel"
              label="模型"
            /><el-table-column prop="status" label="状态" /><el-table-column label="操作" width="80"
              ><template #default="{ row }"
                ><el-button link @click="showDetail(row)">详情</el-button></template
              ></el-table-column
            ></el-table
          ><AppPagination
            v-model:page="callPage"
            v-model:page-size="callPageSize"
            :total="callTotal"
            :disabled="loading"
            @change="load()" /></PageState
      ></el-tab-pane>
      <el-tab-pane v-if="auth.hasAnyPermission(['audit:security:read'])" label="安全事件" name="security"
        ><PageState :loading="loading" :error="error" :empty="!security.length"
          ><el-table :data="security"
            ><el-table-column prop="create_time" label="时间" /><el-table-column
              prop="severity"
              label="级别" /><el-table-column prop="event_type" label="事件" /><el-table-column
              prop="username"
              label="用户" /><el-table-column prop="action" label="动作" /><el-table-column
              prop="resource"
              label="资源" /><el-table-column prop="flags" label="标记" min-width="220" /></el-table
          ><AppPagination
            v-model:page="securityPage"
            v-model:page-size="securityPageSize"
            :total="securityTotal"
            :disabled="loading"
            @change="load()" /></PageState></el-tab-pane
    ></el-tabs>
  </section>
  <el-dialog v-model="detailVisible" title="问答调用详情" width="760"
    ><PageState :loading="detailLoading" :empty="!selected"
      ><el-descriptions v-if="selected" :column="2" border
        ><el-descriptions-item v-for="(value, key) in selected" :key="String(key)" :label="String(key)">
          <pre v-if="typeof value === 'object'">{{ JSON.stringify(value, null, 2) }}</pre>
          <span v-else>{{ value }}</span></el-descriptions-item
        ></el-descriptions
      ></PageState
    ></el-dialog
  >
</template>
