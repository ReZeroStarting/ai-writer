import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useFrameworkStore } from '@/stores/framework'
import type { Framework, FrameworkElement, Conflict } from '@/types'

vi.mock('@/api/framework', () => ({
  frameworkApi: {
    open: vi.fn(),
    get: vi.fn(),
    getByProjectId: vi.fn(),
    finish: vi.fn(),
    listElements: vi.fn(),
    createElement: vi.fn(),
    updateElement: vi.fn(),
    deleteElement: vi.fn(),
    getElementVersions: vi.fn(),
    aiGenerateElements: vi.fn(),
    listConflicts: vi.fn(),
    resolveConflict: vi.fn(),
    getImpact: vi.fn(),
    listEvents: vi.fn(),
    startWriting: vi.fn(),
    continueWriting: vi.fn(),
    intervene: vi.fn(),
    saveChapter: vi.fn(),
    getLatestChapter: vi.fn(),
  },
}))

import { frameworkApi } from '@/api/framework'
const api = vi.mocked(frameworkApi)

function makeFramework(overrides: Partial<Framework> = {}): Framework {
  return {
    id: 100,
    projectId: 10,
    status: 'ACTIVE',
    elementCount: 0,
    chapterCount: 0,
    unresolvedConflictCount: 0,
    createdAt: '2026-05-05T00:00:00',
    updatedAt: '2026-05-05T00:00:00',
    ...overrides,
  }
}

function makeElement(overrides: Partial<FrameworkElement> = {}): FrameworkElement {
  return {
    id: 'el-1',
    version: 1,
    type: 'character',
    name: '张三',
    content: '主角',
    isDeprecated: false,
    createdAt: '2026-05-05T00:00:00',
    ...overrides,
  }
}

function makeConflict(overrides: Partial<Conflict> = {}): Conflict {
  return {
    id: 1,
    elementId: 'el-1',
    chapterId: 1,
    chapterRefVersion: 1,
    currentVersion: 2,
    severity: 'MINOR',
    description: '版本冲突',
    status: 'PENDING',
    createdAt: '2026-05-05T00:00:00',
    ...overrides,
  }
}

describe('useFrameworkStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('initializes with null state', () => {
    const store = useFrameworkStore()
    expect(store.framework).toBeNull()
    expect(store.elements).toEqual([])
    expect(store.chapters).toEqual([])
    expect(store.conflicts).toEqual([])
    expect(store.events).toEqual([])
    expect(store.loading).toBe(false)
  })

  it('opens framework', async () => {
    const fw = makeFramework()
    api.open.mockResolvedValue({ data: fw } as any)

    const store = useFrameworkStore()
    const result = await store.openFramework(10)

    expect(api.open).toHaveBeenCalledWith(10)
    expect(store.framework).toEqual(fw)
    expect(result).toEqual(fw)
  })

  it('fetches framework with elements', async () => {
    const fw = makeFramework()
    const el = makeElement()
    api.get.mockResolvedValue({ data: fw } as any)
    api.listElements.mockResolvedValue({ data: [el] } as any)

    const store = useFrameworkStore()
    await store.fetchFramework(100)

    expect(api.get).toHaveBeenCalledWith(100)
    expect(api.listElements).toHaveBeenCalledWith(100)
    expect(store.framework).toEqual(fw)
    expect(store.elements).toEqual([el])
  })

  it('creates element and prepends to list', async () => {
    const existing = makeElement({ id: 'old', name: '旧角色' })
    const store = useFrameworkStore()
    store.elements = [existing]

    const created = makeElement({ id: 'new', name: '新角色' })
    api.createElement.mockResolvedValue({ data: created } as any)

    const result = await store.createElement(100, { type: 'character', name: '新角色', content: 'desc' })

    expect(api.createElement).toHaveBeenCalledWith(100, { type: 'character', name: '新角色', content: 'desc' })
    expect(store.elements).toHaveLength(2)
    expect(store.elements[0]).toEqual(created)
    expect(result).toEqual(created)
  })

  it('updates element in place', async () => {
    const el = makeElement({ id: 'el-1', name: '旧名' })
    const store = useFrameworkStore()
    store.elements = [el]

    const updated = makeElement({ id: 'el-1', version: 2, name: '新名' })
    api.updateElement.mockResolvedValue({ data: updated } as any)

    await store.updateElement(100, 'el-1', { type: 'character', name: '新名', content: 'desc' })

    expect(api.updateElement).toHaveBeenCalledWith(100, 'el-1', { type: 'character', name: '新名', content: 'desc' })
    expect(store.elements[0].name).toBe('新名')
    expect(store.elements[0].version).toBe(2)
  })

  it('deletes element from list', async () => {
    const el = makeElement({ id: 'el-1' })
    const store = useFrameworkStore()
    store.elements = [el]

    api.deleteElement.mockResolvedValue({} as any)

    await store.deleteElement(100, 'el-1')

    expect(api.deleteElement).toHaveBeenCalledWith(100, 'el-1')
    expect(store.elements).toHaveLength(0)
  })

  it('fetches conflicts', async () => {
    const c = makeConflict()
    api.listConflicts.mockResolvedValue({ data: [c] } as any)

    const store = useFrameworkStore()
    await store.fetchConflicts(100)

    expect(api.listConflicts).toHaveBeenCalledWith(100)
    expect(store.conflicts).toEqual([c])
  })

  it('resolves conflict and updates in array', async () => {
    const c = makeConflict({ id: 1, status: 'PENDING' as const })
    const store = useFrameworkStore()
    store.conflicts = [c]

    const resolved = { ...c, status: 'IGNORED' as const }
    api.resolveConflict.mockResolvedValue({ data: resolved } as any)

    const result = await store.resolveConflict(100, 1, 'ignore')

    expect(api.resolveConflict).toHaveBeenCalledWith(100, 1, { action: 'ignore', patchContent: undefined })
    expect(store.conflicts[0].status).toBe('IGNORED')
    expect(result).toEqual(resolved)
  })

  it('fetches events', async () => {
    api.listEvents.mockResolvedValue({ data: [] } as any)

    const store = useFrameworkStore()
    await store.fetchEvents(100)

    expect(api.listEvents).toHaveBeenCalledWith(100)
  })

  it('finishes framework', async () => {
    const fw = makeFramework({ status: 'ACTIVE' })
    const finished = makeFramework({ status: 'FINISHED' })
    api.finish.mockResolvedValue({ data: finished } as any)

    const store = useFrameworkStore()
    store.framework = fw
    await store.finishFramework(100)

    expect(api.finish).toHaveBeenCalledWith(100)
    expect(store.framework).toEqual(finished)
  })
})
