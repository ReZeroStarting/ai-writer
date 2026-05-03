<template>
  <div class="doc-list">
    <div class="doc-list-header">
      <span>文档</span>
      <el-button size="small" @click="handleCreate">+ 新建</el-button>
    </div>
    <div v-for="doc in store.docs" :key="doc.id"
      :class="['doc-item', { active: doc.id === activeId }]"
      @click="$router.push(`/project/${projectId}/doc/${doc.id}`)">
      <span class="doc-title">{{ doc.title }}</span>
      <span class="doc-date">{{ doc.updatedAt.slice(0, 10) }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useDocStore } from '@/stores/documents'

const props = defineProps<{ projectId: number }>()
const route = useRoute(); const router = useRouter()
const store = useDocStore()
const activeId = computed(() => Number(route.params.docId))

onMounted(() => store.fetchByProject(props.projectId))

async function handleCreate() {
  const doc = await store.create(props.projectId, '未命名文档')
  router.push(`/project/${props.projectId}/doc/${doc.id}`)
}
</script>

<style scoped>
.doc-list { width: 240px; border-right: 1px solid #e4e7ed; background: #fafafa; overflow-y: auto; }
.doc-list-header { padding: 12px; display: flex; justify-content: space-between; align-items: center; font-weight: 600; }
.doc-item { padding: 10px 12px; cursor: pointer; }
.doc-item:hover { background: #ecf5ff; }
.doc-item.active { background: #d9ecff; }
.doc-title { display: block; font-size: 14px; }
.doc-date { font-size: 11px; color: #909399; }
</style>
