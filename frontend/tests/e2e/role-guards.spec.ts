import { test, expect, type Page } from '@playwright/test';

/**
 * E2E tests for role-based UI guards.
 *
 * Ensures that pages with administrative actions hide their functionality
 * from STAFF users and show a clear 'no permission' empty state instead.
 */

async function loginAs(page: Page, username: string, password: string) {
  await page.goto('/login');
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(800);
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.getByRole('button', { name: /đăng nhập/i }).click();
  await page.waitForURL((u) => !u.pathname.startsWith('/login'), { timeout: 15_000 });
}

test.describe('Role guards — STAFF cannot manage periods', () => {
  test('STAFF sees "no permission" state on /periods and no create button', async ({ page }) => {
    await loginAs(page, 'nvminh', '123456');

    await page.goto('/periods');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(1500);

    // Empty state with lock icon
    await expect(page.getByRole('heading', { name: /không có quyền/i })).toBeVisible();

    // No create button should be rendered for STAFF
    await expect(page.getByRole('button', { name: /tạo kỳ lịch/i })).toHaveCount(0);
  });

  test('STAFF can still see sidebar entry (read-only intent)', async ({ page }) => {
    await loginAs(page, 'nvminh', '123456');

    await page.goto('/periods');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(1500);

    // Sidebar still has the link, but page itself denies access
    const link = page.locator('aside a[href="/periods"]').first();
    await expect(link).toBeVisible();
  });

  test('ADMIN can still create periods after guard was added', async ({ page }) => {
    await loginAs(page, 'admin', 'admin123');

    await page.goto('/periods');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(1500);

    // No "no permission" state for ADMIN
    await expect(page.getByRole('heading', { name: /không có quyền/i })).toHaveCount(0);

    // ADMIN sees the create button
    await expect(page.getByRole('button', { name: /tạo kỳ lịch/i }).first()).toBeVisible();
  });
});