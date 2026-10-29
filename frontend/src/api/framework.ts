import client from './client'
import type { Framework, FrameworkElement, Chapter, WritingEvent, Conflict } from '@/types'

export const frameworkApi = {
  open: (projectId: number) => client.post<Framework>(`/v1/projects/${projectId}/framework`),
  getByProjectId: (projectId: number) => client.get<Framework>(`/v1/projects/${projectId}/framework`),
  get: (id: number) => client.get<Framework>(`/v1/frameworks/${id}`),
  finish: (id: number) => client.post<Framework>(`/v1/frameworks/${id}/finish`),

  // Elements
  listElements: (fwId: number, type?: string) =>
    client.get<FrameworkElement[]>(`/v1/frameworks/${fwId}/elements`, { params: { type } }),
  createElement: (fwId: number, data: { type: string; name: string; content: string }) =>
    client.post<FrameworkElement>(`/v1/frameworks/${fwId}/elements`, data),
  updateElement: (fwId: number, elementId: string, data: { type: string; name: string; content: string }) =>
    client.put<FrameworkElement>(`/v1/frameworks/${fwId}/elements/${elementId}`, data),
  deleteElement: (fwId: number, elementId: string) =>
    client.delete(`/v1/frameworks/${fwId}/elements/${elementId}`),
  getElementVersions: (fwId: number, elementId: string) =>
    client.get<FrameworkElement[]>(`/v1/frameworks/${fwId}/elements/${elementId}/versions`),
  aiGenerateElements: (fwId: number, data: { type: string; hint?: string }) =>
    client.post<FrameworkElement[]>(`/v1/frameworks/${fwId}/elements/ai-generate`, data),

  // Writing（SSE 流式：responseType/stream + fetch 适配器；timeout:0 关掉 30s 默认超时，
  // 否则长章节生成会在中途被掐断）
  startWriting: (fwId: number) =>
    client.post(`/v1/frameworks/${fwId}/write/start`, null, { responseType: 'stream', adapter: 'fetch', timeout: 0 }),
  continueWriting: (fwId: number) =>
    client.post(`/v1/frameworks/${fwId}/write/continue`, null, { responseType: 'stream', adapter: 'fetch', timeout: 0 }),
  /** 在当前章节后面接着写（不新开章、不重写） */
  continueChapter: (fwId: number) =>
    client.post(`/v1/frameworks/${fwId}/write/continue-chapter`, null, { responseType: 'stream', adapter: 'fetch', timeout: 0 }),
  intervene: (fwId: number, data: { content: string; position?: string }) =>
    client.post<Chapter>(`/v1/frameworks/${fwId}/write/interrupt`, data),
  saveChapter: (fwId: number, chapterId: number, content: string) =>
    client.post(`/v1/frameworks/${fwId}/write/save`, { chapterId, content }),
  getLatestChapter: (fwId: number) =>
    client.get<Chapter>(`/v1/frameworks/${fwId}/write/latest-chapter`),
  listChapters: (fwId: number) =>
    client.get<Chapter[]>(`/v1/frameworks/${fwId}/chapters`),

  // Conflicts
  listConflicts: (fwId: number) => client.get<Conflict[]>(`/v1/frameworks/${fwId}/conflicts`),
  resolveConflict: (fwId: number, conflictId: number, data: { action: string; patchContent?: string }) =>
    client.post<Conflict>(`/v1/frameworks/${fwId}/conflicts/${conflictId}/resolve`, data),
  getImpact: (fwId: number, elementId: string) =>
    client.post<number[]>(`/v1/frameworks/${fwId}/elements/${elementId}/impact`),

  // Events
  listEvents: (fwId: number) => client.get<WritingEvent[]>(`/v1/frameworks/${fwId}/events`),
}
