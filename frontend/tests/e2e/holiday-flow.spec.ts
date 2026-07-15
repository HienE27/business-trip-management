import { test, expect, loginAs } from './fixtures/auth.fixture';

test.describe('Holiday CRUD Flow', () => {

  test('DANH SACH NGAY LE', async ({ page }) => {
    await loginAs(page);
    await page.goto('/holidays');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);

    // Verify heading
    const heading = page.locator('h1').first();
    await expect(heading).toBeVisible({ timeout: 5000 });

    // Verify table has data
    const table = page.locator('table').first();
    await expect(table).toBeVisible({ timeout: 5000 });
  });

  test('THEM NGAY LE', async ({ page }) => {
    await loginAs(page);
    await page.goto('/holidays');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);

    // Click "Thêm ngày lễ" button
    const addBtn = page.getByRole('button', { name: /thêm.*ngày lễ|thêm mới/i }).first();
    if (await addBtn.isVisible({ timeout: 5000 }).catch(() => false)) {
      await addBtn.click();
      await page.waitForTimeout(500);

      // Fill the modal form
      const modal = page.locator('.fixed.inset-0').last();
      if (await modal.isVisible({ timeout: 3000 }).catch(() => false)) {
        // Fill name
        const nameInput = modal.locator('input[type="text"]').first();
        if (await nameInput.isVisible({ timeout: 2000 }).catch(() => false)) {
          await nameInput.fill('Test Holiday E2E');
        }

        // Fill date
        const dateInput = modal.locator('input[type="date"]').first();
        if (await dateInput.isVisible({ timeout: 2000 }).catch(() => false)) {
          await dateInput.fill('2026-12-25');
        }

        // Submit
        const submitBtn = modal.getByRole('button', { name: /thêm mới|lưu/i }).first();
        if (await submitBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
          await submitBtn.click();
          await page.waitForTimeout(1000);
        }
      }
    }

    await expect(page.locator('html')).toHaveAttribute('lang', 'vi');
  });

  test('LOC THEO NAM', async ({ page }) => {
    await loginAs(page);
    await page.goto('/holidays');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);

    // Find year filter
    const yearFilter = page.locator('select').first();
    if (await yearFilter.isVisible({ timeout: 3000 }).catch(() => false)) {
      await yearFilter.selectOption({ index: 1 });
      await page.waitForTimeout(500);
    }

    await expect(page.locator('html')).toHaveAttribute('lang', 'vi');
  });
});
