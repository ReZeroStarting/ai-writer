import client from './client'
import type { Document } from '@/types'

export const docsApi = {
  list: (projectId: number) => client.get<Document[]>(`/projects/${projectId}/docs`),
  get: (id: number) => client.get<Document>(`/docs/${id}`),
  create: (projectId: number, data: { title: string; content?: string }) =>
    client.post<Document>(`/projects/${projectId}/docs`, data),
  update: (id: number, data: { title: string; content: string }) =>
    client.put<Document>(`/docs/${id}`, data),
  remove: (id: number) => client.delete(`/docs/${id}`),
}
