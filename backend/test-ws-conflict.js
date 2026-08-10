// WebSocket conflict broadcast test
// Uses native WebSocket (no STOMP dep needed for raw endpoint) to /ws/conflicts
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
  console.log('Logged in, token length:', token.length);

  // Try raw WebSocket to /ws/conflicts (STOMP endpoint)
  const ws = new WebSocket('ws://localhost:8080/ws/conflicts');
  ws.on('open', () => {
    console.log('WS connected');
    // Send a STOMP CONNECT frame
    ws.send('CONNECT\naccept-version:1.1,1.2\nheart-beat:0,0\n\n\x00');
    setTimeout(() => {
      ws.send('SUBSCRIBE\nid:sub-0\ndestination:/topic/conflicts\n\n\x00');
      console.log('Subscribed to /topic/conflicts');
    }, 500);
    setTimeout(() => {
      // Trigger a conflict detection (POST a duplicate schedule to force conflict)
      const postData = JSON.stringify({
        staffId: 1, periodId: 19, shiftTypeId: 'L02',
        workDate: '2027-05-03', requirementId: 7722,
      });
      const req = http.request({
        hostname: 'localhost', port: 8080,
        path: '/api/v1/schedules',
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`,
        },
      }, res => {
        let body = '';
        res.on('data', d => body += d);
        res.on('end', () => console.log('POST schedule:', res.statusCode, body.substring(0, 200)));
      });
      req.on('error', e => console.error('POST error:', e.message));
      req.write(postData); req.end();
    }, 1500);
  });
  ws.on('message', data => {
    console.log('WS recv:', data.toString().substring(0, 200));
  });
  ws.on('error', e => console.error('WS error:', e.message));
  ws.on('close', (code, reason) => console.log('WS closed:', code, reason.toString()));

  setTimeout(() => { ws.close(); process.exit(0); }, 10000);
})();
