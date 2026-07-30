<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, asRecord, errorMessage, unwrap } from '../api/client'
import AppPagination from '../components/AppPagination.vue'
import PageState from '../components/PageState.vue'
import SpaceSelect from '../components/SpaceSelect.vue'
import { readServerPage } from '../components/listPagination'
import { useMutationAction } from '../composables/useMutationAction'
import { taskStatusMeta } from '../domain/systemCatalog'

interface TaskRow extends Record<string, unknown> {
  taskId: number
  errorMessage?: string
}

const loading = ref(true),
  rows = ref<TaskRow[]>([]),
  error = ref(''),
  selected = ref<TaskRow>(),
  selection = ref<TaskRow[]>([]),
  detailVisible = ref(false),
  stats = ref<Record<string, number>>({})
const page = ref(1),
  pageSize = ref(10),
  total = ref(0)
const filters = reactive({ status: '', spaceCode: '', sourceType: '' })
const failed = computed(() => Number(stats.value.failed || 0) + Number(stats.value.dead || 0))
const mutation = useMutationAction()
async function load(reset = false) {
  if (reset) page.value = 1
  loading.value = true
  error.value = ''
  try {
    const [list, summary] = await Promise.all([
      unwrap(
        api.GET('/api/v1/tasks', {
          params: {
            query: {
              pageNum: page.value,
              pageSize: pageSize.value,
              status: filters.status || undefined,
              spaceCode: filters.spaceCode || undefined,
              sourceType: filters.sourceType || undefined,
            },
          },
        }),
      ),
      unwrap(api.GET('/api/v1/tasks/stats')),
    ])
    const result = readServerPage<TaskRow>(list, page.value, pageSize.value)
    rows.value = result.items
    total.value = result.total
    stats.value = asRecord(summary) as Record<string, number>
  } catch (e: unknown) {
    error.value = errorMessage(e, '无法读取任务')
  } finally {
    loading.value = false
  }
}
async function retry(id: number) {
  const result = await mutation.run(
    () => unwrap(api.POST('/api/v1/tasks/{taskId}/retry', { params: { path: { taskId: id } } })),
    `任务 ${id} 已重新执行`,
  )
  if (result.ok) await load()
  return result.ok
}
async function retrySelected() {
  const count = selection.value.length
  const result = await mutation.run(async () => {
    for (const row of selection.value)
      await unwrap(api.POST('/api/v1/tasks/{taskId}/retry', { params: { path: { taskId: row.taskId } } }))
  }, `已重试 ${count} 个任务`)
  if (result.ok) await load()
}
function showDetail(value: unknown) {
  const row = value as TaskRow
  selected.value = row
  detailVisible.value = true
}
async function retryCurrent() {
  if (!selected.value) return
  if (await retry(selected.value.taskId)) detailVisible.value = false
}
onMounted(load)
</script>
<template>
  <section class="panel">
    <div class="toolbar">
      <div>
        <span class="eyebrow">RETRY & DEAD LETTER</span>
        <h2>投喂任务</h2>
      </div>
      <div>
        <el-tag :type="failed ? 'danger' : 'success'">失败/死信 {{ failed }}</el-tag
        ><el-button link @click="load()">刷新</el-button>
      </div>
    </div>
    <div class="metric-strip">
      <span v-for="(count, status) in stats" :key="status"
        ><small>{{ taskStatusMeta(String(status)).label }}</small
        ><strong>{{ count }}</strong></span
      >
    </div>
    <div class="filter-bar">
      <el-select v-model="filters.status" placeholder="状态" clearable
        ><el-option label="等待" value="pending" /><el-option label="处理中" value="processing" /><el-option
          label="成功"
          value="success" /><el-option label="失败" value="failed" /><el-option
          label="死信"
          value="dead" /></el-select
      ><SpaceSelect v-model="filters.spaceCode" placeholder="空间编码" clearable /><el-input
        v-model="filters.sourceType"
        placeholder="来源类型"
        clearable
      /><el-button @click="load(true)">查询</el-button
      ><el-button
        v-permission="'task:retry'"
        type="primary"
        :loading="mutation.pending.value"
        :disabled="!selection.length || mutation.pending.value"
        @click="retrySelected"
        >批量重试</el-button
      >
    </div>
    <PageState :loading="loading" :error="error" :empty="!rows.length"
      ><el-table :data="rows" @selection-change="selection = $event" @row-dblclick="showDetail"
        ><el-table-column type="selection" width="44" /><el-table-column
          prop="taskId"
          label="任务"
          width="80"
        /><el-table-column prop="sourceType" label="来源" /><el-table-column
          prop="spaceCode"
          label="空间"
        /><el-table-column label="状态"
          ><template #default="scope"
            ><el-tag :type="taskStatusMeta(scope.row.status).type">{{
              taskStatusMeta(scope.row.status).label
            }}</el-tag></template
          ></el-table-column
        ><el-table-column label="重试"
          ><template #default="s">{{ s.row.retryCount }}/{{ s.row.maxRetry }}</template></el-table-column
        ><el-table-column prop="nextExecuteTime" label="下次执行" min-width="170" /><el-table-column
          prop="errorMessage"
          label="错误"
          min-width="240"
          show-overflow-tooltip
        /><el-table-column label="操作" width="160"
          ><template #default="s"
            ><el-button link @click="showDetail(s.row)">详情</el-button
            ><el-button
              v-permission="'task:retry'"
              link
              type="primary"
              :disabled="mutation.pending.value"
              @click="retry(s.row.taskId)"
              >重试</el-button
            ></template
          ></el-table-column
        ></el-table
      ><AppPagination
        v-model:page="page"
        v-model:page-size="pageSize"
        :total="total"
        :disabled="loading"
        @change="load()"
    /></PageState>
  </section>
  <el-dialog v-model="detailVisible" title="任务详情与负载快照" width="760"
    ><el-alert
      v-if="selected?.errorMessage"
      type="error"
      :title="selected.errorMessage"
      :closable="false"
    /><el-descriptions v-if="selected" :column="2" border class="spaced-top"
      ><el-descriptions-item v-for="(value, key) in selected" :key="String(key)" :label="String(key)">
        <pre v-if="typeof value === 'object' || key === 'meta'">{{
          typeof value === 'string' ? value : JSON.stringify(value, null, 2)
        }}</pre>
        <span v-else>{{ value }}</span></el-descriptions-item
      ></el-descriptions
    ><template #footer
      ><el-button @click="detailVisible = false">关闭</el-button
      ><el-button
        v-permission="'task:retry'"
        type="primary"
        :loading="mutation.pending.value"
        :disabled="!selected || mutation.pending.value"
        @click="retryCurrent"
        >重试任务</el-button
      ></template
    ></el-dialog
  >
</template>
