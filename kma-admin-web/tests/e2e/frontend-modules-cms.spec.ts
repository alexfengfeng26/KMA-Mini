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

async function mockExperience(page: Page) {
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
            {
              versionId: 1,
              versionNo: 1,
              status: 'draft',
              schemaVersion: 3,
              checksum: 'e2e',
              lockVersion: 0,
            },
          ],
        },
      })
    if (path === '/api/v1/admin/portal-sites/default/versions/1')
      return route.fulfill({
        json: {
          code: 200,
          data: {
            versionId: 1,
            versionNo: 1,
            status: 'draft',
            schemaVersion: 3,
            checksum: 'e2e',
            lockVersion: 0,
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
              },
              symbols: {},
              packages: [],
            },
          },
        },
      })
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
})
