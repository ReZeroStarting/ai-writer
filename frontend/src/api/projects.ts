import client from './client'
import type { Project } from '@/types'

export const projectsApi = {
  list: () => client.get<Project[]>('/projects'),
  create: (data: { name: string; description?: string }) => client.post<Project>('/projects', data),
  update: (id: number, data: { name: string; description?: string }) => client.put<Project>(`/projects/${id}`, data),
  remove: (id: number) => client.delete(`/projects/${id}`),
}
