<template>
  <view class="cart">
    <view v-for="it in items" :key="it.id" class="row">
      <image class="cover" :src="fullUrl(it.productCoverUrl)" mode="aspectFill" />
      <view class="info">
        <view class="name">{{ it.productName }}</view>
        <view class="spec" v-if="it.skuSpec">规格：{{ it.skuSpec }}</view>
        <view class="price" v-if="it.flashSaleId">
          <text class="now">¥{{ it.productPrice }}</text>
          <text class="origin" v-if="showOrigin(it)">¥{{ it.productOriginalPrice }}</text>
          <text class="flash">秒杀</text>
        </view>
        <view class="price" v-else>
          <text class="now">¥{{ it.productPrice }}</text>
          <text class="origin" v-if="showOrigin(it)">¥{{ it.productOriginalPrice }}</text>
        </view>
        <view class="off" v-if="!it.onSale">已下架 / 无货</view>
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

// 后端 CartResponse.items 为扁平字段：productName / productPrice / productOriginalPrice /
// productCoverUrl / skuSpec / quantity / subtotalAmount / flashSaleId / onSale ...
// subtotalAmount 已是「按秒杀价拆分后的精确小计」，直接用它与即可
const total = computed(() =>
  items.value.reduce(
    (s, it) => s + (Number(it.subtotalAmount) || Number(it.productPrice) * it.quantity || 0),
    0
  )
)

function showOrigin(it) {
  return it.productOriginalPrice != null && Number(it.productOriginalPrice) > Number(it.productPrice)
}

async function load() {
  if (!isLoggedIn()) {
    uni.navigateTo({ url: '/pages/login/login' })
    return
  }
  const res = await getCart()
  items.value = (res && res.items) ? res.items : []
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
.cart {
  padding-bottom: 120rpx;
}
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
.price {
  margin-top: 12rpx;
  display: flex;
  align-items: baseline;
}
.now {
  color: #e4393c;
  font-weight: bold;
  font-size: 30rpx;
}
.origin {
  color: #999;
  font-size: 22rpx;
  text-decoration: line-through;
  margin-left: 10rpx;
}
.flash {
  color: #fff;
  background: #e4393c;
  font-size: 20rpx;
  padding: 2rpx 8rpx;
  border-radius: 6rpx;
  margin-left: 10rpx;
}
.off {
  color: #999;
  font-size: 22rpx;
  margin-top: 8rpx;
}
.qty {
  display: flex;
  align-items: center;
  flex-shrink: 0;
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
