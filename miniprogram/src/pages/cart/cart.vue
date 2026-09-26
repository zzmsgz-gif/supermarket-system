<template>
  <view class="cart">
    <view v-for="it in items" :key="it.id" class="row">
      <image class="cover" :src="fullUrl(it.product && it.product.coverUrl)" mode="aspectFill" />
      <view class="info">
        <view class="name">{{ it.product && it.product.name }}</view>
        <view class="price">¥{{ it.product && it.product.price }}</view>
      </view>
      <view class="qty">
        <text class="btn" @click="dec(it)">-</text>
        <text class="num">{{ it.quantity }}</text>
        <text class="btn" @click="inc(it)">+</text>
      </view>
    </view>

    <view class="empty" v-if="items.length === 0">购物车是空的</view>

    <view class="footer" v-if="items.length">
      <text class="sum">合计 ¥{{ total }}</text>
      <button class="checkout" @click="goCheckout">去结算</button>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getCart, updateCartItem, removeCartItem } from '@/api/cart'
import { isLoggedIn } from '@/store/auth'
import { fullUrl } from '@/config'

const items = ref([])

const total = computed(() =>
  items.value.reduce((s, it) => s + (it.product ? it.product.price * it.quantity : 0), 0)
)

async function load() {
  if (!isLoggedIn()) {
    uni.navigateTo({ url: '/pages/login/login' })
    return
  }
  const res = await getCart()
  items.value = res || []
}

function inc(it) {
  updateCartItem(it.id, it.quantity + 1).then(load)
}
function dec(it) {
  if (it.quantity <= 1) {
    removeCartItem(it.id).then(load)
  } else {
    updateCartItem(it.id, it.quantity - 1).then(load)
  }
}
function goCheckout() {
  uni.navigateTo({ url: '/pages/checkout/checkout' })
}

onShow(() => load())
</script>

<style scoped>
.row {
  display: flex;
  align-items: center;
  background: #fff;
  margin: 16rpx;
  padding: 16rpx;
  border-radius: 12rpx;
}
.cover {
  width: 140rpx;
  height: 140rpx;
  background: #eee;
  border-radius: 8rpx;
}
.info {
  flex: 1;
  padding: 0 16rpx;
}
.name {
  font-size: 28rpx;
}
.price {
  color: #e4393c;
  margin-top: 12rpx;
}
.qty {
  display: flex;
  align-items: center;
}
.btn {
  width: 56rpx;
  height: 56rpx;
  line-height: 52rpx;
  text-align: center;
  border: 1rpx solid #ddd;
  font-size: 32rpx;
}
.num {
  padding: 0 20rpx;
}
.empty {
  text-align: center;
  color: #999;
  padding: 120rpx 0;
}
.footer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 24rpx;
  background: #fff;
  border-top: 1rpx solid #eee;
}
.sum {
  font-size: 32rpx;
  font-weight: bold;
  color: #e4393c;
}
.checkout {
  background: #07c160;
  color: #fff;
  border-radius: 48rpx;
  margin: 0;
}
</style>
