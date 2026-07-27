export const portalTemplates = ['knowledge-classic', 'cms-news', 'reading-focus'] as const
export const portalDensities = ['comfortable', 'compact'] as const
export const cmsBlockTypes = [
  'hero-search',
  'category-grid',
  'recent-documents',
  'current-topic',
  'reading-history',
  'favorites',
  'announcement',
  'quick-ask',
] as const

export type PortalTemplate = (typeof portalTemplates)[number]
export type PortalDensity = (typeof portalDensities)[number]
export type CmsBlockType = (typeof cmsBlockTypes)[number]
export type CmsBlockProp = string | number | boolean

export interface CmsBlockConfig {
  id: string
  type: CmsBlockType
  enabled: boolean
  variant?: string
  props?: Record<string, CmsBlockProp>
}

export interface CmsPageConfig {
  template: PortalTemplate
  blocks: CmsBlockConfig[]
}

export interface ThemeTokenConfig {
  colorPrimary: string
  colorPrimaryStrong: string
  colorPrimarySoft: string
  colorBackground: string
  colorSurface: string
  colorText: string
  colorTextMuted: string
  colorBorder: string
  fontBody: string
  fontSizeBase: string
  lineHeightBody: string
  radiusCard: string
  radiusControl: string
  shadowCard: string
}

export interface ThemeAssets {
  logo?: string
  favicon?: string
  loginIllustration?: string
}

export interface PortalExperienceConfig {
  template: PortalTemplate
  theme: string
  density: PortalDensity
  modules: Record<string, boolean>
  pages: {
    home: CmsPageConfig
  }
  tokens: ThemeTokenConfig
  assets: ThemeAssets
}

export interface KmaRuntimeConfig {
  schemaVersion: 1
  revision: string
  experience: PortalExperienceConfig
}

export interface RuntimeConfigResult {
  config: KmaRuntimeConfig
  issues: string[]
  source: string
}

interface BlockRule {
  variants: readonly string[]
  props: Record<string, 'boolean' | 'shortText' | 'longText' | 'smallNumber'>
}

const blockRules: Record<CmsBlockType, BlockRule> = {
  'hero-search': {
    variants: ['compact', 'wide'],
    props: { placeholder: 'shortText', showAsk: 'boolean' },
  },
  'category-grid': {
    variants: ['cards', 'links'],
    props: { columns: 'smallNumber' },
  },
  'recent-documents': {
    variants: ['list', 'compact'],
    props: { limit: 'smallNumber' },
  },
  'current-topic': {
    variants: ['card', 'featured'],
    props: {},
  },
  'reading-history': {
    variants: ['list', 'compact'],
    props: { limit: 'smallNumber' },
  },
  favorites: {
    variants: ['list', 'compact'],
    props: { limit: 'smallNumber' },
  },
  announcement: {
    variants: ['standard', 'accent'],
    props: { title: 'shortText', body: 'longText' },
  },
  'quick-ask': {
    variants: ['inline', 'card'],
    props: { placeholder: 'shortText' },
  },
}

const tokenCssVariables: Record<keyof ThemeTokenConfig, string> = {
  colorPrimary: '--kma-color-primary',
  colorPrimaryStrong: '--kma-color-primary-strong',
  colorPrimarySoft: '--kma-color-primary-soft',
  colorBackground: '--kma-color-background',
  colorSurface: '--kma-color-surface',
  colorText: '--kma-color-text',
  colorTextMuted: '--kma-color-text-muted',
  colorBorder: '--kma-color-border',
  fontBody: '--kma-font-body',
  fontSizeBase: '--kma-font-size-base',
  lineHeightBody: '--kma-line-height-body',
  radiusCard: '--kma-radius-card',
  radiusControl: '--kma-radius-control',
  shadowCard: '--kma-shadow-card',
}

