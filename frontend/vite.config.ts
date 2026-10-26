import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// /api 로 시작하는 요청은 Spring Boot 서버(8080)로 넘깁니다.
// 그래서 프론트 코드에서는 fetch('/api/...') 처럼 짧게 쓰면 돼요.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
