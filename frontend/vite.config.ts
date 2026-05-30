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
              name: 'element-plus-icons',
              test: /node_modules[\\/]@element-plus[\\/]icons-vue[\\/]/,
              priority: 29
            },
            {
              name: 'element-plus-table',
              test: /node_modules[\\/]element-plus[\\/]es[\\/]components[\\/](table|table-v2|pagination|scrollbar)[\\/]/,
              priority: 28
            },
            {
              name: 'element-plus-form',
              test: /node_modules[\\/]element-plus[\\/]es[\\/]components[\\/](form|input|input-number|select|select-v2|checkbox|radio|date-picker|switch|slider)[\\/]/,
              priority: 27
            },
            {
              name: 'element-plus-overlay',
              test: /node_modules[\\/]element-plus[\\/]es[\\/]components[\\/](dialog|dropdown|tooltip|popper|popconfirm|message|message-box|notification|loading)[\\/]/,
              priority: 26
            },
            {
              name: 'element-plus-layout',
              test: /node_modules[\\/]element-plus[\\/]es[\\/]components[\\/](alert|breadcrumb|button|card|col|config-provider|container|descriptions|divider|empty|icon|menu|page-header|progress|row|tabs|tag|timeline)[\\/]/,
              priority: 25
            },
            {
              name: 'element-plus-core',
              test: /node_modules[\\/]element-plus[\\/]/,
              priority: 20
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
