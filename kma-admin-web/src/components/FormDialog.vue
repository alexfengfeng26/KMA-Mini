<script setup lang="ts">
withDefaults(
  defineProps<{
    modelValue: boolean
    title: string
    width?: string | number
    submitting?: boolean
    submitDisabled?: boolean
    submitText?: string
  }>(),
  {
    width: 560,
    submitting: false,
    submitDisabled: false,
    submitText: '保存',
  },
)

defineEmits<{
  'update:modelValue': [value: boolean]
  submit: []
}>()
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    :width="width"
    :close-on-click-modal="!submitting"
    :close-on-press-escape="!submitting"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <form class="form-dialog-body" @submit.prevent="$emit('submit')">
      <slot />
    </form>
    <template #footer>
      <slot name="footer">
        <el-button :disabled="submitting" @click="$emit('update:modelValue', false)">取消</el-button>
        <el-button
          type="primary"
          native-type="submit"
          :loading="submitting"
          :disabled="submitDisabled || submitting"
          @click="$emit('submit')"
        >
          {{ submitText }}
        </el-button>
      </slot>
    </template>
  </el-dialog>
</template>
