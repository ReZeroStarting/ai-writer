# AI Writer — 全栈 AI 写作助手设计文档

## 概述

AI Writer 是一个多用户 SaaS 写作平台，集成 Anthropic Claude 提供 AI 写作辅助。核心差异化功能是"AI 风格学习"——用户可以上传小说或文本，让 AI 提炼其中的写作风格和文笔，生成可选 AI 助手。

## 技术栈

| 层 | 技术 |
|---|---|
| 前端 | Vue 3 + Vite + Element Plus + Tiptap + Pinia |
| 后端 | Spring Boot 3.5 + WebClient + Spring Security + Spring Data JPA |
| 数据库 | MySQL |
| AI | Anthropic Claude (WebClient 裸调 Messages API，SSE 流式) |
| 日志 | SLF4J + Logback，AOP 请求切面 |
| 项目结构 | Monorepo 单仓库 |

## 核心功能

### MVP 范围

1. **用户认证** — 邮箱 + 手机号 + OAuth 第三方登录，JWT 双 token
2. **项目管理** — 项目/工作区模式组织文档，每个项目下多篇文档
3. **富文本编辑** — Tiptap 所见即所得编辑器
4. **AI 写作辅助** — 大纲生成、内容续写、改写润色、扩写缩写
5. **AI 风格学习** — 上传文本素材 → Claude 分析提取 → 生成风格模板 → 写作时可选

### 风格学习流程

```
用户上传素材 → 创建 learning_job → Claude 分析文本提取特征
→ 生成结构化 system_prompt → 存入 style_templates
→ 用户在写作时选择风格 → system_prompt 注入 AI 请求
```

实现方式：System Prompt 注入模式。架构预留向量检索扩展点（方案 B）。

## 架构

```
前端 (Vue 3 + Element Plus + Tiptap)
  │ HTTP REST + SSE 流式
后端 (Spring Boot 3.5 + WebClient + Spring Security + JPA)
  │ JDBC / JPA
MySQL (users, projects, documents, style_templates, source_materials, learning_jobs)
```

### 环境变量

| 变量 | 必须 | 说明 |
|------|------|------|
| `ANTHROPIC_API_KEY` | 是 | Claude API 密钥 |
| `JWT_SECRET` | 是 | JWT 签名密钥，**无默认值，不设启动失败** |
| `MYSQL_PASSWORD` | 是 | 数据库密码 |
| `MYSQL_USER` | 否 | 默认 `root` |
| `LOG_PATH` | 否 | 默认 `./logs` |

### 前端组件树

- **LoginView** — 登录（LoginForm + OAuthButtons）
- **RegisterView** — 注册
- **DashboardLayout** — 主布局（AppHeader + ProjectSidebar + router-view）
  - **WorkspaceView** — 工作区（DocList + DocEditor）
    - DocEditor — TiptapEditor + AIPanel
      - AIPanel — StyleSelector + OutlineGenerator + ContinueWriter + PolishRewriter + ExpandShorten
  - **StyleManageView** — 风格管理（StyleList + StyleCreateDialog + StyleLearnDialog）

### 后端分层

- **Controller 层** — REST API 端点
- **Service 层** — 业务逻辑、风格学习编排
- **AI 层** — Spring AI + Claude，Prompt 构建与流式返回
- **Repository 层** — Spring Data JPA

## 数据模型

### 表结构

**users**
- id, username, email, phone, password_hash
- oauth_provider, oauth_open_id
- created_at, updated_at

**projects**
- id, user_id (FK), name, description, created_at, updated_at

**documents**
- id, project_id (FK), user_id (FK), title, content (TEXT), created_at, updated_at

**style_templates**
- id, user_id (FK), name, system_prompt (TEXT), description, created_at

**source_materials**
- id, user_id (FK), template_id (FK), title, content (LONGTEXT), created_at

**learning_jobs**
- id, user_id (FK), template_id (FK), material_id (FK)
- status (PENDING/PROCESSING/COMPLETED/FAILED)
- result_prompt (TEXT), error_msg, created_at, updated_at

## API 设计

### 认证
- `POST /api/auth/register` — 注册
- `POST /api/auth/login` — 登录
- `POST /api/auth/oauth/{provider}` — OAuth 回调

