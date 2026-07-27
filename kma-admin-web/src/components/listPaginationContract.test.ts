import { describe, expect, it } from 'vitest'

const viewSources = import.meta.glob('../views/*.vue', {
  eager: true,
  query: '?raw',
  import: 'default',
}) as Record<string, string>

describe('list pagination contract', () => {
  for (const [file, source] of Object.entries(viewSources)) {
    const tableCount = source.match(/<el-table(?:\s|>)/g)?.length ?? 0
    if (tableCount === 0) continue

    it(`${file} provides one shared paginator for every table`, () => {
      const directPaginationCount = source.match(/<AppPagination(?:\s|>)/g)?.length ?? 0
      const dataTablePageCount = source.match(/<DataTablePage(?:\s|>)/g)?.length ?? 0

      expect(
        source.includes("import AppPagination from '../components/AppPagination.vue'") ||
          source.includes("import DataTablePage from '../components/DataTablePage.vue'"),
      ).toBe(true)
      expect(directPaginationCount + dataTablePageCount).toBe(tableCount)
    })
  }
})
