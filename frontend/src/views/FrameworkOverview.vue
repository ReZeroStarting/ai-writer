<template>
  <div class="framework-overview">
    <el-empty v-if="!fw" description="加载中..." />
    <div v-else class="overview-content">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="框架 ID">{{ fw.id }}</el-descriptions-item>
        <el-descriptions-item label="项目 ID">{{ fw.projectId }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="fw.status === 'ACTIVE' ? 'success' : 'info'">
            {{ fw.status === 'ACTIVE' ? '创作中' : '已完结' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ fw.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ fw.updatedAt }}</el-descriptions-item>
      </el-descriptions>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useFrameworkStore } from '@/stores/framework'

const fwStore = useFrameworkStore()
const fw = computed(() => fwStore.framework)
</script>

<style scoped>
.framework-overview { padding: 16px 0; }
.overview-content { max-width: 600px; }
</style>
