<template>
  <div class="style-page">
    <div class="style-topbar">
      <el-button text @click="$router.back()">← 返回</el-button>
    </div>
    <div class="style-header">
      <h2>写作风格管理</h2>
      <el-button type="primary" @click="showCreate = true">+ 新建风格</el-button>
    </div>
    <StyleList />
    <StyleCreateDialog v-model="showCreate" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import StyleList from '@/components/styles/StyleList.vue'
import StyleCreateDialog from '@/components/styles/StyleCreateDialog.vue'

const showCreate = ref(false)
</script>

<style scoped>
.style-page { min-height: 100vh; padding: 24px; max-width: 900px; margin: 0 auto; }
.style-topbar { margin-bottom: 16px; }
.style-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
</style>
