<template>
  <!--
    优惠券选择弹层 —— 购物车与结算页共用。
    统一的原因：两端要"购物车能改、结算页也能改"，如果各写一套，
    UI 与口径迟早在两边漂移（券列表过滤规则、门槛提示都会不一样）。
  -->
  <view class="cs-mask" v-if="visible" @click="$emit('close')">
    <view class="cs-sheet" @click.stop>
      <view class="cs-head">
        <text class="cs-title">选择优惠券</text>
        <text class="cs-close" @click="$emit('close')">✕</text>
      </view>
      <text class="cs-tip">券与活动优惠不可叠加，系统自动取减免更大的那一档</text>

      <scroll-view scroll-y class="cs-list">
        <view v-for="c in coupons" :key="c.id" class="cs-item" :class="{ on: c.id === selectedId }" @click="$emit('pick', c)">
          <view class="cs-amt-box">
            <text class="cs-amt"><text class="cs-yen">¥</text>{{ fmt(c.discountAmount) }}</text>
            <text class="cs-th">满 {{ fmt(c.thresholdAmount) }} 可用</text>
          </view>
          <view class="cs-mid">
            <text class="cs-name">{{ c.couponName }}</text>
            <text class="cs-exp" v-if="c.endTime">有效期至 {{ String(c.endTime).slice(0, 10) }}</text>
          </view>
          <text class="cs-check">{{ c.id === selectedId ? '●' : '○' }}</text>
        </view>
        <view class="cs-empty" v-if="!coupons.length">暂无可用优惠券</view>
      </scroll-view>

      <view class="cs-foot">
        <view class="cs-none" @click="$emit('pick', null)">不使用优惠券</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { formatAmount } from '@/utils/format'

defineProps({
  visible: { type: Boolean, default: false },
  /** 可用券列表（已按门槛过滤，来自 /coupons/usable） */
  coupons: { type: Array, default: () => [] },
  selectedId: { type: [Number, String], default: null }
})

defineEmits(['close', 'pick'])

const fmt = (n) => formatAmount(n)
</script>

<style scoped>
.cs-mask {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: flex-end;
  z-index: 950;
}
.cs-sheet {
  width: 100%;
  max-height: 76vh;
  background: #fff;
  border-radius: 28rpx 28rpx 0 0;
  display: flex;
  flex-direction: column;
  padding-bottom: env(safe-area-inset-bottom);
}
.cs-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 32rpx 8rpx;
}
.cs-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #1F2937;
}
.cs-close {
  font-size: 30rpx;
  color: #9CA3AF;
  padding: 0 8rpx;
}
.cs-tip {
  padding: 0 32rpx 16rpx;
  font-size: 21rpx;
  color: #A08A6A;
  line-height: 1.5;
}
.cs-list {
  flex: 1;
  min-height: 0;
  padding: 0 32rpx;
}
.cs-item {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 22rpx 20rpx;
  margin-bottom: 14rpx;
  border: 1rpx solid #EDE8DD;
  border-radius: 16rpx;
  background: #FDFCF9;
}
.cs-item.on {
  border-color: #0F6E56;
  background: #F1F8F5;
}
.cs-amt-box {
  width: 150rpx;
  flex-shrink: 0;
  text-align: center;
}
.cs-amt {
  display: block;
  font-size: 40rpx;
  font-weight: 700;
  color: #C0392B;
}
.cs-yen {
  font-size: 22rpx;
}
.cs-th {
  display: block;
  margin-top: 4rpx;
  font-size: 19rpx;
  color: #9A8F7E;
}
.cs-mid {
  flex: 1;
  min-width: 0;
}
.cs-name {
  display: block;
  font-size: 26rpx;
  font-weight: 600;
  color: #1F2937;
}
.cs-exp {
  display: block;
  margin-top: 6rpx;
  font-size: 20rpx;
  color: #A9A296;
}
.cs-check {
  flex-shrink: 0;
  font-size: 30rpx;
  color: #0F6E56;
}
.cs-empty {
  text-align: center;
  padding: 60rpx 0;
  font-size: 26rpx;
  color: #9A8F7E;
}
.cs-foot {
  padding: 16rpx 32rpx 28rpx;
  border-top: 1rpx solid #F5F2EC;
}
.cs-none {
  text-align: center;
  padding: 14rpx 0;
  font-size: 27rpx;
  color: #6B7280;
}
</style>
