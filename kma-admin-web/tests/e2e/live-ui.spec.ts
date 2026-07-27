import { expect, test } from '@playwright/test'

const pages = [
  ['/dashboard', '运行概览'],
  ['/spaces', '知识空间'],
  ['/datasets', '数据集与向量'],
  ['/documents', '技术文档'],
  ['/storage', '存储生命周期'],
  ['/retrieval', '检索调试'],
  ['/qa', '问答实验室'],
  ['/tasks', '任务与死信'],
  ['/models', '模型配置'],
  ['/evaluations', 'RAG 评测'],
  ['/audit', '调用与安全审计'],
  ['/access/users', '用户管理'],
  ['/access/roles', '角色权限'],
  ['/access/organizations', '组织管理'],
  ['/console/profile', '个人与密码'],
] as const

test('live backend: login, password rotation, core workflow and every management page', async ({ page }) => {
  test.setTimeout(90_000)
  test.skip(process.env.KMA_LIVE_UI !== 'true', '需要显式启用真实 KMA 后端联调')
  const initialPassword = process.env.KMA_LIVE_INITIAL_PASSWORD
  const password = process.env.KMA_LIVE_PASSWORD
  expect(initialPassword, 'KMA_LIVE_INITIAL_PASSWORD is required').toBeTruthy()
  expect(password, 'KMA_LIVE_PASSWORD is required').toBeTruthy()

  const runtimeErrors: string[] = []
  const apiFailures: string[] = []
  page.on('pageerror', (error) => runtimeErrors.push(error.message))
  page.on('console', (message) => {
    if (message.type() === 'error') runtimeErrors.push(message.text())
  })
  page.on('response', (response) => {
    if (response.url().includes('/api/') && response.status() >= 400) {
      apiFailures.push(`${response.status()} ${new URL(response.url()).pathname}`)
    }
  })

  await page.goto('/login')
  await page.getByLabel('用户名').fill('admin')
  await page.getByLabel('密码').fill(initialPassword!)
  await page.getByRole('button', { name: '登录知识中心' }).click()
  await expect(page).toHaveURL(/\/profile$/)

  await page.getByLabel('当前密码').fill(initialPassword!)
  await page.getByLabel('新密码（至少 12 位）').fill(password!)
  await page.getByLabel('确认新密码').fill(password!)
  await page.getByRole('button', { name: '修改密码并撤销令牌' }).click()
  await expect(page.getByText('密码已修改，所有刷新令牌已撤销，请重新登录。')).toBeVisible()
  await expect(page).toHaveURL(/\/login$/, { timeout: 8_000 })

  await page.getByLabel('用户名').fill('admin')
  await page.getByLabel('密码').fill(password!)
  await page.getByRole('button', { name: '登录知识中心' }).click()
  await expect(page).toHaveURL(/\/p\/default\/home$/)
  apiFailures.length = 0
  runtimeErrors.length = 0

  await page.goto('/datasets')
  await page.getByRole('button', { name: '创建数据集' }).click()
  await page.getByLabel('数据集名称').fill('UI 全链路数据集')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByText('UI 全链路数据集', { exact: true })).toBeVisible()

  await page.goto('/spaces')
  await page.getByRole('button', { name: '创建知识空间' }).click()
  const spaceDialog = page.getByRole('dialog', { name: '创建空间' })
  await spaceDialog.getByLabel('空间编码').fill('ui-live')
  await spaceDialog.getByLabel('名称').fill('UI 全链路空间')
  await spaceDialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByText('UI 全链路空间', { exact: true })).toBeVisible()

  await page.goto('/documents')
  await page.getByRole('button', { name: '添加文档' }).click()
  const documentDialog = page.getByRole('dialog', { name: '添加文档' })
  await documentDialog.getByRole('button', { name: '粘贴文本' }).click()
  await documentDialog.getByLabel('空间编码').fill('ui-live')
  await documentDialog.getByLabel('标题').fill('UI 全链路知识')
  await documentDialog
    .getByPlaceholder('粘贴需要入库的知识正文')
    .fill('这是 KMA 真实前后端 UI 联调产生的测试知识。')
  await documentDialog.getByRole('button', { name: '提交入库' }).click()
  await expect(page.getByText('UI 全链路知识', { exact: true })).toBeVisible()

  for (const [path, title] of pages) {
    await page.goto(path)
    await expect(page.locator('.topbar h1')).toHaveText(title)
    await expect(page.locator('.main-area')).toBeVisible()
  }

  await page.setViewportSize({ width: 390, height: 844 })
  for (const [path] of pages) {
    await page.goto(path)
    await expect(page.locator('.main-area')).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow, `${path} overflows live mobile viewport`).toBeLessThanOrEqual(1)
  }

  expect(apiFailures).toEqual([])
  expect(runtimeErrors).toEqual([])
})
