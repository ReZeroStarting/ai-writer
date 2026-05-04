<template>
  <div v-if="store.styles.length === 0" class="empty">还没有写作风格，创建一个开始吧</div>
  <div v-else class="style-grid">
    <el-card v-for="s in store.styles" :key="s.id" class="style-card">
      <div class="card-header">
        <h3>{{ s.name }}</h3>
        <el-button type="danger" size="small" text @click="handleDelete(s.id)">删除</el-button>
      </div>
      <p class="card-desc">{{ s.description || '无描述' }}</p>
      <p class="card-prompt">{{ s.systemPrompt?.slice(0, 200) }}{{ s.systemPrompt?.length > 200 ? '...' : '' }}</p>
      <StyleLearnDialog :style-id="s.id" :style-name="s.name" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useStyleStore } from '@/stores/styles'
import { ElMessage } from 'element-plus'
import StyleLearnDialog from './StyleLearnDialog.vue'

const store = useStyleStore()
onMounted(() => store.fetch())

async function handleDelete(id: number) { await store.remove(id); ElMessage.success('已删除') }
</script>

<style scoped>
.empty { text-align: center; color: var(--el-text-color-secondary, #909399); padding: 60px; }
.style-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(380px, 1fr)); gap: 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-header h3 { margin: 0; }
.card-desc { color: var(--el-text-color-secondary, #909399); font-size: 13px; margin: 8px 0; }
.card-prompt { background: var(--el-fill-color-light, #f5f7fa); padding: 10px; border-radius: 4px; font-size: 13px; color: var(--el-text-color-regular, #606266); white-space: pre-wrap; }
</style>
