import { describe, expect, it } from 'vitest'
import { normalizeThemeRuntime } from './portalSites'

const manifest = {
  kind: 'portal-theme',
  entry: 'layout.html',
  capabilities: ['page-context', 'contents', 'navigation'],
}

describe('normalizeThemeRuntime', () => {
  it('keeps a normal theme manifest intact', () => {
    expect(
      normalizeThemeRuntime({
        versionId: 1,
        versionNo: 1,
        status: 'published',
        manifest,
        files: {},
      })?.manifest,
    ).toEqual(manifest)
  })

  it('unwraps legacy PostgreSQL jsonb driver values', () => {
    expect(
      normalizeThemeRuntime({
        versionId: 1,
        versionNo: 1,
        status: 'published',
        manifest: {
          type: 'jsonb',
          value: JSON.stringify({ type: 'jsonb', value: JSON.stringify(manifest) }),
        },
        files: {},
      })?.manifest.capabilities,
    ).toContain('navigation')
  })
})
