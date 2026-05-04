<template>
  <div class="doc-list">
    <div class="doc-list-header">
      <span>章节</span>
      <el-button size="small" @click="handleCreate">+ 新建</el-button>
    </div>
    <div class="search-box">
      <el-input v-model="search" placeholder="搜索..." size="small" clearable />
    </div>
    <div v-for="doc in filteredDocs" :key="doc.id"
      :class="['doc-item', { active: doc.id === activeId }]">
      <div class="doc-main" @click="$router.push(`/project/${projectId}/doc/${doc.id}`)">
        <span class="doc-title">{{ doc.title }}</span>
        <span class="doc-date">{{ doc.updatedAt.slice(0, 10) }}</span>
      </div>
      <el-dropdown trigger="click" @click.stop>
        <el-icon><MoreFilled /></el-icon>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="handleDelete(doc.id)">删除</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useDocStore } from '@/stores/documents'
import { ElMessage } from 'element-plus'
import { MoreFilled } from '@element-plus/icons-vue'

const props = defineProps<{ projectId: number }>()
const route = useRoute(); const router = useRouter()
const store = useDocStore()
const activeId = computed(() => Number(route.params.docId))
const search = ref('')

const filteredDocs = computed(() => {
  if (!search.value.trim()) return store.docs
  const q = search.value.trim().toLowerCase()
  return store.docs.filter(d => d.title.toLowerCase().includes(q))
})

watch(() => props.projectId, (id) => store.fetchByProject(id), { immediate: true })

async function handleCreate() {
  const doc = await store.create(props.projectId, '未命名章节')
  router.push(`/project/${props.projectId}/doc/${doc.id}`)
}

async function handleDelete(id: number) {
  await store.remove(id)
  ElMessage.success('章节已删除')
  if (activeId.value === id) router.push(`/project/${props.projectId}`)
}
</script>

<style scoped>
.doc-list { width: 180px; border-right: 1px solid var(--el-border-color, #e4e7ed); background: var(--el-bg-color-page, #fafafa); overflow-y: auto; }
.doc-list-header { padding: 12px; display: flex; justify-content: space-between; align-items: center; font-weight: 600; font-size: 14px; }
.search-box { padding: 0 8px 8px; }
.doc-item { padding: 8px 12px; cursor: pointer; display: flex; justify-content: space-between; align-items: center; font-size: 13px; }
.doc-item:hover { background: var(--el-color-primary-light-9, #ecf5ff); }
.doc-item.active { background: var(--el-color-primary-light-8, #d9ecff); }
.doc-main { flex: 1; min-width: 0; }
.doc-title { display: block; font-size: 14px; }
.doc-date { font-size: 11px; color: var(--el-text-color-secondary, #909399); }
</style>
