<template>
  <view class="coupons">
    <view class="tabs">
      <view class="tab" :class="{ active: tab === 'mine' }" @click="switchTab('mine')">我的券</view>
      <view class="tab" :class="{ active: tab === 'available' }" @click="switchTab('available')">领券中心</view>
    </view>

    <!-- 我的券 -->
    <view v-if="tab === 'mine'">
      <view v-for="c in myCoupons" :key="c.id" class="cp" :class="{ used: c.status !== 'UNUSED' }">
        <view class="cp-left">
          <text class="cp-amt">¥{{ c.discountAmount }}</text>
          <text class="cp-th">满{{ c.thresholdAmount }}可用</text>
        </view>
        <view class="cp-mid">
          <view class="cp-name">{{ c.couponName }}</view>
          <view class="cp-time">{{ formatTime(c.startTime) }} ~ {{ formatTime(c.endTime) }}</view>
          <view class="cp-reason" v-if="c.status === 'UNUSED' && !c.usable">{{ c.unusableReason }}</view>
        </view>
        <view class="cp-right">
          <text class="cp-status">{{ couponStatusLabel(c.status) }}</text>
        </view>
      </view>
      <view class="empty" v-if="!loading && !myCoupons.length">还没有优惠券</view>
    </view>

    <!-- 领券中心 -->
    <view v-if="tab === 'available'">
      <view v-for="c in available" :key="c.id" class="cp">
        <view class="cp-left">
          <text class="cp-amt">¥{{ c.discountAmount }}</text>
          <text class="cp-th">满{{ c.thresholdAmount }}可用</text>
        </view>
        <view class="cp-mid">
          <view class="cp-name">{{ c.name }}</view>
          <view class="cp-time">剩 {{ Math.max(0, (c.totalCount || 0) - (c.receivedCount || 0)) }} 张</view>
        </view>
        <view class="cp-right">
          <button
            v-if="!c.receivedByCurrentUser"
            class="cp-get"
            @click="receive(c)"
          >领取</button>
          <text v-else class="cp-got">已领取</text>
        </view>
      </view>
      <view class="empty" v-if="!loading && !available.length">暂无可领优惠券</view>
    </view>

    <view class="loading" v-if="loading">加载中…</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { listMyCoupons, listAvailableCoupons, receiveCoupon } from '@/api/coupon'
import { couponStatusLabel, formatTime } from '@/utils/format'

const tab = ref('mine')
const myCoupons = ref([])
const available = ref([])
const loading = ref(false)

async function loadMine() {
  loading.value = true
  try {
    myCoupons.value = await listMyCoupons()
  } catch (e) {
    myCoupons.value = []
  } finally {
    loading.value = false
  }
}

async function loadAvailable() {
  loading.value = true
  try {
    available.value = await listAvailableCoupons()
  } catch (e) {
    available.value = []
  } finally {
    loading.value = false
  }
}

function switchTab(t) {
  tab.value = t
  if (t === 'mine') loadMine()
  else loadAvailable()
}

async function receive(c) {
  try {
    await receiveCoupon(c.id)
    uni.showToast({ title: '领取成功', icon: 'success' })
    loadAvailable()
  } catch (e) {
    uni.showToast({ title: (e && e.message) || '领取失败', icon: 'none' })
  }
}

onShow(() => {
  if (tab.value === 'mine') loadMine()
  else loadAvailable()
})
</script>

<style scoped>
.coupons {
  min-height: 100vh;
  background: #f5f5f5;
  padding-bottom: 24rpx;
}
.tabs {
  display: flex;
  background: #fff;
  position: sticky;
  top: 0;
  z-index: 10;
}
.tab {
  flex: 1;
  text-align: center;
  padding: 20rpx 0;
  font-size: 28rpx;
  color: #666;
}
.tab.active {
  color: #07c160;
  font-weight: bold;
  border-bottom: 4rpx solid #07c160;
}
.cp {
  display: flex;
  align-items: center;
  background: #fff;
  margin: 16rpx;
  border-radius: 12rpx;
  overflow: hidden;
}
.cp.used {
  opacity: 0.6;
}
.cp-left {
  width: 200rpx;
  background: #07c160;
  color: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 28rpx 0;
}
.cp-amt {
  font-size: 44rpx;
  font-weight: bold;
}
.cp-th {
  font-size: 22rpx;
  margin-top: 8rpx;
}
.cp-mid {
  flex: 1;
  padding: 20rpx 24rpx;
  min-width: 0;
}
.cp-name {
  font-size: 28rpx;
  font-weight: bold;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cp-time {
  font-size: 22rpx;
  color: #999;
  margin-top: 10rpx;
}
.cp-reason {
  font-size: 22rpx;
  color: #e4393c;
  margin-top: 6rpx;
}
.cp-right {
  padding: 0 24rpx;
}
.cp-status {
  font-size: 24rpx;
  color: #999;
}
.cp-get {
  background: #e4393c;
  color: #fff;
  font-size: 24rpx;
  padding: 10rpx 24rpx;
  border-radius: 32rpx;
  line-height: 1.4;
}
.cp-got {
  font-size: 24rpx;
  color: #999;
}
.empty {
  text-align: center;
  color: #999;
  padding: 120rpx 0;
}
.loading {
  text-align: center;
  color: #999;
  font-size: 24rpx;
  padding: 24rpx 0;
}
</style>
