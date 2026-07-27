<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { createTopic, getAdminTopics, updateTopic, type PortalTopic, type TopicRequest } from '../api/party'
import PageState from '../components/PageState.vue'
import AppPagination from '../components/AppPagination.vue'
import { useClientPagination } from '../components/listPagination'
import { useMutationAction } from '../composables/useMutationAction'

const queryClient = useQueryClient()
const topicsQuery = useQuery({
  queryKey: ['admin-topics'],
  queryFn: getAdminTopics,
  staleTime: 5 * 60_000,
})
const rows = computed(() => topicsQuery.data.value || [])
const dialog = ref(false)
const editing = ref<PortalTopic>()
const form = reactive<TopicRequest>({
  topicCode: '',
  name: '',
  description: '',
  coverColor: '#2f7f76',
  sortOrder: 0,
  enabled: true,
  featured: false,
})
const { page, pageSize, total, pagedItems, resetPage } = useClientPagination(rows)
const mutation = useMutationAction()

function open(value?: unknown) {
  const row = value as PortalTopic | undefined
  editing.value = row
  Object.assign(
    form,
    row
      ? {
          topicCode: row.topicCode,
          name: row.name,
          description: row.description,
          coverColor: row.coverColor || '#2f7f76',
          sortOrder: row.sortOrder || 0,
          enabled: row.enabled ?? true,
          featured: row.featured ?? false,
        }
      : {
          topicCode: '',
          name: '',
          description: '',
          coverColor: '#2f7f76',
          sortOrder: rows.value.length * 10,
          enabled: true,
          featured: false,
        },
  )
  dialog.value = true
}

async function save() {
  if (!form.topicCode.trim() || !form.name.trim()) return
  const result = await mutation.run(
    () => (editing.value?.topicId ? updateTopic(editing.value.topicId, form) : createTopic(form)),
    '专题已保存',
  )
  if (result.ok) {
    dialog.value = false
    resetPage()
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['admin-topics'] }),
      queryClient.invalidateQueries({ queryKey: ['portal-topics'] }),
      queryClient.invalidateQueries({ queryKey: ['portal-home'] }),
    ])
  }
}
</script>

<template>
  <section class="panel">
    <PageHeader
      eyebrow="CONTENT TAXONOMY"
      title="分类与专题"
      description="五个系统一级分类不可删除；可维护专题、推荐状态和展示顺序。"
    >
      <template #actions>
        <el-button v-permission="'topic:manage'" type="primary" @click="open()">新增专题</el-button>
      </template>
    </PageHeader>
    <PageState
      :loading="topicsQuery.isPending.value"
      :error="topicsQuery.error.value instanceof Error ? topicsQuery.error.value.message : ''"
      :empty="!rows.length"
    >
      <el-table :data="pagedItems">
        <el-table-column prop="name" label="专题" />
        <el-table-column prop="topicCode" label="编码" />
        <el-table-column prop="description" label="说明" min-width="260" />
        <el-table-column prop="sortOrder" label="排序" />
        <el-table-column label="推荐">
          <template #default="{ row }">
            <el-tag :type="row.featured ? 'success' : 'info'">{{ row.featured ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作">
          <template #default="{ row }">
            <el-button v-permission="'topic:manage'" link @click="open(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
      <AppPagination v-model:page="page" v-model:page-size="pageSize" :total="total" />
    </PageState>
  </section>
  <el-dialog v-model="dialog" :title="editing ? '编辑专题' : '新增专题'" width="560">
    <el-form label-position="top">
      <el-form-item label="专题编码" required>
        <el-input v-model="form.topicCode" :disabled="!!editing" maxlength="64" />
      </el-form-item>
      <el-form-item label="名称" required><el-input v-model="form.name" maxlength="128" /></el-form-item>
      <el-form-item label="说明">
        <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" />
      </el-form-item>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="排序"><el-input-number v-model="form.sortOrder" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="门户推荐">
            <el-checkbox v-model="form.featured">推荐展示</el-checkbox>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="dialog = false">取消</el-button>
      <el-button
        type="primary"
        :loading="mutation.pending.value"
        :disabled="!form.topicCode.trim() || !form.name.trim()"
        @click="save"
      >
        保存专题
      </el-button>
    </template>
  </el-dialog>
</template>
