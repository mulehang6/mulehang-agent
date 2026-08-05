import { resolve } from 'node:path'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      input: {
        main: resolve(__dirname, 'index.html'),
        // 独立的设计稿页面：Agent 执行时间线（供实现参考，勿并入主页面）
        timeline: resolve(__dirname, 'timeline.html'),
      },
    },
  },
})
