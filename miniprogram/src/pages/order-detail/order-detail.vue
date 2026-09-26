<template>
  <view class="od" v-if="order">
    <view class="status">状态：{{ order.status }}</view>
    <view v-for="it in (order.items || [])" :key="it.id" class="row">
      <text>{{ it.productName || (it.product && it.product.name) }}</text>
      <text>x{{ it.quantity }}</text>
    </view>
    <view class="total">合计 ¥{{ order.totalAmount }}</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getOrder } from '@/api/order'

const order = ref(null)

onLoad(async (opts) => {
  order.value = await getOrder(opts.id)
})
</script>

<style scoped>
.od {
  padding: 16rpx;
}
.status {
  background: #fff;
  padding: 24rpx;
  border-radius: 12rpx;
  font-weight: bold;
}
.row {
  display: flex;
  justify-content: space-between;
  background: #fff;
  margin-top: 16rpx;
  padding: 24rpx;
  border-radius: 12rpx;
}
.total {
  padding: 24rpx;
  font-size: 32rpx;
  font-weight: bold;
  color: #e4393c;
}
</style>
