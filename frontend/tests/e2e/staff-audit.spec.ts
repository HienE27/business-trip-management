import { test, expect, Page } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';

const NETWORK_LOG: Array<{
  url: string;
  method: string;
  status: number | string;
  latencyMs: number;
  startTime: number;
}> = [];
const CONSOLE_LOG: Array<{ type: string; text: string }> = [];
const PAGE_ERRORS: string[] = [];

async function directLogin(page: Page) {
  await page.goto('http://localhost:3001/login');
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(1000);

  const usernameInput = page.locator('#username');
  const passwordInput = page.locator('#password');

  await usernameInput.waitFor({ state: 'visible', timeout: 10_000 });
  await usernameInput.fill('admin');
  await passwordInput.fill('admin123');

  // Use form submit to avoid strict-mode on multiple buttons
  await Promise.all([
    page.waitForURL((url) => !url.toString().includes('/login'), { timeout: 15_000 }).catch(() => {}),
    page.locator('form').first().evaluate((f) => (f as HTMLFormElement).requestSubmit()),
  ]);
  await page.waitForTimeout(800);
  const ok = !page.url().includes('/login');
  return ok;
}

test.describe('Staff page real browser audit', () => {
  test('full UI audit @ /staff', async ({ page }) => {
    // ----- collect network + console -----
    page.on('console', (msg) => {
      CONSOLE_LOG.push({ type: msg.type(), text: msg.text() });
    });
    page.on('pageerror', (err) => {
      PAGE_ERRORS.push(String(err));
    });
    page.on('response', async (res) => {
      try {
        const req = res.request();
        const url = res.url();
        if (!url.includes('/api/')) return;
        const timing = req.timing();
        NETWORK_LOG.push({
          url: url.replace('http://localhost:3001', '').replace('http://localhost:8080', ''),
          method: req.method(),
          status: res.status(),
          latencyMs: Math.round((timing.responseEnd || 0) - (timing.requestStart || 0)),
          startTime: Date.now(),
        });
      } catch {}
    });
    page.on('requestfailed', (req) => {
      if (!req.url().includes('/api/')) return;
      NETWORK_LOG.push({
        url: req.url().replace('http://localhost:3001', '').replace('http://localhost:8080', ''),
        method: req.method(),
        status: 'FAIL',
        latencyMs: 0,
        startTime: Date.now(),
      });
    });

    // ----- login -----
    const loginOk = await directLogin(page);
    console.log(`[AUDIT] login ok=${loginOk} url=${page.url()}`);

    if (!loginOk) {
      console.log('[AUDIT] LOGIN FAILED — aborting');
      return;
    }

    // ----- go to /staff -----
    await page.goto('http://localhost:3001/staff');
    await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});
    await page.waitForTimeout(1500);
    console.log(`[AUDIT] URL after /staff: ${page.url()}`);

    // ----- table check -----
    const tableExists = (await page.locator('table').count()) > 0;
    const headerCells = await page.locator('table thead th').count();
    const bodyRows = await page.locator('table tbody tr').count();
    const headerLabels: string[] = await page
      .locator('table thead th')
      .evaluateAll((els) => els.map((el) => (el.textContent || '').trim()));
    console.log(`[AUDIT] table=${tableExists} headers=${headerCells} bodyRows=${bodyRows}`);
    console.log(`[AUDIT] header labels: ${JSON.stringify(headerLabels)}`);

    // ----- search debounce test -----
    const beforeSearchCount = NETWORK_LOG.filter((n) => n.url.includes('/staff/search-page')).length;
    const searchInput = page.locator('input[name="staffSearch"]');
    await searchInput.fill('');
    await page.waitForTimeout(400);
    await searchInput.type('a', { delay: 30 });
    await page.waitForTimeout(120);
    await searchInput.type('b', { delay: 30 });
    await page.waitForTimeout(120);
    await searchInput.type('c', { delay: 30 });
    await page.waitForTimeout(1200);
    const searchCalls = NETWORK_LOG.filter((n) => n.url.includes('/staff/search-page')).slice(beforeSearchCount);
    const lastSearch = searchCalls[searchCalls.length - 1];
    console.log(`[AUDIT] search calls during typing 'abc': ${searchCalls.length}`);
    console.log(`[AUDIT] last search: ${lastSearch?.url} status=${lastSearch?.status} latency=${lastSearch?.latencyMs}ms`);
    const searchKeywords = searchCalls.map((c) => {
      const m = c.url.match(/keyword=([^&]*)/);
      return m ? decodeURIComponent(m[1]) : '(none)';
    });
    console.log(`[AUDIT] search keywords in order: [${searchKeywords.join(', ')}]`);

    // clear search
    await searchInput.fill('');
    await page.waitForTimeout(800);

    // ----- rapid filter change -----
    const beforeFilterCount = NETWORK_LOG.filter((n) => n.url.includes('/staff/search-page')).length;
    const roleSelect = page.locator('select[aria-label="Lọc theo vai trò"]');
    await roleSelect.selectOption('ADMIN');
    await page.waitForTimeout(80);
    await roleSelect.selectOption('MANAGER');
    await page.waitForTimeout(80);
    await roleSelect.selectOption('STAFF');
    await page.waitForTimeout(1000);
    const filterCalls = NETWORK_LOG.filter((n) => n.url.includes('/staff/search-page')).slice(beforeFilterCount);
    console.log(`[AUDIT] rapid filter calls: ${filterCalls.length}`);
    filterCalls.forEach((c, i) => {
      console.log(`  ${i + 1}. ${c.url} status=${c.status} latency=${c.latencyMs}ms`);
    });
    const lastFilter = filterCalls[filterCalls.length - 1];
    const lastFilterRole = lastFilter?.url.match(/role=([^&]*)/)?.[1];
    console.log(`[AUDIT] last filter role=${lastFilterRole}`);
    if (filterCalls.length > 1) {
      const statuses = filterCalls.map((c) => c.status);
      const hasFail = statuses.some((s) => s !== 200);
      console.log(`[AUDIT] non-200 in filter calls: ${hasFail}`);
    }

    // reset filter
    await roleSelect.selectOption('');
    await page.waitForTimeout(500);

    // ----- tab click → URL change -----
    const specialtyTab = page.getByRole('button', { name: /Chuyên khoa/ }).first();
    await specialtyTab.click();
    await page.waitForTimeout(500);
    const urlAfterSpecialty = page.url();
    console.log(`[AUDIT] URL after Chuyên khoa tab: ${urlAfterSpecialty}`);
    const tabChangeOk = urlAfterSpecialty.includes('tab=specialties');
    console.log(`[AUDIT] tab URL change OK: ${tabChangeOk}`);

    // back/forward
    await page.goBack();
    await page.waitForTimeout(500);
    console.log(`[AUDIT] URL after back: ${page.url()}`);
    const backOk = !page.url().includes('tab=specialties');
    await page.goForward();
    await page.waitForTimeout(500);
    console.log(`[AUDIT] URL after forward: ${page.url()}`);
    const forwardOk = page.url().includes('tab=specialties');

    // reload (preserves tab)
    await page.reload();
    await page.waitForTimeout(1500);
    const urlAfterReload = page.url();
    console.log(`[AUDIT] URL after reload: ${urlAfterReload}`);
    const reloadOk = urlAfterReload.includes('tab=specialties');

    // back to staff tab
    await page.locator('button:has-text("Nhân viên")').first().click();
    await page.waitForTimeout(1000);

    // ----- "Thêm nhân viên" link -----
    const addLink = page.getByRole('link', { name: /Thêm nhân viên/ });
    const addHref = await addLink.getAttribute('href');
    const addLinkOk = addHref === '/staff/create';
    console.log(`[AUDIT] Thêm nhân viên href=${addHref} expected=/staff/create match=${addLinkOk}`);

    // ----- export button -----
    const beforeExportCount = NETWORK_LOG.length;
    let exportFilename: string | null = null;
    let exportSize = 0;
    let exportPreview = '';
    try {
      const [download] = await Promise.all([
        page.waitForEvent('download', { timeout: 5000 }),
        page.getByRole('button', { name: /Xuất Excel/ }).click(),
      ]);
      exportFilename = download.suggestedFilename();
      const p = await download.path();
      if (p) {
        exportSize = fs.statSync(p).size;
        exportPreview = fs.readFileSync(p, { encoding: 'utf8' }).slice(0, 400);
      }
    } catch (e) {
      console.log('[AUDIT] no download event:', e);
    }
    const exportCalls = NETWORK_LOG.slice(beforeExportCount).filter((n) => n.url.includes('/staff/export'));
    console.log(`[AUDIT] export: download=${exportFilename} size=${exportSize}B`);
    console.log(`[AUDIT] export API calls: ${exportCalls.length}`);
    console.log(`[AUDIT] export CSV head: ${exportPreview.replace(/\n/g, '\\n').slice(0, 200)}`);
    const exportEndpointHit = exportCalls.length > 0;

    // ----- import button -----
    const tmpCsv = path.join(__dirname, '_audit_staff_import.csv');
    fs.writeFileSync(
      tmpCsv,
      'username,fullName,email,phone,position,roles\n' +
        'audit_user_' + Date.now() + ',Audit User,audit@test.local,0901234567,Bác sĩ,STAFF\n',
      'utf8'
    );
    const beforeImportCount = NETWORK_LOG.length;
    let importFileChooserOk = false;
    const importResponseBody = '';
    try {
      const fileChooserPromise = page.waitForEvent('filechooser', { timeout: 5000 });
      await page.getByRole('button', { name: /Nhập Excel/ }).click();
      const fileChooser = await fileChooserPromise;
      await fileChooser.setFiles(tmpCsv);
      importFileChooserOk = true;
      await page.waitForTimeout(3000);
      // capture last toast / response
    } catch (e) {
      console.log('[AUDIT] file chooser error:', e);
    }
    const importCalls = NETWORK_LOG.slice(beforeImportCount).filter((n) => n.url.includes('/staff/import'));
    console.log(`[AUDIT] import: fileChooser=${importFileChooserOk} calls=${importCalls.length}`);
    importCalls.forEach((c, i) => {
      console.log(`  ${i + 1}. ${c.method} ${c.url} status=${c.status} latency=${c.latencyMs}ms`);
    });
    fs.unlinkSync(tmpCsv);

    // ----- a11y manual checks -----
    const selectAriaLabels: { name: string | null; value: string }[] = await page
      .locator('select[aria-label]')
      .evaluateAll((els) => els.map((el) => ({ name: el.getAttribute('aria-label'), value: (el as HTMLSelectElement).value })));
    const inputAriaLabels: { name: string | null }[] = await page
      .locator('input[aria-label]')
      .evaluateAll((els) => els.map((el) => ({ name: el.getAttribute('aria-label') })));
    console.log(`[AUDIT] select aria-labels: ${JSON.stringify(selectAriaLabels)}`);
    console.log(`[AUDIT] input aria-labels: ${JSON.stringify(inputAriaLabels)}`);
    const errorRoleCount = await page.locator('[role="alert"]').count();
    const statusRoleCount = await page.locator('[role="status"]').count();
    const ariaHiddenIconCount = await page.locator('[aria-hidden="true"]').count();
    console.log(`[AUDIT] role=alert=${errorRoleCount} role=status=${statusRoleCount} aria-hidden icons=${ariaHiddenIconCount}`);

    // ----- final dumps -----
    console.log('\n========== NETWORK LOG ==========');
    NETWORK_LOG.forEach((n, i) => {
      console.log(`${String(i + 1).padStart(3, ' ')}. ${n.method} ${n.url} → ${n.status} (${n.latencyMs}ms)`);
    });
    console.log('\n========== CONSOLE LOG ==========');
    CONSOLE_LOG.forEach((c) => console.log(`[${c.type}] ${c.text}`));
    console.log('\n========== PAGE ERRORS ==========');
    PAGE_ERRORS.forEach((e) => console.log(e));
    if (PAGE_ERRORS.length === 0) console.log('(none)');

    // write report to disk
    fs.writeFileSync(
      path.join(__dirname, '_audit_report.json'),
      JSON.stringify(
        {
          table: { exists: tableExists, headers: headerCells, rows: bodyRows, headerLabels },
          search: { calls: searchCalls.length, keywords: searchKeywords, last: lastSearch?.url },
          filter: { calls: filterCalls.length, lastRole: lastFilterRole },
          tab: { urlAfterSpecialty, tabChangeOk, backOk, forwardOk, reloadOk },
          addLink: { href: addHref, ok: addLinkOk },
          export: { download: exportFilename, size: exportSize, endpointHit: exportEndpointHit, preview: exportPreview },
          import: { fileChooserOk: importFileChooserOk, calls: importCalls.length, urls: importCalls.map((c) => c.url) },
          a11y: { selectAriaLabels, inputAriaLabels, errorRoleCount, statusRoleCount, ariaHiddenIconCount },
          network: NETWORK_LOG,
          console: CONSOLE_LOG,
          pageErrors: PAGE_ERRORS,
          finalUrl: page.url(),
          timestamp: new Date().toISOString(),
        },
        null,
        2
      )
    );

    expect(loginOk).toBeTruthy();
  });
});
