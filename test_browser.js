// Sprint 1 verification: open browser, login, change config, verify
const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext();
  const page = await ctx.newPage();

  console.log('1. Open login page');
  await page.goto('http://localhost:3000/login', { waitUntil: 'networkidle', timeout: 30000 });
  await page.screenshot({ path: 'E:\\DACN\\business-trip-management\\test_01_login.png' });

  console.log('2. Login as admin');
  await page.fill('input[type="text"], input[name="username"]', 'admin');
  await page.fill('input[type="password"]', 'admin123');
  await page.click('button[type="submit"]');
  await page.waitForLoadState('networkidle', { timeout: 30000 });
  await page.screenshot({ path: 'E:\\DACN\\business-trip-management\\test_02_dashboard.png' });

  console.log('3. Navigate to algorithm config');
  await page.goto('http://localhost:3000/auto-scheduling/algorithm-config', { waitUntil: 'networkidle', timeout: 30000 });
  await page.waitForTimeout(2000);
  await page.screenshot({ path: 'E:\\DACN\\business-trip-management\\test_03_config.png', fullPage: true });

  console.log('4. Find current L01 min');
  const l01MinField = await page.locator('input[type="number"]').first();
  const count = await page.locator('input[type="number"]').count();
  console.log('   Number inputs found:', count);

  console.log('5. Navigate to auto-scheduling page');
  await page.goto('http://localhost:3000/auto-scheduling', { waitUntil: 'networkidle', timeout: 30000 });
  await page.waitForTimeout(3000);
  await page.screenshot({ path: 'E:\\DACN\\business-trip-management\\test_04_scheduling.png', fullPage: true });

  await browser.close();
  console.log('Done. Screenshots saved.');
})().catch(e => {
  console.error('Failed:', e.message);
  process.exit(1);
});
