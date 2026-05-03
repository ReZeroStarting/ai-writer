import client from './client'
import type { StyleTemplate, LearningJob } from '@/types'

export const stylesApi = {
  list: () => client.get<StyleTemplate[]>('/styles'),
  create: (data: { name: string; systemPrompt: string; description?: string }) =>
    client.post<StyleTemplate>('/styles', data),
  remove: (id: number) => client.delete(`/styles/${id}`),
  learn: (id: number, data: { title: string; content: string }) =>
    client.post<LearningJob>(`/styles/${id}/learn`, data),
  getJobs: (id: number) => client.get<LearningJob[]>(`/styles/${id}/jobs`),
}
