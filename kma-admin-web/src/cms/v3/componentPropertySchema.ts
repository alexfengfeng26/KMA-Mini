import type { CmsV2BlockType, PortalCoreComponent } from '../siteConfig'

export interface ComponentPropertyField {
  key: string
  label: string
  type: 'text' | 'textarea' | 'number' | 'boolean' | 'select'
  placeholder?: string
  min?: number
  max?: number
  options?: Array<{ label: string; value: string }>
}

const title: ComponentPropertyField = { key: 'title', label: '标题', type: 'text' }
const description: ComponentPropertyField = {
  key: 'description',
  label: '说明',
  type: 'textarea',
}
const limit: ComponentPropertyField = {
  key: 'limit',
  label: '展示条数',
  type: 'number',
  min: 1,
  max: 50,
}

const schemas: Partial<Record<CmsV2BlockType | PortalCoreComponent, ComponentPropertyField[]>> = {
  'hero-search': [
    title,
    {
      key: 'placeholder',
      label: '搜索提示',
      type: 'text',
      placeholder: '输入文件标题、文号或关键词',
    },
    { key: 'showAsk', label: '显示 AI 提问入口', type: 'boolean' },
  ],
  'quick-ask': [title, { key: 'placeholder', label: '提问提示', type: 'text', placeholder: '输入知识问题' }],
  announcement: [title, { key: 'body', label: '公告正文', type: 'textarea' }],
  'rich-text': [title, { key: 'markdown', label: 'Markdown 正文', type: 'textarea' }],
  'category-grid': [title, { key: 'columns', label: '栏目列数', type: 'number', min: 1, max: 6 }],
  'recent-documents': [title, limit],
  'reading-history': [title, limit],
  favorites: [title, limit],
  'recommended-articles': [title, limit],
  'pinned-content': [title, limit],
  'ai-assistant': [title, description],
  'no-answer-help': [title, description],
  'human-help': [title, description],
}

export function componentPropertyFields(component: CmsV2BlockType | PortalCoreComponent) {
  return schemas[component] || [title, description]
}
