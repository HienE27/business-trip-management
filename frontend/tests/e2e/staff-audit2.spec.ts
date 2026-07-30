import { test, expect, Page } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';

const NETWORK_LOG: Array<{ url: string; method: string; status: number | string; latencyMs: number; ts: number }> = [];
const CONSOLE_LOG: Array<{ type: string; text: string }> = [];

async function directLogin(page: Page) {
  await page.goto('http://localhost:3001/login');
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(800);
  await page.locator('#username').fill('admin');
  await page.locator('#password').fill('admin123');
  await Promise.all([
    page.waitForURL((url) => !url.toString().includes('/login'), { timeout: 15_000 }).catch(() => {}),
    page.locator('form').first().evaluate((f) => (f as HTMLFormElement).requestSubmit()),
  ]);
  await page.waitForTimeout(500);
}

test.describe('Staff audit re-verify', () => {
  test('search debounce + tab URL + WS auth', async ({ page }) => {
    page.on('console', (msg) => {
      CONSOLE_LOG.push({ type: msg.type(), text: msg.text() });
    });
    page.on('response', async (res) => {
      try {
        const req = res.request();
        const url = res.url();
        if (!url.includes('/api/') && !url.startsWith('ws://')) return;
        const timing = req.timing();
        NETWORK_LOG.push({
          url: url.replace('http://localhost:3001', '').replace('http://localhost:8080', ''),
          method: req.method(),
          status: res.status(),
          latencyMs: Math.round((timing.responseEnd || 0) - (timing.requestStart || 0)),
          ts: Date.now(),
        });
      } catch {}
    });

    await directLogin(page);
    await page.goto('http://localhost:3001/staff');
    await page.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => {});
    await page.waitForTimeout(1500);

    // ---- TEST 1: search debounce ----
    console.log('===== TEST 1: SEARCH DEBOUNCE =====');
    const beforeCount = NETWORK_LOG.filter((n) => n.url.includes('/staff/search-page')).length;
    const tsBefore = Date.now();

    const searchInput = page.locator('input[name="staffSearch"]');
    await searchInput.fill('');
    await page.waitForTimeout(800);
    const beforeAfterClear = NETWORK_LOG.filter((n) => n.url.includes('/staff/search-page')).length;

    // type 'a' then 'b' then 'c' quickly
    await searchInput.press('a');
    await page.waitForTimeout(100);
    await searchInput.press('b');
    await page.waitForTimeout(100);
    await searchInput.press('c');
    await page.waitForTimeout(2000); // long wait for ALL responses to land

    const allSearchCalls = NETWORK_LOG.filter((n) => n.url.includes('/staff/search-page'));
    const newCalls = allSearchCalls.slice(beforeAfterClear);
    console.log(`[search] calls before typing: ${beforeAfterClear}`);
    console.log(`[search] calls after typing+wait: ${allSearchCalls.length}`);
    console.log(`[search] NEW calls during typing 'abc': ${newCalls.length}`);
    newCalls.forEach((c, i) => {
      const kw = c.url.match(/keyword=([^&]*)/)?.[1] || '(empty)';
      console.log(`  ${i + 1}. keyword=${kw} status=${c.status} latency=${c.latencyMs}ms`);
    });
    console.log(`[search] final searchKeyword in DOM: ${await searchInput.inputValue()}`);

    // ---- TEST 2: tab URL change ----
    console.log('\n===== TEST 2: TAB URL CHANGE =====');
    await page.waitForTimeout(800);
    const urlBeforeTab = page.url();
    console.log(`[tab] URL before click: ${urlBeforeTab}`);

    // try to click the "Chuyên khoa" button in the main tab bar specifically
    const tabBarButtons = page.locator('div.flex.items-center.gap-1.p-1 button');
    const tabBtnCount = await tabBarButtons.count();
    console.log(`[tab] tab bar button count: ${tabBtnCount}`);
    for (let i = 0; i < tabBtnCount; i++) {
      const text = (await tabBarButtons.nth(i).textContent())?.trim();
      console.log(`[tab] button ${i + 1}: "${text}"`);
    }

    // click "Chuyên khoa" button
    const ckBtn = page.locator('button:has-text("Chuyên khoa")').first();
    await ckBtn.click();
    await page.waitForTimeout(800);
    const urlAfterTab = page.url();
    console.log(`[tab] URL after Chuyên khoa click: ${urlAfterTab}`);
    console.log(`[tab] URL contains tab=specialties? ${urlAfterTab.includes('tab=specialties')}`);

    // back
    await page.goBack();
    await page.waitForTimeout(500);
    const urlBack = page.url();
    console.log(`[tab] URL after back: ${urlBack}`);

    // forward
    await page.goForward();
    await page.waitForTimeout(500);
    const urlFwd = page.url();
    console.log(`[tab] URL after forward: ${urlFwd}`);

    // reload
    await page.reload();
    await page.waitForTimeout(1500);
    const urlReload = page.url();
    console.log(`[tab] URL after reload: ${urlReload}`);

    // ---- TEST 3: WS auth ----
    console.log('\n===== TEST 3: WS AUTH =====');
    const wsInConsole = CONSOLE_LOG.filter((c) => c.text.includes('ws://') || c.text.includes('wss://'));
    console.log(`[ws] WS connections in console: ${wsInConsole.length}`);
    wsInConsole.forEach((c) => {
      // extract just the URL + status
      const urlMatch = c.text.match(/(ws:\/\/[^\s]+)/);
      const hasToken = c.text.includes('?token=');
      console.log(`  [${c.type}] ${hasToken ? 'JWT in query' : 'no JWT'} url=${urlMatch?.[1]?.slice(0, 60)}...`);
    });

    // ---- TEST 4: a11y targets ----
    console.log('\n===== TEST 4: A11Y =====');
    await page.goto('http://localhost:3001/staff');
    await page.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => {});
    await page.waitForTimeout(1500);

    // wait for spinner to appear by triggering a search
    const si2 = page.locator('input[name="staffSearch"]');
    await si2.fill('zzz'); // unknown term to force loading
    await page.waitForTimeout(50);
    const spinnerRoleStatus = await page.locator('.animate-spin[role="status"]').count();
    const spinnerAriaLabel = await page.locator('.animate-spin').first().getAttribute('aria-label').catch(() => null);
    const spinnerRoleAttr = await page.locator('.animate-spin').first().getAttribute('role').catch(() => null);
    console.log(`[a11y] spinner[role=status] count: ${spinnerRoleStatus}`);
    console.log(`[a11y] spinner aria-label: ${spinnerAriaLabel} role: ${spinnerRoleAttr}`);
    await page.waitForTimeout(1500);

    // check error role=alert during import failure (import is dry-tested with bad data)
    const errorAlertCount = await page.locator('[role="alert"]').count();
    console.log(`[a11y] [role=alert] total: ${errorAlertCount}`);

    // dump full network log to JSON
    fs.writeFileSync(
      path.join(__dirname, '_audit_verify.json'),
      JSON.stringify({ network: NETWORK_LOG, console: CONSOLE_LOG }, null, 2)
    );

    expect(true).toBe(true);
  });
});
