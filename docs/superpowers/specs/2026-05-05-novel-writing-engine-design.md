# 小说写作框架引擎 — 设计规格

**日期:** 2026-05-05
**状态:** 待实现
**方案:** C — 上下文驱动的事件流架构

---

## 概述

在现有 AI Writer 平台上新增**小说写作框架引擎**。采用事件流架构，将一切创作操作建模为不可变事件，支持设定版本化、影响追踪、历史回溯和分支探索。

### 核心能力

1. **框架体系** — Project 升级为框架模式，管理设定/角色/地点/物品/组织/事件等元素
2. **版本化元素** — 每次修改追加版本不覆盖，章节记录生成时引用的版本快照
3. **AI 辅助设定** — AI 可生成元素，与已有元素做冲突检查后入库
4. **无限续写** — AI 基于框架设定持续写作，用户不喊停就不停
5. **随时介入** — 写作中随时插入内容/指令/新设定，AI 以最新上下文续写
6. **冲突检测** — 写前注入设定 + 写后程序/AI 双重检测，大冲突暂停、小冲突自动打补丁
7. **事件流溯源** — 一切操作不可变日志，支持回溯到任意时间点的框架状态

---

## 与现有模型的关系

- `Project` 新增 `mode` 字段（null = 普通模式 / `"framework"` = 框架模式），开启框架模式后不可逆
- 现有 `Document` 映射为框架章节，存储在同一个 Project 下
- `StyleTemplate` 继续用于风格管理，框架写作时可选引用
- 新增实体均通过 FK 关联 Project，不侵入现有模型

---

## 一、数据模型

### 1.1 Framework（框架）

1:1 绑定 Project（开启框架模式时创建）。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | PK |
| projectId | Long | FK -> Project, unique |
| status | Enum | ACTIVE / FINISHED |
| createdAt/updatedAt | DateTime | 审计字段 |

### 1.2 FrameworkElement（元素，版本化）

联合主键 `(id, version)`，修改即追加新行。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | UUID | 元素逻辑 ID，同元素各版本相同 |
| version | int | 版本号，从 1 起递增 |
| frameworkId | Long | FK -> Framework |
| type | Enum/String | CHARACTER / LOCATION / ITEM / ORGANIZATION / EVENT / WORLD_SETTING / 或用户自定义字符串 |
| name | String(200) | 元素名称 |
| content | Text | 元素详细内容 |
| isDeprecated | boolean | 软删除标记 |
| createdAt | DateTime | 创建时间 |

### 1.3 Chapter（章节）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | PK |
| frameworkId | Long | FK -> Framework |
| documentId | Long | FK -> Document |
| chapterIndex | int | 章节序号 |
| elementSnapshot | JSON | 生成时引用的元素版本快照，格式: `{"<elementId>": <version>, ...}` |
| status | Enum | DRAFT / AI_GENERATING / AI_GENERATED / USER_EDITING / COMPLETED |
| createdAt/updatedAt | DateTime | 审计字段 |

### 1.4 WritingEvent（事件流）

不可变日志，只追加不修改。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | PK (自增) |
| frameworkId | Long | FK -> Framework |
| eventType | Enum | 见下方事件类型表 |
| payload | JSON | 事件数据（结构随类型而异） |
| timestamp | DateTime | 事件发生时间（精确到毫秒） |

**事件类型枚举：**

| 类型 | 携带数据 |
|------|---------|
| ELEMENT_CREATED | { elementId, version, type, name, content } |
| ELEMENT_UPDATED | { elementId, oldVersion, newVersion, diff } |
| ELEMENT_DEPRECATED | { elementId, version } |
| ELEMENT_AI_GENERATED | { elements: [{ elementId, version, type, ... }] } |
| CHAPTER_STARTED | { chapterId, chapterIndex, elementSnapshot } |
| CHAPTER_AI_GENERATED | { chapterId, content_hash } |
| USER_INTERVENED | { chapterId, insertedContent, position } |
| CONFLICT_DETECTED | { conflictId, severity, elementId, chapterRefVersion, currentVersion } |
| CONFLICT_RESOLVED | { conflictId, resolution: IGNORE/PATCH/REWRITE } |
| PATCH_APPLIED | { conflictId, patchContent, affectedChapterIds } |
| FRAMEWORK_FINISHED | {} |

