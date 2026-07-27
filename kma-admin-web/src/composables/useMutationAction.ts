import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { errorMessage } from '../api/client'

export function useMutationAction() {
  const pending = ref(false)

  async function run<T>(
    action: () => Promise<T>,
    successMessage?: string,
  ): Promise<{ ok: true; value: T } | { ok: false }> {
    if (pending.value) return { ok: false }
    pending.value = true
    try {
      const result = await action()
      if (successMessage) ElMessage.success(successMessage)
      return { ok: true, value: result }
    } catch (error: unknown) {
      ElMessage.error(errorMessage(error, '操作失败，请稍后重试。'))
      return { ok: false }
    } finally {
      pending.value = false
    }
  }

  return { pending, run }
}
