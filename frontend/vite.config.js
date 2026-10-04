import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  preview: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    rollupOptions: {
      output: {
        // 第三方核心库单独成 vendor chunk：应用代码更新不会改变 vendor 的 hash，
        // 浏览器可长期强缓存，刷新应用代码时不重复下载框架层。
        manualChunks(id) {
          if (id.includes('node_modules')) {
            if (/[\\/]node_modules[\\/](vue|vue-router|pinia|axios)[\\/]/.test(id)) {
              return 'vendor';
            }
            return 'vendor-deps';
          }
        },
      },
    },
  },

});
