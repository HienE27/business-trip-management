import { test, expect } from '@playwright/test';

test.describe('Request Pages', () => {
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

  test('swap requests page loads correctly', async ({ page }) => {
    await page.goto('/swap-requests');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2000);
    
    expect(page.url()).toContain('/swap-requests');
  });

  test('leave requests page loads correctly', async ({ page }) => {
    await page.goto('/leave-requests');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2000);
    
    expect(page.url()).toContain('/leave-requests');
  });
});
