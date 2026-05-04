import axios from 'axios'
import { useAuthStore } from '@/stores/auth'

const client = axios.create({ baseURL: '/api', timeout: 30000 })

let isRefreshing = false
let failedQueue: Array<{ resolve: Function; reject: Function }> = []

function processQueue(error: Error | null) {
  failedQueue.forEach(({ resolve, reject }) => {
    if (error) reject(error)
    else resolve(undefined)
  })
  failedQueue = []
}

client.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.accessToken) config.headers.Authorization = `Bearer ${auth.accessToken}`
  return config
})

client.interceptors.response.use(
  (res) => res,
  async (error) => {
    const originalRequest = error.config

    if (error.response?.status === 401 && !originalRequest._retry) {
      if (originalRequest.url === '/auth/refresh') {
        const auth = useAuthStore()
        auth.logout()
        window.location.href = '/login'
        return Promise.reject(error)
      }

      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject })
        }).then(() => {
          originalRequest.headers.Authorization = `Bearer ${useAuthStore().accessToken}`
          return client(originalRequest)
        })
      }

      originalRequest._retry = true
      isRefreshing = true

      const auth = useAuthStore()
      const refreshed = await auth.refresh()
      isRefreshing = false

      if (refreshed) {
        processQueue(null)
        originalRequest.headers.Authorization = `Bearer ${auth.accessToken}`
        return client(originalRequest)
      }

      processQueue(new Error('refresh_failed'))
      auth.logout()
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default client
