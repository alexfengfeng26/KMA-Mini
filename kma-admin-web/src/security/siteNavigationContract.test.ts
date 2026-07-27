import { describe, expect, it } from 'vitest'

const businessSources = import.meta.glob(['../views/**/*.vue', '../layouts/*.vue', '../cms/blocks/*.vue'], {
  eager: true,
  query: '?raw',
  import: 'default',
}) as Record<string, string>

describe('site navigation contract', () => {
  for (const [file, source] of Object.entries(businessSources)) {
    it(`${file} does not navigate directly to a legacy business path`, () => {
      expect(source).not.toMatch(
        /(?:\bto=|router\.(?:push|replace)\()\s*["'`]\/(?:portal|console)(?:\/|["'`])/,
      )
    })

    it(`${file} does not handcraft canonical site prefixes`, () => {
      expect(source).not.toMatch(/["'`]\/t\/\$\{|["'`]\/t\/[a-z]/)
    })
  }
})
