<script setup lang="ts">
import { ElMessageBox } from 'element-plus'

const props = withDefaults(
  defineProps<{
    message: string
    title?: string
    confirmText?: string
    buttonText: string
    buttonType?: 'primary' | 'success' | 'warning' | 'danger' | 'info'
    loading?: boolean
    disabled?: boolean
  }>(),
  {
    title: '操作确认',
    confirmText: '确定',
    buttonType: 'primary',
    loading: false,
    disabled: false,
  },
)

const emit = defineEmits<{ confirm: [] }>()

async function requestConfirmation() {
  try {
    await ElMessageBox.confirm(props.message, props.title, {
      confirmButtonText: props.confirmText,
      cancelButtonText: '取消',
      type: props.buttonType === 'danger' ? 'warning' : 'info',
    })
    emit('confirm')
  } catch (reason: unknown) {
    if (reason !== 'cancel' && reason !== 'close') {
      console.error('确认对话框异常', reason)
    }
  }
}
</script>

<template>
  <el-button
    :type="buttonType"
    :loading="loading"
    :disabled="disabled || loading"
    @click="requestConfirmation"
  >
    {{ buttonText }}
  </el-button>
</template>
