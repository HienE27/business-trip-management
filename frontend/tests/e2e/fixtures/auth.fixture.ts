import { test as base, type Page } from '@playwright/test';

/**
 * Auth fixture for the Hospital Scheduler E2E test suite.
 *
 * Performs a real login against the running backend.
 *
 * Tests that need an authenticated session should call
 * `await loginAs(page)` directly in the test body, not via
 * `beforeEach(({ loginAs }) => …)` — the latter triggers a sync-loader
 * regression in Playwright 1.61 + Node 22.
 */

const TEST_USERNAME = process.env.E2E_USERNAME ?? 'admin';
const TEST_PASSWORD = process.env.E2E_PASSWORD ?? 'admin123';
const LOGIN_PATH = process.env.E2E_LOGIN_PATH ?? '/login';

export async function waitForAuthReady(page: Page, timeout = 20_000): Promise<void> {
  const selector = page.locator('text=Đang kiểm tra xác thực');
  const start = Date.now();
  while (Date.now() - start < timeout) {
    const count = await selector.count();
    if (count === 0) return;
    const visible = await selector.first().isVisible().catch(() => false);
    if (!visible) return;
    await page.waitForTimeout(300);
  }
}

export async function loginAsTestUser(page: Page): Promise<boolean> {
  await page.goto(LOGIN_PATH);
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(1000);

  const usernameInput = page.locator('#username');
  const passwordInput = page.locator('#password');

  const formVisible = await usernameInput.isVisible({ timeout: 10_000 }).catch(() => false);
  if (!formVisible) return true;

  await usernameInput.fill(TEST_USERNAME);
  await passwordInput.fill(TEST_PASSWORD);
  await page.getByRole('button', { name: /đăng nhập/i }).click();

  const start = Date.now();
  const timeout = 15_000;
  while (Date.now() - start < timeout) {
    const url = page.url();
    if (!url.includes('/login')) return true;
    await page.waitForTimeout(200);
  }

  return !page.url().includes('/login');
}

/**
 * `loginAs(page)` helper — preferred way to authenticate a test.
 * We deliberately expose it as a free function (and as a fixture) so that
 * tests don't have to destructure it inside `beforeEach`, avoiding the
 * Playwright 1.61 / Node 22 sync-loader regression.
 */
export async function loginAs(page: Page): Promise<boolean> {
  return loginAsTestUser(page);
}

export const test = base.extend<{ loginAs: (page: Page) => Promise<boolean> }>({
  loginAs: async ({}, registerFixture) => {
    await registerFixture(loginAs);
  },
});

export { expect } from '@playwright/test';
