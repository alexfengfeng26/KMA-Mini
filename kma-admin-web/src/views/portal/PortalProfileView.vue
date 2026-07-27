<script setup lang="ts">
import { useAuthStore } from '../../stores/auth'
import PortalSystemPageFrame from '../../cms/v3/PortalSystemPageFrame.vue'
import ProfileView from '../ProfileView.vue'
const auth = useAuthStore()
</script>
<template>
  <PortalSystemPageFrame core-component="profile-card">
    <section class="portal-page">
      <header class="portal-page-heading">
        <span class="portal-kicker">PROFILE</span>
        <h1>个人中心</h1>
        <p>查看组织归属，管理登录安全。</p>
      </header>
      <div class="portal-profile-grid">
        <aside>
          <div class="portal-profile-avatar">{{ auth.user?.username?.slice(0, 1)?.toUpperCase() }}</div>
          <h2>{{ auth.user?.username }}</h2>
          <dl>
            <dt>组织归属</dt>
            <dd>{{ auth.user?.organizationCodes?.join('、') || '未分配' }}</dd>
            <dt>角色</dt>
            <dd>{{ auth.user?.roles?.join('、') || '普通用户' }}</dd>
            <dt>登录来源</dt>
            <dd>本地账号</dd>
          </dl>
        </aside>
        <ProfileView />
      </div>
    </section>
  </PortalSystemPageFrame>
</template>
