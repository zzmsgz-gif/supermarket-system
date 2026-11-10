<template>
  <view class="od" v-if="order" :style="{ paddingTop: (statusBarHeight + 44) + 'px' }">
    <!-- 自定义导航栏：支付成功后是 redirectTo 进来的，页面栈里只有自己，
         原生导航栏不会显示返回箭头（用户会卡在这一页），所以自绘一个 -->
    <view class="nav" :style="{ paddingTop: statusBarHeight + 'px' }">
      <view class="nav-bar">
        <view class="nav-back" @click="goBack">
          <text class="nav-arrow">‹</text>
          <text class="nav-back-text">订单</text>
        </view>
        <text class="nav-title">订单详情</text>
        <view class="nav-holder"></view>
      </view>
    </view>

    <!-- 状态块：图标 + 主状态 + 一句说明，替换原来那块空荡荡的纯色横幅 -->
    <view class="status-hero">
      <view class="sh-main">
        <view class="sh-ico-wrap"><text class="sh-ico">{{ statusIcon }}</text></view>
        <view class="sh-text">
          <text class="sh-title">{{ orderStatusLabel(order.status) }}</text>
          <text class="sh-sub">{{ statusHint }}</text>
        </view>
      </view>
      <view class="sh-deadline" v-if="order.status === 'PENDING_PAYMENT' && order.payDeadline">
        请在 {{ formatTime(order.payDeadline) }} 前完成支付
      </view>
    </view>

    <view class="block">
      <view v-for="it in (order.items || [])" :key="it.id" class="row">
        <image class="cover" :src="fullUrl(it.productCoverUrl)" mode="aspectFill" />
        <view class="info">
          <view class="name">{{ it.productName }}</view>
          <view class="spec" v-if="it.skuSpec">{{ it.skuSpec }}</view>
          <view class="meta">¥{{ it.productPrice }} × {{ it.quantity }}</view>
          <!-- 该商品行的售后进度：一笔订单里不同商品可以各走各的售后 -->
          <view class="line-ret" v-if="returnOfItem(it.id)" @click.stop="goReturns">
            <text class="lr-tag" :class="statusClass(returnOfItem(it.id).status)">
              {{ returnOfItem(it.id).statusLabel || returnOfItem(it.id).status }}
            </text>
            <text class="lr-more">查看进度 ›</text>
          </view>
        </view>
        <view class="sub">¥{{ it.subtotalAmount }}</view>
        <!-- 按行申请售后：只在该行没有在途售后时出现 -->
        <view class="line-op" v-if="canApplyItem(it.id)" @click.stop="applyRefund(it)">售后</view>
      </view>
    </view>

    <!-- 履约信息 -->
    <view class="block" v-if="order.fulfillmentType === 'PICKUP'">
      <view class="kv"><text>取货方式</text><text>{{ fulfillmentLabel(order.fulfillmentType) }}</text></view>
      <view class="kv"><text>自提门店</text><text>{{ order.pickupStoreName }}</text></view>
      <view class="kv" v-if="order.pickupCode"><text>自提码</text><text class="code">{{ order.pickupCode }}</text></view>
    </view>
    <view class="block" v-if="order.fulfillmentType === 'INSTANT' || order.fulfillmentType === 'EXPRESS'">
      <view class="kv"><text>配送方式</text><text>{{ fulfillmentLabel(order.fulfillmentType) }}</text></view>
      <view class="kv"><text>收货人</text><text>{{ order.receiverName }} {{ order.receiverPhone }}</text></view>
      <view class="kv"><text>收货地址</text><text class="addr">{{ order.receiverAddress }}</text></view>
      <view class="kv" v-if="order.deliverySlot"><text>配送时段</text><text>{{ order.deliverySlot }}</text></view>
      <view class="kv" v-if="order.fulfillmentType === 'EXPRESS' && order.shipCompany">
        <text>物流</text><text>{{ order.shipCompany }} {{ order.shipNo }}</text>
      </view>
    </view>

    <view class="block">
      <view class="kv"><text>商品金额</text><text>¥{{ order.totalAmount }}</text></view>
      <view class="kv" v-if="order.discountAmount"><text>优惠</text><text class="minus">-¥{{ order.discountAmount }}</text></view>
      <view class="kv" v-if="order.activityDiscount"><text>活动优惠</text><text class="minus">-¥{{ order.activityDiscount }}</text></view>
      <view class="kv" v-if="order.freightAmount"><text>运费</text><text>¥{{ order.freightAmount }}</text></view>
      <view class="kv" v-if="order.pointsDiscountAmount"><text>积分抵扣</text><text class="minus">-¥{{ order.pointsDiscountAmount }}</text></view>
      <view class="kv total"><text>实付</text><text class="amt">¥{{ order.payAmount }}</text></view>
    </view>

    <!-- 订单信息：客服对接、售后申诉都要用到订单号，原来这一页完全看不到 -->
    <view class="block">
      <view class="kv">
        <text>订单编号</text>
        <view class="kv-flex">
          <text class="order-no">{{ order.orderNo }}</text>
          <text class="copy" @click="copyOrderNo">复制</text>
        </view>
      </view>
      <view class="kv" v-if="order.createdAt"><text>下单时间</text><text>{{ formatTime(order.createdAt) }}</text></view>
      <view class="kv" v-if="order.paidAt"><text>支付时间</text><text>{{ formatTime(order.paidAt) }}</text></view>
      <view class="kv" v-if="order.shippedAt"><text>发货时间</text><text>{{ formatTime(order.shippedAt) }}</text></view>
      <view class="kv" v-if="order.completedAt"><text>完成时间</text><text>{{ formatTime(order.completedAt) }}</text></view>
      <view class="kv" v-if="order.canceledAt"><text>取消时间</text><text>{{ formatTime(order.canceledAt) }}</text></view>
      <view class="kv" v-if="order.remark"><text>订单备注</text><text>{{ order.remark }}</text></view>
    </view>

    <view class="actions">
      <button v-if="order.status === 'PENDING_PAYMENT'" class="a pay" @click="pay">去支付</button>
      <button v-if="order.status === 'PENDING_PAYMENT'" class="a ghost" @click="cancelOrderAction">取消</button>
      <!-- 已支付未发货：走取消订单（后端 cancelOrder 允许 PENDING_PAYMENT / PAID） -->
      <button v-if="order.status === 'PAID'" class="a ghost" @click="cancelOrderAction">取消订单</button>
      <button v-if="order.status === 'SHIPPED'" class="a main" @click="confirm">确认收货</button>
      <button v-if="order.status === 'COMPLETED'" class="a main" @click="reorderOrder">再来一单</button>
      <button v-if="order.status === 'COMPLETED'" class="a ghost" @click="openReview">评价</button>
      <!-- 售后：后端 applyRefund 只允许已收货(COMPLETED)申请，申请中置灰 -->
      <button
        v-if="order.status === 'COMPLETED' && order.refundStatus !== 'APPLYING'"
        class="a ghost"
        @click="applyRefund"
      >申请售后</button>
      <button
        v-if="order.status === 'COMPLETED' && order.refundStatus === 'APPLYING'"
        class="a ghost"
        disabled
      >售后审核中</button>
    </view>

    <!-- 评价弹层 -->
    <view class="mask" v-if="showReview" @click="showReview = false">
      <view class="sheet" @click.stop>
        <view class="sheet-t">发表评价</view>
        <view class="stars">
          <text
            v-for="n in 5"
            :key="n"
            class="star"
            :class="{ on: n <= reviewRating }"
            @click="reviewRating = n"
          >★</text>
        </view>
        <textarea class="ta" v-model="reviewContent" placeholder="说说这件商品的使用感受…" />
        <view class="sheet-actions">
          <button class="a ghost" @click="showReview = false">取消</button>
          <button class="a main" @click="submitReviewOrder">提交</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getOrder, payOrder, confirmReceipt, reorder, cancelOrder } from '@/api/order'
