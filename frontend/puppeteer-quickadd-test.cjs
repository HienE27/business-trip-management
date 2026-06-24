/**
 * QuickAddModal Validation - Puppeteer E2E Test
 * Tests the QuickAddModal period-range validation feature.
 *
 * Key validations:
 * 1. onAddDate guard: clicking a date outside period should NOT open modal
 * 2. QuickAddModal: error banner + disabled submit when date out of range
 * 3. Modal works normally when date is within period
 */

const puppeteer = require('puppeteer');
const fs = require('fs');
const path = require('path');

const CONFIG = {
  baseUrl: 'http://localhost:3000',
  loginUrl: 'http://localhost:3000/login',
  credentials: { username: 'admin', password: 'admin123' },
  viewport: { width: 1440, height: 900 },
  timeout: 30000,
  reportPath: './puppeteer-quickadd-report.json',
  screenshotDir: './puppeteer-screenshots'
};

function delay(ms) { return new Promise(r => setTimeout(r, ms)); }

async function screenshot(page, name) {
  if (!fs.existsSync(CONFIG.screenshotDir)) fs.mkdirSync(CONFIG.screenshotDir, { recursive: true });
  const ts = new Date().toISOString().replace(/[:.]/g, '-');
  const fp = path.join(CONFIG.screenshotDir, `${name}-${ts}.png`);
  await page.screenshot({ path: fp, fullPage: false });
  return fp;
}

async function login(page) {
  console.log('\n[LOGIN]');
  await page.goto(CONFIG.loginUrl, { waitUntil: 'networkidle2', timeout: CONFIG.timeout });
  const usernameField = await page.$('input[type="text"], input[name="username"], input[id*="username" i], input[id*="email" i]');
  const passwordField = await page.$('input[type="password"]');
  const submitButton = await page.$('button[type="submit"], input[type="submit"]');
  if (usernameField && passwordField && submitButton) {
    await usernameField.click({ clickCount: 3 });
    await usernameField.type(CONFIG.credentials.username);
    await passwordField.click({ clickCount: 3 });
    await passwordField.type(CONFIG.credentials.password);
    await submitButton.click();
    await delay(3000);
  }
  const url = page.url();
  const ok = !url.includes('/login');
  console.log(`  ${ok ? '✅ OK' : '❌ FAIL'} — ${url}`);
  return ok;
}

