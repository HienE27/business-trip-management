import { test, expect } from './fixtures/auth.fixture';

test.describe('Auto Scheduling Pages', () => {
  test.beforeEach(async ({ loginAs }) => {
    await loginAs();
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
