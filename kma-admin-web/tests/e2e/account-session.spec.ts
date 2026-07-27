import { expect, test } from '@playwright/test'

test('login always uses the single global runtime configuration', async ({ page }) => {
  const configurationRequests: string[] = []
  page.on('request', (request) => {
    const path = new URL(request.url()).pathname
    if (path.startsWith('/config/')) configurationRequests.push(path)
  })
  await page.goto('/login')
  await expect(page.getByLabel('用户名')).toBeVisible()
  await expect(page.getByLabel('密码')).toBeVisible()
  await expect(page.locator('input')).toHaveCount(2)
  expect(configurationRequests).toContain('/config/kma-runtime.json')
})
