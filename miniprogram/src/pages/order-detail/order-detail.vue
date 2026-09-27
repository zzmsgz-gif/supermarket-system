<template>
  <view class="od" v-if="order">
    <view class="status-bar">
      <text class="status">{{ orderStatusLabel(order.status) }}</text>
      <text class="pay-deadline" v-if="order.status === 'PENDING_PAYMENT' && order.payDeadline">
        请在 {{ formatTime(order.payDeadline) }} 前支付
      </text>
    </view>

    <view class="block">
      <view v-for="it in (order.items || [])" :key="it.id" class="row">
        <image class="cover" :src="fullUrl(it.productCoverUrl)" mode="aspectFill" />
        <view class="info">
          <view class="name">{{ it.productName }}</view>
          <view class="spec" v-if="it.skuSpec">{{ it.skuSpec }}</view>
          <view class="meta">¥{{ it.productPrice }} × {{ it.quantity }}</view>
        </view>
        <view class="sub">¥{{ it.subtotalAmount }}</view>
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
      <view class="kv" v-if="order.discountAmount"><text>优惠</text><text>-¥{{ order.discountAmount }}</text></view>
      <view class="kv" v-if="order.freightAmount"><text>运费</text><text>¥{{ order.freightAmount }}</text></view>
      <view class="kv" v-if="order.pointDiscountAmount"><text>积分抵扣</text><text>-¥{{ order.pointDiscountAmount }}</text></view>
      <view class="kv total"><text>实付</text><text class="amt">¥{{ order.payAmount }}</text></view>
    </view>

    <view class="actions">
      <button v-if="order.status === 'PENDING_PAYMENT'" class="a pay" @click="pay">去支付</button>
      <button v-if="order.status === 'SHIPPED'" class="a main" @click="confirm">确认收货</button>
      <button
        v-if="order.status === 'PAID' || order.status === 'SHIPPED'"
        class="a ghost"
        @click="applyRefund"
      >申请退款</button>
      <button v-if="order.status === 'COMPLETED'" class="a main" @click="reorderOrder">再来一单</button>
      <button v-if="order.status === 'COMPLETED'" class="a ghost" @click="openReview">评价</button>
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
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getOrder, payOrder, confirmReceipt, refundApply, reorder } from '@/api/order'
import { submitReview } from '@/api/review'
import { orderStatusLabel, fulfillmentLabel, formatTime } from '@/utils/format'
import { fullUrl } from '@/config'

const order = ref(null)
const showReview = ref(false)
const reviewRating = ref(5)
const reviewContent = ref('')

onLoad(async (opts) => {
  order.value = await getOrder(opts.id)
})

async function pay() {
  uni.showLoading({ title: '支付中' })
  try {
    await payOrder(order.value.id)
    uni.hideLoading()
    uni.showToast({ title: '支付成功', icon: 'success' })
    order.value = await getOrder(order.value.id)
  } catch (e) {
    uni.hideLoading()
    uni.showToast({ title: (e && e.message) || '支付失败', icon: 'none' })
  }
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

function applyRefund() {
  uni.showModal({
    title: '申请退款',
    content: '请简要说明退款原因',
    editable: true,
    placeholderText: '如：商品质量问题',
    success: async (r) => {
      if (!r.confirm) return
      try {
        await refundApply(order.value.id, { reason: r.content || '' })
        uni.showToast({ title: '已提交退款申请', icon: 'none' })
        order.value = await getOrder(order.value.id)
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
  padding: 16rpx;
  padding-bottom: 160rpx;
}
.status-bar {
  background: #07c160;
  color: #fff;
  padding: 24rpx;
  border-radius: 12rpx;
  display: flex;
  flex-direction: column;
}
.status {
  font-size: 32rpx;
  font-weight: bold;
}
.pay-deadline {
  font-size: 24rpx;
  margin-top: 8rpx;
}
.block {
  background: #fff;
  margin-top: 16rpx;
  padding: 16rpx 24rpx;
  border-radius: 12rpx;
}
.row {
  display: flex;
  align-items: center;
  padding: 16rpx 0;
  border-bottom: 1rpx solid #f2f2f2;
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
  color: #e4393c;
  margin-top: 8rpx;
}
.sub {
  color: #333;
  font-size: 26rpx;
}
.kv {
  display: flex;
  justify-content: space-between;
  padding: 14rpx 0;
  font-size: 26rpx;
  border-bottom: 1rpx solid #f2f2f2;
}
.kv .addr {
  max-width: 480rpx;
  text-align: right;
}
.kv.total {
  font-weight: bold;
  border-bottom: none;
}
.code {
  color: #07c160;
  font-weight: bold;
  letter-spacing: 2rpx;
}
.amt {
  color: #e4393c;
  font-weight: bold;
}
.actions {
  margin-top: 24rpx;
  display: flex;
  justify-content: flex-end;
}
.a {
  font-size: 26rpx;
  padding: 12rpx 32rpx;
  margin-left: 16rpx;
  border-radius: 32rpx;
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
