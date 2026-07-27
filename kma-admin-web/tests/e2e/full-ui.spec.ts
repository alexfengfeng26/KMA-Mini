import { expect, test, type Page, type Route } from '@playwright/test'

const permissions = ['kma:admin']

async function authenticate(page: Page) {
  await page.addInitScript(() => {
    sessionStorage.setItem('kma_access_token', 'full-ui-token')
    sessionStorage.setItem('kma_must_change_password', 'false')
  })
  await page.route('**/api/v1/**', async (route: Route) => {
    const url = new URL(route.request().url())
    const path = url.pathname
    if (path === '/api/v1/auth/me')
      return route.fulfill({
        json: {
          code: 200,
          data: {
            userId: 1,
            username: 'admin',
            displayName: 'KMA Admin',
            permissions,
            roles: ['kma-admin'],
            organizationCodes: ['root'],
            authorizationVersion: 1,
            mustChangePassword: false,
          },
        },
      })
    if (path === '/api/v1/metrics/dashboard')
      return route.fulfill({
        json: { code: 200, data: { docCount: 12, chunkCount: 48, pendingTaskCount: 0 } },
      })
    if (path === '/api/v1/system/dependencies')
      return route.fulfill({
        json: {
          code: 200,
          data: {
            core: {
              status: 'UP',
              details: { database: 'kma_ui_test', pgvector: 'installed', storage: 'writable' },
            },
            models: { status: 'DEGRADED', details: { embedding: 'down', llm: 'down' } },
          },
        },
      })
    if (path.endsWith('/page') || path === '/api/v1/tasks' || path === '/api/v1/call-logs')
      return route.fulfill({ json: { code: 200, data: { list: [] } } })
    if (path === '/api/v1/tasks/stats') return route.fulfill({ json: { code: 200, data: {} } })
    return route.fulfill({ json: { code: 200, data: [] } })
  })
}

const pages = [
  ['/console/contents', '内容库'],
  ['/console/reviews', '审核中心'],
  ['/console/publications', '发布管理'],
  ['/console/topics', '分类专题'],
  ['/console/portal-config', '门户配置'],
  ['/console/dashboard', '运行概览'],
  ['/console/spaces', '知识空间'],
  ['/console/datasets', '数据集与向量'],
  ['/console/documents', '技术文档'],
  ['/console/storage', '存储生命周期'],
  ['/console/retrieval', '检索调试'],
  ['/console/qa', '问答实验室'],
  ['/console/tasks', '任务与死信'],
  ['/console/models', '模型配置'],
  ['/console/evaluations', 'RAG 评测'],
  ['/console/audit', '调用与安全审计'],
  ['/console/access/users', '用户管理'],
  ['/console/access/roles', '角色权限'],
  ['/console/access/organizations', '组织管理'],
  ['/console/profile', '个人与密码'],
] as const

test('all protected pages render without runtime or console errors', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(message.text())
  })
  await authenticate(page)
  for (const [path] of pages) {
    await page.goto(path)
    await expect(page.locator('.main-area')).toBeVisible()
    await expect(page.locator('.page > *').first()).toBeVisible()
  }
  expect(errors).toEqual([])
})

test('all protected pages avoid document-level horizontal overflow on narrow screens', async ({ page }) => {
  test.setTimeout(60_000)
  await page.setViewportSize({ width: 390, height: 844 })
  await authenticate(page)
  for (const [path] of pages) {
    await page.goto(path)
    await expect(page.locator('.main-area')).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow, `${path} overflows viewport`).toBeLessThanOrEqual(1)
  }
})
