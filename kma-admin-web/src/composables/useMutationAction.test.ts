import { describe, expect, it, vi } from 'vitest'
import { ElMessage } from 'element-plus'
import { useMutationAction } from './useMutationAction'

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn() },
}))

describe('useMutationAction', () => {
  it('locks duplicate submissions and reports success', async () => {
    let resolve: ((value: number) => void) | undefined
    const action = vi.fn(
      () =>
        new Promise<number>((done) => {
          resolve = done
        }),
    )
    const mutation = useMutationAction()
    const first = mutation.run(action, '保存成功')
    expect(mutation.pending.value).toBe(true)
    await expect(mutation.run(action)).resolves.toEqual({ ok: false })
    resolve?.(7)

    await expect(first).resolves.toEqual({ ok: true, value: 7 })
    expect(ElMessage.success).toHaveBeenCalledWith('保存成功')
    expect(mutation.pending.value).toBe(false)
  })

  it('returns a stable failure and displays traceable errors', async () => {
    const mutation = useMutationAction()
    const failure = Object.assign(new Error('拒绝保存'), { traceId: 'trace-7' })
    await expect(mutation.run(() => Promise.reject(failure))).resolves.toEqual({ ok: false })
    expect(ElMessage.error).toHaveBeenCalledWith(expect.stringContaining('trace-7'))
  })
})
