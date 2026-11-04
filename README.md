# AI Writer

全栈 AI 写作助手 —— 支持 Anthropic Claude / DeepSeek 双上游的多用户写作平台。

除了常规的 AI 写作辅助（大纲、续写、润色、扩写、缩写），AI Writer 提供两项差异化能力：

- **AI 风格学习**：上传小说 / 文本素材，让 AI 提炼其中的写作风格与文笔，生成可复用的风格模板，写作时一键注入。
- **小说写作框架引擎**：以事件流架构管理长篇创作的设定体系，支持设定版本化、上下文驱动续写、冲突自动检测与回退溯源。

## 功能特性

### MCP Server（把写作能力开放给外部 Agent）
- **端点**：`POST /mcp`，JSON-RPC 2.0（Streamable HTTP 的 JSON 响应模式），复用 JWT 鉴权
- **支持方法**：`initialize`、`notifications/initialized`、`tools/list`、`tools/call`、`ping`
- **五个只读工具**：`list_projects`、`get_framework`、`list_framework_elements`、`find_conflicts`、`rank_elements`（按相关性检索设定，复用 RAG）
- **为什么只做只读**：让外部 Agent 直接续写/改章节的风险大于收益；查设定、查冲突才是高频刚需
- **安全**：工具内部一律按当前用户做归属校验，拿到别人的 `frameworkId` 也读不到数据

客户端配置（Cursor / Claude Code 的 `mcp.json`）：

```json
{
  "mcpServers": {
    "ai-writer": {
      "type": "http",
      "url": "http://localhost:8080/mcp",
      "headers": { "Authorization": "Bearer <accessToken>" }
    }
  }
}
```

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
- **AI 辅助设定**：由 AI 生成设定元素，注入已有元素上下文避免自相矛盾
- **无限续写**：AI 基于「全部设定 + 当前章 + 前章 + 历史摘要」四层上下文持续写作，用户不喊停就不停
- **随时介入**：写作过程中可插入内容或指令，AI 以最新上下文继续
- **冲突检测**：写后由 AI 对照最新设定做一致性审查，大冲突挂起待处理、小冲突自动打补丁，冲突可忽略 / 打补丁 / 重写章节
- **事件流溯源**：所有创作操作记为不可变事件，支持时间轴回溯与任意时间点的框架状态重建

### AI 工程化（韧性 / 可观测 / 提示词版本）
- **调用韧性**：`AiCallGuard` 给所有上游调用统一加超时（默认 90s）、重试（默认 2 次，**仅在流式首个 token 之前**）、熔断（连续 5 次失败后冷却 30s，快速失败不再打上游）
- **可观测性**：Micrometer + Actuator，暴露 `ai_calls_total{provider,outcome}` 与 `ai_call_duration_seconds{provider}`；`/actuator/health` 与 `/actuator/prometheus` 放行（探活与抓取都不便携带 JWT），其余 actuator 端点仍需鉴权。**生产环境请把 `/actuator/prometheus` 限制在内网或独立管理端口**
- **提示词版本化**：system prompt 收拢到 `PromptRegistry`（id + version + 正文），改提示词必须升版本，便于日志对齐与 A/B
- **模型输出守卫**：`JsonGuard` 统一处理模型返回的 JSON（剥代码围栏、定位数组、解析失败即降级），冲突检测不再依赖"模型一定听话"
- **Token 记账**：解析上游 `usage`（Anthropic 在 `message_start`/`message_delta`，OpenAI 兼容需 `stream_options.include_usage`），记 `ai_tokens_total{provider,type}` 与按单价折算的 `ai_cost_micros_total`；拿不到 usage 时按字符折算并单独记 `ai_tokens_estimated_total`，避免估算值冒充真实值
- **回放式评测集**：`src/test/resources/eval/*.json` 固化真实踩过的脏输出（围栏、前后解释、编造 id、超数量、空描述），`ConflictEvalTest` 每次 CI 跑一遍——改提示词或解析逻辑导致命中数下降会直接挂掉

