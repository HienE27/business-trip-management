// Final verification: use UI + API to confirm config-driven scheduling works
const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 1024 } });
  const page = await ctx.newPage();
  page.setDefaultTimeout(15000);

  // Login via API (faster than UI)
  await page.goto('http://localhost:3000/login', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(1500);
  await page.fill('input[type="text"], input[name="username"]', 'admin');
  await page.fill('input[type="password"]', 'admin123');
  await page.click('button[type="submit"]');
  await page.waitForTimeout(3000);

  // Set new config: increase min for all types to test config-driven
  const setConfig = await page.evaluate(async () => {
    const loginResp = await fetch('http://localhost:8080/api/v1/auth/login', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'admin', password: 'admin123' }),
    });
    const token = (await loginResp.json()).data.token;
    const newConfig = {
      enabled: true,
      l01MinPerDay: 4, l01MaxPerDay: 5,
      l02MinPerDay: 5, l02MaxPerDay: 6,
      l03MinPerDay: 5, l03MaxPerDay: 6,
      l04MinPerDay: 4, l04MaxPerDay: 5,
      l01MinPerWeek: 20, l02MinPerWeek: 20, l03MinPerWeek: 20, l04MinPerWeek: 20,
      l01MaxPerWeek: 30, l02MaxPerWeek: 30, l03MaxPerWeek: 30, l04MaxPerWeek: 30,
      holidayMode: 'SKIP', removedShiftTypes: [],
      l04CrossSpecialty: true, l04CrossSpecialtyRatio: 1.0
    };
    const saveResp = await fetch('http://localhost:8080/api/v1/auto-schedule/auto-gen-config', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify(newConfig),
    });
    const saveResult = await saveResp.json();
    const verifyResp = await fetch('http://localhost:8080/api/v1/auto-schedule/auto-gen-config', {
      headers: { Authorization: `Bearer ${token}` },
    });
    const verify = await verifyResp.json();
    return { saveSuccess: saveResult.success, verified: verify.data };
  });
  console.log('Config set:', JSON.stringify({
    saveSuccess: setConfig.saveSuccess,
    l01: `${setConfig.verified.l01MinPerDay}-${setConfig.verified.l01MaxPerDay}`,
    l02: `${setConfig.verified.l02MinPerDay}-${setConfig.verified.l02MaxPerDay}`,
    l03: `${setConfig.verified.l03MinPerDay}-${setConfig.verified.l03MaxPerDay}`,
    l04: `${setConfig.verified.l04MinPerDay}-${setConfig.verified.l04MaxPerDay}`,
  }));

  // Wait then navigate to config page to verify UI reflects new values
  await page.goto('http://localhost:3000/auto-scheduling/algorithm-config', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(3000);
  await page.screenshot({ path: 'E:\\DACN\\business-trip-management\\test_10_config_new.png', fullPage: true });

  // Run preview
  const previewResult = await page.evaluate(async () => {
    const loginResp = await fetch('http://localhost:8080/api/v1/auth/login', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'admin', password: 'admin123' }),
    });
    const token = (await loginResp.json()).data.token;
    const previewResp = await fetch('http://localhost:8080/api/v1/auto-schedule/preview', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ periodId: 19, algorithmType: 'GREEDY', excludedStaffIds: [] }),
    });
    const result = await previewResp.json();
    return {
      success: result.success,
      total: result.data?.totalSchedulesCreated,
      coverage: result.data?.coverageRate,
      balance: result.data?.balanceScore,
      message: result.data?.message,
      perStaff: (() => {
        const byStaff = {};
        (result.data?.schedules || []).forEach(s => {
          if (!byStaff[s.staffId]) byStaff[s.staffId] = { name: s.staffName, L01:0, L02:0, L03:0, L04:0 };
          byStaff[s.staffId][s.shiftTypeId]++;
        });
        return Object.entries(byStaff)
          .map(([id, v]) => ({ id, name: v.name, total: v.L01+v.L02+v.L03+v.L04, L01:v.L01, L02:v.L02, L03:v.L03, L04:v.L04 }))
          .sort((a,b) => b.total - a.total);
      })()
    };
  });
  console.log('Preview result with new config:');
  console.log(`  Total: ${previewResult.total}, Coverage: ${previewResult.coverage}, Balance: ${previewResult.balance}`);
  console.log('  Per-staff distribution:');
  previewResult.perStaff.forEach(s => {
    console.log(`    ${s.id} ${s.name}: total=${s.total} L01=${s.L01} L02=${s.L02} L03=${s.L03} L04=${s.L04}`);
  });

  // Restore original config
  await page.evaluate(async () => {
    const loginResp = await fetch('http://localhost:8080/api/v1/auth/login', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'admin', password: 'admin123' }),
    });
    const token = (await loginResp.json()).data.token;
    const originalConfig = {
      enabled: true,
      l01MinPerDay: 3, l01MaxPerDay: 4,
      l02MinPerDay: 4, l02MaxPerDay: 5,
      l03MinPerDay: 4, l03MaxPerDay: 5,
      l04MinPerDay: 3, l04MaxPerDay: 5,
      l01MinPerWeek: 14, l02MinPerWeek: 14, l03MinPerWeek: 14, l04MinPerWeek: 16,
      l01MaxPerWeek: 20, l02MaxPerWeek: 22, l03MaxPerWeek: 22, l04MaxPerWeek: 24,
      holidayMode: 'SKIP', removedShiftTypes: [],
      l04CrossSpecialty: true, l04CrossSpecialtyRatio: 1.0
    };
    await fetch('http://localhost:8080/api/v1/auto-schedule/auto-gen-config', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify(originalConfig),
    });
  });

  await browser.close();
  console.log('Done.');
})().catch(e => {
  console.error('Failed:', e.message);
  process.exit(1);
});