import { applyReturn, listReturnsByOrder } from '@/api/returns'
import { submitReview } from '@/api/review'
import { orderStatusLabel, fulfillmentLabel, formatTime } from '@/utils/format'
import { fullUrl } from '@/config'

const order = ref(null)
const showReview = ref(false)
const reviewRating = ref(5)
const reviewContent = ref('')
// 本单的全部售后单（按商品行并行售后时会有多条），用于在商品行上显示进度
const itemReturns = ref([])

// 没有「在途」售后的商品行才允许申请（已驳回的可以重新申请）
const IN_FLIGHT = ['APPLYING', 'WAITING_SHIP', 'SHIPPED_BACK', 'APPROVED']

function returnOfItem(itemId) {
  if (!itemId) return null
  return itemReturns.value.find((r) => Number(r.orderItemId) === Number(itemId)) || null
}
function canApplyItem(itemId) {
  if (!order.value || order.value.status !== 'COMPLETED') return false
  const r = returnOfItem(itemId)
  return !r || r.status === 'REJECTED'
}
function statusClass(s) {
  if (s === 'REJECTED') return 'lr-reject'
  if (s === 'APPROVED') return 'lr-done'
  return 'lr-wait'
}
function goReturns() {
  uni.navigateTo({ url: '/pages/returns/returns' })
}

