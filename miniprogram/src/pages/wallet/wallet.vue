<template>
  <view class="wallet">
    <view class="balance-card">
      <view class="b-lab">账户余额（元）</view>
      <view class="b-num">¥{{ balance }}</view>
      <button class="recharge" @click="doRecharge">充值</button>
    </view>

    <view class="list-title">交易明细</view>
    <view
      v-for="t in txns"
      :key="t.id"
      class="tx"
    >
      <view class="tx-left">
        <view class="tx-type">{{ txLabel(t.type) }}</view>
        <view class="tx-remark" v-if="t.remark">{{ t.remark }}</view>
        <view class="tx-time">{{ formatTime(t.createdAt) }}</view>
      </view>
      <view class="tx-amt" :class="amtClass(t.amount)">
        {{ Number(t.amount) >= 0 ? '+' : '-' }}¥{{ Math.abs(Number(t.amount || 0)).toFixed(2) }}
      </view>
    </view>

    <view class="empty" v-if="!loading && !txns.length">暂无交易记录</view>
    <view class="loading" v-if="loading">加载中…</view>
    <view class="more" v-else-if="hasMore" @click="loadMore">点击加载更多</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow, onReachBottom } from '@dcloudio/uni-app'
import { getWallet, recharge, listWalletTransactions } from '@/api/wallet'
import { formatTime } from '@/utils/format'

const balance = ref('0.00')
const txns = ref([])
const page = ref(0)
const size = ref(20)
const total = ref(0)
const loading = ref(false)
const hasMore = ref(false)

function txLabel(type) {
  const map = {
    RECHARGE: '充值',
    PAY: '消费',
    ORDER_PAY: '消费',
    REFUND: '退款',
    REFUND_RECEIVE: '退款',
    POINT_REDEEM: '积分抵现'
  }
  return map[type] || type || '交易'
}

function amtClass(amount) {
  return Number(amount) >= 0 ? 'in' : 'out'
}

async function loadWallet() {
  try {
    const w = await getWallet()
    balance.value = Number((w && w.balance) || 0).toFixed(2)
  } catch (e) {
    balance.value = '0.00'
  }
}

async function loadTxns(reset = false) {
  if (loading.value) return
  if (reset) {
    page.value = 0
    txns.value = []
  }
  loading.value = true
  try {
    const res = await listWalletTransactions({ page: page.value, size: size.value })
    const items = (res && res.items) ? res.items : []
    total.value = (res && res.total) || 0
    txns.value = page.value === 0 ? items : txns.value.concat(items)
    page.value += 1
    hasMore.value = txns.value.length < total.value
  } catch (e) {
    uni.showToast({ title: (e && e.message) || '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function doRecharge() {
  uni.showModal({
    title: '充值',
    content: '输入充值金额（元）',
    editable: true,
    placeholderText: '如 100',
    success: async (r) => {
      if (!r.confirm) return
      const amt = Number(r.content)
      if (!amt || amt <= 0) {
        uni.showToast({ title: '金额无效', icon: 'none' })
        return
      }
      uni.showLoading({ title: '充值中' })
      try {
        await recharge({ amount: amt })
        uni.hideLoading()
        uni.showToast({ title: '充值成功', icon: 'success' })
        loadWallet()
        loadTxns(true)
      } catch (e) {
        uni.hideLoading()
        uni.showToast({ title: (e && e.message) || '充值失败', icon: 'none' })
      }
    }
  })
}

onShow(() => {
  loadWallet()
  loadTxns(true)
})
onReachBottom(() => {
  if (hasMore.value) loadTxns()
})
</script>

<style scoped>
.wallet {
  min-height: 100vh;
  background: #f5f5f5;
}
.balance-card {
  background: linear-gradient(135deg, #07c160, #05a050);
  color: #fff;
  padding: 48rpx 32rpx;
  text-align: center;
}
.b-lab {
  font-size: 24rpx;
  opacity: 0.9;
}
.b-num {
  font-size: 56rpx;
  font-weight: bold;
  margin-top: 12rpx;
}
.recharge {
  display: inline-block;
  margin-top: 24rpx;
  background: #fff;
  color: #07c160;
  font-size: 26rpx;
  padding: 8rpx 40rpx;
  border-radius: 40rpx;
  line-height: 1.6;
}
.list-title {
  font-size: 26rpx;
  color: #999;
  padding: 24rpx 32rpx 8rpx;
}
.tx {
  display: flex;
  align-items: center;
  background: #fff;
  margin: 0 16rpx 2rpx;
  padding: 20rpx 24rpx;
}
.tx-left {
  flex: 1;
  min-width: 0;
}
.tx-type {
  font-size: 28rpx;
  font-weight: bold;
}
.tx-remark {
  font-size: 22rpx;
  color: #999;
  margin-top: 6rpx;
}
.tx-time {
  font-size: 22rpx;
  color: #999;
  margin-top: 6rpx;
}
.tx-amt {
  font-size: 30rpx;
  font-weight: bold;
}
.tx-amt.in {
  color: #07c160;
}
.tx-amt.out {
  color: #e4393c;
}
.empty {
  text-align: center;
  color: #999;
  padding: 120rpx 0;
}
.loading,
.more {
  text-align: center;
  color: #999;
  font-size: 24rpx;
  padding: 24rpx 0;
}
</style>
