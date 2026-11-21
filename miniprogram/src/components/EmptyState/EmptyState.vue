<template>
  <view class="empty" :class="{ compact }">
    <view class="e-ico-wrap">
      <text class="e-ico">{{ icon }}</text>
    </view>
    <text class="e-title">{{ title }}</text>
    <text class="e-desc" v-if="desc">{{ desc }}</text>
    <view class="e-btn" v-if="actionText" @click="$emit('action')">{{ actionText }}</view>
  </view>
</template>

<script setup>
/**
 * 统一空状态（小程序版）。
 *
 * 为什么不用 PC 那套内联 SVG：微信小程序的 `<svg>` 标签不参与渲染，
 * 而 image 组件也不支持 svg 文件 —— 所以这里用**圆形浅色底 + 大 emoji**，
 * 跨端（小程序 / H5）都稳，且不需要额外的图片或字体资源。
 *
 * 用法：
 *   <EmptyState icon="🛒" title="购物车还是空的" desc="挑点新鲜的，满 99 元免运费"
 *               action-text="去逛逛" @action="goShop" />
 *   楼层内 / 弹层内等空间小的地方加 compact。
 */
defineProps({
  /** emoji 图标，如 🛒 👟 ❤️ 🎟️ */
  icon: { type: String, default: '📦' },
  /** 主文案：说清「现在是什么状态」 */
  title: { type: String, required: true },
  /** 副文案：告诉他「接下来能做什么」 */
  desc: { type: String, default: '' },
  /** 有出口就给按钮，没有就不显示 */
  actionText: { type: String, default: '' },
  /** 紧凑模式：楼层/弹层内使用，尺寸减半 */
  compact: { type: Boolean, default: false }
})
defineEmits(['action'])
</script>

<style scoped>
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 100rpx 48rpx;
}
.e-ico-wrap {
  width: 160rpx;
  height: 160rpx;
  border-radius: 50%;
  /* 浅绿渐变，比纯灰圆更有生气，也不抢商品图的戏 */
  background: linear-gradient(160deg, #F1F8F5, #E2EFE9);
  display: flex;
  align-items: center;
  justify-content: center;
}
.e-ico {
  font-size: 72rpx;
  line-height: 1;
}
.e-title {
  margin-top: 28rpx;
  font-size: 28rpx;
  font-weight: 600;
  color: #4B5563;
  text-align: center;
}
.e-desc {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #9CA3AF;
  text-align: center;
  line-height: 1.6;
}
.e-btn {
  margin-top: 32rpx;
  padding: 0 44rpx;
  height: 68rpx;
  line-height: 68rpx;
  font-size: 26rpx;
  color: #fff;
  background: #0F6E56;
  border-radius: 34rpx;
}
.e-btn:active {
  opacity: 0.85;
}
/* 紧凑版：楼层空、弹层空这类「旁边还有别的内容」的场景 */
.empty.compact {
  padding: 40rpx 24rpx;
}
.empty.compact .e-ico-wrap {
  width: 88rpx;
  height: 88rpx;
}
.empty.compact .e-ico {
  font-size: 40rpx;
}
.empty.compact .e-title {
  margin-top: 16rpx;
  font-size: 24rpx;
}
.empty.compact .e-desc {
  font-size: 22rpx;
}
</style>
