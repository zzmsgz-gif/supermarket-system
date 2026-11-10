<template>
  <view class="rt">
    <!-- 筛选：后端没有 status 参数，这里只做前端分组 -->
    <view class="tabs">
      <view
        v-for="t in tabs"
        :key="t.key"
        class="tab"
        :class="{ active: activeTab === t.key }"
        @click="switchTab(t.key)"
      >{{ t.label }}</view>
    </view>

    <view
      v-for="r in filtered"
      :key="r.id"
      class="card"
      @click="goOrder(r.orderId)"
    >
      <view class="head">
        <text class="ono">订单 {{ r.orderNo }}</text>
        <text class="st" :class="statusClass(r.status)">{{ r.statusLabel || statusText(r.status) }}</text>
      </view>

      <view class="item">
        <image
          v-if="r.itemCoverUrl"
          class="cover"
          :src="fullUrl(r.itemCoverUrl)"
          mode="aspectFill"
        />
        <view v-else class="cover cover-empty"><text class="ce-t">整单</text></view>
        <view class="info">
          <text class="name">{{ r.itemProductName || '整单退款（未指定单件商品）' }}</text>
          <text class="qty" v-if="r.itemQuantity && r.itemUnitPrice">× {{ r.itemQuantity }} · ¥{{ r.itemUnitPrice }}/件</text>
          <text class="qty" v-else-if="r.itemQuantity">共 {{ r.itemQuantity }} 件</text>
          <text class="qty" v-else>早期整单售后，未记录具体商品</text>
        </view>
        <view class="amt">
          <text class="amt-label">退款</text>
          <text class="amt-val">¥{{ r.refundAmount }}</text>
        </view>
      </view>

      <view class="kv" v-if="r.reason">
        <text class="k">申请原因</text>
        <text class="v">{{ r.reason }}</text>
      </view>
      <view class="kv" v-if="r.adminRemark">
        <text class="k">商家说明</text>
        <text class="v">{{ r.adminRemark }}</text>
      </view>
      <view class="kv" v-if="r.expressCompany">
        <text class="k">寄回物流</text>
        <text class="v">{{ r.expressCompany }} {{ r.trackingNo }}</text>
      </view>
      <view class="kv" v-if="r.frozenPoints">
        <text class="k">关联积分</text>
        <text class="v">{{ r.frozenPoints }} 分（审核通过时按规则扣回）</text>
      </view>

      <view class="next" v-if="r.nextStep">{{ r.nextStep }}</view>

      <view class="ops" v-if="r.status === 'WAITING_SHIP'">
        <button class="btn" @click.stop="openShip(r)">填写寄回物流</button>
      </view>
    </view>

    <view class="empty" v-if="!loading && !filtered.length">
      <view class="e-ico">🧾</view>
      <text class="e-title">{{ emptyTitle }}</text>
      <text class="e-sub">{{ emptySub }}</text>
      <view class="e-btn" @click="goOrders">去我的订单</view>
    </view>

    <view class="loading" v-if="loading">加载中…</view>

    <!-- 寄回物流弹层：两个输入框，uni.showModal 只有单输入，所以自绘 -->
    <view class="mask" v-if="shipOpen" @click="shipOpen = false">
      <view class="sheet" @click.stop>
        <text class="sheet-title">填写寄回物流</text>
        <text class="sheet-sub">{{ shipItem }}</text>
        <view class="field">
          <text class="f-label">快递公司</text>
          <input class="f-input" v-model="shipForm.expressCompany" placeholder="如：顺丰速运" />
        </view>
        <view class="field">
          <text class="f-label">运单号</text>
          <input class="f-input" v-model="shipForm.trackingNo" placeholder="请输入快递单号" />
        </view>
        <view class="sheet-ops">
          <button class="btn ghost" @click="shipOpen = false">取消</button>
          <button class="btn main" :disabled="shipSubmitting" @click="submitShip">
            {{ shipSubmitting ? '提交中…' : '提交' }}
          </button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow, onReachBottom } from '@dcloudio/uni-app'
import { listReturns, shipBackReturn } from '@/api/returns'
import { fullUrl } from '@/config'
import { isLoggedIn } from '@/store/auth'

const tabs = [
  { key: 'ALL', label: '全部' },
  { key: 'DOING', label: '处理中' },
  { key: 'DONE', label: '已完成' }
]

