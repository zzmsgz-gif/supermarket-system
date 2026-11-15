<template>
  <!--
    骨架屏：数据没回来之前先把版式占住，避免"白屏 → 突然弹出内容"的跳动。
    比 loading 转圈好在：用户能预期内容长什么样，感知等待更短。
  -->
  <view class="sk">
    <view class="sk-row" v-for="i in rows" :key="i">
      <!-- 左侧商品图占位 -->
      <view class="sk-cover shimmer"></view>
      <!-- 中间文字占位：名称 / 规格 / 价格 三行，宽度递减更像真实文本 -->
      <view class="sk-info">
        <view class="sk-line shimmer" :style="{ width: lineWidths[0] }"></view>
        <view class="sk-line shimmer" :style="{ width: lineWidths[1] }"></view>
        <view class="sk-line shimmer" :style="{ width: lineWidths[2] }"></view>
      </view>
      <!-- 右侧数量控件占位 -->
      <view class="sk-qty shimmer"></view>
    </view>
  </view>
</template>

<script setup>
defineProps({
  rows: { type: Number, default: 3 },
  // 三行文字的宽度，允许调用方按场景微调（默认按购物车行的视觉比例）
  lineWidths: {
    type: Array,
    default: () => ['72%', '40%', '26%']
  }
})
</script>

<style scoped>
.sk {
  padding: 0 0 8rpx;
}
.sk-row {
  display: flex;
  align-items: flex-start;
  gap: 20rpx;
  margin: 0 24rpx 20rpx;
  padding: 24rpx 20rpx;
  background: #ffffff;
  border-radius: 20rpx;
}
.sk-cover {
  width: 160rpx;
  height: 160rpx;
  border-radius: 16rpx;
  flex-shrink: 0;
}
.sk-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 14rpx;
  padding-top: 6rpx;
}
.sk-line {
  height: 24rpx;
  border-radius: 12rpx;
}
.sk-qty {
  width: 150rpx;
  height: 52rpx;
  border-radius: 26rpx;
  flex-shrink: 0;
  margin-top: 52rpx;
}

/* 流光动画：背景渐变横扫。用 background-position 而不是 opacity 闪烁，
   视觉上像"正在加载"，不会让人觉得是渲染坏了 */
.shimmer {
  background: linear-gradient(90deg, #F1EDE5 25%, #E7E1D4 37%, #F1EDE5 63%);
  background-size: 400% 100%;
  animation: sk-shimmer 1.4s ease infinite;
}
@keyframes sk-shimmer {
  0% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0 50%;
  }
}
</style>
