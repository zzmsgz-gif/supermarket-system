<template>
  <view class="detail" v-if="product">
    <view class="cover-wrap">
      <image class="cover" :src="fullUrl(product.coverUrl)" mode="aspectFill" />
      <view class="fav" :class="{ on: faved }" @click="toggleFav">
        {{ faved ? '♥' : '♡' }}
      </view>
    </view>

    <view class="name">{{ product.name }}</view>
    <view class="price">
      <text class="now">¥{{ displayPrice }}</text>
      <text class="origin" v-if="showOriginal">¥{{ displayOriginal }}</text>
      <text class="member" v-if="product.memberPrice && product.memberPrice < displayPrice">会员价 ¥{{ product.memberPrice }}</text>
    </view>
    <view class="desc">{{ product.description }}</view>

    <view class="specs" v-if="hasSpec">
      <view v-for="(vals, dim) in specDimensions" :key="dim" class="spec-row">
        <view class="spec-dim">{{ dim }}</view>
        <view class="spec-vals">
          <text
            v-for="v in vals"
            :key="v"
            class="spec-val"
            :class="{ active: selectedSpec[dim] === v }"
            @click="pickSpec(dim, v)"
          >{{ v }}</text>
        </view>
      </view>
    </view>

    <view class="qty-row" v-if="hasSpec">
      <text class="qty-label">数量</text>
      <view class="qty">
        <text class="btn" @click="quantity = Math.max(1, quantity - 1)">-</text>
        <text class="num">{{ quantity }}</text>
        <text class="btn" @click="quantity++">+</text>
      </view>
    </view>

    <!-- 评价 -->
    <view class="reviews" v-if="reviews.length">
      <view class="rv-head">商品评价（{{ reviewTotal }}）</view>
      <view v-for="r in reviews" :key="r.id" class="rv">
        <view class="rv-top">
          <text class="rv-name">{{ r.nickname || '匿名用户' }}</text>
          <text class="rv-stars">{{ starString(r.rating) }}</text>
        </view>
        <view class="rv-content">{{ r.content }}</view>
        <view class="rv-imgs" v-if="r.imageUrls && r.imageUrls.length">
          <image
            v-for="(u, i) in r.imageUrls"
            :key="i"
            class="rv-img"
            :src="fullUrl(u)"
            mode="aspectFill"
          />
        </view>
        <view class="rv-reply" v-if="r.replyContent">商家回复：{{ r.replyContent }}</view>
        <view class="rv-time">{{ formatTime(r.createdAt) }}</view>
      </view>
    </view>

    <view class="bar">
      <button class="cart" @click="add">加入购物车</button>
      <button class="buy" @click="buyNow">立即购买</button>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getProduct } from '@/api/product'
import { addToCart } from '@/api/cart'
import { listFavoriteIds, addFavorite, removeFavorite } from '@/api/favorite'
import { listProductReviews } from '@/api/review'
import { isLoggedIn } from '@/store/auth'
import { fullUrl } from '@/config'
import { starString, formatTime } from '@/utils/format'

const product = ref(null)
const quantity = ref(1)
const selectedSpec = reactive({})
const faved = ref(false)
const reviews = ref([])
const reviewTotal = ref(0)

function safeParseSpec(str) {
  try {
    return JSON.parse(str || '{}') || {}
  } catch (e) {
    return {}
  }
}

const specDimensions = computed(() => {
  const skus = product.value?.skus || []
  const dims = {}
  for (const sku of skus) {
    const spec = safeParseSpec(sku.specJson)
    for (const key of Object.keys(spec)) {
      if (!dims[key]) dims[key] = []
      if (!dims[key].includes(spec[key])) dims[key].push(spec[key])
    }
  }
  return dims
})

const hasSpec = computed(() => Object.keys(specDimensions.value).length > 0)

const selectedSku = computed(() => {
  const skus = product.value?.skus || []
  if (!Object.keys(selectedSpec).length) return null
  return skus.find((sku) => {
    const spec = safeParseSpec(sku.specJson)
    return Object.keys(spec).every((k) => String(spec[k]) === String(selectedSpec[k]))
  }) || null
})

const displayPrice = computed(() => {
  if (selectedSku.value && selectedSku.value.price != null) return selectedSku.value.price
  return product.value?.price
})
const displayOriginal = computed(() => {
  if (selectedSku.value && selectedSku.value.originalPrice != null) return selectedSku.value.originalPrice
  return product.value?.originalPrice
})
const showOriginal = computed(
  () => displayOriginal.value != null && Number(displayOriginal.value) > Number(displayPrice.value || 0)
)

const skuSpecText = computed(() =>
  Object.keys(selectedSpec)
    .sort()
    .map((k) => `${k}:${selectedSpec[k]}`)
    .join(' ')
)

function pickSpec(dim, val) {
  selectedSpec[dim] = val
}

async function loadFav() {
  if (!isLoggedIn()) return
  try {
    const ids = await listFavoriteIds()
    faved.value = Array.isArray(ids) && ids.includes(product.value.id)
  } catch (e) {
    /* 忽略 */
  }
}

async function toggleFav() {
  if (!isLoggedIn()) {
    uni.navigateTo({ url: '/pages/login/login' })
    return
  }
  try {
    if (faved.value) {
      await removeFavorite(product.value.id)
      faved.value = false
      uni.showToast({ title: '已取消收藏', icon: 'none' })
    } else {
      await addFavorite(product.value.id)
      faved.value = true
      uni.showToast({ title: '已收藏', icon: 'success' })
    }
  } catch (e) {
    uni.showToast({ title: (e && e.message) || '操作失败', icon: 'none' })
  }
}

