import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Framework, FrameworkElement, Chapter, Conflict, WritingEvent } from '@/types'
import { frameworkApi } from '@/api/framework'

export const useFrameworkStore = defineStore('framework', () => {
  const framework = ref<Framework | null>(null)
  const elements = ref<FrameworkElement[]>([])
  const chapters = ref<Chapter[]>([])
  const conflicts = ref<Conflict[]>([])
  const events = ref<WritingEvent[]>([])
  const loading = ref(false)

  async function openFramework(projectId: number) {
    const res = await frameworkApi.open(projectId)
    framework.value = res.data
    return res.data
  }

  async function fetchFramework(frameworkId: number) {
    loading.value = true
    try {
      const res = await frameworkApi.get(frameworkId)
      framework.value = res.data
      const [els] = await Promise.all([
        frameworkApi.listElements(frameworkId),
      ])
      elements.value = els.data
    } finally { loading.value = false }
  }

  async function createElement(fwId: number, data: { type: string; name: string; content: string }) {
    const res = await frameworkApi.createElement(fwId, data)
    elements.value.unshift(res.data)
    await refreshSummary(fwId)
    return res.data
  }

  async function updateElement(fwId: number, elementId: string, data: { type: string; name: string; content: string }) {
    const res = await frameworkApi.updateElement(fwId, elementId, data)
    const idx = elements.value.findIndex(e => e.id === elementId)
    if (idx >= 0) elements.value[idx] = res.data
    return res.data
  }

  async function deleteElement(fwId: number, elementId: string) {
    await frameworkApi.deleteElement(fwId, elementId)
    elements.value = elements.value.filter(e => e.id !== elementId)
    await refreshSummary(fwId)
  }

  /** 重新拉取框架汇总，使顶部统计卡片（元素数/章节数/冲突数）即时更新。 */
  async function refreshSummary(fwId: number) {
    try {
      const res = await frameworkApi.get(fwId)
      framework.value = res.data
    } catch { /* 汇总刷新失败不影响主流程 */ }
  }

  async function fetchConflicts(fwId: number) {
    const res = await frameworkApi.listConflicts(fwId)
    conflicts.value = res.data
  }

  async function resolveConflict(fwId: number, conflictId: number, action: string, patchContent?: string) {
    const res = await frameworkApi.resolveConflict(fwId, conflictId, { action, patchContent })
    const idx = conflicts.value.findIndex(c => c.id === conflictId)
    if (idx >= 0) conflicts.value[idx] = res.data
    return res.data
  }

  async function fetchEvents(fwId: number) {
    const res = await frameworkApi.listEvents(fwId)
    events.value = res.data
  }

  async function finishFramework(fwId: number) {
    const res = await frameworkApi.finish(fwId)
    framework.value = res.data
  }

  return {
    framework, elements, chapters, conflicts, events, loading,
    openFramework, fetchFramework,
    createElement, updateElement, deleteElement, refreshSummary,
    fetchConflicts, resolveConflict,
    fetchEvents, finishFramework,
  }
})
