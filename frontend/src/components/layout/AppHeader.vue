<template>
  <el-header class="app-header">
    <span class="logo">AI Writer</span>
    <div class="header-right">
      <el-button text @click="theme.toggle()" class="theme-btn">
        <el-icon><Sunny v-if="theme.theme === 'light'" /><Moon v-else-if="theme.theme === 'dark'" /><Sunrise v-else /></el-icon>
      </el-button>
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
import { useThemeStore } from '@/stores/theme'
import { useRouter } from 'vue-router'
import { Sunny, Moon, Sunrise } from '@element-plus/icons-vue'
const auth = useAuthStore()
const theme = useThemeStore()
const router = useRouter()
function handleLogout() { auth.logout(); router.push('/login') }
</script>

<style scoped>
.app-header { display: flex; align-items: center; justify-content: space-between; padding: 0 20px; background: var(--el-bg-color-overlay, #fff); border-bottom: 1px solid var(--el-border-color, #e4e7ed); height: 56px; }
.logo { font-size: 18px; font-weight: 700; }
.header-right { display: flex; align-items: center; gap: 16px; }
.theme-btn { font-size: 20px; }
.nav-link { color: var(--el-text-color-regular, #606266); text-decoration: none; font-size: 14px; }
.nav-link:hover { color: var(--el-color-primary, #409eff); }
.user-info { cursor: pointer; color: var(--el-text-color-regular, #606266); }
</style>
