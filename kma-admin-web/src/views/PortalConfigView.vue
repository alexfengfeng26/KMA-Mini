<script setup lang="ts">
import { computed, reactive, watchEffect } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { getAdminTopics, getPortalConfig, updatePortalConfig } from '../api/party'
import { useMutationAction } from '../composables/useMutationAction'

const queryClient = useQueryClient()
const configQuery = useQuery({
  queryKey: ['admin-portal-config'],
  queryFn: getPortalConfig,
  staleTime: 5 * 60_000,
})
const topicsQuery = useQuery({
  queryKey: ['admin-topics'],
  queryFn: getAdminTopics,
  staleTime: 5 * 60_000,
})
const topics = computed(() => topicsQuery.data.value || [])
const form = reactive({ unitName: '', helpText: '', currentTopicCode: '' })
const mutation = useMutationAction()

watchEffect(() => {
  const config = configQuery.data.value
  if (config) Object.assign(form, config)
})

async function save() {
  const result = await mutation.run(
    () =>
      updatePortalConfig({
        unitName: form.unitName.trim(),
        helpText: form.helpText.trim() || undefined,
        currentTopicCode: form.currentTopicCode || undefined,
      }),
    '门户配置已更新',
  )
  if (result.ok) {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['admin-portal-config'] }),
      queryClient.invalidateQueries({ queryKey: ['portal-home'] }),
    ])
  }
}
</script>

<template>
  <section class="panel">
    <PageHeader
      eyebrow="PORTAL EXPERIENCE"
      title="门户配置"
      description="配置知识门户单位名称、当前专题和可信使用提示。"
    >
      <template #actions>
        <el-button
          v-permission="'portal:configure'"
          type="primary"
          :loading="mutation.pending.value"
          :disabled="!form.unitName.trim()"
          @click="save"
        >
          保存配置
        </el-button>
      </template>
    </PageHeader>
    <el-form
      v-loading="configQuery.isPending.value || topicsQuery.isPending.value"
      class="form-narrow"
      label-position="top"
    >
      <el-form-item label="单位名称" required>
        <el-input v-model="form.unitName" maxlength="80" show-word-limit />
      </el-form-item>
      <el-form-item label="当前专题">
        <el-select v-model="form.currentTopicCode" clearable>
          <el-option
            v-for="item in topics"
            :key="item.topicCode"
            :label="item.name"
            :value="item.topicCode"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="帮助与可信提示">
        <el-input v-model="form.helpText" type="textarea" :rows="5" maxlength="500" show-word-limit />
      </el-form-item>
    </el-form>
  </section>
</template>
