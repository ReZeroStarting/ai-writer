<template>
  <div class="workspace">
    <DocList v-if="projectId" :project-id="projectId" />
    <div class="editor-area">
      <DocEditor v-if="currentId" :doc-id="currentId" />
      <div v-else class="empty-state">选择一篇文档开始写作</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useProjectStore } from '@/stores/projects'
import DocList from '@/components/editor/DocList.vue'
import DocEditor from '@/components/editor/DocEditor.vue'

const route = useRoute()
const projects = useProjectStore()
// 路由参数名是 `id`（见 router: project/:id?）；刷新/直链时 route.params.id 是唯一真源，
// 不能只靠 Pinia 内存态 projects.currentId，否则刷新后左侧章节列表会消失。
const routeProjectId = computed(() => Number(route.params.id) || null)
const projectId = computed(() => routeProjectId.value || projects.currentId)
const currentId = computed(() => Number(route.params.docId) || null)

watch(routeProjectId, (id) => { if (id) projects.setCurrent(id) }, { immediate: true })
</script>

<style scoped>
.workspace { display: flex; height: 100%; }
.editor-area { flex: 1; }
.empty-state { display: flex; align-items: center; justify-content: center; height: 100%; color: var(--el-text-color-secondary, #909399); font-size: 16px; }
</style>
