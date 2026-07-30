<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { api, unwrap } from '../api/client'
import AppPagination from '../components/AppPagination.vue'
import { useClientPagination } from '../components/listPagination'
import SpaceSelect from '../components/SpaceSelect.vue'
import type { components } from '../api/generated/schema'

type RetrieveDebugResult = components['schemas']['RetrieveDebugResult']
type ChunkHit = components['schemas']['ChunkHitVO']

const form = reactive({ spaceCode: 'default', query: '', topK: 8 }),
  loading = ref(false),
  result = ref<RetrieveDebugResult>()
const hits = computed<ChunkHit[]>(() => result.value?.finalHits || [])
const { page, pageSize, total, pagedItems, resetPage } = useClientPagination(hits)
async function run() {
  loading.value = true
  try {
    result.value = await unwrap(api.POST('/api/v1/retrieval/debug', { body: form }))
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
    <el-form class="retrieval-form" label-position="top"
      ><el-row :gutter="16"
        ><el-col :xs="24" :sm="6"
          ><el-form-item label="知识空间"><SpaceSelect v-model="form.spaceCode" /></el-form-item></el-col
        ><el-col :xs="24" :sm="14"
          ><el-form-item label="检索问题"
            ><el-input v-model="form.query" @keyup.enter="run" /></el-form-item></el-col
        ><el-col :xs="24" :sm="4"
          ><el-form-item label="候选数"
            ><el-input-number
              v-model="form.topK"
              :min="1"
              :max="100"
              class="full-width" /></el-form-item></el-col></el-row
      ><el-button type="primary" :loading="loading" @click="run">运行混合检索</el-button></el-form
    ><el-table v-if="result?.finalHits" :data="pagedItems" class="spaced-top-lg"
      ><el-table-column prop="docTitle" label="来源" /><el-table-column
        prop="content"
        label="命中内容"
        min-width="360"
        show-overflow-tooltip /><el-table-column prop="vectorScore" label="向量" /><el-table-column
        prop="fullTextScore"
        label="全文" /><el-table-column prop="rrfScore" label="RRF" /><el-table-column
        prop="rerankScore"
        label="重排" /></el-table
    ><AppPagination
      v-if="result?.finalHits"
      v-model:page="page"
      v-model:page-size="pageSize"
      :total="total"
    />
  </div>
</template>
