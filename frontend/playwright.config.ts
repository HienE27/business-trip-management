import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: 'html',
  timeout: process.env.CI ? 60_000 : 60_000,
  use: {
    baseURL: 'http://localhost:3000',
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  // Reuse the existing frontend on :3000 (typically started by dev manually).
  // On CI (no existing server) we boot it ourselves.
  webServer: process.env.CI ? {
    command: 'pnpm start',
    url: 'http://localhost:3000',
    reuseExistingServer: false,
    timeout: 120_000,
  } : undefined,
});
