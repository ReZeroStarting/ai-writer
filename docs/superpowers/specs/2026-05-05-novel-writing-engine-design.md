# 小说写作框架引擎 — 设计规格

**日期:** 2026-05-05  
**状态:** 待实现

---

## 概述

在现有 AI Writer 平台上新增**小说写作框架引擎**。用户可定义结构化设定集（人物、世界观、情节），AI 基于设定集自动接力续写，用户随时插入内容引导方向。通过动态摘要注入确保持续一致，解决"前后忘本"问题。

### 核心四要素

1. **设定集（World Bible）** — 结构化表单：人物卡片、世界观条目、情节锚点
2. **永不停止续写** — AI 完成一段后自动继续，用户不喊停就不停
3. **随时介入** — 支持侧边栏插话 + 正文直接编辑两种方式
4. **长期一致** — 动态摘要引擎，前文按章摘要注入 system prompt

---

## 一、数据模型

### 新增实体

**SettingBook** — 设定集容器，1:1 绑定 Project

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | PK |
| projectId | Long | FK → Project，unique |
| createdAt/updatedAt | DateTime | 审计字段 |

**CharacterEntry** — 人物卡片

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | PK |
| settingBookId | Long | FK → SettingBook |
| name | String(100) | 姓名 |
| gender | String(20) | 性别 |
| personality | Text | 性格描述 |
| appearance | Text | 外貌描述 |
| background | Text | 背景故事 |
| relationships | Text | 与其他人物关系 |
| sortOrder | int | 排序 |

**WorldEntry** — 世界观条目

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | PK |
| settingBookId | Long | FK → SettingBook |
| category | Enum | 地名/规则/历史/其他 |
| title | String(200) | 标题 |
| content | Text | 内容 |
| sortOrder | int | 排序 |

**PlotEntry** — 情节锚点

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | PK |
| settingBookId | Long | FK → SettingBook |
| title | String(200) | 锚点标题 |
| description | Text | 必须发生的事件/伏笔 |
| isResolved | boolean | 是否已用，默认 false |
| sortOrder | int | 排序 |

**WritingSession** — 写作会话状态，1:1 绑定 Project

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | PK |
| projectId | Long | FK → Project，unique |
| isRunning | boolean | 接力开关 |
| pauseAfterWords | int | 每 N 字暂停，0=不暂停 |
| lastContentHash | String(64) | 用于侦测用户编辑 |
| updatedAt | DateTime | 最后活跃时间 |

**ChapterSummary** — 章节摘要，用于"不忘本"

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | PK |
| projectId | Long | FK → Project |
| chapterIndex | int | 第几章 |
| summary | Text | AI 生成的章节摘要 |
| createdAt | DateTime | 生成时间 |

### 与现有 Document 模型的关系

- 现有 `Document` 映射为小说的**章节**（一个 Project 下多个 Document = 多章）
- `WritingSession` 绑定 Project，在用户当前打开的 Document 内续写
- "切章"由用户手动触发：保存当前 Document → 创建新 Document → AI 在新章节继续
- `ChapterSummary.chapterIndex` 对应 Document 在 Project 下的序号

### 现有实体不变

- `Project` / `Document` / `StyleTemplate` / `SourceMaterial` / `LearningJob` 保持现状
- `SettingBook` 通过 `projectId` 关联 Project，不侵入现有模型

---

## 二、后端 Service 设计

### 2.1 SettingService — 设定集 CRUD

```
SettingBook getOrCreate(projectId)
CharacterEntry add/update/delete/reorder → CRUD + 排序
WorldEntry add/update/delete/reorder → CRUD + 排序
PlotEntry add/update/delete/reorder/markResolved → CRUD + 标记完成
```

### 2.2 SummaryManager — 动态摘要引擎

核心方法：`ContextPackage buildContext(projectId, currentContent, settingBookId)`

**三层拼装策略：**

| 层 | 内容 | 形式 |
|---|---|---|
| 第一层 | 核心设定：人物卡全量 + 世界观全量 + 未完成的 PlotEntry | 全文注入 |
| 第二层 | 最近 2 章正文 | 全文注入 |
| 第三层 | 更早章节摘要（最近 10 条 ChapterSummary） | 摘要注入 |

超出 10 章的老内容不再引用。总 prompt 控制在模型 max_tokens 的 70% 以内。

**摘要生成时机：** 用户手动切章或 AI 写完一章后，异步生成该章摘要。

### 2.3 NovelSessionService — 写作接力引擎

```
start(projectId)        → 开启自动接力
stop(projectId)         → 停止
intervene(projectId, text) → 用户插入内容
buildWritePrompt(projectId, currentContent) → 拼装完整 prompt
```

**接力循环逻辑：**

