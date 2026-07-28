<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, authorizedJson, unwrap, errorMessage } from '../api/client'
import { getAuthorizedPage } from '../api/page'
import AppPagination from '../components/AppPagination.vue'
import PageState from '../components/PageState.vue'
import { readServerPage } from '../components/listPagination'
import { useAuthStore } from '../stores/auth'
import { openAuthorizedFile } from '../api/download'

interface CallLogRow extends Record<string, unknown> {
  logId?: number
}

type SecurityAuditRow = Record<string, unknown>

const auth = useAuthStore()
const calls = ref<CallLogRow[]>([]),
  security = ref<SecurityAuditRow[]>([]),
  governance = ref<SecurityAuditRow[]>([]),
  governanceSummary = ref<Record<string, unknown>>({}),
  loading = ref(true),
  error = ref(''),
  tab = ref('calls')
const callPage = ref(1),
  callPageSize = ref(10),
  callTotal = ref(0)
const securityPage = ref(1),
  securityPageSize = ref(20),
  securityTotal = ref(0)
const governancePage = ref(1),
  governancePageSize = ref(20),
  governanceTotal = ref(0)
const governanceFilters = reactive({
  username: '',
  resource: '',
  eventType: '',
  action: '',
  from: '',
  to: '',
})
const detailVisible = ref(false),
  detailLoading = ref(false),
  selected = ref<Record<string, unknown>>()
const governanceSummaryByType = computed(() => {
  const source = governanceSummary.value.byType
  return Array.isArray(source)
    ? source.filter(
        (item): item is { eventType?: string; total?: number } => !!item && typeof item === 'object',
      )
    : []
})

async function load(reset = false) {
  if (reset) {
    callPage.value = 1
    securityPage.value = 1
    governancePage.value = 1
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
    const governanceData = auth.hasAnyPermission(['audit:security:read'])
      ? await getAuthorizedPage<SecurityAuditRow>('/api/v1/security-audits/governance', {
          pageNum: governancePage.value,
          pageSize: governancePageSize.value,
          username: governanceFilters.username || undefined,
          resource: governanceFilters.resource || undefined,
          eventType: governanceFilters.eventType || undefined,
          action: governanceFilters.action || undefined,
          from: governanceFilters.from || undefined,
          to: governanceFilters.to || undefined,
        })
      : undefined
    const governanceOverview = auth.hasAnyPermission(['audit:security:read'])
      ? await authorizedJson<Record<string, unknown>>('/api/v1/security-audits/governance/summary')
      : undefined
    const callResult = readServerPage<CallLogRow>(callData, callPage.value, callPageSize.value)
    calls.value = callResult.items
    callTotal.value = callResult.total
    security.value = securityData?.list || []
    securityTotal.value = securityData?.total || 0
    governance.value = governanceData?.list || []
    governanceTotal.value = governanceData?.total || 0
    governanceSummary.value = (governanceOverview || {}) as Record<string, unknown>
    if (!auth.hasAnyPermission([tab.value === 'calls' ? 'audit:call:read' : 'audit:security:read'])) {
      tab.value = auth.hasAnyPermission(['audit:call:read']) ? 'calls' : 'security'
    }
  } catch (e: unknown) {
    error.value = errorMessage(e, '无法读取审计日志')
  } finally {
    loading.value = false
  }
}

