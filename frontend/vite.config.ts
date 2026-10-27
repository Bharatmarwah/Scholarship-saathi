import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The backend has no CORS configuration, so the dev server proxies /v1 to
// Spring Boot. This keeps the backend untouched and makes the browser calls
// same-origin. Override the target with VITE_API_TARGET if the backend runs
// somewhere else.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/v1': {
        target: process.env.VITE_API_TARGET ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
