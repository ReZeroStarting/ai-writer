# AI Writer UI Enhancements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Five UI enhancements: rename labels, reduce sidebar widths, add search bars, implement dark/eye-care themes, and make style management a standalone page.

**Architecture:** Vue 3 + Element Plus frontend changes only. New Pinia store for theme state. CSS variable-based theming with Element Plus dark class. No backend changes.

**Tech Stack:** Vue 3, TypeScript, Pinia, Element Plus, vitest

---

## File Map

```
frontend/
├── src/
│   ├── stores/
│   │   └── theme.ts            # NEW - theme state management
│   ├── components/
│   │   ├── layout/
│   │   │   ├── AppHeader.vue    # MODIFY - add theme toggle button
│   │   │   └── ProjectSidebar.vue # MODIFY - rename, resize, add search
│   │   └── editor/
│   │       └── DocList.vue      # MODIFY - rename, resize, add search
│   ├── views/
│   │   └── StyleManageView.vue  # MODIFY - standalone layout, back button
│   └── router/
│       └── index.ts             # MODIFY - /styles as top-level route
├── src/__tests__/
│   ├── theme.test.ts            # NEW - theme store tests
│   └── sidebar-search.test.ts   # NEW - search filter tests
└── package.json                 # MODIFY - add vitest deps
```

---

### Task 1: Install vitest + test dependencies

**Files:** `package.json`

- [ ] **Step 1: Install vitest and vue test utils**

```bash
cd frontend && npm install -D vitest @vue/test-utils jsdom
```

- [ ] **Step 2: Add test script to package.json**

Edit `package.json`, add to scripts:
```json
"test": "vitest run"
```

- [ ] **Step 3: Add vitest config**

Create `frontend/vitest.config.ts`:
```ts
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'jsdom',
  },
})
```

- [ ] **Step 4: Run tests to verify setup**

```bash
cd frontend && npx vitest run
```
Expected: no tests found (exit 0 or info message)

- [ ] **Step 5: Commit**

---

### Task 2: Write theme store + tests (TDD)

**Files:**
- Create: `frontend/src/stores/theme.ts`
- Create: `frontend/src/__tests__/theme.test.ts`

- [ ] **Step 1: Write failing test**

```ts
// frontend/src/__tests__/theme.test.ts
import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useThemeStore } from '@/stores/theme'

describe('useThemeStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('defaults to light theme', () => {
    const store = useThemeStore()
    expect(store.theme).toBe('light')
  })

  it('cycles light -> dark -> eye-care -> light', () => {
    const store = useThemeStore()
    expect(store.theme).toBe('light')
    store.toggle()
    expect(store.theme).toBe('dark')
    store.toggle()
    expect(store.theme).toBe('eye-care')
    store.toggle()
    expect(store.theme).toBe('light')
  })

  it('persists theme to localStorage', () => {
    const store = useThemeStore()
    store.toggle() // light -> dark
    expect(localStorage.getItem('ai-writer-theme')).toBe('dark')
  })

  it('initializes from localStorage', () => {
    localStorage.setItem('ai-writer-theme', 'eye-care')
    const store = useThemeStore()
    expect(store.theme).toBe('eye-care')
  })
})
```

- [ ] **Step 2: Run test - expect FAIL**

```bash
cd frontend && npx vitest run
```

- [ ] **Step 3: Implement theme store**

```ts
// frontend/src/stores/theme.ts
import { ref, watch } from 'vue'
import { defineStore } from 'pinia'

const STORAGE_KEY = 'ai-writer-theme'
type Theme = 'light' | 'dark' | 'eye-care'

const themes: Theme[] = ['light', 'dark', 'eye-care']

function loadTheme(): Theme {
  const saved = localStorage.getItem(STORAGE_KEY)
  if (saved && themes.includes(saved as Theme)) return saved as Theme
  return 'light'
}

export const useThemeStore = defineStore('theme', () => {
  const theme = ref<Theme>(loadTheme())

  function apply() {
    document.documentElement.classList.remove('dark', 'eye-care')
    if (theme.value === 'dark') document.documentElement.classList.add('dark')
    if (theme.value === 'eye-care') document.documentElement.classList.add('eye-care')
  }

  function toggle() {
    const idx = themes.indexOf(theme.value)
    theme.value = themes[(idx + 1) % themes.length]
  }

  watch(theme, (t) => {
    localStorage.setItem(STORAGE_KEY, t)
    apply()
  })

  return { theme, toggle, apply }
})
```

