import { expect, test, type Page, type Route } from '@playwright/test'
import { portalBootstrap } from './support/portalBootstrap'

const runtimeConfig = {
  schemaVersion: 1,
  revision: 'e2e-site-theme',
  experience: {
    template: 'cms-news',
    theme: 'site-red',
    density: 'compact',
    modules: { 'portal.library': false },
    pages: {
      home: {
        template: 'cms-news',
        blocks: [
          { id: 'hero', type: 'hero-search', enabled: true, variant: 'compact' },
          {
            id: 'notice',
            type: 'announcement',
            enabled: true,
            variant: 'accent',
            props: { title: '站点通知', body: '本周完成专题材料学习。' },
          },
          { id: 'categories', type: 'category-grid', enabled: true, variant: 'links' },
        ],
      },
    },
    tokens: {
      colorPrimary: '#8f1730',
      colorPrimaryStrong: '#641126',
      colorPrimarySoft: '#f8e7eb',
      colorBackground: '#fbf7f1',
      colorSurface: '#fffdf9',
      colorText: '#2c2425',
      colorTextMuted: '#6f6062',
      colorBorder: '#e5d8d8',
      fontBody: "'DM Sans Variable', 'Noto Sans SC', sans-serif",
      fontSizeBase: '14px',
      lineHeightBody: '1.6',
      radiusCard: '10px',
      radiusControl: '8px',
      shadowCard: '0 8px 24px rgb(72 36 42 / 0.08)',
    },
    assets: {},
  },
}

interface MockPortalVersion {
  versionId: number
  versionNo: number
  status: 'draft' | 'reviewing' | 'published'
  schemaVersion: 3
  checksum: string
  lockVersion: number
  reviewedAt?: string
}

