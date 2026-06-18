import { test, expect } from '@playwright/test';

test.describe('Reports Pages', () => {
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

  test('reports main page loads correctly', async ({ page }) => {
    await page.goto('/reports');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2000);
    
    expect(page.url()).toContain('/reports');
  });

  test('staff reports page loads correctly', async ({ page }) => {
    await page.goto('/reports/staff');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2000);
    
    expect(page.url()).toContain('/reports/staff');
  });

  test('monthly reports page loads correctly', async ({ page }) => {
    await page.goto('/reports/monthly');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2000);
    
    expect(page.url()).toContain('/reports/monthly');
  });

  test('conflicts reports page loads correctly', async ({ page }) => {
    await page.goto('/reports/conflicts');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2000);
    
    expect(page.url()).toContain('/reports/conflicts');
  });
});
