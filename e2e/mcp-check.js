// 直接从 Node 调 MCP：登录拿 token → initialize → tools/list → tools/call
const BASE = 'http://localhost:8080';

async function post(path, body, token) {
  const res = await fetch(BASE + path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: JSON.stringify(body),
  });
  const text = await res.text();
  return { status: res.status, body: text };
}

(async () => {
  const u = 'mcp' + Date.now().toString().slice(-6);
  const reg = await post('/api/auth/register', {
    username: u, email: u + '@test.local', password: 'Test123456',
  });
  console.log('注册:', reg.status);
  const token = JSON.parse(reg.body).accessToken;
  console.log('拿到 accessToken:', !!token);

  const init = await post('/mcp', {
    jsonrpc: '2.0', id: 1, method: 'initialize',
    params: { protocolVersion: '2025-06-18', clientInfo: { name: 'node-cli', version: '1' } },
  }, token);
  console.log('\n[initialize]', init.status, init.body.slice(0, 240));

  const list = await post('/mcp', { jsonrpc: '2.0', id: 2, method: 'tools/list' }, token);
  const tools = JSON.parse(list.body).result.tools;
  console.log('\n[tools/list] 工具数:', tools.length);
  tools.forEach((t) => console.log('  -', t.name, '|', t.description));

  const call = await post('/mcp', {
    jsonrpc: '2.0', id: 3, method: 'tools/call',
    params: { name: 'list_projects', arguments: {} },
  }, token);
  console.log('\n[tools/call list_projects]', call.status, call.body.slice(0, 300));

  const noAuth = await post('/mcp', {
    jsonrpc: '2.0', id: 4, method: 'tools/call',
    params: { name: 'list_projects', arguments: {} },
  });
  console.log('\n[未认证]', noAuth.status, noAuth.body.slice(0, 200));

  const bad = await post('/mcp', {
    jsonrpc: '2.0', id: 5, method: 'tools/call',
    params: { name: 'delete_everything', arguments: {} },
  }, token);
  console.log('\n[未知工具]', bad.status, bad.body.slice(0, 200));
})().catch((e) => { console.error('FATAL', e.message); process.exit(1); });
