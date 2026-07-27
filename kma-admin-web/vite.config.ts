import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import ElementPlus from 'unplugin-element-plus/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  plugins: [
    vue(),
    Components({
      dts: 'src/components.d.ts',
      resolvers: [ElementPlusResolver({ importStyle: 'css' })],
    }),
    ElementPlus({ useSource: false }),
  ],
  server: {
    port: 27183,
    proxy: { '/api': 'http://localhost:8090', '/actuator': 'http://localhost:8090' },
  },
  build: {
    cssCodeSplit: true,
    manifest: true,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('@tanstack/vue-query')) return 'vendor-query'
          if (id.includes('vue-router') || id.includes('pinia')) return 'vendor-vue'
          return undefined
        },
      },
    },
  },
})
