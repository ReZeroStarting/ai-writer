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
const projectId = computed(() => Number(route.params.projectId) || projects.currentId)
const currentId = computed(() => Number(route.params.docId) || null)

watch(projectId, (id) => { if (id) projects.setCurrent(id) }, { immediate: true })
</script>

<style scoped>
.workspace { display: flex; height: 100%; }
.editor-area { flex: 1; }
.empty-state { display: flex; align-items: center; justify-content: center; height: 100%; color: #909399; font-size: 16px; }
</style>
