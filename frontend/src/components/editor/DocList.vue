<template>
  <div class="doc-list">
    <div class="doc-list-header">
      <span>文档</span>
      <el-button size="small" @click="handleCreate">+ 新建</el-button>
    </div>
    <div v-for="doc in store.docs" :key="doc.id"
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
import { computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useDocStore } from '@/stores/documents'
import { ElMessage } from 'element-plus'
import { MoreFilled } from '@element-plus/icons-vue'

const props = defineProps<{ projectId: number }>()
const route = useRoute(); const router = useRouter()
const store = useDocStore()
const activeId = computed(() => Number(route.params.docId))

watch(() => props.projectId, (id) => store.fetchByProject(id), { immediate: true })

async function handleCreate() {
  const doc = await store.create(props.projectId, '未命名文档')
  router.push(`/project/${props.projectId}/doc/${doc.id}`)
}

async function handleDelete(id: number) {
  await store.remove(id)
  ElMessage.success('文档已删除')
  if (activeId.value === id) router.push(`/project/${props.projectId}`)
}
</script>

<style scoped>
.doc-list { width: 240px; border-right: 1px solid #e4e7ed; background: #fafafa; overflow-y: auto; }
.doc-list-header { padding: 12px; display: flex; justify-content: space-between; align-items: center; font-weight: 600; }
.doc-item { padding: 10px 12px; cursor: pointer; display: flex; justify-content: space-between; align-items: center; }
.doc-item:hover { background: #ecf5ff; }
.doc-item.active { background: #d9ecff; }
.doc-main { flex: 1; min-width: 0; }
.doc-title { display: block; font-size: 14px; }
.doc-date { font-size: 11px; color: #909399; }
</style>
