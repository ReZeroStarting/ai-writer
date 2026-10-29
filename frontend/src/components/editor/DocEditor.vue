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
import { ensureHtml, htmlToPlain } from '@/utils/richText'
import TiptapEditor from './TiptapEditor.vue'
import AIPanel from './AIPanel.vue'

const props = defineProps<{ docId: number }>()
const store = useDocStore()

const content = ref('')

const doc = computed(() => store.currentDoc)

// 用统一的行内转换：<p> 之间要变成换行，否则 textContent 会把整篇挤成一整行，
// 标题也会变成「第一章正文正文正文…」这种 60 字的糊
const plainText = computed(() => htmlToPlain(content.value))

const title = computed(() => {
  const text = plainText.value.trim()
  if (!text) return '未命名章节'
  // 模型爱在开头写 "## 第一章 xxx"，标题里就别带井号了
  return text.split('\n')[0].replace(/^#{1,6}\s+/, '').slice(0, 60)
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
    // 老数据 / 框架生成的章节可能是纯文本：转成段落再交给编辑器，否则换行被折叠
    content.value = ensureHtml(store.currentDoc.content || store.currentDoc.title || '')

    // 历史数据的标题是「第一章正文正文正文…」这种糊（当年用 textContent 取文本，
    // 段落之间没换行）。只在标题明显过长、且确实来自正文开头时才纠正，
    // 避免覆盖用户自己改过的标题。
    const stored = store.currentDoc.title || ''
    const squash = (s: string) => s.replace(/\s+/g, '')
    const looksDerived = stored.length >= 55 && squash(plainText.value).startsWith(squash(stored).slice(0, 20))
    if (looksDerived && stored !== title.value) {
      await store.save(id, title.value, content.value)
    }
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
