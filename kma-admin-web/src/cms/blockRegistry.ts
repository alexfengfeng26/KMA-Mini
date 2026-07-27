import { defineAsyncComponent, type Component } from 'vue'
import CmsBlockError from './CmsBlockError.vue'
import type { CmsBlockType } from '../app/runtimeConfig'

const loaders: Record<CmsBlockType, () => Promise<{ default: Component }>> = {
  'hero-search': () => import('./blocks/HeroSearchBlock.vue'),
  'category-grid': () => import('./blocks/CategoryGridBlock.vue'),
  'recent-documents': () => import('./blocks/RecentDocumentsBlock.vue'),
  'current-topic': () => import('./blocks/CurrentTopicBlock.vue'),
  'reading-history': () => import('./blocks/ReadingHistoryBlock.vue'),
  favorites: () => import('./blocks/FavoritesBlock.vue'),
  announcement: () => import('./blocks/AnnouncementBlock.vue'),
  'quick-ask': () => import('./blocks/QuickAskBlock.vue'),
}

export const cmsBlockRegistry = Object.fromEntries(
  Object.entries(loaders).map(([type, loader]) => [
    type,
    defineAsyncComponent({
      loader,
      errorComponent: CmsBlockError,
      timeout: 10_000,
      suspensible: false,
    }),
  ]),
) as Record<CmsBlockType, Component>
