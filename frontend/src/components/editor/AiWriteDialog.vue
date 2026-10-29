<template>
  <el-dialog v-model="visible" :title="isImitate ? 'AI 仿写' : 'AI 自动写作'" width="600px" @close="reset">
    <el-form label-position="top">
      <el-form-item :label="isImitate ? '参考文本（AI 模仿其风格）' : '写作主题'">
        <el-input v-model="content" type="textarea" :rows="isImitate ? 6 : 3"
          :placeholder="isImitate ? '粘贴一段你想模仿风格的文字……' : '描述你想写的故事，例如：一个少年在雨夜中发现了一座废弃的古堡……'" />
      </el-form-item>
      <el-form-item label="主题要求" v-if="isImitate">
        <el-input v-model="topic" placeholder="用上述风格写什么？例如：写一段武侠打斗场景" />
      </el-form-item>
      <el-form-item label="选择风格（可选）">
        <el-select v-model="styleId" placeholder="不选则自由发挥" clearable style="width:100%">
          <el-option v-for="s in styleStore.styles" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="额外要求（可选）">
        <el-input v-model="instruction" placeholder="例如：800字左右，第一人称，悬疑风格" />
      </el-form-item>
    </el-form>

    <div v-if="streaming || outputText" class="dialog-output">
      <div class="output-header">{{ streaming ? 'AI 生成中...' : 'AI 生成结果' }}</div>
      <div class="output-content" v-text="outputText"></div>
    </div>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button type="primary" :loading="streaming" @click="handleGenerate">
        {{ streaming ? '生成中...' : '开始生成' }}
      </el-button>
      <el-button v-if="outputText && !streaming" type="success" @click="handleInsert">
        插入到文档
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { aiApi } from '@/api/ai'
import { useStyleStore } from '@/stores/styles'
import { streamSseText, isUpstreamErrorNotice } from '@/utils/sse'

const props = defineProps<{ modelValue?: boolean }>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'insert', text: string): void
}>()

const visible = ref(props.modelValue)
watch(() => props.modelValue, v => { visible.value = v })
watch(visible, v => emit('update:modelValue', v))

const styleStore = useStyleStore()
onMounted(() => styleStore.fetch())

const isImitate = ref(false)
const content = ref('')
const topic = ref('')
const styleId = ref<number | undefined>()
const instruction = ref('')
const streaming = ref(false)
const outputText = ref('')

function open(mode: 'auto' | 'imitate') {
  reset()
  isImitate.value = mode === 'imitate'
  visible.value = true
}

function reset() {
  content.value = ''
  topic.value = ''
  styleId.value = undefined
  instruction.value = ''
  outputText.value = ''
  streaming.value = false
}

async function handleGenerate() {
  if (!content.value.trim()) {
    ElMessage.warning(isImitate.value ? '请粘贴参考文本' : '请输入写作主题')
    return
  }
  if (isImitate.value && !topic.value.trim()) {
    ElMessage.warning('请输入主题要求')
    return
  }

  streaming.value = true
  outputText.value = ''

  try {
    const endpoint = isImitate.value ? 'imitateWrite' : 'autoWrite'
    const reqContent = isImitate.value ? content.value : content.value
    const reqInstruction = isImitate.value ? topic.value : instruction.value

    const response = await (aiApi as any)[endpoint]({
      content: reqContent,
      styleTemplateId: styleId.value,
      instruction: reqInstruction || undefined,
    })
    // 后端按 SSE 帧返回，必须解析后再拼接，否则会把 "data:" 协议头当正文
    await streamSseText(response.data, (text) => { outputText.value += text })
    // 上游失败时后端把原因当文本帧推下来，不能当成正文
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

/** 转义 HTML 特殊字符，防止 AI 输出里的标签被当作 HTML 执行/持久化。 */
function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}
function handleInsert() {
  // AI 输出为纯文本：转义后再插入，换行转 <br> 保留排版
  emit('insert', escapeHtml(outputText.value).replace(/\n/g, '<br>'))
  outputText.value = ''
  visible.value = false
}

defineExpose({ open })
</script>

<style scoped>
.dialog-output { margin-top: 12px; }
.output-header { font-size: 13px; color: var(--el-color-primary, #409eff); margin-bottom: 8px; }
.output-content {
  background: var(--el-bg-color, #fff);
  border: 1px solid var(--el-border-color, #e4e7ed);
  border-radius: 4px; padding: 8px;
  min-height: 80px; max-height: 300px; overflow-y: auto;
  font-size: 14px; line-height: 1.7;
  color: var(--el-text-color-primary, #303133);
  white-space: pre-wrap; word-break: break-word;
}
</style>
