export const portalScenarios = ['party', 'internal-policy', 'product-help'] as const
export const cmsLayouts = ['single', 'sidebar-left', 'sidebar-right', 'twelve-grid'] as const
export const cmsV2BlockTypes = [
  'hero-search',
  'category-grid',
  'recent-documents',
  'current-topic',
  'reading-history',
  'favorites',
  'announcement',
  'quick-ask',
  'category-tree',
  'category-cards',
  'hot-searches',
  'recommended-articles',
  'pinned-content',
  'faq-list',
  'release-notes',
  'validity-dashboard',
  'document-timeline',
  'related-documents',
  'download-area',
  'sop-steps',
  'process-navigation',
  'role-entry',
  'learning-path',
  'ai-assistant',
  'suggested-questions',
  'no-answer-help',
  'human-help',
  'rich-text',
  'image-banner',
  'metric-cards',
  'feedback',
] as const

export type PortalScenario = (typeof portalScenarios)[number]
export type CmsLayout = (typeof cmsLayouts)[number]
export type CmsV2BlockType = (typeof cmsV2BlockTypes)[number]
export type CmsRegion = 'header' | 'main' | 'sidebar' | 'footer'
export type PortalVisualPack = 'party-authority' | 'policy-workbench' | 'help-product'
export type PortalBreakpoint = 'desktop' | 'tablet' | 'mobile'
export type LowCodeNodeType =
  'section' | 'container' | 'grid' | 'stack' | 'component' | 'sandbox' | 'symbol-ref'

export interface ResponsiveValue<T> {
  desktop: T
  tablet?: T
  mobile?: T
}

export interface LowCodeNodeLayout {
  span?: ResponsiveValue<number>
  order?: ResponsiveValue<number>
  gap?: ResponsiveValue<number>
  hidden?: ResponsiveValue<boolean>
  align?: ResponsiveValue<'start' | 'center' | 'end' | 'stretch'>
  direction?: ResponsiveValue<'row' | 'column'>
  maxWidth?: string
}

export interface LowCodeNodeStyle {
  background?: string
  color?: string
  borderColor?: string
  radius?: string
  padding?: ResponsiveValue<number>
}

interface LowCodeNodeBase {
  id: string
  type: LowCodeNodeType
  name?: string
  locked?: boolean
  layout?: LowCodeNodeLayout
  style?: LowCodeNodeStyle
  visibleWhen?: LowCodeCondition
}

export interface LowCodeCondition {
  op: 'eq' | 'contains' | 'and' | 'or' | 'not' | 'empty'
  field?: string
  value?: string | number | boolean
  children?: LowCodeCondition[]
}

export interface LowCodeAction {
  event: 'click' | 'submit' | 'select'
  type: 'navigate' | 'set-filter' | 'search' | 'ask' | 'open-content' | 'dialog' | 'feedback' | 'analytics'
  config?: Record<string, string | number | boolean>
}

export interface SectionNode extends LowCodeNodeBase {
  type: 'section'
  children: LayoutNode[]
}

export interface ContainerNode extends LowCodeNodeBase {
  type: 'container'
  children: LayoutNode[]
}

export interface GridNode extends LowCodeNodeBase {
  type: 'grid'
  columns?: ResponsiveValue<number>
  children: LayoutNode[]
}

export interface StackNode extends LowCodeNodeBase {
  type: 'stack'
  children: LayoutNode[]
}

export interface ComponentNode extends LowCodeNodeBase {
  type: 'component'
  component: CmsV2BlockType | PortalCoreComponent
  version?: string
  props?: Record<string, string | number | boolean>
  dataSource?: {
    source: string
    filters?: Record<string, string | number | boolean>
  }
  actions?: LowCodeAction[]
}

export interface SandboxNode extends LowCodeNodeBase {
  type: 'sandbox'
  source?: 'package' | 'inline'
  packageId?: string
  version?: string
  height?: number
  capabilities?: string[]
  config?: Record<string, string | number | boolean>
  inline?: PortalInlineCode
}

export interface PortalInlineCode {
  files: Record<string, string>
  manifest?: { capabilities?: PortalSandboxCapability[] }
}

export type PortalSandboxCapability = 'page-context' | 'contents' | 'search' | 'ask' | 'analytics'

export interface SymbolReferenceNode extends LowCodeNodeBase {
  type: 'symbol-ref'
  symbolId: string
}

export type LayoutNode =
  SectionNode | ContainerNode | GridNode | StackNode | ComponentNode | SandboxNode | SymbolReferenceNode

export type PortalCoreComponent =
  | 'content-results'
  | 'document-reader'
  | 'ai-conversation'
  | 'topic-directory'
  | 'favorite-list'
  | 'profile-card'
  | 'portal-navigation'
  | 'account-entry'

export interface LowCodePage {
  slug: string
  title?: string
  kind: 'home' | 'library' | 'search' | 'content' | 'ask' | 'topics' | 'favorites' | 'profile' | 'custom'
  root: LayoutNode
}

export interface ReusableSection {
  id: string
  name: string
  revision: number
  root: LayoutNode
}

export interface PackageReference {
  packageId: string
  version: string
  source: 'platform' | 'site'
}

