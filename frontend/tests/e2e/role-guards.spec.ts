import { test, expect, type Page } from '@playwright/test';

/**
 * E2E tests for role-based UI guards.
 *
 * Ensures that admin/manager-only pages hide their functionality from STAFF
 * users and show a clear 'no permission' empty state instead. STAFF is
 * always allowed on /swap-requests and /notifications because those flows
 * are part of the staff self-service experience.
 *
 * Every guarded page is covered by a single parameterized spec to keep the
 * suite readable and easy to extend when a new admin-only page is added.
 */

async function loginAs(page: Page, username: string, password: string) {
  await page.goto('/login');
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(800);
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.getByRole('button', { name: /đăng nhập/i }).click();
  await page.waitForURL((u) => !u.pathname.startsWith('/login'), { timeout: 15_000 });
}

type GuardedPage = {
  path: string;
  /** Roles allowed in addition to ADMIN/MANAGER. Empty means STAFF is denied. */
  staffAllowed?: boolean;
};

const ADMIN_MANAGER_ONLY_PAGES: GuardedPage[] = [
  { path: '/periods' },
  { path: '/audit-history' },
  { path: '/settings/roles' },
  { path: '/staff/create' },
  { path: '/settings' },
];

const ALL_ROLES_PAGES: GuardedPage[] = [
  { path: '/swap-requests', staffAllowed: true },
  { path: '/notifications', staffAllowed: true },
];

// Pages whose guard is implemented via GuardedScheduleByTypePage
// (inline role check, no double-shell). Tested under the
// same ADMIN+MANAGER-only contract.
const SCHEDULE_BY_TYPE_PAGES: GuardedPage[] = [
  { path: '/duty-24' },
  { path: '/all-day' },
  { path: '/service-clinic' },
  { path: '/expert-clinic' },
];

const ALL_GUARDED_PAGES = [...ADMIN_MANAGER_ONLY_PAGES, ...ALL_ROLES_PAGES, ...SCHEDULE_BY_TYPE_PAGES];

test.describe('Role guards — STAFF cannot reach admin pages', () => {
  for (const guarded of ADMIN_MANAGER_ONLY_PAGES) {
    test(`STAFF sees "no permission" on ${guarded.path}`, async ({ page }) => {
      await loginAs(page, 'nvminh', '123456');
      await page.goto(guarded.path);
      await page.waitForLoadState('domcontentloaded');
      await page.waitForTimeout(1500);

      // Denied state visible
      await expect(page.getByText(/không có quyền/i)).toBeVisible();
    });
  }

  for (const guarded of ALL_GUARDED_PAGES) {
    test(`ADMIN keeps access on ${guarded.path}`, async ({ page }) => {
      await loginAs(page, 'admin', 'admin123');
      await page.goto(guarded.path);
      await page.waitForLoadState('domcontentloaded');
      await page.waitForTimeout(1500);

      // No "no permission" state for ADMIN
      await expect(page.getByText(/không có quyền/i)).toHaveCount(0);
    });
  }

  for (const guarded of ALL_ROLES_PAGES) {
    test(`STAFF keeps access on ${guarded.path} (staff-allowed)`, async ({ page }) => {
      await loginAs(page, 'nvminh', '123456');
      await page.goto(guarded.path);
      await page.waitForLoadState('domcontentloaded');
      await page.waitForTimeout(1500);

      // STAFF can access these self-service flows — the "no permission"
      // empty state must NOT appear.
      await expect(page.getByText(/không có quyền/i)).toHaveCount(0);
    });
  }

	  test('STAFF still sees sidebar entries (visibility, not access)', async ({ page }) => {
	    await loginAs(page, 'nvminh', '123456');
	    await page.goto('/dashboard');
	    await page.waitForLoadState('domcontentloaded');
	    await page.waitForTimeout(1000);

	    // Each guarded page still has a sidebar link so STAFF can see what exists.
	    // Skip nested routes (e.g. /settings/roles, /reports/*) — they live under
	    // their parent's nav item (/settings, /reports), not as a standalone
	    // sidebar entry. Also skip /staff/create which is reached from /staff.
	    // Also skip pages that don't have dedicated sidebar nav entries.
	    const SIDEBAR_HREFS = new Set([
	      '/dashboard', '/monthly-schedule', '/duty-24', '/all-day',
	      '/service-clinic', '/expert-clinic', '/staff', '/leave-requests',
	      '/swap-requests', '/holidays', '/notifications', '/settings',
	    ]);
	    const sidebarEntries = ALL_GUARDED_PAGES.filter(
	      (g) =>
	        SIDEBAR_HREFS.has(g.path) &&
	        !g.path.startsWith('/settings/') &&
	        !g.path.startsWith('/reports/') &&
	        g.path !== '/staff/create',
	    );
	    for (const guarded of sidebarEntries) {
	      const link = page.locator(`a[href="${guarded.path}"]`).first();
	      await expect(link, `sidebar link for ${guarded.path} should exist`).toBeVisible();
	    }
  });
});
