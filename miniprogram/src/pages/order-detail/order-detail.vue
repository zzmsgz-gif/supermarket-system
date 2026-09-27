<template>
  <view class="od" v-if="order">
    <view class="status-bar">
      <text class="status">{{ orderStatusLabel(order.status) }}</text>
      <text class="pay-deadline" v-if="order.status === 'PENDING_PAYMENT' && order.payDeadline">
        请在 {{ order.payDeadline }} 前支付
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

    <view class="block" v-if="order.fulfillmentType === 'PICKUP'">
      <view class="kv"><text>取货方式</text><text>{{ fulfillmentLabel(order.fulfillmentType) }}</text></view>
      <view class="kv"><text>自提门店</text><text>{{ order.pickupStoreName }}</text></view>
      <view class="kv" v-if="order.pickupCode"><text>自提码</text><text class="code">{{ order.pickupCode }}</text></view>
    </view>

    <view class="block">
      <view class="kv"><text>商品金额</text><text>¥{{ order.totalAmount }}</text></view>
      <view class="kv" v-if="order.discountAmount"><text>优惠</text><text>-¥{{ order.discountAmount }}</text></view>
      <view class="kv" v-if="order.freightAmount"><text>运费</text><text>¥{{ order.freightAmount }}</text></view>
      <view class="kv total"><text>实付</text><text class="amt">¥{{ order.payAmount }}</text></view>
    </view>

    <view class="actions" v-if="order.status === 'PENDING_PAYMENT'">
      <button class="pay" @click="pay">去支付</button>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getOrder, payOrder } from '@/api/order'
import { orderStatusLabel, fulfillmentLabel } from '@/utils/format'
import { fullUrl } from '@/config'

const order = ref(null)

onLoad(async (opts) => {
  order.value = await getOrder(opts.id)
})

async function pay() {
  uni.showLoading({ title: '支付中' })
  try {
    // 后端 POST /orders/{id}/pay 无 body，默认走钱包余额 BALANCE
    await payOrder(order.value.id)
    uni.hideLoading()
    uni.showToast({ title: '支付成功', icon: 'success' })
    order.value = await getOrder(order.value.id)
  } catch (e) {
    uni.hideLoading()
    uni.showToast({ title: (e && e.message) || '支付失败', icon: 'none' })
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
}
.pay {
  background: #07c160;
  color: #fff;
  border-radius: 48rpx;
}
</style>