### 1.5 Conflict（冲突记录）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | PK |
| frameworkId | Long | FK -> Framework |
| elementId | UUID | 相关元素 |
| chapterId | Long | 相关章节 |
| severity | Enum | MAJOR / MINOR |
| description | Text | AI 生成的冲突描述 |
| chapterRefVersion | int | 章节生成时引用的版本 |
| currentVersion | int | 当前最新版本 |
| status | Enum | PENDING / IGNORED / PATCHED / REWRITTEN |
| createdAt/updatedAt | DateTime | 审计字段 |

### 1.6 Patch（补丁）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | PK |
| conflictId | Long | FK -> Conflict |
| content | Text | 补丁说明或修正文本 |
| affectedChapterIds | JSON | 受影响的章节 ID 列表 |
| createdAt | DateTime | 创建时间 |

---

## 二、后端 Service 设计

### 2.1 FrameworkService

```
Framework openFramework(projectId)                    -- 为 Project 开启框架模式，不可逆
Framework getFramework(frameworkId)                   -- 获取框架全貌
void finishFramework(frameworkId)                     -- 完结框架
```

### 2.2 ElementService

- 所有 CRUD 方法追加新版本，写 ELEMENT_UPDATED 事件
- `delete` 是软删除（isDeprecated = true），写 ELEMENT_DEPRECATED 事件
- `getVersions(elementId)` 返回完整版本历史
- `aiGenerate(frameworkId, type, hint)` -- 调用 Claude 生成元素，注入已有元素上下文避免冲突，返回新版本列表

### 2.3 ChapterService

```
Chapter startChapter(frameworkId) -- 创建新章节，记录当前所有元素的版本快照
void markGenerated(chapterId, contentHash) -- 标记 AI 生成完成
void intervene(chapterId, content, position) -- 记录用户介入
```

### 2.4 ContextBuilder（上下文构建器）

核心方法：`ContextPackage buildContext(frameworkId, currentChapterId)`

**拼装策略（四层）：**

| 层 | 内容 | 形式 |
|---|---|---|
| 第一层 | 全部非弃用元素的最新版本 | 全文注入 System Prompt |
| 第二层 | 当前章正文 + 上一章正文 | 全文注入 |
| 第三层 | 更早章节 AI 摘要（最多 15 条） | 摘要注入 |
| 第四层 | 未解决的 PlotEntry（如有） | 全文注入 |

总 prompt 控制在模型 max_tokens 的 70%。超出时优先缩减第二层之外的摘要层。

**摘要生成**：AI 写完一章后异步调用 Claude 生成该章 200 字以内摘要。

### 2.5 NovelWriteService（写作引擎）

- `startWriting(frameworkId)` -> 调用 `ContextBuilder.buildContext()` -> `ClaudeService.stream()` -> SSE 流式返回
- 每章生成完暂停，发送 `CHAPTER_COMPLETE` 事件，等待用户指令
- `intervene(frameworkId, chapterId, userInput)` -> 将用户输入写入当前章 -> 以插入点后内容为基础重新构建上下文 -> 继续流式生成

### 2.6 ConflictService（冲突检测）

**写前检测（注入期）：**
- 在 System Prompt 中注入全部元素内容，要求 AI 生成时自行遵守
- 如用户提供了"本次写作必须遵守的约束"，额外标注

**写后检测（审查期）：**
- 每章生成完成后，调用 Claude 对照当前元素最新版本做一致性审查
- Claude 返回：冲突列表 [{ elementId, severity, description }]
- MAJOR 冲突 -> 创建 Conflict，状态 PENDING，发送事件，前台通知用户
- MINOR 冲突 -> 自动调用 Claude 生成补丁 -> 创建 Conflict + Patch，状态 PATCHED，发送事件，前台通知

**冲突处理：**
- 用户可选 IGNORE（忽略）/ PATCH（手动补丁）/ REWRITE（重写该章）
- REWRITE -> 删除该章及之后所有章，从该章开头重新生成
- 所有处理写 CONFLICT_RESOLVED 事件

### 2.7 EventService（事件流）

- `appendEvent(frameworkId, eventType, payload)` -- 追加事件（内部使用）
- `getEvents(frameworkId, page, filters)` -- 分页查询，支持按类型/时间范围筛选
- `getStateAt(frameworkId, timestamp)` -- 回溯到指定时间点，重放事件重建框架状态

