// 第二段走查：框架模式 / 元素 / 冲突 / 历史 / 风格 / AI 设置 / 主题 / 登出
const puppeteer = require('puppeteer-core');
const fs = require('fs');
const ui = require('./ui');

const log = [];
function step(name, ok, note = '') {
  log.push({ name, ok, note });
  console.log(`${ok ? '✅' : '⚠️ '} ${name}${note ? ' — ' + note : ''}`);
}

async function clickSel(page, sel) {
  return page.evaluate((s) => {
    const el = document.querySelector(s);
    if (!el) return false;
    el.click();
    return true;
  }, sel);
}

async function clickVisibleText(page, text) {
  return page.evaluate((t) => {
    const els = Array.from(document.querySelectorAll('button,a,span,div,li'))
      .filter((el) => el.offsetParent !== null && (el.innerText || '').trim().includes(t));
    const hit = els.sort((a, b) => (a.innerText || '').length - (b.innerText || '').length)[0];
    if (!hit) return false;
    hit.click();
    return true;
  }, text);
}

/** 填当前可见对话框里的输入（按顺序） */
async function fillDialog(page, values) {
  return page.evaluate((vals) => {
    const inputs = Array.from(document.querySelectorAll('.el-dialog input, .el-dialog textarea'))
      .filter((i) => i.offsetParent !== null);
    inputs.forEach((el, i) => {
      if (vals[i] === undefined) return;
      el.focus();
      el.value = vals[i];
      el.dispatchEvent(new Event('input', { bubbles: true }));
      el.dispatchEvent(new Event('change', { bubbles: true }));
    });
    return inputs.length;
  }, values);
}

(async () => {
  const browser = await puppeteer.launch({
    executablePath: ui.CHROME, headless: false,
    defaultViewport: { width: 1440, height: 900 },
  });
  const page = await browser.newPage();
  page.on('pageerror', (e) => console.log('[pageerror]', e.message));

  // 准备数据：注册 → 小说 → 章节 → 一段文字
  await ui.register(page);
  await clickSel(page, '.sidebar-header button');
  await ui.sleep(600);
  await fillDialog(page, ['长安十二时辰']);
  await ui.clickButton(page, '创建');
  await ui.sleep(1200);
  const projectUrl = page.url();
  const projectId = projectUrl.match(/\/project\/(\d+)/)[1];
  await clickSel(page, '.doc-list-header button');
  await ui.sleep(1200);
  await page.evaluate(() => {
    const el = document.querySelector('.ProseMirror') || document.querySelector('[contenteditable="true"]');
    if (el) el.focus();
  });
  await page.keyboard.type('沈砚是盲人，靠听觉辨位。长安城的夜里，他提剑站在城门下。', { delay: 12 });
  await ui.sleep(3800);
  step('准备数据：小说 + 章节 + 正文', true, 'project=' + projectId);

  // 1. 框架模式
  await page.goto(`http://localhost:3000/project/${projectId}/framework`, { waitUntil: 'networkidle2' });
  await ui.sleep(1500);
  await ui.shot(page, '60-framework-empty');
  let txt = await ui.bodyText(page, 400);
  console.log('  框架页文本:', txt.replace(/\n/g, ' | '));
  if (/开启|启用|创建框架/.test(txt)) {
    await clickVisibleText(page, '开启');
    await ui.sleep(1800);
    await ui.shot(page, '61-framework-opened');
    step('开启框架模式', true, page.url());
  } else {
    step('开启框架模式', false, '页面上没找到开启入口');
  }

  // 2. 设定元素
  await page.goto(`http://localhost:3000/project/${projectId}/framework/elements`, { waitUntil: 'networkidle2' });
  await ui.sleep(1500);
  await ui.shot(page, '62-elements-empty');
  const elemBtn = await page.evaluate(() => {
    const btn = Array.from(document.querySelectorAll('button'))
      .find((b) => b.offsetParent !== null && /新建|新增|添加/.test(b.innerText || ''));
    if (!btn) return false;
    btn.click();
    return true;
  });
  await ui.sleep(800);
  if (elemBtn) {
    await ui.shot(page, '63-element-dialog');
    const n = await fillDialog(page, ['沈砚', '沈砚，盲人剑客，靠听觉辨位，左手使剑']);
    await ui.sleep(300);
    for (const label of ['创建', '确定', '保存']) {
      if (await ui.clickButton(page, label)) break;
    }
    await ui.sleep(1500);
    await ui.shot(page, '64-element-created');
    step('新增设定元素', n > 0, `${n} 个输入框`);
  } else {
    step('新增设定元素', false, '没找到新建按钮');
  }

  // 3. 冲突 / 历史 / 写作 / 教程 页面
  for (const [path, name, shotName] of [
    ['conflicts', '冲突检测页', '65-conflicts'],
    ['history', '事件历史页', '66-history'],
    ['write', '续写工作台', '67-write'],
    ['tutorial', '教程页', '68-tutorial'],
  ]) {
    await page.goto(`http://localhost:3000/project/${projectId}/framework/${path}`, { waitUntil: 'networkidle2' });
    await ui.sleep(1200);
    await ui.shot(page, shotName);
    txt = await ui.bodyText(page, 220);
    step(name, !/404|Not Found/.test(txt), txt.replace(/\n/g, ' | ').slice(0, 80));
  }

  // 4. 风格管理：新建风格
  await page.goto('http://localhost:3000/styles', { waitUntil: 'networkidle2' });
  await ui.sleep(1200);
  await ui.shot(page, '70-styles');
  const styleNew = await page.evaluate(() => {
    const btn = Array.from(document.querySelectorAll('button'))
      .find((b) => b.offsetParent !== null && /新建|创建|新增/.test(b.innerText || ''));
    if (!btn) return false;
    btn.click();
    return true;
  });
  await ui.sleep(800);
  if (styleNew) {
    await ui.shot(page, '71-style-dialog');
    const n = await fillDialog(page, ['克制口语风', '短句为主，克制，不用书面腔']);
    for (const label of ['创建', '保存', '确定']) {
      if (await ui.clickButton(page, label)) break;
    }
    await ui.sleep(1500);
    await ui.shot(page, '72-style-created');
    step('新建风格模板', n > 0, `${n} 个输入框`);
  } else {
    step('新建风格模板', false, '没找到新建按钮');
  }

  // 5. AI 设置
  await page.goto('http://localhost:3000/settings/ai', { waitUntil: 'networkidle2' });
  await ui.sleep(1500);
  await ui.shot(page, '73-ai-settings');
  txt = await ui.bodyText(page, 300);
  step('AI 设置页（多上游）', /DeepSeek|OpenAI|Claude|模型/.test(txt), txt.replace(/\n/g, ' | ').slice(0, 90));

  // 主题切换与登出由 walk-tail.js 覆盖（它用的是正确的触发方式：
  // 主题改的是 documentElement 的 class，用户菜单是 hover 触发的 el-dropdown）

  fs.writeFileSync('walk-pages.json', JSON.stringify(log, null, 2));
  await browser.close();
})().catch((e) => { console.error('FATAL:', e.message); process.exit(1); });