async function loadItemReturns() {
  if (!order.value) return
  try {
    itemReturns.value = (await listReturnsByOrder(order.value.id)) || []
  } catch (e) {
    itemReturns.value = []
  }
}

// 自定义导航栏要自己让开状态栏
const statusBarHeight = ref((uni.getSystemInfoSync().statusBarHeight) || 20)

// 状态块：图标 + 一句人话，别让用户只看到一个干巴巴的「待发货」
const STATUS_VIEW = {
  PENDING_PAYMENT: { icon: '⏰', hint: '尽快完成支付，超时订单会自动关闭' },
  PAID: { icon: '📦', hint: '商家正在拣货打包，发货后会有通知' },
  SHIPPED: { icon: '🚚', hint: '包裹已发出，收货后记得确认' },
  COMPLETED: { icon: '✅', hint: '交易已完成，有问题可申请售后' },
  CANCELED: { icon: '⚪', hint: '订单已取消，金额已按原路退回' },
  CLOSED: { icon: '⚪', hint: '订单已关闭' }
}

const statusIcon = computed(() => (STATUS_VIEW[order.value?.status] || {}).icon || '📋')
const statusHint = computed(() => (STATUS_VIEW[order.value?.status] || {}).hint || '')

/**
 * 返回上一页；但如果是 redirectTo 进来的（页面栈只有自己），
 * 直接回「我的订单」——否则点了没反应，用户会卡死在详情页。
 */
function goBack() {
  const pages = getCurrentPages()
  if (pages && pages.length > 1) {
    uni.navigateBack()
  } else {
    uni.switchTab({ url: '/pages/orders/orders' })
  }
}

// 订单号一键复制（客服/售后沟通要用）
function copyOrderNo() {
  const no = (order.value && order.value.orderNo) || ''
  if (!no) return
  uni.setClipboardData({
    data: no,
    success: () => uni.showToast({ title: '订单号已复制', icon: 'none' })
  })
}

onLoad(async (opts) => {
  order.value = await getOrder(opts.id)
  // 售后单单独拉，失败不影响订单主体展示
  loadItemReturns()
})

// 详情页「去支付」统一进收银台：能看倒计时和余额，余额不足可先去充值
function pay() {
  uni.navigateTo({ url: '/pages/pay/pay?id=' + order.value.id })
}

async function confirm() {
  uni.showModal({
    title: '确认收货',
    content: '确认已收到商品？',
    success: async (r) => {
      if (!r.confirm) return
      try {
        await confirmReceipt(order.value.id)
        uni.showToast({ title: '已确认', icon: 'success' })
        order.value = await getOrder(order.value.id)
      } catch (e) {
        uni.showToast({ title: (e && e.message) || '操作失败', icon: 'none' })
      }
    }
  })
}

// 取消订单：后端 cancelOrder 允许「待付款 / 待发货」，已支付的原路退回
function cancelOrderAction() {
  uni.showModal({
    title: '取消订单',
    content: '确定要取消该订单吗？已支付的金额将原路退回。',
    success: async (r) => {
      if (!r.confirm) return
      uni.showLoading({ title: '处理中' })
      try {
        await cancelOrder(order.value.id)
        uni.hideLoading()
        uni.showToast({ title: '已取消', icon: 'none' })
        order.value = await getOrder(order.value.id)
      } catch (e) {
        uni.hideLoading()
        uni.showToast({ title: (e && e.message) || '取消失败', icon: 'none' })
      }
    }
  })
}

// ⚠️ uni-app 的 showModal 在 H5 下，用户没输入时 res.content 会回填 placeholderText，
// 直接当原因提交会存进一句废话，所以要和 placeholder 对比。
const AFTERSALES_PLACEHOLDER = '填写售后原因（必填），如：商品破损 / 少件 / 质量问题'

