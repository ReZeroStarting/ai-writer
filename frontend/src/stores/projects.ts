import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Project } from '@/types'
import { projectsApi } from '@/api/projects'

export const useProjectStore = defineStore('projects', () => {
  const projects = ref<Project[]>([])
  const currentId = ref<number | null>(null)
  const loading = ref(false)

  async function fetch() {
    loading.value = true
    try { const res = await projectsApi.list(); projects.value = res.data }
    finally { loading.value = false }
  }

  async function create(name: string, description?: string) {
    const res = await projectsApi.create({ name, description })
    projects.value.unshift(res.data)
    return res.data
  }

  async function update(id: number, name: string, description?: string) {
    await projectsApi.update(id, { name, description })
    const p = projects.value.find(p => p.id === id)
    if (p) { p.name = name; p.description = description }
  }

  async function remove(id: number) {
    await projectsApi.remove(id)
    projects.value = projects.value.filter(p => p.id !== id)
    if (currentId.value === id) currentId.value = null
  }

  function setCurrent(id: number | null) { currentId.value = id }

  return { projects, currentId, loading, fetch, create, update, remove, setCurrent }
})
