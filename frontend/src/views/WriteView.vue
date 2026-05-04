<template>
  <div class="write-view">
    <!-- Context Bar -->
    <div class="context-bar">
      <el-tag v-for="stat in contextStats" :key="stat.label" size="small">
        {{ stat.label }}: {{ stat.count }}
      </el-tag>
      <el-button text size="small" @click="showContextDetail = !showContextDetail">
        {{ showContextDetail ? '收起' : '展开' }}详情
      </el-button>
    </div>

    <div class="write-layout">
      <!-- Chapter Panel -->
      <div class="chapter-panel">
        <h4>章节列表</h4>
        <div v-for="ch in chapters" :key="ch.id" class="chapter-item">
          <span class="ch-index">第{{ ch.chapterIndex }}章</span>
          <el-tag :type="statusTag(ch.status)" size="small">{{ statusLabel(ch.status) }}</el-tag>
        </div>
        <el-empty v-if="!chapters.length" description="暂无章节" />
      </div>

      <!-- Writing Canvas -->
      <div class="writing-canvas">
        <div v-if="!isWriting && !currentContent" class="canvas-empty">
          <el-empty description="准备开始写作">
            <el-button type="primary" size="large" @click="startWriting">开始写作</el-button>
          </el-empty>
        </div>
        <div v-else class="canvas-content">
          <div class="content-display">{{ currentContent }}</div>
          <div v-if="isWriting" class="writing-indicator">
            <el-icon class="is-loading"><Loading /></el-icon> AI 正在写作...
          </div>
        </div>
      </div>
    </div>

    <!-- Controls -->
    <div class="write-controls">
      <template v-if="!isWriting">
        <el-button type="primary" @click="startWriting">开始写作</el-button>
        <el-button @click="continueWriting" :disabled="!currentContent">继续下一章</el-button>
      </template>
      <template v-else>
        <el-button @click="pauseWriting">暂停</el-button>
      </template>
      <el-button @click="showIntervene = true" :disabled="!currentContent">插入内容</el-button>
      <el-button type="danger" plain @click="handleFinish">完结</el-button>
    </div>

    <!-- Intervene Dialog -->
    <el-dialog v-model="showIntervene" title="插入内容或指令" width="600px">
      <el-input v-model="interveneText" type="textarea" :rows="8" placeholder="输入你要插入的内容，或给 AI 的指令..." />
      <template #footer>
        <el-button @click="showIntervene = false">取消</el-button>
        <el-button type="primary" @click="handleIntervene">提交并续写</el-button>
      </template>
    </el-dialog>

    <!-- Conflict Notifier -->
    <div v-if="newConflicts.length > 0" class="conflict-notifier" @click="goConflicts">
      <el-icon><WarningFilled /></el-icon>
      检测到 {{ newConflicts.length }} 个设定冲突，点击处理
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useFrameworkStore } from '@/stores/framework'
import { frameworkApi } from '@/api/framework'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading, WarningFilled } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const fwStore = useFrameworkStore()

const elements = computed(() => fwStore.elements)
const chapters = ref<any[]>([])
const currentContent = ref('')
const currentChapterId = ref<number | null>(null)
const isWriting = ref(false)
const showIntervene = ref(false)
const showContextDetail = ref(false)
const interveneText = ref('')
const newConflicts = ref<any[]>([])

const contextStats = computed(() => {
  const grouped: Record<string, number> = {}
  elements.value.forEach(e => {
    const label = e.type
    grouped[label] = (grouped[label] || 0) + 1
  })
  return Object.entries(grouped).map(([label, count]) => ({ label, count }))
})

function statusTag(s: string) {
  const map: Record<string, string> = { DRAFT: 'info', AI_GENERATING: 'warning', AI_GENERATED: 'success', USER_EDITING: '', COMPLETED: 'primary' }
  return map[s] ?? 'info'
}
function statusLabel(s: string) {
  const map: Record<string, string> = { DRAFT: '草稿', AI_GENERATING: '生成中', AI_GENERATED: '已生成', USER_EDITING: '编辑中', COMPLETED: '已完成' }
  return map[s] ?? s
}

async function startWriting() {
  const fwId = fwStore.framework?.id
  if (!fwId) return
  isWriting.value = true
  currentContent.value = ''
  try {
    // Fetch latest chapter to get its ID
    try {
      const chRes = await frameworkApi.getLatestChapter(fwId)
      currentChapterId.value = chRes.data.id
    } catch { currentChapterId.value = null }

    const response = await frameworkApi.startWriting(fwId)
    const reader = response.data.getReader()
    const decoder = new TextDecoder()
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      currentContent.value += decoder.decode(value, { stream: true })
    }
    // Save content after stream completes
    if (currentChapterId.value) {
      try {
        await frameworkApi.saveChapter(fwId, currentChapterId.value, currentContent.value)
      } catch { /* save failure is non-fatal */ }
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || 'AI 写作失败')
  }
  isWriting.value = false
}

async function continueWriting() {
  await startWriting()
}

function pauseWriting() {
  isWriting.value = false
  ElMessage.info('已暂停')
}

async function handleIntervene() {
  if (!interveneText.value.trim()) return
  showIntervene.value = false
  const fwId = fwStore.framework?.id
  if (!fwId) return
  try {
    await frameworkApi.intervene(fwId, { content: interveneText.value })
    currentContent.value += '\n\n' + interveneText.value
    interveneText.value = ''
    await startWriting()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '介入失败')
  }
}

async function handleFinish() {
  try {
    await ElMessageBox.confirm('确定要完结此框架吗？', '确认完结', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    if (fwStore.framework) await fwStore.finishFramework(fwStore.framework.id)
    ElMessage.success('框架已完结')
  } catch { /* cancelled */ }
}

function goConflicts() {
  router.push({ name: 'framework-conflicts', params: { id: route.params.id } })
}
</script>

<style scoped>
.write-view { display: flex; flex-direction: column; height: calc(100vh - 200px); }
.context-bar { display: flex; gap: 8px; padding: 8px 0; border-bottom: 1px solid var(--el-border-color); flex-wrap: wrap; }
.write-layout { flex: 1; display: flex; overflow: hidden; }
.chapter-panel { width: 200px; border-right: 1px solid var(--el-border-color); padding: 12px; overflow-y: auto; }
.chapter-item { padding: 8px 0; border-bottom: 1px solid var(--el-border-color-lighter); display: flex; justify-content: space-between; align-items: center; }
.writing-canvas { flex: 1; padding: 20px; overflow-y: auto; }
.canvas-empty { display: flex; justify-content: center; align-items: center; height: 100%; }
.canvas-content { white-space: pre-wrap; line-height: 1.8; font-size: 16px; }
.writing-indicator { display: flex; align-items: center; gap: 8px; color: var(--el-color-primary); margin-top: 16px; }
.write-controls { display: flex; gap: 12px; padding: 12px 0; border-top: 1px solid var(--el-border-color); justify-content: center; }
.conflict-notifier { position: fixed; right: 24px; bottom: 80px; background: var(--el-color-danger); color: white; padding: 12px 20px; border-radius: 8px; cursor: pointer; display: flex; align-items: center; gap: 8px; z-index: 1000; animation: slideUp 0.3s ease; }
@keyframes slideUp { from { transform: translateY(20px); opacity: 0; } to { transform: translateY(0); opacity: 1; } }
</style>