// 后端五态：APPLYING 待审核 / WAITING_SHIP 待寄回 / SHIPPED_BACK 已寄回 /
// APPROVED 已通过 / REJECTED 已驳回（statusLabel 后端也会给，这里只做兜底）
const STATUS_TEXT = {
  APPLYING: '待审核',
  WAITING_SHIP: '待寄回',
  SHIPPED_BACK: '已寄回',
  APPROVED: '已通过',
  REJECTED: '已驳回'
}
const DOING = ['APPLYING', 'WAITING_SHIP', 'SHIPPED_BACK']

const activeTab = ref('ALL')
const list = ref([])
const page = ref(0)
const size = ref(20)
const total = ref(0)
const loading = ref(false)

const shipOpen = ref(false)
const shipSubmitting = ref(false)
const shipTarget = ref(null)
const shipItem = ref('')
const shipForm = ref({ expressCompany: '', trackingNo: '' })

function statusText(s) {
  return STATUS_TEXT[s] || s || ''
}
function statusClass(s) {
  if (s === 'REJECTED') return 'st-reject'
  if (s === 'APPROVED') return 'st-done'
  if (s === 'WAITING_SHIP' || s === 'SHIPPED_BACK') return 'st-wait'
  return 'st-doing'
}

const filtered = computed(() => {
  if (activeTab.value === 'DOING') return list.value.filter((r) => DOING.includes(r.status))
  if (activeTab.value === 'DONE') return list.value.filter((r) => r.status === 'APPROVED' || r.status === 'REJECTED')
  return list.value
})

const emptyTitle = computed(() => {
  if (activeTab.value === 'DOING') return '没有处理中的售后'
  if (activeTab.value === 'DONE') return '还没有已完结的售后'
  return '还没有售后记录'
})
const emptySub = computed(() => {
  if (activeTab.value === 'DOING') return '提交的售后会在这里显示进度'
  if (activeTab.value === 'DONE') return '通过或驳回的售后会归档到这里'
  return '收货后如有问题，可在订单详情申请售后'
})

