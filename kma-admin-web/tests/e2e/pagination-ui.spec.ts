import { expect, test, type Page, type Route } from '@playwright/test'

async function authenticate(page: Page) {
  await page.addInitScript(() => {
    sessionStorage.setItem('kma_access_token', 'pagination-ui-token')
    sessionStorage.setItem('kma_must_change_password', 'false')
  })

  await page.route('**/api/v1/**', async (route: Route) => {
    const url = new URL(route.request().url())
    const path = url.pathname
    if (path === '/api/v1/auth/me') {
      return route.fulfill({
        json: {
          code: 200,
          data: {
            userId: 1,
            username: 'admin',
            displayName: '管理员',
            permissions: ['kma:admin'],
            roles: ['kma-admin'],
            organizationCodes: ['default-root'],
            authorizationVersion: 1,
            mustChangePassword: false,
          },
        },
      })
    }
    if (path === '/api/v1/spaces/page') {
      const pageNum = Number(url.searchParams.get('pageNum') || 1)
      const pageSize = Number(url.searchParams.get('pageSize') || 10)
      const all = Array.from({ length: 25 }, (_, index) => ({
        spaceId: index + 1,
        spaceCode: `space-${index + 1}`,
        name: `知识空间 ${index + 1}`,
        status: 'active',
      }))
      const start = (pageNum - 1) * pageSize
      return route.fulfill({
        json: {
          code: 200,
          data: {
            list: all.slice(start, start + pageSize),
            total: all.length,
            pageNum,
            pageSize,
          },
        },
      })
    }
    if (path === '/api/v1/admin/users/page') {
      const pageNum = Number(url.searchParams.get('pageNum') || 1)
      const pageSize = Number(url.searchParams.get('pageSize') || 20)
      const all = Array.from({ length: 25 }, (_, index) => ({
        user_id: index + 1,
        username: `user-${index + 1}`,
        display_name: `用户 ${index + 1}`,
        identity_provider: 'local',
        roles: 'knowledge-reader',
        organizations: 'default-root',
        status: 'active',
      }))
      const start = (pageNum - 1) * pageSize
      return route.fulfill({
        json: {
          code: 200,
          data: {
            list: all.slice(start, start + pageSize),
            total: all.length,
            pageNum,
            pageSize,
          },
        },
      })
    }
    if (path === '/api/v1/admin/organizations/tree') {
      return route.fulfill({
        json: {
          code: 200,
          data: [
            {
              orgId: 1,
              orgCode: 'root',
              name: '根组织',
              status: 'active',
              builtIn: true,
              children: Array.from({ length: 500 }, (_, index) => ({
                orgId: index + 2,
                orgCode: `org-${index + 2}`,
                name: `基层组织 ${index + 1}`,
                parentId: 1,
                memberCount: index % 5,
                status: 'active',
                children: [],
              })),
            },
          ],
        },
      })
    }
    if (path.endsWith('/page') || path === '/api/v1/tasks' || path === '/api/v1/call-logs') {
      return route.fulfill({ json: { code: 200, data: { list: [], total: 0, pageNum: 1, pageSize: 10 } } })
    }
    return route.fulfill({ json: { code: 200, data: [] } })
  })
}

async function goToSecondPage(page: Page) {
  await page.locator('.app-pagination .el-pager .number').filter({ hasText: /^2$/ }).click()
}

test('服务端分页会携带新页码并替换当前页数据', async ({ page }) => {
  await authenticate(page)
  await page.goto('/spaces')

  await expect(page.getByText('知识空间 1', { exact: true })).toBeVisible()
  await expect(page.getByText('知识空间 11', { exact: true })).toHaveCount(0)
  await Promise.all([
    page.waitForRequest(
      (request) => request.url().includes('/api/v1/spaces/page') && request.url().includes('pageNum=2'),
    ),
    goToSecondPage(page),
  ])
  await expect(page.getByText('知识空间 11', { exact: true })).toBeVisible()
  await expect(page.getByText('知识空间 1', { exact: true })).toHaveCount(0)
})

test('用户列表使用同一控件完成服务端分页', async ({ page }) => {
  await authenticate(page)
  await page.goto('/access/users')

  await expect(page.getByText('用户 1', { exact: true })).toBeVisible()
  await expect(page.getByText('用户 21', { exact: true })).toHaveCount(0)
  await Promise.all([
    page.waitForRequest(
      (request) => request.url().includes('/api/v1/admin/users/page') && request.url().includes('pageNum=2'),
    ),
    goToSecondPage(page),
  ])
  await expect(page.getByText('用户 21', { exact: true })).toBeVisible()
  await expect(page.getByText('用户 1', { exact: true })).toHaveCount(0)
})

test('公共分页使用统一绿色主题和白色选中文字', async ({ page }) => {
  await authenticate(page)
  await page.goto('/spaces')
  await expect(page.locator('.app-pagination')).toBeVisible()

  const theme = await page.locator('.app-pagination').evaluate((wrapper) => {
    const active = wrapper.querySelector<HTMLElement>('.el-pager li.is-active')!
    const wrapperStyle = getComputedStyle(wrapper)
    const activeStyle = getComputedStyle(active)
    return {
      primary: wrapperStyle.getPropertyValue('--el-color-primary').trim(),
      activeBackground: activeStyle.backgroundColor,
      activeColor: activeStyle.color,
    }
  })

  expect(theme.primary).toMatch(/^oklch\(48% (?:0?\.)11 187(?:deg)?\)$/)
  expect(theme.activeBackground).toMatch(/^oklch\((?:0\.48|48%) (?:0?\.)11 187(?:deg)?\)$/)
  expect(theme.activeColor).toBe('rgb(255, 255, 255)')
})

test('超过 500 个组织节点时切换为虚拟树', async ({ page }) => {
  await authenticate(page)
  await page.goto('/access/organizations')

  await expect(page.getByRole('tree')).toBeVisible()
  await expect(page.getByText('根组织', { exact: true })).toBeVisible()
  const renderedRows = await page.locator('.virtual-org-row').count()
  expect(renderedRows).toBeGreaterThan(0)
  expect(renderedRows).toBeLessThan(501)
})
