import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Document } from '@/types'
import { docsApi } from '@/api/documents'

export const useDocStore = defineStore('documents', () => {
  const docs = ref<Document[]>([])
  const currentDoc = ref<Document | null>(null)
  const loading = ref(false)

  async function fetchByProject(projectId: number) {
    loading.value = true
    try { const res = await docsApi.list(projectId); docs.value = res.data }
    finally { loading.value = false }
  }

  async function fetchOne(id: number) {
    const res = await docsApi.get(id); currentDoc.value = res.data
  }

  async function create(projectId: number, title: string) {
    const res = await docsApi.create(projectId, { title, content: '' })
    docs.value.unshift(res.data); return res.data
  }

  async function save(id: number, title: string, content: string) {
    await docsApi.update(id, { title, content })
    const d = docs.value.find(d => d.id === id)
    if (d) { d.title = title; d.content = content }
    if (currentDoc.value?.id === id) { currentDoc.value.title = title; currentDoc.value.content = content }
  }

  async function remove(id: number) {
    await docsApi.remove(id)
    docs.value = docs.value.filter(d => d.id !== id)
    if (currentDoc.value?.id === id) currentDoc.value = null
  }

  return { docs, currentDoc, loading, fetchByProject, fetchOne, create, save, remove }
})
