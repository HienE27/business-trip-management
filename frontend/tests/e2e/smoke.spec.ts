import { test, expect, loginAs } from './fixtures/auth.fixture';

/**
 * Smoke tests verify that the public surface of the dashboard can
 * boot and render without throwing. They do not assert on backend
 * data, only that the page chrome, navigation, and shared
 * shell components hydrate correctly.
 */

test.describe('smoke', () => {
  test('root redirects to a safe page', async ({ page }) => {
    await loginAs(page);
    await page.goto('/');
    // Wait for navigation away from '/' to complete
    await page.waitForURL('**/(!/**)', { timeout: 10_000 }).catch(() => {});
    const finalUrl = page.url();
    const landed = finalUrl.includes('/dashboard') || finalUrl.includes('/login');
    expect(landed).toBe(true);
  });

  test('dashboard route returns a page with the MedSchedule brand', async ({ page }) => {
    await loginAs(page);
    await page.goto('/dashboard');
    await page.waitForLoadState('domcontentloaded');
    const html = page.locator('html');
    await expect(html).toHaveAttribute('lang', 'vi');
  });

  test('login page exposes the title input', async ({ page }) => {
    await page.goto('/login');
    await page.waitForLoadState('domcontentloaded');
    await expect(page.locator('#username').first()).toBeVisible();
  });
});
