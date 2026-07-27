import { defineAsyncComponent, type Component } from 'vue'
import type { CmsV2BlockType } from './siteConfig'
import CmsBlockError from './CmsBlockError.vue'

export interface CmsBlockDefinition {
  type: CmsV2BlockType
  title: string
  category: string
  supportedPages: string[]
  variants: string[]
  permissions: string[]
  loader: () => Promise<{ default: Component }>
}

const existing: Partial<Record<CmsV2BlockType, () => Promise<{ default: Component }>>> = {
  'hero-search': () => import('./blocks/HeroSearchBlock.vue'),
  'category-grid': () => import('./blocks/CategoryGridBlock.vue'),
  'recent-documents': () => import('./blocks/RecentDocumentsBlock.vue'),
  'current-topic': () => import('./blocks/CurrentTopicBlock.vue'),
  'reading-history': () => import('./blocks/ReadingHistoryBlock.vue'),
  favorites: () => import('./blocks/FavoritesBlock.vue'),
  announcement: () => import('./blocks/AnnouncementBlock.vue'),
  'quick-ask': () => import('./blocks/QuickAskBlock.vue'),
}

const labels: Record<CmsV2BlockType, [string, string]> = {
  'hero-search': ['权威资料检索', '搜索与问答'],
  'category-grid': ['知识分类', '栏目'],
  'recent-documents': ['最近更新', '内容'],
  'current-topic': ['当前专题', '专题'],
  'reading-history': ['最近阅读', '个人'],
  favorites: ['我的收藏', '个人'],
  announcement: ['公告栏', '内容'],
  'quick-ask': ['快速提问', 'AI'],
  'category-tree': ['栏目树', '栏目'],
  'category-cards': ['栏目卡片', '栏目'],
  'hot-searches': ['热门搜索', '搜索与问答'],
  'recommended-articles': ['推荐文章', '内容'],
  'pinned-content': ['置顶内容', '内容'],
  'faq-list': ['常见问题', '帮助中心'],
  'release-notes': ['版本更新', '帮助中心'],
  'validity-dashboard': ['制度效力看板', '制度'],
  'document-timeline': ['文件时间轴', '制度'],
  'related-documents': ['相关资料', '内容'],
  'download-area': ['下载区', '内容'],
  'sop-steps': ['SOP 步骤', '流程'],
  'process-navigation': ['流程导航', '流程'],
  'role-entry': ['岗位入口', '流程'],
  'learning-path': ['学习路径', '学习'],
  'ai-assistant': ['AI 助手', 'AI'],
  'suggested-questions': ['推荐问题', 'AI'],
  'no-answer-help': ['无答案引导', 'AI'],
  'human-help': ['人工帮助入口', '帮助中心'],
  'rich-text': ['静态图文', '基础'],
  'image-banner': ['图片横幅', '基础'],
  'metric-cards': ['指标卡', '基础'],
  feedback: ['反馈区', '基础'],
}

const genericLoader = () => import('./blocks/GenericScenarioBlock.vue')

export const cmsBlockDefinitions = Object.entries(labels).map(([type, [title, category]]) => ({
  type: type as CmsV2BlockType,
  title,
  category,
  supportedPages: ['home', 'library', 'content', 'search', 'ask', 'topics', 'custom'],
  variants: ['default', 'compact', 'cards', 'list', 'featured'],
  permissions: ['content:read'],
  loader: existing[type as CmsV2BlockType] || genericLoader,
})) satisfies CmsBlockDefinition[]

export const cmsV2BlockRegistry = Object.fromEntries(
  cmsBlockDefinitions.map((definition) => [
    definition.type,
    defineAsyncComponent({
      loader: definition.loader,
      errorComponent: CmsBlockError,
      timeout: 10_000,
      suspensible: false,
    }),
  ]),
) as Record<CmsV2BlockType, Component>

export function blockDefinition(type: CmsV2BlockType) {
  return cmsBlockDefinitions.find((item) => item.type === type)
}