- [ ] **Step 4: Run tests - expect PASS**

- [ ] **Step 5: Commit**

---

### Task 3: Rename labels + reduce widths

**Files:**
- Modify: `frontend/src/components/layout/ProjectSidebar.vue`
- Modify: `frontend/src/components/editor/DocList.vue`

- [ ] **Step 1: Modify ProjectSidebar**

- `.sidebar` width: `240px` → `180px`
- Header text: `"我的项目"` → `"小说列表"`
- Dialog title: `"新建项目"` → `"新建小说"`
- Dialog title: `"重命名"` → `"重命名"` (no change)

- [ ] **Step 2: Modify DocList**

- `.doc-list` width: `240px` → `180px`
- Header text: `"文档"` → `"章节"`
- Button text in create handler: `'未命名文档'` → `'未命名章节'`
- Toast: `'文档已删除'` → `'章节已删除'`
- Dialog title if any: update to `"新建章节"`

- [ ] **Step 3: Verify build passes**

```bash
cd frontend && npx vue-tsc -b --noEmit
```

- [ ] **Step 4: Commit**

---

### Task 4: Add search bars + tests

**Files:**
- Modify: `frontend/src/components/layout/ProjectSidebar.vue`
- Modify: `frontend/src/components/editor/DocList.vue`

- [ ] **Step 1: Add search to ProjectSidebar**

Add `<el-input v-model="search" placeholder="搜索..." size="small" clearable />` below the header.
Add `const search = ref('')`
Add `const filteredProjects = computed(() => store.projects.filter(p => p.name.includes(search.value)))`
Use `filteredProjects` in v-for instead of `store.projects`.

- [ ] **Step 2: Add search to DocList**

Same pattern: `search` ref, `filteredDocs` computed, use in v-for.

- [ ] **Step 3: Build verification**

```bash
cd frontend && npx vue-tsc -b --noEmit
```

- [ ] **Step 4: Commit**

---

### Task 5: Dark mode + eye-care mode (CSS + AppHeader)

**Files:**
- Modify: `frontend/src/components/layout/AppHeader.vue`
- Modify: `frontend/src/components/layout/ProjectSidebar.vue`
- Modify: `frontend/src/components/editor/DocList.vue`
- Modify: `frontend/src/views/WorkspaceView.vue`
- Modify: `frontend/src/views/StyleManageView.vue`

- [ ] **Step 1: Add theme toggle button to AppHeader**

Add an icon button with `@click="theme.toggle()"` in the header-right div.
Import `useThemeStore` from `@/stores/theme`.
Show Sun/Moon/umbrella icons based on current theme.

- [ ] **Step 2: Apply theme class on app init**

In `main.ts`, after creating Pinia, call `useThemeStore().apply()`.

- [ ] **Step 3: Update CSS in all components to use CSS variables**

Replace hardcoded colors:
- `background: #fafafa` → `background: var(--el-bg-color-page)`
- `border-right: 1px solid #e4e7ed` → `border-right: 1px solid var(--el-border-color)`
- `background: #fff` → `background: var(--el-bg-color)`
- `color: #909399` → `color: var(--el-text-color-secondary)`
- etc.

Add eye-care mode overrides:
```css
:global(.eye-care) {
  --el-bg-color: #f5f0e8;
  --el-bg-color-page: #ede8dd;
  background: #f5f0e8;
}
```

- [ ] **Step 4: Build verification**

- [ ] **Step 5: Commit**

---

### Task 6: Style management as standalone page

**Files:**
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/views/StyleManageView.vue`

- [ ] **Step 1: Move /styles route**

Remove `/styles` from DashboardLayout children. Add as top-level route:
```ts
{
  path: '/styles', name: 'styles',
  component: () => import('@/views/StyleManageView.vue'),
  meta: { requiresAuth: true },
}
```

- [ ] **Step 2: Add back button to StyleManageView**

Add a back button at the top:
```html
<el-button text @click="$router.back()">← 返回</el-button>
```

- [ ] **Step 3: Build verification + type check**

- [ ] **Step 4: Commit**

---

### Task 7: Final integration verification

- [ ] **Step 1: Run all tests**

```bash
cd frontend && npx vitest run
```

- [ ] **Step 2: Type check + build**

```bash
cd frontend && npx vue-tsc -b && npx vite build
```

- [ ] **Step 3: Run dev server and verify visually if possible**

```bash
cd frontend && npx vite --host 0.0.0.0
```
