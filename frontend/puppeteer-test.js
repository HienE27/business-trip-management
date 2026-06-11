/**
 * Hospital Scheduler - Puppeteer E2E Test Suite
 * 
 * This script performs comprehensive testing of the Hospital Scheduler application:
 * 1. Logs into the app (admin/admin123)
 * 2. Tests multiple pages for load without errors
 * 3. Captures page metadata and screenshots
 * 4. Generates a JSON report
 */

const puppeteer = require('puppeteer');
const fs = require('fs');
const path = require('path');

// Helper function for delay (replaces deprecated page.waitForTimeout)
function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

// Configuration
const CONFIG = {
  baseUrl: 'http://localhost:3000',
  loginUrl: 'http://localhost:3000/login',
  credentials: {
    username: 'admin',
    password: 'admin123'
  },
  viewport: { width: 1280, height: 720 },
  timeout: 30000,
  waitUntil: 'networkidle2',
  screenshotDir: './puppeteer-screenshots',
  reportPath: './puppeteer-test-report.json'
};

// Pages to test
const PAGES_TO_TEST = [
  { path: '/dashboard', name: 'Dashboard', requiresAuth: true },
  { path: '/monthly-schedule', name: 'Monthly Schedule', requiresAuth: true },
  { path: '/staff', name: 'Staff Management', requiresAuth: true },
  { path: '/leave-requests', name: 'Leave Requests', requiresAuth: true },
  { path: '/swap-requests', name: 'Swap Requests', requiresAuth: true },
  { path: '/notifications', name: 'Notifications', requiresAuth: true },
  { path: '/reports', name: 'Reports', requiresAuth: true },
  { path: '/audit-history', name: 'Audit History', requiresAuth: true },
  { path: '/settings', name: 'Settings', requiresAuth: true },
  { path: '/staff/create', name: 'Create Staff', requiresAuth: true }
];

// Test results storage
const testResults = {
  timestamp: new Date().toISOString(),
  summary: {
    total: 0,
    passed: 0,
    failed: 0,
    skipped: 0
  },
  login: null,
  pages: []
};

/**
 * Ensure screenshot directory exists
 */
function ensureScreenshotDir() {
  if (!fs.existsSync(CONFIG.screenshotDir)) {
    fs.mkdirSync(CONFIG.screenshotDir, { recursive: true });
  }
}

/**
 * Take a screenshot with timestamp
 */
async function takeScreenshot(page, name) {
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
  const filename = `${name.replace(/\//g, '-').replace(/[^a-zA-Z0-9-]/g, '')}-${timestamp}.png`;
  const filepath = path.join(CONFIG.screenshotDir, filename);
  
  try {
    await page.screenshot({ path: filepath, fullPage: true });
    return { success: true, path: filepath, filename };
  } catch (error) {
    return { success: false, error: error.message };
  }
}

/**
 * Extract visible text from page (for content verification)
 */
async function extractPageContent(page) {
  try {
    return await page.evaluate(() => {
      // Get main content area text
      const main = document.querySelector('main') || document.body;
      const text = main.innerText || '';
      return text.substring(0, 500); // First 500 chars
    });
  } catch {
    return '';
  }
}

/**
 * Check for error messages on page
 */
async function checkForErrors(page) {
  const errors = [];
  
  try {
    // Check for error alert elements
    const errorSelectors = [
      '.error',
      '.alert-error',
      '[class*="error"]',
      '[class*="alert"]',
      '.toast-error',
      '[role="alert"]'
    ];
    
    for (const selector of errorSelectors) {
      try {
        const elements = await page.$$(selector);
        for (const el of elements) {
          const text = await el.innerText();
          if (text && text.trim()) {
            errors.push({ selector, text: text.substring(0, 200) });
          }
        }
      } catch {
        // Selector might not exist
      }
    }
    
    // Check page title for error indicators
    const title = await page.title();
    if (title.toLowerCase().includes('error') || title.toLowerCase().includes('404')) {
      errors.push({ type: 'title', text: title });
    }
    
  } catch (error) {
    errors.push({ type: 'check', error: error.message });
  }
  
  return errors;
}

/**
 * Login to the application
 */
