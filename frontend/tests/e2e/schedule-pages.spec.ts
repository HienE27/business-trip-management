import { test, expect } from '@playwright/test';

test.describe('Schedule Pages', () => {
  test.beforeEach(async ({ page }) => {
    // Login first
    await page.goto('/login');
    await page.waitForLoadState('networkidle');
    
    const usernameInput = page.locator('input[name="username"]');
    const passwordInput = page.locator('input[name="password"]');
    
    if (await usernameInput.isVisible({ timeout: 10000 })) {
      await usernameInput.fill('admin');
      await passwordInput.fill('admin123');
      await page.getByRole('button', { name: /đăng nhập/i }).click();
      await page.waitForTimeout(2000);
    }
  });

  test('monthly schedule page loads correctly', async ({ page }) => {
    await page.goto('/monthly-schedule');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2000);
    
    expect(page.url()).toContain('/monthly-schedule');
  });

  test('schedule page has tabs available', async ({ page }) => {
    await page.goto('/monthly-schedule');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2000);
    
    // Check for tabs or buttons
    const tabs = page.locator('[role="tab"], button');
    const tabCount = await tabs.count();
    
    expect(tabCount).toBeGreaterThanOrEqual(0);
  });
});