### 项目
- `GET /api/projects` — 列表
- `POST /api/projects` — 创建
- `PUT /api/projects/{id}` — 更新
- `DELETE /api/projects/{id}` — 删除

### 文档
- `GET /api/projects/{id}/docs` — 列表
- `POST /api/projects/{id}/docs` — 创建
- `GET /api/docs/{id}` — 获取内容
- `PUT /api/docs/{id}` — 保存
- `DELETE /api/docs/{id}` — 删除

### AI 写作（SSE 流式）
- `POST /api/ai/generate-outline` — 生成大纲
- `POST /api/ai/continue` — 续写
- `POST /api/ai/polish` — 润色
- `POST /api/ai/expand` — 扩写
- `POST /api/ai/shorten` — 缩写

请求体：`{ content, style_template_id?, instruction? }`

### 风格模板
- `GET /api/styles` — 列表
- `POST /api/styles` — 创建
- `GET /api/styles/{id}` — 详情
- `PUT /api/styles/{id}` — 编辑
- `DELETE /api/styles/{id}` — 删除

### 风格学习
- `POST /api/styles/{id}/learn` — 上传素材触发学习
- `GET /api/styles/{id}/jobs` — 学习任务列表
- `GET /api/jobs/{id}` — 任务状态

## 认证流程

- 注册：邮箱 + 密码 + 手机号（可选），bcrypt 加密
- 登录：邮箱/手机号 + 密码，或 OAuth 回调
- JWT：access_token 30 分钟，refresh_token 7 天
- 前端：Pinia 存储 token，Axios 拦截器自动附加，Router 守卫

## 错误处理

### HTTP 状态码规范

| 状态码 | 异常类型 | 场景 |
|--------|------|------|
| 200 | — | 成功 |
| 400 | `IllegalArgumentException` | 参数校验失败 |
| 401 | `AuthException` | 未登录 / Token 无效 / 密码错误 |
| 403 | `ForbiddenException` | 无权操作他人资源 |
| 404 | `NotFoundException` | 资源不存在 |
| 500 | `RuntimeException` | 服务器内部错误（日志记录堆栈） |

- 前端：Axios 统一拦截（401 → 刷新 token，有防无限循环保护；其他 → Toast）
- AI 流式中断：保留已生成内容
- 编辑器保护：3 秒自动保存到后端
- 风格学习失败：learning_job status = FAILED，记录 error_msg

## 日志

- 框架：SLF4J + Logback（`logback-spring.xml`）
- 请求日志：AOP 切面自动记录所有 Controller 请求（IP、方法、URI、耗时）
- 业务日志：AuthService（登录/注册）、AiService（5 种 AI 操作）
- 存储：控制台 + `./logs/ai-writer.log`（保留 30 天）+ `./logs/ai-writer-error.log`（保留 60 天）

## 测试策略

- 后端：JUnit + Mockito 单元测试，MockMvc 集成测试，AI 层用 mock 响应 **（待实施）**
- 前端：Vitest 组件/Store 测试，MSW 模拟 API，Playwright E2E 覆盖核心路径 **（待实施）**

## 路由设计

| 路由 | 组件 | 说明 |
|---|---|---|
| `/login` | LoginView | 登录页 |
| `/register` | RegisterView | 注册页 |
| `/` | DashboardLayout | 主入口 |
| `/project/:id` | WorkspaceView | 工作区 |
| `/project/:id/doc/:docId` | WorkspaceView | 编辑文档 |
| `/styles` | StyleManageView | 风格管理 |

## 版本记录

| 日期 | 版本 | 变更 |
|------|------|------|
| 2026-05-04 | 0.1.2 | 测试：JWT/认证/文档/项目 4 个 Service 共 32 个单元测试，CI/CD GitHub Actions |
| 2026-05-04 | 0.1.1 | 安全加固：JWT 密钥环境变量化、异常分类处理（401/403/404）、日志体系建立、前端 401 循环修复 |
| 2026-05-03 | 0.1.0 | 项目初始化，MVP 功能完成 |

## 相关文档

- [运维文档](../operations.md) — 部署、环境变量、日志、架构决策记录
- [技术债务](../tech-debt.md) — 已知问题和修复计划
- [ADR-001: WebClient vs Spring AI](../adr/001-webclient-vs-spring-ai.md)
