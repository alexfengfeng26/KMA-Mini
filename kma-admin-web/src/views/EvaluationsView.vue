<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { api, unwrap, asList, asRecord, errorMessage } from '../api/client'
import type { components } from '../api/generated/schema'
import AppPagination from '../components/AppPagination.vue'
import PageState from '../components/PageState.vue'
import SpaceSelect from '../components/SpaceSelect.vue'
import { getAuthorizedPage } from '../api/page'
import { useMutationAction } from '../composables/useMutationAction'

type Gate = components['schemas']['EvaluationGateRequest']
interface EvaluationDataset {
  evaluation_dataset_id: number
  name: string
  case_count?: number
}
interface EvaluationRun {
  evaluation_run_id: number
  gate_passed?: boolean
}

const datasets = ref<EvaluationDataset[]>([]),
  selectedId = ref<number>(),
  runs = ref<EvaluationRun[]>([]),
  loading = ref(true),
  error = ref('')
const mutation = useMutationAction()
const page = ref(1),
  pageSize = ref(20),
  total = ref(0)
const createForm = reactive({ name: '党建知识基线', spaceCode: 'default', description: '' })
const caseForm = reactive({ question: '', expectedAnswer: '', expectedRefs: '', shouldRefuse: false })
const gate = reactive<Gate>({
  minRecallAtK: 0.8,
  minMrr: 0.6,
  minCitationPrecision: 0.8,
  minRefusalAccuracy: 0.9,
  minAnswerCorrectness: 0.7,
  minCaseCount: 1,
  enabled: true,
})

async function loadDatasets() {
  loading.value = true
  error.value = ''
  try {
    datasets.value = asList<EvaluationDataset>(await unwrap(api.GET('/api/v1/evaluations/datasets')))
    if (!selectedId.value && datasets.value.length) selectedId.value = datasets.value[0].evaluation_dataset_id
    if (selectedId.value) await loadDetail()
  } catch (e: unknown) {
    error.value = errorMessage(e, '无法读取评测中心')
  } finally {
    loading.value = false
  }
}
async function loadDetail() {
  if (!selectedId.value) return
  const [gateData, runData] = await Promise.all([
    unwrap(
      api.GET('/api/v1/evaluations/datasets/{datasetId}/gate', {
        params: { path: { datasetId: selectedId.value } },
      }),
    ),
    getAuthorizedPage<EvaluationRun>(`/api/v1/evaluations/datasets/${selectedId.value}/runs/page`, {
      pageNum: page.value,
      pageSize: pageSize.value,
      sortBy: 'startTime',
      sortOrder: 'desc',
    }),
  ])
  const source = asRecord(gateData)
  Object.assign(gate, {
    minRecallAtK: source.min_recall_at_k,
    minMrr: source.min_mrr,
    minCitationPrecision: source.min_citation_precision,
    minRefusalAccuracy: source.min_refusal_accuracy,
    minAnswerCorrectness: source.min_answer_correctness,
    minCaseCount: source.min_case_count,
    enabled: source.enabled,
  })
  runs.value = runData.list
  total.value = runData.total
}
async function createDataset() {
  const result = await mutation.run(
    () => unwrap(api.POST('/api/v1/evaluations/datasets', { body: createForm })),
    '评测集已创建',
  )
  if (!result.ok) return
  selectedId.value = result.value
  await loadDatasets()
}
async function addCase() {
  const datasetId = selectedId.value
  if (!datasetId) return
  const result = await mutation.run(
    () =>
      unwrap(
        api.POST('/api/v1/evaluations/datasets/{datasetId}/cases', {
          params: { path: { datasetId } },
          body: {
            question: caseForm.question,
            expectedAnswer: caseForm.expectedAnswer || undefined,
            expectedExternalRefs: caseForm.expectedRefs
              .split(',')
              .map((value) => value.trim())
              .filter(Boolean),
            shouldRefuse: caseForm.shouldRefuse,
          },
        }),
      ),
    '标准问答用例已加入',
  )
  if (!result.ok) return
  Object.assign(caseForm, { question: '', expectedAnswer: '', expectedRefs: '', shouldRefuse: false })
}
async function saveGate() {
  const datasetId = selectedId.value
  if (!datasetId) return
  await mutation.run(
    () =>
      unwrap(
        api.PUT('/api/v1/evaluations/datasets/{datasetId}/gate', {
          params: { path: { datasetId } },
          body: gate,
        }),
      ),
    '发布门禁阈值已保存',
  )
}
async function execute() {
  const datasetId = selectedId.value
  if (!datasetId) return
  const result = await mutation.run(
    () =>
      unwrap(
        api.POST('/api/v1/evaluations/datasets/{datasetId}/runs', {
          params: { path: { datasetId }, query: { topK: 10 } },
        }),
      ),
    '评测运行已完成',
  )
  if (result.ok) await loadDetail()
}
async function assertRelease(runId: number) {
  await mutation.run(
    () =>
      unwrap(
        api.POST('/api/v1/evaluations/runs/{runId}/assert-release', {
          params: { path: { runId } },
        }),
      ),
    '该评测运行满足发布门禁',
  )
}
onMounted(loadDatasets)
</script>

