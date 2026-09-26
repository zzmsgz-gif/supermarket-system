<template>
  <view class="orders">
    <view v-for="o in orders" :key="o.id" class="row" @click="goDetail(o.id)">
      <text class="oid">订单 #{{ o.id }}</text>
      <text class="status">{{ o.status }}</text>
    </view>
    <view class="empty" v-if="orders.length === 0">暂无订单</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { listOrders } from '@/api/order'
import { isLoggedIn } from '@/store/auth'

const orders = ref([])

async function load() {
  if (!isLoggedIn()) {
    uni.navigateTo({ url: '/pages/login/login' })
    return
  }
  orders.value = await listOrders()
}

function goDetail(id) {
  uni.navigateTo({ url: '/pages/order-detail/order-detail?id=' + id })
}

onShow(() => load())
</script>

<style scoped>
.row {
  display: flex;
  justify-content: space-between;
  background: #fff;
  margin: 16rpx;
  padding: 24rpx;
  border-radius: 12rpx;
}
.oid {
  font-size: 28rpx;
}
.status {
  color: #07c160;
}
.empty {
  text-align: center;
  color: #999;
  padding: 120rpx 0;
}
</style>
