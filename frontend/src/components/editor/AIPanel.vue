<template>
  <div class="ai-panel">
    <div class="ai-panel-header">AI 助手</div>
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
      </div>

      <el-input v-model="instruction" type="textarea" :rows="2" placeholder="额外指令（可选）" size="small" style="margin-top:8px;" />
    </div>

    <div v-if="streaming" class="ai-output">
      <div class="output-header">AI 生成中...</div>
      <div class="output-content" v-html="outputText"></div>
      <el-button size="small" type="success" @click="insertText" style="margin-top:8px;">插入到文档</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { aiApi } from '@/api/ai'
import { useStyleStore } from '@/stores/styles'

const props = defineProps<{ editorContent: string }>()
const emit = defineEmits(['insert'])

const styleStore = useStyleStore()
const styleId = ref<number | undefined>()
const instruction = ref('')
const streaming = ref(false)
const outputText = ref('')

onMounted(() => styleStore.fetch())

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
    const reader = response.data.getReader()
    const decoder = new TextDecoder()
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      outputText.value += decoder.decode(value, { stream: true })
    }
  } catch (e: any) { ElMessage.error('AI 调用失败') }
  streaming.value = false
}

function insertText() { emit('insert', outputText.value); outputText.value = '' }
</script>

<style scoped>
.ai-panel { width: 300px; border-left: 1px solid #e4e7ed; background: #fafafa; padding: 12px; overflow-y: auto; display: flex; flex-direction: column; }
.ai-panel-header { font-weight: 600; margin-bottom: 12px; }
.ai-btn-group { display: flex; flex-wrap: wrap; gap: 4px; }
.ai-output { margin-top: 12px; }
.output-header { font-size: 13px; color: #409eff; margin-bottom: 8px; }
.output-content { background: #fff; border: 1px solid #e4e7ed; border-radius: 4px; padding: 8px; min-height: 80px; max-height: 300px; overflow-y: auto; font-size: 14px; line-height: 1.7; }
</style>