### 设定检索（RAG）
- **问题**：框架模式下设定可能有几十上百条，全量塞进 prompt 会超预算；原来的兜底是**按顺序截断**——被截掉的常常正是本章要用的那条。
- **做法**：超预算时按"当前正文 ↔ 设定元素"的相似度取 top-K（默认 12），先把最相关的保住。
- **实现**：`EmbeddingModel` 抽象两个实现——本地 `HashingEmbeddingModel`（字符 n-gram 哈希，零依赖、确定性、CI 可跑）与 `OpenAiEmbeddingModel`（走 `/embeddings`，配 `ai.embedding.provider=openai` 才启用）；`ElementRanker` 负责余弦排序，**向量化失败降级为原顺序**。
- **边界**：不做向量库存索引，每次请求现算（元素百级足够）；本地向量只认字面相似，语义匹配要换真 embedding。

## 技术栈

| 层次 | 技术 |
|------|------|
| 前端 | Vue 3.5 · Vite 6 · TypeScript · Element Plus 2 · Tiptap 2 · Pinia · Vue Router 4 |
| 后端 | Java 17 · Spring Boot 3.5 · Spring Security · Spring Data JPA · WebClient |
| 数据库 | MySQL 8（JPA `ddl-auto: update` 自动建表） |
| 缓存 / 会话 | Redis 7（refresh token 白名单与轮换、接口限流） |
| 可观测 | Micrometer + Spring Boot Actuator（指标暴露 Prometheus 格式） |
| AI | Anthropic Messages API / DeepSeek OpenAI 兼容协议（WebClient 裸调，SSE 流式），可由 `AI_PROVIDER` 切换 |
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
│   │   ├── ai/                  # AiProvider 及实现（ClaudeService / DeepSeekService）、PromptBuilder、StyleAnalyzer
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

**完整清单见 [`.env.example`](.env.example)**：里面列了所有变量（含可选调参与默认值），只放键名不放值。
复制成 `.env` 或直接 `export` / `set` 后填；`.env`、`.env.bat` 和 `application-local.yml` 都已被 `.gitignore` 忽略。

