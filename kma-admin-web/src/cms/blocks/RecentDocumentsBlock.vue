<script setup lang="ts">
import { computed } from 'vue'
import type { PortalHome } from '../../api/party'
import type { CmsBlockConfig } from '../../app/runtimeConfig'
import StatusTag from '../../components/StatusTag.vue'
import { useSiteNavigation } from '../../composables/useSiteNavigation'

const props = defineProps<{ config: CmsBlockConfig; data: PortalHome }>()
const { sitePath } = useSiteNavigation()
const items = computed(() => props.data.recent.slice(0, Number(props.config.props?.limit || 8)))
</script>

<template>
  <section class="portal-section cms-section">
    <div class="portal-section-title">
      <div>
        <span>最近更新</span>
        <h2>权威文件</h2>
      </div>
    </div>
    <div class="portal-document-list">
      <router-link
        v-for="item in items"
        :key="item.contentId"
        class="portal-document-link"
        :to="sitePath('/portal/content/:contentId', { contentId: Number(item.contentId) })"
      >
        <article>
          <div>
            <StatusTag :status="item.validityStatus" />
            <h3>{{ item.title }}</h3>
            <p>{{ item.summary || '打开查看解析正文和权威来源。' }}</p>
          </div>
          <dl>
            <dt>文号</dt>
            <dd>{{ item.documentNumber || '—' }}</dd>
            <dt>发文机关</dt>
            <dd>{{ item.issuingAuthority || '—' }}</dd>
            <dt>发布日期</dt>
            <dd>{{ item.publishDate || '—' }}</dd>
          </dl>
        </article>
      </router-link>
      <p v-if="!items.length" class="portal-empty">暂无已发布资料，请由内容管理员完成审核发布。</p>
    </div>
  </section>
</template>