async function loadReviews() {
  try {
    const res = await listProductReviews(product.value.id, { page: 0, size: 10 })
    reviews.value = (res && res.items) ? res.items : []
    reviewTotal.value = (res && res.total) || reviews.value.length
  } catch (e) {
    reviews.value = []
  }
}

onLoad(async (opts) => {
  product.value = await getProduct(opts.id)
  const skus = product.value?.skus || []
  if (skus.length) {
    const first = safeParseSpec(skus[0].specJson)
    for (const k in first) selectedSpec[k] = first[k]
  }
  loadFav()
  loadReviews()
})

function add() {
  if (!isLoggedIn()) {
    uni.navigateTo({ url: '/pages/login/login' })
    return
  }
  addToCart(product.value.id, quantity.value, skuSpecText.value).then(() =>
    uni.showToast({ title: '已加入', icon: 'success' })
  )
}

function buyNow() {
  if (!isLoggedIn()) {
    uni.navigateTo({ url: '/pages/login/login' })
    return
  }
  const url =
    '/pages/checkout/checkout?mode=quick&productId=' + product.value.id +
    '&quantity=' + quantity.value +
    '&skuSpec=' + encodeURIComponent(skuSpecText.value)
  uni.navigateTo({ url })
}
</script>

<style scoped>
.detail {
  padding: 0 0 120rpx;
}
.cover-wrap {
  position: relative;
}
.cover {
  width: 100%;
  height: 600rpx;
  background: #eee;
}
.fav {
  position: absolute;
  top: 24rpx;
  right: 24rpx;
  width: 72rpx;
  height: 72rpx;
  line-height: 72rpx;
  text-align: center;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.4);
  color: #fff;
  font-size: 40rpx;
}
.fav.on {
  color: #e4393c;
  background: rgba(255, 255, 255, 0.9);
}
.name {
  padding: 24rpx;
  font-size: 32rpx;
  font-weight: bold;
}
.price {
  padding: 0 24rpx 12rpx;
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
}
.now {
  color: #e4393c;
  font-size: 36rpx;
  font-weight: bold;
}
.origin {
  color: #999;
  font-size: 24rpx;
  text-decoration: line-through;
  margin-left: 12rpx;
}
.member {
  color: #07c160;
  font-size: 22rpx;
  border: 1rpx solid #07c160;
  border-radius: 6rpx;
  padding: 2rpx 8rpx;
  margin-left: 12rpx;
}
.desc {
  padding: 0 24rpx;
  color: #666;
  font-size: 26rpx;
  line-height: 1.6;
}
.specs {
  padding: 16rpx 24rpx;
}
.spec-row {
  display: flex;
  align-items: flex-start;
  margin-bottom: 20rpx;
}
.spec-dim {
  width: 120rpx;
  font-size: 26rpx;
  color: #666;
  padding-top: 8rpx;
}
.spec-vals {
  flex: 1;
  display: flex;
  flex-wrap: wrap;
}
.spec-val {
  padding: 8rpx 24rpx;
  margin: 0 16rpx 16rpx 0;
  background: #f5f5f5;
  border: 1rpx solid #eee;
  border-radius: 32rpx;
  font-size: 26rpx;
}
.spec-val.active {
  background: #07c160;
  color: #fff;
  border-color: #07c160;
}
.qty-row {
  display: flex;
  align-items: center;
  padding: 8rpx 24rpx 24rpx;
}
.qty-label {
  font-size: 26rpx;
  color: #666;
  margin-right: 24rpx;
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
.reviews {
  background: #fff;
  margin: 16rpx;
  border-radius: 12rpx;
  padding: 16rpx 24rpx;
}
.rv-head {
  font-size: 28rpx;
  font-weight: bold;
  padding: 8rpx 0 16rpx;
}
.rv {
  padding: 16rpx 0;
  border-bottom: 1rpx solid #f2f2f2;
}
.rv-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.rv-name {
  font-size: 26rpx;
  color: #333;
}
.rv-stars {
  color: #ff9800;
  font-size: 24rpx;
}
.rv-content {
  font-size: 26rpx;
  color: #444;
  margin-top: 8rpx;
  line-height: 1.5;
}
.rv-imgs {
  display: flex;
  flex-wrap: wrap;
  margin-top: 8rpx;
}
.rv-img {
  width: 140rpx;
  height: 140rpx;
  margin: 0 12rpx 12rpx 0;
  border-radius: 8rpx;
  background: #eee;
}
.rv-reply {
  font-size: 24rpx;
  color: #07c160;
  background: #f0fff7;
  border-radius: 8rpx;
  padding: 8rpx 12rpx;
  margin-top: 8rpx;
}
.rv-time {
  font-size: 22rpx;
  color: #999;
  margin-top: 8rpx;
}
.bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  padding: 16rpx 24rpx;
  background: #fff;
}
.cart {
  flex: 1;
  background: #ffb300;
  color: #fff;
  border-radius: 48rpx;
  margin-right: 16rpx;
}
.buy {
  flex: 1;
  background: #07c160;
  color: #fff;
  border-radius: 48rpx;
}
</style>
