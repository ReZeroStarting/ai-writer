<template>
  <div class="auth-container">
    <el-card class="auth-card">
      <h1 class="auth-title">AI Writer</h1>
      <p class="auth-subtitle">AI 写作助手</p>
      <LoginForm />
      <p class="auth-link">还没有账号？<router-link to="/register">立即注册</router-link></p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import LoginForm from '@/components/auth/LoginForm.vue'
</script>

<style scoped>
.auth-container { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: var(--el-bg-color-page, #f5f7fa); }
.auth-card { width: 420px; padding: 20px; }
.auth-title { text-align: center; font-size: 28px; margin-bottom: 4px; }
.auth-subtitle { text-align: center; color: var(--el-text-color-secondary, #909399); margin-bottom: 24px; }
.auth-link { text-align: center; margin-top: 16px; color: var(--el-text-color-secondary, #909399); }
</style>
