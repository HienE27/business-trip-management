import { chromium } from 'playwright';

const browser = await chromium.launch({
  executablePath: 'C:/Users/vhbk0/AppData/Local/ms-playwright/chromium-1228/chrome-win64/chrome.exe',
  headless: true,
});
const ctx = await browser.newContext();
const page = await ctx.newPage();

const apiEvents = [];
page.on('request', (req) => {
  const u = req.url();
  if (u.includes('/api/') || u.includes('/auth/')) apiEvents.push(`>>> ${req.method()} ${u}`);
});
page.on('response', async (res) => {
  const u = res.url();
  if (u.includes('/api/') || u.includes('/auth/')) apiEvents.push(`<<< ${res.status()} ${u}`);
});
page.on('requestfailed', (req) => {
  const u = req.url();
  if (u.includes('/api/') || u.includes('/auth/')) apiEvents.push(`XXX ${u} ${req.failure()?.errorText}`);
});

await page.goto('http://localhost:3000/login', { waitUntil: 'domcontentloaded', timeout: 15000 });
console.log('=== DOM loaded ===');
await page.waitForTimeout(2000);

await page.fill('#username', 'admin');
await page.fill('#password', 'admin123');
console.log('=== Submitting form ===');
const submitStart = Date.now();
await page.click('button[type="submit"]');

// Wait for either URL change (away from /login) or for a real error box that contains text
try {
  await page.waitForFunction(
    () => {
      const url = window.location.pathname;
      if (!url.startsWith('/login')) return true;
      const alertEl = document.querySelector('section form div[role="alert"]');
      return alertEl && alertEl.textContent && alertEl.textContent.trim().length > 0;
    },
    { timeout: 15000 }
  );
  const elapsed = Date.now() - submitStart;
  const url = page.url();
  const errText = await page.locator('section form div[role="alert"]').first().textContent().catch(() => null);
  console.log(`=== Resolved in ${elapsed}ms ===`);
  console.log('Current URL:', url);
  console.log('Form error:', errText);
} catch (e) {
  console.log('WAIT_TIMEOUT after', Date.now() - submitStart, 'ms:', e.message);
  console.log('Current URL:', page.url());
  const errText = await page.locator('section form div[role="alert"]').first().textContent().catch(() => null);
  console.log('Form error:', errText);
}

console.log('\n=== API EVENTS ===');
console.log(apiEvents.join('\n'));

await browser.close();
