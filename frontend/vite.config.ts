import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

const apiProxyTarget = process.env.VITE_API_PROXY_TARGET || 'http://localhost:8080'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 5173,
    // 修复 WebSocket 断连导致页面重载问题
    hmr: {
      overlay: false
    },
    // 保持连接稳定
    watch: {
      usePolling: false
    },
    proxy: {
      '/api': {
        target: apiProxyTarget,
        changeOrigin: true
      }
    }
  },
  build: {
    rolldownOptions: {
      output: {
        codeSplitting: {
          minSize: 20 * 1024,
          groups: [
            {
              name: 'vue-vendor',
              test: /node_modules[\\/](vue|vue-router|pinia)[\\/]/,
              priority: 30
            },
            {
              name: 'element-plus',
              test: /node_modules[\\/](element-plus|@element-plus)[\\/]/,
              priority: 20,
              maxSize: 260 * 1024
            },
            {
              name: 'http-vendor',
              test: /node_modules[\\/]axios[\\/]/,
              priority: 10
            }
          ]
        }
      }
    }
  }
})
