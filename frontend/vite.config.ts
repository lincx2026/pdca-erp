import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { createSvgIconsPlugin } from 'vite-plugin-svg-icons'
import path from 'path'

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd())
  return {
    plugins: [
      vue(),
      createSvgIconsPlugin({
        iconDirs: [path.resolve(process.cwd(), 'src/assets/icons')],
        symbolId: 'icon-[dir]-[name]'
      })
    ],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src')
      }
    },
    css: {
      preprocessorOptions: {
        scss: {
          api: 'modern-compiler',
          additionalData: `@use "@/assets/styles/variables.scss" as *;`
        }
      }
    },
    server: {
      host: '0.0.0.0',
      port: 5180,
      open: false,
      cors: true,
      proxy: {
        // 后端接口代理: /api/** → http://localhost:8080/** (去掉 /api 前缀)
        '/api': {
          target: env.VITE_API_PROXY || 'http://localhost:8080',
          changeOrigin: true,
          rewrite: (p) => p.replace(/^\/api/, '')
        },
        // Knife4j 接口文档
        '/doc.html': {
          target: env.VITE_API_PROXY || 'http://localhost:8080',
          changeOrigin: true
        },
        '/v3': {
          target: env.VITE_API_PROXY || 'http://localhost:8080',
          changeOrigin: true
        },
        '/webjars': {
          target: env.VITE_API_PROXY || 'http://localhost:8080',
          changeOrigin: true
        },
        // Druid 监控
        '/druid': {
          target: env.VITE_API_PROXY || 'http://localhost:8080',
          changeOrigin: true
        }
      }
    },
    build: {
      target: 'es2015',
      outDir: 'dist',
      sourcemap: mode !== 'production',
      chunkSizeWarningLimit: 2000,
      rollupOptions: {
        output: {
          // 拆分 chunk
          manualChunks: {
            vue:        ['vue', 'vue-router', 'pinia'],
            element:    ['element-plus', '@element-plus/icons-vue'],
            echarts:    ['echarts'],
            libs:       ['axios', 'dayjs', 'nprogress']
          }
        }
      }
    }
  }
})
