<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, unwrap, errorMessage } from '../api/client'
import { useAuthStore } from '../stores/auth'
const form = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' }),
  loading = ref(false),
  error = ref(''),
  success = ref('')
const router = useRouter(),
  auth = useAuthStore()
const mustChangePassword = ref(sessionStorage.getItem('kma_must_change_password') === 'true')
async function change() {
  error.value = ''
  success.value = ''
  if (form.newPassword !== form.confirmPassword) {
    error.value = '两次输入的新密码不一致'
    return
  }
  loading.value = true
  try {
    await unwrap(
      api.POST('/api/v1/auth/change-password', {
        body: { currentPassword: form.currentPassword, newPassword: form.newPassword },
      }),
    )
    sessionStorage.setItem('kma_must_change_password', 'false')
    mustChangePassword.value = false
    success.value = '密码已修改，所有刷新令牌已撤销，请重新登录。'
    setTimeout(async () => {
      await auth.logout()
      router.replace('/login')
    }, 800)
  } catch (e: unknown) {
    error.value = errorMessage(e, '密码修改失败')
  } finally {
    loading.value = false
  }
}
</script>
<template>
  <section class="panel">
    <div class="toolbar">
      <div>
        <span class="eyebrow">ACCOUNT SECURITY</span>
        <h2>个人信息与密码</h2>
      </div>
      <el-button @click="auth.logout().then(() => router.replace('/login'))">撤销当前会话</el-button>
    </div>
    <el-alert
      v-if="mustChangePassword"
      type="warning"
      title="首次登录必须修改初始密码后才能使用其他功能"
      :closable="false"
    /><el-form label-position="top" class="form-compact profile-form"
      ><el-form-item label="当前密码"
        ><el-input v-model="form.currentPassword" type="password" show-password /></el-form-item
      ><el-form-item label="新密码（至少 12 位）"
        ><el-input v-model="form.newPassword" type="password" show-password /></el-form-item
      ><el-form-item label="确认新密码"
        ><el-input v-model="form.confirmPassword" type="password" show-password
      /></el-form-item>
      <p v-if="error" class="form-error">{{ error }}</p>
      <el-alert v-if="success" type="success" :title="success" :closable="false" /><el-button
        type="primary"
        :loading="loading"
        @click="change"
        >修改密码并撤销令牌</el-button
      ></el-form
    >
  </section>
</template>
