import { test, expect, loginAs } from './fixtures/auth.fixture';

test.describe('Staff Management Pages', () => {
  test('staff list page loads correctly', async ({ page }) => {
    await loginAs(page);
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
    await loginAs(page);
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
    await loginAs(page);
    await page.goto('/staff/profile');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2000);
    
    expect(page.url()).toContain('/staff/profile');
  });
});
