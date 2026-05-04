# ADR-001: 使用 WebClient 裸调 Anthropic API 而非 Spring AI

**日期:** 2026-05-03
**状态:** 已采纳

---

## 背景

项目需要集成 Anthropic Claude API（SSE 流式 + 同步两种模式）。可选 Spring AI 或直接用 WebClient。

## 方案

### 方案 A: Spring AI
- 优点：Spring 官方集成，自动处理认证和序列化
- 缺点：当时对 Claude SSE 流式支持不稳定，版本仍在快速迭代

### 方案 B: WebClient 裸调
- 优点：完全控制请求/响应，SSE 解析细节可控，不受框架升级影响
- 缺点：需要自行处理消息协议（SSE delta 解析、content block 提取）

## 决策

选择方案 B（WebClient）。

## 后果

- 代码中有约 50 行手动解析 Anthropic Messages API 响应
- `extractDeltaText()` 和 `extractContentText()` 需随 Anthropic API 变更而适配
- 如果 Spring AI 成熟，可以考虑迁移
