import type { PortalScenario, PortalVisualPack } from './siteConfig'

export interface VisualPackDefinition {
  id: PortalVisualPack
  label: string
  description: string
  scenario: PortalScenario
  shellLayout: 'editorial-authority' | 'sidebar-workbench' | 'search-center'
}

export const visualPacks: VisualPackDefinition[] = [
  {
    id: 'party-authority',
    label: '权威档案',
    description: '编辑式权威阅读、专题时间线与制度效力提示。',
    scenario: 'party',
    shellLayout: 'editorial-authority',
  },
  {
    id: 'policy-workbench',
    label: '制度工作台',
    description: '左侧流程导航、紧凑制度卡与岗位操作入口。',
    scenario: 'internal-policy',
    shellLayout: 'sidebar-workbench',
  },
  {
    id: 'help-product',
    label: '产品帮助',
    description: '居中搜索、FAQ、版本更新与产品化帮助入口。',
    scenario: 'product-help',
    shellLayout: 'search-center',
  },
]

export function visualPack(id?: string): VisualPackDefinition {
  return visualPacks.find((item) => item.id === id) || visualPacks[0]
}
