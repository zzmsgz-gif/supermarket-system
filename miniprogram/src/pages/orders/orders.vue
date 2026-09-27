<template>
  <view class="orders">
    <view class="tabs">
      <view
        v-for="t in tabs"
        :key="t.key"
        class="tab"
        :class="{ active: activeTab === t.key }"
        @click="switchTab(t.key)"
      >{{ t.label }}</view>
    </view>

    <view v-for="o in orders" :key="o.id" class="row" @click="goDetail(o.id)">
      <view class="head">
        <text class="oid">订单 {{ o.orderNo || ('#' + o.id) }}</text>
        <text class="status">{{ orderStatusLabel(o.status) }}</text>
      </view>
      <view class="items">
        <view v-for="it in (o.items || []).slice(0, 3)" :key="it.id" class="mini">
          <image class="m-cover" :src="fullUrl(it.productCoverUrl)" mode="aspectFill" />
          <text class="m-name">{{ it.productName }}</text>
        </view>
        <text v-if="(o.items || []).length > 3" class="more">等{{ (o.items || []).length }}件</text>
      </view>
      <view class="meta">
        <text>{{ fulfillmentLabel(o.fulfillmentType) }} · 共 {{ (o.items || []).length }} 件</text>
        <text class="amt">实付 ¥{{ o.payAmount != null ? o.payAmount : o.totalAmount }}</text>
      </view>
      <view class="actions" @click.stop>
        <button
          v-if="o.status === 'PENDING_PAYMENT'"
          class="a pay"
          @click="pay(o)"
        >去支付</button>
        <button
          v-if="o.status === 'PENDING_PAYMENT'"
          class="a ghost"
          @click="cancel(o)"
        >取消</button>
        <button
          v-if="o.status === 'PAID' || o.status === 'SHIPPED'"
          class="a ghost"
          @click="goDetail(o.id)"
        >申请退款</button>
        <button
          v-if="o.status === 'SHIPPED'"
          class="a main"
          @click="confirm(o)"
        >确认收货</button>
        <button
          v-if="o.status === 'COMPLETED'"
          class="a main"
          @click="reorderOrder(o)"
        >再来一单</button>
        <button
          v-if="o.status === 'COMPLETED'"
          class="a ghost"
          @click="goDetail(o.id)"
        >评价</button>
      </view>
    </view>

    <view class="empty" v-if="!loading && !orders.length">暂无订单</view>
    <view class="loading" v-if="loading">加载中…</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow, onReachBottom } from '@dcloudio/uni-app'
import { listOrders, payOrder, cancelOrder, confirmReceipt, reorder } from '@/api/order'
import { orderStatusLabel, fulfillmentLabel } from '@/utils/format'
import { isLoggedIn } from '@/store/auth'
import { fullUrl } from '@/config'

const tabs = [
  { key: 'ALL', label: '全部' },
  { key: 'PENDING_PAYMENT', label: '待付款' },
  { key: 'PAID', label: '待发货' },
  { key: 'SHIPPED', label: '待收货' },
  { key: 'COMPLETED', label: '已完成' }
]
const activeTab = ref('ALL')
const orders = ref([])
const page = ref(0)
const size = ref(20)
const total = ref(0)
const loading = ref(false)
const hasMore = ref(false)

async function load(reset = false) {
  if (loading.value) return
  if (reset) {
    page.value = 0
    orders.value = []
  }
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (activeTab.value !== 'ALL') params.status = activeTab.value
    const res = await listOrders(params)
    const items = (res && res.items) ? res.items : []
    total.value = (res && res.total) || 0
    orders.value = page.value === 0 ? items : orders.value.concat(items)
    page.value += 1
    hasMore.value = orders.value.length < total.value
  } catch (e) {
    uni.showToast({ title: (e && e.message) || '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function switchTab(key) {
  if (activeTab.value === key) return
  activeTab.value = key
  load(true)
}

function goDetail(id) {
  uni.navigateTo({ url: '/pages/order-detail/order-detail?id=' + id })
}

async function pay(o) {
  uni.showLoading({ title: '支付中' })
  try {
    await payOrder(o.id)
    uni.hideLoading()
    uni.showToast({ title: '支付成功', icon: 'success' })
    load(true)
  } catch (e) {
    uni.hideLoading()
    uni.showToast({ title: (e && e.message) || '支付失败', icon: 'none' })
  }
}

async function cancel(o) {
  uni.showModal({
    title: '取消订单',
    content: '确定要取消该订单吗？',
    success: async (r) => {
      if (!r.confirm) return
      try {
        await cancelOrder(o.id)
        uni.showToast({ title: '已取消', icon: 'none' })
        load(true)
      } catch (e) {
        uni.showToast({ title: (e && e.message) || '操作失败', icon: 'none' })
      }
    }
  })
}

async function confirm(o) {
  uni.showModal({
    title: '确认收货',
    content: '确认已收到商品？',
    success: async (r) => {
      if (!r.confirm) return
      try {
        await confirmReceipt(o.id)
        uni.showToast({ title: '已确认', icon: 'success' })
        load(true)
      } catch (e) {
        uni.showToast({ title: (e && e.message) || '操作失败', icon: 'none' })
      }
    }
  })
}

async function reorderOrder(o) {
  uni.showLoading({ title: '处理中' })
  try {
    const res = await reorder(o.id)
    const added = (res && res.addedCount) || 0
    const skipped = (res && res.skipped) || []
    uni.hideLoading()
    let msg = '已加入购物车 ' + added + ' 件'
    if (skipped.length) msg += '，' + skipped.length + ' 件不可用'
    uni.showToast({ title: msg, icon: 'none' })
    load(true)
  } catch (e) {
    uni.hideLoading()
    uni.showToast({ title: (e && e.message) || '操作失败', icon: 'none' })
  }
}

onShow(() => {
  if (!isLoggedIn()) {
    uni.navigateTo({ url: '/pages/login/login' })
    return
  }
  load(true)
})

onReachBottom(() => {
  if (hasMore.value) load()
})
</script>

<style scoped>
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
  font-size: 26rpx;
  color: #666;
}
.tab.active {
  color: #07c160;
  font-weight: bold;
  border-bottom: 4rpx solid #07c160;
}
.row {
  background: #fff;
  margin: 16rpx;
  padding: 20rpx 24rpx;
  border-radius: 12rpx;
}
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.oid {
  font-size: 26rpx;
  font-weight: bold;
}
.status {
  color: #07c160;
  font-size: 24rpx;
}
.items {
  display: flex;
  align-items: center;
  padding: 16rpx 0;
  border-bottom: 1rpx solid #f2f2f2;
  overflow-x: auto;
}
.mini {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-right: 16rpx;
  width: 120rpx;
}
.m-cover {
  width: 100rpx;
  height: 100rpx;
  background: #eee;
  border-radius: 8rpx;
}
.m-name {
  font-size: 20rpx;
  color: #666;
  margin-top: 6rpx;
  max-width: 120rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.more {
  font-size: 22rpx;
  color: #999;
}
.meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 22rpx;
  color: #999;
  padding: 12rpx 0;
}
.amt {
  color: #e4393c;
  font-weight: bold;
}
.actions {
  display: flex;
  justify-content: flex-end;
}
.a {
  font-size: 24rpx;
  padding: 8rpx 24rpx;
  margin-left: 16rpx;
  border-radius: 32rpx;
  line-height: 1.4;
}
.a.main {
  background: #07c160;
  color: #fff;
}
.a.pay {
  background: #e4393c;
  color: #fff;
}
.a.ghost {
  background: #fff;
  color: #666;
  border: 1rpx solid #ddd;
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
