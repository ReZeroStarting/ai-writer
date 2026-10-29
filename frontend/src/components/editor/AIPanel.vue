<template>
  <div class="ai-panel">
    <el-tabs v-model="activeTab" class="panel-tabs">
      <el-tab-pane label="AI 助手" name="ai">
        <div class="ai-tools">
          <el-select v-model="styleId" placeholder="选择风格" clearable size="small" style="width:100%;margin-bottom:8px;">
            <el-option v-for="s in styleStore.styles" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>

          <div class="ai-btn-group">
            <el-button size="small" @click="callAi('generateOutline')">大纲生成</el-button>
            <el-button size="small" @click="callAi('continue')">续写</el-button>
            <el-button size="small" @click="callAi('polish')">改写润色</el-button>
            <el-button size="small" @click="callAi('expand')">扩写</el-button>
            <el-button size="small" @click="callAi('shorten')">缩写</el-button>
            <el-button size="small" type="primary" @click="writeDialog?.open('auto')">AI 自动写作</el-button>
            <el-button size="small" type="primary" @click="writeDialog?.open('imitate')">AI 仿写</el-button>
          </div>

          <el-input v-model="instruction" type="textarea" :rows="2" placeholder="额外指令（可选）" size="small" style="margin-top:8px;" />
        </div>

        <div v-if="streaming || outputText" class="ai-output">
          <div class="output-header">{{ streaming ? 'AI 生成中...' : 'AI 生成结果' }}</div>
          <div class="output-content" v-text="outputText"></div>
          <el-button size="small" type="success" @click="insertText" style="margin-top:8px;" :disabled="streaming">插入到文档</el-button>
        </div>
      </el-tab-pane>

      <el-tab-pane label="写作检查" name="check">
        <div class="check-panel" v-if="editorContent.trim().length > 50">
          <!-- Score -->
          <div class="score-section">
            <div class="score-ring" :class="scoreClass">{{ report.readabilityScore }}</div>
            <div class="score-label">综合评分</div>
          </div>

          <!-- Key metrics -->
          <div class="metrics-grid">
            <div class="metric">
              <span class="metric-value">{{ report.sentenceCount }}</span>
              <span class="metric-label">句子数</span>
            </div>
            <div class="metric">
              <span class="metric-value">{{ report.avgSentenceLen }}</span>
              <span class="metric-label">平均句长</span>
            </div>
            <div class="metric">
              <span class="metric-value">{{ report.adverbDensity }}<small>/千字</small></span>
              <span class="metric-label">副词密度</span>
            </div>
            <div class="metric">
              <span class="metric-value">{{ Math.round(report.dialogueRatio * 100) }}%</span>
              <span class="metric-label">对话占比</span>
            </div>
          </div>

          <!-- Suggestions -->
          <div v-if="report.suggestions.length" class="check-section">
            <div class="section-title">建议</div>
            <div v-for="(s, i) in report.suggestions" :key="i" class="suggestion-item" :class="{ 'suggestion-good': s.includes('良好') }">
              {{ s }}
            </div>
          </div>

          <!-- Long sentences -->
          <div v-if="report.longSentences.length" class="check-section">
            <div class="section-title">长句标记 ({{ report.longSentences.length }} 句 &gt;30字)</div>
            <div v-for="(s, i) in report.longSentences.slice(0, 5)" :key="i" class="long-sentence">
              <span class="len-badge">{{ s.length }}字</span> {{ s.text }}
            </div>
            <div v-if="report.longSentences.length > 5" class="more-hint">...还有 {{ report.longSentences.length - 5 }} 句</div>
          </div>

          <!-- Top words -->
          <div v-if="report.topWords.length" class="check-section">
            <div class="section-title">高频词汇</div>
            <div class="word-tags">
              <el-tag v-for="w in report.topWords.slice(0, 12)" :key="w.word" size="small" effect="plain">
                {{ w.word }} ×{{ w.count }}
              </el-tag>
            </div>
          </div>

          <!-- Cliches -->
          <div v-if="report.cliches.length" class="check-section">
            <div class="section-title">套话检测</div>
            <div class="word-tags">
              <el-tag v-for="c in report.cliches" :key="c.phrase" size="small" type="warning" effect="light">
                {{ c.phrase }} ×{{ c.count }}
              </el-tag>
            </div>
          </div>
        </div>

        <div v-else class="check-placeholder">
          输入至少 50 字后显示写作质量分析
        </div>
      </el-tab-pane>
    </el-tabs>

    <AiWriteDialog ref="writeDialog" @insert="handleInsert" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { aiApi } from '@/api/ai'
import { useStyleStore } from '@/stores/styles'
import { analyzeWriting, type WritingReport } from '@/utils/writingCheck'
import { streamSseText, isUpstreamErrorNotice } from '@/utils/sse'
import AiWriteDialog from './AiWriteDialog.vue'

const props = defineProps<{ editorContent: string }>()
const emit = defineEmits(['insert'])

const styleStore = useStyleStore()
const activeTab = ref('ai')
const styleId = ref<number | undefined>()
const instruction = ref('')
const streaming = ref(false)
const outputText = ref('')
const writeDialog = ref<InstanceType<typeof AiWriteDialog> | null>(null)

onMounted(() => styleStore.fetch())

const report = ref<WritingReport>(emptyReport())

function emptyReport(): WritingReport {
  return {
    sentenceCount: 0, avgSentenceLen: 0, longSentences: [],
    adverbDensity: 0, topAdverbs: [], topWords: [],
    avgParagraphLen: 0, longestParagraph: 0,
    dialogueRatio: 0, punctuationProfile: [],
    cliches: [], readabilityScore: 0, suggestions: [],
  }
}

