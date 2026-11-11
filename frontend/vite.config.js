import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发代理 /api → 8080（03§3.3 vite.config.js）
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist'
  }
})