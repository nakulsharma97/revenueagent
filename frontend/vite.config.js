import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules/recharts') || id.includes('node_modules/d3-') || id.includes('node_modules/victory-') || id.includes('node_modules/clsx') || id.includes('node_modules/react-is') || id.includes('node_modules/smooth-scrollbar') || id.includes('node_modules/eventemitter3') || id.includes('node_modules/tuple-function')) {
            return 'recharts';
          }
        },
      },
    },
  },
})
