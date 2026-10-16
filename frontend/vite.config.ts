import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

// 后端地址可通过环境变量覆盖，避免为了换端口而复制一份 vite 配置：
//   VITE_API_TARGET=http://127.0.0.1:8093 npm run dev
const apiTarget = process.env.VITE_API_TARGET || 'http://localhost:8080'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: apiTarget,
        changeOrigin: true,
      },
    },
  },
})
