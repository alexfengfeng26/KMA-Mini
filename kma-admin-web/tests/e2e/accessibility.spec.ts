import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page, type Route } from '@playwright/test'
import { portalBootstrap } from './support/portalBootstrap'

async function expectNoSeriousAccessibilityViolations(page: Page) {
  await expect(page.locator('.el-zoom-in-center-enter-active')).toHaveCount(0)
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze()
  const blocking = results.violations.filter(
    (violation) => violation.impact === 'critical' || violation.impact === 'serious',
  )
  expect(blocking, blocking.map((violation) => `${violation.id}: ${violation.help}`).join('\n')).toEqual([])
}

async function mockAuthenticatedShell(page: Page) {
  await page.addInitScript(() => {
    sessionStorage.setItem('kma_access_token', 'accessibility-token')
    sessionStorage.setItem('kma_must_change_password', 'false')
  })
  await page.route('**/api/v1/**', async (route: Route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/v1/auth/me') {
      return route.fulfill({
        json: {
          code: 200,
          data: {
            userId: 1,
            username: 'accessibility-auditor',
            displayName: '无障碍审计员',
            permissions: ['kma:admin'],
            roles: ['kma-admin'],
            organizationCodes: ['root'],
            mustChangePassword: false,
          },
        },
      })
    }
    if (path === '/api/v1/portal-sites/default/bootstrap') {
      return route.fulfill({
        json: {
          code: 200,
          data: portalBootstrap({
            config: {
              unit_name: '党建知识服务中心',
              help_text: '所有回答均来自已发布的权威资料。',
            },
            categories: [
              { content_type: 'party_constitution', name: '党章党规', total: 2 },
              { content_type: 'policy', name: '政策文件', total: 3 },
            ],
            recent: [],
            topics: [],
            history: [],
            favorites: [],
          }),
        },
      })
    }
    if (path === '/api/v1/metrics/dashboard') {
      return route.fulfill({
        json: {
          code: 200,
          data: { docCount: 12, chunkCount: 80, pendingTaskCount: 1, failedTaskCount: 0 },
        },
      })
    }
    if (path === '/api/v1/system/dependencies') {
      return route.fulfill({
        json: {
          code: 200,
          data: { core: { status: 'UP', details: {} }, models: { status: 'UP', details: {} } },
        },
      })
    }
    return route.fulfill({ json: { code: 200, data: [] } })
  })
}

test('登录页无严重或致命无障碍问题', async ({ page }) => {
  await page.goto('/login')
  await expect(page.getByRole('button', { name: '登录知识中心' })).toBeVisible()
  await expectNoSeriousAccessibilityViolations(page)
})

test('党员门户首页无严重或致命无障碍问题', async ({ page }) => {
  await mockAuthenticatedShell(page)
  await page.goto('/p/default/home')
  await expect(page.getByRole('button', { name: '查权威文件' })).toBeVisible()
  await expectNoSeriousAccessibilityViolations(page)
})

test('治理后台首页无严重或致命无障碍问题', async ({ page }) => {
  await mockAuthenticatedShell(page)
  await page.goto('/console/dashboard')
  await expect(page.getByRole('heading', { name: '系统依赖状态' })).toBeVisible()
  await expectNoSeriousAccessibilityViolations(page)
})

test('门户首页满足 LCP、CLS 和 INP 性能预算', async ({ page }) => {
  await page.addInitScript(() => {
    const metrics = { lcp: 0, cls: 0, inp: 0 }
    Object.defineProperty(window, '__kmaWebVitals', { value: metrics })

    new PerformanceObserver((list) => {
      const entries = list.getEntries()
      const latest = entries.at(-1)
      if (latest) metrics.lcp = latest.startTime
    }).observe({ type: 'largest-contentful-paint', buffered: true })

    new PerformanceObserver((list) => {
      for (const entry of list.getEntries()) {
        const shift = entry as PerformanceEntry & { value: number; hadRecentInput: boolean }
        if (!shift.hadRecentInput) metrics.cls += shift.value
      }
    }).observe({ type: 'layout-shift', buffered: true })

    new PerformanceObserver((list) => {
      for (const entry of list.getEntries()) {
        const event = entry as PerformanceEntry & { duration: number; interactionId?: number }
        if (event.interactionId) metrics.inp = Math.max(metrics.inp, event.duration)
      }
    }).observe({ type: 'event', buffered: true, durationThreshold: 16 })
  })
  await mockAuthenticatedShell(page)
  await page.goto('/p/default/home')
  await expect(page.getByRole('button', { name: '查权威文件' })).toBeVisible()
  await page.reload()
  const search = page.getByPlaceholder('输入文件标题、文号、关键词或党建问题')
  await expect(search).toBeVisible()
  await search.fill('党章')
  await search.press('Tab')
  await page.evaluate(
    () =>
      new Promise<void>((resolve) => {
        requestAnimationFrame(() => requestAnimationFrame(() => resolve()))
      }),
  )

  const metrics = await page.evaluate(
    () =>
      (
        window as Window & {
          __kmaWebVitals: { lcp: number; cls: number; inp: number }
        }
      ).__kmaWebVitals,
  )
  expect(metrics.lcp).toBeGreaterThan(0)
  expect(metrics.lcp).toBeLessThanOrEqual(2_500)
  expect(metrics.cls).toBeLessThanOrEqual(0.1)
  expect(metrics.inp).toBeLessThanOrEqual(200)
})
