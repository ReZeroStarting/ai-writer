<template>
  <div class="history-view">
    <el-timeline v-if="fwStore.events.length > 0">
      <el-timeline-item
        v-for="e in fwStore.events"
        :key="e.id"
        :timestamp="e.timestamp"
        :type="eventIcon(e.eventType)"
        placement="top"
      >
        <el-card>
          <div class="event-head">
            <h4>{{ eventLabel(e.eventType) }}</h4>
            <p class="event-desc">{{ describe(e) }}</p>
          </div>
          <!-- 原始 JSON 默认收起：给排查用，不该是主要阅读内容 -->
          <el-collapse v-if="e.payload" class="event-raw">
            <el-collapse-item title="原始数据" :name="String(e.id)">
              <pre class="payload">{{ formatJson(e.payload) }}</pre>
            </el-collapse-item>
          </el-collapse>
        </el-card>
      </el-timeline-item>
    </el-timeline>
    <el-empty v-else description="暂无事件记录" />
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useFrameworkStore } from '@/stores/framework'
import { elementTypeLabel } from '@/types'

const route = useRoute()
const fwStore = useFrameworkStore()

onMounted(async () => {
  const fwId = fwStore.framework?.id
  if (fwId) await fwStore.fetchEvents(fwId)
})

const eventLabels: Record<string, string> = {
  ELEMENT_CREATED: '创建元素', ELEMENT_UPDATED: '更新元素', ELEMENT_DEPRECATED: '弃用元素',
  CHAPTER_STARTED: '开始章节', CHAPTER_AI_GENERATED: 'AI 生成章节完成', USER_INTERVENED: '用户介入',
  CONFLICT_DETECTED: '检测到冲突', CONFLICT_RESOLVED: '解决冲突', PATCH_APPLIED: '应用补丁',
  FRAMEWORK_FINISHED: '框架完结', CHAPTER_SUMMARY_GENERATED: '生成摘要', ELEMENT_AI_GENERATED: 'AI 生成元素',
}

const resolutionLabels: Record<string, string> = {
  ignore: '忽略', patch: '打补丁', rewrite: '重写章节',
}

function eventLabel(t: string) { return eventLabels[t] ?? t }

function eventIcon(t: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  if (t.includes('CONFLICT')) return 'danger'
  if (t.includes('ELEMENT')) return 'primary'
  if (t.includes('CHAPTER') || t.includes('AI')) return 'success'
  return 'info'
}

function parsePayload(e: any): any {
  try { return e.payload ? JSON.parse(e.payload) : {} } catch { return {} }
}

const shortId = (id: unknown) => (id ? String(id).slice(0, 8) : '?')
const clip = (s: unknown, n = 60) => {
  const t = String(s ?? '')
  return t.length > n ? t.slice(0, n) + '…' : t
}

/** 把 payload 翻译成一句人话——历史事件是给人看的，不该直接糊一坨 JSON。 */
function describe(e: any): string {
  const p = parsePayload(e)
  switch (e.eventType) {
    case 'CHAPTER_STARTED': {
      const n = p.elementSnapshot ? Object.keys(p.elementSnapshot).length : 0
      return `第 ${p.chapterIndex ?? '?'} 章开始写作${n ? `，锁定 ${n} 个设定版本` : ''}`
    }
    case 'CHAPTER_AI_GENERATED':
      return `章节 #${p.chapterId ?? '?'} 由 AI 写完`
    case 'CHAPTER_SUMMARY_GENERATED':
      return `生成章节摘要：${clip(p.summary, 40)}`
    case 'CONFLICT_DETECTED': {
      const who = p.elementId ? `设定 ${shortId(p.elementId)}` : '设定'
      const how = p.source === 'ai' ? 'AI 语义审校' : '设定版本比章节快照新'
      return `${p.severity === 'MAJOR' ? '大' : '小'}冲突 #${p.conflictId ?? '?'}，涉及${who}（${how}）`
    }
    case 'CONFLICT_RESOLVED':
      return `冲突 #${p.conflictId ?? '?'} 处理为「${resolutionLabels[p.resolution] ?? p.resolution}」`
    case 'PATCH_APPLIED':
      return `对冲突 #${p.conflictId ?? '?'} 应用补丁：${clip(p.patchContent, 40)}`
    case 'USER_INTERVENED':
      return '写作过程中插入了内容或指令'
    case 'FRAMEWORK_FINISHED':
      return '框架已完结'
    case 'ELEMENT_CREATED':
    case 'ELEMENT_AI_GENERATED':
      return `${elementTypeLabel(p.type ?? '')} ${p.name ?? ''}`.trim() || '新增了一个设定元素'
    case 'ELEMENT_UPDATED':
      return `设定 ${p.name ?? ''} 更新到 v${p.version ?? '?'}`
    case 'ELEMENT_DEPRECATED':
      return `设定 ${p.name ?? ''} 已弃用`
    default:
      return ''
  }
}

function formatJson(s: string) {
  try { return JSON.stringify(JSON.parse(s), null, 2) } catch { return s }
}
</script>

<style scoped>
.history-view { padding: 16px; max-width: 800px; }
.event-head { display: flex; flex-direction: column; gap: 6px; }
.event-head h4 { margin: 0; font-size: 15px; }
.event-desc { margin: 0; color: var(--el-text-color-regular, #606266); font-size: 13px; line-height: 1.7; }
.event-raw { margin-top: 8px; }
.event-raw :deep(.el-collapse-item__header) { font-size: 12px; color: var(--el-text-color-secondary, #909399); height: 32px; }
.payload { background: var(--el-fill-color-light); padding: 8px; border-radius: 4px; font-size: 12px; overflow-x: auto; margin: 0; }
</style>
