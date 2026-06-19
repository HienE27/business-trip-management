import { test, expect } from './fixtures/auth.fixture';

/**
 * E2E tests for Auto-Scheduling page including M07-F06 (Unassigned Report)
 * and M07-F09 (Algorithm Balance Chart).
 */

test.describe('Auto Scheduling Page — M07-F06 / M07-F09', () => {
  test.beforeEach(async ({ loginAs }) => {
    await loginAs();
  });

  test('page loads without crash', async ({ page }) => {
    await page.goto('/auto-scheduling');
    await page.waitForLoadState('domcontentloaded');
    expect(page.url()).toContain('/auto-scheduling');
  });

  test('shows Khối lượng theo nhân sự section', async ({ page }) => {
    await page.goto('/auto-scheduling');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2000);

    await expect(
      page.getByText('Khối lượng theo nhân sự').first(),
    ).toBeVisible();
  });

  test('shows unassigned report card or its success state', async ({ page }) => {
    await page.goto('/auto-scheduling');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2000);

    // Either a warning card or a success card
    const warningCard = page.locator('.border-error-container').first();
    const successCard = page.locator('.border-secondary-container').first();
    const eitherVisible = (await warningCard.count()) > 0 || (await successCard.count()) > 0;
    expect(eitherVisible).toBeTruthy();
  });

  test('algorithm config page loads', async ({ page }) => {
    await page.goto('/auto-scheduling/algorithm-config');
    await page.waitForLoadState('domcontentloaded');
    expect(page.url()).toContain('/algorithm-config');
  });

  test('algorithm history page loads', async ({ page }) => {
    await page.goto('/auto-scheduling/history');
    await page.waitForLoadState('domcontentloaded');
    expect(page.url()).toContain('/history');
  });

  test('settings page links to roles page', async ({ page }) => {
    await page.goto('/settings');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(1000);

    const permCard = page.getByText('Phân quyền hệ thống').first();
    await expect(permCard).toBeVisible();
  });
});
