import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { User, LoginRequest, RegisterRequest } from '@/types'
import { authApi } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const accessToken = ref<string | null>(localStorage.getItem('access_token'))
  const refreshToken = ref<string | null>(localStorage.getItem('refresh_token'))
  const isLoggedIn = computed(() => !!accessToken.value)

  function setAuth(data: any) {
    accessToken.value = data.accessToken
    refreshToken.value = data.refreshToken
    user.value = data.user
    localStorage.setItem('access_token', data.accessToken)
    localStorage.setItem('refresh_token', data.refreshToken)
  }

  async function login(req: LoginRequest) { const res = await authApi.login(req); setAuth(res.data) }
  async function register(req: RegisterRequest) { const res = await authApi.register(req); setAuth(res.data) }

  async function refresh(): Promise<boolean> {
    if (!refreshToken.value) return false
    try {
      const res = await authApi.refresh(refreshToken.value)
      setAuth(res.data)
      return true
    } catch { return false }
  }

  function logout() {
    user.value = null; accessToken.value = null; refreshToken.value = null
    localStorage.removeItem('access_token'); localStorage.removeItem('refresh_token')
  }

  return { user, accessToken, refreshToken, isLoggedIn, login, register, refresh, logout }
})
