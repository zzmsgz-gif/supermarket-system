<template>
  <view class="pay" v-if="order">
    <!-- 倒计时 -->
    <view class="cd" :class="{ urgent: remaining <= 60 }">
      <text class="cd-label">支付剩余时间</text>
      <text class="cd-time">{{ countdownText }}</text>
      <text class="cd-tip">超时订单将自动关闭，库存会退回</text>
    </view>

    <!-- 金额 -->
    <view class="amount">
      <text class="a-label">待支付金额</text>
      <text class="a-num"><text class="a-yen">¥</text>{{ order.payAmount }}</text>
    </view>

    <!-- 支付方式 -->
    <view class="block">
      <view class="pm">
        <view class="pm-left">
          <text class="pm-ico">💰</text>
          <view class="pm-info">
            <text class="pm-name">钱包余额</text>
            <text class="pm-bal">当前余额 ¥{{ balance }}<text class="pm-lack" v-if="!enough">（不足 ¥{{ lack }}）</text></text>
          </view>
        </view>
        <view class="pm-check">✓</view>
      </view>
    </view>

    <view class="block">
      <view class="kv"><text>订单编号</text><text class="ono">{{ order.orderNo }}</text></view>
      <view class="kv"><text>商品件数</text><text>{{ itemCount }} 件</text></view>
      <view class="kv" v-if="order.freightAmount"><text>运费</text><text>¥{{ order.freightAmount }}</text></view>
      <view class="kv" v-if="order.discountAmount"><text>优惠</text><text class="minus">-¥{{ order.discountAmount }}</text></view>
      <view class="kv" v-if="order.activityDiscount"><text>活动优惠</text><text class="minus">-¥{{ order.activityDiscount }}</text></view>
      <view class="kv" v-if="order.pointsDiscountAmount"><text>积分抵扣</text><text class="minus">-¥{{ order.pointsDiscountAmount }}</text></view>
    </view>

    <view class="ops">
      <button class="btn ghost" @click="later">稍后支付</button>
      <button
        class="btn main"
        :disabled="paying || !enough || expired"
        @click="doPay"
      >{{ expired ? '订单已超时' : (paying ? '支付中…' : (enough ? '立即支付' : '余额不足')) }}</button>
    </view>

    <view class="recharge-tip" v-if="!enough" @click="goRecharge">
      余额不足，去充值 ›
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onUnmounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getOrder, payOrder } from '@/api/order'
import { getWallet } from '@/api/wallet'

const order = ref(null)
const balance = ref(0)
const paying = ref(false)
const remaining = ref(0)
let timer = null

const countdownText = computed(() => {
  const s = Math.max(remaining.value, 0)
  const m = Math.floor(s / 60)
  const sec = s % 60
  return `${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`
})
const expired = computed(() => remaining.value <= 0 && !!order.value)
const payAmount = computed(() => Number((order.value || {}).payAmount || 0))
const enough = computed(() => Number(balance.value) >= payAmount.value)
const lack = computed(() => Math.max(payAmount.value - Number(balance.value), 0).toFixed(2))
const itemCount = computed(() => (order.value && order.value.items || []).reduce((s, it) => s + (it.quantity || 0), 0))

function tick() {
  if (remaining.value > 0) {
    remaining.value -= 1
    if (remaining.value <= 0) stopTimer()
  }
}

function startTimer() {
  stopTimer()
  timer = setInterval(tick, 1000)
}
function stopTimer() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

/**
 * 解析后端 payDeadline。后端给的是 ISO 串（2026-11-10T22:30:00），
 * ⚠️ 不要 replace('-','/') —— 那会把 ISO 格式破坏成 `2026/11/10T22:30:00` 导致 NaN。
 * 先用 ISO 直接解析，失败再退化到「空格 + 斜杠」的老格式兜底。
 */
function parseDeadline(s) {
  if (!s) return 0
  const str = String(s).trim()
  let t = new Date(str).getTime()
  if (isNaN(t)) {
    t = new Date(str.replace('T', ' ').replace(/-/g, '/')).getTime()
  }
  return isNaN(t) ? 0 : t
}

async function load(id) {
  order.value = await getOrder(id)
  // 倒计时用后端 payDeadline（超时关单规则都在后端），本地只负责展示
  const deadline = order.value && order.value.payDeadline
  const deadlineMs = parseDeadline(deadline)
  if (deadlineMs > 0) {
    remaining.value = Math.max(Math.floor((deadlineMs - Date.now()) / 1000), 0)
  } else {
    remaining.value = 15 * 60
  }
  startTimer()
  try {
    const w = await getWallet()
    balance.value = Number((w && w.balance) || 0)
  } catch (e) {
    balance.value = 0
  }
}