watch(() => props.editorContent, (text) => {
  if (text.trim().length > 50) {
    report.value = analyzeWriting(text)
  }
})

const scoreClass = computed(() => {
  const s = report.value.readabilityScore
  if (s >= 75) return 'score-good'
  if (s >= 50) return 'score-ok'
  return 'score-bad'
})

async function callAi(endpoint: string) {
  if (!props.editorContent.trim()) { ElMessage.warning('请先输入一些内容'); return }
  streaming.value = true; outputText.value = ''
  try {
    const fn = (aiApi as any)[endpoint]
    const response = await fn({
      content: props.editorContent,
      styleTemplateId: styleId.value,
      instruction: instruction.value || undefined,
    })
    // 后端按 SSE 帧返回，必须解析后再拼接，否则会把 "data:" 协议头当正文
    await streamSseText(response.data, (text) => { outputText.value += text })
    // 上游失败时后端把原因当文本帧推下来，不能留在输出框里被「插入正文」
    if (isUpstreamErrorNotice(outputText.value)) {
      const notice = outputText.value.trim()
      outputText.value = ''
      throw new Error(notice)
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || 'AI 调用失败')
  }
  streaming.value = false
}

/**
 * 把 AI 纯文本输出安全地转成可插入编辑器的 HTML 片段：
 * 先转义 HTML 特殊字符（防止 <img onerror> 之类被当标签执行/持久化），
 * 再把换行转成 <br> 以保留排版。
 */
function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}
function toSafeHtml(text: string): string {
  return escapeHtml(text).replace(/\n/g, '<br>')
}
function insertText() { emit('insert', toSafeHtml(outputText.value)); outputText.value = '' }
function handleInsert(text: string) { emit('insert', text) }
</script>

<style scoped>
.ai-panel { width: 300px; border-left: 1px solid var(--el-border-color, #e4e7ed); background: var(--el-bg-color-page, #fafafa); display: flex; flex-direction: column; overflow: hidden; }
.panel-tabs { flex: 1; display: flex; flex-direction: column; }
.panel-tabs :deep(.el-tabs__header) { margin: 0 12px; }
.panel-tabs :deep(.el-tabs__content) { flex: 1; overflow-y: auto; padding: 0 12px 12px; }

/* AI tab */
.ai-tools { padding-top: 8px; }
.ai-btn-group { display: flex; flex-wrap: wrap; gap: 4px; }
.ai-output { margin-top: 12px; }
.output-header { font-size: 13px; color: var(--el-color-primary, #409eff); margin-bottom: 8px; }
.output-content { background: var(--el-bg-color, #fff); border: 1px solid var(--el-border-color, #e4e7ed); border-radius: 4px; padding: 8px; min-height: 80px; max-height: 300px; overflow-y: auto; font-size: 14px; line-height: 1.7; color: var(--el-text-color-primary, #303133); white-space: pre-wrap; word-break: break-word; }

/* Check tab */
.check-panel { display: flex; flex-direction: column; gap: 12px; padding-top: 4px; }
.check-placeholder { text-align: center; color: var(--el-text-color-secondary, #909399); padding: 40px 0; font-size: 13px; }

.score-section { text-align: center; }
.score-ring { display: inline-flex; align-items: center; justify-content: center; width: 56px; height: 56px; border-radius: 50%; font-size: 22px; font-weight: 700; border: 3px solid; }
.score-ring.score-good { color: #67c23a; border-color: #67c23a; background: #f0f9eb; }
.score-ring.score-ok { color: #e6a23c; border-color: #e6a23c; background: #fdf6ec; }
.score-ring.score-bad { color: #f56c6c; border-color: #f56c6c; background: #fef0f0; }
.score-label { font-size: 12px; color: var(--el-text-color-secondary, #909399); margin-top: 4px; }

.metrics-grid { display: flex; text-align: center; background: var(--el-bg-color, #fff); border: 1px solid var(--el-border-color-light, #e4e7ed); border-radius: 6px; padding: 8px 0; }
.metrics-grid .metric { flex: 1; }
.metric-value { display: block; font-size: 15px; font-weight: 600; color: var(--el-text-color-primary, #303133); }
.metric-value small { font-size: 10px; font-weight: 400; color: var(--el-text-color-secondary, #909399); }
.metric-label { font-size: 11px; color: var(--el-text-color-secondary, #909399); }

.check-section { background: var(--el-bg-color, #fff); border: 1px solid var(--el-border-color-light, #e4e7ed); border-radius: 6px; padding: 8px 10px; }
.section-title { font-size: 12px; font-weight: 600; color: var(--el-text-color-primary, #303133); margin-bottom: 6px; }

.suggestion-item { font-size: 12px; padding: 3px 0; color: var(--el-color-warning, #e6a23c); border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5); }
.suggestion-item:last-child { border-bottom: none; }
.suggestion-good { color: var(--el-color-success, #67c23a); }

.long-sentence { font-size: 12px; padding: 3px 0; border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5); line-height: 1.5; color: var(--el-text-color-regular, #606266); }
.long-sentence:last-child { border-bottom: none; }
.len-badge { display: inline-block; background: var(--el-color-warning-light-9, #fdf6ec); color: var(--el-color-warning, #e6a23c); font-size: 10px; padding: 0 4px; border-radius: 3px; margin-right: 4px; }
.more-hint { font-size: 11px; color: var(--el-text-color-secondary, #909399); }

.word-tags { display: flex; flex-wrap: wrap; gap: 4px; }
</style>
