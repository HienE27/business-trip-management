import { test, expect, loginAs } from './fixtures/auth.fixture';

/**
 * E2E tests for the Periods (Kỳ lịch công tác) page.
 *
 * Covers:
 *   - Route renders without redirecting away
 *   - Sidebar shows and highlights the "Kỳ lịch công tác" item
 *   - Page heading and create button are visible
 *   - List of periods loads from the backend
 */

test.describe('Periods Page (M02 — Kỳ lịch công tác)', () => {
  test('does not redirect away from /periods', async ({ page }) => {
    await loginAs(page);
    await page.goto('/periods');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(1500);

    // URL must stay on /periods (regression: previously highlighted 'monthly-schedule')
    expect(page.url()).toContain('/periods');
    expect(page.url()).not.toContain('/monthly-schedule');
  });

  test('shows page heading and create button', async ({ page }) => {
    await loginAs(page);
    await page.goto('/periods');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(1500);

    const heading = page.getByRole('heading', { name: /kỳ lịch công tác/i }).first();
    await expect(heading).toBeVisible();

    const createBtn = page.getByRole('button', { name: /tạo kỳ lịch/i }).first();
    await expect(createBtn).toBeVisible();
  });

  test('sidebar highlights "Kỳ lịch công tác" item', async ({ page }) => {
    await loginAs(page);
    await page.goto('/periods');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(1500);

    // Active sidebar item is marked with aria-current="page"
    const activeLink = page.locator('aside a[aria-current="page"], nav a[aria-current="page"]').first();
    await expect(activeLink).toBeVisible();

    const activeText = (await activeLink.textContent())?.toLowerCase() ?? '';
    expect(activeText).toContain('kỳ lịch công tác');
  });

  test('periods list renders rows from backend', async ({ page }) => {
    await loginAs(page);
    await page.goto('/periods');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2500);

    // Either table rows or empty-state message must be present
    const rows = page.locator('table tbody tr, [role="row"]');
    const emptyState = page.getByText(/chưa có kỳ lịch/i);

    const rowCount = await rows.count();
    if (rowCount === 0) {
      await expect(emptyState.first()).toBeVisible();
    } else {
      expect(rowCount).toBeGreaterThan(0);
    }
  });

  test('clicking "Tạo kỳ lịch" opens the create modal', async ({ page }) => {
    await loginAs(page);
    await page.goto('/periods');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(1500);

    const createBtn = page.getByRole('button', { name: /tạo kỳ lịch/i }).first();
    await createBtn.click();
    await page.waitForTimeout(500);

    // Modal should appear with name + date inputs
    const modal = page.locator('[role="dialog"], dialog, .modal').first();
    await expect(modal).toBeVisible({ timeout: 3000 });

    const nameInput = page.locator('input[type="text"]').first();
    await expect(nameInput).toBeVisible();
  });

  test('sidebar link to /periods navigates correctly', async ({ page }) => {
    await loginAs(page);
    await page.goto('/dashboard');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(1000);

    // Locate by href for stability against parallel test interference
    const periodsLink = page.locator('aside a[href="/periods"], nav a[href="/periods"]').first();
    await expect(periodsLink).toBeVisible();
    await periodsLink.click();

    await page.waitForURL((url) => url.pathname === '/periods', { timeout: 10_000 });
    expect(new URL(page.url()).pathname).toBe('/periods');
  });
});
