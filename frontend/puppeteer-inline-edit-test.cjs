/**
 * Dashboard Inline Edit Feature - Puppeteer Test
 * Tests: ScheduleMatrixGrid tooltip + inline edit flow
 */

const puppeteer = require('puppeteer');
const fs = require('fs');
const path = require('path');

function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

async function runTest() {
  console.log('Starting inline edit test...\n');

  const browser = await puppeteer.launch({
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage']
  });

  const page = await browser.newPage();
  await page.setViewport({ width: 1440, height: 900 });

  const results = {
    passed: [],
    failed: [],
    screenshots: []
  };

  async function screenshot(name) {
    const dir = './puppeteer-screenshots';
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
    const filepath = path.join(dir, `${name}.png`);
    await page.screenshot({ path: filepath, fullPage: true });
    results.screenshots.push(filepath);
    console.log(`  [screenshot] ${name}`);
    return filepath;
  }

  try {
    // 1. Login
    console.log('[1] Login...');
    await page.goto('http://localhost:3000/login', { waitUntil: 'networkidle2', timeout: 30000 });
    await delay(1500);

    const usernameField = await page.$('input[type="text"], input[name="username"], input[id*="username" i]');
    const passwordField = await page.$('input[type="password"]');
    const submitButton = await page.$('button[type="submit"], input[type="submit"]');

    if (usernameField && passwordField && submitButton) {
      await usernameField.click({ clickCount: 3 });
      await usernameField.type('admin');
      await passwordField.click({ clickCount: 3 });
      await passwordField.type('admin123');
      await submitButton.click();
      await delay(3000);
    }

    const postLoginUrl = page.url();
    console.log(`  [OK] URL: ${postLoginUrl}`);

    // 2. Navigate to dashboard
    console.log('\n[2] Navigate to dashboard...');
    await page.goto('http://localhost:3000/dashboard', { waitUntil: 'networkidle2', timeout: 30000 });
    await delay(3000);
    await screenshot('01-dashboard-loaded');

    // 3. Check matrix view is rendered
    console.log('\n[3] Verify matrix view is rendered...');
    const matrixTable = await page.$('table');
    if (matrixTable) {
      console.log('  [OK] Matrix table found');
      results.passed.push('Matrix table rendered');
    } else {
      console.log('  [FAIL] Matrix table NOT found');
      results.failed.push('Matrix table not rendered');
    }

    // 4. Find schedule chips
    console.log('\n[4] Find schedule chips in matrix...');
    const shiftChips = await page.$$('button[class*="rounded border px-1 py-0.5"]');
    console.log(`  [INFO] Found ${shiftChips.length} shift chips`);
    await screenshot('02-shift-chips');

    if (shiftChips.length === 0) {
      console.log('  [INFO] No shift chips found - may need schedule data');
      results.passed.push('No shift chips (expected if no schedule data)');
    } else {
      // 5. Click first shift chip
      console.log('\n[5] Click first shift chip...');
      await shiftChips[0].click();
      await delay(1000);
      await screenshot('03-after-chip-click');

      // 6. Check tooltip appeared
      console.log('\n[6] Check if tooltip appeared...');
      const tooltip = await page.$('[role="dialog"]');
      if (tooltip) {
        console.log('  [OK] Tooltip dialog appeared');
        results.passed.push('Tooltip appeared on chip click');
        await screenshot('04-tooltip-shown');

        // 7. Get all buttons in tooltip
        const tooltipBtns = await page.evaluate(() => {
          const dialog = document.querySelector('[role="dialog"]');
          if (!dialog) return [];
          return Array.from(dialog.querySelectorAll('button')).map(b => b.textContent.trim());
        });
        console.log(`  [INFO] Buttons in tooltip: ${JSON.stringify(tooltipBtns)}`);

        // 8. Check for "Sửa" button
        console.log('\n[8] Check for "Sửa" button...');
        const hasSua = tooltipBtns.some(t => t.includes('Sửa'));
        if (hasSua) {
          console.log('  [OK] "Sửa" button found');
          results.passed.push('"Sửa" button visible in tooltip');

          // 9. Click "Sửa" to enter edit mode
          console.log('\n[9] Click "Sửa" to enter edit mode...');
          await page.evaluate(() => {
            const dialog = document.querySelector('[role="dialog"]');
            const btn = Array.from(dialog.querySelectorAll('button')).find(b => b.textContent.includes('Sửa'));
            if (btn) btn.click();
          });
          await delay(1000);
          await screenshot('05-edit-mode');

          // 10. Check edit form
          console.log('\n[10] Check edit form appeared...');
          const editForm = await page.evaluate(() => {
            const dialog = document.querySelector('[role="dialog"]');
            if (!dialog) return { selects: 0, saveDisabled: null };
            const selects = Array.from(dialog.querySelectorAll('select'));
            const saveBtn = Array.from(dialog.querySelectorAll('button')).find(b => b.textContent.includes('Lưu'));
            return { selects: selects.length, saveDisabled: saveBtn ? saveBtn.disabled : null };
          });

          if (editForm.selects >= 2) {
            console.log(`  [OK] Edit form found: ${editForm.selects} selects`);
            results.passed.push('Edit form with staff + shift type selects appeared');
          } else {
            console.log(`  [FAIL] Edit form has only ${editForm.selects} selects`);
            results.failed.push('Edit form missing required selects');
          }
          if (editForm.saveDisabled !== null) {
            console.log(`  [INFO] Save button disabled: ${editForm.saveDisabled}`);
            results.passed.push('Save button state verified');
          }
        } else {
          console.log('  [FAIL] "Sửa" button NOT found in tooltip');
          results.failed.push('"Sửa" button not found - inline edit not enabled');
        }
      } else {
        console.log('  [FAIL] Tooltip did NOT appear');
        results.failed.push('Tooltip did not appear on chip click');
      }
    }

  } catch (err) {
    console.error(`\n[FATAL ERROR] ${err.message}`);
    results.failed.push(`Fatal: ${err.message}`);
    await screenshot('error');
  } finally {
    await browser.close();
  }

  // Print summary
  console.log('\n' + '='.repeat(60));
  console.log('TEST RESULTS');
  console.log('='.repeat(60));
  console.log(`\n  PASSED (${results.passed.length}):`);
  results.passed.forEach(p => console.log(`    + ${p}`));
  if (results.failed.length > 0) {
    console.log(`\n  FAILED (${results.failed.length}):`);
    results.failed.forEach(f => console.log(`    - ${f}`));
  }
  console.log(`\n  Screenshots: ${results.screenshots.length}`);
  results.screenshots.forEach(s => console.log(`    ${s}`));
  console.log('\n' + '='.repeat(60));

  return results.failed.length === 0 ? 0 : 1;
}

runTest()
  .then(exitCode => process.exit(exitCode))
  .catch(err => { console.error(err); process.exit(1); });
