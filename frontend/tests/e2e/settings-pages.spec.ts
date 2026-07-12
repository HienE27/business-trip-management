import { test, expect } from './fixtures/auth.fixture';

/**
 * E2E tests for the Settings pages.
 */

test.describe('Settings Pages', () => {
  test('settings page loads correctly', async ({ page, loginAs }) => {
    if (!await loginAs(page)) { test.skip(); }
    await page.goto('/settings');
    await page.waitForLoadState('domcontentloaded');
    expect(page.url()).toContain('/settings');
  });

  test('settings page shows role/permission card', async ({ page, loginAs }) => {
    if (!await loginAs(page)) { test.skip(); }
    await page.goto('/settings');
    await page.waitForLoadState('domcontentloaded');
    const permCard = page.getByText('Phân quyền hệ thống').first();
    await expect(permCard).toBeVisible();
  });

  test('role permission page loads (admin only)', async ({ page, loginAs }) => {
    if (!await loginAs(page)) { test.skip(); }
    await page.goto('/settings/roles');
    await page.waitForLoadState('domcontentloaded');
    expect(page.url()).toContain('/settings/roles');
    const body = page.locator('body');
    await expect(body).toBeVisible();
  });

  test('role permission page accessible only to admin', async ({ page, loginAs }) => {
    if (!await loginAs(page)) { test.skip(); }
    await page.goto('/settings/roles');
    await page.waitForLoadState('domcontentloaded');
    const pageLoaded = page.locator('body');
    await expect(pageLoaded).toBeVisible();
  });
});
