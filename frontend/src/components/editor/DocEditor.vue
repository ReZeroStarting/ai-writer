<template>
  <div class="doc-editor" v-if="doc">
    <div class="doc-header">
      <el-input v-model="title" size="large" placeholder="文档标题" @blur="autoSave" />
    </div>
    <div class="doc-body">
      <TiptapEditor v-model="content" />
      <AIPanel :editor-content="plainText" @insert="handleInsert" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useDocStore } from '@/stores/documents'
import TiptapEditor from './TiptapEditor.vue'
import AIPanel from './AIPanel.vue'

const props = defineProps<{ docId: number }>()
const store = useDocStore()

const title = ref('')
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

onMounted(async () => {
  await store.fetchOne(props.docId)
  if (store.currentDoc) {
    title.value = store.currentDoc.title
    content.value = store.currentDoc.content || ''
  }
})

let saveTimer: ReturnType<typeof setTimeout>
function autoSave() {
  clearTimeout(saveTimer)
  saveTimer = setTimeout(() => {
    store.save(props.docId, title.value, content.value)
  }, 3000)
}

watch([title, content], autoSave)

function handleInsert(text: string) { content.value = content.value + text }
</script>

<style scoped>
.doc-editor { height: 100%; display: flex; flex-direction: column; }
.doc-header { padding: 12px 16px; border-bottom: 1px solid var(--el-border-color, #e4e7ed); }
.doc-body { flex: 1; display: flex; overflow: hidden; }
</style>
