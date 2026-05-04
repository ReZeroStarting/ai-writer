<template>
  <el-dialog v-model="visible" :title="'上传素材学习 — ' + styleName" width="600px">
    <el-form>
      <el-form-item label="素材标题"><el-input v-model="title" placeholder="例如：《百年孤独》节选" /></el-form-item>
      <el-form-item label="文本内容">
        <el-input v-model="content" type="textarea" :rows="10" placeholder="粘贴小说原文或文本片段，或点击下方按钮上传文件..." />
        <div class="upload-row">
          <input ref="fileInput" type="file" accept=".txt,.md,.text,.epub" style="display:none" @change="handleFileUpload" />
          <el-button size="small" @click="fileInput?.click()">📁 上传文件</el-button>
          <span class="upload-hint">支持 .txt / .md / .epub 文件</span>
        </div>
      </el-form-item>
    </el-form>
    <div v-if="job" class="job-status">
      <el-tag v-if="job.status === 'PENDING'" type="info">等待中...</el-tag>
      <el-tag v-else-if="job.status === 'PROCESSING'" type="warning">AI 正在分析...</el-tag>
      <el-tag v-else-if="job.status === 'COMPLETED'" type="success">学习完成！</el-tag>
      <el-tag v-else-if="job.status === 'FAILED'" type="danger">失败：{{ job.errorMsg }}</el-tag>
    </div>
    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button type="primary" :loading="loading" @click="handleLearn">开始学习</el-button>
    </template>
  </el-dialog>

  <el-button size="small" @click="visible = true" style="margin-top: 8px;">📖 上传素材学习</el-button>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { stylesApi } from '@/api/styles'
import JSZip from 'jszip'
import type { LearningJob } from '@/types'

const props = defineProps<{ styleId: number; styleName: string }>()

const visible = ref(false); const loading = ref(false)
const title = ref(''); const content = ref('')
const rawLength = ref(0)
const job = ref<LearningJob | null>(null)

const fileInput = ref<HTMLInputElement | null>(null)
const MAX_CHARS = 15000
const PASSAGE_LEN = 3000

function smartSample(text: string): string {
  if (text.length <= MAX_CHARS) return text
  const positions = [0, 0.2, 0.4, 0.6, 0.8, 1]
  const passages: string[] = []
  const len = text.length
  for (const pos of positions) {
    let start = Math.floor(pos * (len - PASSAGE_LEN))
    if (start < 0) start = 0
    let passage = text.slice(start, start + PASSAGE_LEN).trim()
    const breakIdx = Math.max(passage.lastIndexOf('\n'), passage.lastIndexOf('。'), passage.lastIndexOf('.'))
    if (breakIdx > PASSAGE_LEN * 0.6) {
      passage = passage.slice(0, breakIdx + 1)
    }
    if (passage.length > 200) passages.push(passage)
  }
  return passages.join('\n\n---\n\n')
}

function setContent(text: string, sourceName?: string) {
  rawLength.value = text.length
  content.value = smartSample(text)
  if (sourceName && !title.value.trim()) {
    title.value = sourceName.replace(/\.[^.]+$/, '')
  }
}

function htmlToText(html: string): string {
  const el = document.createElement('div')
  el.innerHTML = html
  return (el.textContent || '').replace(/\s+/g, ' ').trim()
}

async function handleEpub(file: File) {
  const buf = await file.arrayBuffer()
  const zip = await JSZip.loadAsync(buf)
  const htmlFiles = Object.keys(zip.files).filter(
    f => f.endsWith('.html') || f.endsWith('.xhtml') || f.endsWith('.htm')
  )
  // Sort by content length (longest chapters are likely the main content)
  const chapterTexts: { name: string; text: string }[] = []
  for (const name of htmlFiles) {
    const html = await zip.files[name].async('string')
    const text = htmlToText(html)
    if (text && text.length > 200) chapterTexts.push({ name, text })
  }
  chapterTexts.sort((a, b) => b.text.length - a.text.length)
  // Take top chapters, up to ~50000 chars raw, then smart-sample
  let combined = ''
  for (const ch of chapterTexts) {
    if (combined.length > 50000) break
    combined += ch.text + '\n\n'
  }
  setContent(combined, file.name)
}

function handleFileUpload(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  if (file.name.endsWith('.epub')) {
    handleEpub(file)
    return
  }

  const reader = new FileReader()
  reader.onload = () => {
    setContent(reader.result as string, file.name)
  }
  reader.readAsText(file, 'UTF-8')
}

async function handleLearn() {
  if (!title.value.trim() || !content.value.trim()) { ElMessage.warning('请填写标题和内容'); return }
  loading.value = true
  try {
    const res = await stylesApi.learn(props.styleId, { title: title.value, content: content.value })
    job.value = res.data
    const timer = setInterval(async () => {
      const jobs = await stylesApi.getJobs(props.styleId)
      const latest = jobs.data[0]
      if (latest) {
        job.value = latest
        if (latest.status === 'COMPLETED' || latest.status === 'FAILED') {
          clearInterval(timer)
          if (latest.status === 'COMPLETED') ElMessage.success('风格学习完成！')
        }
      }
    }, 2000)
  } catch (e: any) { ElMessage.error('学习请求失败') }
  finally { loading.value = false }
}
</script>

<style scoped>
.job-status { margin-top: 12px; }
.upload-row { margin-top: 8px; display: flex; align-items: center; gap: 8px; }
.upload-hint { font-size: 12px; color: var(--el-text-color-secondary, #909399); }
</style>