async function mockExperience(page: Page, versionOverride: Partial<MockPortalVersion> = {}) {
  const currentVersion: MockPortalVersion = {
    versionId: 1,
    versionNo: 1,
    status: 'draft',
    schemaVersion: 3,
    checksum: 'e2e',
    lockVersion: 0,
    ...versionOverride,
  }
  const publishedFallback: MockPortalVersion = {
    versionId: 1,
    versionNo: 1,
    status: 'published',
    schemaVersion: 3,
    checksum: 'published-e2e',
    lockVersion: 0,
  }
  await page.addInitScript(() => {
    sessionStorage.setItem('kma_access_token', 'frontend2-token')
    sessionStorage.setItem('kma_must_change_password', 'false')
  })
  await page.route('**/config/kma-runtime.json', (route) =>
    route.fulfill({ json: { ...runtimeConfig, revision: 'global' } }),
  )
  await page.route('**/api/v1/**', async (route: Route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/v1/auth/me')
      return route.fulfill({
        json: {
          code: 200,
          data: {
            userId: 1,
            username: 'theme-admin',
            permissions: ['kma:admin'],
            roles: ['kma-admin'],
            organizationCodes: ['root'],
            mustChangePassword: false,
          },
        },
      })
    if (path === '/api/v1/portal-sites/default/bootstrap')
      return route.fulfill({
        json: {
          code: 200,
          data: portalBootstrap(
            {
              config: { unit_name: '主题站点知识库', help_text: '仅使用已发布材料。' },
              categories: [{ content_type: 'policy', name: '政策文件', total: 12 }],
              recent: [],
              topics: [],
              history: [],
              favorites: [],
            },
            {
              modules: { 'portal.library': false },
              theme: {
                mode: 'light',
                pack: 'party-authority',
                preset: 'site-red',
                density: 'compact',
                tokens: runtimeConfig.experience.tokens,
                scopedCss: '',
              },
              page: {
                slug: 'home',
                layout: 'twelve-grid',
                regions: { main: runtimeConfig.experience.pages.home.blocks },
                extensions: [],
              },
            },
          ),
        },
      })
    if (path === '/api/v1/admin/portal-sites')
      return route.fulfill({
        json: {
          code: 200,
          data: [
            {
              siteId: 1,
              siteKey: 'default',
              name: 'KMA Mini',
              scenario: 'party',
              status: 'active',
              defaultSite: true,
            },
          ],
        },
      })
    if (path === '/api/v1/admin/portal-sites/default/versions')
      return route.fulfill({
        json: {
          code: 200,
          data: [
            currentVersion,
            ...(currentVersion.versionId === publishedFallback.versionId ? [] : [publishedFallback]),
          ],
        },
      })
    if (path === '/api/v1/admin/portal-sites/default/design-capability')
      return route.fulfill({
        json: {
          code: 200,
          data: {
            available: true,
            provider: 'deepseek',
            model: 'deepseek-v4-flash',
          },
        },
      })
    if (path === '/api/v1/admin/portal-sites/default/design-proposals')
      return route.fulfill({
        json: {
          code: 200,
          data: {
            scope: 'page',
            pageSlug: 'home',
            model: 'deepseek-v4-flash',
            summary: '突出首页搜索并增加公告区',
            warnings: [],
            promptTokens: 120,
            completionTokens: 80,
            target: {
              slug: 'home',
              title: '首页',
              kind: 'home',
              root: {
                id: 'home-root',
                type: 'section',
                children: [
                  {
                    id: 'ai-announcement',
                    type: 'component',
                    component: 'announcement',
                    name: 'AI 公告区',
                    props: { title: '最新通知' },
                  },
                ],
              },
            },
          },
        },
      })
    if (path.startsWith('/api/v1/admin/portal-sites/default/versions/')) {
      const requestedVersionId = Number(path.split('/').at(-1))
      const requestedVersion =
        requestedVersionId === currentVersion.versionId ? currentVersion : publishedFallback
      return route.fulfill({
        json: {
          code: 200,
          data: {
            ...requestedVersion,
            config: {
              schemaVersion: 3,
              revision: 'e2e-v3',
              site: { siteKey: 'default', scenario: 'party', name: 'KMA Mini', locale: 'zh-CN' },
              shell: {
                header: { id: 'header', type: 'section', children: [] },
                footer: { id: 'footer', type: 'section', children: [] },
                navigation: [{ id: 'home', label: '首页', target: 'home' }],
              },
              theme: {
                preset: 'emerald',
                mode: 'light',
                density: 'compact',
                tokens: {},
              },
              modules: {},
              contentScope: {
                allSpaces: true,
                spaceCodes: [],
                topicCodes: [],
                contentTypes: [],
                validityStatuses: ['effective'],
              },
              search: { placeholder: '搜索', hotKeywords: [], defaultMode: 'hybrid' },
              assistant: {
                enabled: true,
                title: '助手',
                welcomeText: '您好',
                suggestedQuestions: [],
              },
              pages: {
                home: {
                  slug: 'home',
                  title: '首页',
                  kind: 'home',
                  root: { id: 'home-root', type: 'section', children: [] },
                },
                library: {
                  slug: 'library',
                  title: '资料中心',
                  kind: 'library',
                  root: {
                    id: 'library-root',
                    type: 'section',
                    name: '资料中心页面',
                    children: [
                      {
                        id: 'library-core',
                        type: 'component',
                        name: '资料中心组件',
                        component: 'content-results',
                        locked: true,
                      },
                    ],
                  },
                },
              },
              symbols: {},
              packages: [],
            },
          },
        },
      })
    }
    return route.fulfill({ json: { code: 200, data: [] } })
  })
}

test('全局主题、CMS 模板和区块顺序由运行时配置生效', async ({ page }) => {
  await mockExperience(page)
  await page.goto('/p/default/home')

  await expect(page.locator('html')).toHaveAttribute('data-kma-template', 'cms-news')
  await expect(page.locator('html')).toHaveAttribute('data-kma-theme', 'site-red')
  await expect(page.getByText('站点通知')).toBeVisible()
  await expect(page.locator('.cms-block').nth(1)).toContainText('站点通知')
  expect(
    await page
      .locator('html')
      .evaluate((element) => getComputedStyle(element).getPropertyValue('--kma-color-primary').trim()),
  ).toBe('#8f1730')
})

