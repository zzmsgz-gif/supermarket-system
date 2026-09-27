<template>
  <view class="search-page">
    <view class="bar">
      <input
        class="input"
        v-model="keyword"
        placeholder="搜索商品名称/品牌"
        confirm-type="search"
        @confirm="doSearch"
      />
      <view class="btn" @click="doSearch">搜索</view>
    </view>

    <view class="filter" v-if="categories.length">
      <view
        class="f-all"
        :class="{ active: !activeCat }"
        @click="pickCat(null)"
      >全部</view>
      <scroll-view scroll-x class="f-scroll">
        <view
          v-for="c in categories"
          :key="c.id"
          class="f-cat"
          :class="{ active: activeCat === c.id }"
          @click="pickCat(c.id)"
        >{{ c.name }}</view>
      </scroll-view>
    </view>

    <view class="result" v-if="products.length">
      <view
        v-for="p in products"
        :key="p.id"
        class="card"
        @click="goDetail(p.id)"
      >
        <image class="cover" :src="fullUrl(p.coverUrl)" mode="aspectFill" />
        <view class="name">{{ p.name }}</view>
        <view class="price">
          <text class="now">¥{{ p.price }}</text>
          <text class="origin" v-if="showOrigin(p)">¥{{ p.originalPrice }}</text>
        </view>
      </view>
    </view>

    <view class="loading" v-if="loading">加载中…</view>
    <view class="more" v-else-if="hasMore" @click="loadMore">点击加载更多</view>
    <view class="empty" v-if="!loading && !products.length">没有找到相关商品</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onReachBottom } from '@dcloudio/uni-app'
import { listProducts, listCategories } from '@/api/product'
import { fullUrl } from '@/config'

const keyword = ref('')
const activeCat = ref(null)
const categories = ref([])
const products = ref([])
const page = ref(0)
const size = ref(20)
const total = ref(0)
const loading = ref(false)
const hasMore = ref(false)

function showOrigin(p) {
  return p.originalPrice != null && Number(p.originalPrice) > Number(p.price || 0)
}

async function doSearch() {
  page.value = 0
  products.value = []
  total.value = 0
  await loadMore()
}

async function loadMore() {
  if (loading.value) return
  loading.value = true
  try {
    const res = await listProducts({
      keyword: keyword.value || undefined,
      categoryId: activeCat.value || undefined,
      page: page.value,
      size: size.value
    })
    const items = (res && res.items) ? res.items : []
    total.value = (res && res.total) || 0
    products.value = page.value === 0 ? items : products.value.concat(items)
    page.value += 1
    hasMore.value = products.value.length < total.value
  } catch (e) {
    uni.showToast({ title: (e && e.message) || '搜索失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function pickCat(id) {
  activeCat.value = id
  doSearch()
}

function goDetail(id) {
  uni.navigateTo({ url: '/pages/product/detail?id=' + id })
}

onLoad(async (opts) => {
  if (opts.keyword) keyword.value = opts.keyword
  if (opts.categoryId) activeCat.value = Number(opts.categoryId)
  listCategories().then((cs) => (categories.value = cs || [])).catch(() => (categories.value = []))
  doSearch()
})

onReachBottom(loadMore)
</script>

<style scoped>
.search-page {
  padding-bottom: 32rpx;
}
.bar {
  display: flex;
  align-items: center;
  padding: 16rpx;
  background: #07c160;
}
.input {
  flex: 1;
  background: #fff;
  border-radius: 40rpx;
  padding: 16rpx 24rpx;
  font-size: 26rpx;
}
.btn {
  color: #fff;
  font-size: 28rpx;
  padding: 0 24rpx;
}
.filter {
  display: flex;
  align-items: center;
  background: #fff;
  padding: 12rpx 16rpx;
}
.f-all {
  font-size: 26rpx;
  padding: 8rpx 20rpx;
  border-radius: 32rpx;
  color: #666;
}
.f-all.active {
  background: #07c160;
  color: #fff;
}
.f-scroll {
  flex: 1;
  white-space: nowrap;
}
.f-cat {
  display: inline-block;
  font-size: 26rpx;
  padding: 8rpx 20rpx;
  margin-left: 12rpx;
  border-radius: 32rpx;
  color: #666;
}
.f-cat.active {
  background: #07c160;
  color: #fff;
}
.result {
  display: flex;
  flex-wrap: wrap;
  padding: 8rpx;
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
}
.now {
  color: #e4393c;
  font-weight: bold;
}
.origin {
  color: #999;
  font-size: 22rpx;
  text-decoration: line-through;
  margin-left: 8rpx;
}
.loading,
.more {
  text-align: center;
  color: #999;
  font-size: 24rpx;
  padding: 24rpx 0;
}
.empty {
  text-align: center;
  color: #999;
  font-size: 26rpx;
  padding: 120rpx 0;
}
</style>
