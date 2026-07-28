import './styles/main.css'
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { VueQueryPlugin } from '@tanstack/vue-query'
import App from './App.vue'
import router from './router'
import { permissionDirective } from './security/permission'
import { fetchRuntimeConfig, applyExperienceTheme } from './app/runtimeConfig'
import { useExperienceStore } from './stores/experience'
import { queryClient } from './app/queryClient'
import { loadPortalTemplateStyle } from './templates/registry'
import { ElMessage } from 'element-plus'

async function bootstrap() {
  const initialExperience = await fetchRuntimeConfig('/config/kma-runtime.json')
  await loadPortalTemplateStyle(initialExperience.config.experience.template)
  applyExperienceTheme(initialExperience.config.experience)
  const app = createApp(App)
  const pinia = createPinia()
  app.directive('permission', permissionDirective)
  app.use(pinia)
  const experienceStore = useExperienceStore(pinia)
  experienceStore.initialize(initialExperience)
  window.addEventListener('kma-auth-cleared', experienceStore.resetPreview)
  window.addEventListener('kma-authorization-changed', () => {
    ElMessage.info('权限或组织范围已更新，当前功能已按最新授权重新加载。')
  })
  app.use(VueQueryPlugin, { queryClient }).use(router).mount('#app')
}

void bootstrap()
