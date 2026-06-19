import { test as base, type Page } from '@playwright/test';

/**
 * Test credentials for the dev/staging backend.
 *
 * These are intentionally placeholders. The CI runner overrides
 * them via the standard Playwright env mechanism — set
 * E2E_USERNAME / E2E_PASSWORD (and optionally E2E_LOGIN_PATH)
 * as repository secrets, then map them to environment variables
 * in the workflow.
 *
 * Never inline real credentials inside individual spec files:
 * always go through {@link loginAsTestUser} so a single secret
 * rotation covers every spec.
 */
const TEST_USERNAME = process.env.E2E_USERNAME ?? 'admin';
const TEST_PASSWORD = process.env.E2E_PASSWORD ?? 'change-me';
const LOGIN_PATH = process.env.E2E_LOGIN_PATH ?? '/login';

/**
 * Click through the login form using the placeholder credentials
 * above. No-op (returns) when the login form is not visible,
 * which is the case for routes that do not require auth.
 */
export async function loginAsTestUser(page: Page): Promise<void> {
  await page.goto(LOGIN_PATH);
  await page.waitForLoadState('networkidle');

  const usernameInput = page.locator('input[name="username"]');
  const passwordInput = page.locator('input[name="password"]');

  const visible = await usernameInput.isVisible({ timeout: 10_000 }).catch(() => false);
  if (!visible) return;

  await usernameInput.fill(TEST_USERNAME);
  await passwordInput.fill(TEST_PASSWORD);
  await page.getByRole('button', { name: /đăng nhập/i }).click();
  await page.waitForTimeout(2_000);
}

/**
 * Custom test fixture that exposes `loginAs` directly on the
 * test context. Use it in specs that need an authenticated
 * browser session:
 *
 *   test('does something', async ({ page, loginAs }) => {
 *     await loginAs();
 *     await page.goto('/dashboard');
 *   });
 *
 * Internally we close over the `page` provided by Playwright,
 * so callers never need to thread it through manually.
 */
export const test = base.extend<{ loginAs: () => Promise<void> }>({
  loginAs: async ({ page }, use) => {
    await use(() => loginAsTestUser(page));
  },
});

export { expect } from '@playwright/test';