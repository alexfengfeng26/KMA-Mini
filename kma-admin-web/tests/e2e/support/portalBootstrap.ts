export function portalBootstrap(
  portalData: Record<string, unknown> = {},
  overrides: Record<string, unknown> = {},
) {
  return {
    site: {
      siteId: 1,
      siteKey: 'default',
      name: 'KMA Mini',
      scenario: 'party',
      status: 'active',
      defaultSite: true,
      publishedVersionId: 1,
      locale: 'zh-CN',
    },
    publishedVersion: 1,
    schemaVersion: 2,
    revision: 'e2e-default-1',
    shell: {
      layout: 'editorial-authority',
      header: { showSearch: true },
      navigation: [
        { id: 'home', label: '首页', target: 'home' },
        { id: 'library', label: '资料中心', target: 'library' },
        { id: 'ask', label: 'AI 问答', target: 'ask' },
        { id: 'topics', label: '专题学习', target: 'topics' },
      ],
      footer: { text: 'KMA Mini 知识服务' },
    },
    theme: {
      mode: 'light',
      pack: 'party-authority',
      preset: 'emerald',
      tokens: {},
      density: 'compact',
      scopedCss: '',
    },
    modules: {},
    search: {
      defaultMode: 'hybrid',
      hotKeywords: [],
      placeholder: '输入文件标题、文号、关键词或党建问题',
    },
    assistant: {
      title: 'AI 知识助手',
      enabled: true,
      welcomeText: '所有回答均来自已发布的权威资料。',
      suggestedQuestions: [],
    },
    page: {
      slug: 'home',
      layout: 'twelve-grid',
      regions: {
        main: [
          { id: 'hero', span: 12, type: 'hero-search', enabled: true, variant: 'compact' },
          { id: 'categories', span: 12, type: 'category-grid', enabled: true, variant: 'cards' },
          { id: 'recent', span: 8, type: 'recent-documents', enabled: true, variant: 'list' },
          { id: 'topic', span: 4, type: 'current-topic', enabled: true, variant: 'card' },
        ],
      },
      extensions: [],
    },
    symbols: {},
    packages: [],
    extensions: [],
    portalData: {
      config: { unit_name: 'KMA Mini', help_text: '所有回答均来自已发布的权威资料。' },
      categories: [],
      recent: [],
      topics: [],
      history: [],
      favorites: [],
      ...portalData,
    },
    ...overrides,
  }
}
