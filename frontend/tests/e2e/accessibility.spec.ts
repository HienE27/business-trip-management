import { test, expect } from './fixtures/auth.fixture';
import { scanPage, PAGES } from './accessibility';

/**
 * axe-core accessibility regression suite.
 *
 * Runs on every push to develop and every PR touching frontend/ files.
 * Critical violations fail the build; moderate/minor are reported but don't block.
 *
 * To add a new page to the scan, add it to PAGES in `accessibility.ts`.
 */
test.describe('Accessibility (axe-core)', { tag: '@a11y' }, () => {
  for (const { path, label, auth } of PAGES) {
    test(`${label} (${path}) — axe-core scan`, async ({ page, loginAs }) => {
      await scanPage(page, path, label, auth ? () => loginAs(page) : null);
    });
  }

  test('Login page <html lang> attribute is set', async ({ page, loginAs }) => {
    await loginAs(page);
    await page.goto('/login');
    await page.waitForLoadState('domcontentloaded');
    const html = page.locator('html');
    await expect(html).toHaveAttribute('lang', 'vi');
  });

  test('Dashboard has a non-empty <title>', async ({ page, loginAs }) => {
    await loginAs(page);
    await page.goto('/dashboard');
    await page.waitForLoadState('domcontentloaded');
    const title = await page.title();
    expect(title.trim().length).toBeGreaterThan(0);
  });
});
