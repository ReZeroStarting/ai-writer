<template>
  <div class="tiptap-wrapper">
    <EditorToolbar :editor="editor" />
    <editor-content :editor="editor" class="tiptap-content" />
  </div>
</template>

<script setup lang="ts">
import { useEditor, EditorContent } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Underline from '@tiptap/extension-underline'
import Placeholder from '@tiptap/extension-placeholder'
import EditorToolbar from './EditorToolbar.vue'
import { watch, onBeforeUnmount } from 'vue'

const props = defineProps<{ modelValue: string }>()
const emit = defineEmits(['update:modelValue'])

const editor = useEditor({
  content: props.modelValue,
  extensions: [
    StarterKit, Underline,
    Placeholder.configure({ placeholder: '开始写作...' }),
  ],
  onUpdate: ({ editor }) => { emit('update:modelValue', editor.getHTML()) },
})

watch(() => props.modelValue, (val) => {
  if (editor.value && editor.value.getHTML() !== val) {
    editor.value.commands.setContent(val)
  }
})

onBeforeUnmount(() => editor.value?.destroy())
</script>

<style>
.tiptap-wrapper { border: 1px solid #e4e7ed; border-radius: 8px; overflow: hidden; min-height: 400px; flex: 1; display: flex; flex-direction: column; }
.tiptap-content { padding: 16px 24px; flex: 1; }
.tiptap-content .ProseMirror { outline: none; min-height: 300px; line-height: 1.8; }
.tiptap-content .ProseMirror p.is-editor-empty:first-child::before { content: attr(data-placeholder); color: #c0c4cc; float: left; height: 0; pointer-events: none; }
.tiptap-content h1 { font-size: 24px; margin: 12px 0 8px; }
.tiptap-content h2 { font-size: 20px; margin: 10px 0 6px; }
.tiptap-content blockquote { border-left: 3px solid #409eff; padding-left: 12px; color: #606266; }
.tiptap-content pre { background: #f5f7fa; padding: 12px; border-radius: 4px; overflow-x: auto; }
</style>