async function login(page) {
  console.log('\n🔐 Attempting to login...');
  
  const result = {
    success: false,
    pageTitle: '',
    screenshot: null,
    errors: [],
    cookies: []
  };
  
  try {
    await page.goto(CONFIG.loginUrl, { 
      waitUntil: CONFIG.waitUntil,
      timeout: CONFIG.timeout 
    });
    
    // Wait for page to load
    await page.waitForSelector('body', { timeout: 5000 });
    
    // Take screenshot of login page
    result.screenshot = await takeScreenshot(page, 'login-page');
    
    // Check for any redirect or login form
    const currentUrl = page.url();
    console.log(`   Current URL: ${currentUrl}`);
    
    // Try to find login form elements
    const usernameField = await page.$('input[type="text"], input[name="username"], input[name="email"], input[id*="username" i], input[id*="email" i]');
    const passwordField = await page.$('input[type="password"]');
    const submitButton = await page.$('button[type="submit"], input[type="submit"]');
    
    if (!usernameField || !passwordField || !submitButton) {
      // Form not found - might already be logged in or different structure
      console.log('   Login form elements not found, checking current auth state...');
      
      // Check if we're already authenticated
      if (currentUrl.includes('/dashboard') || currentUrl.includes('/login') === false) {
        result.success = true;
        result.pageTitle = await page.title();
        testResults.login = result;
        return result;
      }
    } else {
      // Fill in credentials
      console.log('   Filling login form...');
      await usernameField.click({ clickCount: 3 });
      await usernameField.type(CONFIG.credentials.username);
      
      await passwordField.click({ clickCount: 3 });
      await passwordField.type(CONFIG.credentials.password);
      
      // Take screenshot before submit
      await takeScreenshot(page, 'login-filled');
      
      // Submit the form
      console.log('   Submitting login form...');
      await submitButton.click();
      
      // Wait for navigation
      await delay(2000);
      
      // Wait for either dashboard or login error
      try {
        await page.waitForFunction(
          (url) => window.location.href.includes('/dashboard') || window.location.href.includes('/login'),
          { timeout: 10000 }
        );
      } catch {
        // Timeout is OK, check current state
      }
      
      // Get final URL
      const finalUrl = page.url();
      console.log(`   Final URL after login: ${finalUrl}`);
      
      // Check if login was successful
      if (finalUrl.includes('/dashboard') || !finalUrl.includes('/login')) {
        result.success = true;
        console.log('   ✅ Login successful!');
      } else {
        result.errors.push({ type: 'login_failed', message: 'Login did not redirect to dashboard' });
        console.log('   ❌ Login failed - still on login page');
      }
    }
    
    // Get page info
    result.pageTitle = await page.title();
    
    // Check for error messages
    result.errors = await checkForErrors(page);
    
    // Get cookies
    result.cookies = await page.cookies();
    
  } catch (error) {
    result.errors.push({ type: 'exception', message: error.message });
    console.log(`   ❌ Login error: ${error.message}`);
  }
  
  testResults.login = result;
  return result;
}

/**
 * Test a single page
 */
