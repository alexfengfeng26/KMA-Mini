export const PARTY_CONTENT_CATEGORIES = [
  { value: 'party_constitution', label: '党章党规', description: '党章、准则与条例' },
  { value: 'policy', label: '政策文件', description: '权威政策与规范性文件' },
  { value: 'learning_material', label: '学习材料', description: '理论学习与专题辅导' },
  { value: 'grassroots_case', label: '基层案例', description: '基层实践与经验案例' },
  { value: 'organization_system', label: '组织工作制度', description: '组织生活与工作制度' },
] as const

export const VALIDITY_STATUS = {
  effective: { label: '现行有效', type: 'success' },
  pending: { label: '即将生效', type: 'warning' },
  expired: { label: '已失效', type: 'danger' },
  repealed: { label: '已废止', type: 'danger' },
  unknown: { label: '效力待确认', type: 'info' },
} as const

export const WORKFLOW_STATUS = {
  draft: { label: '草稿', type: 'info' },
  reviewing: { label: '待审核', type: 'warning' },
  published: { label: '已发布', type: 'success' },
} as const

export function categoryLabel(value?: string) {
  return PARTY_CONTENT_CATEGORIES.find((item) => item.value === value)?.label || value || '未分类'
}

export function validityMeta(value?: string) {
  return VALIDITY_STATUS[value as keyof typeof VALIDITY_STATUS] || VALIDITY_STATUS.unknown
}
