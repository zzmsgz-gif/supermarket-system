<template>
  <!-- 节日装饰层：**纯视觉**，pointer-events:none 不挡任何点击；按 currentFestiveTheme 渲染不同主题。
       换节日：改 src/festive.js 的候选 + 后台「首页氛围」即可，这里只做分发。
       ⚠️ 刻意**不放实心月盘**：hero 里叠的月亮会被 hero 顶边裁掉一半，且落在白色「商城公告」面板上
          42% 透明度下根本看不见（实测）。中秋只留「月晕/金色微光 + 飘落桂花」；日常用「柔和上浮光斑」。 -->
  <div v-if="active" class="festive-decor" :class="'festive-' + theme" aria-hidden="true">
    <!-- 中秋：月晕 + 金色微光 + 飘落桂花 -->
    <template v-if="theme === 'midautumn'">
      <span class="fd-glow"></span>
      <span
        v-for="(p, i) in petals"
        :key="'p' + i"
        class="fd-petal"
        :style="{
          left: p.left,
          width: p.size + 'px',
          height: p.size + 'px',
          animationDelay: p.delay + 's',
          animationDuration: p.duration + 's',
          '--fd-sway': p.sway + 'px',
          '--fd-opacity': p.opacity,
        }"
      ></span>
    </template>
    <!-- 日常：柔和上浮光斑（比中秋更克制，不抢内容） -->
    <template v-else-if="theme === 'daily'">
      <span class="fd-daily-glow"></span>
      <span
        v-for="(s, i) in sparks"
        :key="'s' + i"
        class="fd-spark"
        :style="{
          left: s.left,
          width: s.size + 'px',
          height: s.size + 'px',
          animationDelay: s.delay + 's',
          animationDuration: s.duration + 's',
          '--fd-drift': s.drift + 'px',
          '--fd-op': s.opacity,
        }"
      ></span>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { currentFestiveTheme } from '../festive';

// 主题直接绑定 reactive 源：接口拉回 / 后台切换都会即时反映（无需 event bus）
const theme = currentFestiveTheme;
const active = computed(
  () => theme.value === 'midautumn' || theme.value === 'daily'
);

// 桂花：位置/时长**写死不用随机数** —— 每次刷新都一样，便于截图比对与复现。
// ⚠️ 尺寸/透明度是**实测调过的**：第一版 5–8px + 0.65~0.85 透明度，在首页那种密集背景上
//    **根本看不见**（截图后量出来才知道不是"没渲染"，是太小太淡）。
//    现在 11–15px、透明度 0.85~0.95，并给花瓣加了一圈描边提高对比。
const petals = [
  { left: '4%', size: 14, delay: 0, duration: 14, sway: 26, opacity: 0.95 },
  { left: '13%', size: 11, delay: 3.4, duration: 17, sway: -18, opacity: 0.88 },
  { left: '23%', size: 15, delay: 6.1, duration: 15, sway: 22, opacity: 0.92 },
  { left: '36%', size: 12, delay: 1.8, duration: 18, sway: -24, opacity: 0.9 },
  { left: '48%', size: 14, delay: 8.6, duration: 16, sway: 20, opacity: 0.95 },
  { left: '61%', size: 11, delay: 4.9, duration: 19, sway: -16, opacity: 0.85 },
  { left: '72%', size: 15, delay: 10.4, duration: 15, sway: 25, opacity: 0.92 },
  { left: '84%', size: 12, delay: 2.6, duration: 17, sway: -22, opacity: 0.9 },
  { left: '93%', size: 14, delay: 7.2, duration: 16, sway: 18, opacity: 0.88 },
];

// 日常上浮光斑：位置/时长同样写死（可复现）。从 hero 底部缓缓上浮 + 轻微横移，落地感干净。
const sparks = [
  { left: '8%', size: 10, delay: 0, duration: 9, drift: 14, opacity: 0.5 },
  { left: '19%', size: 7, delay: 2.2, duration: 11, drift: -10, opacity: 0.42 },
  { left: '30%', size: 12, delay: 4.1, duration: 8, drift: 18, opacity: 0.55 },
  { left: '42%', size: 6, delay: 1.3, duration: 12, drift: -8, opacity: 0.38 },
  { left: '55%', size: 9, delay: 5.6, duration: 10, drift: 12, opacity: 0.48 },
  { left: '67%', size: 7, delay: 3.0, duration: 9.5, drift: -14, opacity: 0.4 },
  { left: '78%', size: 11, delay: 6.4, duration: 8.5, drift: 16, opacity: 0.52 },
  { left: '88%', size: 6, delay: 2.0, duration: 11.5, drift: -12, opacity: 0.36 },
  { left: '95%', size: 9, delay: 4.8, duration: 9, drift: 10, opacity: 0.46 },
];
</script>
