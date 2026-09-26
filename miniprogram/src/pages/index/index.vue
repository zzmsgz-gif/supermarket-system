<template>
  <view class="home">
    <view class="search" @click="goSearch">搜索商品</view>

    <scroll-view scroll-x class="cats">
      <view
        v-for="c in categories"
        :key="c.id"
        class="cat"
        :class="{ active: c.id === activeCat }"
        @click="pickCat(c.id)"
      >{{ c.name }}</view>
    </scroll-view>

    <view class="grid">
      <view
        v-for="p in products"
        :key="p.id"
        class="card"
        @click="goDetail(p.id)"
      >
        <image class="cover" :src="fullUrl(p.coverUrl)" mode="aspectFill" />
        <view class="name">{{ p.name }}</view>
        <view class="price">¥{{ p.price }}</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { listCategories, listProducts } from '@/api/product'
import { isLoggedIn } from '@/store/auth'
import { fullUrl } from '@/config'

const categories = ref([])
const products = ref([])
const activeCat = ref(null)

async function loadProducts() {
  const res = await listProducts({ categoryId: activeCat.value || undefined, size: 20 })
  products.value = (res && res.content) ? res.content : (res || [])
}

async function load() {
  categories.value = await listCategories()
  await loadProducts()
}

function pickCat(id) {
  activeCat.value = id
  loadProducts()
}

function goDetail(id) {
  uni.navigateTo({ url: '/pages/product/detail?id=' + id })
}

function goSearch() {
  uni.showToast({ title: '搜索待实现', icon: 'none' })
}

onShow(() => {
  if (!isLoggedIn()) {
    uni.navigateTo({ url: '/pages/login/login' })
    return
  }
  load()
})
</script>

<style scoped>
.search {
  margin: 16rpx;
  padding: 16rpx;
  background: #fff;
  border-radius: 40rpx;
  color: #999;
  text-align: center;
}
.cats {
  white-space: nowrap;
  padding: 0 16rpx 16rpx;
}
.cat {
  display: inline-block;
  padding: 8rpx 24rpx;
  margin-right: 12rpx;
  background: #fff;
  border-radius: 32rpx;
  font-size: 26rpx;
}
.cat.active {
  background: #07c160;
  color: #fff;
}
.grid {
  display: flex;
  flex-wrap: wrap;
  padding: 0 8rpx;
}
.card {
  width: 50%;
  box-sizing: border-box;
  padding: 8rpx;
}
.cover {
  width: 100%;
  height: 320rpx;
  background: #eee;
  border-radius: 12rpx;
}
.name {
  padding: 8rpx;
  font-size: 26rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.price {
  padding: 0 8rpx 8rpx;
  color: #e4393c;
  font-weight: bold;
}
</style>
