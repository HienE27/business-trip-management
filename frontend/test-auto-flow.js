import puppeteer from 'puppeteer';

async function run() {
  const browser = await puppeteer.launch({
    headless: false,
    args: ['--no-sandbox', '--disable-dev-shm-usage']
  });

  const page = await browser.newPage();
  await page.setViewport({ width: 1440, height: 900 });

  const allResponses = [];
  page.on('response', function(resp) {
    if (resp.url().indexOf('auto-schedule') !== -1 && resp.request().method() !== 'OPTIONS') {
      resp.text().then(function(text) {
        allResponses.push({ url: resp.url(), status: resp.status(), text: text });
      }).catch(function() {});
    }
  });

  // Login
  await page.goto('http://localhost:3000/login', { waitUntil: 'networkidle0', timeout: 30000 });
  await new Promise(function(r) { setTimeout(r, 3000); });
  await page.type('input[name="username"]', 'admin', { delay: 50 });
  await page.type('input[name="password"]', 'admin123', { delay: 50 });
  await page.click('button[type="submit"]');
  await new Promise(function(r) { setTimeout(r, 5000); });

  // Monthly Schedule
  await page.goto('http://localhost:3000/monthly-schedule', { waitUntil: 'networkidle0', timeout: 30000 });
  await new Promise(function(r) { setTimeout(r, 3000); });

  await page.evaluate(function() {
    var sel = document.querySelector('select');
    if (sel) {
      var opts = sel.querySelectorAll('option');
      for (var i = 0; i < opts.length; i++) {
        if (opts[i].text.indexOf('09/2026') !== -1) {
          sel.value = opts[i].value;
          sel.dispatchEvent(new Event('change', { bubbles: true }));
          return;
        }
      }
    }
  });
  await new Promise(function(r) { setTimeout(r, 3000); });

  // Click Auto Schedule
  var btnIdx = await page.evaluate(function() {
    var btns = document.querySelectorAll('button');
    for (var i = 0; i < btns.length; i++) {
      var t = btns[i].textContent || '';
      if (t.indexOf('Auto Schedule') !== -1 && t.indexOf('Làm mới') === -1) return i;
    }
    return -1;
  });

  if (btnIdx >= 0) {
    var btns = await page.$$('button');
    await btns[btnIdx].click();
    console.log('Clicked');
    await new Promise(function(r) { setTimeout(r, 10000); });

    // Get URL and full body
    console.log('URL:', page.url());

    // Get ALL section headings visible on page
    var sections = await page.evaluate(function() {
      var headings = document.querySelectorAll('h1, h2, h3, h4, [class*=title], [class*=headline]');
      var result = [];
      for (var i = 0; i < headings.length; i++) {
        if (headings[i].offsetParent !== null) {
          result.push(headings[i].textContent.trim().substring(0, 80));
        }
      }
      return result;
    });
    console.log('\n=== PAGE SECTIONS ===');
    sections.forEach(function(h) { console.log(' -', h); });

    // Get visible text snippets
    var text = await page.evaluate(function() {
      var t = document.body.innerText;
      return t.substring(0, 8000);
    });
    console.log('\n=== BODY TEXT (first 3000) ===');
    console.log(text.substring(0, 3000));

    // API responses
    console.log('\n=== API RESPONSES ===');
    allResponses.forEach(function(r) {
      try {
        var data = JSON.parse(r.text);
        console.log('Status:', r.status, '| success:', data.success, '| inner:', data.data ? data.data.success : 'N/A');
        if (data.data && data.data.data) {
          console.log('  coverage:', data.data.data.coverageRate, 'created:', data.data.data.totalSchedulesCreated);
        }
      } catch(_e) {
        console.log('PARSE ERROR:', r.text.substring(0, 100));
      }
    });
  }

  await browser.close();
  process.exit(0);
}

run().catch(function(err) { console.error('ERROR:', err.message); process.exit(1); });
