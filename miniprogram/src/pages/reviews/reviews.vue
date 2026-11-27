<template>
  <view class="reviews-page">
    <!-- 评分汇总：大分数 + 星级 + 总条数 -->
    <view class="sum">
      <view class="sum-left">
        <text class="sum-score">{{ summary ? summary.avgRating : '—' }}</text>
        <text class="sum-outof">分</text>
      </view>
      <view class="sum-right">
        <text class="sum-stars">{{ summary ? starString(summary.avgRating) : '☆☆☆☆☆' }}</text>
        <text class="sum-count">{{ total }} 位用户评价</text>
      </view>
    </view>

    <view class="list" v-if="reviews.length">
      <view v-for="r in reviews" :key="r.id" class="rv">
        <view class="rv-top">
          <text class="rv-avatar">{{ (r.nickname || '匿')[0] }}</text>
          <view class="rv-user">
            <text class="rv-name">{{ r.nickname || '匿名用户' }}</text>
            <text class="rv-stars">{{ starString(r.rating) }}</text>
          </view>
          <text class="rv-time">{{ formatTime(r.createdAt) }}</text>
        </view>
        <view class="rv-content">{{ r.content }}</view>
        <view class="rv-imgs" v-if="r.imageUrls && r.imageUrls.length">
          <image
            v-for="(u, i) in r.imageUrls"
            :key="i"
            class="rv-img"
            :src="fullUrl(u)"
            mode="aspectFill"
            @click="previewImages(r.imageUrls, i)"
          />
        </view>
        <view class="rv-reply" v-if="r.replyContent">商家回复：{{ r.replyContent }}</view>
      </view>
    </view>

    <!-- 空态 / 加载态 / 到底了 -->
    <view class="empty" v-if="!loading && !reviews.length">
      <text class="empty-ico">📝</text>
      <text class="empty-text">这件商品还没有评价</text>
    </view>
    <view class="foot" v-if="reviews.length">
      <text v-if="loading">加载中…</text>
      <text v-else-if="noMore">— 没有更多评价了 —</text>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onReachBottom } from '@dcloudio/uni-app'
import { listProductReviews } from '@/api/review'
import { fullUrl } from '@/config'
import { starString, formatTime } from '@/utils/format'
import { ensureRatings, ratingMap } from '@/utils/productRating'

// ⚠️ 分页必须从 page=1 开始：后端分页参数带 @Min(1)，传 0 直接 400
// （详情页曾踩过：异常被吞掉后列表恒为空，页面看着特别空）
const PAGE_SIZE = 10

const productId = ref(null)
const reviews = ref([])
const total = ref(0)
const summary = ref(null)
const page = ref(0)
const loading = ref(false)
const noMore = ref(false)

async function loadPage(p) {
  if (loading.value || productId.value == null) return
  loading.value = true
  try {
    const res = await listProductReviews(productId.value, { page: p, size: PAGE_SIZE })
    const items = (res && res.items) || []
    reviews.value = p === 1 ? items : reviews.value.concat(items)
    total.value = (res && res.total) || reviews.value.length
    page.value = p
    // items 为空或已达总数 → 收口，避免到底后还反复请求
    noMore.value = items.length === 0 || reviews.value.length >= total.value
  } catch (e) {
    uni.showToast({ title: (e && e.message) || '评价加载失败', icon: 'none' })
    noMore.value = true
  } finally {
    loading.value = false
  }
}

function previewImages(urls, index) {
  const list = (urls || []).map((u) => fullUrl(u))
  if (!list.length) return
  uni.previewImage({ current: list[index] || list[0], urls: list })
}

