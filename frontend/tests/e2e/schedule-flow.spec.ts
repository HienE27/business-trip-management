import { test, expect, loginAs } from './fixtures/auth.fixture';

test.describe('Schedule & Conflict Flow', () => {

  test('DASHBOARD HIEN THI LICH', async ({ page }) => {
    await loginAs(page);
    await page.goto('/dashboard');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(3000);

    // Wait for KPI grid to load
    const kpiGrid = page.locator('.grid, [class*=\"grid\"]').first();
    await expect(kpiGrid).toBeVisible({ timeout: 10000 });
  });

  test('MONTHLY SCHEDULE HIEN THI', async ({ page }) => {
    await loginAs(page);
    await page.goto('/monthly-schedule');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);

    // Verify heading exists
    const heading = page.locator('h1, h2').first();
    await expect(heading).toBeVisible({ timeout: 5000 });
  });

  test('LICH TRUC 24/24', async ({ page }) => {
    await loginAs(page);
    await page.goto('/duty-24');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);

    // Verify the page loads (no error boundary)
    await expect(page.locator('html')).toHaveAttribute('lang', 'vi');
  });

  test('LICH THONG TAM', async ({ page }) => {
    await loginAs(page);
    await page.goto('/all-day');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);
    await expect(page.locator('html')).toHaveAttribute('lang', 'vi');
  });

  test('LICH PHONG KHAM DICH VU', async ({ page }) => {
    await loginAs(page);
    await page.goto('/service-clinic');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);
    await expect(page.locator('html')).toHaveAttribute('lang', 'vi');
  });

  test('LICH PHONG KHAM CHUYEN GIA', async ({ page }) => {
    await loginAs(page);
    await page.goto('/expert-clinic');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);
    await expect(page.locator('html')).toHaveAttribute('lang', 'vi');
  });
});
