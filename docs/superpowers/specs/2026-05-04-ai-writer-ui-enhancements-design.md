# AI Writer UI Enhancements Design

**Date:** 2026-05-04
**Status:** Approved

## Overview

Five UI enhancements to the AI Writer application: rename labels, reduce sidebar widths, add search bars, implement dark/eye-care themes, and make style management a standalone page.

---

## 1. Rename Labels + Width Reduction

**Current state:**
- ProjectSidebar header: "我的项目", width: 240px
- DocList header: "文档", width: 240px

**Target:**
- ProjectSidebar header: "小说列表", width: 180px
- DocList header: "章节", width: 180px
- Related dialog titles and toast messages updated to match ("新建项目" → "新建小说", "文档已删除" → "章节已删除", etc.)

**Files:** `ProjectSidebar.vue`, `DocList.vue`
**Nature:** Pure text and CSS value changes. No logic changes.

---

## 2. Search Bars

Add a search input to both ProjectSidebar and DocList:

- **Position:** Below the header bar (title + new button), above the item list
- **Style:** `el-input` with search icon, full width within the 180px sidebar
- **Behavior:** Real-time filtering via `computed` — unmatched items hidden; clearing the input restores full list
- **State:** Local `ref<string>`, per-component, not persisted

**Files:** `ProjectSidebar.vue`, `DocList.vue`

---

## 3. Dark Mode + Eye-Care Mode

**Approach:** Element Plus built-in `dark` class + project-level CSS variables.

### Theme Store

New Pinia store `useThemeStore`:

- `theme: Ref<'light' | 'dark' | 'eye-care'>`
- `toggle()` — cycles light → dark → eye-care → light
- `init()` — reads from `localStorage`, applies class to `<html>`
- `apply()` — sets class on `<html>` (`dark` for dark mode, `eye-care` for eye-care, none for light)

### Persistence

`localStorage` key `ai-writer-theme`. Initialized on app start.

### Toggle UI

AppHeader right side, before the style-management link: an icon button cycling through sun/moon/eye icons corresponding to the current mode.

### Eye-Care Mode

- `<html class="eye-care">` — global background `#f5f0e8` (warm beige), text remains dark
- Editor area, sidebars, and header all adopt the warm background

### Dark Mode

- `<html class="dark">` — Element Plus components auto-adapt
- Custom components: use CSS variables (or Element Plus `var(--el-bg-color)` etc.) so they adapt automatically

### CSS Strategy

- Sidebar backgrounds: use `var(--el-bg-color-page)` instead of hardcoded `#fafafa`
- Borders: use `var(--el-border-color)` instead of hardcoded `#e4e7ed`
- Editor background and text areas use Element Plus CSS variables

**Files:** `stores/theme.ts` (new), `AppHeader.vue`, `main.ts` or `App.vue`, `ProjectSidebar.vue`, `DocList.vue`, `WorkspaceView.vue`, `StyleManageView.vue`

---

## 4. Style Management as Standalone Page

- Move `/styles` route from a child of DashboardLayout to a top-level route
- The page renders full-screen without AppHeader or ProjectSidebar
- Add a "← 返回" button at the top of the page, using `router.back()` to navigate to the previous page

### Router Changes

```ts
// Before: /styles is a child of /
// After: /styles is a sibling of /
{
  path: '/styles', name: 'styles',
  component: () => import('@/views/StyleManageView.vue'),
  meta: { requiresAuth: true },
}
```

The existing `/styles` child route under `DashboardLayout` is removed.

**Files:** `router/index.ts`, `StyleManageView.vue`

---

## Implementation Order

1. Rename labels + width reduction
2. Search bars
3. Dark mode + eye-care mode
4. Style management standalone page
