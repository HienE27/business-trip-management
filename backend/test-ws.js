// WebSocket conflict broadcast test
const WebSocket = require('ws');
const http = require('http');

async function login() {
  return new Promise((resolve, reject) => {
    const data = JSON.stringify({ username: 'admin', password: 'admin123' });
    const req = http.request({
      hostname: 'localhost', port: 8080, path: '/api/v1/auth/login',
      method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Forwarded-For': '10.99.99.99' },
    }, res => {
      let body = '';
      res.on('data', d => body += d);
      res.on('end', () => resolve(JSON.parse(body).data.token));
    });
    req.on('error', reject);
    req.write(data); req.end();
  });
}

async function createTestPeriod(token, name, start, end) {
  return new Promise((resolve, reject) => {
    const data = JSON.stringify({ periodName: name, startDate: start, endDate: end });
    const req = http.request({
      hostname: 'localhost', port: 8080, path: '/api/v1/periods?generatedById=1',
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
    }, res => {
      let body = '';
      res.on('data', d => body += d);
      res.on('end', () => resolve(JSON.parse(body).data.id));
    });
    req.on('error', reject);
    req.write(data); req.end();
  });
}

async function createRequirements(token, periodId) {
  const reqs = [];
  for (let d = 7; d <= 9; d++) {
    for (const st of ['L01','L02','L03']) {
      reqs.push({ workDate: `2027-07-0${d}`, shiftTypeId: st, requiredStaffCount: 1 });
    }
  }
  return new Promise((resolve, reject) => {
    const req = http.request({
      hostname: 'localhost', port: 8080, path: `/api/v1/shift-requirements/period/${periodId}`,
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
    }, res => {
      let body = '';
      res.on('data', d => body += d);
      res.on('end', () => resolve(body));
    });
    req.on('error', reject);
    req.write(JSON.stringify(reqs)); req.end();
  });
}

async function createConflict(token, periodId, staffId, workDate, shiftTypeId) {
  // Create two schedules same date+staff+shift (one to satisfy req, one to conflict)
  return new Promise((resolve, reject) => {
    const data = JSON.stringify({
      staffId, periodId, shiftTypeId, workDate, requirementId: 1,
    });
    const req = http.request({
      hostname: 'localhost', port: 8080, path: '/api/v1/schedules',
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
    }, res => {
      let body = '';
      res.on('data', d => body += d);
      res.on('end', () => resolve({ status: res.statusCode, body }));
    });
    req.on('error', reject);
    req.write(data); req.end();
  });
}

(async () => {
  const token = await login();
  console.log('Logged in');

  // Create fresh test period
  const pid = await createTestPeriod(token, 'WS-Test', '2027-07-07', '2027-07-13');
  console.log('Created period', pid);

  // Connect to WS FIRST so we can see broadcasts
  const ws = new WebSocket('ws://localhost:8080/ws/conflicts');
  const messages = [];
  ws.on('open', () => {
    console.log('WS connected');
    ws.send('CONNECT\naccept-version:1.1,1.2\nheart-beat:0,0\n\n\x00');
  });
  ws.on('message', data => {
    const s = data.toString();
    console.log('WS recv:', s.substring(0, 250));
    messages.push(s);
  });
  ws.on('error', e => console.error('WS error:', e.message));

  await new Promise(r => setTimeout(r, 800));
  ws.send('SUBSCRIBE\nid:sub-0\ndestination:/topic/conflicts\n\n\x00');
  console.log('Subscribed to /topic/conflicts');
  await new Promise(r => setTimeout(r, 800));

  // Now trigger conflict detection (POST a duplicate schedule)
  // First create one
  console.log('Creating schedule 1...');
  const r1 = await createConflict(token, pid, 1, '2027-07-07', 'L01');
  console.log('  Result:', r1.status, r1.body.substring(0, 200));
  await new Promise(r => setTimeout(r, 1000));
  console.log('Creating schedule 2 (duplicate — should conflict)...');
  const r2 = await createConflict(token, pid, 1, '2027-07-07', 'L01');
  console.log('  Result:', r2.status, r2.body.substring(0, 200));

  // Trigger check-conflicts endpoint
  console.log('Calling /schedules/conflicts/check...');
  const cr = await new Promise(r => {
    const req = http.request({
      hostname: 'localhost', port: 8080, path: `/api/v1/schedules/conflicts/check/${pid}`,
      method: 'GET', headers: { 'Authorization': `Bearer ${token}` },
    }, res => { let b=''; res.on('data', d=>b+=d); res.on('end', ()=>r({status: res.statusCode, body: b})); });
    req.end();
  });
  console.log('  Conflict-check status:', cr.status, 'conflicts:', (JSON.parse(cr.body).data?.totalConflicts ?? 'n/a'));

  await new Promise(r => setTimeout(r, 3000));
  console.log('\nTotal WS messages received:', messages.length);
  ws.close();
  process.exit(0);
})().catch(e => { console.error('FATAL:', e); process.exit(1); });
