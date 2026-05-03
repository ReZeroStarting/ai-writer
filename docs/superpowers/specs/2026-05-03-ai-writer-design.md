# AI Writer — 全栈 AI 写作助手设计文档

## 概述

AI Writer 是一个多用户 SaaS 写作平台，集成 Anthropic Claude 提供 AI 写作辅助。核心差异化功能是"AI 风格学习"——用户可以上传小说或文本，让 AI 提炼其中的写作风格和文笔，生成可选 AI 助手。

## 技术栈

| 层 | 技术 |
|---|---|
| 前端 | Vue 3 + Vite + Element Plus + Tiptap + Pinia |
| 后端 | Spring Boot 3 + Spring AI + Spring Security + Spring Data JPA |
| 数据库 | MySQL |
| AI | Anthropic Claude (通过 Spring AI 集成) |
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
后端 (Spring Boot 3 + Spring AI)
  │ JDBC / JPA
MySQL (users, projects, documents, style_templates, source_materials, learning_jobs)
```

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

- 前端：Axios 统一拦截（401 → 刷新 token，403/429/500 → Toast）
- AI 流式中断：保留已生成内容
- 编辑器保护：30 秒自动保存草稿到 localStorage
- 后端：@ControllerAdvice 统一异常处理
- 风格学习失败：learning_job status = FAILED，记录 error_msg

## 测试策略

- 后端：JUnit + Mockito 单元测试，MockMvc 集成测试，AI 层用 mock 响应
- 前端：Vitest 组件/Store 测试，MSW 模拟 API，Playwright E2E 覆盖核心路径

## 路由设计

| 路由 | 组件 | 说明 |
|---|---|---|
| `/login` | LoginView | 登录页 |
| `/register` | RegisterView | 注册页 |
| `/` | DashboardLayout | 主入口 |
| `/project/:id` | WorkspaceView | 工作区 |
| `/project/:id/doc/:docId` | WorkspaceView | 编辑文档 |
| `/styles` | StyleManageView | 风格管理 |
