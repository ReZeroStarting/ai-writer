<template>
  <div class="doc-editor" v-if="doc">
    <div class="doc-body">
      <TiptapEditor v-model="content" />
      <AIPanel :editor-content="plainText" @insert="handleInsert" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useDocStore } from '@/stores/documents'
import TiptapEditor from './TiptapEditor.vue'
import AIPanel from './AIPanel.vue'

const props = defineProps<{ docId: number }>()
const store = useDocStore()

const content = ref('')

const doc = computed(() => store.currentDoc)

let htmlToText: (html: string) => string
function getHtmlToText() {
  if (!htmlToText) {
    const el = document.createElement('div')
    htmlToText = (html: string) => {
      el.innerHTML = html
      return el.textContent || ''
    }
  }
  return htmlToText
}

const plainText = computed(() => getHtmlToText()(content.value))

const title = computed(() => {
  const text = plainText.value.trim()
  if (!text) return '未命名章节'
  return text.split('\n')[0].slice(0, 60)
})

// Optimistically update sidebar title as user types
watch(title, (t) => {
  const d = store.docs.find(d => d.id === props.docId)
  if (d) d.title = t
})

let previousId = 0

watch(() => props.docId, async (id) => {
  // Flush pending save for previous doc before switching
  if (previousId && previousId !== id) {
    clearTimeout(saveTimer)
    await store.save(previousId, title.value, content.value)
  }
  await store.fetchOne(id)
  if (store.currentDoc) {
    content.value = store.currentDoc.content || store.currentDoc.title || ''
  }
  previousId = id
}, { immediate: true })

let saveTimer: ReturnType<typeof setTimeout>
function autoSave() {
  clearTimeout(saveTimer)
  saveTimer = setTimeout(() => {
    store.save(props.docId, title.value, content.value)
  }, 3000)
}

watch(content, autoSave)

function handleInsert(text: string) { content.value = content.value + text }
</script>

<style scoped>
.doc-editor { height: 100%; display: flex; flex-direction: column; }
.doc-body { flex: 1; display: flex; overflow: hidden; }
</style>
