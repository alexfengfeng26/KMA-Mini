import { describe, expect, it } from 'vitest'
import { modelCapabilityMeta, roleLabel, roleStatusMeta, taskStatusMeta } from './systemCatalog'

describe('system display catalog', () => {
  it('translates task, role and model status consistently', () => {
    expect(taskStatusMeta('processing')).toEqual({ label: '处理中', type: 'primary' })
    expect(roleStatusMeta('disabled')).toEqual({ label: '停用', type: 'info' })
    expect(modelCapabilityMeta('embedding').label).toBe('向量模型')
    expect(roleLabel('knowledge-reader')).toBe('知识阅读者')
  })

  it('keeps unknown backend codes visible for diagnostics', () => {
    expect(taskStatusMeta('paused')).toEqual({ label: 'paused', type: 'info' })
    expect(roleLabel('custom-reviewer')).toBe('custom-reviewer')
  })
})
