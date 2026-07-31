<script setup lang="ts">
import { computed, reactive, ref, watch, watchEffect } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { getAdminTopics, getPortalConfig, updatePortalConfig } from '../api/party'
import { useMutationAction } from '../composables/useMutationAction'
import PageHeader from '../components/PageHeader.vue'

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
const mutation = useMutationAction()

const topics = computed(() => topicsQuery.data.value || [])
const form = reactive({ unitName: '', helpText: '', currentTopicCode: '' })

watchEffect(() => {
  const config = configQuery.data.value
  if (config) Object.assign(form, config)
})

const currentTopic = computed(() => topics.value.find((t) => t.topicCode === form.currentTopicCode))

const helpTemplates = [
  '所有回答均应以已发布、有效且有权访问的材料为依据。',
  '本知识库内容仅供内部学习参考，请以正式文件为准。',
  'AI 生成的内容可能存在偏差，建议结合原文核对关键信息。',
]
function applyTemplate(text: string) {
  form.helpText = text
}

const isDefault = ref(false)
function resetDefault() {
  form.unitName = 'KMA 党建知识库'
  form.helpText = '所有回答均应以已发布、有效且有权访问的材料为依据。'
  form.currentTopicCode = ''
  isDefault.value = true
  setTimeout(() => (isDefault.value = false), 1200)
}

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

watch(
  () => form.unitName,
  () => {
    if (isDefault.value) ElMessage.info('已恢复默认配置，请确认后保存')
  },
)
</script>

<template>
  <section class="panel">
    <PageHeader
      eyebrow="PORTAL EXPERIENCE"
      title="门户配置"
      description="配置知识门户单位名称、当前专题和可信使用提示。"
    >
      <template #actions>
        <el-button @click="resetDefault">恢复默认</el-button>
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

    <div v-loading="configQuery.isPending.value || topicsQuery.isPending.value" class="portal-config-layout">
      <el-form class="portal-config-form" label-position="top">
        <el-form-item label="单位名称" required>
          <el-input v-model="form.unitName" maxlength="80" show-word-limit />
        </el-form-item>

        <el-form-item label="当前专题">
          <el-select v-model="form.currentTopicCode" clearable class="portal-topic-select">
            <el-option
              v-for="item in topics"
              :key="item.topicCode"
              :label="item.name"
              :value="item.topicCode"
            >
              <div class="topic-option">
                <span
                  class="topic-option-swatch"
                  :style="{ background: item.coverColor || '#2f7f76' }"
                  aria-hidden="true"
                ></span>
                <div class="topic-option-text">
                  <strong>{{ item.name }}</strong>
                  <small>{{ item.description || item.topicCode }}</small>
                </div>
              </div>
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="帮助与可信提示">
          <el-input v-model="form.helpText" type="textarea" :rows="5" maxlength="500" show-word-limit />
          <div class="help-templates">
            <span class="help-templates-label">常用模板：</span>
            <el-button
              v-for="(text, index) in helpTemplates"
              :key="index"
              size="small"
              text
              @click="applyTemplate(text)"
            >
              模板 {{ index + 1 }}
            </el-button>
          </div>
        </el-form-item>
      </el-form>

      <aside class="portal-preview-panel">
        <h3>门户预览</h3>
        <div class="portal-preview-card">
          <div class="portal-preview-header">
            <span class="portal-preview-mark">K</span>
            <div>
              <strong>{{ form.unitName || 'KMA 党建知识库' }}</strong>
              <small>KNOWLEDGE ARCHIVE</small>
            </div>
          </div>

          <div
            v-if="currentTopic"
            class="portal-preview-topic"
            :style="{ '--topic-color': currentTopic.coverColor || '#2f7f76' }"
          >
            <span class="portal-preview-topic-swatch" aria-hidden="true"></span>
            <div>
              <strong>{{ currentTopic.name }}</strong>
              <small>{{ currentTopic.description || '当前专题' }}</small>
            </div>
          </div>
          <div v-else class="portal-preview-topic portal-preview-topic--empty">未选择当前专题</div>

          <div class="portal-preview-tip">
            <span>提示：</span>
            <span>{{ form.helpText || '所有回答均应以已发布、有效且有权访问的材料为依据。' }}</span>
          </div>
        </div>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.portal-config-layout {
  display: grid;
  grid-template-columns: minmax(320px, 480px) 1fr;
  gap: 24px;
  align-items: start;
}
.portal-config-form {
  padding: 18px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: white;
}
.portal-topic-select {
  width: 100%;
}
.topic-option {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 0;
}
.topic-option-swatch {
  width: 16px;
  height: 16px;
  border-radius: 4px;
  flex: 0 0 auto;
}
.topic-option-text {
  display: grid;
  gap: 2px;
  min-width: 0;
}
.topic-option-text strong {
  font-size: 13px;
}
.topic-option-text small {
  color: var(--el-text-color-secondary);
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.help-templates {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
  font-size: 12px;
}
.help-templates-label {
  color: var(--el-text-color-secondary);
}
.portal-preview-panel {
  position: sticky;
  top: 16px;
}
.portal-preview-panel h3 {
  margin: 0 0 10px;
  font-size: 15px;
  color: var(--el-text-color-regular);
}
.portal-preview-card {
  display: grid;
  gap: 16px;
  padding: 20px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  background: white;
  box-shadow: 0 2px 8px oklch(20% 0.01 195 / 0.06);
}
.portal-preview-header {
  display: flex;
  align-items: center;
  gap: 10px;
}
.portal-preview-mark {
  display: inline-grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: var(--el-color-primary);
  color: white;
  font-weight: 700;
}
.portal-preview-header div {
  display: grid;
  gap: 2px;
}
.portal-preview-header strong {
  font-size: 17px;
}
.portal-preview-header small {
  color: var(--el-text-color-secondary);
  font-size: 10px;
  letter-spacing: 0.05em;
}
.portal-preview-topic {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
  border-left: 4px solid var(--topic-color, var(--el-color-primary));
}
.portal-preview-topic-swatch {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  background: var(--topic-color);
}
.portal-preview-topic div {
  display: grid;
  gap: 2px;
}
.portal-preview-topic strong {
  font-size: 15px;
}
.portal-preview-topic small {
  color: var(--el-text-color-secondary);
  font-size: 11px;
}
.portal-preview-topic--empty {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  border-left-color: var(--el-border-color);
}
.portal-preview-tip {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 10px 12px;
  border-radius: 6px;
  background: var(--el-color-info-light-9);
  color: var(--el-text-color-regular);
  font-size: 12px;
  line-height: 1.5;
}
.portal-preview-tip i {
  margin-top: 2px;
  color: var(--el-color-info);
}
@media (max-width: 900px) {
  .portal-config-layout {
    grid-template-columns: 1fr;
  }
  .portal-preview-panel {
    position: static;
  }
}
</style>
