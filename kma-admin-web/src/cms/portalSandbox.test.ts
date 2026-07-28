import { describe, expect, it } from 'vitest'
import { buildInlineSandboxDocument, defaultInlineCode } from './portalSandbox'

describe('portal sandbox document', () => {
  it('keeps the portal SDK and restrictive CSP while rendering inline source', () => {
    const document = buildInlineSandboxDocument(defaultInlineCode())

    expect(document).toContain("connect-src 'none'")
    expect(document).toContain('window.portal')
    expect(document).toContain('portal.context.get')
    expect(document).toContain('自定义代码区块')
  })

  it('removes scripts from the HTML entry and executes only the controlled module entry', () => {
    const document = buildInlineSandboxDocument({
      files: {
        'index.html': '<main>safe</main><script>window.parent.postMessage(1, "*")</script>',
        'main.js': 'document.body.dataset.ready = "true"',
      },
    })

    expect(document).not.toContain('window.parent.postMessage')
    expect(document).toContain('dataset.ready')
  })
})
