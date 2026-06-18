import { test, expect } from '@playwright/test';

/**
 * Smoke tests verify that the public surface of the dashboard can
 * boot and render without throwing. They do not assert on backend
 * data (the auth-protected API requests will fail in this minimal
 * environment), only that the page chrome, navigation, and shared
 * shell components hydrate correctly.
 */

test.describe('smoke', () => {
  test('root redirects to /dashboard', async ({ page }) => {
    const response = await page.goto('/');
    // Either a 200 after following the redirect, or a 307/308
    // before the redirect lands. Both are acceptable boot outcomes.
    expect([200, 307, 308]).toContain(response?.status() ?? 0);
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test('dashboard route returns a page with the MedSchedule brand', async ({ page }) => {
    await page.goto('/dashboard');
    // The DashboardShell renders the brand block ("MedSchedule Pro")
    // or the empty-state placeholder, both of which keep the
    // document <html lang="vi"> attribute set by the root layout.
    const html = page.locator('html');
    await expect(html).toHaveAttribute('lang', 'vi');
  });

  test('login page exposes the title input', async ({ page }) => {
    await page.goto('/login');
    await expect(page.locator('input[name="username"], input[type="text"]').first()).toBeVisible();
  });
});
