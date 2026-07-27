<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useExperienceStore } from '../stores/experience'
import { errorMessage } from '../api/client'
import { resolveSafePostLoginPath } from '../router'
import { takeSessionNotice } from '../security/browserSession'
import { portalHome, portalProfile } from '../security/siteRoute'
const form = reactive({ username: 'admin', password: '' })
const loading = ref(false)
const error = ref('')
const auth = useAuthStore()
const experience = useExperienceStore()
const router = useRouter()
const route = useRoute()
async function submit() {
  loading.value = true
  error.value = ''
  try {
    await auth.login(form.username, form.password)
    const fallback = auth.user?.mustChangePassword
      ? portalProfile('default')
      : auth.hasAnyPermission(['content:read'])
        ? portalHome('default')
        : auth.hasAnyPermission(['qa:use'])
          ? '/portal/ask'
          : portalProfile('default')
    router.push(
      auth.user?.mustChangePassword ? fallback : resolveSafePostLoginPath(route.query.redirect, fallback),
    )
  } catch (e: unknown) {
    error.value = errorMessage(e, '无法登录，请检查账号、密码和服务状态。')
  } finally {
    loading.value = false
  }
}
onMounted(() => {
  error.value = takeSessionNotice()
})
</script>
<template>
  <div class="login-page">
    <section class="login-intro">
      <span class="eyebrow">KNOWLEDGE MANAGEMENT AI</span>
      <h1>让知识可靠地<br />进入每一次回答。</h1>
      <p>统一管理文档版本、检索质量、模型依赖与访问权限。</p>
      <img
        v-if="experience.experience.assets.loginIllustration"
        class="login-illustration"
        :src="experience.experience.assets.loginIllustration"
        alt=""
      />
      <div class="signal"><i></i> 引用可追溯 · 入库可恢复</div>
    </section>
    <form class="login-card" @submit.prevent="submit">
      <div>
        <img
          v-if="experience.experience.assets.logo"
          class="brand-logo"
          :src="experience.experience.assets.logo"
          alt="KMA"
        />
        <span v-else class="brand-mark">K</span>
        <h2>登录管理中心</h2>
        <p>使用 KMA 本地账号登录。</p>
      </div>
      <label>用户名<el-input v-model="form.username" autocomplete="username" /></label
      ><label
        >密码<el-input v-model="form.password" type="password" show-password autocomplete="current-password"
      /></label>
      <p v-if="error" class="form-error">{{ error }}</p>
      <el-button native-type="submit" type="primary" size="large" :loading="loading">登录知识中心</el-button>
      <small>本地刷新令牌仅保存在 HttpOnly Cookie。</small>
    </form>
  </div>
</template>
