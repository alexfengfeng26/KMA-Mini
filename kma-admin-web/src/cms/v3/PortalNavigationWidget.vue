<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { useExperienceStore } from '../../stores/experience'
import { usePortalSiteStore } from '../../stores/portalSite'
import { consoleNavigation, firstAuthorizedPath } from '../../security/navigation'
import { isFrontendModuleEnabled, portalModules } from '../../modules/registry'
import { consolePath, portalSitePath } from '../../security/siteRoute'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const experience = useExperienceStore()
const portalSite = usePortalSiteStore()
const mobileOpen = ref(false)
const keyword = ref('')
const siteKey = computed(() => portalSite.siteKey || 'default')
const unitName = computed(() => portalSite.site?.name || '知识门户')
const primaryOrganization = computed(() => auth.user?.organizationCodes?.[0] || '未分配组织')
const canManage = computed(() =>
  consoleNavigation.some((item) => item.section !== 'account' && auth.hasAnyPermission(item.permissions)),
)
const nav = computed(() =>
  portalSite.bootstrap?.shell.navigation?.length
    ? portalSite.bootstrap.shell.navigation.map((item) => ({
        path: portalSitePath(siteKey.value, `/${item.target}`),
        label: item.label,
      }))
    : portalModules
        .filter(
          (module) =>
            module.navigation &&
            auth.hasAnyPermission(module.permissions) &&
            isFrontendModuleEnabled(module, portalSite.isModuleEnabled),
        )
        .map((module) => ({
          path: portalSitePath(siteKey.value, module.navigation!.path),
          label: module.navigation!.label,
        })),
)

function search() {
  void router.push({
    path: portalSitePath(siteKey.value, '/library'),
    query: keyword.value.trim() ? { keyword: keyword.value.trim() } : {},
  })
  mobileOpen.value = false
}

function openConsole() {
  void router.push(consolePath(firstAuthorizedPath(auth.hasAnyPermission, experience.isFeatureEnabled)))
}

async function logout() {
  await auth.logout()
  await router.replace('/login')
}

async function switchAccount() {
  await auth.logout()
  await router.replace({ path: '/login', query: { switch: '1' } })
}
</script>

<template>
  <div class="portal-header-inner">
    <router-link :to="portalSitePath(siteKey, '/home')" class="portal-brand">
      <img
        v-if="experience.experience.assets.logo"
        class="brand-logo"
        :src="experience.experience.assets.logo"
        alt=""
      />
      <span v-else class="brand-mark">K</span>
      <span>
        <strong>{{ unitName }}</strong>
        <small>{{ primaryOrganization }}</small>
      </span>
    </router-link>
    <nav class="portal-nav" aria-label="知识门户导航">
      <router-link
        v-for="item in nav"
        :key="item.path"
        :to="item.path"
        :class="{ active: route.path.startsWith(item.path) }"
      >
        {{ item.label }}
      </router-link>
    </nav>
    <div class="portal-actions">
      <button v-if="canManage" class="portal-console-link" @click="openConsole">管理后台</button>
      <button class="portal-account-switch" type="button" @click="switchAccount">切换账号</button>
      <router-link :to="portalSitePath(siteKey, '/profile')" class="portal-user" aria-label="个人中心">
        {{ auth.user?.username?.slice(0, 1)?.toUpperCase() || '我' }}
      </router-link>
      <button
        class="portal-menu-button"
        aria-label="展开导航"
        :aria-expanded="mobileOpen"
        @click="mobileOpen = !mobileOpen"
      >
        ☰
      </button>
    </div>
  </div>
  <div class="portal-search-strip">
    <form class="portal-header-search" role="search" @submit.prevent="search">
      <input v-model="keyword" placeholder="搜索标题、正文或文号" aria-label="全局搜索" />
      <button>搜索</button>
    </form>
  </div>
  <div v-if="mobileOpen" class="portal-mobile-menu">
    <router-link v-for="item in nav" :key="item.path" :to="item.path" @click="mobileOpen = false">
      {{ item.label }}
    </router-link>
    <button v-if="canManage" @click="openConsole">进入管理后台</button>
    <button @click="switchAccount">切换账号</button>
    <button @click="logout">退出登录</button>
  </div>
</template>
