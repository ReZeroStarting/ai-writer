<template>
  <div class="toolbar" v-if="editor">
    <button @click="editor.chain().focus().toggleBold().run()" :class="{ active: editor.isActive('bold') }"><b>B</b></button>
    <button @click="editor.chain().focus().toggleItalic().run()" :class="{ active: editor.isActive('italic') }"><i>I</i></button>
    <button @click="editor.chain().focus().toggleUnderline().run()" :class="{ active: editor.isActive('underline') }"><u>U</u></button>
    <span class="sep" />
    <button @click="editor.chain().focus().toggleHeading({ level: 1 }).run()" :class="{ active: editor.isActive('heading', { level: 1 }) }">H1</button>
    <button @click="editor.chain().focus().toggleHeading({ level: 2 }).run()" :class="{ active: editor.isActive('heading', { level: 2 }) }">H2</button>
    <span class="sep" />
    <button @click="editor.chain().focus().toggleBlockquote().run()" :class="{ active: editor.isActive('blockquote') }">引用</button>
    <button @click="editor.chain().focus().toggleBulletList().run()">列表</button>
    <button @click="editor.chain().focus().toggleCodeBlock().run()">代码</button>
  </div>
</template>

<script setup lang="ts">
import type { Editor } from '@tiptap/vue-3'
defineProps<{ editor: Editor | null | undefined }>()
</script>

<style scoped>
.toolbar { display: flex; gap: 2px; padding: 8px 12px; border-bottom: 1px solid var(--el-border-color, #e4e7ed); background: var(--el-bg-color-page, #fafafa); flex-wrap: wrap; }
.toolbar button { padding: 4px 10px; border: 1px solid var(--el-border-color, #dcdfe6); border-radius: 4px; background: var(--el-bg-color, #fff); cursor: pointer; font-size: 13px; color: var(--el-text-color-regular, #606266); }
.toolbar button:hover { border-color: var(--el-color-primary, #409eff); color: var(--el-color-primary, #409eff); }
.toolbar button.active { background: var(--el-color-primary, #409eff); color: #fff; border-color: var(--el-color-primary, #409eff); }
.sep { width: 1px; background: var(--el-border-color, #dcdfe6); margin: 0 4px; }
</style>
