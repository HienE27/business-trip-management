import { test, expect } from '@playwright/test';
import { loginAsTestUser, waitForAuthReady } from './fixtures/auth.fixture';

/**
 * B-verification: kiểm tra plan B trên môi trường docker đang chạy.
 *  1. Trang /auto-scheduling/algorithm-config có nút "Lab-Eval" preset.
 *  2. Bấm Lab-Eval → form fill + callout "Lab-Eval là cấu hình đánh giá"
 *     hiển thị (đặc tính Hiến: nút chỉ nạp form, không auto-save).
 *  3. Trang /auto-scheduling có bảng "Đánh giá L04 theo chuyên khoa"
 *     với cross-leak=0 trên period 5 (đã có 1392 L04 schedules).
 */

test.describe.configure({ mode: 'serial' });

test('B#1 — Lab-Eval preset button visible & applies config', async ({ page }) => {
  await loginAsTestUser(page);
  await waitForAuthReady(page);
  await page.goto('/auto-scheduling/algorithm-config', { waitUntil: 'networkidle' });

  const labEvalBtn = page.getByRole('button', { name: /Lab-Eval/i });
  await expect(labEvalBtn).toBeVisible({ timeout: 15000 });

  await labEvalBtn.click();

  // Callout giải thích preset (manual-save required) hiện
  await expect(page.getByText(/Lab-Eval là cấu hình đánh giá/i)).toBeVisible({ timeout: 5000 });

  // Nút reported pressed (active state)
  await expect(labEvalBtn).toHaveAttribute('aria-pressed', 'true');
});

test('B#2 — L04EvalTable renders with cross OFF (period 5)', async ({ page }) => {
  await loginAsTestUser(page);
  await waitForAuthReady(page);
  await page.goto('/auto-scheduling?periodId=5', { waitUntil: 'networkidle' });

  const heading = page.getByText('Đánh giá L04 theo chuyên khoa');
  await expect(heading).toBeVisible({ timeout: 30000 });

  // KPI cell "Cross-leak" phải hiển thị giá trị 0. Dùng last() để skip header subtext
  // ("Yêu cầu / đã gán / cross-leak") — KPI là phần tử match cuối cùng.
  const crossLeakCells = page.getByText('Cross-leak');
  await expect(crossLeakCells.last()).toBeVisible({ timeout: 8000 });
  await expect(crossLeakCells.last().locator('..')).toContainText('0');

  // Badge "cross OFF" hiển thị (bằng chứng leak = 0)
  await expect(page.getByText(/cross OFF/i).first()).toBeVisible({ timeout: 5000 });
});