<template>
  <PageState :loading="loading" :error="error"
    ><section class="panel">
      <div class="toolbar">
        <div>
          <span class="eyebrow">QUALITY GATE</span>
          <h2>RAG 评测与发布门禁</h2>
        </div>
        <el-button
          v-permission="'evaluation:run'"
          :disabled="!selectedId || mutation.pending.value"
          :loading="mutation.pending.value"
          type="primary"
          @click="execute"
          >运行评测</el-button
        >
      </div>
      <el-form inline
        ><el-form-item label="评测集"
          ><el-select v-model="selectedId" class="control-width-lg" @change="loadDetail"
            ><el-option
              v-for="item in datasets"
              :key="item.evaluation_dataset_id"
              :label="`${item.name} · ${item.case_count} 例`"
              :value="item.evaluation_dataset_id" /></el-select></el-form-item
        ><el-form-item label="新建名称"><el-input v-model="createForm.name" /></el-form-item
        ><el-form-item label="空间"><SpaceSelect v-model="createForm.spaceCode" /></el-form-item
        ><el-button
          v-permission="'evaluation:create'"
          :loading="mutation.pending.value"
          :disabled="mutation.pending.value"
          @click="createDataset"
          >创建评测集</el-button
        ></el-form
      >
      <el-divider>标准问答用例</el-divider
      ><el-form inline
        ><el-form-item label="问题"
          ><el-input v-model="caseForm.question" class="control-width-xl" /></el-form-item
        ><el-form-item label="期望引用"
          ><el-input v-model="caseForm.expectedRefs" placeholder="externalRef,逗号分隔" /></el-form-item
        ><el-form-item label="期望答案"><el-input v-model="caseForm.expectedAnswer" /></el-form-item
        ><el-checkbox v-model="caseForm.shouldRefuse">应拒答</el-checkbox
        ><el-button
          v-permission="'evaluation:case:create'"
          :loading="mutation.pending.value"
          :disabled="!selectedId || !caseForm.question || mutation.pending.value"
          @click="addCase"
          >加入用例</el-button
        ></el-form
      >
      <el-divider>门禁阈值</el-divider
      ><el-form inline
        ><el-form-item label="Recall@K"
          ><el-input-number v-model="gate.minRecallAtK" :min="0" :max="1" :step="0.05" /></el-form-item
        ><el-form-item label="MRR"
          ><el-input-number v-model="gate.minMrr" :min="0" :max="1" :step="0.05" /></el-form-item
        ><el-form-item label="引用准确率"
          ><el-input-number
            v-model="gate.minCitationPrecision"
            :min="0"
            :max="1"
            :step="0.05" /></el-form-item
        ><el-form-item label="拒答率"
          ><el-input-number v-model="gate.minRefusalAccuracy" :min="0" :max="1" :step="0.05" /></el-form-item
        ><el-form-item label="答案正确率"
          ><el-input-number
            v-model="gate.minAnswerCorrectness"
            :min="0"
            :max="1"
            :step="0.05" /></el-form-item
        ><el-form-item label="最少用例"><el-input-number v-model="gate.minCaseCount" :min="1" /></el-form-item
        ><el-button
          v-permission="'evaluation:gate:update'"
          :loading="mutation.pending.value"
          :disabled="mutation.pending.value"
          @click="saveGate"
          >保存门禁</el-button
        ></el-form
      >
      <el-table :data="runs" class="spaced-top-lg"
        ><el-table-column prop="evaluation_run_id" label="运行" /><el-table-column
          prop="status"
          label="状态"
        /><el-table-column prop="top_k" label="Top K" /><el-table-column
          prop="gate_passed"
          label="门禁"
        /><el-table-column
          prop="metrics"
          label="指标"
          min-width="280"
          show-overflow-tooltip
        /><el-table-column
          prop="gate_failures"
          label="未通过项"
          min-width="220"
          show-overflow-tooltip
        /><el-table-column label="发布"
          ><template #default="{ row }"
            ><el-button
              v-permission="'evaluation:release:assert'"
              link
              type="primary"
              :disabled="!row.gate_passed || mutation.pending.value"
              @click="assertRelease(row.evaluation_run_id)"
              >验证可发布</el-button
            ></template
          ></el-table-column
        ></el-table
      >
      <AppPagination
        v-model:page="page"
        v-model:page-size="pageSize"
        :total="total"
        :disabled="loading"
        @change="loadDetail"
      /></section
  ></PageState>
</template>
