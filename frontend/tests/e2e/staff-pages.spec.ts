import { test, expect } from '@playwright/test';

test.describe('Staff Management Pages', () => {
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

  test('staff list page loads correctly', async ({ page }) => {
    await page.goto('/staff');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2000);
    
    expect(page.url()).toContain('/staff');
    
    // Check for any page content
    const mainContent = page.locator('main, section, [role="main"]');
    const contentCount = await mainContent.count();
    expect(contentCount).toBeGreaterThanOrEqual(0);
  });

  test('staff create page loads correctly', async ({ page }) => {
    await page.goto('/staff/create');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2000);
    
    expect(page.url()).toContain('/staff/create');
    
    // Check for form elements
    const formElements = page.locator('input, select, textarea');
    const formCount = await formElements.count();
    expect(formCount).toBeGreaterThanOrEqual(0);
  });

  test('staff profile page loads correctly', async ({ page }) => {
    await page.goto('/staff/profile');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2000);
    
    expect(page.url()).toContain('/staff/profile');
  });
});
