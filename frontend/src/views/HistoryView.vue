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
          <h4>{{ eventLabel(e.eventType) }}</h4>
          <pre v-if="e.payload" class="payload">{{ formatJson(e.payload) }}</pre>
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
function eventLabel(t: string) { return eventLabels[t] ?? t }
function eventIcon(t: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  if (t.includes('CONFLICT')) return 'danger'
  if (t.includes('ELEMENT')) return 'primary'
  if (t.includes('CHAPTER') || t.includes('AI')) return 'success'
  return 'info'
}
function formatJson(s: string) { try { return JSON.stringify(JSON.parse(s), null, 2) } catch { return s } }
</script>

<style scoped>
.history-view { padding: 16px; max-width: 800px; }
.payload { background: var(--el-fill-color-light); padding: 8px; border-radius: 4px; font-size: 12px; overflow-x: auto; }
</style>
