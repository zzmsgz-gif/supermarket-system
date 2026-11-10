<template>
  <view class="pts">
    <!-- 概览 -->
    <view class="hero">
      <text class="h-label">当前可用积分</text>
      <text class="h-num">{{ profile.points || 0 }}</text>
      <text class="h-rule">100 积分 = ¥1，下单时可抵</text>
      <text class="h-frozen" v-if="profile.frozenPoints">
        另有 {{ profile.frozenPoints }} 分关联售后审核中（已预留，通过时按规则扣回）
      </text>
    </view>

    <view class="list">
      <view class="list-head">
        <text class="list-title">积分明细</text>
        <text class="list-count" v-if="total">{{ total }} 条</text>
      </view>

      <view class="row" v-for="r in ledger" :key="r.id">
        <view class="r-left">
          <text class="r-type" :class="'t-' + r.type">{{ label(r.type) }}</text>
          <text class="r-remark">{{ r.remark || label(r.type) }}</text>
          <view class="r-meta">
            <text v-if="r.refOrderId" class="r-order">订单 #{{ r.refOrderId }}</text>
            <text class="r-time">{{ formatTime(r.createdAt) }}</text>
          </view>
        </view>
        <view class="r-right">
          <text class="r-amt" :class="{ minus: isMinus(r.type) }">{{ sign(r.type) }}{{ r.amount }}</text>
          <text class="r-bal">余 {{ r.balanceAfter }}</text>
        </view>
      </view>

      <view class="empty" v-if="!loading && !ledger.length">
        <text class="e-title">还没有积分变动</text>
        <text class="e-sub">下单完成、参与会员日活动后，这里会记录每一笔增减</text>
      </view>
      <view class="loading" v-if="loading">加载中…</view>
      <view class="no-more" v-if="!loading && ledger.length && ledger.length >= total">没有更多了</view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onReachBottom } from '@dcloudio/uni-app'
import { getMemberProfile, listPointLedger } from '@/api/member'
import { formatTime } from '@/utils/format'
import { isLoggedIn } from '@/store/auth'

// 类型文案与正负方向与 web /points 页完全一致：
// point_ledger.amount 存的是绝对值正数，方向由 type 决定。
const LABELS = {
  EARN: '消费获得',
  REDEEM: '积分抵扣',
  REFUND: '订单回退',
  ADJUST: '系统调整',
  EARN_REVERSE: '退款回收积分',
  PAYMENT: '支付抵扣',
  RECHARGE: '充值赠送'
}
// REDEEM（抵扣）与 EARN_REVERSE（退款时回收已发放积分）是扣减，其余为增加
const MINUS = ['REDEEM', 'EARN_REVERSE', 'PAYMENT']

const profile = ref({})
const ledger = ref([])
const page = ref(0)
const size = ref(20)
const total = ref(0)
const loading = ref(false)

function label(t) {
  return LABELS[t] || t || '变动'
}
function isMinus(t) {
  return MINUS.includes(t)
}
function sign(t) {
  return isMinus(t) ? '-' : '+'
}

async function load(reset = false) {
  if (loading.value) return
  if (reset) {
    page.value = 0
    ledger.value = []
  }
  loading.value = true
  try {
    const res = await listPointLedger({ page: page.value + 1, size: size.value })
    const items = (res && res.items) || []
    total.value = (res && res.total) || 0
    ledger.value = page.value === 0 ? items : ledger.value.concat(items)
    page.value += 1
  } catch (e) {
    uni.showToast({ title: (e && e.message) || '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

onLoad(async () => {
  if (!isLoggedIn()) {
    uni.navigateTo({ url: '/pages/login/login' })
    return
  }
  load(true)
  // 概览失败不影响明细展示
  try {
    profile.value = (await getMemberProfile()) || {}
  } catch (e) {
    profile.value = {}
  }
})

onReachBottom(() => {
  if (ledger.value.length < total.value && !loading.value) load()
})
</script>

<style scoped>
.pts {
  min-height: 100vh;
  background: #FAF8F3;
  padding: 16rpx 16rpx 40rpx;
}
.hero {
  background: linear-gradient(135deg, #12725C 0%, #0F6E56 60%, #0B5A46 100%);
  border-radius: 20rpx;
  padding: 30rpx 26rpx;
  display: flex;
  flex-direction: column;
  box-shadow: 0 8rpx 24rpx rgba(15, 110, 86, 0.16);
}
.h-label {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.78);
}
.h-num {
  margin-top: 8rpx;
  font-size: 64rpx;
  font-weight: 700;
  color: #ffffff;
  line-height: 1.1;
}
.h-rule {
  margin-top: 10rpx;
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.72);
}
.h-frozen {
  margin-top: 14rpx;
  padding-top: 14rpx;
  border-top: 1rpx solid rgba(255, 255, 255, 0.18);
  font-size: 22rpx;
  color: #FFE9B8;
  line-height: 1.5;
}

.list {
  margin-top: 20rpx;
  background: #fff;
  border-radius: 20rpx;
  padding: 8rpx 26rpx 20rpx;
  box-shadow: 0 2rpx 10rpx rgba(31, 41, 55, 0.03);
}
.list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 0 12rpx;
  border-bottom: 1rpx solid #F0EDE6;
}
.list-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #1F2937;
}
.list-count {
  font-size: 22rpx;
  color: #9CA3AF;
}

.row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24rpx;
  padding: 20rpx 0;
  border-bottom: 1rpx solid #F7F5F0;
}
.row:last-child {
  border-bottom: none;
}
.r-left {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}
.r-type {
  align-self: flex-start;
  font-size: 20rpx;
  padding: 2rpx 10rpx;
  border-radius: 6rpx;
  color: #0F6E56;
  background: #E9F4EF;
}
.t-REDEEM,
.t-EARN_REVERSE,
.t-PAYMENT {
  color: #C0392B;
  background: #FDECEA;
}
.r-remark {
  font-size: 25rpx;
  color: #1F2937;
  line-height: 1.4;
}
.r-meta {
  display: flex;
  align-items: center;
  gap: 14rpx;
  font-size: 21rpx;
  color: #9CA3AF;
}
.r-order {
  color: #6B7280;
}
.r-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  flex-shrink: 0;
  gap: 4rpx;
}
.r-amt {
  font-size: 30rpx;
  font-weight: 700;
  color: #0F6E56;
}
.r-amt.minus {
  color: #C0392B;
}
.r-bal {
  font-size: 20rpx;
  color: #9CA3AF;
}

.empty {
  padding: 60rpx 20rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.e-title {
  font-size: 28rpx;
  color: #4B5563;
  font-weight: 600;
}
.e-sub {
  margin-top: 10rpx;
  font-size: 23rpx;
  color: #9CA3AF;
  text-align: center;
  line-height: 1.6;
}
.loading,
.no-more {
  text-align: center;
  font-size: 23rpx;
  color: #9CA3AF;
  padding: 24rpx 0;
}
</style>
