<template>
  <!-- 节日装饰层：**纯视觉**，pointer-events:none 不挡任何点击；由 src/festive.js 的开关控制。
       换节日：改那个常量即可（这里是 midautumn 的实现）。
       ⚠️ 刻意**不放实心月盘**：hero 里叠的月亮会被 hero 顶边裁掉一半，且落在白色「商城公告」面板上
          42% 透明度下根本看不见（实测）。而首页轮播的中秋主视觉里本来就有一轮大月亮 ——
          重复放反而像贴纸。这里只留「月晕/金色微光 + 飘落桂花」。 -->
  <div v-if="active" class="festive-decor" aria-hidden="true">
    <span class="fd-glow"></span>
    <span
      v-for="(p, i) in petals"
      :key="i"
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
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { FESTIVE_THEME } from '../festive';

const active = computed(() => FESTIVE_THEME === 'midautumn');

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
</script>
