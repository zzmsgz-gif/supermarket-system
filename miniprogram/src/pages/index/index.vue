<template>
  <view class="home">
    <!-- 搜索条 -->
    <view class="search" @click="goSearch">
      <text class="s-icon">🔍</text>
      <text class="s-ph">搜索商品</text>
    </view>

    <!-- 轮播 -->
    <swiper
      v-if="banners.length"
      class="banner"
      indicator-dots
      autoplay
      circular
      interval="3500"
      duration="500"
    >
      <swiper-item v-for="b in banners" :key="b.id" @click="onBanner(b)">
        <image class="b-img" :src="fullUrl(b.imageUrl)" mode="aspectFill" />
      </swiper-item>
    </swiper>

    <!-- 热搜词 -->
    <view class="hot" v-if="hotSearches.length">
      <text class="hot-t">热搜</text>
      <view
        v-for="h in hotSearches"
        :key="h.id"
        class="hot-chip"
        @click="goSearch(h.keyword)"
      >{{ h.keyword }}</view>
    </view>

    <!-- 秒杀区 -->
    <view class="flash" v-if="flashSales.length">
      <view class="flash-head">
        <text class="flash-t">限时秒杀</text>
        <text class="flash-more" @click="goSearch('')">更多 ›</text>
      </view>
      <scroll-view scroll-x class="flash-scroll">
        <view
          v-for="f in flashSales"
          :key="f.id"
          class="flash-card"
          @click="goFlash(f)"
        >
          <image class="f-cover" :src="fullUrl(f.productCoverUrl)" mode="aspectFill" />
          <view class="f-name">{{ f.productName }}</view>
          <view class="f-price">¥{{ f.flashPrice }}</view>
          <view class="f-origin">¥{{ f.price }}</view>
          <view class="f-bar">
            <view class="f-bar-in" :style="{ width: (f.progressPercent || 0) + '%' }"></view>
          </view>
          <view class="f-state">
            <text v-if="f.state === 'RUNNING'" class="f-cd">{{ formatCountdown(f.remaining) }}</text>
            <text v-else :class="['f-tag', f.state === 'UPCOMING' ? 'up' : 'end']">{{ flashStateLabel(f.state) }}</text>
          </view>
        </view>
      </scroll-view>
    </view>

    <!-- 分类快捷 -->
    <scroll-view scroll-x class="cats">
      <view
        v-for="c in categories"
        :key="c.id"
        class="cat"
        @click="jumpFloor(c.id)"
      >{{ c.name }}</view>
    </scroll-view>

    <!-- 分类楼层 -->
    <view
      v-for="fl in floors"
      :key="fl.category.id"
      :class="'floor floor-' + fl.category.id"
    >
      <view class="floor-head">
        <text class="floor-t">{{ fl.category.name }}</text>
        <text class="floor-more" @click="goSearch('', fl.category.id)">更多 ›</text>
      </view>
      <view class="grid">
        <view
          v-for="p in fl.products"
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
      <view class="floor-empty" v-if="!fl.products.length">该分类暂无商品</view>
    </view>

    <view class="footer-tip">已经到底啦 ~</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow, onHide, onUnload } from '@dcloudio/uni-app'
import { listCategories, listProducts } from '@/api/product'
import { listBanners } from '@/api/banner'
import { listHotSearches } from '@/api/hotSearch'
import { listFlashSales } from '@/api/flashSale'
import { isLoggedIn } from '@/store/auth'
import { fullUrl } from '@/config'
import { flashStateLabel, formatCountdown } from '@/utils/format'

const categories = ref([])
const banners = ref([])
const hotSearches = ref([])
const flashSales = ref([])
const floors = ref([])

let timer = null

function showOrigin(p) {
  return p.originalPrice != null && Number(p.originalPrice) > Number(p.price || 0)
}

async function load() {
  const [cats, bn, hs, fs] = await Promise.all([
    listCategories(),
    listBanners(),
    listHotSearches(),
    listFlashSales()
  ])
  categories.value = cats || []
  banners.value = (bn || []).filter((b) => b.enabled !== false)
  hotSearches.value = (hs || []).filter((h) => h.enabled !== false)
  flashSales.value = (fs || []).map((f) => ({ ...f, remaining: f.countdownSeconds || 0 }))

  // 分类楼层：每个分类取前 8 件（按 categoryId 分组现场取，避免只用分页第一页截断）
  const floorsData = []
  for (const c of categories.value) {
    try {
      const res = await listProducts({ categoryId: c.id, size: 8 })
      floorsData.push({ category: c, products: (res && res.items) ? res.items : [] })
    } catch (e) {
      floorsData.push({ category: c, products: [] })
    }
  }
  floors.value = floorsData
}

