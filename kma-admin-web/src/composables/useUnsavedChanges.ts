import { onBeforeUnmount, onMounted, type Ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessageBox } from 'element-plus'

export function useUnsavedChanges(dirty: Readonly<Ref<boolean>>) {
  function beforeUnload(event: BeforeUnloadEvent) {
    if (!dirty.value) return
    event.preventDefault()
    event.returnValue = ''
  }

  async function confirmDiscard(): Promise<boolean> {
    if (!dirty.value) return true
    return ElMessageBox.confirm('当前表单还有未保存修改，确认放弃吗？', '未保存的修改', {
      type: 'warning',
      confirmButtonText: '放弃修改',
      cancelButtonText: '继续编辑',
    }).then(
      () => true,
      () => false,
    )
  }

  onMounted(() => window.addEventListener('beforeunload', beforeUnload))
  onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
  onBeforeRouteLeave(() => confirmDiscard())

  return { confirmDiscard }
}
