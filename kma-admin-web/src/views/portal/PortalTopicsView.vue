<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'
import { getPortalTopics } from '../../api/party'
import PortalSystemPageFrame from '../../cms/v3/PortalSystemPageFrame.vue'
import PageState from '../../components/PageState.vue'
import { useSiteNavigation } from '../../composables/useSiteNavigation'

const router = useRouter()
const { sitePath } = useSiteNavigation()
const topicsQuery = useQuery({
  queryKey: ['portal-topics'],
  queryFn: getPortalTopics,
  staleTime: 5 * 60_000,
})
const topics = computed(() => topicsQuery.data.value || [])
</script>

<template>
  <PortalSystemPageFrame core-component="topic-directory">
    <section class="portal-page">
      <header class="portal-page-heading">
        <span class="portal-kicker">TOPICS</span>
        <h1>专题学习</h1>
        <p>围绕重点主题聚合权威文件、学习材料和基层实践案例。</p>
      </header>
      <PageState
        :loading="topicsQuery.isPending.value"
        :error="topicsQuery.error.value instanceof Error ? topicsQuery.error.value.message : ''"
        :empty="!topics.length"
      >
        <div class="portal-topic-grid">
          <article v-for="(topic, index) in topics" :key="topic.topicCode">
            <span>{{ String(index + 1).padStart(2, '0') }}</span>
            <h2>{{ topic.name }}</h2>
            <p>{{ topic.description || '专题资料持续更新中。' }}</p>
            <button
              @click="
                router.push({ path: sitePath('/portal/library'), query: { topicCode: topic.topicCode } })
              "
            >
              浏览专题资料 →
            </button>
          </article>
        </div>
      </PageState>
    </section>
  </PortalSystemPageFrame>
</template>