function startTimer() {
  stopTimer()
  timer = setInterval(() => {
    let any = false
    for (const f of flashSales.value) {
      if (f.state === 'RUNNING' && f.remaining > 0) {
        f.remaining -= 1
        any = true
      }
    }
    if (any) flashSales.value = [...flashSales.value]
  }, 1000)
}
function stopTimer() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function goSearch(keyword, categoryId) {
  let url = '/pages/search/search'
  const q = []
  if (keyword) q.push('keyword=' + encodeURIComponent(keyword))
  if (categoryId) q.push('categoryId=' + categoryId)
  if (q.length) url += '?' + q.join('&')
  uni.navigateTo({ url })
}

function goDetail(id) {
  uni.navigateTo({ url: '/pages/product/detail?id=' + id })
}

function onBanner(b) {
  if (b.linkProductId) goDetail(b.linkProductId)
}

function goFlash(f) {
  goDetail(f.productId || f.linkProductId)
}

function jumpFloor(id) {
  // 滚到对应分类楼层（mp-weixin 支持 selector 滚动）
  uni.pageScrollTo({ selector: '.floor-' + id, duration: 300 })
}

onShow(() => {
  if (!isLoggedIn()) {
    uni.navigateTo({ url: '/pages/login/login' })
    return
  }
  load()
  startTimer()
})

onHide(stopTimer)
onUnload(stopTimer)
</script>

<style scoped>
.home {
  padding-bottom: 32rpx;
}
.search {
  display: flex;
  align-items: center;
  margin: 16rpx;
  padding: 16rpx 24rpx;
  background: #fff;
  border-radius: 40rpx;
  color: #999;
}
.s-icon {
  margin-right: 12rpx;
}
.banner {
  height: 280rpx;
  margin: 0 16rpx;
  border-radius: 12rpx;
  overflow: hidden;
}
.b-img {
  width: 100%;
  height: 280rpx;
}
.hot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  padding: 16rpx;
}
.hot-t {
  font-size: 24rpx;
  color: #999;
  margin-right: 12rpx;
}
.hot-chip {
  font-size: 24rpx;
  color: #333;
  background: #fff;
  border-radius: 24rpx;
  padding: 6rpx 20rpx;
  margin: 8rpx 12rpx 8rpx 0;
}
.flash {
  background: #fff;
  margin: 16rpx;
  border-radius: 12rpx;
  padding: 16rpx;
}
.flash-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.flash-t {
  font-size: 30rpx;
  font-weight: bold;
  color: #e4393c;
}
.flash-more {
  font-size: 24rpx;
  color: #999;
}
.flash-scroll {
  white-space: nowrap;
  margin-top: 12rpx;
}
.flash-card {
  display: inline-block;
  width: 200rpx;
  margin-right: 16rpx;
  vertical-align: top;
}
.f-cover {
  width: 200rpx;
  height: 200rpx;
  background: #eee;
  border-radius: 12rpx;
}
.f-name {
  font-size: 24rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.f-price {
  color: #e4393c;
  font-weight: bold;
  font-size: 28rpx;
}
.f-origin {
  color: #999;
  font-size: 22rpx;
  text-decoration: line-through;
  margin-left: 8rpx;
}
.f-bar {
  height: 10rpx;
  background: #f2f2f2;
  border-radius: 6rpx;
  margin-top: 8rpx;
  overflow: hidden;
}
.f-bar-in {
  height: 100%;
  background: #07c160;
}
.f-state {
  margin-top: 8rpx;
  font-size: 22rpx;
}
.f-cd {
  color: #e4393c;
}
.f-tag.up {
  color: #ff9800;
}
.f-tag.end {
  color: #999;
}
.cats {
  white-space: nowrap;
  padding: 16rpx;
}
.cat {
  display: inline-block;
  padding: 8rpx 24rpx;
  margin-right: 12rpx;
  background: #fff;
  border-radius: 32rpx;
  font-size: 26rpx;
}
.floor {
  margin: 16rpx;
}
.floor-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8rpx 0 16rpx;
}
.floor-t {
  font-size: 30rpx;
  font-weight: bold;
}
.floor-more {
  font-size: 24rpx;
  color: #999;
}
.grid {
  display: flex;
  flex-wrap: wrap;
  background: #fff;
  border-radius: 12rpx;
  padding: 8rpx 0;
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
.floor-empty {
  background: #fff;
  border-radius: 12rpx;
  color: #999;
  font-size: 24rpx;
  text-align: center;
  padding: 32rpx 0;
}
.footer-tip {
  text-align: center;
  color: #ccc;
  font-size: 22rpx;
  padding: 24rpx 0;
}
</style>
