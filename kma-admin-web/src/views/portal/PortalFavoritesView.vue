<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'
import {
  getPortalFavorites,
  getPortalHistory,
  removePortalFavorite,
  type PortalListItem,
} from '../../api/party'
import { errorMessage } from '../../api/client'
import PortalSystemPageFrame from '../../cms/v3/PortalSystemPageFrame.vue'
import PageState from '../../components/PageState.vue'
import { useMutationAction } from '../../composables/useMutationAction'
import { useSiteNavigation } from '../../composables/useSiteNavigation'

const router = useRouter()
const { sitePath } = useSiteNavigation()
const queryClient = useQueryClient()
const tab = ref<'favorites' | 'history'>('favorites')
const favoritesQuery = useQuery({
  queryKey: ['portal-favorites'],
  queryFn: () => getPortalFavorites(100),
})
const historyQuery = useQuery({
  queryKey: ['portal-history'],
  queryFn: () => getPortalHistory(100),
})
const favorites = computed(() => favoritesQuery.data.value || [])
const history = computed(() => historyQuery.data.value || [])
const activeError = computed(() => {
  const value = tab.value === 'favorites' ? favoritesQuery.error.value : historyQuery.error.value
  return value ? errorMessage(value, '无法读取个人资料') : ''
})
const mutation = useMutationAction()

async function remove(item: PortalListItem) {
  if (!item.favoriteId) return
  const result = await mutation.run(() => removePortalFavorite(item.favoriteId as number), '已取消收藏')
  if (result.ok) {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['portal-favorites'] }),
      queryClient.invalidateQueries({ queryKey: ['portal-home'] }),
    ])
  }
}
</script>

<template>
  <PortalSystemPageFrame core-component="favorite-list">
    <section class="portal-page">
      <header class="portal-page-heading">
        <span class="portal-kicker">MY KNOWLEDGE</span>
        <h1>收藏与阅读历史</h1>
        <p>集中查看收藏文件、收藏问答和最近阅读记录。</p>
      </header>
      <div class="portal-tabs" role="tablist" aria-label="收藏与阅读历史">
        <button
          role="tab"
          :aria-selected="tab === 'favorites'"
          :class="{ active: tab === 'favorites' }"
          @click="tab = 'favorites'"
        >
          我的收藏 {{ favorites.length }}
        </button>
        <button
          role="tab"
          :aria-selected="tab === 'history'"
          :class="{ active: tab === 'history' }"
          @click="tab = 'history'"
        >
          最近阅读 {{ history.length }}
        </button>
      </div>
      <PageState
        :loading="tab === 'favorites' ? favoritesQuery.isPending.value : historyQuery.isPending.value"
        :error="activeError"
        :empty="tab === 'favorites' ? !favorites.length : !history.length"
      >
        <div v-if="tab === 'favorites'" class="portal-personal-list">
          <article v-for="item in favorites" :key="item.favoriteId">
            <div>
              <span>{{ item.favoriteType === 'content' ? '收藏文件' : '收藏问答' }}</span>
              <h2>{{ item.title }}</h2>
              <p>{{ item.documentNumber || '' }} {{ item.issuingAuthority || '' }}</p>
            </div>
            <button
              v-if="item.docId"
              @click="router.push(sitePath('/portal/content/:contentId', { contentId: Number(item.docId) }))"
            >
              打开
            </button>
            <button class="quiet" :disabled="mutation.pending.value" @click="remove(item)">取消收藏</button>
          </article>
        </div>
        <div v-else class="portal-personal-list">
          <article v-for="item in history" :key="item.docId">
            <div>
              <span>阅读 {{ item.readCount || 0 }} 次</span>
              <h2>{{ item.title }}</h2>
              <p>{{ item.documentNumber || '无文号' }} · 最近阅读 {{ item.lastReadAt }}</p>
            </div>
            <button
              @click="router.push(sitePath('/portal/content/:contentId', { contentId: Number(item.docId) }))"
            >
              继续阅读
            </button>
          </article>
        </div>
      </PageState>
    </section>
  </PortalSystemPageFrame>
</template>