onLoad((opts) => {
  productId.value = Number((opts && opts.productId) || 0) || null
  if (opts && opts.name) {
    uni.setNavigationBarTitle({ title: '评价 · ' + decodeURIComponent(opts.name) })
  } else {
    uni.setNavigationBarTitle({ title: '全部评价' })
  }
  if (productId.value == null) {
    noMore.value = true
    return
  }
  // 汇总与商品卡共用同一份缓存，不再单独打接口
  ensureRatings().then(() => {
    const hit = ratingMap.value[Number(productId.value)]
    if (hit && hit.count > 0) {
      summary.value = { avgRating: Number(hit.avg || 0).toFixed(1), count: hit.count }
      // 列表 total 与汇总同源；汇总先到就用它撑着，避免「0 位用户评价」闪一下
      if (!total.value) total.value = hit.count
    }
  })
  loadPage(1)
})

onReachBottom(() => {
  if (!noMore.value) loadPage(page.value + 1)
})
</script>

<style scoped>
.reviews-page {
  min-height: 100vh;
  background: #FAF8F3;
  padding: 20rpx 24rpx 40rpx;
}

/* ---------- 评分汇总 ---------- */
.sum {
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: 20rpx;
  padding: 32rpx 36rpx;
  margin-bottom: 20rpx;
}
.sum-left {
  display: flex;
  align-items: baseline;
  padding-right: 36rpx;
  border-right: 1rpx solid #f0ece3;
}
.sum-score {
  font-size: 64rpx;
  font-weight: 700;
  color: #0F6E56;
  line-height: 1;
}
.sum-outof {
  margin-left: 6rpx;
  font-size: 24rpx;
  color: #8a8378;
}
.sum-right {
  flex: 1;
  min-width: 0;
  padding-left: 36rpx;
  display: flex;
  flex-direction: column;
}
.sum-stars {
  font-size: 28rpx;
  color: #ffa042;
  letter-spacing: 2rpx;
}
.sum-count {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #8a8378;
}

/* ---------- 评价列表（与详情页同款）---------- */
.list {
  background: #fff;
  border-radius: 20rpx;
  padding: 8rpx 28rpx;
}
.rv {
  padding: 26rpx 0;
  border-bottom: 1rpx solid #f4f1ea;
}
.rv:last-child {
  border-bottom: none;
}
.rv-top {
  display: flex;
  align-items: center;
}
.rv-avatar {
  width: 56rpx;
  height: 56rpx;
  line-height: 56rpx;
  text-align: center;
  border-radius: 50%;
  background: #e6f2ee;
  color: #0F6E56;
  font-size: 26rpx;
  flex: 0 0 auto;
}
.rv-user {
  flex: 1;
  min-width: 0;
  margin-left: 16rpx;
  display: flex;
  flex-direction: column;
}
.rv-name {
  font-size: 26rpx;
  color: #1F2937;
}
.rv-stars {
  margin-top: 4rpx;
  font-size: 22rpx;
  color: #ffa042;
  letter-spacing: 1rpx;
}
.rv-time {
  font-size: 22rpx;
  color: #9CA3AF;
  flex: 0 0 auto;
}
.rv-content {
  margin-top: 16rpx;
  font-size: 27rpx;
  line-height: 1.7;
  color: #374151;
  word-break: break-word;
}
.rv-imgs {
  display: flex;
  flex-wrap: wrap;
  margin-top: 14rpx;
}
.rv-img {
  width: 160rpx;
  height: 160rpx;
  border-radius: 12rpx;
  margin: 0 12rpx 12rpx 0;
  background: #f5f5f5;
}
.rv-reply {
  margin-top: 14rpx;
  padding: 16rpx 20rpx;
  background: #FBF9F4;
  border-radius: 12rpx;
  font-size: 24rpx;
  line-height: 1.6;
  color: #6B7280;
}

/* ---------- 空态 / 底部 ---------- */
.empty {
  padding: 120rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.empty-ico {
  font-size: 64rpx;
}
.empty-text {
  margin-top: 20rpx;
  font-size: 26rpx;
  color: #9CA3AF;
}
.foot {
  padding: 32rpx 0 8rpx;
  text-align: center;
  font-size: 24rpx;
  color: #9CA3AF;
}
</style>
