<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '../stores/auth'

const props = defineProps<{ permission?: string; permissions?: string[] }>()
const auth = useAuthStore()
const allowed = computed(() =>
  auth.hasAnyPermission(props.permissions || (props.permission ? [props.permission] : [])),
)
</script>

<template>
  <slot v-if="allowed" />
  <slot v-else name="fallback" />
</template>
