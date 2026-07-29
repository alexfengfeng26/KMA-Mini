import { describe, expect, it } from 'vitest'
import type { PortalBootstrap, PortalThemeRuntime } from '../siteConfig'
import { buildThemeDocument } from './themeRuntime'

function fixture(files: Record<string, string>) {
  const runtime: PortalThemeRuntime = {
    versionId: 2,
    versionNo: 2,
    status: 'draft',
    manifest: { capabilities: ['page-context'] },
    checksum: 'test',
    themeKey: 'default-theme',
    displayName: '测试主题',
    files,
  }
  const bootstrap = {
    site: { siteKey: 'default', name: '<script>unsafe</script>' },
    page: { slug: 'home', kind: 'home', title: '首页', template: 'pages/home.html' },
    portalData: {
      recent: [{ contentId: 7, title: '<img src=x>', summary: '安全摘要' }],
      topics: [],
      favorites: [],
      history: [],
    },
  } as unknown as PortalBootstrap
  return { runtime, bootstrap }
}

describe('Portal Theme V4 runtime', () => {
  it('escapes Liquid output, expands partials and renders controlled widgets', () => {
    const { runtime, bootstrap } = fixture({
      'layout.html': '{% include "partials/header.html" %}<kma-slot name="content" />',
      'partials/header.html': '<header>{{ site.name }}</header>',
      'pages/home.html':
        '{% if page.title %}<h1>{{ page.title | upcase }}</h1>{% endif %}<kma-widget name="content-list" />',
      'styles/theme.css': '.card{color:red}',
      'scripts/theme.js': '',
    })

    const document = buildThemeDocument(runtime, bootstrap)
    expect(document).toContain('&lt;script&gt;unsafe&lt;/script&gt;')
    expect(document).toContain('&lt;img src=x&gt;')
    expect(document).toContain('<h1>首页</h1>')
    expect(document).toContain("connect-src 'none'")
    expect(document).toContain('event.preventDefault()')
    expect(document).toContain('let navigationPending = false')
    expect(document).toContain('withNavigationLock')
    expect(document).toContain('.finally(()=>{navigationPending=false})')
    expect(document).not.toContain('<script>unsafe</script>')
  })

  it('embeds local assets and rewrites relative ES modules into the immutable snapshot', () => {
    const { runtime, bootstrap } = fixture({
      'layout.html': '<main>{% slot content %}</main>',
      'pages/home.html': '<img src="assets/logo.png">',
      'styles/theme.css': 'body{background:url("assets/logo.png")}',
      'scripts/theme.js': "import { ready } from './helper.js'; document.body.dataset.ready=ready;",
      'scripts/helper.js': 'export const ready="yes";',
      'assets/logo.png': 'data:image/png;base64,AA==',
    })

    const document = buildThemeDocument(runtime, bootstrap)
    expect(document).toContain('data:image/png;base64,AA==')
    expect(document).toContain('data:text/javascript;charset=utf-8,')
    expect(document).not.toContain("from './helper.js'")
  })
})