async function load(reset = false) {
  if (loading.value) return
  if (reset) {
    page.value = 0
    list.value = []
  }
  loading.value = true
  try {
    const res = await listReturns({ page: page.value + 1, size: size.value })
    const items = (res && res.items) || []
    total.value = (res && res.total) || 0
    list.value = page.value === 0 ? items : list.value.concat(items)
    page.value += 1
  } catch (e) {
    uni.showToast({ title: (e && e.message) || '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function switchTab(key) {
  if (activeTab.value === key) return
  activeTab.value = key
}

function goOrder(orderId) {
  if (!orderId) return
  uni.navigateTo({ url: '/pages/order-detail/order-detail?id=' + orderId })
}
function goOrders() {
  uni.switchTab({ url: '/pages/orders/orders' })
}

function openShip(r) {
  shipTarget.value = r
  shipItem.value = r.itemProductName + (r.itemQuantity ? ' × ' + r.itemQuantity : '')
  shipForm.value = { expressCompany: '', trackingNo: '' }
  shipOpen.value = true
}

async function submitShip() {
  const company = (shipForm.value.expressCompany || '').trim()
  const no = (shipForm.value.trackingNo || '').trim()
  if (!company) {
    uni.showToast({ title: '请填写快递公司', icon: 'none' })
    return
  }
  if (!no) {
    uni.showToast({ title: '请填写运单号', icon: 'none' })
    return
  }
  shipSubmitting.value = true
  try {
    await shipBackReturn(shipTarget.value.id, { expressCompany: company, trackingNo: no })
    shipOpen.value = false
    uni.showToast({ title: '已提交寄回信息', icon: 'none' })
    load(true)
  } catch (e) {
    uni.showToast({ title: (e && e.message) || '提交失败', icon: 'none' })
  } finally {
    shipSubmitting.value = false
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
  if (list.value.length < total.value && !loading.value) load()
})
</script>

<style scoped>
.rt {
  min-height: 100vh;
  background: #FAF8F3;
  padding: 0 16rpx 40rpx;
}
.tabs {
  display: flex;
  background: #fff;
  position: sticky;
  top: 0;
  z-index: 10;
  border-radius: 0 0 16rpx 16rpx;
}
.tab {
  flex: 1;
  text-align: center;
  padding: 22rpx 0;
  font-size: 26rpx;
  color: #666;
}
.tab.active {
  color: #0F6E56;
  font-weight: 600;
  border-bottom: 4rpx solid #0F6E56;
}

.card {
  margin-top: 20rpx;
  background: #fff;
  border-radius: 20rpx;
  padding: 20rpx 24rpx;
  box-shadow: 0 2rpx 10rpx rgba(31, 41, 55, 0.03);
}
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 14rpx;
  border-bottom: 1rpx solid #F5F3EE;
}
.ono {
  font-size: 24rpx;
  color: #8A8578;
}
.st {
  font-size: 23rpx;
  font-weight: 600;
  padding: 2rpx 14rpx;
  border-radius: 8rpx;
}
.st-doing {
  color: #B26A00;
  background: #FFF4E5;
}
.st-wait {
  color: #C0392B;
  background: #FDECEA;
}
.st-done {
  color: #0F6E56;
  background: #E9F4EF;
}
.st-reject {
  color: #6B7280;
  background: #F1F2F4;
}

.item {
  display: flex;
  align-items: center;
  padding: 18rpx 0;
}
.cover {
  width: 100rpx;
  height: 100rpx;
  border-radius: 10rpx;
  background: #f2efe8;
  flex-shrink: 0;
  margin-right: 18rpx;
}
/* 整单售后没有商品图时的占位（早期数据没存商品快照） */
.cover-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #F4F1EA;
}
.ce-t {
  font-size: 22rpx;
  color: #B0A88F;
}
.info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.name {
  font-size: 26rpx;
  color: #1F2937;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.qty {
  font-size: 22rpx;
  color: #9CA3AF;
}
.amt {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  flex-shrink: 0;
  margin-left: 16rpx;
}
.amt-label {
  font-size: 20rpx;
  color: #9CA3AF;
}
.amt-val {
  font-size: 28rpx;
  font-weight: 700;
  color: #C0392B;
}

.kv {
  display: flex;
  align-items: flex-start;
  gap: 24rpx;
  padding: 12rpx 0;
  border-top: 1rpx solid #F9F7F2;
}
.k {
  flex: 0 0 auto;
  width: 150rpx;
  font-size: 24rpx;
  color: #8A8578;
}
.v {
  flex: 1;
  min-width: 0;
  font-size: 24rpx;
  color: #4B5563;
  line-height: 1.5;
  word-break: break-all;
}

.next {
  margin-top: 12rpx;
  padding: 14rpx 18rpx;
  background: #F4FAF7;
  border-radius: 12rpx;
  font-size: 23rpx;
  color: #0F6E56;
  line-height: 1.5;
}

.ops {
  display: flex;
  justify-content: flex-end;
  margin-top: 16rpx;
}
.btn {
  font-size: 26rpx;
  padding: 10rpx 30rpx;
  border-radius: 30rpx;
  background: #0F6E56;
  color: #fff;
  margin: 0;
  line-height: 1.5;
}
.btn.ghost {
  background: #fff;
  color: #666;
  border: 1rpx solid #ddd;
}
.btn.main {
  background: #0F6E56;
  color: #fff;
}

.empty {
  padding: 100rpx 60rpx 40rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.e-ico {
  width: 150rpx;
  height: 150rpx;
  line-height: 150rpx;
  text-align: center;
  border-radius: 50%;
  background: #F1F7F4;
  font-size: 66rpx;
}
.e-title {
  margin-top: 28rpx;
  font-size: 30rpx;
  font-weight: 600;
  color: #1F2937;
}
.e-sub {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #9CA3AF;
  text-align: center;
  line-height: 1.6;
}
.e-btn {
  margin-top: 32rpx;
  width: 260rpx;
  height: 76rpx;
  line-height: 76rpx;
  text-align: center;
  background: #0F6E56;
  color: #fff;
  border-radius: 38rpx;
  font-size: 28rpx;
  font-weight: 600;
}

.loading {
  text-align: center;
  color: #9CA3AF;
  font-size: 24rpx;
  padding: 30rpx 0;
}

.mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: flex-end;
  z-index: 999;
}
.sheet {
  width: 100%;
  background: #fff;
  border-radius: 24rpx 24rpx 0 0;
  padding: 30rpx 28rpx calc(30rpx + env(safe-area-inset-bottom));
}
.sheet-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #1F2937;
}
.sheet-sub {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #9CA3AF;
}
.field {
  margin-top: 24rpx;
}
.f-label {
  font-size: 24rpx;
  color: #8A8578;
}
.f-input {
  margin-top: 10rpx;
  height: 80rpx;
  background: #F7F5F0;
  border-radius: 12rpx;
  padding: 0 20rpx;
  font-size: 26rpx;
  color: #1F2937;
}
.sheet-ops {
  display: flex;
  justify-content: flex-end;
  gap: 16rpx;
  margin-top: 30rpx;
}
</style>