export interface PortalExtensionBinding {
  extensionId: string
  version: string
  slotKey: string
  region?: CmsRegion
  enabled?: boolean
  config?: Record<string, string | number | boolean>
}

export interface ResolvedPortalExtension extends PortalExtensionBinding {
  displayName: string
  entryUrl: string
  integrityHash: string
  manifest: {
    capabilities?: string[]
    slots?: CmsRegion[]
    settingsSchema?: Record<string, unknown>
  }
}

export interface NavigationItem {
  id: string
  label: string
  target: string
}

export interface CmsBlockConfigV2 {
  id: string
  type: CmsV2BlockType
  enabled: boolean
  variant?: string
  span?: number
  props?: Record<string, string | number | boolean>
  dataSource?: {
    source: string
    filters?: Record<string, string | number | boolean>
  }
}

export interface CmsPageConfigV2 {
  slug: string
  layout: CmsLayout
  regions: {
    header?: CmsBlockConfigV2[]
    main: CmsBlockConfigV2[]
    sidebar?: CmsBlockConfigV2[]
    footer?: CmsBlockConfigV2[]
  }
  extensions?: PortalExtensionBinding[]
}

export interface PortalSiteConfigV2 {
  schemaVersion: 2
  revision: string
  site: {
    siteKey: string
    scenario: PortalScenario
    name: string
    locale: string
  }
  shell: {
    layout?: 'editorial-authority' | 'sidebar-workbench' | 'search-center'
    header: { showSearch?: boolean }
    navigation: NavigationItem[]
    footer: { text?: string }
  }
  theme: {
    pack?: PortalVisualPack
    preset: string
    mode: 'light' | 'dark' | 'system'
    density: 'comfortable' | 'compact'
    tokens: Record<string, string>
    customCss?: string
    scopedCss?: string
  }
  modules: Record<string, boolean>
  contentScope: {
    allSpaces: boolean
    spaceCodes: string[]
    topicCodes: string[]
    contentTypes: string[]
    validityStatuses: string[]
  }
  search: {
    placeholder: string
    hotKeywords: string[]
    defaultMode: string
  }
  assistant: {
    enabled: boolean
    title: string
    welcomeText: string
    suggestedQuestions: string[]
  }
  pages: Record<string, CmsPageConfigV2>
}

export interface PortalSiteConfigV3 {
  schemaVersion: 3
  revision: string
  site: PortalSiteConfigV2['site']
  shell: {
    header: LayoutNode
    footer: LayoutNode
    navigation: NavigationItem[]
  }
  theme: PortalSiteConfigV2['theme']
  modules: Record<string, boolean>
  contentScope: PortalSiteConfigV2['contentScope']
  search: PortalSiteConfigV2['search']
  assistant: PortalSiteConfigV2['assistant']
  pages: Record<string, LowCodePage>
  symbols: Record<string, ReusableSection>
  packages: PackageReference[]
}

export type PortalSiteConfig = PortalSiteConfigV2 | PortalSiteConfigV3
export type PortalPageConfig = CmsPageConfigV2 | LowCodePage

export interface PortalSiteSummary {
  siteId: number
  siteKey: string
  name: string
  scenario: PortalScenario
  status: 'active' | 'disabled'
  defaultSite: boolean
  publishedVersionId?: number
  publishedVersion?: number
}

export interface PortalConfigVersion {
  versionId: number
  versionNo: number
  status: 'draft' | 'reviewing' | 'published' | 'archived'
  schemaVersion: 2 | 3
  checksum: string
  lockVersion: number
  changeNote?: string
  config?: PortalSiteConfig
  createTime?: string
  reviewedAt?: string
  publishedAt?: string
}

export interface PortalBootstrap {
  site: PortalSiteSummary
  publishedVersion: number
  revision: string
  schemaVersion: 2 | 3
  shell: PortalSiteConfigV2['shell'] | PortalSiteConfigV3['shell']
  theme: PortalSiteConfigV2['theme']
  modules: Record<string, boolean>
  search: PortalSiteConfigV2['search']
  assistant: PortalSiteConfigV2['assistant']
  page: PortalPageConfig
  symbols?: Record<string, ReusableSection>
  packages?: PackageReference[]
  extensions: ResolvedPortalExtension[]
  portalData: import('../api/party').PortalHome
  preview?: boolean
  previewVersion?: number
  previewVersionId?: number
}

export function isPortalSiteConfigV2(value: unknown): value is PortalSiteConfigV2 {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Partial<PortalSiteConfigV2>
  return (
    candidate.schemaVersion === 2 &&
    Boolean(candidate.site?.siteKey) &&
    Boolean(candidate.shell) &&
    Boolean(candidate.theme) &&
    Boolean(candidate.pages?.home)
  )
}

export function isPortalSiteConfigV3(value: unknown): value is PortalSiteConfigV3 {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Partial<PortalSiteConfigV3>
  return (
    candidate.schemaVersion === 3 &&
    Boolean(candidate.site?.siteKey) &&
    Boolean(candidate.shell?.header) &&
    Boolean(candidate.shell?.footer) &&
    Boolean(candidate.theme) &&
    Boolean(candidate.pages?.home?.root)
  )
}

export function isLowCodePage(value: PortalPageConfig): value is LowCodePage {
  return 'root' in value
}
