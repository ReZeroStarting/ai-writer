import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { StyleTemplate } from '@/types'
import { stylesApi } from '@/api/styles'

export const useStyleStore = defineStore('styles', () => {
  const styles = ref<StyleTemplate[]>([])

  async function fetch() {
    const res = await stylesApi.list()
    styles.value = res.data
  }

  async function create(name: string, systemPrompt: string, description?: string) {
    const res = await stylesApi.create({ name, systemPrompt, description })
    styles.value.unshift(res.data)
    return res.data
  }

  async function remove(id: number) {
    await stylesApi.remove(id)
    styles.value = styles.value.filter(s => s.id !== id)
  }

  return { styles, fetch, create, remove }
})
