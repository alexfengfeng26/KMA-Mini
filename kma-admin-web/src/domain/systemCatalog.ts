type TagType = 'success' | 'warning' | 'danger' | 'info' | 'primary'

export interface DisplayMeta {
  label: string
  type: TagType
}

const UNKNOWN_META: DisplayMeta = { label: '未知', type: 'info' }

export const TASK_STATUS: Record<string, DisplayMeta> = {
  pending: { label: '等待处理', type: 'warning' },
  processing: { label: '处理中', type: 'primary' },
  retrying: { label: '等待重试', type: 'warning' },
  success: { label: '已完成', type: 'success' },
  ready: { label: '待激活', type: 'success' },
  failed: { label: '失败', type: 'danger' },
  dead: { label: '死信', type: 'danger' },
  superseded: { label: '已被新版本替代', type: 'info' },
}

export const ROLE_STATUS: Record<string, DisplayMeta> = {
  active: { label: '启用', type: 'success' },
  disabled: { label: '停用', type: 'info' },
}

export const MODEL_CAPABILITIES: Record<string, DisplayMeta> = {
  llm: { label: '大语言模型', type: 'primary' },
  embedding: { label: '向量模型', type: 'success' },
  rerank: { label: '重排模型', type: 'warning' },
  ocr: { label: '文字识别', type: 'info' },
}

export const BUILT_IN_ROLES: Record<string, string> = {
  'kma-admin': '系统管理员',
  'knowledge-admin': '知识管理员',
  'knowledge-editor': '知识编辑者',
  'knowledge-reader': '知识阅读者',
  auditor: '审计员',
}

export function taskStatusMeta(status?: string): DisplayMeta {
  return TASK_STATUS[status || ''] || { ...UNKNOWN_META, label: status || UNKNOWN_META.label }
}

export function roleStatusMeta(status?: string): DisplayMeta {
  return ROLE_STATUS[status || ''] || { ...UNKNOWN_META, label: status || UNKNOWN_META.label }
}

export function userStatusMeta(status?: string): DisplayMeta {
  return ROLE_STATUS[status || ''] || { ...UNKNOWN_META, label: status || UNKNOWN_META.label }
}

export function orgStatusMeta(status?: string): DisplayMeta {
  return ROLE_STATUS[status || ''] || { ...UNKNOWN_META, label: status || UNKNOWN_META.label }
}

export function modelCapabilityMeta(capability?: string): DisplayMeta {
  return (
    MODEL_CAPABILITIES[capability || ''] || {
      ...UNKNOWN_META,
      label: capability || UNKNOWN_META.label,
    }
  )
}

export function roleLabel(roleCode?: string): string {
  return BUILT_IN_ROLES[roleCode || ''] || roleCode || '未分配'
}
