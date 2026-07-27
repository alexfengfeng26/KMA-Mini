<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useRoute, useRouter, type LocationQueryRaw } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  addPortalFavorite,
  getPortalContents,
  getPortalTopics,
  type PartyContent,
  type PortalContentQuery,
} from '../../api/party'
import { errorMessage } from '../../api/client'
import PortalSystemPageFrame from '../../cms/v3/PortalSystemPageFrame.vue'
import AppPagination from '../../components/AppPagination.vue'
import PageState from '../../components/PageState.vue'
import StatusTag from '../../components/StatusTag.vue'
import { categoryLabel, PARTY_CONTENT_CATEGORIES } from '../../domain/partyKnowledge'
import { useSiteNavigation } from '../../composables/useSiteNavigation'

interface LibraryFilters {
  keyword: string
  contentType: string
  topicCode: string
  issuingAuthority: string
  validityStatus: string
  spaceCode: string
  publishDateFrom: string
  publishDateTo: string
  includeHistorical: boolean
}

const route = useRoute()
const router = useRouter()
const { sitePath } = useSiteNavigation()
const loading = ref(false)
const error = ref('')
const rows = ref<PartyContent[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const favoritePending = ref(new Set<number>())
let controller: AbortController | undefined

const filters = reactive<LibraryFilters>({
  keyword: '',
  contentType: '',
  topicCode: '',
  issuingAuthority: '',
  validityStatus: '',
  spaceCode: '',
  publishDateFrom: '',
  publishDateTo: '',
  includeHistorical: false,
})
const topicsQuery = useQuery({
  queryKey: ['portal-topics'],
  queryFn: getPortalTopics,
  staleTime: 5 * 60_000,
})
const topics = computed(() => topicsQuery.data.value || [])

function queryValue(name: string) {
  const value = route.query[name]
  return Array.isArray(value) ? String(value[0] || '') : String(value || '')
}

function applyRouteState() {
  filters.keyword = queryValue('keyword')
  filters.contentType = queryValue('contentType')
  filters.topicCode = queryValue('topicCode')
  filters.issuingAuthority = queryValue('issuingAuthority')
  filters.validityStatus = queryValue('validityStatus')
  filters.spaceCode = queryValue('spaceCode')
  filters.publishDateFrom = queryValue('publishDateFrom')
  filters.publishDateTo = queryValue('publishDateTo')
  filters.includeHistorical = queryValue('includeHistorical') === 'true'
  page.value = Math.max(1, Number(queryValue('pageNum')) || 1)
  pageSize.value = Math.max(1, Number(queryValue('pageSize')) || 20)
}

function requestQuery(): PortalContentQuery {
  return {
    keyword: filters.keyword || undefined,
    contentType: filters.contentType || undefined,
    topicCode: filters.topicCode || undefined,
    issuingAuthority: filters.issuingAuthority || undefined,
    validityStatus: filters.validityStatus || undefined,
    spaceCode: filters.spaceCode || undefined,
    publishDateFrom: filters.publishDateFrom || undefined,
    publishDateTo: filters.publishDateTo || undefined,
    includeHistorical: filters.includeHistorical || undefined,
    pageNum: page.value,
    pageSize: pageSize.value,
  }
}

function routeQuery(): LocationQueryRaw {
  return Object.fromEntries(
    Object.entries(requestQuery())
      .filter(([, value]) => value !== undefined && value !== '')
      .map(([key, value]) => [key, typeof value === 'boolean' ? String(value) : value]),
  ) as LocationQueryRaw
}

async function load() {
  controller?.abort()
  controller = new AbortController()
  loading.value = true
  error.value = ''
  try {
    const result = await getPortalContents(requestQuery(), controller.signal)
    if (controller.signal.aborted) return
    rows.value = result.list
    total.value = result.total
  } catch (cause: unknown) {
    if ((cause as Error).name !== 'AbortError') error.value = errorMessage(cause, '无法读取资料')
  } finally {
    if (!controller.signal.aborted) loading.value = false
  }
}

async function applyFilters(resetPage = true) {
  if (resetPage) page.value = 1
  const target = routeQuery()
  const current = Object.fromEntries(Object.entries(route.query).map(([key, value]) => [key, String(value)]))
  if (JSON.stringify(target) === JSON.stringify(current)) await load()
  else await router.push({ query: target })
}

async function resetFilters() {
  Object.assign(filters, {
    keyword: '',
    contentType: '',
    topicCode: '',
    issuingAuthority: '',
    validityStatus: '',
    spaceCode: '',
    publishDateFrom: '',
    publishDateTo: '',
    includeHistorical: false,
  })
  await applyFilters(true)
}

async function favorite(item: PartyContent) {
  if (!item.contentId || favoritePending.value.has(item.contentId) || item.favorite) return
  favoritePending.value = new Set(favoritePending.value).add(item.contentId)
  try {
    await addPortalFavorite({
      favoriteType: 'content',
      docId: item.contentId,
      title: item.title || '未命名资料',
    })
    item.favorite = true
    ElMessage.success('已收藏')
  } catch (cause: unknown) {
    ElMessage.error(errorMessage(cause, '收藏失败'))
  } finally {
    const next = new Set(favoritePending.value)
    next.delete(item.contentId)
    favoritePending.value = next
  }
}

async function copyCitation(item: PartyContent) {
  try {
    await window.navigator.clipboard.writeText(`${item.title}（${item.documentNumber || '无文号'}）`)
    ElMessage.success('引用已复制')
  } catch {
    ElMessage.error('浏览器未允许复制，请手动选择标题和文号。')
  }
}

watch(
  () => route.fullPath,
  () => {
    applyRouteState()
    void load()
  },
  { immediate: true },
)
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <PortalSystemPageFrame core-component="content-results">
    <div class="portal-library">
      <aside class="portal-filter-panel">
        <div>
          <span class="portal-kicker">资料筛选</span>
          <h2>权威资料中心</h2>
        </div>
        <label>
          内容分类
          <el-select v-model="filters.contentType" clearable placeholder="全部分类">
            <el-option
              v-for="item in PARTY_CONTENT_CATEGORIES"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </label>
        <label>
          专题
          <el-select
            v-model="filters.topicCode"
            clearable
            placeholder="全部专题"
            :loading="topicsQuery.isPending.value"
          >
            <el-option
              v-for="item in topics"
              :key="item.topicCode"
              :label="item.name"
              :value="item.topicCode"
            />
          </el-select>
        </label>
        <label>发文机关<el-input v-model="filters.issuingAuthority" placeholder="输入机关全称" /></label>
        <label
          >发布日期起<input v-model="filters.publishDateFrom" class="portal-date-field" type="date"
        /></label>
        <label
          >发布日期止<input v-model="filters.publishDateTo" class="portal-date-field" type="date"
        /></label>
        <label>
          效力状态
          <el-select v-model="filters.validityStatus" clearable placeholder="有效与即将生效">
            <el-option label="现行有效" value="effective" />
            <el-option label="即将生效" value="pending" />
            <el-option label="已失效" value="expired" />
            <el-option label="已废止" value="repealed" />
          </el-select>
        </label>
        <label>知识空间<el-input v-model="filters.spaceCode" placeholder="空间编码" /></label>
        <el-checkbox v-model="filters.includeHistorical">包含历史失效资料</el-checkbox>
        <el-button type="primary" :loading="loading" @click="applyFilters(true)">应用筛选</el-button>
        <el-button :disabled="loading" @click="resetFilters">重置筛选</el-button>
      </aside>

      <section class="portal-results">
        <div class="portal-library-heading">
          <div>
            <span class="portal-kicker">LIBRARY</span>
            <h1>资料检索</h1>
            <p>共找到 {{ total }} 份有权访问的资料</p>
          </div>
          <form class="portal-library-search" @submit.prevent="applyFilters(true)">
            <input v-model="filters.keyword" placeholder="标题、正文、文号组合搜索" />
            <button>搜索</button>
          </form>
        </div>

        <PageState :loading="loading" :error="error" :empty="!rows.length">
          <div class="portal-result-list">
            <article v-for="item in rows" :key="item.contentId">
              <div class="portal-result-main">
                <div class="portal-result-tags">
                  <StatusTag :status="item.validityStatus" />
                  <span>{{ categoryLabel(item.contentType) }}</span>
                  <span>{{ item.spaceName }}</span>
                </div>
                <h2>
                  <router-link
                    :to="sitePath('/portal/content/:contentId', { contentId: Number(item.contentId) })"
                  >
                    {{ item.title }}
                  </router-link>
                </h2>
                <p>{{ item.summary || '暂无摘要，打开原文查看完整内容。' }}</p>
                <dl>
                  <div>
                    <dt>文号</dt>
                    <dd>{{ item.documentNumber || '—' }}</dd>
                  </div>
                  <div>
                    <dt>发文机关</dt>
                    <dd>{{ item.issuingAuthority || '—' }}</dd>
                  </div>
                  <div>
                    <dt>发布日期</dt>
                    <dd>{{ item.publishDate || '—' }}</dd>
                  </div>
                </dl>
              </div>
              <div class="portal-result-actions">
                <router-link
                  :to="sitePath('/portal/content/:contentId', { contentId: Number(item.contentId) })"
                >
                  打开原文
                </router-link>
                <button
                  :disabled="
                    item.favorite ||
                    !item.contentId ||
                    (item.contentId ? favoritePending.has(item.contentId) : false)
                  "
                  @click="favorite(item)"
                >
                  {{ item.favorite ? '已收藏' : '收藏' }}
                </button>
                <button @click="copyCitation(item)">复制引用</button>
              </div>
            </article>
          </div>
          <AppPagination
            v-model:page="page"
            v-model:page-size="pageSize"
            :total="total"
            :disabled="loading"
            @change="applyFilters(false)"
          />
        </PageState>
      </section>
    </div>
  </PortalSystemPageFrame>
</template>
