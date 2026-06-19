import { test, expect } from './fixtures/auth.fixture';

test.describe('Request Pages', () => {
  test.beforeEach(async ({ loginAs }) => {
    await loginAs();
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
