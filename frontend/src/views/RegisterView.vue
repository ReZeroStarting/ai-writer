<template>
  <div class="auth-container">
    <el-card class="auth-card">
      <h1 class="auth-title">创建账号</h1>
      <p class="auth-subtitle">加入 AI Writer</p>
      <RegisterForm />
      <p class="auth-link">已有账号？<router-link to="/login">立即登录</router-link></p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import RegisterForm from '@/components/auth/RegisterForm.vue'
</script>

<style scoped>
.auth-container { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: #f5f7fa; }
.auth-card { width: 420px; padding: 20px; }
.auth-title { text-align: center; font-size: 28px; margin-bottom: 4px; }
.auth-subtitle { text-align: center; color: #909399; margin-bottom: 24px; }
.auth-link { text-align: center; margin-top: 16px; color: #909399; }
</style>
