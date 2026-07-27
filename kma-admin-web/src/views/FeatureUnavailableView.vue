<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { getFrontendModule } from '../modules/registry'
import { portalHome, routeSiteKey } from '../security/siteRoute'

const route = useRoute()
const siteFailure = computed(() => route.query.reason === 'site')
const moduleTitle = computed(() => getFrontendModule(String(route.query.module || ''))?.title || '当前功能')
const title = computed(() => (siteFailure.value ? '门户站点暂时不可用' : `${moduleTitle.value}未启用`))
const description = computed(() =>
  siteFailure.value
    ? '站点配置加载失败。请检查服务状态后重试；服务恢复后会自动返回站点首页。'
    : '当前前端模块配置已关闭此功能。如需使用，请联系门户管理员调整运行时配置。',
)
const retryPath = computed(() => portalHome(routeSiteKey(route.query.site) || 'default'))
</script>

<template>
  <main class="feature-unavailable">
    <span>{{ siteFailure ? 'SITE UNAVAILABLE' : 'FEATURE DISABLED' }}</span>
    <h1>{{ title }}</h1>
    <p>{{ description }}</p>
    <router-link :to="siteFailure ? retryPath : '/'">
      {{ siteFailure ? '重新加载站点' : '返回可用首页' }}
    </router-link>
  </main>
</template>

<style scoped>
.feature-unavailable {
  display: grid;
  place-content: center;
  min-height: 100vh;
  padding: 32px;
  background: var(--kma-color-background);
  color: var(--kma-color-text);
  text-align: center;
}

.feature-unavailable span {
  color: var(--kma-color-primary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
}

.feature-unavailable h1 {
  margin: 12px 0;
  font-size: clamp(28px, 5vw, 48px);
}

.feature-unavailable p {
  max-width: 48ch;
  color: var(--kma-color-text-muted);
  line-height: 1.7;
}

.feature-unavailable a {
  justify-self: center;
  margin-top: 16px;
  padding: 10px 16px;
  border-radius: var(--kma-radius-control);
  background: var(--kma-color-primary);
  color: white;
  text-decoration: none;
}
</style>
