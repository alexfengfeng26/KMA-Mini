<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, unwrap, asRecord, errorMessage } from '../api/client'
import AppPagination from '../components/AppPagination.vue'
import PageState from '../components/PageState.vue'
import { getAuthorizedPage } from '../api/page'
import { useMutationAction } from '../composables/useMutationAction'

type StorageObject = Record<string, unknown>
type ReconcileSummary = Record<string, string | number | undefined>

const rows = ref<StorageObject[]>([]),
  summary = ref<ReconcileSummary>({}),
  status = ref(''),
  keyword = ref(''),
  loading = ref(true),
  error = ref('')
const page = ref(1),
  pageSize = ref(20),
  total = ref(0)
const mutation = useMutationAction()
async function load(reset = false) {
  if (reset) page.value = 1
  loading.value = true
  error.value = ''
  try {
    const result = await getAuthorizedPage<StorageObject>('/api/v1/admin/storage/objects/page', {
      pageNum: page.value,
      pageSize: pageSize.value,
      keyword: keyword.value,
      status: status.value || undefined,
      sortBy: 'updateTime',
      sortOrder: 'desc',
    })
    rows.value = result.list
    total.value = result.total
  } catch (e: unknown) {
    error.value = errorMessage(e, '无法读取对象台账')
  } finally {
    loading.value = false
  }
}
async function reconcile() {
  const result = await mutation.run(async () => {
    summary.value = asRecord(await unwrap(api.POST('/api/v1/admin/storage/reconcile'))) as ReconcileSummary
  }, '对象存储对账完成')
  if (result.ok) await load()
}
async function cleanup() {
  const result = await mutation.run(
    () => unwrap(api.POST('/api/v1/admin/storage/cleanup')),
    '已执行到期孤儿清理',
  )
  if (result.ok) await load()
}
onMounted(load)
</script>
<template>
  <section class="panel">
    <div class="toolbar">
      <div>
        <span class="eyebrow">OBJECT LIFECYCLE</span>
        <h2>存储台账与孤儿对账</h2>
      </div>
      <div>
        <el-input v-model="keyword" clearable placeholder="位置或异常" @keyup.enter="load(true)" />
        <el-select
          v-model="status"
          clearable
          placeholder="全部状态"
          class="control-width-sm"
          @change="load(true)"
          ><el-option
            v-for="item in ['active', 'orphan', 'missing', 'corrupt', 'delete_failed', 'deleted']"
            :key="item"
            :value="item" /></el-select
        ><el-button
          v-permission="'storage:reconcile'"
          :loading="mutation.pending.value"
          :disabled="mutation.pending.value"
          @click="reconcile"
          >立即对账</el-button
        ><el-button
          v-permission="'storage:cleanup'"
          type="danger"
          plain
          :loading="mutation.pending.value"
          :disabled="mutation.pending.value"
          @click="cleanup"
          >清理到期孤儿</el-button
        >
      </div>
    </div>
    <el-alert
      v-if="Object.keys(summary).length"
      type="info"
      :closable="false"
      :title="`对账结果：活跃 ${summary.active || 0}，孤儿 ${summary.orphan || 0}，缺失 ${summary.missing || 0}，损坏 ${summary.corrupt || 0}，新发现 ${summary.discovered || 0}`"
      class="spaced-bottom"
    /><PageState :loading="loading" :error="error" :empty="!rows.length"
      ><el-table :data="rows"
        ><el-table-column prop="object_id" label="对象" width="85" /><el-table-column
          prop="location"
          label="位置"
          min-width="300"
          show-overflow-tooltip /><el-table-column prop="status" label="状态" /><el-table-column
          prop="size_bytes"
          label="字节" /><el-table-column prop="checksum_algorithm" label="校验" /><el-table-column
          prop="reference_count"
          label="引用" /><el-table-column
          prop="last_reconciled_at"
          label="最近对账"
          min-width="170" /><el-table-column
          prop="delete_after"
          label="清理时间"
          min-width="170" /><el-table-column
          prop="error_message"
          label="异常"
          min-width="220"
          show-overflow-tooltip /></el-table
      ><AppPagination
        v-model:page="page"
        v-model:page-size="pageSize"
        :total="total"
        :disabled="loading"
        @change="load()"
    /></PageState>
  </section>
</template>
