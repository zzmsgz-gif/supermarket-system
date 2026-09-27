<template>
  <view class="fav-page">
    <view
      v-for="f in favorites"
      :key="f.id"
      class="fav"
      @click="goDetail(f.productId)"
    >
      <image class="cover" :src="fullUrl(f.product && f.product.coverUrl)" mode="aspectFill" />
      <view class="info">
        <view class="name">{{ f.product && f.product.name }}</view>
        <view class="price-row">
          <text class="now">¥{{ f.currentPrice }}</text>
          <text class="drop" v-if="f.priceDropped">降 ¥{{ f.dropAmount }}</text>
        </view>
        <view class="old">收藏时 ¥{{ f.priceAtFavorite }}</view>
      </view>
      <view class="ops" @click.stop>
        <text class="op" @click="goDetail(f.productId)">看同款</text>
        <text class="op del" @click="remove(f)">取消收藏</text>
      </view>
    </view>

    <view class="empty" v-if="!loading && !favorites.length">还没有收藏的商品</view>
    <view class="loading" v-if="loading">加载中…</view>
    <view class="more" v-else-if="hasMore" @click="loadMore">点击加载更多</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow, onReachBottom } from '@dcloudio/uni-app'
import { listFavorites, removeFavorite } from '@/api/favorite'
import { fullUrl } from '@/config'

const favorites = ref([])
const page = ref(0)
const size = ref(20)
const total = ref(0)
const loading = ref(false)
const hasMore = ref(false)

async function load(reset = false) {
  if (loading.value) return
  if (reset) {
    page.value = 0
    favorites.value = []
  }
  loading.value = true
  try {
    const res = await listFavorites({ page: page.value, size: size.value })
    const items = (res && res.items) ? res.items : []
    total.value = (res && res.total) || 0
    favorites.value = page.value === 0 ? items : favorites.value.concat(items)
    page.value += 1
    hasMore.value = favorites.value.length < total.value
  } catch (e) {
    uni.showToast({ title: (e && e.message) || '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function goDetail(id) {
  uni.navigateTo({ url: '/pages/product/detail?id=' + id })
}

function remove(f) {
  uni.showModal({
    title: '取消收藏',
    content: '确定取消收藏该商品？',
    success: async (r) => {
      if (!r.confirm) return
      try {
        await removeFavorite(f.productId)
        uni.showToast({ title: '已取消', icon: 'none' })
        load(true)
      } catch (e) {
        uni.showToast({ title: (e && e.message) || '操作失败', icon: 'none' })
      }
    }
  })
}

onShow(() => load(true))
onReachBottom(() => {
  if (hasMore.value) load()
})
</script>

<style scoped>
.fav-page {
  min-height: 100vh;
  background: #f5f5f5;
}
.fav {
  display: flex;
  align-items: center;
  background: #fff;
  margin: 16rpx;
  border-radius: 12rpx;
  padding: 16rpx;
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
.price-row {
  display: flex;
  align-items: center;
  margin-top: 10rpx;
}
.now {
  color: #e4393c;
  font-weight: bold;
  font-size: 30rpx;
}
.drop {
  font-size: 20rpx;
  color: #fff;
  background: #e4393c;
  border-radius: 16rpx;
  padding: 2rpx 12rpx;
  margin-left: 12rpx;
}
.old {
  font-size: 22rpx;
  color: #999;
  margin-top: 8rpx;
}
.ops {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}
.op {
  font-size: 24rpx;
  color: #666;
  margin-top: 16rpx;
}
.op.del {
  color: #e4393c;
}
.empty {
  text-align: center;
  color: #999;
  padding: 120rpx 0;
}
.loading,
.more {
  text-align: center;
  color: #999;
  font-size: 24rpx;
  padding: 24rpx 0;
}
</style>
