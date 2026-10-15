# AI Writer

全栈 AI 写作助手 —— 集成 Anthropic Claude 的多用户写作平台。

除了常规的 AI 写作辅助（大纲、续写、润色、扩写、缩写），AI Writer 提供两项差异化能力：

- **AI 风格学习**：上传小说 / 文本素材，让 Claude 提炼其中的写作风格与文笔，生成可复用的风格模板，写作时一键注入。
- **小说写作框架引擎**：以事件流架构管理长篇创作的设定体系，支持设定版本化、上下文驱动续写、冲突自动检测与回退溯源。

## 功能特性

### 基础写作
- 用户认证：邮箱 / 用户名注册登录，JWT 双 Token（access 30 分钟 + refresh 7 天）自动续期
- 项目管理：以项目（小说）为单位组织章节，侧边栏搜索定位
- 富文本编辑：Tiptap 所见即所得编辑器，3 秒自动保存，切换章节自动落盘
- AI 写作操作：生成大纲 / 续写 / 润色 / 扩写 / 缩写 / 自动写作 / 模仿写作，均为 SSE 流式返回
- 中文写作质量分析：实时检测 AI 味、重复用词等问题
- 风格管理：手动创建或上传素材学习（支持 `.txt` / `.md` / `.epub`，自动采样大文件），学习任务异步执行并可查询状态
- 主题切换：浅色 / 深色 / 护眼三种模式，记忆到 localStorage

### 小说写作框架引擎
- **框架模式**：项目可升级为框架模式（不可逆），统一管理角色、地点、物品、组织、事件、世界观等设定元素，支持自定义类型
- **版本化元素**：修改即追加新版本而非覆盖，可查看完整版本历史，章节记录生成时引用的版本快照
- **AI 辅助设定**：由 Claude 生成设定元素，注入已有元素上下文避免自相矛盾
- **无限续写**：AI 基于「全部设定 + 当前章 + 前章 + 历史摘要」四层上下文持续写作，用户不喊停就不停
- **随时介入**：写作过程中可插入内容或指令，AI 以最新上下文继续
- **冲突检测**：写后由 Claude 对照最新设定做一致性审查，大冲突挂起待处理、小冲突自动打补丁，冲突可忽略 / 打补丁 / 重写章节
- **事件流溯源**：所有创作操作记为不可变事件，支持时间轴回溯与任意时间点的框架状态重建

## 技术栈

| 层次 | 技术 |
|------|------|
| 前端 | Vue 3.5 · Vite 6 · TypeScript · Element Plus 2 · Tiptap 2 · Pinia · Vue Router 4 |
| 后端 | Java 17 · Spring Boot 3.5 · Spring Security · Spring Data JPA · WebClient |
| 数据库 | MySQL 8（JPA `ddl-auto: update` 自动建表） |
| AI | Anthropic Claude Messages API（WebClient 裸调，SSE 流式） |
| 鉴权 | JWT（jjwt 0.12.6）+ BCrypt |
| 日志 | SLF4J + Logback + AOP 请求切面 |
| 测试 | JUnit 5 + Mockito（后端）/ Vitest + @vue/test-utils（前端） |
| CI | GitHub Actions |

> 为什么用 WebClient 裸调而不用 Spring AI？见 [`docs/adr/001-webclient-vs-spring-ai.md`](docs/adr/001-webclient-vs-spring-ai.md)。

## 项目结构

```
ai-writer/
├── backend/                     # Spring Boot 后端
│   ├── src/main/java/com/aiwriter/
│   │   ├── config/              # SecurityConfig 等
│   │   ├── controller/          # REST 端点（auth / project / document / ai / style / framework ...）
│   │   ├── service/             # 业务逻辑（含 ContextBuilder / NovelWriteService / ConflictService）
│   │   ├── ai/                  # ClaudeService、PromptBuilder、StyleAnalyzer
│   │   ├── model/               # JPA 实体
│   │   ├── repository/          # Spring Data JPA
│   │   ├── security/            # JWT Filter / Provider
│   │   ├── dto/ · exception/ · logging/
│   │   └── AiWriterApplication.java
│   ├── src/main/resources/      # application.yml · schema.sql · logback-spring.xml
│   └── src/test/                # 84 个单元测试
├── frontend/                    # Vue 3 前端
│   └── src/
│       ├── api/                 # axios 封装（含 401 自动刷新队列）
│       ├── components/          # auth / editor / layout / styles
│       ├── views/               # 页面（工作区、框架、元素、写作、冲突、历史、风格管理 ...）
│       ├── stores/ · router/ · types/ · utils/ · styles/
│       └── __tests__/           # 24 个 Vitest 用例
├── docs/                        # 设计规格、计划、ADR、运维手册、框架教程
└── .github/workflows/ci.yml     # CI 流水线
```

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- Node.js 20+
- MySQL 8（本机 3306）

### 1. 准备数据库

```sql
CREATE DATABASE ai_writer DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

表结构由 JPA `ddl-auto: update` 在启动时自动创建；`backend/src/main/resources/schema.sql` 为等价的手动初始化脚本，供需要时参考。

### 2. 配置环境变量

| 变量 | 必须 | 默认值 | 说明 |
|------|------|--------|------|
| `ANTHROPIC_API_KEY` | 是 | 无 | Claude API 密钥，[获取地址](https://console.anthropic.com) |
| `JWT_SECRET` | 是 | 无 | JWT 签名密钥，256 位以上随机串，**未设置则启动失败** |
| `MYSQL_PASSWORD` | 是 | 无 | 数据库密码 |
| `MYSQL_USER` | 否 | `root` | 数据库用户名 |
| `LOG_PATH` | 否 | `./logs` | 日志输出目录 |

生成密钥：

```bash
openssl rand -base64 32
```

### 3. 启动后端

```bash
cd backend
# Linux / macOS
ANTHROPIC_API_KEY=sk-xxx JWT_SECRET=xxx MYSQL_PASSWORD=xxx mvn spring-boot:run
# Windows PowerShell
$env:ANTHROPIC_API_KEY="sk-xxx"; $env:JWT_SECRET="xxx"; $env:MYSQL_PASSWORD="xxx"; mvn spring-boot:run
```

服务监听 `http://localhost:8080`。

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

