// 补齐三项：新建风格 / 主题切换 / 登出
const puppeteer = require('puppeteer-core');
const ui = require('./ui');

const step = (n, ok, note = '') => console.log(`${ok ? '✅' : '⚠️ '} ${n}${note ? ' — ' + note : ''}`);

(async () => {
  const browser = await puppeteer.launch({
    executablePath: ui.CHROME, headless: false,
    defaultViewport: { width: 1440, height: 900 },
  });
  const page = await browser.newPage();
  await ui.register(page);

  // ---- 1. 新建风格 ----
  await page.goto('http://localhost:3000/styles', { waitUntil: 'networkidle2' });
  await ui.sleep(1000);
  await page.evaluate(() => {
    const b = Array.from(document.querySelectorAll('button')).find((x) => (x.innerText || '').includes('新建风格'));
    if (b) b.click();
  });
  await ui.sleep(900);
  await ui.shot(page, 'A1-style-dialog');
  const filled = await page.evaluate(() => {
    const inputs = Array.from(document.querySelectorAll('.el-dialog input')).filter((i) => i.offsetParent !== null);
    const areas = Array.from(document.querySelectorAll('.el-dialog textarea')).filter((i) => i.offsetParent !== null);
    const set = (el, v) => { if (!el) return false; el.focus(); el.value = v; el.dispatchEvent(new Event('input', { bubbles: true })); return true; };
    const a = set(inputs[0], '克制口语风');
    const b = set(inputs[1], '短句、克制、白描');
    const c = set(areas[0], '请以克制的口语风格写作：短句为主，少用形容词，不写内心独白。');
    return { a, b, c };
  });
  console.log('  填表结果:', JSON.stringify(filled));
  await ui.sleep(300);
  await ui.shot(page, 'A2-style-filled');
  const created = await page.evaluate(() => {
    const b = Array.from(document.querySelectorAll('.el-dialog button')).find((x) => (x.innerText || '').trim() === '创建');
    if (!b) return false; b.click(); return true;
  });
  await ui.sleep(1600);
  await ui.shot(page, 'A3-style-created');
  const styleText = await ui.bodyText(page, 400);
  step('新建风格模板', created && styleText.includes('克制口语风'), created ? '已提交' : '没找到创建按钮');

  // ---- 2. 主题切换（class 挂在 documentElement 上）----
  await page.goto('http://localhost:3000/project', { waitUntil: 'networkidle2' });
  await ui.sleep(900);
  const t0 = await page.evaluate(() => ({ cls: document.documentElement.className, ls: localStorage.getItem('ai-writer-theme') }));
  await page.evaluate(() => document.querySelector('.theme-btn').click());
  await ui.sleep(700);
  const t1 = await page.evaluate(() => ({ cls: document.documentElement.className, ls: localStorage.getItem('ai-writer-theme') }));
  await ui.shot(page, 'A4-theme-dark');
  await page.evaluate(() => document.querySelector('.theme-btn').click());
  await ui.sleep(700);
  const t2 = await page.evaluate(() => ({ cls: document.documentElement.className, ls: localStorage.getItem('ai-writer-theme') }));
  await ui.shot(page, 'A5-theme-eye-care');
  step('主题切换（light → dark → eye-care）',
    t1.ls === 'dark' && t2.ls === 'eye-care',
    `${t0.ls} → ${t1.ls}(${t1.cls}) → ${t2.ls}(${t2.cls})`);

  // ---- 3. 登出（el-dropdown 默认 hover 触发）----
  await page.hover('.user-info');
  await ui.sleep(900);
  await ui.shot(page, 'A6-user-menu');
  const clickedOut = await page.evaluate(() => {
    const items = Array.from(document.querySelectorAll('.el-dropdown-menu__item'))
      .filter((e) => e.offsetParent !== null);
    const hit = items.find((e) => (e.innerText || '').includes('退出'));
    if (!hit) return false; hit.click(); return true;
  });
  await ui.sleep(1800);
  await ui.shot(page, 'A7-logged-out');
  const url = page.url();
  step('登出', clickedOut && url.includes('/login'), `跳到 ${url}`);

  await browser.close();
})().catch((e) => { console.error('FATAL:', e.message); process.exit(1); });
