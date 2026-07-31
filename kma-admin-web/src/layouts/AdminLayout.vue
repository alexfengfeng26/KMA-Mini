<script setup lang="ts">
import '../styles/p22.css'
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useExperienceStore } from '../stores/experience'
import { accountNavigationItem, authorizedNavigationSections } from '../security/navigation'
import { consolePath, portalHome } from '../security/siteRoute'
import { authorizedJson } from '../api/client'
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const experience = useExperienceStore()
const mobileNavigationOpen = ref(false)
const governanceCounts = ref<Record<string, number>>({})
const primaryOrganization = computed(() => auth.user?.organizationCodes?.[0] || '未分配组织')
const organizationInitial = computed(() => primaryOrganization.value.trim().slice(0, 1).toUpperCase() || 'K')
const sections = computed(() =>
  authorizedNavigationSections(auth.hasAnyPermission, experience.isFeatureEnabled).map((section) => ({
    ...section,
    items: section.items.map((item) => ({
      ...item,
      path: consolePath(item.path),
      count:
        item.path === '/console/reviews'
          ? governanceCounts.value.reviewing || 0
          : item.path === '/console/publications'
            ? governanceCounts.value.pendingPublish || 0
            : 0,
    })),
  })),
)
async function loadGovernanceCounts() {
  if (!auth.hasAnyPermission(['content:read'])) return
  try {
    governanceCounts.value =
      (await authorizedJson<Record<string, number>>('/api/v1/admin/governance/insights')) || {}
  } catch {
    governanceCounts.value = {}
  }
}
loadGovernanceCounts()
const accountMenu = computed(() => {
  const item = accountNavigationItem()
  return { ...item, path: consolePath(item.path) }
})
watch(
  () => route.fullPath,
  () => {
    mobileNavigationOpen.value = false
  },
)
function isActive(path: string) {
  return route.path === path || route.path.startsWith(`${path}/`)
}
async function logout() {
  await auth.logout()
  await router.push('/login')
}
async function switchAccount() {
  await auth.logout()
  await router.push({ path: '/login', query: { switch: '1' } })
}
</script>

<template>
  <div class="shell console-shell">
    <button
      v-if="mobileNavigationOpen"
      class="sidebar-scrim"
      aria-label="关闭导航"
      @click="mobileNavigationOpen = false"
    ></button>
    <aside id="console-navigation" class="sidebar" :class="{ 'mobile-open': mobileNavigationOpen }">
      <div class="brand">
        <img
          v-if="experience.experience.assets.logo"
          class="brand-logo"
          :src="experience.experience.assets.logo"
          alt="KMA"
        />
        <span v-else class="brand-mark">K</span>
        <div><strong>KMA</strong><small>KNOWLEDGE OPS</small></div>
      </div>
      <nav class="sidebar-navigation" aria-label="主导航">
        <div class="navigation-scroll">
          <section
            v-for="section in sections"
            :key="section.id"
            class="navigation-section"
            :aria-labelledby="`nav-${section.id}`"
          >
            <div :id="`nav-${section.id}`" class="navigation-section-label">{{ section.title }}</div>
            <div class="navigation-items">
              <router-link
                v-for="item in section.items"
                :key="item.path"
                :to="item.path"
                class="navigation-link"
                :class="{ active: isActive(item.path) }"
                :title="item.title"
              >
                <span class="navigation-badge" :data-badge="item.badge" aria-hidden="true"></span>
                <span class="navigation-title">{{ item.title }}</span>
                <span v-if="item.count" class="navigation-count">{{
                  item.count > 99 ? '99+' : item.count
                }}</span>
              </router-link>
            </div>
          </section>
        </div>
      </nav>
      <footer class="sidebar-footer">
        <section class="organization-identity-card" aria-label="当前组织信息">
          <span class="organization-identity-mark" aria-hidden="true">{{ organizationInitial }}</span>
          <span class="organization-identity-copy">
            <strong :title="primaryOrganization">{{ primaryOrganization }}</strong>
            <small>KMA Mini</small>
          </span>
        </section>
        <router-link
          :to="accountMenu.path"
          class="account-link sidebar-account-link"
          :class="{ active: isActive(accountMenu.path) }"
          :title="`${auth.user?.username || '当前用户'} · ${accountMenu.title}`"
        >
          <span class="navigation-badge" :data-badge="accountMenu.badge" aria-hidden="true"></span>
          <span class="sidebar-account-copy">
            <strong>{{ auth.user?.username || '当前用户' }}</strong>
            <small>{{ accountMenu.title }}</small>
          </span>
        </router-link>
        <div class="sidebar-account-actions">
          <router-link
            v-if="auth.hasAnyPermission(['content:read'])"
            :to="portalHome('default')"
            class="console-portal-switch"
          >
            返回门户
          </router-link>
          <button type="button" class="quiet-button" @click="switchAccount">切换账号</button>
        </div>
        <button type="button" class="quiet-button sidebar-logout-button" @click="logout">退出登录</button>
      </footer>
    </aside>
    <main class="main-area">
      <button
        class="mobile-navigation-button"
        type="button"
        aria-label="打开导航"
        aria-controls="console-navigation"
        :aria-expanded="mobileNavigationOpen"
        @click="mobileNavigationOpen = true"
      >
        <span aria-hidden="true"></span>
        <span aria-hidden="true"></span>
        <span aria-hidden="true"></span>
      </button>
      <div class="page"><router-view /></div>
    </main>
  </div>
</template>
