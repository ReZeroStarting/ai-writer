<template>
  <div class="dashboard">
    <AppHeader />
    <div class="dashboard-body">
      <ProjectSidebar />
      <main class="main-content"><router-view /></main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import AppHeader from '@/components/layout/AppHeader.vue'
import ProjectSidebar from '@/components/layout/ProjectSidebar.vue'
import { useProjectStore } from '@/stores/projects'
const projects = useProjectStore()
onMounted(() => projects.fetch())
</script>

<style scoped>
.dashboard { height: 100vh; display: flex; flex-direction: column; }
.dashboard-body { flex: 1; display: flex; overflow: hidden; }
.main-content { flex: 1; overflow-y: auto; background: #fff; }
</style>
