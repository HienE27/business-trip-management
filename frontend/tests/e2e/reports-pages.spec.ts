import { test, expect, loginAs } from './fixtures/auth.fixture';

test.describe('Reports Pages', () => {
  test('reports main page loads correctly', async ({ page }) => {
    await loginAs(page);
    await page.goto('/reports');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2000);
    
    expect(page.url()).toContain('/reports');
  });

  test('staff reports page loads correctly', async ({ page }) => {
    await loginAs(page);
    await page.goto('/reports/staff');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2000);
    
    expect(page.url()).toContain('/reports/staff');
  });

  test('monthly reports page loads correctly', async ({ page }) => {
    await loginAs(page);
    await page.goto('/reports/monthly');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2000);
    
    expect(page.url()).toContain('/reports/monthly');
  });

  test('conflicts reports page loads correctly', async ({ page }) => {
    await loginAs(page);
    await page.goto('/reports/conflicts');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2000);
    
    expect(page.url()).toContain('/reports/conflicts');
  });
});
