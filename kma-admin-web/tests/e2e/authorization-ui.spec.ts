import { expect, test, type Page, type Route } from '@playwright/test'

type Persona = {
  name: string
  permissions: string[]
  expectedPath: string
  menu: string[]
  organizations?: string[]
}

const personas: Persona[] = [
  {
    name: '管理员',
    permissions: ['kma:admin'],
    expectedPath: '/console/contents',
    menu: [
      '内容库',
      '审核中心',
      '发布管理',
      '分类专题',
      '门户配置',
      '门户设计中心',
      '知识空间',
      '技术文档',
      '数据集与向量',
      '问答实验室',
      '检索调试',
      'RAG 评测',
      '运行概览',
      '任务与死信',
      '模型配置',
      '存储生命周期',
      '调用与安全审计',
      '用户管理',
      '角色权限',
      '组织管理',
    ],
  },
  {
    name: '编辑者',
    permissions: [
      'space:read',
      'document:read',
      'document:ingest',
      'task:read',
      'task:retry',
      'retrieval:use',
      'qa:use',
      'chat:read',
    ],
    expectedPath: '/console/spaces',
    menu: ['知识空间', '技术文档', '问答实验室', '检索调试', '任务与死信'],
  },
  {
    name: '只读用户',
    permissions: ['space:read', 'document:read', 'retrieval:use', 'qa:use', 'chat:read'],
    expectedPath: '/console/spaces',
    menu: ['知识空间', '技术文档', '问答实验室', '检索调试'],
  },
  {
    name: '审计员',
    permissions: ['dashboard:read', 'audit:call:read', 'audit:security:read'],
    expectedPath: '/console/dashboard',
    menu: ['运行概览', '调用与安全审计'],
  },
  {
    name: '组织成员',
    permissions: ['space:read', 'document:read', 'retrieval:use', 'qa:use', 'chat:read'],
    expectedPath: '/console/spaces',
    menu: ['知识空间', '技术文档', '问答实验室', '检索调试'],
    organizations: ['east-community'],
  },
]

async function authenticate(page: Page, persona: Persona) {
  await page.addInitScript(() => {
    sessionStorage.setItem('kma_access_token', 'authorization-ui-token')
    sessionStorage.setItem('kma_must_change_password', 'false')
  })
  await page.route('**/api/v1/**', async (route: Route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/v1/auth/me')
      return route.fulfill({
        json: {
          code: 200,
          data: {
            userId: 10,
            username: persona.name,
            displayName: persona.name,
            permissions: persona.permissions,
            roles: [],
            organizationCodes: persona.organizations || [],
            authorizationVersion: 1,
            mustChangePassword: false,
          },
        },
      })
    if (path === '/api/v1/metrics/dashboard')
      return route.fulfill({
        json: { code: 200, data: { docCount: 9, chunkCount: 12, pendingTaskCount: 2 } },
      })
    if (path === '/api/v1/system/dependencies')
      return route.fulfill({
        json: {
          code: 200,
          data: { core: { status: 'UP', details: {} }, models: { status: 'DEGRADED', details: {} } },
        },
      })
    if (path === '/api/v1/spaces/page')
      return route.fulfill({
        json: {
          code: 200,
          data: {
            list: [
              {
                spaceCode: 'grassroot-cases',
                name: '基层实践案例',
                embeddingModel: 'BAAI/bge-m3',
                embeddingDim: 1024,
                defaultTopK: 6,
                status: 'active',
              },
            ],
          },
        },
      })
    if (path.endsWith('/page') || path === '/api/v1/tasks' || path === '/api/v1/call-logs')
      return route.fulfill({ json: { code: 200, data: { list: [] } } })
    if (path === '/api/v1/tasks/stats') return route.fulfill({ json: { code: 200, data: {} } })
    return route.fulfill({ json: { code: 200, data: [] } })
  })
}

for (const persona of personas) {
  test(`${persona.name}只看到被授权菜单，未授权直达进入 403`, async ({ page }) => {
    await authenticate(page, persona)
    await page.goto('/console')
    await expect(page).toHaveURL(new RegExp(`${persona.expectedPath}$`))
    const navigation = page.getByRole('navigation', { name: '主导航' })
    await expect(navigation.getByRole('link')).toHaveText(persona.menu)

    if (!persona.permissions.includes('kma:admin')) {
      await page.goto('/access/users')
      await expect(page).toHaveURL(/\/403$/)
      await expect(page.getByRole('heading', { name: '没有访问该页面的权限' })).toBeVisible()
    }
  })
}

test('编辑者只能看到被授权的业务按钮', async ({ page }) => {
  await authenticate(page, personas[1])
  await page.goto('/spaces')
  await expect(page.getByRole('button', { name: '创建知识空间' })).toHaveCount(0)
  await page.goto('/documents')
  await expect(page.getByRole('button', { name: '添加文档' })).toBeVisible()
})

test('只读用户不能看到入库按钮，组织成员只接收授权空间', async ({ page }) => {
  await authenticate(page, personas[4])
  await page.goto('/spaces')
  await expect(page.getByText('基层实践案例', { exact: true })).toBeVisible()
  await expect(page.getByRole('row')).toHaveCount(2)
  await page.goto('/documents')
  await expect(page.getByRole('button', { name: '添加文档' })).toHaveCount(0)
})