打开 `http://localhost:3000`。开发服务器已把 `/api` 代理到 `localhost:8080`，前端无需额外配置跨域。

### 常用命令

| 位置 | 命令 | 作用 |
|------|------|------|
| backend | `mvn spring-boot:run` | 启动后端 |
| backend | `mvn test` | 运行单元测试 |
| backend | `mvn package` | 打包可执行 jar |
| frontend | `npm run dev` | 启动开发服务器 |
| frontend | `npm run build` | 类型检查 + 生产构建 |
| frontend | `npm run test` | 运行 Vitest |
| frontend | `npm run preview` | 预览构建产物 |

## API 概览

所有业务端点均需在请求头携带 `Authorization: Bearer <accessToken>`。

### 认证 `/api/auth`
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/register` | 注册 |
| POST | `/login` | 登录 |
| POST | `/refresh` | 刷新 accessToken |

### 项目 `/api/projects`
| 方法 | 路径 | 说明 |
|------|------|------|
| GET / POST | `/` | 列表 / 创建 |
| PUT / DELETE | `/{id}` | 更新 / 删除 |

### 章节 `/api`
| 方法 | 路径 | 说明 |
|------|------|------|
| GET / POST | `/projects/{projectId}/docs` | 章节列表 / 新建 |
| GET / PUT / DELETE | `/docs/{id}` | 读取 / 保存 / 删除 |

### AI 写作 `/api/ai`（全部 SSE 流式）
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/generate-outline` | 生成大纲 |
| POST | `/continue` | 续写 |
| POST | `/polish` | 润色 |
| POST | `/expand` · `/shorten` | 扩写 · 缩写 |
| POST | `/auto-write` · `/imitate-write` | 自动写作 · 模仿写作 |

### 风格与学习 `/api`
| 方法 | 路径 | 说明 |
|------|------|------|
| GET / POST | `/styles` | 风格模板列表 / 创建 |
| GET / PUT / DELETE | `/styles/{id}` | 详情 / 更新 / 删除 |
| POST | `/styles/{id}/learn` | 上传素材触发学习 |
| GET | `/styles/{id}/jobs` · `/jobs/{id}` | 学习任务列表 / 状态 |

### 小说写作框架 `/api/v1`
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/projects/{projectId}/framework` | 开启框架模式 |
| GET | `/projects/{projectId}/framework` · `/frameworks/{id}` | 获取框架 |
| POST | `/frameworks/{id}/finish` | 完结框架 |
| GET / POST | `/frameworks/{frameworkId}/elements` | 元素列表 / 新增 |
| PUT / DELETE | `/frameworks/{fid}/elements/{eid}` | 更新（追加版本）/ 弃用 |
| GET | `/frameworks/{fid}/elements/{eid}/versions` | 版本历史 |
| POST | `/frameworks/{fid}/elements/ai-generate` | AI 生成元素 |
| POST | `/frameworks/{fid}/write/start` · `/continue` | 开始 / 继续写作（SSE） |
| POST | `/frameworks/{fid}/write/interrupt` · `/save` | 介入插入 / 保存章节 |
| GET | `/frameworks/{fid}/write/latest-chapter` | 最新章节 |
| GET | `/frameworks/{fid}/conflicts` | 冲突列表 |
| POST | `/frameworks/{fid}/conflicts/{cid}/resolve` | 处理冲突 |
| POST | `/frameworks/{fid}/elements/{eid}/impact` | 查询元素改动影响的章节 |
| GET | `/frameworks/{fid}/events` · `/events/{timestamp}` | 事件流 / 回溯到指定时刻 |

## 测试

```bash
cd backend && mvn test          # 84 个单元测试：JWT、Auth、Project、Document、Element、Context、Conflict、Event、Chapter、Framework
cd frontend && npm run test     # 24 个 Vitest 用例：framework store、theme、写作质量检测
```

CI（`.github/workflows/ci.yml`）在 push / PR 到 `main` / `master` 时自动执行后端 `mvn test`、前端 `npm ci` + 类型检查。

## 文档

| 文档 | 内容 |
|------|------|
| [`docs/operations.md`](docs/operations.md) | 环境变量、日志、状态码规范、架构决策、启动命令 |
| [`docs/tech-debt.md`](docs/tech-debt.md) | 已知技术债务与修复计划 |
| [`docs/framework-tutorial.mdx`](docs/framework-tutorial.mdx) | 小说写作框架使用教程（应用内「教程」页同源） |
| [`docs/superpowers/specs/`](docs/superpowers/specs/) | 平台初版、UI 增强、写作引擎三份设计规格 |
| [`docs/superpowers/plans/`](docs/superpowers/plans/) | 对应的实施计划 |
| [`docs/adr/`](docs/adr/) | 架构决策记录 |

## 安全说明

- JWT 密钥与 AI API Key 全部走环境变量，仓库内无任何硬编码密钥
- 密码使用 BCrypt 存储；Token 有效期短，前端拦截器自动刷新
- 资源访问做 ownership 校验，越权返回 403

已知风险（详见技术债务）：Token 存于 localStorage 存在 XSS 窃取风险、refreshToken 暂无轮换、缺少请求速率限制。

## 版本

当前 `0.1.0`（`backend/pom.xml`、`frontend/package.json`）。
