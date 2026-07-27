import { expect, test, type Page, type Route } from '@playwright/test'
import { portalBootstrap } from './support/portalBootstrap'

const published = {
  contentId: 101,
  spaceId: 1,
  spaceCode: 'party-regulations',
  spaceName: '党章党规库',
  title: '中国共产党章程（现行整理版）',
  contentType: 'party_constitution',
  documentNumber: '党章（2022年修改）',
  issuingAuthority: '中国共产党第二十次全国代表大会',
  publishDate: '2022-10-22',
  effectiveDate: '2022-10-22',
  validityStatus: 'effective',
  workflowStatus: 'published',
  reviewDecision: 'approved',
  online: true,
  active: true,
  sourceVersion: 2,
  summary: '集中规定党的性质宗旨、党员义务和组织制度。',
  favorite: false,
}

async function mockPortal(page: Page, management = false) {
  let workflowStatus = 'reviewing',
    reviewDecision = 'pending',
    online = false
  await page.addInitScript(() => {
    sessionStorage.setItem('kma_access_token', 'party-portal-token')
    sessionStorage.setItem('kma_must_change_password', 'false')
  })
  await page.route('**/api/v1/**', async (route: Route) => {
    const url = new URL(route.request().url()),
      path = url.pathname
    if (path === '/api/v1/auth/me')
      return route.fulfill({
        json: {
          code: 200,
          data: {
            userId: 1,
            username: management ? 'content-admin' : 'party-member',
            permissions: management
              ? [
                  'content:read',
                  'content:create',
                  'content:update',
                  'content:submit',
                  'content:review',
                  'content:publish',
                  'topic:manage',
                  'portal:configure',
                ]
              : ['content:read', 'qa:use'],
            roles: [management ? 'knowledge-admin' : 'knowledge-reader'],
            organizationCodes: ['root'],
            mustChangePassword: false,
          },
        },
      })
    if (path === '/api/v1/portal-sites/default/bootstrap')
      return route.fulfill({
        json: {
          code: 200,
          data: portalBootstrap({
            config: { unit_name: '党建知识服务中心', help_text: '所有回答均来自已发布的权威资料。' },
            categories: [
              { content_type: 'party_constitution', name: '党章党规', total: 1 },
              { content_type: 'policy', name: '政策文件', total: 1 },
              { content_type: 'learning_material', name: '学习材料', total: 1 },
              { content_type: 'grassroots_case', name: '基层案例', total: 1 },
              { content_type: 'organization_system', name: '组织工作制度', total: 1 },
            ],
            recent: [published],
            topics: [
              { topic_code: 'party_constitution', name: '党章专题', description: '学习党章、遵守党章。' },
            ],
            history: [],
            favorites: [],
          }),
        },
      })
    if (path === '/api/v1/portal-sites/default/contents' && route.request().method() === 'GET')
      return route.fulfill({
        json: { code: 200, data: { list: [published], total: 1, pageNum: 1, pageSize: 20 } },
      })
    if (path === '/api/v1/portal-sites/default/contents/101/source')
      return route.fulfill({
        status: 200,
        headers: {
          'content-type': 'application/pdf',
          'content-disposition': "inline; filename*=UTF-8''party-rules.pdf",
        },
        body: '%PDF-1.4 KMA test document',
      })
    if (path === '/api/v1/portal-sites/default/contents/101')
      return route.fulfill({
        json: {
          code: 200,
          data: {
            ...published,
            mimeType: 'application/pdf',
            topicCodes: ['party_constitution'],
            sections: [
              {
                chunk_id: 1,
                chunk_index: 0,
                content: '中国共产党是中国工人阶级的先锋队，是中国特色社会主义事业的领导核心。',
              },
            ],
            versions: [{ content_id: 101, source_version: 2, active: true, workflow_status: 'published' }],
            related: [{ ...published, contentId: 102, title: '中国共产党党员教育管理工作条例' }],
          },
        },
      })
    if (path === '/api/v1/portal-sites/default/contents/102')
      return route.fulfill({
        json: {
          code: 200,
          data: {
            ...published,
            contentId: 102,
            title: '中国共产党党员教育管理工作条例',
            documentNumber: '中发〔2019〕17号',
            sections: [
              {
                chunk_id: 2,
                chunk_index: 0,
                content: '党员教育管理是党的建设基础性经常性工作。',
              },
            ],
            versions: [{ content_id: 102, source_version: 1, active: true, workflow_status: 'published' }],
            related: [],
          },
        },
      })
    if (path === '/api/v1/portal/topics' || path === '/api/v1/admin/topics')
      return route.fulfill({
        json: {
          code: 200,
          data: [
            {
              topic_id: 1,
              topic_code: 'party_constitution',
              name: '党章专题',
              description: '学习党章、遵守党章。',
              enabled: true,
              featured: true,
            },
          ],
        },
      })
    if (path === '/api/v1/portal/favorites' && route.request().method() === 'GET')
      return route.fulfill({ json: { code: 200, data: [] } })
    if (path === '/api/v1/portal/favorites' && route.request().method() === 'POST')
      return route.fulfill({ json: { code: 200, data: 9 } })
    if (path === '/api/v1/portal/history') return route.fulfill({ json: { code: 200, data: [] } })
    if (path === '/api/v1/portal-sites/default/ask/stream')
      return route.fulfill({
        status: 200,
        headers: { 'content-type': 'text/event-stream' },
        body: `event: citations\ndata: [{"chunkId":1,"docId":101,"docTitle":"中国共产党章程（现行整理版）","documentNumber":"党章（2022年修改）","issuingAuthority":"中国共产党第二十次全国代表大会","validityStatus":"effective","chunkIndex":0,"section":"总纲","content":"中国共产党是中国工人阶级的先锋队。"}]\n\nevent: message\ndata: 中国共产党是中国特色社会主义事业的领导核心。\n\nevent: done\ndata: 88\n\n`,
      })
    if (path === '/api/v1/admin/contents' && route.request().method() === 'GET')
      return route.fulfill({
        json: {
          code: 200,
          data: {
            list: [
              {
                ...published,
                contentId: 202,
                title: '基层组织生活记录工作提示',
                parseStatus: 'completed',
                workflowStatus,
                reviewDecision,
                online,
              },
            ],
            total: 1,
            pageNum: 1,
            pageSize: 20,
          },
        },
      })
    if (path === '/api/v1/admin/contents/202/approve') {
      reviewDecision = 'approved'
      return route.fulfill({ json: { code: 200 } })
    }
    if (path === '/api/v1/admin/contents/202/publish') {
      workflowStatus = 'published'
      online = true
      return route.fulfill({ json: { code: 200 } })
    }
    return route.fulfill({ json: { code: 200, data: [] } })
  })
}

