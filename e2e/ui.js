// 通用 UI 驱动库：注册 / 建项目 / 建章节 / 输入 / AI 操作，全部带诊断
const puppeteer = require('puppeteer-core');
const CHROME = 'C:\\Users\\Administrator\\AppData\\Local\\Google\\Chrome\\Application\\chrome.exe';
const SHOTS = 'D:\\MyProjects\\ai-writer\\e2e\\shots';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function shot(page, name) {
  await page.screenshot({ path: `${SHOTS}\\${name}.png` });
  console.log(`  [截图] ${name}.png  ← ${page.url()}`);
}

async function bodyText(page, limit = 600) {
  return (await page.evaluate(() => document.body.innerText)).replace(/\n{2,}/g, '\n').slice(0, limit);
}

/** 填输入框：按 placeholder 关键字找 */
async function fillByPlaceholder(page, keyword, value) {
  const ok = await page.evaluate((kw, v) => {
    const inputs = Array.from(document.querySelectorAll('input,textarea'));
    const el = inputs.find((i) => (i.getAttribute('placeholder') || '').includes(kw));
    if (!el) return false;
    el.focus();
    el.value = v;
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
    return true;
  }, keyword, value);
  if (!ok) throw new Error(`找不到 placeholder 含「${keyword}」的输入框`);
  return true;
}

/** 按精确文本点击按钮（去空白后相等） */
async function clickButton(page, exact) {
  const ok = await page.evaluate((label) => {
    const btns = Array.from(document.querySelectorAll('button'))
      .filter((b) => b.offsetParent !== null);
    const el = btns.find((b) => (b.innerText || '').replace(/\s/g, '') === label);
    if (!el) return false;
    el.click();
    return true;
  }, exact.replace(/\s/g, ''));
  return ok;
}

/** 按包含文本点击任意可点元素（取最短匹配，最精确） */
async function clickContaining(page, text) {
  return page.evaluate((t) => {
    const els = Array.from(document.querySelectorAll('button,a,div,span'))
      .filter((el) => el.offsetParent !== null && (el.innerText || '').includes(t));
    const hit = els.sort((a, b) => (a.innerText || '').length - (b.innerText || '').length)[0];
    if (!hit) return false;
    hit.click();
    return true;
  }, text);
}

async function register(page) {
  const user = 'demo' + Date.now().toString().slice(-6);
  await page.goto('http://localhost:3000/register', { waitUntil: 'networkidle2' });
  await sleep(600);
  await shot(page, '30-register');

  const inputs = await page.$$('input');
  console.log('  注册页输入框数量:', inputs.length);
  // 依次填：用户名、邮箱、密码、确认密码
  const guesses = [
    ['用户名', user], ['name', user],
    ['邮箱', user + '@test.local'], ['email', user + '@test.local'],
    ['密码', 'Test123456'], ['密码', 'Test123456'],
  ];
  let filled = 0;
  for (const [kw, val] of guesses) {
    try { await fillByPlaceholder(page, kw, val); filled++; } catch { /* 跳过 */ }
  }
  // 兜底：还有空输入框就按顺序填
  await page.evaluate((u, p) => {
    const empties = Array.from(document.querySelectorAll('input')).filter((i) => !i.value);
    empties.forEach((el, idx) => {
      el.focus();
      el.value = idx === 0 ? u : (el.type === 'password' ? p : u + '@test.local');
      el.dispatchEvent(new Event('input', { bubbles: true }));
    });
  }, user, 'Test123456');
  await shot(page, '31-register-filled');

  const clicked = await clickButton(page, '注册');
  console.log('  点注册:', clicked);
  await sleep(3000);
  await shot(page, '32-after-register');

  const url = page.url();
  if (url.includes('/register')) {
    const err = await page.evaluate(() => {
      const el = Array.from(document.querySelectorAll('.el-form-item__error,.el-message,.el-alert'))
        .map((e) => e.innerText.trim()).filter(Boolean);
      return el.join(' | ') || '(无表单错误提示)';
    });
    throw new Error(`注册未成功，仍在 ${url}，错误提示: ${err}`);
  }
  console.log('  注册成功 →', url);
  return user;
}

module.exports = { shot, bodyText, fillByPlaceholder, clickButton, clickContaining, register, sleep, CHROME, SHOTS };