async function testPage(page, pageInfo) {
  console.log(`\n📄 Testing: ${pageInfo.name} (${pageInfo.path})`);
  
  const result = {
    name: pageInfo.name,
    path: pageInfo.path,
    url: '',
    pageTitle: '',
    status: 'pending',
    errors: [],
    warnings: [],
    screenshot: null,
    content: '',
    loadTime: 0
  };
  
  const startTime = Date.now();
  
  try {
    const targetUrl = `${CONFIG.baseUrl}${pageInfo.path}`;
    result.url = targetUrl;
    
    // Navigate to page
    const response = await page.goto(targetUrl, { 
      waitUntil: CONFIG.waitUntil,
      timeout: CONFIG.timeout,
      referer: CONFIG.baseUrl
    });
    
    result.loadTime = Date.now() - startTime;
    
    // Get final URL (after any redirects)
    result.url = page.url();
    
    // Get HTTP status
    const httpStatus = response ? response.status() : 'unknown';
    console.log(`   HTTP Status: ${httpStatus}`);
    
    // Handle different status codes
    if (httpStatus === 401 || httpStatus === 403) {
      result.status = 'auth_required';
      result.warnings.push({ type: 'auth', message: `Page requires authentication (${httpStatus})` });
      console.log('   ⚠️  Page requires authentication');
    } else if (httpStatus >= 400) {
      result.status = 'error';
      result.errors.push({ type: 'http_error', status: httpStatus, message: `HTTP ${httpStatus} error` });
      console.log(`   ❌ HTTP Error: ${httpStatus}`);
    } else {
      // Wait a bit for React to hydrate
      await delay(1000);

      // Wait for dashboard-specific content to load (spinner goes away, content appears)
      try {
        await page.waitForFunction(
          () => {
            const body = document.body.innerText || '';
            const hasSpinner = document.querySelector('[class*="animate-spin"], [class*="skeleton"], [class*="loading"]');
            const hasRealContent = body.length > 200;
            return hasRealContent || !hasSpinner;
          },
          { timeout: 8000 }
        );
      } catch {
        // Timeout OK - page might have loaded but content is thin
      }

      await delay(1500); // extra buffer for React re-renders
      
      result.status = 'success';
      result.pageTitle = await page.title();
      console.log(`   Page Title: ${result.pageTitle}`);
      console.log(`   Load Time: ${result.loadTime}ms`);
      
      // Check for console errors
      const consoleErrors = [];
      page.on('console', msg => {
        if (msg.type() === 'error') {
          consoleErrors.push(msg.text());
        }
      });
      
      // Wait for any dynamic content
      await delay(500);;
      
      // Check for page-level errors
      result.errors = await checkForErrors(page);
      
      // Add console errors
      if (consoleErrors.length > 0) {
        result.warnings.push({ type: 'console', messages: consoleErrors });
      }
      
      // Extract content
      result.content = await extractPageContent(page);
      
      // Check if main content loaded
      const bodyText = await page.evaluate(() => document.body.innerText);
      if (bodyText.length < 50) {
        result.warnings.push({ type: 'empty', message: 'Page appears to have minimal content' });
      }
      
      console.log(`   ✅ Page loaded successfully`);
    }
    
    // Take screenshot
    result.screenshot = await takeScreenshot(page, pageInfo.path);
    if (result.screenshot.success) {
      console.log(`   📸 Screenshot: ${result.screenshot.filename}`);
    }
    
  } catch (error) {
    result.loadTime = Date.now() - startTime;
    result.status = 'exception';
    result.errors.push({ type: 'exception', message: error.message });
    console.log(`   ❌ Error: ${error.message}`);
    
    // Try to take screenshot on error
    result.screenshot = await takeScreenshot(page, `${pageInfo.path}-error`);
  }
  
  return result;
}

/**
 * Generate HTML report
 */
