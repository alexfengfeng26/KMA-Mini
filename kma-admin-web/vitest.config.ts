import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['src/**/*.test.ts'],
    exclude: ['tests/e2e/**'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json-summary', 'html'],
      reportsDirectory: 'coverage',
      include: [
        'src/app/runtimeConfig.ts',
        'src/api/{client,download,page,party,sse}.ts',
        'src/components/{AppPagination,PageState,listPagination}.vue',
        'src/components/listPagination.ts',
        'src/composables/*.ts',
        'src/domain/*.ts',
        'src/modules/{contract,registry}.ts',
        'src/cms/blockRegistry.ts',
        'src/router/*.ts',
        'src/security/*.ts',
        'src/stores/*.ts',
      ],
      exclude: ['src/**/*.test.ts'],
      thresholds: {
        lines: 70,
        statements: 70,
        functions: 60,
        branches: 60,
        'src/api/**.ts': {
          lines: 85,
          statements: 85,
        },
        'src/security/**.ts': {
          lines: 85,
          statements: 85,
        },
        'src/router/**.ts': {
          lines: 85,
          statements: 85,
        },
        'src/stores/**.ts': {
          lines: 85,
          statements: 85,
        },
      },
    },
  },
})
