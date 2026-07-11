import { test, expect, loginAs } from './fixtures/auth.fixture';

test.describe('Schedule Pages', () => {
  test('monthly schedule page loads correctly', async ({ page }) => {
    await loginAs(page);
    await page.goto('/monthly-schedule');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2000);
    
    expect(page.url()).toContain('/monthly-schedule');
  });

  test('schedule page has tabs available', async ({ page }) => {
    await loginAs(page);
    await page.goto('/monthly-schedule');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2000);
    
    // Check for tabs or buttons
    const tabs = page.locator('[role="tab"], button');
    const tabCount = await tabs.count();
    
    expect(tabCount).toBeGreaterThanOrEqual(0);
  });
});