test('党员从首页检索到打开权威正文不超过三次操作', async ({ page }) => {
  await mockPortal(page)
  await page.goto('/p/default/home')
  await expect(page.getByText('权威资料检索', { exact: true })).toBeVisible()
  await expect(page.locator('.portal-hero h1')).toHaveCount(0)
  await page.getByPlaceholder('输入文件标题、文号、关键词或党建问题').fill('党章')
  await page.getByRole('button', { name: '查权威文件' }).click()
  await expect(page).toHaveURL(/\/p\/default\/library\?keyword=/)
  await Promise.all([
    page.waitForURL('/p/default/content/101'),
    page.getByRole('link', { name: published.title }).click(),
  ])
  await expect(page.getByText('中国共产党是中国工人阶级的先锋队')).toBeVisible()
  await expect(page.getByText('党章（2022年修改）')).toBeVisible()
})

test('AI 回答显示权威引用并能定位正文', async ({ page }) => {
  await mockPortal(page)
  await page.goto('/p/default/ask')
  await page.getByPlaceholder(/基层党组织/).fill('党的性质是什么？')
  await page.getByRole('button', { name: '生成有依据的回答' }).click()
  await expect(page.getByText('中国共产党是中国特色社会主义事业的领导核心。')).toBeVisible()
  await expect(page.getByText('党章（2022年修改）')).toBeVisible()
  await page.getByRole('button', { name: /中国共产党章程/ }).click()
  await expect(page).toHaveURL(/\/p\/default\/content\/101/)
})

test('同一阅读组件切换文档会刷新正文，原文件下载携带认证', async ({ page }) => {
  await mockPortal(page)
  await page.goto('/p/default/content/101')

  const sourceRequest = page.waitForRequest(
    (request) => new URL(request.url()).pathname === '/api/v1/portal-sites/default/contents/101/source',
  )
  await page.getByRole('button', { name: '打开原始文件' }).click()
  expect((await sourceRequest).headers().authorization).toBe('Bearer party-portal-token')

  await page.getByRole('link', { name: '中国共产党党员教育管理工作条例' }).click()
  await expect(page).toHaveURL('/p/default/content/102')
  await expect(page.getByRole('heading', { name: '中国共产党党员教育管理工作条例' })).toBeVisible()
  await expect(page.getByText('党员教育管理是党的建设基础性经常性工作。')).toBeVisible()
  await expect(page.getByText('中国共产党是中国工人阶级的先锋队')).toHaveCount(0)
})

test('资料筛选、分页与浏览器返回保持 URL 和结果一致', async ({ page }) => {
  await mockPortal(page)
  await page.goto('/p/default/library')

  const keyword = page.getByPlaceholder('标题、正文、文号组合搜索')
  await keyword.fill('党章')
  await page.getByRole('button', { name: '搜索' }).click()
  await expect(page).toHaveURL(/keyword=%E5%85%9A%E7%AB%A0/)

  await keyword.fill('组织工作')
  await page.getByRole('button', { name: '搜索' }).click()
  await expect(page).toHaveURL(/keyword=%E7%BB%84%E7%BB%87%E5%B7%A5%E4%BD%9C/)

  await page.goBack()
  await expect(page).toHaveURL(/keyword=%E5%85%9A%E7%AB%A0/)
  await expect(keyword).toHaveValue('党章')
})

