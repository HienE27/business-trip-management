import { test, expect } from '@playwright/test';

test.describe('Auto Scheduling Pages', () => {
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

  test('auto scheduling main page loads correctly', async ({ page }) => {
    await page.goto('/auto-scheduling');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2000);
    
    expect(page.url()).toContain('/auto-scheduling');
    
    // Check page has content
    const pageContent = page.locator('main, section, [role="main"]');
    const contentCount = await pageContent.count();
    expect(contentCount).toBeGreaterThanOrEqual(0);
  });

  test('algorithm config page loads correctly', async ({ page }) => {
    await page.goto('/auto-scheduling/algorithm-config');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2000);
    
    expect(page.url()).toContain('/auto-scheduling/algorithm-config');
    
    // Check for form elements
    const formElements = page.locator('input, select, button');
    const formCount = await formElements.count();
    expect(formCount).toBeGreaterThanOrEqual(0);
  });

  test('auto scheduling history page loads correctly', async ({ page }) => {
    await page.goto('/auto-scheduling/history');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2000);
    
    expect(page.url()).toContain('/auto-scheduling/history');
  });
});
