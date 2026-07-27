<script setup lang="ts">
import { computed } from 'vue'
import { validityMeta } from '../domain/partyKnowledge'

const props = defineProps<{ status?: string }>()
const meta = computed(() => validityMeta(props.status))
</script>

<template>
  <el-tag :type="meta.type">{{ meta.label }}</el-tag>
</template>
