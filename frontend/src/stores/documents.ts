import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
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
    try {
      await docsApi.update(id, { title, content })
    } catch (e: any) {
      // 409 = 乐观锁冲突：同一个文档在别的标签页/设备上被改过。
      // 不能静默失败，也不能直接覆盖——提示用户并拉回最新版本。
      if (e?.response?.status === 409) {
        ElMessage.warning('内容已在别处被修改，已载入最新版本，请确认后重新编辑')
        await fetchOne(id)
        return
      }
      throw e
    }
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