async function exportGovernance() {
  const query = new URLSearchParams({
    username: governanceFilters.username,
    resource: governanceFilters.resource,
    eventType: governanceFilters.eventType,
    action: governanceFilters.action,
    from: governanceFilters.from,
    to: governanceFilters.to,
  })
  await openAuthorizedFile(`/api/v1/security-audits/governance/export?${query.toString()}`)
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
        <h2>调用、安全与授权治理审计</h2>
      </div>
      <el-button link @click="load(true)">刷新</el-button>
    </div>
    <el-tabs v-model="tab">
      <el-tab-pane v-if="auth.hasAnyPermission(['audit:call:read'])" label="问答调用" name="calls">
        <PageState :loading="loading" :error="error" :empty="!calls.length">
          <el-table :data="calls" @row-dblclick="showDetail">
            <el-table-column prop="createTime" label="时间" />
            <el-table-column prop="username" label="用户" />
            <el-table-column prop="spaceCode" label="空间" />
            <el-table-column prop="query" label="脱敏问题" min-width="260" show-overflow-tooltip />
            <el-table-column prop="securityFlags" label="安全标记" />
            <el-table-column prop="llmModel" label="模型" />
            <el-table-column prop="status" label="状态" />
            <el-table-column label="操作" width="80"
              ><template #default="{ row }"
                ><el-button link @click="showDetail(row)">详情</el-button></template
              ></el-table-column
            >
          </el-table>
          <AppPagination
            v-model:page="callPage"
            v-model:page-size="callPageSize"
            :total="callTotal"
            :disabled="loading"
            @change="load()"
          />
        </PageState>
      </el-tab-pane>
      <el-tab-pane v-if="auth.hasAnyPermission(['audit:security:read'])" label="安全事件" name="security">
        <PageState :loading="loading" :error="error" :empty="!security.length">
          <el-table :data="security">
            <el-table-column prop="create_time" label="时间" /><el-table-column
              prop="severity"
              label="级别"
            />
            <el-table-column prop="event_type" label="事件" /><el-table-column prop="username" label="用户" />
            <el-table-column prop="action" label="动作" /><el-table-column prop="resource" label="资源" />
            <el-table-column prop="flags" label="标记" min-width="220" />
          </el-table>
          <AppPagination
            v-model:page="securityPage"
            v-model:page-size="securityPageSize"
            :total="securityTotal"
            :disabled="loading"
            @change="load()"
          />
        </PageState>
      </el-tab-pane>
      <el-tab-pane v-if="auth.hasAnyPermission(['audit:security:read'])" label="授权治理" name="governance">
        <div class="filter-bar">
          <el-input v-model="governanceFilters.username" placeholder="操作者" clearable />
          <el-input v-model="governanceFilters.resource" placeholder="资源，例如 user:1" clearable />
          <el-input v-model="governanceFilters.action" placeholder="操作，例如 role.update" clearable />
          <el-select v-model="governanceFilters.eventType" placeholder="全部事件" clearable>
            <el-option label="身份变更" value="identity_change" /><el-option
              label="角色变更"
              value="role_change"
            />
            <el-option label="组织/ACL 变更" value="space_authorization_change" /><el-option
              label="主题发布"
              value="portal_theme"
            />
          </el-select>
          <input
            v-model="governanceFilters.from"
            class="native-field"
            type="datetime-local"
            aria-label="开始时间"
          />
          <input
            v-model="governanceFilters.to"
            class="native-field"
            type="datetime-local"
            aria-label="结束时间"
          />
          <el-button @click="load(true)">查询</el-button
          ><el-button plain @click="exportGovernance">导出 CSV</el-button>
        </div>
        <div v-if="governanceSummaryByType.length" class="muted">
          近 {{ governanceSummary.days || 30 }} 天：
          <el-tag v-for="item in governanceSummaryByType" :key="item.eventType" class="summary-tag"
            >{{ item.eventType }} {{ item.total }}</el-tag
          >
        </div>
        <PageState :loading="loading" :error="error" :empty="!governance.length">
          <el-table :data="governance">
            <el-table-column prop="createTime" label="时间" min-width="165" /><el-table-column
              prop="username"
              label="操作者"
              width="120"
            />
            <el-table-column prop="eventType" label="事件" width="180" /><el-table-column
              prop="action"
              label="动作"
              min-width="200"
            />
            <el-table-column prop="resource" label="资源" min-width="180" /><el-table-column
              prop="severity"
              label="级别"
              width="95"
            />
            <el-table-column label="差异" width="100"
              ><template #default="{ row }"
                ><el-popover width="520" trigger="click"
                  ><template #reference><el-button link>查看</el-button></template
                  ><strong>变更前</strong>
                  <pre>{{ JSON.stringify(row.beforeState, null, 2) }}</pre>
                  <strong>变更后</strong>
                  <pre>{{ JSON.stringify(row.afterState, null, 2) }}</pre>
                  <strong>说明</strong>
                  <pre>{{ JSON.stringify(row.details, null, 2) }}</pre>
                </el-popover></template
              ></el-table-column
            >
          </el-table>
          <AppPagination
            v-model:page="governancePage"
            v-model:page-size="governancePageSize"
            :total="governanceTotal"
            :disabled="loading"
            @change="load()"
          />
        </PageState>
      </el-tab-pane>
    </el-tabs>
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

<style scoped>
.summary-tag {
  margin: 0 6px 10px 0;
}
</style>
