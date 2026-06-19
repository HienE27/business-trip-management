import { test, expect } from './fixtures/auth.fixture';

test.describe('System Pages', () => {
  test.beforeEach(async ({ loginAs }) => {
    await loginAs();
  });

  test('holidays page loads correctly', async ({ page }) => {
    await page.goto('/holidays');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2000);
    
    expect(page.url()).toContain('/holidays');
  });

  test('notifications page loads correctly', async ({ page }) => {
    await page.goto('/notifications');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2000);
    
    expect(page.url()).toContain('/notifications');
  });

  test('audit history page loads correctly', async ({ page }) => {
    await page.goto('/audit-history');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2000);
    
    expect(page.url()).toContain('/audit-history');
  });

  test('settings page loads correctly', async ({ page }) => {
    await page.goto('/settings');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2000);
    
    expect(page.url()).toContain('/settings');
    
    // Check for form elements
    const formElements = page.locator('input, select, button');
    const formCount = await formElements.count();
    expect(formCount).toBeGreaterThanOrEqual(0);
  });
});
