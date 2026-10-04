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
        //
        // ⚠️ 别把 node_modules 再拆成 vendor / vendor-deps 两个 chunk（2026-10-04 修）：
        // 这么拆会产生**跨 chunk 的循环依赖**（vue 运行时 ↔ @vue/shared 等内部工具互相引用），
        // rollup 本身能处理，但 terser 压缩后只要模块求值顺序稍有变化就抛
        // `ReferenceError: Cannot access 'x' before initialization` → **整页白屏、
        // setup 直接失败、连一个 API 请求都发不出去**。而且它只在**压缩产物**里出现：
        // `--minify false` 的构建完全正常，光看源码与 dev 模式永远复现不了。
        //症状迷惑性极强：堆栈落在 vue 的运行时 chunk 里，看不出跟自己的代码有关。
        //
        // 缓存收益本来就不大（vendor 也就几百 KB），合成一个 chunk 最省心。
        manualChunks(id) {
          if (id.includes('node_modules')) return 'vendor';
        },
      },
    },
  },

});
