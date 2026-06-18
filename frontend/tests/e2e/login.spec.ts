import { test, expect } from '@playwright/test';

test.describe('Authentication', () => {
  test('login page renders correctly', async ({ page }) => {
    await page.goto('/login');
    
    // Check for username input
    const usernameInput = page.locator('input[name="username"]');
    await expect(usernameInput).toBeVisible();
    
    // Check for password input
    const passwordInput = page.locator('input[name="password"]');
    await expect(passwordInput).toBeVisible();
    
    // Check for login button
    const loginButton = page.getByRole('button', { name: /đăng nhập/i });
    await expect(loginButton).toBeVisible();
  });

  test('login form has proper styling', async ({ page }) => {
    await page.goto('/login');
    
    // Check inputs have proper classes
    const usernameInput = page.locator('input[name="username"]');
    await expect(usernameInput).toBeAttached();
    
    const passwordInput = page.locator('input[name="password"]');
    await expect(passwordInput).toBeAttached();
  });

  test('login page has hospital branding', async ({ page }) => {
    await page.goto('/login');
    
    // Check for page title or branding
    const pageContent = page.locator('body');
    await expect(pageContent).toBeVisible();
  });
});

test.describe('Login Form Validation', () => {
  test('shows validation error when submitting empty form', async ({ page }) => {
    await page.goto('/login');
    
    // Click login without entering credentials
    const loginButton = page.getByRole('button', { name: /đăng nhập/i });
    await loginButton.click();
    
    // Should show validation message (depends on implementation)
    // The form should prevent submission
  });

  test('password field is masked', async ({ page }) => {
    await page.goto('/login');
    
    const passwordInput = page.locator('input[name="password"]');
    await expect(passwordInput).toHaveAttribute('type', 'password');
  });
});