test('关闭模块后菜单隐藏且直接访问进入功能未启用页', async ({ page }) => {
  await mockExperience(page)
  await page.goto('/p/default/home')
  await expect(page.getByRole('navigation').getByRole('link', { name: '资料中心' })).toHaveCount(0)

  await page.goto('/p/default/library')
  await expect(page).toHaveURL(/\/unavailable\?module=portal.library/)
  await expect(page.getByRole('heading', { name: '资料中心未启用' })).toBeVisible()
})

test('门户设计中心加载站点 V3 草稿并提供响应式编辑器', async ({ page }) => {
  await page.setViewportSize({ width: 1600, height: 900 })
  await mockExperience(page)
  await page.goto('/console/portal-appearance')
  await expect(page.getByRole('combobox', { name: '选择门户站点' })).toBeVisible()
  await expect(page.getByText('KMA Mini', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('桌面 12 列')).toBeVisible()
  await expect(page.getByRole('button', { name: '保存草稿' })).toBeEnabled()

  await page.getByRole('treeitem', { name: '资料中心 library', exact: true }).click()
  await expect(page.getByText('资料检索结果')).toBeVisible()
  await expect(page.getByText('content-results')).toBeVisible()
  await expect(page.getByText('系统核心组件 · 真实数据在门户运行时加载')).toBeVisible()
  const previewPromise = page.waitForEvent('popup')
  await page.getByRole('button', { name: '打开真实页面' }).click()
  const preview = await previewPromise
  await expect
    .poll(() => preview.evaluate(() => sessionStorage.getItem('kma_access_token')))
    .toBe('frontend2-token')
  await preview.close()
  await page.getByRole('treeitem', { name: '资料中心页面 section', exact: true }).click()
  await page.getByRole('treeitem', { name: '资料中心组件 component 锁定' }).click()
  await expect(page.getByTestId('inspector-panel')).toContainText('library-core')
  await expect(page.getByText('自动', { exact: true })).toBeVisible()
  await page.getByText('手机 4 列', { exact: true }).click()
  await expect(page.getByText('390px · 110%')).toBeVisible()
  const zoomSlider = page.getByRole('slider', { name: '滑块介于 40 至 110' })
  await zoomSlider.press('ArrowLeft')
  await expect(page.getByText('手动', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '适应' }).click()
  await expect(page.getByText('自动', { exact: true })).toBeVisible()
})

test('门户设计中心在中等宽度将属性栏改为抽屉并扩大画布', async ({ page }) => {
  await page.setViewportSize({ width: 1220, height: 800 })
  await mockExperience(page)
  await page.goto('/console/portal-appearance')

  await expect(page.getByTestId('portal-designer')).toBeVisible()
  await expect(page.getByTestId('inspector-panel')).toBeHidden()
  const stageWidth = await page
    .getByTestId('designer-stage')
    .evaluate((element) => element.getBoundingClientRect().width)
  expect(stageWidth).toBeGreaterThanOrEqual(680)

  await page.getByRole('button', { name: '属性' }).click()
  await expect(page.getByTestId('inspector-panel')).toBeVisible()
  const stageWidthWithDrawer = await page
    .getByTestId('designer-stage')
    .evaluate((element) => element.getBoundingClientRect().width)
  expect(stageWidthWithDrawer).toBe(stageWidth)
})

test('门户设计中心预览并可撤销 DeepSeek 页面提案', async ({ page }) => {
  await page.setViewportSize({ width: 1600, height: 900 })
  await mockExperience(page)
  await page.goto('/console/portal-appearance')

  await page.getByRole('button', { name: 'AI 设计' }).click()
  await expect(page.getByText('deepseek-v4-flash 已就绪')).toBeVisible()
  await page.getByText('当前页面', { exact: true }).click()
  await page.getByLabel('描述你想要的效果').fill('突出首页搜索，并增加一个简洁的公告区。')
  await page.getByRole('button', { name: '生成设计提案' }).click()
  await expect(page.getByText('突出首页搜索并增加公告区')).toBeVisible()
  await expect(page.getByText('+1')).toBeVisible()
  await page.getByRole('button', { name: '应用到草稿' }).click()

  await expect(page.getByText('AI 公告区')).toBeVisible()
  await expect(page.getByText('未保存')).toBeVisible()
  await page.getByRole('button', { name: '撤销' }).click()
  await expect(page.getByText('AI 公告区')).toHaveCount(0)
})

test('门户属性编辑形成独立历史并保护高级字段', async ({ page }) => {
  await page.setViewportSize({ width: 1600, height: 900 })
  await mockExperience(page)
  await page.goto('/console/portal-appearance')

  await page.getByRole('treeitem', { name: '资料中心 library', exact: true }).click()
  await expect(page.getByText('资料检索结果')).toBeVisible()
  await page.getByRole('treeitem', { name: '资料中心页面 section', exact: true }).click()
  await page.getByRole('treeitem', { name: '资料中心组件 component 锁定' }).click()
  await page.getByRole('tab', { name: '内容' }).click()
  const title = page.getByLabel('标题', { exact: true })
  await title.fill('权威资料入口')
  await title.press('Tab')
  await expect(page.getByRole('button', { name: '撤销' })).toBeEnabled()
  await page.getByRole('button', { name: '撤销' }).click()
  await expect(title).toHaveValue('')

  await page.getByRole('tab', { name: '高级' }).click()
  await page.getByLabel('高级属性 JSON').fill('{"id":"hacked"}')
  await page.getByRole('button', { name: '应用高级属性' }).click()
  await expect(page.getByText('不允许修改字段：id')).toBeVisible()
})

test('门户组件可从组件库拖入精确画布容器并撤销', async ({ page }) => {
  await page.setViewportSize({ width: 1600, height: 900 })
  await mockExperience(page)
  await page.goto('/console/portal-appearance')

  await page.getByText('组件', { exact: true }).click()
  const announcement = page.locator('.low-code-palette button').filter({ hasText: '公告栏' })
  await expect(announcement).toHaveCount(1)
  await announcement.dragTo(page.locator('[data-node-id="home-root"]'))
  const canvasComponents = page.getByTestId('canvas-viewport').locator('.designer-node--component')
  await expect(canvasComponents).toHaveCount(1)
  await expect(page.getByText('未保存')).toBeVisible()
  await page.getByRole('button', { name: '撤销' }).click()
  await expect(canvasComponents).toHaveCount(0)
})

test('门户设计中心窄宽度使用双抽屉并支持沉浸模式', async ({ page }) => {
  await page.setViewportSize({ width: 800, height: 760 })
  await mockExperience(page)
  await page.goto('/console/portal-appearance')

  await expect(page.getByTestId('structure-panel')).toBeHidden()
  await expect(page.getByTestId('inspector-panel')).toBeHidden()
  await expect(page.getByText('桌面 12 列', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '结构' }).click()
  await expect(page.getByTestId('structure-panel')).toBeVisible()
  await page.getByRole('button', { name: '关闭结构面板' }).click()
  await expect(page.getByTestId('structure-panel')).toBeHidden()

  await page.getByRole('button', { name: '沉浸设计' }).click()
  await expect(page.getByTestId('portal-designer')).toHaveClass(/is-immersive/)
  await page.keyboard.press('Escape')
  await expect(page.getByTestId('portal-designer')).not.toHaveClass(/is-immersive/)
})

test('门户设计中心跟随最新审核版本并显示完整审核发布动作', async ({ page }) => {
  await page.setViewportSize({ width: 1600, height: 900 })
  await mockExperience(page, {
    versionId: 2,
    versionNo: 2,
    status: 'reviewing',
  })
  await page.goto('/console/portal-appearance')

  await expect(page.getByText('default · V2 · reviewing')).toBeVisible()
  await expect(page.getByRole('button', { name: '提交审核' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '审核通过' })).toBeEnabled()
  await expect(page.getByRole('button', { name: '驳回' })).toBeEnabled()
  await expect(page.getByRole('button', { name: '发布' })).toHaveCount(0)
})

test('门户设计中心仅在审核通过后开放发布动作', async ({ page }) => {
  await page.setViewportSize({ width: 1600, height: 900 })
  await mockExperience(page, {
    versionId: 2,
    versionNo: 2,
    status: 'reviewing',
    reviewedAt: '2026-07-27T15:45:00+08:00',
  })
  await page.goto('/console/portal-appearance')

  await expect(page.getByRole('button', { name: '审核通过' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '发布' })).toBeEnabled()
})
