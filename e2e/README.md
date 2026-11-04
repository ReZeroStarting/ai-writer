# E2E 走查脚本（Playwright 风格，基于 puppeteer-core 驱动本机 Chrome）

这些脚本用来**真人在场时的端到端验证**：打开真实浏览器窗口，按用户路径点一遍，
每步截图留证。它们不进 CI（CI 里没有浏览器和真实 LLM Key），只作为本地验收工具。

## 前置

```bash
# 1) 起依赖（MySQL + Redis）
docker compose up -d
#    没有 Docker 的话：本机 MySQL 3306 + Redis 6379 即可

# 2) 起后端
cd backend
JWT_SECRET=<32字节以上> AI_PROVIDER=deepseek DEEPSEEK_API_KEY=<key> \
  mvn spring-boot:run -Dspring-boot.run.profiles=local

# 3) 起前端
cd frontend && npm run dev
```

## 运行

```bash
cd e2e
npm install          # 只装 puppeteer-core（不下载浏览器，用本机 Chrome）
node walk-full.js    # 注册 → 建小说 → 建章节 → 输入 → 自动保存 → AI 续写
node walk-pages.js   # 框架概览 / 设定元素 / 冲突 / 历史 / 写作台 / 教程 / 风格 / 设置
node walk-tail.js    # 新建风格 / 主题切换 / 登出
node mcp-check.js    # 直接调 MCP：initialize / tools/list / tools/call / 鉴权
```

截图默认写到 `e2e/shots/`。Chrome 路径在 `ui.js` 的 `CHROME` 常量里改。

## 已验证过的结果（2026-11-04）

| 脚本 | 结果 |
|---|---|
| `walk-full.js` | 注册/小说/章节/自动保存(PUT /api/docs)/AI 流式续写 全通 |
| `walk-pages.js` | 框架概览、设定元素（新增"沈砚"成功）、冲突/历史/写作台/教程、风格、AI 设置 全通 |
| `walk-tail.js` | 新建风格、主题 light→dark→eye-care、登出 全通 |
| `mcp-check.js` | initialize 200、5 个工具、未认证 401、未知工具 -32602 |
| `conflict-e2e.ps1` | 生成中改设定 → 产出 `[MINOR] 元素 [沈砚] 在章节生成后被修改（v1 → v2）` |
| `style-learn-e2e.ps1` | 提交素材 → 任务 COMPLETED → 自动生成 442 字风格指令并写回模板 |

## 已知限制

- 脚本依赖页面文本匹配，前端改文案会失效——它的定位是"人工验收助手"，不是稳定回归套件。
- **AI 生成设定元素**（`/elements/ai-generate`）目前没有端到端脚本覆盖。
- 冲突检测的**语义审校**（`aiCheckAgainstElements`）依赖模型判断，结果不稳定；`conflict-e2e.ps1` 验证的是**确定性的版本比对**那条路径。

