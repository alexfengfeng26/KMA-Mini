<script setup lang="ts">
import type { PortalHome } from '../../api/party'
import { useSiteNavigation } from '../../composables/useSiteNavigation'
defineProps<{ data: PortalHome }>()
const { sitePath } = useSiteNavigation()
</script>

<template>
  <section class="portal-topic-block cms-side-block">
    <div class="portal-section-title">
      <div>
        <span>当前专题</span>
        <h2>{{ data.topics[0]?.name || '专题学习' }}</h2>
      </div>
    </div>
    <div class="portal-topic-card">
      <p>{{ data.topics[0]?.description || '按专题聚合权威文件和学习材料。' }}</p>
      <router-link :to="sitePath('/portal/topics')">进入专题目录 →</router-link>
    </div>
  </section>
</template>
