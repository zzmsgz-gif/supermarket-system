<template>
  <!-- 文本 / 详情块骨架屏：可选头像 + 标题条 + 多行。
       ⚠️ 纯装饰、aria-hidden；显隐由调用方的 loading 决定。不带任何业务类，避免被 hover/入场规则命中。 -->
  <div class="sk-block" aria-hidden="true">
    <div v-if="avatar" class="sk-avatar sk-shimmer"></div>
    <div v-if="title" class="sk-title sk-shimmer" :style="titleStyle"></div>
    <div
      v-for="(w, i) in lineWidths"
      :key="i"
      class="sk-line sk-shimmer"
      :style="{ width: w + '%' }"
    ></div>
  </div>
</template>

<script setup>
import { computed } from 'vue';

// 只负责"长什么样"：显示几行、要不要头像/标题、每行多宽，都可以配。
const props = defineProps({
  lines: { type: Number, default: 3 },          // 文本行数
  title: { type: Boolean, default: false },      // 是否显示顶部标题条
  avatar: { type: Boolean, default: false },     // 是否显示圆形头像（详情页作者/店铺等）
  widths: { type: Array, default: () => [] },    // 自定义每行宽度(%)，缺省自动生成
  titleWidth: { type: [Number, String], default: 40 }, // 标题条宽度(%或带单位)
});

// 没传 widths 时：末行收短，模拟一段自然段落
const lineWidths = computed(() => {
  if (props.widths && props.widths.length) return props.widths;
  return Array.from({ length: props.lines }, (_, i) =>
    i === props.lines - 1 ? 55 : 96
  );
});

const titleStyle = computed(() => ({
  width:
    typeof props.titleWidth === 'number' ? props.titleWidth + '%' : props.titleWidth,
}));
</script>
