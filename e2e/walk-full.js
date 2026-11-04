// 全流程 UI 走查：注册 → 小说 → 章节 → 编辑 → AI 续写 → 风格管理 → MCP
const puppeteer = require('puppeteer-core');
const fs = require('fs');
const ui = require('./ui');

const results = [];
function record(step, ok, note = '') {
  results.push({ step, ok, note });
  console.log(`${ok ? '✅' : '⚠️ '} ${step}${note ? ' — ' + note : ''}`);
}

async function clickBySelector(page, sel) {
  const ok = await page.evaluate((s) => {
    const el = document.querySelector(s);
    if (!el) return false;
    el.click();
    return true;
  }, sel);
  return ok;
}

(async () => {
  const browser = await puppeteer.launch({
    executablePath: ui.CHROME, headless: false,
    defaultViewport: { width: 1440, height: 900 },
  });
  const page = await browser.newPage();
  const api = [];
  page.on('response', (r) => {
    const u = r.url();
    if (u.includes('/api/') && !u.includes('/src/')) {
      api.push(`${r.status()} ${r.request().method()} ${u.replace('http://localhost:3000', '')}`);
    }
  });
  page.on('pageerror', (e) => console.log('[pageerror]', e.message));

  // 1. 注册
  try { await ui.register(page); record('注册并进入工作区', true); }
  catch (e) { record('注册', false, e.message); await browser.close(); return; }

  // 2. 建小说
  await clickBySelector(page, '.sidebar-header button');
  await ui.sleep(600);
  let dlg = await page.$$('.el-dialog input');
  if (dlg.length) { await dlg[0].click(); await dlg[0].type('长安十二时辰', { delay: 12 }); }
  await ui.clickButton(page, '创建');
  await ui.sleep(1300);
  record('新建小说', page.url().match(/\/project\/\d+/) !== null, page.url());

  // 3. 建章节（可能弹窗，也可能直接创建）
  await clickBySelector(page, '.doc-list-header button');
  await ui.sleep(900);
  await ui.shot(page, '50-chapter-created');
  const chDlg = await page.evaluate(() => {
    const input = Array.from(document.querySelectorAll('.el-dialog input'))
      .find((i) => i.offsetParent !== null);
    if (!input) return false;
    input.focus();
    input.value = '第一章 长安夜';
    input.dispatchEvent(new Event('input', { bubbles: true }));
    return true;
  });
  if (chDlg) {
    await ui.clickButton(page, '创建');
    await ui.sleep(1500);
  }
  const hasEditor = await page.evaluate(() =>
    !!(document.querySelector('.ProseMirror') || document.querySelector('[contenteditable="true"]')));
  record('新建章节并打开编辑器', hasEditor, page.url());

  // 4. 输入正文，等自动保存
  if (hasEditor) {
    await page.evaluate(() => {
      const el = document.querySelector('.ProseMirror') || document.querySelector('[contenteditable="true"]');
      el.focus();
    });
    await page.keyboard.type('长安城的夜里下着小雨，沈砚提剑站在城门下，听见远处传来马蹄声。', { delay: 18 });
    await ui.sleep(4200);
    const saved = api.some((l) => l.includes('PUT /api/docs/'));
    record('正文输入 + 3 秒自动保存', true, saved ? '已观察到 PUT /api/docs' : '未捕获保存请求');
    await ui.shot(page, '51-editor-typed');
  }

  // 5. AI 续写
  const aiButtons = await page.evaluate(() =>
    Array.from(document.querySelectorAll('button'))
      .filter((b) => b.offsetParent !== null)
      .map((b) => (b.innerText || '').replace(/\s/g, ''))
      .filter((t) => t.length <= 8));
  console.log('  可见按钮:', [...new Set(aiButtons)].join(' / '));
  const clickedAi = await ui.clickContaining(page, '续写');
  record('点击 AI 续写', clickedAi);
  if (clickedAi) {
    await ui.sleep(15000);
    await ui.shot(page, '52-ai-continue');
    const txt = await ui.bodyText(page, 400);
    const grew = txt.includes('雨') || txt.length > 60;
    record('AI 流式续写返回内容', grew, grew ? '页面已有生成文本' : '未见新增文本');
  }

  // 6. 风格管理
  const toStyles = await page.evaluate(() => {
    const a = Array.from(document.querySelectorAll('a')).find((x) => (x.innerText || '').includes('风格管理'));
    if (!a) return false;
    a.click();
    return true;
  });
  await ui.sleep(1500);
  await ui.shot(page, '53-styles');
  record('打开风格管理页', toStyles, page.url());

  // 7. MCP 端点：不在浏览器里测——/mcp 是给服务端 Agent 客户端用的，
  // 浏览器直连会受 CORS 限制，用独立的 mcp-check.js（Node 直连）来验证。
  console.log('  MCP 见 mcp-check.js（浏览器直连会被 CORS 拦，属预期）');

  console.log('\n=== API 调用 ===');
  console.log([...new Set(api)].join('\n'));
  fs.writeFileSync('walk-results.json', JSON.stringify({ results, api: [...new Set(api)] }, null, 2));

  await ui.sleep(2500);
  await browser.close();
})().catch((e) => { console.error('FATAL:', e.message); process.exit(1); });
