<template>
  <view class="orders">
    <view v-for="o in orders" :key="o.id" class="row" @click="goDetail(o.id)">
      <view class="left">
        <text class="oid">订单 {{ o.orderNo || ('#' + o.id) }}</text>
        <text class="meta">{{ fulfillmentLabel(o.fulfillmentType) }} · 共 {{ (o.items || []).length }} 件</text>
      </view>
      <view class="right">
        <text class="status">{{ orderStatusLabel(o.status) }}</text>
        <text class="amt">¥{{ o.payAmount != null ? o.payAmount : o.totalAmount }}</text>
      </view>
    </view>
    <view class="empty" v-if="orders.length === 0">暂无订单</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { listOrders } from '@/api/order'
import { orderStatusLabel, fulfillmentLabel } from '@/utils/format'
import { isLoggedIn } from '@/store/auth'

const orders = ref([])

async function load() {
  if (!isLoggedIn()) {
    uni.navigateTo({ url: '/pages/login/login' })
    return
  }
  // 后端 GET /orders → PageResponse<OrderResponse>{ content: [...] }
  const res = await listOrders()
  orders.value = (res && res.content) ? res.content : []
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
  align-items: center;
  background: #fff;
  margin: 16rpx;
  padding: 24rpx;
  border-radius: 12rpx;
}
.left {
  display: flex;
  flex-direction: column;
}
.oid {
  font-size: 28rpx;
  font-weight: bold;
}
.meta {
  font-size: 22rpx;
  color: #999;
  margin-top: 8rpx;
}
.right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}
.status {
  color: #07c160;
  font-size: 26rpx;
}
.amt {
  color: #e4393c;
  font-weight: bold;
  margin-top: 8rpx;
}
.empty {
  text-align: center;
  color: #999;
  padding: 120rpx 0;
}
</style>