| 变量 | 必须 | 默认值 | 说明 |
|------|------|--------|------|
| `AI_PROVIDER` | 否 | `anthropic` | AI 上游选择：`anthropic` 或 `deepseek` |
| `ANTHROPIC_API_KEY` | 视情况 | 无 | Claude API 密钥，[获取地址](https://console.anthropic.com)。`AI_PROVIDER=anthropic`（默认）时必填 |
| `DEEPSEEK_API_KEY` | 视情况 | 无 | DeepSeek API 密钥，[获取地址](https://platform.deepseek.com/api_keys)。`AI_PROVIDER=deepseek` 时必填 |
| `JWT_SECRET` | 是 | 无 | JWT 签名密钥，256 位以上随机串，**未设置则启动失败** |
| `MYSQL_PASSWORD` | 是 | 无 | 数据库密码 |
| `MYSQL_USER` | 否 | `root` | 数据库用户名 |
| `REDIS_HOST` / `REDIS_PORT` | 否 | `localhost` / `6379` | Redis 地址（refresh 白名单、限流） |
| `REDIS_PASSWORD` | 否 | 无 | Redis 密码 |
| `RATE_LIMIT_ENABLED` / `RATE_LIMIT_RPM` | 否 | `true` / `60` | 接口限流开关与每用户每分钟配额 |
| `AI_TIMEOUT_SECONDS` / `AI_MAX_RETRIES` | 否 | `90` / `2` | AI 上游超时与重试次数 |
| `AI_RAG_TOP_K` | 否 | `12` | 设定超预算时保留的相关条目数 |
| `AI_EMBEDDING_PROVIDER` | 否 | `hashing` | `hashing`（本地离线）或 `openai`（走 /embeddings） |
| `AI_EMBEDDING_BASE_URL` / `_API_KEY` / `_MODEL` | 否 | 硅基流动 / 空 / `BAAI/bge-m3` | 仅在 provider=openai 时生效 |
| `LOG_PATH` | 否 | `./logs` | 日志输出目录 |

生成密钥：

```bash
openssl rand -base64 32
```

### 2.1 选择 AI 上游

项目同时支持两家 provider，通过 `AI_PROVIDER` 切换，二者协议不同但上层接口一致：

| provider | 协议 | 端点 | 鉴权 | 默认模型 |
|---|---|---|---|---|
| `anthropic`（默认） | Anthropic Messages API | `/v1/messages` | `x-api-key` | `claude-sonnet-4-6` |
| `deepseek` | OpenAI 兼容协议 | `/chat/completions` | `Bearer` | `deepseek-flash` |

不设置 `AI_PROVIDER` 时行为与改造前完全一致（走 Anthropic）。

用 DeepSeek：

```bash
# Linux / macOS
AI_PROVIDER=deepseek DEEPSEEK_API_KEY=sk-xxx JWT_SECRET=xxx MYSQL_PASSWORD=xxx mvn spring-boot:run
# Windows PowerShell
$env:AI_PROVIDER="deepseek"; $env:DEEPSEEK_API_KEY="sk-xxx"; $env:JWT_SECRET="xxx"; $env:MYSQL_PASSWORD="xxx"; mvn spring-boot:run
```

模型与端点可在 `application.yml` 的 `deepseek.*` / `anthropic.*` 段调整（也可用同名环境变量覆盖）。

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

如果后端跑在别的端口（例如为了验证临时起在 8093），无需另建配置文件，用环境变量覆盖代理目标即可：

```bash
# Linux / macOS
VITE_API_TARGET=http://127.0.0.1:8093 npm run dev
# Windows PowerShell
$env:VITE_API_TARGET="http://127.0.0.1:8093"; npm run dev
```

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
| POST | `/refresh` | 刷新 accessToken（旧 refresh 立即作废，即轮换） |
| POST | `/logout` | 登出（作废当前 refresh 的 jti，之后无法再刷新） |

### 项目 `/api/projects`
| 方法 | 路径 | 说明 |
|------|------|------|
| GET / POST | `/` | 列表 / 创建 |
| PUT / DELETE | `/{id}` | 更新 / 删除 |

### 章节 `/api`
| 方法 | 路径 | 说明 |
|------|------|------|
| GET / POST | `/projects/{projectId}/docs` | 章节列表 / 新建 |
| GET / PUT / DELETE | `/docs/{id}` | 读取 / 保存 / 删除（**PUT 带乐观锁：并发冲突返回 409**） |

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
- **refresh token 走 Redis 白名单**：签发登记 jti、刷新时轮换、登出即作废，可主动强制下线
- **接口限流**：每用户（未登录按 IP）每分钟 60 次，超出返回 429；Redis 故障时降级放行，不阻断主流程
- 资源访问做 ownership 校验，越权返回 403
- **并发写保护**：`Document` 用 `@Version` 乐观锁。两个会话同时改同一章时，后提交者收到 `409 CONCURRENT_MODIFICATION`，前端提示并拉回最新版本——不会静默覆盖别人的修改
- 输入长度上限：正文 20 万字、AI 单次输入 5 万字、学习素材 100 万字，超出返回 400（避免一条请求把超大文本塞进数据库和 prompt）

已知风险（详见技术债务）：Token 存于 localStorage 存在 XSS 窃取风险。~~refreshToken 暂无轮换~~ 已改为 Redis 白名单 + 轮换；~~缺少请求速率限制~~ 已加按用户限流。

## 版本

当前 `0.1.0`（`backend/pom.xml`、`frontend/package.json`）。
