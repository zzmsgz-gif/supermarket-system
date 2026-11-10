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
        <view class="status-wrap">
          <!-- 有售后时把售后进度也标出来：光看「已完成」看不出退款走到哪一步 -->
          <text
            class="refund-tag"
            v-if="o.refundStatus && o.refundStatus !== 'NONE'"
          >{{ refundStatusText(o.refundStatus) }}</text>
          <text class="status">{{ orderStatusLabel(o.status) }}</text>
        </view>
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
        <!-- 未支付的直接「取消」 -->
        <button
          v-if="o.status === 'PENDING_PAYMENT'"
          class="a ghost"
          @click="cancel(o)"
        >取消</button>
        <!-- 已支付未发货：后端 cancelOrder 允许 PENDING_PAYMENT / PAID，
             这条走「取消订单」而不是售后（货没发，直接退款取消更直接） -->
        <button
          v-if="o.status === 'PAID'"
          class="a ghost"
          @click="cancel(o)"
        >取消订单</button>
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
        <!-- 售后：后端 applyRefund 只允许「已收货(COMPLETED)」申请
             （2026-10-07 收紧，未发货请走取消订单），申请中则置灰 -->
        <button
          v-if="o.status === 'COMPLETED' && o.refundStatus !== 'APPLYING'"
          class="a ghost"
          @click="applyAftersales(o)"
        >申请售后</button>
        <button
          v-if="o.status === 'COMPLETED' && o.refundStatus === 'APPLYING'"
          class="a ghost"
          disabled
        >售后审核中</button>
      </view>
    </view>

    <!-- 空态：不再是干巴巴一行「暂无订单」，给一句贴合当前 tab 的话 + 出口 -->
    <view class="empty-wrap" v-if="!loading && !orders.length">
      <view class="empty-ico">📋</view>
      <text class="empty-title">{{ emptyTitle }}</text>
      <text class="empty-sub">{{ emptySub }}</text>
      <view class="empty-btn" @click="goHome">去逛逛</view>
    </view>

    <!-- 空态也给点可买的，别让用户卡在死胡同 -->
    <view class="reco" v-if="!loading && !orders.length && recommends.length">
      <view class="reco-head">
        <text class="reco-line"></text>
        <text class="reco-title">大家都在买</text>
        <text class="reco-line"></text>
      </view>
      <view class="reco-grid">
        <view class="reco-card" v-for="p in recommends" :key="p.id" @click="openProduct(p)">
          <image class="reco-img" :src="fullUrl(p.coverUrl)" mode="aspectFill" />
          <view class="reco-body">
            <text class="reco-name">{{ p.name }}</text>
            <view class="reco-foot">
              <text class="reco-price">¥{{ p.price }}</text>
              <text class="reco-sales">{{ p.sales || 0 }} 人买过</text>
            </view>
          </view>
        </view>
      </view>
    </view>

    <view class="loading" v-if="loading">加载中…</view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow, onReachBottom } from '@dcloudio/uni-app'
import { listOrders, payOrder, cancelOrder, confirmReceipt, reorder, refundApply } from '@/api/order'
import { listHotProducts } from '@/api/product'
import { orderStatusLabel, fulfillmentLabel } from '@/utils/format'
import { isLoggedIn } from '@/store/auth'
import { fullUrl } from '@/config'

const tabs = [
  { key: 'ALL', label: '全部' },
  { key: 'PENDING_PAYMENT', label: '待付款' },
  { key: 'PAID', label: '待发货' },
  { key: 'SHIPPED', label: '待收货' },
  { key: 'COMPLETED', label: '已完成' },
  // 售后不是订单状态，而是 refundStatus != NONE —— 后端 buildUserOrderSpec 已支持这个特殊值
  { key: 'AFTER_SALE', label: '售后' }
]

// 退款/售后状态中文（对应后端 7 值：NONE / APPLYING / APPROVED / REJECTED /
// WAITING_SHIP / SHIPPED_BACK / PARTIAL）
const REFUND_STATUS_TEXT = {
  APPLYING: '售后审核中',
  APPROVED: '售后已通过',
  REJECTED: '售后被驳回',
  WAITING_SHIP: '待寄回',
  SHIPPED_BACK: '已寄回',
  PARTIAL: '部分退款'
}

function refundStatusText(status) {
  return REFUND_STATUS_TEXT[status] || ''
}

// —— 空态文案：按当前 tab 说人话，比统一一句「暂无订单」有用 ——
const EMPTY_COPY = {
  ALL: ['还没有任何订单', '挑点新鲜的，下单后可以在这里跟踪进度'],
  PENDING_PAYMENT: ['没有待付款的订单', '该付的都付了，轻松'],
  PAID: ['没有待发货的订单', '已付款的订单会在这里等仓库拣货'],
  SHIPPED: ['没有待收货的订单', '发货后的订单会出现在这里'],
  COMPLETED: ['还没有已完成的订单', '确认收货后就能在这里评价和申请售后'],
  AFTER_SALE: ['没有售后中的订单', '希望一直用不上，有问题随时可以申请']
}

const emptyTitle = computed(() => {
  const c = EMPTY_COPY[activeTab.value]
  return c ? c[0] : '暂无订单'
})
const emptySub = computed(() => {
  const c = EMPTY_COPY[activeTab.value]
  return c ? c[1] : ''
})

// 空态推荐（拉热销，仅空列表时拉一次）
const recommends = ref([])

async function loadRecommends() {
  if (recommends.value.length) return
  try {
    recommends.value = await listHotProducts(6)
  } catch (e) {
    recommends.value = []
  }
}

function goHome() {
  uni.switchTab({ url: '/pages/index/index' })
}

