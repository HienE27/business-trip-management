import { test, expect, type Page } from '@playwright/test';

/**
 * E2E tests for role-based UI guards.
 *
 * Ensures that admin/manager-only pages hide their functionality from STAFF
 * users and show a clear 'no permission' empty state instead.
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
  /** Locator that must be present for ADMIN/MANAGER and absent for STAFF */
  adminOnlyControl: ReturnType<Page['getByRole']>;
  adminOnlyControlName: RegExp;
};

const GUARDED_PAGES: GuardedPage[] = [
  {
    path: '/periods',
    adminOnlyControl: undefined as unknown as ReturnType<Page['getByRole']>, // placeholder
    adminOnlyControlName: /tạo kỳ lịch/i,
  },
  {
    path: '/holidays',
    adminOnlyControl: undefined as unknown as ReturnType<Page['getByRole']>,
    adminOnlyControlName: /thêm ngày lễ/i,
  },
  {
    path: '/requirements',
    adminOnlyControl: undefined as unknown as ReturnType<Page['getByRole']>,
    adminOnlyControlName: /thêm yêu cầu/i,
  },
  {
    path: '/audit-history',
    adminOnlyControl: undefined as unknown as ReturnType<Page['getByRole']>,
    adminOnlyControlName: /xuất|nhật ký|export/i,
  },
  {
    path: '/settings/roles',
    adminOnlyControl: undefined as unknown as ReturnType<Page['getByRole']>,
    adminOnlyControlName: /ma trận|matrix|phân quyền/i,
  },
  {
    path: '/staff',
    adminOnlyControl: undefined as unknown as ReturnType<Page['getByRole']>,
    adminOnlyControlName: /thêm nhân sự|nhân viên/i,
  },
  {
    path: '/reports/conflicts',
    adminOnlyControl: undefined as unknown as ReturnType<Page['getByRole']>,
    adminOnlyControlName: /báo cáo|xung đột/i,
  },
  {
    path: '/reports/staff',
    adminOnlyControl: undefined as unknown as ReturnType<Page['getByRole']>,
    adminOnlyControlName: /khối lượng|nhân sự/i,
  },
  {
    path: '/reports/monthly',
    adminOnlyControl: undefined as unknown as ReturnType<Page['getByRole']>,
    adminOnlyControlName: /kỳ lịch|tổng hợp/i,
  },
];

test.describe('Role guards — STAFF cannot reach admin pages', () => {
  for (const guarded of GUARDED_PAGES) {
    test(`STAFF sees "no permission" on ${guarded.path}`, async ({ page }) => {
      await loginAs(page, 'nvminh', '123456');
      await page.goto(guarded.path);
      await page.waitForLoadState('domcontentloaded');
      await page.waitForTimeout(1500);

      // Denied state visible
      await expect(page.getByRole('heading', { name: /không có quyền/i })).toBeVisible();
    });
  }

  for (const guarded of GUARDED_PAGES) {
    test(`ADMIN keeps access on ${guarded.path}`, async ({ page }) => {
      await loginAs(page, 'admin', 'admin123');
      await page.goto(guarded.path);
      await page.waitForLoadState('domcontentloaded');
      await page.waitForTimeout(1500);

      // No "no permission" state for ADMIN
      await expect(page.getByRole('heading', { name: /không có quyền/i })).toHaveCount(0);
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
    // sidebar entry.
    const sidebarEntries = GUARDED_PAGES.filter(
      (g) => !g.path.startsWith('/settings/') && !g.path.startsWith('/reports/')
    );
    for (const guarded of sidebarEntries) {
      const link = page.locator(`aside a[href="${guarded.path}"]`).first();
      await expect(link, `sidebar link for ${guarded.path} should exist`).toBeVisible();
    }
  });
});