function generateHtmlReport(results) {
  const html = `
<!DOCTYPE html>
<html lang="vi">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Hospital Scheduler - Puppeteer Test Report</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif; background: #f7f9fb; color: #191c1e; }
    .container { max-width: 1200px; margin: 0 auto; padding: 24px; }
    h1 { font-size: 32px; margin-bottom: 8px; }
    .timestamp { color: #737686; margin-bottom: 24px; }
    .summary { display: flex; gap: 16px; margin-bottom: 32px; }
    .summary-card { background: white; border: 1px solid #c3c6d7; border-radius: 8px; padding: 20px; flex: 1; }
    .summary-card.success { border-left: 4px solid #006e2d; }
    .summary-card.failed { border-left: 4px solid #ba1a1a; }
    .summary-card.skipped { border-left: 4px solid #737686; }
    .summary-number { font-size: 48px; font-weight: 700; }
    .summary-label { color: #737686; font-size: 14px; text-transform: uppercase; }
    .login-section { background: white; border: 1px solid #c3c6d7; border-radius: 8px; padding: 20px; margin-bottom: 32px; }
    .login-section h2 { font-size: 20px; margin-bottom: 16px; }
    .status-badge { display: inline-block; padding: 4px 12px; border-radius: 9999px; font-size: 12px; font-weight: 600; }
    .status-success { background: #7cf994; color: #007230; }
    .status-failed { background: #ffdad6; color: #93000a; }
    .status-pending { background: #e0e3e5; color: #434655; }
    .status-warning { background: #ffe0b2; color: #973400; }
    .page-list { display: flex; flex-direction: column; gap: 16px; }
    .page-card { background: white; border: 1px solid #c3c6d7; border-radius: 8px; overflow: hidden; }
    .page-header { padding: 16px 20px; display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #e0e3e5; }
    .page-name { font-weight: 600; font-size: 16px; }
    .page-path { color: #737686; font-size: 13px; }
    .page-body { padding: 16px 20px; }
    .page-meta { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 12px; margin-bottom: 12px; }
    .meta-item { display: flex; flex-direction: column; }
    .meta-label { font-size: 11px; text-transform: uppercase; color: #737686; }
    .meta-value { font-size: 14px; }
    .error-list { background: #ffdad6; border-radius: 4px; padding: 12px; margin-top: 12px; }
    .error-list h4 { color: #93000a; font-size: 12px; margin-bottom: 8px; }
    .error-item { color: #ba1a1a; font-size: 13px; margin-bottom: 4px; }
    .warning-list { background: #ffe0b2; border-radius: 4px; padding: 12px; margin-top: 12px; }
    .warning-list h4 { color: #973400; font-size: 12px; margin-bottom: 8px; }
    .screenshot-link { display: inline-block; margin-top: 12px; color: #004ac6; font-size: 13px; }
  </style>
</head>
<body>
  <div class="container">
    <h1>🏥 Hospital Scheduler - E2E Test Report</h1>
    <p class="timestamp">Generated: ${new Date(results.timestamp).toLocaleString('vi-VN')}</p>
    
    <div class="summary">
      <div class="summary-card success">
        <div class="summary-number">${results.summary.passed}</div>
        <div class="summary-label">Passed</div>
      </div>
      <div class="summary-card failed">
        <div class="summary-number">${results.summary.failed}</div>
        <div class="summary-label">Failed</div>
      </div>
      <div class="summary-card skipped">
        <div class="summary-number">${results.summary.skipped}</div>
        <div class="summary-label">Skipped / Auth Required</div>
      </div>
    </div>
    
    <div class="login-section">
      <h2>🔐 Login Test</h2>
      <p>Status: <span class="status-badge ${results.login?.success ? 'status-success' : 'status-failed'}">${results.login?.success ? 'Success' : 'Failed'}</span></p>
      ${results.login?.pageTitle ? `<p>Page Title: ${results.login.pageTitle}</p>` : ''}
    </div>
    
    <h2 style="margin-bottom: 16px;">📄 Page Tests</h2>
    <div class="page-list">
      ${results.pages.map(page => `
        <div class="page-card">
          <div class="page-header">
            <div>
              <div class="page-name">${page.name}</div>
              <div class="page-path">${page.path}</div>
            </div>
            <span class="status-badge ${page.status === 'success' ? 'status-success' : page.status === 'error' || page.status === 'exception' ? 'status-failed' : 'status-warning'}">
              ${page.status}
            </span>
          </div>
          <div class="page-body">
            <div class="page-meta">
              <div class="meta-item">
                <span class="meta-label">URL</span>
                <span class="meta-value">${page.url}</span>
              </div>
              <div class="meta-item">
                <span class="meta-label">Page Title</span>
                <span class="meta-value">${page.pageTitle || 'N/A'}</span>
              </div>
              <div class="meta-item">
                <span class="meta-label">Load Time</span>
                <span class="meta-value">${page.loadTime}ms</span>
              </div>
            </div>
            
            ${page.errors.length > 0 ? `
              <div class="error-list">
                <h4>❌ Errors</h4>
                ${page.errors.map(e => `<div class="error-item">${e.message || JSON.stringify(e)}</div>`).join('')}
              </div>
            ` : ''}
            
            ${page.warnings.length > 0 ? `
              <div class="warning-list">
                <h4>⚠️ Warnings</h4>
                ${page.warnings.map(w => `<div class="error-item">${w.message || JSON.stringify(w)}</div>`).join('')}
              </div>
            ` : ''}
            
            ${page.screenshot?.success ? `<a class="screenshot-link" href="${page.screenshot.path}">📸 View Screenshot</a>` : ''}
          </div>
        </div>
      `).join('')}
    </div>
  </div>
</body>
</html>
  `;
  
  const htmlPath = CONFIG.reportPath.replace('.json', '.html');
  fs.writeFileSync(htmlPath, html, 'utf-8');
  console.log(`\n📊 HTML Report saved to: ${htmlPath}`);
  return htmlPath;
}

