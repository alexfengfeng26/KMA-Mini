<script setup lang="ts">
import { computed } from 'vue'
import type { PortalHome } from '../../api/party'
import type { CmsBlockConfigV2 } from '../siteConfig'
import { blockDefinition } from '../blockDefinitions'
import { useSiteNavigation } from '../../composables/useSiteNavigation'
import { usePortalSiteStore } from '../../stores/portalSite'
import KmaMarkup from '../KmaMarkup.vue'

const props = defineProps<{
  config: CmsBlockConfigV2
  data: PortalHome
}>()
const { sitePath } = useSiteNavigation()
const portalSite = usePortalSiteStore()
const definition = computed(() => blockDefinition(props.config.type))
const title = computed(() => String(props.config.props?.title || definition.value?.title || '知识服务'))
const items = computed(() => {
  if (props.config.type.includes('question'))
    return ['如何快速找到现行制度？', '回答引用的资料从哪里查看？', '没有检索到答案时怎么办？']
  if (props.config.type === 'faq-list')
    return ['如何查找权威文件', '如何查看文件效力状态', '如何基于单篇材料提问']
  if (props.config.type === 'sop-steps') return ['确认适用范围', '查阅现行制度', '按流程执行', '留存处理记录']
  if (props.config.type === 'category-tree' || props.config.type === 'category-cards')
    return props.data.categories.slice(0, 6).map((item) => item.name)
  return props.data.recent.slice(0, 5).map((item) => item.title || '知识内容')
})
const pack = computed(() => portalSite.bootstrap?.theme.pack || 'party-authority')
const markup = computed(() =>
  String(
    props.config.props?.markdown ||
      `:::callout{tone="info"}\n内容由受控标签渲染，不执行 HTML 或脚本。\n:::\n\n:badge[已发布]{tone="success"} 可在设计中心编辑文案与样式。`,
  ),
)
</script>

<template>
  <section class="cms-generic-block" :class="`cms-generic-block--${pack}`">
    <header>
      <span>{{ definition?.category || '知识服务' }}</span>
      <h2>{{ title }}</h2>
    </header>
    <div v-if="config.type === 'ai-assistant'" class="cms-generic-assistant">
      <strong>基于已发布资料获得可追溯回答</strong>
      <p>{{ config.props?.description || '每条回答都附带来源，帮助快速核对原文。' }}</p>
      <router-link :to="sitePath('/portal/ask')">开始提问</router-link>
    </div>
    <ol v-else-if="config.type === 'sop-steps'" class="cms-step-list">
      <li v-for="(item, index) in items" :key="item">
        <span>{{ String(index + 1).padStart(2, '0') }}</span
        >{{ item }}
      </li>
    </ol>
    <KmaMarkup v-else-if="config.type === 'rich-text' || config.type === 'faq-list'" :source="markup" />
    <ul v-else class="cms-generic-list">
      <li v-for="item in items" :key="item">{{ item }}</li>
    </ul>
  </section>
</template>