function applyRefund(item) {
  // 传商品行 => 按行售后（只退这一件）；不传 => 整单
  const target = item && item.id ? item : null
  const shortName = target ? String(target.productName || '').slice(0, 10) : ''
  uni.showModal({
    title: target ? `售后：${shortName}` : '申请售后（整单）',
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
      try {
        await applyReturn({
          orderId: order.value.id,
          orderItemId: target ? target.id : null,
          reason,
          type: 'REFUND'
        })
        uni.showToast({ title: '已提交售后申请', icon: 'none' })
        order.value = await getOrder(order.value.id)
        await loadItemReturns()
      } catch (e) {
        uni.showToast({ title: (e && e.message) || '操作失败', icon: 'none' })
      }
    }
  })
}

async function reorderOrder() {
  uni.showLoading({ title: '处理中' })
  try {
    const res = await reorder(order.value.id)
    const added = (res && res.addedCount) || 0
    uni.hideLoading()
    uni.showToast({ title: '已加入购物车 ' + added + ' 件', icon: 'none' })
  } catch (e) {
    uni.hideLoading()
    uni.showToast({ title: (e && e.message) || '操作失败', icon: 'none' })
  }
}

function openReview() {
  reviewRating.value = 5
  reviewContent.value = ''
  showReview.value = true
}

async function submitReviewOrder() {
  if (!reviewContent.value.trim()) {
    uni.showToast({ title: '写点评价吧', icon: 'none' })
    return
  }
  uni.showLoading({ title: '提交中' })
  try {
    await submitReview(order.value.id, {
      rating: reviewRating.value,
      content: reviewContent.value.trim()
    })
    uni.hideLoading()
    showReview.value = false
    uni.showToast({ title: '评价成功', icon: 'success' })
  } catch (e) {
    uni.hideLoading()
    uni.showToast({ title: (e && e.message) || '提交失败', icon: 'none' })
  }
}
</script>

<style scoped>
.od {
  /* padding-top 由模板内联给出（状态栏 + 44px 导航栏高度），这里只管左右和底部 */
  padding-left: 16rpx;
  padding-right: 16rpx;
  padding-bottom: 160rpx;
  min-height: 100vh;
  box-sizing: border-box;
  background: #FAF8F3;
}

/* ---------- 自定义导航栏 ---------- */
.nav {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  background: #0F6E56;
}
.nav-bar {
  height: 44px;
  display: flex;
  align-items: center;
  padding: 0 24rpx 0 16rpx;
}
.nav-back {
  min-width: 150rpx;
  display: flex;
  align-items: center;
  gap: 6rpx;
  height: 44px;
}
.nav-arrow {
  font-size: 42rpx;
  line-height: 1;
  color: #ffffff;
}
.nav-back-text {
  font-size: 27rpx;
  color: rgba(255, 255, 255, 0.92);
}
.nav-title {
  flex: 1;
  text-align: center;
  font-size: 32rpx;
  font-weight: 600;
  color: #ffffff;
}
/* 右侧占位：让标题真正居中，同时避开微信胶囊按钮 */
.nav-holder {
  min-width: 150rpx;
}

