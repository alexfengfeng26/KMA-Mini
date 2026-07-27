import type {
  CmsBlockConfigV2,
  CmsPageConfigV2,
  LayoutNode,
  LowCodePage,
  PortalExtensionBinding,
  PortalSiteConfigV2,
  PortalSiteConfigV3,
  PortalCoreComponent,
} from '../siteConfig'

function componentNode(block: CmsBlockConfigV2): LayoutNode {
  return {
    id: block.id,
    type: 'component',
    component: block.type,
    name: block.type,
    props: block.props,
    dataSource: block.dataSource,
    layout: {
      span: {
        desktop: Math.min(12, Math.max(1, block.span || 12)),
        tablet: Math.min(8, Math.max(1, Math.ceil(((block.span || 12) / 12) * 8))),
        mobile: 4,
      },
      hidden: { desktop: !block.enabled, tablet: !block.enabled, mobile: !block.enabled },
    },
  }
}

const systemPages: Array<[LowCodePage['kind'], string, PortalCoreComponent]> = [
  ['library', '资料中心', 'content-results'],
  ['search', '搜索结果', 'content-results'],
  ['content', '内容阅读', 'document-reader'],
  ['ask', 'AI 问答', 'ai-conversation'],
  ['topics', '专题', 'topic-directory'],
  ['favorites', '收藏', 'favorite-list'],
  ['profile', '个人中心', 'profile-card'],
]

function systemPage(kind: LowCodePage['kind'], title: string, component: PortalCoreComponent): LowCodePage {
  return {
    slug: kind,
    title,
    kind,
    root: {
      id: `${kind}-root`,
      type: 'section',
      name: `${title}页面`,
      locked: true,
      children: [
        {
          id: `${kind}-core`,
          type: 'component',
          component,
          name: title,
          locked: true,
          layout: {
            span: { desktop: 12, tablet: 8, mobile: 4 },
            hidden: { desktop: false, tablet: false, mobile: false },
          },
        },
      ],
    },
  }
}

function regionNode(pageSlug: string, region: string, blocks: CmsBlockConfigV2[]): LayoutNode {
  return {
    id: `${pageSlug}-${region}`,
    type: 'grid',
    name: `${region} 区域`,
    columns: { desktop: 12, tablet: 8, mobile: 4 },
    layout: { gap: { desktop: 16, tablet: 12, mobile: 12 } },
    children: blocks.map(componentNode),
  }
}

function extensionNode(binding: PortalExtensionBinding): LayoutNode {
  return {
    id: binding.slotKey,
    type: 'sandbox',
    packageId: binding.extensionId,
    version: binding.version,
    config: binding.config,
    layout: {
      span: { desktop: 12, tablet: 8, mobile: 4 },
      hidden: {
        desktop: binding.enabled === false,
        tablet: binding.enabled === false,
        mobile: binding.enabled === false,
      },
    },
  }
}

function migratePage(page: CmsPageConfigV2): LowCodePage {
  const regions = ['header', 'main', 'sidebar', 'footer'] as const
  const children = regions
    .filter(
      (region) =>
        (page.regions[region] || []).length > 0 ||
        (page.extensions || []).some((binding) => (binding.region || 'main') === region),
    )
    .map((region) => {
      const node = regionNode(page.slug, region, page.regions[region] || [])
      if ('children' in node) {
        node.children.push(
          ...(page.extensions || [])
            .filter((binding) => (binding.region || 'main') === region)
            .map(extensionNode),
        )
      }
      return node
    })
  return {
    slug: page.slug,
    title: page.slug === 'home' ? '首页' : page.slug,
    kind: page.slug === 'home' ? 'home' : 'custom',
    root: {
      id: `${page.slug}-root`,
      type: 'section',
      name: '页面根节点',
      locked: true,
      children,
    },
  }
}

function shellNode(id: string, component: 'portal-navigation' | 'account-entry'): LayoutNode {
  return {
    id,
    type: 'container',
    locked: true,
    children: [
      {
        id: `${id}-${component}`,
        type: 'component',
        component,
        locked: true,
        layout: { span: { desktop: 12, tablet: 8, mobile: 4 } },
      },
    ],
  }
}

export function migratePortalConfigV2ToV3(config: PortalSiteConfigV2): PortalSiteConfigV3 {
  const pages = Object.fromEntries(
    Object.entries(config.pages).map(([slug, page]) => [slug, migratePage(page)]),
  )
  for (const [kind, title, component] of systemPages) {
    if (!pages[kind]) pages[kind] = systemPage(kind, title, component)
  }
  return {
    schemaVersion: 3,
    revision: `v3-${Date.now()}`,
    site: structuredClone(config.site),
    shell: {
      header: shellNode('global-header', 'portal-navigation'),
      footer: shellNode('global-footer', 'account-entry'),
      navigation: structuredClone(config.shell.navigation),
    },
    theme: structuredClone(config.theme),
    modules: structuredClone(config.modules),
    contentScope: structuredClone(config.contentScope),
    search: structuredClone(config.search),
    assistant: structuredClone(config.assistant),
    pages,
    symbols: {},
    packages: Array.from(
      new Map(
        Object.values(config.pages)
          .flatMap((page) => page.extensions || [])
          .map((binding) => [
            `${binding.extensionId}@${binding.version}`,
            { packageId: binding.extensionId, version: binding.version, source: 'platform' as const },
          ]),
      ).values(),
    ),
  }
}
