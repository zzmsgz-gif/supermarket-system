<template>
  <view class="detail" v-if="product">
    <image class="cover" :src="fullUrl(product.coverUrl)" mode="aspectFill" />
    <view class="name">{{ product.name }}</view>
    <view class="price">¥{{ product.price }}</view>
    <view class="desc">{{ product.description }}</view>
    <view class="bar">
      <button class="add" @click="add">加入购物车</button>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getProduct } from '@/api/product'
import { addToCart } from '@/api/cart'
import { isLoggedIn } from '@/store/auth'
import { fullUrl } from '@/config'

const product = ref(null)

onLoad(async (opts) => {
  product.value = await getProduct(opts.id)
})

function add() {
  if (!isLoggedIn()) {
    uni.navigateTo({ url: '/pages/login/login' })
    return
  }
  addToCart(product.value.id, 1).then(() =>
    uni.showToast({ title: '已加入', icon: 'success' })
  )
}
</script>

<style scoped>
.detail {
  padding: 0 0 120rpx;
}
.cover {
  width: 100%;
  height: 600rpx;
  background: #eee;
}
.name {
  padding: 24rpx;
  font-size: 32rpx;
  font-weight: bold;
}
.price {
  padding: 0 24rpx 12rpx;
  color: #e4393c;
  font-size: 36rpx;
  font-weight: bold;
}
.desc {
  padding: 0 24rpx;
  color: #666;
  font-size: 26rpx;
  line-height: 1.6;
}
.bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 16rpx 24rpx;
  background: #fff;
}
.add {
  background: #07c160;
  color: #fff;
  border-radius: 48rpx;
}
</style>