/* ---------- 状态块 ---------- */
.status-hero {
  margin-top: 16rpx;
  background: linear-gradient(135deg, #12725C 0%, #0F6E56 55%, #0B5A46 100%);
  border-radius: 20rpx;
  padding: 28rpx 26rpx;
  color: #ffffff;
  box-shadow: 0 8rpx 24rpx rgba(15, 110, 86, 0.16);
}
.sh-main {
  display: flex;
  align-items: center;
}
.sh-ico-wrap {
  width: 76rpx;
  height: 76rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.16);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 20rpx;
  flex-shrink: 0;
}
.sh-ico {
  font-size: 38rpx;
  line-height: 1;
}
.sh-text {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.sh-title {
  font-size: 34rpx;
  font-weight: 700;
  letter-spacing: 1rpx;
}
.sh-sub {
  margin-top: 8rpx;
  font-size: 23rpx;
  color: rgba(255, 255, 255, 0.78);
  line-height: 1.45;
}
.sh-deadline {
  margin-top: 18rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid rgba(255, 255, 255, 0.18);
  font-size: 23rpx;
  color: #FFE9B8;
}
.block {
  background: #fff;
  margin-top: 20rpx;
  padding: 4rpx 26rpx;
  border-radius: 20rpx;
  box-shadow: 0 2rpx 10rpx rgba(31, 41, 55, 0.03);
}
.row {
  display: flex;
  align-items: center;
  padding: 20rpx 0;
  border-bottom: 1rpx solid #F5F3EE;
}
.row:last-child {
  border-bottom: none;
}
/* 商品行上的售后进度 + 按行申请按钮 */
.line-ret {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin-top: 8rpx;
}
.lr-tag {
  font-size: 19rpx;
  padding: 2rpx 10rpx;
  border-radius: 6rpx;
  color: #B26A00;
  background: #FFF4E5;
}
.lr-reject {
  color: #6B7280;
  background: #F1F2F4;
}
.lr-done {
  color: #0F6E56;
  background: #E9F4EF;
}
.lr-wait {
  color: #C0392B;
  background: #FDECEA;
}
.lr-more {
  font-size: 20rpx;
  color: #0F6E56;
}
.line-op {
  flex-shrink: 0;
  margin-left: 12rpx;
  padding: 6rpx 18rpx;
  font-size: 22rpx;
  color: #0F6E56;
  border: 1rpx solid #CBDED6;
  border-radius: 24rpx;
  line-height: 1.4;
}
.cover {
  width: 120rpx;
  height: 120rpx;
  background: #eee;
  border-radius: 8rpx;
  flex-shrink: 0;
}
.info {
  flex: 1;
  padding: 0 16rpx;
  min-width: 0;
}
.name {
  font-size: 28rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.spec {
  font-size: 22rpx;
  color: #999;
  margin-top: 4rpx;
}
.meta {
  font-size: 24rpx;
  color: #C0392B;
  margin-top: 8rpx;
}
.sub {
  color: #333;
  font-size: 26rpx;
}
.kv {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  /* 标签与值之间留出固定呼吸位：原来没有 gap，地址一长就贴到标签上 */
  gap: 28rpx;
  padding: 18rpx 0;
  font-size: 26rpx;
  border-bottom: 1rpx solid #F5F3EE;
}
/* 左侧标签：固定宽度不压缩，颜色弱化 */
.kv > text:first-child {
  flex: 0 0 auto;
  width: 150rpx;
  color: #8A8578;
  line-height: 1.5;
}
/* 右侧值：右对齐、可换行 */
.kv > text:last-child {
  flex: 1;
  min-width: 0;
  text-align: right;
  color: #1F2937;
  line-height: 1.5;
  word-break: break-all;
}
.kv:last-child {
  border-bottom: none;
}
.kv.total {
  font-weight: bold;
  border-bottom: none;
}
.kv.total > text:first-child {
  color: #1F2937;
}
/* 订单号那一行：值区是 flex，号码 + 复制按钮 */
.kv-flex {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12rpx;
}
.order-no {
  font-size: 25rpx;
  color: #1F2937;
  word-break: break-all;
  text-align: right;
  line-height: 1.5;
}
.copy {
  flex-shrink: 0;
  font-size: 21rpx;
  color: #0F6E56;
  border: 1rpx solid #CBDED6;
  border-radius: 20rpx;
  padding: 2rpx 14rpx;
  line-height: 1.6;
}
.minus {
  color: #C0392B;
}
.amt {
  color: #C0392B;
  font-size: 32rpx;
  font-weight: 700;
}
.code {
  color: #0F6E56;
  font-weight: bold;
  letter-spacing: 2rpx;
}
.actions {
  margin-top: 24rpx;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  flex-wrap: wrap;
  gap: 16rpx;
}
/* uni-button 默认 display:block + margin:auto，会吃掉 flex 的对齐，
   导致 justify-content:flex-end 名存实亡（按钮仍在左边）。
   显式 flex:0 0 auto 才能让它真正靠右。 */
.actions .a {
  flex: 0 0 auto;
  margin: 0;
}
.a {
  font-size: 26rpx;
  padding: 12rpx 32rpx;
  margin-left: 16rpx;
  border-radius: 32rpx;
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
.mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: flex-end;
  z-index: 100;
}
.sheet {
  width: 100%;
  background: #fff;
  border-radius: 24rpx 24rpx 0 0;
  padding: 32rpx;
}
.sheet-t {
  font-size: 30rpx;
  font-weight: bold;
  text-align: center;
  margin-bottom: 16rpx;
}
.stars {
  text-align: center;
}
.star {
  font-size: 48rpx;
  color: #ddd;
  margin: 0 8rpx;
}
.star.on {
  color: #ff9800;
}
.ta {
  width: 100%;
  height: 160rpx;
  background: #f5f5f5;
  border-radius: 12rpx;
  padding: 16rpx;
  margin-top: 16rpx;
  box-sizing: border-box;
  font-size: 26rpx;
}
.sheet-actions {
  display: flex;
  margin-top: 24rpx;
}
.sheet-actions .a {
  flex: 1;
  margin: 0 8rpx;
}
</style>
