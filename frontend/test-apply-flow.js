const puppeteer = require('puppeteer');

async function run() {
  const browser = await puppeteer.launch({
    headless: false,
    args: ['--no-sandbox', '--disable-dev-shm-usage']
  });
  const page = await browser.newPage();
  await page.setViewport({ width: 1440, height: 900 });

  // Login
  console.log('=== LOGIN ===');
  await page.goto('http://localhost:3000/login', { waitUntil: 'networkidle0', timeout: 30000 });
  await new Promise(function(r) { setTimeout(r, 3000); });
  await page.type('input[name="username"]', 'admin', { delay: 50 });
  await page.type('input[name="password"]', 'admin123', { delay: 50 });
  await page.click('button[type="submit"]');
  await new Promise(function(r) { setTimeout(r, 5000); });
  console.log('Logged in');

  // Direct API test
  console.log('\n=== DIRECT API TEST ===');
  var result = await page.evaluate(async function() {
    var token = localStorage.getItem('medschedule.token');

    // 1. Auto schedule preview
    var r1 = await fetch('http://localhost:8080/api/v1/auto-schedule/preview', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token },
      body: JSON.stringify({ periodId: 6, algorithmType: 'GREEDY', maxIterations: 1000, excludedStaffIds: [] })
    });
    var d1 = await r1.json();

    if (!d1.data) {
      return { error: 'No data in response', raw: JSON.stringify(d1).substring(0, 200) };
    }

    var previewResult = d1.data;
    console.log('Preview success:', previewResult.success);
    console.log('Coverage:', previewResult.coverageRate);
    console.log('Schedules:', previewResult.schedules ? previewResult.schedules.length : 'undefined');

    // Build schedules array for apply
    var schedules = [];
    if (previewResult.schedules && previewResult.schedules.length > 0) {
      var allNull = previewResult.schedules.every(function(s) { return s.scheduleId == null; });
      schedules = previewResult.schedules.map(function(s) {
        return { workDate: s.workDate, shiftTypeId: s.shiftTypeId, staffId: s.staffId };
      });
      console.log('All scheduleIds null (preview mode):', allNull, '| Schedules to apply:', schedules.length);
    }

    // 2. Apply preview
    var r2 = await fetch('http://localhost:8080/api/v1/auto-schedule/apply-preview', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token },
      body: JSON.stringify({ periodId: 6, algorithmType: 'GREEDY', schedules: schedules })
    });
    var d2 = await r2.json();

    // 3. Check schedules in DB
    var r3 = await fetch('http://localhost:8080/api/v1/schedules/period/6', {
      headers: { Authorization: 'Bearer ' + token }
    });
    var d3 = await r3.json();

    // 4. Check compensation days
    var r4 = await fetch('http://localhost:8080/api/v1/schedules/compensation-days/6', {
      headers: { Authorization: 'Bearer ' + token }
    });
    var d4 = await r4.json();

    // 5. Check conflicts
    var r5 = await fetch('http://localhost:8080/api/v1/schedules/conflicts/check/6', {
      headers: { Authorization: 'Bearer ' + token }
    });
    var d5 = await r5.json();

    return {
      preview: { success: previewResult.success, coverage: previewResult.coverageRate, balance: previewResult.balanceScore, conflicts: previewResult.conflictCount, schedules: previewResult.schedules ? previewResult.schedules.length : 0, totalCreated: previewResult.totalSchedulesCreated },
      apply: { status: r2.status, success: d2.success, message: d2.message, data: d2.data ? { created: d2.data.totalSchedulesCreated } : null },
      schedules: { count: d3.data ? d3.data.length : 0, l01: d3.data ? d3.data.filter(function(s) { return s.shiftType.id === 'L01'; }).length : 0, l02: d3.data ? d3.data.filter(function(s) { return s.shiftType.id === 'L02'; }).length : 0, l03: d3.data ? d3.data.filter(function(s) { return s.shiftType.id === 'L03'; }).length : 0, l04: d3.data ? d3.data.filter(function(s) { return s.shiftType.id === 'L04'; }).length : 0 },
      compDays: d4.data ? (d4.data.data ? d4.data.data.length : (Array.isArray(d4.data) ? d4.data.length : 0)) : 0,
      conflicts: d5.data ? (d5.data.totalConflicts !== undefined ? d5.data.totalConflicts : d5.data.length) : 'N/A'
    };
  });

  if (result.error) {
    console.log('ERROR:', result.error, result.raw);
  } else {
    console.log('\n--- PREVIEW ---');
    console.log(JSON.stringify(result.preview, null, 2));
    console.log('\n--- APPLY ---');
    console.log(JSON.stringify(result.apply, null, 2));
    console.log('\n--- SCHEDULES IN DB ---');
    console.log(JSON.stringify(result.schedules, null, 2));
    console.log('\n--- COMPENSATION DAYS ---');
    console.log('Count:', result.compDays);
    console.log('\n--- CONFLICTS ---');
    console.log('Total:', result.conflicts);
  }

  // Test publish
  console.log('\n=== TEST PUBLISH ===');
  var publishResult = await page.evaluate(async function() {
    var token = localStorage.getItem('medschedule.token');
    var r = await fetch('http://localhost:8080/api/v1/periods/6/publish', {
      method: 'POST',
      headers: { Authorization: 'Bearer ' + token }
    });
    var d = await r.json();
    return { status: r.status, success: d.success, message: d.message };
  });
  console.log(JSON.stringify(publishResult, null, 2));

  console.log('\n=== DONE ===');
  await browser.close();
  process.exit(0);
}

run().catch(function(err) { console.error('ERROR:', err.message); process.exit(1); });
