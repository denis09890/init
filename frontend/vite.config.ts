import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // The backend listens on :8080. Proxying /api keeps the browser on one origin, so no CORS is needed.
    proxy: { '/api': 'http://localhost:8080' },
  },
})
