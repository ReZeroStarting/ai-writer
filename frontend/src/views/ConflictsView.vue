<template>
  <div class="conflicts-view">
    <el-radio-group v-model="statusFilter">
      <el-radio-button value="">全部</el-radio-button>
      <el-radio-button value="PENDING">待处理</el-radio-button>
      <el-radio-button value="PATCHED">已补丁</el-radio-button>
      <el-radio-button value="IGNORED">已忽略</el-radio-button>
      <el-radio-button value="REWRITTEN">已重写</el-radio-button>
    </el-radio-group>

    <div class="conflict-list">
      <el-card v-for="c in filteredConflicts" :key="c.id" class="conflict-card">
        <div class="conflict-header">
          <el-tag :type="c.severity === 'MAJOR' ? 'danger' : 'warning'">
            {{ c.severity === 'MAJOR' ? '大冲突' : '小冲突' }}
          </el-tag>
          <el-tag :type="statusMap[c.status]">{{ statusLabel(c.status) }}</el-tag>
        </div>
        <p>{{ c.description }}</p>
        <div class="conflict-meta">
          <span>元素 ID: {{ c.elementId }}</span>
          <span>章节: {{ c.chapterId }}</span>
          <span>版本: v{{ c.chapterRefVersion }} → v{{ c.currentVersion }}</span>
        </div>
        <div v-if="c.status === 'PENDING'" class="conflict-actions">
          <el-button size="small" @click="resolve(c.id, 'ignore')">忽略</el-button>
          <el-button size="small" type="primary" @click="showPatchDialog(c.id)">打补丁</el-button>
          <el-button size="small" type="danger" @click="resolve(c.id, 'rewrite')">重写章节</el-button>
        </div>
      </el-card>
    </div>

    <el-empty v-if="filteredConflicts.length === 0" description="暂无冲突" />

    <!-- Patch Dialog -->
    <el-dialog v-model="showPatch" title="打补丁" width="500px">
      <el-input v-model="patchContent" type="textarea" :rows="6" placeholder="输入补丁内容..." />
      <template #footer>
        <el-button @click="showPatch = false">取消</el-button>
        <el-button type="primary" @click="submitPatch">提交补丁</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useFrameworkStore } from '@/stores/framework'
import { ElMessage } from 'element-plus'

const route = useRoute()
const fwStore = useFrameworkStore()
const statusFilter = ref('')
const showPatch = ref(false)
const patchContent = ref('')
const patchingConflictId = ref<number | null>(null)

const statusMap: Record<string, string> = { PENDING: 'danger', PATCHED: 'success', IGNORED: 'info', REWRITTEN: 'warning' }

const filteredConflicts = computed(() => {
  let list = fwStore.conflicts
  if (statusFilter.value) list = list.filter(c => c.status === statusFilter.value)
  return list
})

function statusLabel(s: string) {
  const map: Record<string, string> = { PENDING: '待处理', PATCHED: '已补丁', IGNORED: '已忽略', REWRITTEN: '已重写' }
  return map[s] ?? s
}

function showPatchDialog(conflictId: number) {
  patchingConflictId.value = conflictId
  patchContent.value = ''
  showPatch.value = true
}

async function submitPatch() {
  const fwId = fwStore.framework?.id
  if (!fwId || !patchingConflictId.value) return
  try {
    await fwStore.resolveConflict(fwId, patchingConflictId.value, 'patch', patchContent.value)
    ElMessage.success('补丁已应用')
    showPatch.value = false
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '补丁应用失败')
  }
}

async function resolve(conflictId: number, action: string) {
  const fwId = fwStore.framework?.id
  if (!fwId) return
  try {
    await fwStore.resolveConflict(fwId, conflictId, action)
    ElMessage.success('处理完成')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '处理失败')
  }
}

onMounted(async () => {
  const fwId = fwStore.framework?.id
  if (fwId) await fwStore.fetchConflicts(fwId)
})
</script>

<style scoped>
.conflicts-view { padding: 16px; }
.conflict-list { margin-top: 16px; display: flex; flex-direction: column; gap: 12px; }
.conflict-header { display: flex; gap: 8px; margin-bottom: 8px; }
.conflict-meta { display: flex; gap: 16px; margin-top: 8px; font-size: 12px; color: var(--el-text-color-secondary); }
.conflict-actions { display: flex; gap: 8px; margin-top: 12px; justify-content: flex-end; }
</style>
