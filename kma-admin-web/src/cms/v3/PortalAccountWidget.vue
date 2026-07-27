<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '../../stores/auth'
import { usePortalSiteStore } from '../../stores/portalSite'

const auth = useAuthStore()
const portalSite = usePortalSiteStore()
const unitName = computed(() => portalSite.site?.name || '知识门户')
const footerText = computed(() => {
  const footer = portalSite.bootstrap?.shell.footer
  return footer && 'text' in footer && footer.text ? footer.text : '内部知识服务 · 引用请核对现行效力'
})
</script>

<template>
  <footer class="portal-footer">
    <span>{{ unitName }}</span>
    <span>{{ footerText }}</span>
    <span>{{ auth.user?.username }}</span>
  </footer>
</template>
