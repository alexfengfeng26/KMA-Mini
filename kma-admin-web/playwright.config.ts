import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './tests/e2e',
  workers: 4,
  use: {
    baseURL: 'http://127.0.0.1:27183',
    trace: 'retain-on-failure',
    ...(process.env.CI ? {} : { channel: 'chrome' }),
  },
  webServer: {
    command: 'npm run dev -- --host 127.0.0.1',
    url: 'http://127.0.0.1:27183',
    reuseExistingServer: true,
  },
})