test('内容管理员完成审核通过和发布流程', async ({ page }) => {
  await mockPortal(page, true)
  await page.goto('/console/reviews')
  await expect(page.getByText('待审核', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '通过' }).click()
  await page.getByRole('button', { name: '确定' }).click()
  await expect(page.getByText('审核通过，待发布')).toBeVisible()
  await page.getByRole('button', { name: '发布' }).click()
  await page.getByRole('button', { name: '确定' }).click()
  await expect(page.getByText('已发布', { exact: true })).toBeVisible()
})

test('内容长表单离开前提示未保存修改', async ({ page }) => {
  await mockPortal(page, true)
  await page.goto('/console/contents')
  await page.getByRole('button', { name: '新增内容' }).click()
  await page.getByLabel('标题').fill('尚未保存的党建材料')

  await page.getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '未保存的修改' })).toBeVisible()
  await page.getByRole('button', { name: '继续编辑' }).click()
  await expect(page.getByRole('dialog', { name: '新增党建内容' })).toBeVisible()

  await page.getByRole('button', { name: '取消', exact: true }).click()
  await page.getByRole('button', { name: '放弃修改' }).click()
  await expect(page.getByRole('dialog', { name: '新增党建内容' })).toHaveCount(0)
})

test('390px 门户首屏无页面级横向溢出', async ({ page }) => {
  await mockPortal(page)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/p/default/home')
  await expect(page.getByRole('button', { name: '查权威文件' })).toBeVisible()
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  )
  expect(overflow).toBeLessThanOrEqual(1)
})

test('门户桌面密度与长文阅读尺寸符合紧凑规范', async ({ page }) => {
  await mockPortal(page)
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/p/default/home')

  const homeMetrics = {
    baseFont: await page
      .locator('.portal-shell')
      .evaluate((element) => Number.parseFloat(getComputedStyle(element).fontSize)),
    headerHeight: await page
      .locator('.portal-header-inner')
      .evaluate((element) => element.getBoundingClientRect().height),
    controlHeight: await page
      .getByRole('button', { name: '查权威文件' })
      .evaluate((element) => element.getBoundingClientRect().height),
    categoryHeight: await page
      .getByRole('button', { name: /党章党规/ })
      .evaluate((element) => element.getBoundingClientRect().height),
  }

  expect(homeMetrics.baseFont).toBe(14)
  expect(homeMetrics.headerHeight).toBeLessThanOrEqual(64)
  expect(homeMetrics.controlHeight).toBeGreaterThanOrEqual(34)
  expect(homeMetrics.controlHeight).toBeLessThanOrEqual(40)
  expect(homeMetrics.categoryHeight).toBeLessThanOrEqual(148)

  const homeAlignment = await page.evaluate(() => {
    const latest = document.querySelector<HTMLElement>('[data-cms-block="recent-documents"]')!
    const topic = document.querySelector<HTMLElement>('[data-cms-block="current-topic"]')!
    return {
      latestTitleTop: latest.querySelector('h2')!.getBoundingClientRect().top,
      topicTitleTop: topic.querySelector('h2')!.getBoundingClientRect().top,
      latestContentTop: latest.querySelector('.portal-document-list')!.getBoundingClientRect().top,
      topicContentTop: topic.querySelector('.portal-topic-card')!.getBoundingClientRect().top,
    }
  })
  expect(Math.abs(homeAlignment.latestTitleTop - homeAlignment.topicTitleTop)).toBeLessThanOrEqual(1)
  expect(Math.abs(homeAlignment.latestContentTop - homeAlignment.topicContentTop)).toBeLessThanOrEqual(1)

  await page.goto('/p/default/content/101')
  const readerMetrics = await page
    .locator('.reader-body p')
    .first()
    .evaluate((element) => ({
      fontSize: Number.parseFloat(getComputedStyle(element).fontSize),
      lineHeight: Number.parseFloat(getComputedStyle(element).lineHeight),
    }))
  expect(readerMetrics.fontSize).toBe(16)
  expect(readerMetrics.lineHeight).toBeCloseTo(28, 0)
})

test('门户关键页面在桌面、平板和手机宽度均无页面级溢出', async ({ page }) => {
  await mockPortal(page)
  const viewports = [
    { width: 1440, height: 900 },
    { width: 1024, height: 768 },
    { width: 390, height: 844 },
  ]
  const paths = ['/p/default/home', '/p/default/library', '/p/default/content/101', '/p/default/ask']

  for (const viewport of viewports) {
    await page.setViewportSize(viewport)
    for (const path of paths) {
      await page.goto(path)
      const overflow = await page.evaluate(
        () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
      )
      expect(overflow, `${path} overflows at ${viewport.width}px`).toBeLessThanOrEqual(1)
    }
  }

  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/p/default/home')
  const mobileControlHeight = await page
    .getByRole('button', { name: '查权威文件' })
    .evaluate((element) => element.getBoundingClientRect().height)
  expect(mobileControlHeight).toBeGreaterThanOrEqual(40)
})
