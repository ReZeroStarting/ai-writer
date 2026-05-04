import client from './client'
import type { Framework, FrameworkElement, Chapter, WritingEvent, Conflict } from '@/types'

export const frameworkApi = {
  open: (projectId: number) => client.post<Framework>(`/v1/projects/${projectId}/framework`),
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

  // Writing
  startWriting: (fwId: number) =>
    client.post(`/v1/frameworks/${fwId}/write/start`, null, { responseType: 'stream', adapter: 'fetch' }),
  continueWriting: (fwId: number) =>
    client.post(`/v1/frameworks/${fwId}/write/continue`, null, { responseType: 'stream', adapter: 'fetch' }),
  intervene: (fwId: number, data: { content: string; position?: string }) =>
    client.post(`/v1/frameworks/${fwId}/write/interrupt`, data),

  // Conflicts
  listConflicts: (fwId: number) => client.get<Conflict[]>(`/v1/frameworks/${fwId}/conflicts`),
  resolveConflict: (fwId: number, conflictId: number, data: { action: string; patchContent?: string }) =>
    client.post<Conflict>(`/v1/frameworks/${fwId}/conflicts/${conflictId}/resolve`, data),
  getImpact: (fwId: number, elementId: string) =>
    client.post<number[]>(`/v1/frameworks/${fwId}/elements/${elementId}/impact`),

  // Events
  listEvents: (fwId: number) => client.get<WritingEvent[]>(`/v1/frameworks/${fwId}/events`),
}
