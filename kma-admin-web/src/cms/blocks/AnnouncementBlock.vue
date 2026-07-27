<script setup lang="ts">
import type { CmsBlockConfig } from '../../app/runtimeConfig'
defineProps<{ config: CmsBlockConfig }>()
</script>

<template>
  <section class="cms-announcement" :class="`cms-announcement--${config.variant || 'standard'}`">
    <span>门户提示</span>
    <h2>{{ config.props?.title || '权威内容，规范使用' }}</h2>
    <p>{{ config.props?.body || '引用文件前请核对效力状态、发布机关和当前版本。' }}</p>
  </section>
</template>
