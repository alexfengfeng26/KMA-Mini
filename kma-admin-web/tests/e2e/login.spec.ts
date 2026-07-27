import { expect, test } from '@playwright/test'
import { portalBootstrap } from './support/portalBootstrap'

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/metrics/dashboard', (route) => route.fulfill({ json: { code: 200, data: {} } }))
  await page.route('**/api/v1/system/dependencies', (route) =>
    route.fulfill({ json: { code: 200, data: {} } }),
  )
  await page.route('**/api/v1/portal-sites/default/bootstrap**', (route) =>
    route.fulfill({
      json: {
        code: 0,
        data: portalBootstrap(),
      },
    }),
  )
  await page.route('**/api/v1/portal-sites/default/events', (route) =>
    route.fulfill({ json: { code: 200, data: null } }),
  )
})

test('login page presents local authentication', async ({ page }) => {
  await page.route('**/api/v1/auth/oidc/config', (route) =>
    route.fulfill({ json: { code: 0, data: { enabled: false, mode: 'local' } } }),
  )
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: '登录管理中心' })).toBeVisible()
  await expect(page.getByText('HttpOnly Cookie')).toBeVisible()
  await expect(page.getByRole('button', { name: '登录知识中心' })).toBeEnabled()
})

test('local account login stores the access token and enters the personal portal fallback', async ({
  page,
}) => {
  await page.addInitScript(() => sessionStorage.setItem('kma_access_token', 'expired-access-token'))
  await page.route('**/api/v1/auth/oidc/config', (route) =>
    route.fulfill({ json: { code: 0, data: { enabled: false, mode: 'local' } } }),
  )
  await page.route('**/api/v1/auth/login', (route) => {
    expect(route.request().headers().authorization).toBeUndefined()
    return route.fulfill({
      json: {
        code: 0,
        data: {
          accessToken: 'local-access-token',
          userId: 1,
          username: 'admin',
          displayName: 'KMA 管理员',
          permissions: [],
          roles: ['kma-admin'],
          organizationCodes: [],
          authorizationVersion: 1,
          mustChangePassword: false,
        },
      },
    })
  })
  await page.goto('/login')
  await page.getByLabel('用户名').fill('admin')
  await page.getByLabel('密码').fill('correct-password')
  await page.getByRole('button', { name: '登录知识中心' }).click()
  await expect(page).toHaveURL(/\/p\/default\/profile$/)
  await expect
    .poll(() => page.evaluate(() => sessionStorage.getItem('kma_access_token')))
    .toBe('local-access-token')
})