function openProduct(p) {
  uni.navigateTo({ url: '/pages/product/detail?id=' + p.id })
}

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
    const params = { page: page.value + 1, size: size.value }
    if (activeTab.value !== 'ALL') params.status = activeTab.value
    const res = await listOrders(params)
    const items = (res && res.items) ? res.items : []
    total.value = (res && res.total) || 0
    orders.value = page.value === 0 ? items : orders.value.concat(items)
    page.value += 1
    hasMore.value = orders.value.length < total.value
    // 空列表才拉推荐（切 tab 会反复触发 load，靠 loadRecommends 内部去重）
    if (!orders.value.length) loadRecommends()
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

function pay(o) {
  // 统一进收银台（有倒计时 + 余额校验），不再在列表里直接扣款
  uni.navigateTo({ url: '/pages/pay/pay?id=' + o.id })
}

/**
 * 申请售后。
 * ⚠️ 后端 applyRefund 只接受「已完成(COMPLETED)」订单（未发货请走取消订单）。
 * ⚠️ uni-app 的 showModal 在 H5 下，用户**没输入**时 res.content 会回填 placeholderText，
 *    直接拿它当原因会存进"请填写售后原因…"这种废话（实测踩到），所以要和 placeholder 比对。
 */
const AFTERSALES_PLACEHOLDER = '填写售后原因（必填），如：商品破损 / 少件 / 质量问题'

function applyAftersales(o) {
  uni.showModal({
    title: '申请售后',
    content: '',
    editable: true,
    placeholderText: AFTERSALES_PLACEHOLDER,
    success: async (r) => {
      if (!r.confirm) return
      const reason = (r.content || '').trim()
      if (!reason || reason === AFTERSALES_PLACEHOLDER) {
        uni.showToast({ title: '请填写售后原因', icon: 'none' })
        return
      }
      uni.showLoading({ title: '提交中' })
      try {
        await refundApply(o.id, { reason })
        uni.hideLoading()
        uni.showToast({ title: '已提交售后申请', icon: 'none' })
        load(true)
      } catch (e) {
        uni.hideLoading()
        uni.showToast({ title: (e && e.message) || '提交失败', icon: 'none' })
      }
    }
  })
}

async function cancel(o) {
  uni.showModal({
    title: '取消订单',
    content: '确定要取消该订单吗？',    success: async (r) => {
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
  // 从个人中心「我的订单」四宫格进来时带了预选 tab：
  // orders 是 tabBar 页，switchTab 不支持参数，所以用 Storage 中转，取一次就清掉
  const pending = uni.getStorageSync('orders_pending_tab')
  if (pending) {
    uni.removeStorageSync('orders_pending_tab')
    if (tabs.some((t) => t.key === pending)) activeTab.value = pending
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
  font-size: 24rpx;
  color: #666;
}
.tab.active {
  color: #0F6E56;
  font-weight: bold;
  border-bottom: 4rpx solid #0F6E56;
}
/* 订单号右侧：售后进度标签 + 订单状态 */
.status-wrap {
  display: flex;
  align-items: center;
  gap: 10rpx;
}
.refund-tag {
  font-size: 20rpx;
  color: #C0392B;
  background: #FDECEA;
  border-radius: 6rpx;
  padding: 2rpx 10rpx;
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
  color: #0F6E56;
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
  color: #C0392B;
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
  background: #0F6E56;
  color: #fff;
}
.a.pay {
  background: #C0392B;
  color: #fff;
}
.a.ghost {
  background: #fff;
  color: #666;
  border: 1rpx solid #ddd;
}
/* ---------- 空态（不再是一行灰字） ---------- */
.empty-wrap {
  padding: 90rpx 60rpx 40rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.empty-ico {
  width: 150rpx;
  height: 150rpx;
  line-height: 150rpx;
  text-align: center;
  border-radius: 50%;
  background: #F1F7F4;
  font-size: 68rpx;
}
.empty-title {
  margin-top: 30rpx;
  font-size: 30rpx;
  font-weight: 600;
  color: #1F2937;
}
.empty-sub {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #9CA3AF;
  text-align: center;
  line-height: 1.6;
}
.empty-btn {
  margin-top: 34rpx;
  width: 260rpx;
  height: 76rpx;
  line-height: 76rpx;
  text-align: center;
  background: #0F6E56;
  color: #ffffff;
  border-radius: 38rpx;
  font-size: 28rpx;
  font-weight: 600;
}

/* ---------- 空态推荐（与购物车/详情页同一套视觉） ---------- */
.reco {
  margin: 8rpx 24rpx 0;
}
.reco-head {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  margin-bottom: 20rpx;
}
.reco-line {
  width: 60rpx;
  height: 1rpx;
  background: #E5E1D8;
}
.reco-title {
  font-size: 26rpx;
  font-weight: 600;
  color: #4B5563;
}
.reco-grid {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
}
.reco-card {
  width: 330rpx;
  margin-bottom: 20rpx;
  background: #fff;
  border-radius: 18rpx;
  overflow: hidden;
  box-shadow: 0 2rpx 12rpx rgba(15, 110, 86, 0.04);
}
.reco-img {
  width: 330rpx;
  height: 240rpx;
  background: #f2efe8;
}
.reco-body {
  padding: 14rpx 16rpx 18rpx;
}
.reco-name {
  font-size: 24rpx;
  color: #1F2937;
  line-height: 1.35;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  height: 66rpx;
}
.reco-foot {
  margin-top: 10rpx;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}
.reco-price {
  color: #C0392B;
  font-size: 28rpx;
  font-weight: 700;
}
.reco-sales {
  font-size: 20rpx;
  color: #9CA3AF;
}
.loading {
  text-align: center;
  color: #999;
  font-size: 24rpx;
  padding: 24rpx 0;
}
</style>