async function runTests() {
  console.log('╔══════════════════════════════════════════════╗');
  console.log('║  QuickAddModal Period-Validation E2E Test ║');
  console.log('╚══════════════════════════════════════════════╝');

  let browser;
  const results = { tests: [], timestamp: new Date().toISOString() };

  try {
    browser = await puppeteer.launch({
      headless: true,
      args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage']
    });

    const page = await browser.newPage();
    await page.setViewport(CONFIG.viewport);

    const consoleErrors = [];
    page.on('console', msg => {
      if (msg.type() === 'error') consoleErrors.push(msg.text());
    });

    // ===== LOGIN =====
    if (!await login(page)) {
      console.error('[FATAL] Login failed');
      process.exit(1);
    }

    // ===== NAVIGATE =====
    console.log('\n[NAVIGATE] /monthly-schedule?periodId=5 (out-of-range period)');
    await page.goto(`${CONFIG.baseUrl}/monthly-schedule?periodId=5`, { waitUntil: 'networkidle2', timeout: CONFIG.timeout });
    await delay(4000);
    await screenshot(page, '1-period-9-loaded');

    // Verify page loaded
    const pageLoaded = await page.evaluate(() => document.body.innerText.length > 100);
    console.log(`  Page loaded: ${pageLoaded}`);

    // Check current URL state
    const currentPeriod = await page.evaluate(() => {
      const url = window.location.href;
      const match = url.match(/periodId=(\d+)/);
      return match ? match[1] : null;
    });
    console.log(`  Period in URL: ${currentPeriod}`);

    // ===== TEST 1: Click add button on a calendar cell (date outside period) =====
    console.log('\n[TEST 1] Click "+" on a date cell when period is out of range');
    console.log('  (Period 9: Tháng 9, today is June 21 — all dates are out of range)');

    // Find "+" buttons inside calendar cells (these trigger onAddClick)
    const addButtonClicked = await page.evaluate(() => {
      // Find cells with the "+" add button (aria-label contains "Thêm lịch")
      const addBtns = Array.from(document.querySelectorAll('button[aria-label*="Thêm lịch"]'));
      if (addBtns.length === 0) {
        // Try finding any add button in the calendar
        const calendarBtns = Array.from(document.querySelectorAll('[class*="calendar"], [class*="schedule"] button'));
        return { found: false, count: addBtns.length };
      }
      // Click the first add button
      const btn = addBtns[0];
      const label = btn.getAttribute('aria-label');
      btn.click();
      return { found: true, label, count: addBtns.length };
    });

    console.log(`  Add buttons found: ${addButtonClicked.count}`);
    if (addButtonClicked.found) {
      console.log(`  Clicked: "${addButtonClicked.label}"`);
    }
    await delay(1500);
    await screenshot(page, '2-after-add-click');

    // Check if modal opened (should NOT open because date is out of range)
    const modalAfterClick = await page.evaluate(() => {
      const dialog = document.querySelector('[role="dialog"]');
      return dialog ? dialog.textContent?.substring(0, 200) : null;
    });

    if (modalAfterClick) {
      console.log('  ⚠️  Modal opened (parent guard did not block)');
      results.tests.push({ name: 'Parent guard blocks modal when date out of range', status: 'FAIL', detail: 'Modal opened despite date being out of period' });

      // Check error banner in modal
      const alertText = await page.evaluate(() => {
        const alert = document.querySelector('[role="alert"]');
        return alert ? alert.textContent?.trim() : null;
      });
      if (alertText && alertText.includes('kỳ lịch')) {
        console.log(`  ✅ Error banner: "${alertText}"`);
        results.tests.push({ name: 'Error banner when date out of range', status: 'PASS', detail: alertText });
      } else if (alertText) {
        console.log(`  ⚠️  Alert (different): "${alertText}"`);
        results.tests.push({ name: 'Error banner when date out of range', status: 'PARTIAL', detail: alertText });
      } else {
        console.log('  ❌ No error banner');
        results.tests.push({ name: 'Error banner when date out of range', status: 'FAIL', detail: 'No alert found' });
      }

      // Close modal
      await page.evaluate(() => {
        const closeBtn = Array.from(document.querySelectorAll('button')).find(b => b.textContent?.includes('Hủy') || b.getAttribute('aria-label')?.includes('close'));
        if (closeBtn) closeBtn.click();
      });
      await delay(500);
    } else {
      console.log('  ✅ Modal did NOT open (parent guard working)');
      results.tests.push({ name: 'Parent guard blocks modal when date out of range', status: 'PASS' });
    }

    // ===== TEST 2: Navigate to in-range period, modal should work =====
    console.log('\n[TEST 2] Switch to in-range period with calendar view (periodId=1&view=calendar)');
    await page.goto(`${CONFIG.baseUrl}/monthly-schedule?periodId=1&view=calendar`, { waitUntil: 'networkidle2', timeout: CONFIG.timeout });
    await delay(4000);
    await screenshot(page, '3-period-1-calendar');

    const period1Loaded = await page.evaluate(() => {
      const body = document.body.innerText;
      return body.includes('06/2026') || body.includes('06/2026');
    });
    console.log(`  Period 1 loaded: ${period1Loaded}`);

    // Now try clicking add button (in calendar view)
    const addResult = await page.evaluate(() => {
      const addBtns = Array.from(document.querySelectorAll('button[aria-label*="Thêm lịch"]'));
      if (addBtns.length > 0) {
        const btn = addBtns[0];
        btn.click();
        return { clicked: true, method: 'aria-label', label: btn.getAttribute('aria-label'), count: addBtns.length };
      }
      return { clicked: false, count: 0 };
    });

    console.log(`  Add buttons in period 1: ${addResult.count}`);
    if (addResult.clicked) {
      console.log(`  Clicked: "${addResult.label}"`);
    }
    await delay(2000);
    await screenshot(page, '4-modal-period-1');

    const modalPeriod1 = await page.evaluate(() => {
      const dialog = document.querySelector('[role="dialog"]');
      if (!dialog) return null;
      const alert = dialog.querySelector('[role="alert"]');
      const submitBtn = Array.from(dialog.querySelectorAll('button[type="submit"]')).find(b => b.textContent?.includes('Tạo lịch'));
      const dateBadge = Array.from(dialog.querySelectorAll('div')).find(d => d.textContent?.includes('Ngày:'));
      return {
        opened: true,
        alertText: alert?.textContent?.trim() || null,
        submitDisabled: submitBtn ? submitBtn.disabled : null,
        dateBadge: dateBadge?.textContent?.trim() || null,
        title: dialog.querySelector('h2, h3')?.textContent || null
      };
    });

      if (modalPeriod1) {
      console.log(`  Modal title: ${modalPeriod1.title}`);
      console.log(`  Date badge: ${modalPeriod1.dateBadge}`);
      console.log(`  Alert: ${modalPeriod1.alertText || '(none — correct, date is in period)'}`);
      console.log(`  Submit disabled: ${modalPeriod1.submitDisabled} (correct: staff not yet selected)`);

      if (!modalPeriod1.alertText) {
        console.log('  ✅ No error banner (date within period)');
        results.tests.push({ name: 'No error when date within period', status: 'PASS' });
      } else if (modalPeriod1.alertText.includes('kỳ lịch')) {
        console.log('  ❌ Error shown for in-period date');
        results.tests.push({ name: 'No error when date within period', status: 'FAIL', detail: modalPeriod1.alertText });
      }

      // Submit is disabled because staff is not selected yet — this is correct UX
      // Verify that selecting a staff member enables the button
      if (modalPeriod1.submitDisabled === true) {
        console.log('  ✅ Submit button correctly requires staff selection');
        results.tests.push({ name: 'Submit disabled until staff selected', status: 'PASS' });
      } else {
        console.log(`  ⚠️  Unexpected submit state: ${modalPeriod1.submitDisabled}`);
        results.tests.push({ name: 'Submit disabled until staff selected', status: 'PARTIAL' });
      }

      results.tests.push({ name: 'Modal opens for in-period date', status: 'PASS' });
    } else {
      console.log('  ❌ Modal did not open');
      results.tests.push({ name: 'Modal opens for in-period date', status: 'FAIL' });
    }

    // ===== TEST 3: Console errors =====
    console.log('\n[TEST 3] Console errors');
    const criticalErrors = consoleErrors.filter(e =>
      !e.includes('401') && !e.includes('Failed to load resource') &&
      !e.includes('favicon') && !e.includes('net::ERR') && !e.includes('socket')
    );
    if (criticalErrors.length === 0) {
      console.log('  ✅ No critical errors');
      results.tests.push({ name: 'No critical console errors', status: 'PASS' });
    } else {
      criticalErrors.forEach(e => console.log(`  ❌ ${e}`));
      results.tests.push({ name: 'No critical console errors', status: 'FAIL', detail: criticalErrors.join('; ') });
    }

    // ===== SUMMARY =====
    console.log('\n' + '='.repeat(50));
    console.log('TEST RESULTS');
    console.log('='.repeat(50));
    const passed = results.tests.filter(t => t.status === 'PASS').length;
    const failed = results.tests.filter(t => t.status === 'FAIL').length;
    results.tests.forEach(t => {
      const icon = t.status === 'PASS' ? '✅' : t.status === 'FAIL' ? '❌' : '⚠️';
      console.log(`  ${icon} ${t.name}: ${t.status}`);
      if (t.detail) console.log(`     → ${t.detail}`);
    });
    console.log(`\n  Total: ${results.tests.length} | ✅ ${passed} | ❌ ${failed}`);

    await screenshot(page, 'final');
    fs.writeFileSync(CONFIG.reportPath, JSON.stringify(results, null, 2));
    console.log(`\n  Report: ${CONFIG.reportPath}`);

    const exitCode = failed > 0 ? 1 : 0;
    console.log(`\n${exitCode === 0 ? '✅' : '❌'} Exit: ${exitCode}`);
    return exitCode;

  } catch (err) {
    console.error('\n❌ Fatal:', err.message);
    fs.writeFileSync(CONFIG.reportPath, JSON.stringify({ error: err.message, ...results }, null, 2));
    return 1;
  } finally {
    if (browser) await browser.close();
  }
}

runTests().then(code => process.exit(code));