export const defaultRuntimeConfig: KmaRuntimeConfig = {
  schemaVersion: 1,
  revision: 'builtin-emerald-v1',
  experience: {
    template: 'knowledge-classic',
    theme: 'emerald',
    density: 'compact',
    modules: {},
    pages: {
      home: {
        template: 'knowledge-classic',
        blocks: [
          { id: 'hero', type: 'hero-search', enabled: true, variant: 'compact' },
          { id: 'categories', type: 'category-grid', enabled: true, variant: 'cards' },
          { id: 'recent', type: 'recent-documents', enabled: true, variant: 'list', props: { limit: 8 } },
          { id: 'topic', type: 'current-topic', enabled: true, variant: 'card' },
          { id: 'history', type: 'reading-history', enabled: true, variant: 'compact', props: { limit: 5 } },
          { id: 'favorites', type: 'favorites', enabled: true, variant: 'compact', props: { limit: 5 } },
        ],
      },
    },
    tokens: {
      colorPrimary: 'oklch(48% 0.11 187)',
      colorPrimaryStrong: 'oklch(34% 0.07 190)',
      colorPrimarySoft: 'oklch(92% 0.038 175)',
      colorBackground: 'oklch(97% 0.015 92)',
      colorSurface: 'oklch(99% 0.008 92)',
      colorText: 'oklch(28% 0.035 205)',
      colorTextMuted: '#536a6c',
      colorBorder: 'oklch(88% 0.018 195)',
      fontBody: "'DM Sans Variable', 'Noto Sans SC', sans-serif",
      fontSizeBase: '14px',
      lineHeightBody: '1.6',
      radiusCard: '12px',
      radiusControl: '8px',
      shadowCard: '0 8px 30px oklch(30% 0.03 200 / 0.05)',
    },
    assets: {},
  },
}

function cloneConfig<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function validIdentifier(value: unknown, max = 64): value is string {
  return typeof value === 'string' && new RegExp(`^[A-Za-z0-9][A-Za-z0-9_-]{0,${max - 1}}$`).test(value)
}

function validFeatureKey(value: unknown): value is string {
  return (
    typeof value === 'string' &&
    value.length <= 100 &&
    /^[A-Za-z0-9][A-Za-z0-9_.-]*$/.test(value) &&
    !value.includes('..')
  )
}

