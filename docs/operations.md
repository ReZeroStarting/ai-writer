# AI Writer 运维文档

## 环境变量

| 变量 | 必须 | 默认值 | 说明 |
|------|------|--------|------|
| `ANTHROPIC_API_KEY` | 是 | 无 | Claude API 密钥，获取地址: https://console.anthropic.com |
| `JWT_SECRET` | 是 | 无 | JWT 签名密钥，至少 256 位随机字符串。未设置则启动失败 |
| `MYSQL_USER` | 否 | `root` | 数据库用户名 |
| `MYSQL_PASSWORD` | 是 | 无 | 数据库密码 |
| `LOG_PATH` | 否 | `./logs` | 日志文件目录 |

生成 JWT_SECRET：
```bash
openssl rand -base64 32
```

## 日志

- 路径：`${LOG_PATH}/ai-writer.log`（全量，保留 30 天）
- 错误日志：`${LOG_PATH}/ai-writer-error.log`（WARN 及以上，保留 60 天）
- 请求日志：每条请求记录来源 IP、方法、URI、耗时、异常

## API 状态码规范

| 状态码 | 场景 |
|--------|------|
| 200 | 成功 |
| 400 | 参数校验失败 |
| 401 | 未登录 / Token 无效 / 密码错误 |
| 403 | 无权操作他人资源 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

## 安全

- JWT accessToken 有效期 30 分钟，refreshToken 7 天
- 前端 Token 存储在 localStorage（后续应迁移到 httpOnly cookie）
- 密码使用 BCrypt 加密
- AI API 调用日志不记录完整请求内容

## 架构决策

| 日期 | 决策 | 原因 |
|------|------|------|
| 2026-05-03 | 使用 WebClient 裸调 Claude API 而非 Spring AI | Spring AI 对 SSE 流式支持的成熟度不足 |
| 2026-05-04 | JWT 密钥移除硬编码默认值 | 安全问题：硬编码密钥可被用于伪造任意用户 Token |
| 2026-05-04 | 异常处理改为分类型 HTTP 状态码 | 之前所有错误统一返回 400，前端无法区分认证失败和参数错误 |
| 2026-05-04 | 添加暗色模式+护眼模式切换 | 提供 light/dark/eye-care 三种主题，persist 到 localStorage |
| 2026-05-04 | 重命名 UI 文案并添加搜索栏 | "我的项目"→"小说列表"，"文档"→"章节"，侧边栏支持搜索过滤 |
| 2026-05-04 | 风格管理独立为顶层路由 | 从 DashboardLayout 子路由移出，全屏显示，添加返回按钮 |

## 主题切换

AI Writer 支持三种主题模式，通过顶栏按钮循环切换：

| 模式 | 效果 | CSS 实现 |
|------|------|------|
| light | 默认浅色主题 | Element Plus 默认 |
| dark | 深色主题 | Element Plus `dark` class |
| eye-care | 暖黄护眼模式 | 自定义 CSS 变量覆盖，背景 `#f5f0e8` |

主题状态存储在 `localStorage` 的 `ai-writer-theme` 键中。

## 启动命令

```bash
# 后端
cd backend
ANTHROPIC_API_KEY=sk-xxx JWT_SECRET=xxx MYSQL_PASSWORD=xxx mvn spring-boot:run

# 前端
cd frontend
npm run dev
```
