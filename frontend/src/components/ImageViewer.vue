<template>
  <!-- 图片预览器：Teleport 到 body，避免被祖先的 overflow / transform 裁剪或错位。
       ⚠️ 这个组件**必须是 .vue 文件而不是 JS 里的字符串 template**：
       Vite 默认用 runtime-only 构建，字符串 template 不会被编译，
       组件会静默不渲染（控制台只有一行警告，很容易漏掉）。 -->
  <Teleport to="body">
    <div v-if="state.open" class="img-viewer" @click.self="closeImageViewer">
      <button class="iv-close" type="button" aria-label="关闭" @click="closeImageViewer">&times;</button>
      <button v-if="state.list.length > 1" class="iv-nav iv-prev" type="button" aria-label="上一张"
              @click.stop="step(-1)">&#10094;</button>
      <img class="iv-img" :src="absImageUrl(state.src)" alt="预览图" />
      <button v-if="state.list.length > 1" class="iv-nav iv-next" type="button" aria-label="下一张"
              @click.stop="step(1)">&#10095;</button>
      <div v-if="state.list.length > 1" class="iv-count">{{ state.index + 1 }} / {{ state.list.length }}</div>
    </div>
  </Teleport>
</template>

<script setup>
import { imageViewerState as state, closeImageViewer, step, absImageUrl } from '../utils/imageViewer.js';
</script>