---

## 三、Controller API

所有端点基础路径 `/api/v1`，均需认证，均有 ownership 校验。

### 3.1 框架

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /v1/projects/:id/framework | 开启框架模式 |
| GET | /v1/frameworks/:id | 获取框架全貌 |
| POST | /v1/frameworks/:id/finish | 完结框架 |

### 3.2 元素

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /v1/frameworks/:id/elements | 列出元素（可按 type 筛选，默认返回各元素最新版本） |
| POST | /v1/frameworks/:id/elements | 添加元素 |
| PUT | /v1/frameworks/:id/elements/:eid | 更新元素（追加版本） |
| DELETE | /v1/frameworks/:id/elements/:eid | 弃用元素 |
| GET | /v1/frameworks/:id/elements/:eid/versions | 查看版本历史 |
| POST | /v1/frameworks/:id/elements/ai-generate | AI 生成元素 |

### 3.3 写作

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /v1/frameworks/:id/write/start | 开始写作（SSE 流式） |
| POST | /v1/frameworks/:id/write/interrupt | 中断并插入用户内容 |
| POST | /v1/frameworks/:id/write/continue | 继续下一章 |

### 3.4 冲突与补丁

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /v1/frameworks/:id/conflicts | 列出冲突（按 severity/status 筛选） |
| POST | /v1/frameworks/:id/conflicts/:cid/resolve | 处理冲突（body: { action, patchContent? }） |
| POST | /v1/frameworks/:id/elements/:eid/impact | 查询修改该元素影响哪些章节 |

### 3.5 事件流

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /v1/frameworks/:id/events | 事件流列表（分页 + 筛选） |
| GET | /v1/frameworks/:id/events/:timestamp | 回溯到指定时间点 |

---

## 四、前端设计

### 4.1 路由

| 路径 | 组件 | 说明 |
|------|------|------|
| `/project/:id/framework` | FrameworkView | 框架主页（概览） |
| `/project/:id/framework/elements` | ElementsView | 元素管理 |
| `/project/:id/framework/write` | WriteView | 写作工作台（核心） |
| `/project/:id/framework/conflicts` | ConflictsView | 冲突管理 |
| `/project/:id/framework/history` | HistoryView | 事件流/回溯 |

### 4.2 组件层级

```
FrameworkView（框架主页）
  +-- FrameworkHeader          -- 框架名、状态指示、完结按钮
  +-- FrameworkSidebar         -- 导航：概览 / 元素 / 写作 / 冲突 / 历史
  |
  +-- ElementsView（元素管理）
  |   +-- ElementToolbar       -- 类型筛选、搜索、添加按钮、AI 生成按钮
  |   +-- ElementCardGrid      -- 元素卡片网格（按类型分组）
  |   +-- ElementDetailPanel   -- 侧滑面板：查看/编辑/版本历史
  |   +-- ElementAIGenDialog   -- AI 生成元素对话框（选类型 + 输入提示）
  |
  +-- WriteView（写作工作台）★ 核心页面
  |   +-- WritingContextBar    -- 顶部栏：当前引用的设定摘要（可展开查看详情）
  |   +-- ChapterPanel         -- 左侧：章节列表 + 状态标记（草稿/AI生成/已完成）
  |   +-- WritingCanvas        -- 中央：AI 流式内容实时展示区域
  |   +-- UserInterveneInput   -- 底部输入区：用户插入内容/指令
  |   +-- WritingControls      -- 底部控制栏：开始 / 暂停 / 继续 / 完结
  |   +-- ConflictNotifier     -- 右下角浮动气泡：实时冲突通知
  |
  +-- ConflictsView（冲突管理）
  |   +-- ConflictFilter       -- 按严重度/状态筛选
  |   +-- ConflictList         -- 冲突列表（严重度颜色：红=大冲突，黄=小冲突）
  |   +-- ConflictDetail       -- 详情 + 操作（忽略/打补丁/重写）
  |
  +-- HistoryView（事件流）
      +-- EventFilter          -- 按类型/时间范围筛选
      +-- EventTimeline        -- 时间轴视图，垂直展示事件节点
      +-- EventDetail          -- 选中事件查看 payload 详情 + 差异对比
```

