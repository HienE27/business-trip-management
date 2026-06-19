import { test, expect } from './fixtures/auth.fixture';

/**
 * Smoke tests for every primary route in the dashboard.
 *
 * For each route, we verify:
 *   1. The page does not redirect away (regression: app must keep the user on the requested URL).
 *   2. The expected sidebar section is highlighted via aria-current="page".
 *
 * If a new route is added to APP_SECTIONS, add an entry here so the sidebar mapping stays verified.
 */

type Case = { path: string; expectedSection: string };

const CASES: Case[] = [
  { path: '/dashboard', expectedSection: 'Tổng quan' },
  { path: '/monthly-schedule', expectedSection: 'Lập lịch tháng' },
  { path: '/periods', expectedSection: 'Kỳ lịch công tác' },
  { path: '/duty-24', expectedSection: 'Lịch trực 24/24' },
  { path: '/all-day', expectedSection: 'Lịch thông tầm' },
  { path: '/service-clinic', expectedSection: 'Lịch PK dịch vụ' },
  { path: '/expert-clinic', expectedSection: 'Lịch PK chuyên gia' },
  { path: '/auto-scheduling', expectedSection: 'Tự động xếp lịch' },
  { path: '/staff', expectedSection: 'Nhân sự' },
  { path: '/leave-requests', expectedSection: 'Nghỉ phép' },
  { path: '/swap-requests', expectedSection: 'Đổi trực' },
  { path: '/requirements', expectedSection: 'Yêu cầu nhân sự' },
  { path: '/reports', expectedSection: 'Báo cáo' },
  { path: '/reports/conflicts', expectedSection: 'Báo cáo' },
  { path: '/reports/monthly', expectedSection: 'Báo cáo' },
  { path: '/reports/staff', expectedSection: 'Báo cáo' },
  { path: '/holidays', expectedSection: 'Ngày lễ' },
  { path: '/notifications', expectedSection: 'Thông báo' },
  { path: '/audit-history', expectedSection: 'Nhật ký' },
  { path: '/settings', expectedSection: 'Cài đặt' },
];

test.describe.configure({ mode: 'serial' });
test.describe('All routes — sidebar + redirect regression', () => {
  test.beforeEach(async ({ loginAs }) => {
    await loginAs();
  });

  for (const { path, expectedSection } of CASES) {
    test(`GET ${path} → stays on URL and highlights "${expectedSection}"`, async ({ page }) => {
      await page.goto(path);
      await page.waitForLoadState('domcontentloaded');
      await page.waitForTimeout(1200);

      // 1) URL check
      const url = new URL(page.url());
      expect(url.pathname).toBe(path);

      // 2) Active sidebar item
      const activeLink = page
        .locator('aside a[aria-current="page"], nav a[aria-current="page"]')
        .first();
      await expect(activeLink).toBeVisible({ timeout: 5000 });

      const activeText = (await activeLink.textContent())?.trim() ?? '';
      expect(activeText).toContain(expectedSection);
    });
  }
});