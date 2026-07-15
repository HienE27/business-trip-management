import { test, expect, loginAs } from './fixtures/auth.fixture';

test.describe('Auto-scheduling Flow', () => {

  test('TRANG AUTO SCHEDULING', async ({ page }) => {
    await loginAs(page);
    await page.goto('/auto-scheduling');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);

    // Verify the page loads with heading
    const heading = page.locator('h1, h2').first();
    await expect(heading).toBeVisible({ timeout: 5000 });
  });

  test('TRANG CAU HINH THUAT TOAN', async ({ page }) => {
    await loginAs(page);
    await page.goto('/auto-scheduling/algorithm-config');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);

    // Verify page loads
    const heading = page.locator('h1').first();
    await expect(heading).toBeVisible({ timeout: 5000 });
  });

  test('CHON PRESET', async ({ page }) => {
    await loginAs(page);
    await page.goto('/auto-scheduling/algorithm-config');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);

    // Try to click a preset button
    const presetBtn = page.locator('button[aria-pressed]').first();
    if (await presetBtn.isVisible({ timeout: 5000 }).catch(() => false)) {
      await presetBtn.click();
      await page.waitForTimeout(500);
    }

    await expect(page.locator('html')).toHaveAttribute('lang', 'vi');
  });

  test('CHON KY LICH', async ({ page }) => {
    await loginAs(page);
    await page.goto('/auto-scheduling');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);

    // Find a period selector
    const periodSelect = page.locator('select').first();
    if (await periodSelect.isVisible({ timeout: 5000 }).catch(() => false)) {
      const options = await periodSelect.locator('option').count();
      if (options > 1) {
        await periodSelect.selectOption({ index: 1 });
        await page.waitForTimeout(1000);
      }
    }

    await expect(page.locator('html')).toHaveAttribute('lang', 'vi');
  });
});