### 4.3 WriteView 交互流程

```
进入 WriteView
  -> 顶部 ContextBar 显示当前元素统计（角色 X 个、地点 Y 个、设定 Z 条...）
  -> 左侧 ChapterPanel 显示已有章节
  -> 中央显示当前章内容
  -> 用户点击"开始写作"
      -> POST /v1/frameworks/:id/write/start
      -> Canvas 进入流式渲染，实时显示 AI 输出
      -> Controls 变为 [暂停] [插入] [完结]
  -> AI 写完整章 -> 暂停 -> [继续下一章] [插入内容] [修改设定] [完结]
  -> 用户点"插入" -> 底部展开输入框 -> 用户输入内容/指令 -> 提交
      -> POST /v1/frameworks/:id/write/interrupt
      -> AI 基于插入内容续写
  -> 如果 ConflictNotifier 弹出 -> 点击 -> 跳转冲突处理页
  -> 用户点"完结" -> 确认对话框 -> POST /v1/frameworks/:id/finish
```

### 4.4 改造现有组件

- **DashboardLayout / ProjectSidebar**：框架模式 Project 在侧边栏显示特殊图标/标记，点击跳转 FrameworkView
- **WorkspaceView**：普通模式保持不变。框架模式重定向到 WriteView
- **AIPanel**：在 WriteView 中复用风格选择 + AI 操作面板（底部缩略模式）

---

## 五、状态机

```
框架状态:
  ACTIVE --> FINISHED (用户完结，不可逆)

章节状态:
  DRAFT --> AI_GENERATING --> AI_GENERATED --> USER_EDITING --> COMPLETED
                 ^                                      |
                 +--------------------------------------+ (用户触发重写)

冲突状态:
  PENDING --> IGNORED
           +-> PATCHED
           +-> REWRITTEN (触重重写相关章节)
```

---

## 六、错误处理

| 场景 | 处理 |
|------|------|
| Claude API 返回异常 | 重试 1 次，仍失败则暂停 + 事件记录 + 前端提示 |
| Token 超限 | 自动缩窄上下文窗口（减少摘要层），控制在 max_tokens 的 70% |
| 用户短时间多次介入 | 防抖，以最后一次提交为准 |
| 元素为空时开始写作 | 正常开始，但前端提示"建议先完善设定" |
| SSE 断连 | 前端显示重连按钮，后端保留当前章已生成内容 |
| 冲突检测 Claude 调用失败 | 跳过本轮检测，记录事件，下次写作前重试 |
| 并发修改同一元素 | 乐观锁（version 递增），冲突时返回 409 |

---

## 七、测试策略

| 层 | 内容 |
|---|---|
| ElementService 单测 | CRUD + 版本化 + 类型筛选 |
| ContextBuilder 单测 | 四层拼装逻辑 + 超限缩窄 |
| ConflictService 单测 | 写后检测流程 + MAJOR/MINOR 分支 |
| EventService 单测 | 追加 + 分页筛选 + 重放回溯 |
| FrameworkService 集成测试 | 开启框架 -> 添加元素 -> 写作 -> 完结，Mock ClaudeService |
| 前端组件测试 | ElementsView CRUD 交互、WriteView 状态机流转、ConflictNotifier 弹出 |
| 前端 store 测试 | framework store / elements store / writing session store |

---

## 八、实现顺序

1. **数据层** -- 新建 6 个 Entity + Repository + Flyway migration（用 schema.sql 而非 ddl-auto）
2. **FrameworkService + ElementService** -- 框架 CRUD + 元素版本化 + Controller
3. **前端设定管理** -- ElementsView + ElementDetailPanel + ElementAIGenDialog
4. **ContextBuilder** -- 上下文拼装 + ChapterSummary 生成
5. **NovelWriteService + ConflictService** -- 写作引擎 + 冲突检测 + Controller
6. **前端写作工作台** -- WriteView + WritingCanvas + ChapterPanel + Controls
7. **EventService** -- 事件流 + 回溯 + Controller
8. **前端冲突/历史** -- ConflictsView + HistoryView + ConflictNotifier
9. **联动测试** -- 端到端：设定 -> 写作 -> 介入 -> 冲突检测 -> 处理 -> 完结