/**
 * Main test runner
 */
async function runTests() {
  console.log('╔════════════════════════════════════════════════════════════╗');
  console.log('║   Hospital Scheduler - Puppeteer E2E Test Suite           ║');
  console.log('╚════════════════════════════════════════════════════════════╝');
  console.log(`\n🕐 Started at: ${new Date().toISOString()}`);
  console.log(`🌐 Base URL: ${CONFIG.baseUrl}`);
  console.log(`📱 Viewport: ${CONFIG.viewport.width}x${CONFIG.viewport.height}`);
  
  // Ensure screenshot directory exists
  ensureScreenshotDir();
  
  let browser;
  
  try {
    // Launch browser
    console.log('\n🚀 Launching browser...');
    browser = await puppeteer.launch({
      headless: true,
      args: [
        '--no-sandbox',
        '--disable-setuid-sandbox',
        '--disable-dev-shm-usage',
        '--disable-accelerated-2d-canvas',
        '--disable-gpu'
      ]
    });
    
    const context = browser.defaultBrowserContext();
    context.overridePermissions(CONFIG.baseUrl, ['notifications']);
    
    const page = await browser.newPage();
    await page.setViewport(CONFIG.viewport);
    
    // Enable console logging
    page.on('console', msg => {
      if (msg.type() === 'error') {
        console.log(`   [Console Error]: ${msg.text()}`);
      }
    });
    
    // Track page errors
    let pageErrors = [];
    page.on('pageerror', error => {
      pageErrors.push(error.message);
    });
    
    // Login
    const loginResult = await login(page);
    console.log(`\n${'='.repeat(60)}`);
    
    if (!loginResult.success) {
      console.log('\n⚠️  Login failed. Some tests may fail due to authentication requirements.');
    }
    
    // Test each page
    console.log('\n📋 Starting page tests...');
    testResults.summary.total = PAGES_TO_TEST.length;
    
    for (const pageInfo of PAGES_TO_TEST) {
      // Reset page errors for each page
      pageErrors = [];
      
      const result = await testPage(page, pageInfo);
      
      // Add any page-level errors
      if (pageErrors.length > 0) {
        result.warnings = result.warnings || [];
        result.warnings.push({ type: 'page_error', messages: pageErrors });
      }
      
      testResults.pages.push(result);
      
      // Update summary
      if (result.status === 'success') {
        testResults.summary.passed++;
      } else if (result.status === 'auth_required') {
        testResults.summary.skipped++;
      } else {
        testResults.summary.failed++;
      }
    }
    
    // Save JSON report
    fs.writeFileSync(CONFIG.reportPath, JSON.stringify(testResults, null, 2), 'utf-8');
    console.log(`\n📊 JSON Report saved to: ${CONFIG.reportPath}`);
    
    // Generate HTML report
    generateHtmlReport(testResults);
    
    // Print summary
    console.log('\n' + '='.repeat(60));
    console.log('📋 TEST SUMMARY');
    console.log('='.repeat(60));
    console.log(`Total Pages Tested: ${testResults.summary.total}`);
    console.log(`✅ Passed: ${testResults.summary.passed}`);
    console.log(`❌ Failed: ${testResults.summary.failed}`);
    console.log(`⏭️  Skipped/Auth: ${testResults.summary.skipped}`);
    console.log(`\nLogin Status: ${testResults.login?.success ? '✅ Success' : '❌ Failed'}`);
    
    // Exit with appropriate code
    const exitCode = testResults.summary.failed > 0 ? 1 : 0;
    console.log(`\n${exitCode === 0 ? '✅' : '❌'} Test run completed with exit code: ${exitCode}`);
    
    return exitCode;
    
  } catch (error) {
    console.error('\n❌ Fatal error:', error.message);
    testResults.fatalError = error.message;
    fs.writeFileSync(CONFIG.reportPath, JSON.stringify(testResults, null, 2), 'utf-8');
    return 1;
    
  } finally {
    if (browser) {
      await browser.close();
      console.log('\n🔒 Browser closed.');
    }
  }
}

// Run tests
runTests()
  .then(exitCode => {
    process.exit(exitCode);
  })
  .catch(error => {
    console.error('Unhandled error:', error);
    process.exit(1);
  });
