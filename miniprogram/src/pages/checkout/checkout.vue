<template>
  <view class="checkout" v-if="items.length">
    <view class="list">
      <view v-for="it in items" :key="it.id" class="row">
        <text class="nm">{{ it.product && it.product.name }}</text>
        <text class="qy">x{{ it.quantity }}</text>
      </view>
    </view>

    <view class="total">合计 ¥{{ total }}</view>
    <view class="channel">支付方式：钱包余额（首期未接入微信支付）</view>

    <button class="pay" @click="pay">提交订单并支付</button>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getCart } from '@/api/cart'
import { createOrder, payOrder } from '@/api/order'

const items = ref([])

const total = computed(() =>
  items.value.reduce((s, it) => s + (it.product ? it.product.price * it.quantity : 0), 0)
)

onLoad(async () => {
  items.value = await getCart()
})

async function pay() {
  const payload = {
    items: items.value.map((it) => ({ productId: it.product.id, quantity: it.quantity })),
    channel: 'BALANCE'
  }
  uni.showLoading({ title: '提交中' })
  try {
    const order = await createOrder(payload)
    await payOrder(order.id, 'BALANCE')
    uni.hideLoading()
    uni.showToast({ title: '支付成功', icon: 'success' })
    uni.redirectTo({ url: '/pages/orders/orders' })
  } catch (e) {
    uni.hideLoading()
    uni.showToast({ title: (e && e.message) || '下单失败', icon: 'none' })
  }
}
</script>

<style scoped>
.list {
  background: #fff;
  margin: 16rpx;
  border-radius: 12rpx;
}
.row {
  display: flex;
  justify-content: space-between;
  padding: 20rpx 24rpx;
  border-bottom: 1rpx solid #f2f2f2;
}
.total {
  padding: 24rpx;
  font-size: 32rpx;
  font-weight: bold;
  color: #e4393c;
}
.channel {
  padding: 0 24rpx 24rpx;
  color: #999;
  font-size: 24rpx;
}
.pay {
  margin: 24rpx;
  background: #07c160;
  color: #fff;
  border-radius: 48rpx;
}
</style>