async function doPay() {
  if (paying.value || expired.value) return
  if (!enough.value) {
    uni.showToast({ title: '余额不足，请先充值', icon: 'none' })
    return
  }
  paying.value = true
  uni.showLoading({ title: '支付中' })
  try {
    await payOrder(order.value.id)
    uni.hideLoading()
    uni.showToast({ title: '支付成功', icon: 'success' })
    stopTimer()
    setTimeout(() => {
      uni.redirectTo({ url: '/pages/order-detail/order-detail?id=' + order.value.id })
    }, 600)
  } catch (e) {
    uni.hideLoading()
    uni.showToast({ title: (e && e.message) || '支付失败', icon: 'none' })
  } finally {
    paying.value = false
  }
}

function later() {
  stopTimer()
  uni.redirectTo({ url: '/pages/order-detail/order-detail?id=' + order.value.id })
}

function goRecharge() {
  stopTimer()
  uni.navigateTo({ url: '/pages/wallet/wallet' })
}

onLoad((opts) => {
  if (!opts.id) {
    uni.showToast({ title: '缺少订单号', icon: 'none' })
    return
  }
  load(opts.id)
})

onUnmounted(() => {
  stopTimer()
})
</script>

<style scoped>
.pay {
  min-height: 100vh;
  background: #FAF8F3;
  padding: 16rpx 16rpx 40rpx;
}
.cd {
  background: linear-gradient(135deg, #12725C 0%, #0F6E56 60%, #0B5A46 100%);
  border-radius: 20rpx;
  padding: 28rpx 26rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: 0 8rpx 24rpx rgba(15, 110, 86, 0.16);
}
.cd.urgent {
  background: linear-gradient(135deg, #D9480F 0%, #C0392B 60%, #A02F23 100%);
}
.cd-label {
  font-size: 23rpx;
  color: rgba(255, 255, 255, 0.78);
}
.cd-time {
  margin-top: 10rpx;
  font-size: 62rpx;
  font-weight: 700;
  color: #ffffff;
  letter-spacing: 4rpx;
  line-height: 1.1;
}
.cd-tip {
  margin-top: 8rpx;
  font-size: 21rpx;
  color: rgba(255, 255, 255, 0.7);
}

.amount {
  margin-top: 20rpx;
  background: #fff;
  border-radius: 20rpx;
  padding: 30rpx 26rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: 0 2rpx 10rpx rgba(31, 41, 55, 0.03);
}
.a-label {
  font-size: 24rpx;
  color: #8A8578;
}
.a-num {
  margin-top: 8rpx;
  font-size: 72rpx;
  font-weight: 700;
  color: #C0392B;
  line-height: 1.1;
}
.a-yen {
  font-size: 40rpx;
  margin-right: 2rpx;
}

.block {
  margin-top: 20rpx;
  background: #fff;
  border-radius: 20rpx;
  padding: 4rpx 26rpx;
  box-shadow: 0 2rpx 10rpx rgba(31, 41, 55, 0.03);
}
.pm {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 22rpx 0;
}
.pm-left {
  display: flex;
  align-items: center;
  gap: 16rpx;
  min-width: 0;
}
.pm-ico {
  font-size: 36rpx;
}
.pm-info {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}
.pm-name {
  font-size: 27rpx;
  color: #1F2937;
  font-weight: 600;
}
.pm-bal {
  font-size: 22rpx;
  color: #9CA3AF;
}
.pm-lack {
  color: #C0392B;
}
.pm-check {
  width: 40rpx;
  height: 40rpx;
  line-height: 40rpx;
  text-align: center;
  border-radius: 50%;
  background: #0F6E56;
  color: #fff;
  font-size: 24rpx;
  flex-shrink: 0;
}

.kv {
  display: flex;
  justify-content: space-between;
  gap: 28rpx;
  padding: 18rpx 0;
  font-size: 25rpx;
  border-bottom: 1rpx solid #F5F3EE;
}
.kv > text:first-child {
  color: #8A8578;
  flex-shrink: 0;
}
.kv > text:last-child {
  color: #1F2937;
  text-align: right;
  word-break: break-all;
}
.kv:last-child {
  border-bottom: none;
}
.ono {
  font-size: 23rpx;
  color: #6B7280;
}
.minus {
  color: #C0392B;
}

.ops {
  margin-top: 30rpx;
  display: flex;
  gap: 16rpx;
}
.btn {
  flex: 1;
  height: 88rpx;
  line-height: 88rpx;
  text-align: center;
  border-radius: 44rpx;
  font-size: 30rpx;
  font-weight: 600;
  margin: 0;
}
.btn.ghost {
  background: #fff;
  color: #4B5563;
  border: 1rpx solid #E2DED4;
}
.btn.main {
  background: #0F6E56;
  color: #fff;
}
.btn.main[disabled] {
  background: #C9D5D0;
  color: #fff;
}

.recharge-tip {
  margin-top: 20rpx;
  text-align: center;
  font-size: 24rpx;
  color: #C0392B;
}
</style>