```
开启 → AI 写一段 → 流结束 →
  ├─ 用户停止了？ → 退出
  ├─ 用户插入了？ → 以插入内容为基础继续
  ├─ 用户编辑了？ → 以编辑后正文为基础继续
  └─ 都没有 → 自动继续写
```

### 2.4 NovelWriteService — 小说写作服务

- 调用 `SummaryManager.buildContext()` 获取上下文
- 调用 `StyleService` 获取风格 system prompt
- 拼装完整 prompt → `ClaudeService.stream()` → SSE 流式返回

---

## 三、Controller API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/settings/{projectId} | 获取设定集 |
| PUT | /api/settings/{projectId} | 更新设定集元数据 |
| POST | /api/settings/{projectId}/characters | 新增人物 |
| PUT | /api/settings/{projectId}/characters/{id} | 更新人物 |
| DELETE | /api/settings/{projectId}/characters/{id} | 删除人物 |
| PUT | /api/settings/{projectId}/characters/reorder | 人物排序 |
| POST | /api/settings/{projectId}/worlds | 新增世界观 |
| PUT | /api/settings/{projectId}/worlds/{id} | 更新世界观 |
| DELETE | /api/settings/{projectId}/worlds/{id} | 删除世界观 |
| POST | /api/settings/{projectId}/plots | 新增情节锚点 |
| PUT | /api/settings/{projectId}/plots/{id} | 更新情节锚点 |
| DELETE | /api/settings/{projectId}/plots/{id} | 删除情节锚点 |
| PUT | /api/settings/{projectId}/plots/{id}/resolve | 标记完成 |
| POST | /api/novel/{projectId}/start | 开启接力 |
| POST | /api/novel/{projectId}/stop | 停止接力 |
| POST | /api/novel/{projectId}/intervene | 插入内容 |
| GET | /api/novel/{projectId}/stream | SSE 流式续写 |

---

## 四、前端设计

### 4.1 新增页面

**设定管理页** `/project/:id/settings`

```
SettingsView.vue
  ├─ CharacterTab.vue         — 人物卡片列表 + 新增/编辑/删除/拖拽排序
  │   └─ CharacterFormDialog.vue
  ├─ WorldTab.vue             — 世界观条目，按分类分组
  │   └─ WorldFormDialog.vue
  └─ PlotTab.vue              — 情节锚点列表，支持标记完成
      └─ PlotFormDialog.vue
```

### 4.2 工作区增强（改造 WorkspaceView）

```
DocEditor.vue (改造)
  ├─ TiptapEditor.vue         — 现有编辑器 + 用户编辑感知
  ├─ AIPanel.vue (改造)
  │   ├─ [自动写作] 开关      — 开启/关闭接力
  │   ├─ [暂停/继续] 按钮     — 接力中临时暂停
  │   └─ [插入内容] 输入框    — 侧边栏插话
  └─ 状态指示器               — "AI 写作中..." / "已暂停" / "已停止"
```

### 4.3 交互流程

```
[自动写作] 开启 → POST /api/novel/start → SSE 流开始 → 编辑器实时显示

侧边栏 [插入] → POST /api/novel/intervene → AI 基于插入续写

编辑器直接改字 → 失焦/停顿 2s → 检测变更 → AI 从编辑处继续

[停止] → POST /api/novel/stop → 接力终止
```

---

## 五、错误处理

| 场景 | 处理 |
|------|------|
| AI 返回异常 | 重试 1 次，仍失败则暂停 + 提示 |
| Token 超限 | 自动缩窄近期窗口，控制在 70% 内 |
| 用户短时间多次介入 | 防抖，以最后一次为准 |
| 设定集为空 | 正常写作，提示"建议先完善设定" |
| API 超时 | SSE 断连，提示重试 |
| 接力中关闭页面 | beforeunload 发 stop |
| 网络断开 | 断连提示 + 重连按钮 |

---

## 六、测试策略

| 层 | 内容 |
|---|---|
| SettingService 单测 | CRUD 全流程 + 排序 + 标记完成 |
| SummaryManager 单测 | 上下文拼装逻辑验证 |
| NovelSessionService 单测 | 状态机：start/stop/intervene 转换 |
| 集成测试 | Mock ClaudeService → 验证 SSE 链路 |
| 前端组件测试 | 设定表单验证、开关状态机 |
| E2E | 不要求，手动验证核心流程 |

---

## 七、实现顺序

1. **数据层** — 新建 5 个 Entity + Repository + 数据库自动建表
2. **SettingService** — 设定集 CRUD + Controller + 前端设定管理页
3. **SummaryManager** — 上下文拼装 + ChapterSummary 生成
4. **NovelSessionService + NovelWriteService** — 接力引擎 + SSE 流
5. **前端工作区增强** — AIPanel 改造 + 编辑感知 + 接力交互
6. **联动测试** — 设定 → 写作 → 介入 → 一致性，端到端验证
