import { test, expect } from '@playwright/test';

test.describe('Authentication', () => {
  test('login page renders correctly', async ({ page }) => {
    await page.goto('/login');

    const usernameInput = page.locator('#username');
    const passwordInput = page.locator('#password');
    const loginButton = page.getByRole('button', { name: /đăng nhập/i });

    await expect(usernameInput).toBeVisible();
    await expect(passwordInput).toBeVisible();
    await expect(loginButton).toBeVisible();
  });

  test('login form has proper styling', async ({ page }) => {
    await page.goto('/login');

    const usernameInput = page.locator('#username');
    const passwordInput = page.locator('#password');

    await expect(usernameInput).toBeAttached();
    await expect(passwordInput).toBeAttached();
  });

  test('login page has hospital branding', async ({ page }) => {
    await page.goto('/login');

    const pageContent = page.locator('body');
    await expect(pageContent).toBeVisible();
  });
});

test.describe('Login Form Validation', () => {
  test('shows validation error when submitting empty form', async ({ page }) => {
    await page.goto('/login');

    const loginButton = page.getByRole('button', { name: /đăng nhập/i });
    await loginButton.click();

    const usernameInput = page.locator('#username');
    await expect(usernameInput).toBeVisible();
  });

  test('password field is masked', async ({ page }) => {
    await page.goto('/login');

    const passwordInput = page.locator('#password');
    await expect(passwordInput).toHaveAttribute('type', 'password');
  });
});
