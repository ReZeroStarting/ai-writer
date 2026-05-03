import axios from 'axios'
import { useAuthStore } from '@/stores/auth'

const client = axios.create({ baseURL: '/api', timeout: 30000 })

client.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.accessToken) config.headers.Authorization = `Bearer ${auth.accessToken}`
  return config
})

client.interceptors.response.use(
  (res) => res,
  async (error) => {
    if (error.response?.status === 401) {
      const auth = useAuthStore()
      const refreshed = await auth.refresh()
      if (refreshed) {
        error.config.headers.Authorization = `Bearer ${auth.accessToken}`
        return client(error.config)
      }
      auth.logout()
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default client
