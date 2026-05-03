<template>
  <el-header class="app-header">
    <span class="logo">AI Writer</span>
    <div class="header-right">
      <router-link to="/styles" class="nav-link">风格管理</router-link>
      <el-dropdown>
        <span class="user-info">{{ auth.user?.username }}</span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="handleLogout">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </el-header>
</template>

<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'
const auth = useAuthStore()
const router = useRouter()
function handleLogout() { auth.logout(); router.push('/login') }
</script>

<style scoped>
.app-header { display: flex; align-items: center; justify-content: space-between; padding: 0 20px; background: #fff; border-bottom: 1px solid #e4e7ed; height: 56px; }
.logo { font-size: 18px; font-weight: 700; }
.header-right { display: flex; align-items: center; gap: 16px; }
.nav-link { color: #606266; text-decoration: none; font-size: 14px; }
.nav-link:hover { color: #409eff; }
.user-info { cursor: pointer; }
</style>
