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

const plainText = computed(() => {
  const div = document.createElement('div')
  div.innerHTML = content.value
  return div.textContent || ''
})

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
  }, 1000)
}

watch([title, content], autoSave)

function handleInsert(text: string) { content.value = content.value + text }
</script>

<style scoped>
.doc-editor { height: 100%; display: flex; flex-direction: column; }
.doc-header { padding: 12px 16px; border-bottom: 1px solid #e4e7ed; }
.doc-body { flex: 1; display: flex; overflow: hidden; }
</style>
