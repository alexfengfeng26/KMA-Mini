import { expect, test, type Page } from '@playwright/test'

async function authenticate(page: Page, permissions: string[]) {
  await page.addInitScript(() => {
    sessionStorage.setItem('kma_access_token', 'p22-access-token')
    sessionStorage.setItem('kma_must_change_password', 'false')
  })
  await page.route('**/api/v1/auth/me', (route) =>
    route.fulfill({
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
    }),
  )
}

test('permission-aware navigation hides unauthorized management areas', async ({ page }) => {
  await authenticate(page, ['qa:use'])
  await page.route('**/api/v1/metrics/dashboard', (route) => route.fulfill({ json: { code: 200, data: {} } }))
  await page.route('**/api/v1/system/dependencies', (route) =>
    route.fulfill({ json: { code: 200, data: {} } }),
  )
  await page.goto('/console')
  await expect(page).toHaveURL(/\/console\/qa$/)
  await expect(page.getByRole('link', { name: '问答实验室' })).toBeVisible()
  await expect(page.getByRole('link', { name: '模型配置' })).toHaveCount(0)
})

test('compact console shell removes the global topbar and keeps organization context in the sidebar', async ({
  page,
}) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await authenticate(page, ['qa:use', 'content:read'])
  await page.route('**/api/v1/chat/sessions**', (route) => route.fulfill({ json: { code: 200, data: [] } }))

  await page.goto('/console/qa')
  await expect(page.locator('.topbar')).toHaveCount(0)
  await expect(page.locator('.organization-identity-card')).toContainText('root')
  await expect(page.locator('.organization-identity-card')).toContainText('KMA Mini')
  await expect(page.locator('.sidebar-account-link')).toContainText('admin')
  await expect(page.locator('.sidebar-account-link')).toContainText('个人与密码')
  await expect(page.getByRole('link', { name: '返回门户' })).toBeVisible()
  await expect(page.getByRole('button', { name: '切换账号' })).toBeVisible()
  await expect(page.getByRole('button', { name: '退出登录' })).toBeVisible()

  const pageBox = await page.locator('.page').boundingBox()
  expect(pageBox?.y).toBeLessThanOrEqual(24)

  await page.setViewportSize({ width: 1024, height: 768 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await expect(page.locator('.organization-identity-card')).toBeVisible()
})

test('compact console shell exposes account actions from the mobile navigation drawer', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await authenticate(page, ['qa:use'])
  await page.route('**/api/v1/chat/sessions**', (route) => route.fulfill({ json: { code: 200, data: [] } }))

  await page.goto('/console/qa')
  const menuButton = page.getByRole('button', { name: '打开导航' })
  await expect(menuButton).toBeVisible()
  await menuButton.click()
  await expect(page.locator('#console-navigation')).toHaveClass(/mobile-open/)
  await expect(page.locator('.organization-identity-card')).toContainText('root')
  await expect(page.getByRole('button', { name: '退出登录' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
})

test('streaming QA renders citations, chunks and completion session', async ({ page }) => {
  await authenticate(page, ['qa:use'])
  await page.route('**/api/v1/chat/sessions**', (route) => route.fulfill({ json: { code: 200, data: [] } }))
  await page.route('**/api/v1/qa/stream', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'text/event-stream',
      body:
        'event: citations\ndata: [{"chunkId":1,"docTitle":"党章","content":"党员必须履行义务"}]\n\n' +
        'event: message\ndata: 根据党章，\n\nevent: message\ndata: 党员应履行规定义务。\n\n' +
        'event: done\ndata: 88\n\n',
    }),
  )
  await page.goto('/qa')
  await page.getByPlaceholder('输入一个需要知识依据的问题').fill('党员有哪些义务？')
  await page.getByRole('button', { name: '生成有依据的回答' }).click()
  await expect(page.getByText('根据党章，党员应履行规定义务。')).toBeVisible()
  await expect(page.getByText('党章', { exact: true })).toBeVisible()
  await expect(page.getByText('会话 88')).toBeVisible()
})

test('space ACL can be listed and added', async ({ page }) => {
  await authenticate(page, ['space:read', 'space:acl:manage'])
  await page.route('**/api/v1/spaces/page**', (route) =>
    route.fulfill({
      json: {
        code: 200,
        data: {
          list: [
            {
              spaceId: 10,
              spaceCode: 'party',
              name: '党建知识',
              embeddingModel: 'bge-m3',
              embeddingDim: 1024,
              defaultTopK: 6,
              status: 'active',
            },
          ],
        },
      },
    }),
  )
  await page.route('**/api/v1/datasets/list', (route) => route.fulfill({ json: { code: 200, data: [] } }))
  await page.route('**/api/v1/model-profiles**', (route) => route.fulfill({ json: { code: 200, data: [] } }))
  await page.route('**/api/v1/admin/access/principals**', (route) =>
    route.fulfill({
      json: {
        code: 200,
        data: [{ type: 'role', value: 'qa-user', label: '知识问答用户', secondary: 'qa-user' }],
      },
    }),
  )
  await page.route('**/api/v1/spaces/party/acl', async (route) => {
    if (route.request().method() === 'POST') {
      expect(route.request().postDataJSON()).toMatchObject({
        spaceId: 10,
        principalType: 'role',
        principalValue: 'qa-user',
        permission: 'read',
      })
      await route.fulfill({ json: { code: 200, data: 1 } })
    } else await route.fulfill({ json: { code: 200, data: [] } })
  })
  await page.goto('/spaces')
  await page.getByRole('button', { name: 'ACL' }).click()
  await page.getByRole('combobox', { name: '授权主体' }).click()
  await page.getByRole('option', { name: '知识问答用户 · qa-user' }).click()
  await page.getByRole('button', { name: '添加' }).click()
  await expect(page.getByRole('dialog')).toContainText('党建知识')
})

test('document upload and task retry execute real UI operations', async ({ page }) => {
  await authenticate(page, ['document:read', 'document:ingest', 'task:read', 'task:retry'])
  await page.route('**/api/v1/documents/page**', (route) =>
    route.fulfill({ json: { code: 200, data: { list: [] } } }),
  )
  let uploaded = false
  await page.route('**/api/v1/documents/file**', async (route) => {
    uploaded = true
    await route.fulfill({ json: { code: 200, data: { docId: 9, parseStatus: 'pending' } } })
  })
  await page.goto('/documents')
  await page.getByRole('button', { name: '添加文档' }).click()
  await page
    .locator('input[type=file]')
    .setInputFiles({ name: 'party.txt', mimeType: 'text/plain', buffer: Buffer.from('党建知识') })
  await page.getByRole('button', { name: '提交入库' }).click()
  await expect.poll(() => uploaded).toBe(true)

  let retried = false
  await page.route('**/api/v1/tasks?**', (route) =>
    route.fulfill({
      json: {
        code: 200,
        data: {
          list: [
            {
              taskId: 7,
              sourceType: 'file',
              spaceCode: 'party',
              status: 'dead',
              retryCount: 3,
              maxRetry: 3,
              errorMessage: '模型不可用',
            },
          ],
        },
      },
    }),
  )
  await page.route('**/api/v1/tasks/stats', (route) =>
    route.fulfill({ json: { code: 200, data: { dead: 1 } } }),
  )
  await page.route('**/api/v1/tasks/7/retry', async (route) => {
    retried = true
    await route.fulfill({ json: { code: 200 } })
  })
  await page.goto('/tasks')
  await page.getByRole('button', { name: '重试', exact: true }).click()
  await expect.poll(() => retried).toBe(true)
})

test('QA history, text ingestion, dataset creation and call detail close remaining UI workflows', async ({
  page,
}) => {
  await authenticate(page, [
    'qa:use',
    'chat:read',
    'document:read',
    'document:ingest',
    'dataset:read',
    'dataset:create',
    'audit:call:read',
  ])
  await page.route(
    (url) => url.pathname === '/api/v1/chat/sessions',
    (route) =>
      route.fulfill({
        json: {
          code: 200,
          data: [{ sessionId: 12, title: '党章问答', spaceCode: 'party', updateTime: '2026-07-21T10:00:00' }],
        },
      }),
  )
  await page.route('**/api/v1/chat/sessions/12/messages', (route) =>
    route.fulfill({
      json: {
        code: 200,
        data: [
          { messageId: 1, sessionId: 12, role: 'user', content: '党的宗旨是什么？' },
          { messageId: 2, sessionId: 12, role: 'assistant', content: '全心全意为人民服务。' },
        ],
      },
    }),
  )
  await page.goto('/qa')
  await page.getByRole('button', { name: /党章问答/ }).click()
  await expect(page.getByText('全心全意为人民服务。')).toBeVisible()

  await page.route('**/api/v1/documents/page**', (route) =>
    route.fulfill({ json: { code: 200, data: { list: [] } } }),
  )
  let textIngested = false
  await page.route('**/api/v1/documents/text', async (route) => {
    textIngested = route.request().postDataJSON().content === '党建知识正文'
    await route.fulfill({ json: { code: 200, data: { docId: 18, parseStatus: 'pending' } } })
  })
  await page.goto('/documents')
  await page.getByRole('button', { name: '添加文档' }).click()
  await page.getByRole('button', { name: '粘贴文本' }).click()
  await page.getByLabel('标题').fill('党建知识')
  await page.getByPlaceholder('粘贴需要入库的知识正文').fill('党建知识正文')
  await page.getByRole('button', { name: '提交入库' }).click()
  await expect.poll(() => textIngested).toBe(true)

  await page.route('**/api/v1/datasets/page**', (route) =>
    route.fulfill({ json: { code: 200, data: { list: [], total: 0, pageNum: 1, pageSize: 10 } } }),
  )
  await page.route('**/api/v1/model-profiles', (route) => route.fulfill({ json: { code: 200, data: [] } }))
  let datasetCreated = false
  await page.route('**/api/v1/datasets', async (route) => {
    datasetCreated = route.request().method() === 'POST' && route.request().postDataJSON().name === '新数据集'
    await route.fulfill({ json: { code: 200, data: 21 } })
  })
  await page.goto('/datasets')
  await page.getByRole('button', { name: '创建数据集' }).click()
  await page.getByLabel('数据集名称').fill('新数据集')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => datasetCreated).toBe(true)

  await page.route('**/api/v1/call-logs?**', (route) =>
    route.fulfill({
      json: {
        code: 200,
        data: { list: [{ logId: 31, username: 'admin', query: '脱敏问题', status: 'success' }] },
      },
    }),
  )
  await page.route('**/api/v1/security-audits**', (route) => route.fulfill({ json: { code: 200, data: [] } }))
  await page.route('**/api/v1/call-logs/31', (route) =>
    route.fulfill({ json: { code: 200, data: { logId: 31, hitCount: 4, costMillis: 120 } } }),
  )
  await page.goto('/audit')
  await page.getByRole('button', { name: '详情' }).click()
  await expect(page.getByRole('dialog')).toContainText('120')
})

test('retrieval debug exposes score decomposition', async ({ page }) => {
  await authenticate(page, ['retrieval:use'])
  await page.route('**/api/v1/retrieval/debug', (route) =>
    route.fulfill({
      json: {
        code: 200,
        data: {
          finalHits: [
            {
              chunkId: 1,
              docTitle: '制度',
              content: '内容',
              vectorScore: 0.81,
              fullTextScore: 0.72,
              rrfScore: 0.03,
              rerankScore: 0.91,
            },
          ],
        },
      },
    }),
  )
  await page.goto('/retrieval')
  await page.getByLabel('检索问题').fill('测试问题')
  await page.getByRole('button', { name: '运行混合检索' }).click()
  await expect(page.getByText('0.91')).toBeVisible()
})
