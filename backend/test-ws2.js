// Test WebSocket conflict broadcast by creating a real conflict (not duplicate)
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

(async () => {
  const token = await login();

  // Find a L01 schedule that has comp-day for adjacent date, then add a schedule on comp day
  const ws = new WebSocket('ws://localhost:8080/ws/conflicts');
  const messages = [];
  ws.on('open', () => {
    ws.send('CONNECT\naccept-version:1.1,1.2\nheart-beat:0,0\n\n\x00');
  });
  ws.on('message', data => {
    const s = data.toString();
    console.log('WS recv:', s.substring(0, 400));
    messages.push(s);
  });
  ws.on('error', e => console.error('WS error:', e.message));

  await new Promise(r => setTimeout(r, 800));
  ws.send('SUBSCRIBE\nid:sub-0\ndestination:/topic/conflicts\n\n\x00');
  console.log('Subscribed to /topic/conflicts');
  await new Promise(r => setTimeout(r, 800));

  // Find a published period with comp-days — use period 4 (already has 2 comp-day conflicts)
  console.log('Calling /schedules/conflicts/check/4 (which has known conflicts)...');
  const cr = await new Promise(r => {
    const req = http.request({
      hostname: 'localhost', port: 8080, path: '/api/v1/schedules/conflicts/check/4',
      method: 'GET', headers: { 'Authorization': `Bearer ${token}` },
    }, res => { let b=''; res.on('data', d=>b+=d); res.on('end', ()=>r({status: res.statusCode, body: b})); });
    req.end();
  });
  const data = JSON.parse(cr.body).data;
  console.log('  Conflicts:', data.totalConflicts, 'Gaps:', data.totalCoverageGaps);
  if (data.conflicts?.length > 0) {
    console.log('  First conflict:', data.conflicts[0]);
  }

  await new Promise(r => setTimeout(r, 5000));
  console.log('\nTotal WS messages received:', messages.length);
  ws.close();
  process.exit(0);
})().catch(e => { console.error('FATAL:', e); process.exit(1); });