function validColor(value: unknown): value is string {
  if (typeof value !== 'string' || value.length > 100 || /[;{}]|url\s*\(/i.test(value)) return false
  return /^#[0-9a-f]{3,8}$/i.test(value) || /^(?:rgb|rgba|hsl|hsla|oklch)\([^)]{1,80}\)$/i.test(value)
}

function validPixel(value: unknown, min: number, max: number): value is string {
  if (typeof value !== 'string') return false
  const match = /^(\d+(?:\.\d+)?)px$/.exec(value)
  return Boolean(match && Number(match[1]) >= min && Number(match[1]) <= max)
}

function validLineHeight(value: unknown): value is string {
  if (typeof value !== 'string') return false
  const numeric = Number(value)
  return Number.isFinite(numeric) && numeric >= 1.2 && numeric <= 2
}

function validFont(value: unknown): value is string {
  return (
    typeof value === 'string' &&
    value.length > 0 &&
    value.length <= 160 &&
    !/[;{}]|url\s*\(|javascript:/i.test(value)
  )
}

function validShadow(value: unknown): value is string {
  return typeof value === 'string' && value.length <= 160 && !/[;{}]|url\s*\(|javascript:/i.test(value)
}

function validAsset(value: unknown): value is string {
  return (
    typeof value === 'string' &&
    value.length <= 255 &&
    value.startsWith('/themes/') &&
    !value.includes('..') &&
    !value.includes('\\')
  )
}

function normalizeTokens(value: unknown, base: ThemeTokenConfig, issues: string[]): ThemeTokenConfig {
  if (!isRecord(value)) return cloneConfig(base)
  const result = cloneConfig(base)
  const colorKeys: (keyof ThemeTokenConfig)[] = [
    'colorPrimary',
    'colorPrimaryStrong',
    'colorPrimarySoft',
    'colorBackground',
    'colorSurface',
    'colorText',
    'colorTextMuted',
    'colorBorder',
  ]
  for (const key of colorKeys) {
    if (value[key] === undefined) continue
    if (validColor(value[key])) result[key] = value[key]
    else issues.push(`tokens.${key} 不是允许的颜色值，已使用回退值`)
  }
  if (value.fontBody !== undefined) {
    if (validFont(value.fontBody)) result.fontBody = value.fontBody
    else issues.push('tokens.fontBody 不合法，已使用回退值')
  }
  if (value.fontSizeBase !== undefined) {
    if (validPixel(value.fontSizeBase, 12, 20)) result.fontSizeBase = value.fontSizeBase
    else issues.push('tokens.fontSizeBase 必须为 12px–20px')
  }
  if (value.lineHeightBody !== undefined) {
    if (validLineHeight(value.lineHeightBody)) result.lineHeightBody = value.lineHeightBody
    else issues.push('tokens.lineHeightBody 必须为 1.2–2')
  }
  for (const key of ['radiusCard', 'radiusControl'] as const) {
    if (value[key] === undefined) continue
    if (validPixel(value[key], 0, 32)) result[key] = value[key]
    else issues.push(`tokens.${key} 必须为 0px–32px`)
  }
  if (value.shadowCard !== undefined) {
    if (validShadow(value.shadowCard)) result.shadowCard = value.shadowCard
    else issues.push('tokens.shadowCard 不合法，已使用回退值')
  }
  return result
}

function normalizeAssets(value: unknown, base: ThemeAssets, issues: string[]): ThemeAssets {
  if (!isRecord(value)) return cloneConfig(base)
  const result = cloneConfig(base)
  for (const key of ['logo', 'favicon', 'loginIllustration'] as const) {
    if (value[key] === undefined || value[key] === '') {
      delete result[key]
      continue
    }
    if (validAsset(value[key])) result[key] = value[key]
    else issues.push(`assets.${key} 只允许使用同源 /themes/** 资源`)
  }
  return result
}

function normalizeProps(
  blockType: CmsBlockType,
  value: unknown,
  issues: string[],
): Record<string, CmsBlockProp> | undefined {
  if (!isRecord(value)) return undefined
  const result: Record<string, CmsBlockProp> = {}
  const rules = blockRules[blockType].props
  for (const [key, raw] of Object.entries(value)) {
    const rule = rules[key]
    if (!rule) {
      issues.push(`区块 ${blockType} 不支持属性 ${key}`)
      continue
    }
    if (rule === 'boolean' && typeof raw === 'boolean') result[key] = raw
    else if (
      rule === 'smallNumber' &&
      typeof raw === 'number' &&
      Number.isInteger(raw) &&
      raw >= 1 &&
      raw <= 20
    )
      result[key] = raw
    else if (rule === 'shortText' && typeof raw === 'string' && raw.length <= 120) result[key] = raw
    else if (rule === 'longText' && typeof raw === 'string' && raw.length <= 500) result[key] = raw
    else issues.push(`区块 ${blockType} 的属性 ${key} 值不合法`)
  }
  return Object.keys(result).length ? result : undefined
}

function normalizeBlocks(value: unknown, base: CmsBlockConfig[], issues: string[]): CmsBlockConfig[] {
  if (!Array.isArray(value)) return cloneConfig(base)
  const result: CmsBlockConfig[] = []
  const identifiers = new Set<string>()
  for (const raw of value) {
    if (!isRecord(raw) || !validIdentifier(raw.id) || !cmsBlockTypes.includes(raw.type as CmsBlockType)) {
      issues.push('发现无效 CMS 区块，已忽略')
      continue
    }
    if (identifiers.has(raw.id)) {
      issues.push(`区块 ID ${raw.id} 重复，已忽略后续配置`)
      continue
    }
    identifiers.add(raw.id)
    const type = raw.type as CmsBlockType
    const rule = blockRules[type]
    const variant =
      typeof raw.variant === 'string' && rule.variants.includes(raw.variant) ? raw.variant : undefined
    if (raw.variant !== undefined && !variant) issues.push(`区块 ${raw.id} 的变体不受支持`)
    result.push({
      id: raw.id,
      type,
      enabled: raw.enabled !== false,
      variant,
      props: normalizeProps(type, raw.props, issues),
    })
  }
  return result.length ? result : cloneConfig(base)
}

function normalizeExperience(
  value: unknown,
  base: PortalExperienceConfig,
  issues: string[],
): PortalExperienceConfig {
  if (!isRecord(value)) return cloneConfig(base)
  const result = cloneConfig(base)
  if (value.template !== undefined) {
    if (portalTemplates.includes(value.template as PortalTemplate))
      result.template = value.template as PortalTemplate
    else issues.push('experience.template 不受支持，已使用回退模板')
  }
  if (value.theme !== undefined) {
    if (validIdentifier(value.theme)) result.theme = value.theme
    else issues.push('experience.theme 不合法，已使用回退主题')
  }
  if (value.density !== undefined) {
    if (portalDensities.includes(value.density as PortalDensity))
      result.density = value.density as PortalDensity
    else issues.push('experience.density 不受支持')
  }
  if (isRecord(value.modules)) {
    result.modules = { ...result.modules }
    for (const [key, enabled] of Object.entries(value.modules)) {
      if (validFeatureKey(key) && typeof enabled === 'boolean') result.modules[key] = enabled
      else issues.push(`模块开关 ${key} 不合法，已忽略`)
    }
  }
  result.tokens = normalizeTokens(value.tokens, result.tokens, issues)
  result.assets = normalizeAssets(value.assets, result.assets, issues)
  if (isRecord(value.pages) && isRecord(value.pages.home)) {
    const home = value.pages.home
    let template = result.template
    if (home.template !== undefined) {
      if (portalTemplates.includes(home.template as PortalTemplate))
        template = home.template as PortalTemplate
      else issues.push('pages.home.template 不受支持，已使用门户模板')
    }
    result.pages.home = {
      template,
      blocks: normalizeBlocks(home.blocks, result.pages.home.blocks, issues),
    }
  } else {
    result.pages.home.template = result.template
  }
  return result
}

export function parseRuntimeConfig(
  value: unknown,
  source = 'runtime',
  base: KmaRuntimeConfig = defaultRuntimeConfig,
): RuntimeConfigResult {
  const fallback = cloneConfig(base)
  const issues: string[] = []
  if (!isRecord(value)) {
    return { config: fallback, issues: ['配置不是 JSON 对象，已使用默认配置'], source }
  }
  if (value.schemaVersion !== 1) {
    return { config: fallback, issues: ['配置版本不受支持，已使用默认配置'], source }
  }
  const revision =
    typeof value.revision === 'string' && value.revision.length <= 100 ? value.revision : fallback.revision
  return {
    config: {
      schemaVersion: 1,
      revision,
      experience: normalizeExperience(value.experience, fallback.experience, issues),
    },
    issues,
    source,
  }
}

export async function fetchRuntimeConfig(
  path: string,
  base: KmaRuntimeConfig = defaultRuntimeConfig,
  fetcher: typeof fetch = fetch,
  optional = false,
): Promise<RuntimeConfigResult> {
  try {
    const response = await fetcher(path, { cache: 'no-store', credentials: 'same-origin' })
    if (optional && response.status === 404) return { config: cloneConfig(base), issues: [], source: path }
    if (!response.ok)
      return {
        config: cloneConfig(base),
        issues: [`配置请求失败（HTTP ${response.status}），已使用回退配置`],
        source: path,
      }
    return parseRuntimeConfig(await response.json(), path, base)
  } catch {
    return {
      config: cloneConfig(base),
      issues: ['配置加载失败，已使用回退配置'],
      source: path,
    }
  }
}

export function applyExperienceTheme(
  experience: PortalExperienceConfig,
  targetDocument: Document = document,
) {
  const root = targetDocument.documentElement
  root.dataset.kmaTheme = experience.theme
  root.dataset.kmaTemplate = experience.template
  root.dataset.kmaDensity = experience.density
  for (const [key, variable] of Object.entries(tokenCssVariables) as [keyof ThemeTokenConfig, string][]) {
    root.style.setProperty(variable, experience.tokens[key])
  }
  const favicon = experience.assets.favicon
  let link = targetDocument.querySelector<HTMLLinkElement>('link[rel="icon"][data-kma-theme-icon]')
  if (favicon) {
    if (!link) {
      link = targetDocument.createElement('link')
      link.rel = 'icon'
      link.dataset.kmaThemeIcon = 'true'
      targetDocument.head.append(link)
    }
    link.href = favicon
  } else {
    link?.remove()
  }
}

export function runtimeDraftKey() {
  return 'kma:portal-experience:draft'
}

export function serializeRuntimeConfig(config: KmaRuntimeConfig) {
  return `${JSON.stringify(config, null, 2)}\n`
}

export function cloneRuntimeConfig(config: KmaRuntimeConfig = defaultRuntimeConfig) {
  return cloneConfig(config)
}
