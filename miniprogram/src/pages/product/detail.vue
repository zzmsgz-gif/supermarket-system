<template>
  <view class="detail" v-if="product">
    <!-- ===== 1. 主图轮播（多图可横滑 + 计数 + 点击看大图） ===== -->
    <view class="gallery">
      <swiper
        class="gallery-swiper"
        :current="currentImg"
        circular
        @change="onGalleryChange"
      >
        <swiper-item v-for="(u, i) in galleryImages" :key="i">
          <image class="gallery-img" :src="fullUrl(u)" mode="aspectFill" @click="previewGallery(i)" />
        </swiper-item>
      </swiper>
      <view class="gallery-counter" v-if="galleryImages.length > 1">{{ currentImg + 1 }}/{{ galleryImages.length }}</view>
      <view class="gallery-badges">
        <text class="g-badge hot" v-if="product.isHot === 1">热销</text>
        <text class="g-badge new" v-if="product.isNew === 1">新品</text>
        <text class="g-badge off" v-if="soldOut">已售罄</text>
      </view>
      <view class="fav" :class="{ on: faved }" @click="toggleFav">{{ faved ? '♥' : '♡' }}</view>
    </view>

    <!-- ===== 2. 价格卡 ===== -->
    <view class="card price-card">
      <view class="price-row">
        <text class="p-now"><text class="p-yen">¥</text>{{ displayPrice }}</text>
        <text class="p-origin" v-if="showOriginal">¥{{ displayOriginal }}</text>
        <text class="p-off" v-if="discountSave > 0">立省 ¥{{ discountSave }}<text class="p-rate" v-if="discountRate">· {{ discountRate }} 折</text></text>
      </view>
      <view class="member-row" v-if="showMemberPrice">
        <text class="m-badge">会员价</text>
        <text class="m-price">¥{{ product.memberPrice }}</text>
        <text class="m-tip">升级会员再省 ¥{{ memberSave }}</text>
      </view>
      <view class="tag-row" v-if="tagList.length">
        <text class="p-tag" v-for="t in tagList" :key="t">{{ t }}</text>
      </view>
      <view class="stat-row">
        <text class="stat">已售 {{ product.sales || 0 }}</text>
        <text class="stat">库存 {{ product.stock || 0 }}{{ unitText }}</text>
        <text class="stat-warn" v-if="stockTight">仅剩 {{ product.stock }} 件，手慢无</text>
      </view>
    </view>

    <!-- ===== 3. 标题卡 ===== -->
    <view class="card title-card">
      <view class="p-name">{{ product.name }}</view>
      <view class="p-sub" v-if="product.subtitle">{{ product.subtitle }}</view>
      <view class="p-brand" v-if="product.brand">
        <text class="brand-label">品牌</text>
        <text class="brand-name">{{ product.brand }}</text>
      </view>
    </view>

    <!-- ===== 4. 规格选择（有 SKU 才出现） ===== -->
    <view class="card" v-if="hasSpec">
      <view class="sec-head">
        <text class="sec-title">选择规格</text>
        <text class="sec-hint" v-if="skuSpecText">{{ skuSpecText }}</text>
      </view>
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

    <!-- ===== 5. 购买数量（所有商品都能选，不再依赖规格） ===== -->
    <view class="card qty-card">
      <view class="sec-head">
        <text class="sec-title">购买数量</text>
        <text class="sec-hint" v-if="stockTight">最多可买 {{ maxQty }} 件</text>
      </view>
      <view class="qty">
        <view class="q-btn" :class="{ off: quantity <= 1 }" @click="decQty">−</view>
        <text class="q-num">{{ quantity }}</text>
        <view class="q-btn" :class="{ off: quantity >= maxQty }" @click="incQty">+</view>
        <text class="q-total" v-if="totalAmount">小计 ¥{{ totalAmount }}</text>
      </view>
    </view>

    <!-- ===== 6. 服务与配送 ===== -->
    <view class="card service-card">
      <view class="sec-title">服务与配送</view>
      <view class="svc-row">
        <text class="svc-ico">⚡</text>
        <view class="svc-main">
          <text class="svc-strong">最快 30 分钟送达</text>
          <text class="svc-desc">门店自提随到随取 · 配送按所选时段送达</text>
        </view>
      </view>
      <view class="svc-row">
        <text class="svc-ico">🚚</text>
        <view class="svc-main">
          <text class="svc-strong">满 ¥99 免运费</text>
          <text class="svc-desc">未满收取 ¥8 配送费</text>
        </view>
      </view>
      <view class="svc-row">
        <text class="svc-ico">🛡</text>
        <view class="svc-main">
          <text class="svc-strong">坏果包赔 · 7 天无理由</text>
          <text class="svc-desc">收到有问题可在订单里申请售后</text>
        </view>
      </view>
    </view>

    <!-- ===== 7. 商品详情 ===== -->
    <view class="card" v-if="product.description">
      <view class="sec-title">商品详情</view>
      <view class="desc-text">{{ product.description }}</view>
      <view class="desc-meta">
        <text class="dm-item" v-if="product.brand">品牌：{{ product.brand }}</text>
        <text class="dm-item" v-if="unitText">规格单位：1{{ unitText }}</text>
        <text class="dm-item">商品编号：{{ product.sku }}</text>
      </view>
    </view>

    <!-- ===== 8. 商品评价 ===== -->
    <view class="card review-card">
      <view class="sec-head">
        <text class="sec-title">商品评价<text class="sec-count">（{{ reviewTotal }}）</text></text>
        <text class="sec-more" v-if="reviewTotal > reviews.length" @click="loadMoreReviews">
          {{ loadingMore ? '加载中…' : '查看全部 ›' }}
        </text>
      </view>

      <!-- 评分汇总 -->
      <view class="rating-sum" v-if="ratingSummary">
        <view class="rs-left">
          <text class="rs-score">{{ ratingSummary.avgRating }}</text>
          <text class="rs-outof">分</text>
        </view>
        <view class="rs-right">
          <text class="rs-stars">{{ starString(ratingSummary.avgRating) }}</text>
          <text class="rs-count">{{ reviewTotal }} 位用户评价</text>
        </view>
      </view>

      <view v-if="reviews.length">
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
            />
          </view>
          <view class="rv-reply" v-if="r.replyContent">商家回复：{{ r.replyContent }}</view>
        </view>
      </view>
      <view v-else class="rv-empty">
        <text class="rv-empty-ico">📝</text>
        <text class="rv-empty-text">还没有人评价，买过的都在这——欢迎你来第一个分享</text>
      </view>
    </view>

    <!-- ===== 9. 猜你喜欢 ===== -->
    <view class="reco" v-if="recommends.length">
      <view class="reco-head">
        <text class="reco-line"></text>
        <text class="reco-title">猜你喜欢</text>
        <text class="reco-line"></text>
      </view>
      <view class="reco-grid">
        <view class="reco-card" v-for="p in recommends" :key="p.id" @click="openProduct(p)">
          <image class="reco-img" :src="fullUrl(p.coverUrl)" mode="aspectFill" />
          <view class="reco-body">
            <text class="reco-name">{{ p.name }}</text>
            <view class="reco-foot">
              <text class="reco-price">¥{{ p.price }}</text>
              <text class="reco-sales">{{ p.sales || 0 }} 人买过</text>
            </view>
          </view>
        </view>
      </view>
    </view>

    <view class="bottom-space"></view>

    <!-- ===== 10. 底部操作条 ===== -->
    <view class="bar">
      <view class="bar-side" @click="goCart">
        <text class="bar-ico">🛒</text>
        <text class="bar-txt">购物车</text>
      </view>
      <button class="bar-btn cart" :disabled="soldOut" @click="add">加入购物车</button>
      <button class="bar-btn buy" :disabled="soldOut" @click="buyNow">{{ soldOut ? '已售罄' : '立即购买' }}</button>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getProduct, listProducts } from '@/api/product'
import { addToCart } from '@/api/cart'
import { listFavoriteIds, addFavorite, removeFavorite } from '@/api/favorite'
import { listProductReviews } from '@/api/review'
import { request } from '@/api/request'
import { isLoggedIn } from '@/store/auth'
import { fullUrl } from '@/config'
import { starString, formatTime } from '@/utils/format'

const product = ref(null)
const quantity = ref(1)
const selectedSpec = reactive({})
const faved = ref(false)
const reviews = ref([])
const reviewTotal = ref(0)
const loadingMore = ref(false)
const ratingSummary = ref(null)
const recommends = ref([])
const currentImg = ref(0)

// 单位中文（与后台商品表单的 unit 取值对应；查不到就直接显示原值）
const UNIT_TEXT = {
  piece: '件', bag: '袋', kg: '千克', g: '克', bottle: '瓶',
  box: '盒', pack: '包', jin: '斤', portion: '份', ml: '毫升'
}
const unitText = computed(() => UNIT_TEXT[product.value?.unit] || '')

const soldOut = computed(() => (product.value?.stock || 0) <= 0)
const stockTight = computed(() => {
  const s = product.value?.stock || 0
  return s > 0 && s <= 20
})
const maxQty = computed(() => Math.max(1, product.value?.stock || 1))

// —— 主图：优先多图（images），没有就用封面单图 ——
const galleryImages = computed(() => {
  const imgs = (product.value?.images || [])
    .slice()
    .sort((a, b) => (a.sortNo || 0) - (b.sortNo || 0))
    .map((it) => it.url)
    .filter(Boolean)
  if (imgs.length) return imgs
  return product.value?.coverUrl ? [product.value.coverUrl] : []
})

function onGalleryChange(e) {
  currentImg.value = e.detail.current
}

function previewGallery(i) {
  const urls = galleryImages.value.map((u) => fullUrl(u))
  if (!urls.length) return
  uni.previewImage({ current: urls[i] || urls[0], urls })
}

// —— 卖点标签（后端存的是逗号分隔字符串）——
const tagList = computed(() => {
  const raw = product.value?.tags || ''
  return String(raw)
    .split(/[,，]/)
    .map((s) => s.trim())
    .filter(Boolean)
    .slice(0, 4)
})

// —— 价格 ——
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
// 立省 / 折扣率（只在有原价且高于现价时显示，避免编造）
const discountSave = computed(() => {
  if (!showOriginal.value) return 0
  return Math.round((Number(displayOriginal.value) - Number(displayPrice.value)) * 100) / 100
})
const discountRate = computed(() => {
  const d = Number(displayOriginal.value) || 0
  const p = Number(displayPrice.value) || 0
  if (!d || !p || p >= d) return ''
  return (p / d * 10).toFixed(1)
})
// 会员价：只有真正低于当前售价才展示
const showMemberPrice = computed(() => {
  const m = Number(product.value?.memberPrice || 0)
  const p = Number(displayPrice.value || 0)
  return m > 0 && m < p
})
const memberSave = computed(() => {
  if (!showMemberPrice.value) return '0.00'
  return (Number(displayPrice.value) - Number(product.value.memberPrice)).toFixed(2)
})

const totalAmount = computed(() => {
  const p = Number(displayPrice.value) || 0
  const t = Math.round(p * quantity.value * 100) / 100
  return t > 0 ? t : 0
})

const skuSpecText = computed(() =>
  Object.keys(selectedSpec)
    .sort()
    .map((k) => `${k}:${selectedSpec[k]}`)
    .join(' ')
)

function pickSpec(dim, val) {
  selectedSpec[dim] = val
}

function decQty() {
  if (quantity.value > 1) quantity.value -= 1
}
function incQty() {
  if (quantity.value < maxQty.value) quantity.value += 1
  else uni.showToast({ title: `最多可买 ${maxQty.value} 件`, icon: 'none' })
}

// —— 收藏 ——
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

// —— 评价：首屏 3 条，点「查看全部」每次再加 10 条，直到全部展示 ——
// ⚠️ page 必须从 **1** 开始：后端分页参数带 @Min(1)，传 0 会直接 400
// （"操作失败，请稍后重试"），异常被 catch 吞掉后 reviews 恒为空 ——
// 于是详情页 42 条评价一条都不显示，页面看着特别空（2026-11-08 修）。
const reviewSize = ref(3)

async function loadReviews(size = reviewSize.value) {
  try {
    const res = await listProductReviews(product.value.id, { page: 1, size })
    reviews.value = (res && res.items) ? res.items : []
    reviewTotal.value = (res && res.total) || reviews.value.length
    reviewSize.value = size
  } catch (e) {
    reviews.value = []
  }
}

async function loadMoreReviews() {
  if (loadingMore.value) return
  loadingMore.value = true
  try {
    // 每次多拉 10 条；超出总数时后端直接返回全部，按钮随即消失
    await loadReviews(reviewSize.value + 10)
  } finally {
    loadingMore.value = false
  }
}

// —— 评分汇总：复用全站接口，按 productId 取当前商品（不额外加后端）——
async function loadRatingSummary() {
  try {
    const list = await request('/products/rating-summary', { auth: false })
    const hit = (list || []).find((it) => Number(it.productId) === Number(product.value.id))
    if (hit && Number(hit.reviewCount) > 0) {
      ratingSummary.value = {
        avgRating: Number(hit.avgRating || 0).toFixed(1),
        reviewCount: hit.reviewCount
      }
      // 列表接口的 total 与汇总同源（都排除 hidden），以较大的为准，避免显示 0 条却有评分
      if (!reviewTotal.value) reviewTotal.value = hit.reviewCount
    }
  } catch (e) {
    /* 汇总失败不影响主流程 */
  }
}

// —— 猜你喜欢：同分类其他商品（最多 6 个）——
async function loadRecommends() {
  try {
    const res = await listProducts({ categoryId: product.value.categoryId, page: 1, size: 8 })
    const items = (res && res.items) || []
    recommends.value = items
      .filter((it) => Number(it.id) !== Number(product.value.id))
      .slice(0, 6)
    // 同分类不够 6 个就退回全站热销补位
    if (recommends.value.length < 6) {
      const more = await listProducts({ page: 1, size: 12 })
      const exist = new Set(recommends.value.map((it) => Number(it.id)))
      exist.add(Number(product.value.id))
      for (const it of ((more && more.items) || [])) {
        if (recommends.value.length >= 6) break
        if (exist.has(Number(it.id))) continue
        recommends.value.push(it)
      }
    }
  } catch (e) {
    recommends.value = []
  }
}

function openProduct(p) {
  // 详情 → 详情用 redirectTo，避免页面栈越压越深
  uni.redirectTo({ url: '/pages/product/detail?id=' + p.id })
}

function goCart() {
  uni.switchTab({ url: '/pages/cart/cart' })
}

onLoad(async (opts) => {
  product.value = await getProduct(opts.id)
  const skus = product.value?.skus || []
  if (skus.length) {
    const first = safeParseSpec(skus[0].specJson)
    for (const k in first) selectedSpec[k] = first[k]
  }
  // 首屏关键内容先出（商品已渲染），下面这些并行拉、失败不影响主体
  loadFav()
  loadReviews()
  loadRatingSummary()
  loadRecommends()
})

async function add() {
  if (!isLoggedIn()) {
    uni.navigateTo({ url: '/pages/login/login' })
    return
  }
  if (soldOut.value) {
    uni.showToast({ title: '这件已售罄', icon: 'none' })
    return
  }
  try {
    await addToCart(product.value.id, quantity.value, skuSpecText.value)
    uni.showToast({ title: '已加入购物车', icon: 'success' })
  } catch (e) {
    uni.showToast({ title: (e && e.message) || '加购失败，请重试', icon: 'none' })
  }
}

function buyNow() {
  if (!isLoggedIn()) {
    uni.navigateTo({ url: '/pages/login/login' })
    return
  }
  if (soldOut.value) {
    uni.showToast({ title: '这件已售罄', icon: 'none' })
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
  min-height: 100vh;
  background: #FAF8F3;
  padding-bottom: 24rpx;
}

/* ---------- 1. 主图 ---------- */
.gallery {
  position: relative;
}
.gallery-swiper {
  width: 100%;
  height: 660rpx;
  background: #f2efe8;
}
.gallery-img {
  width: 100%;
  height: 660rpx;
  background: #f2efe8;
}
.gallery-counter {
  position: absolute;
  right: 24rpx;
  bottom: 24rpx;
  padding: 6rpx 18rpx;
  border-radius: 24rpx;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: 22rpx;
}
.gallery-badges {
  position: absolute;
  left: 24rpx;
  top: 24rpx;
  display: flex;
  gap: 10rpx;
}
.g-badge {
  padding: 4rpx 14rpx;
  border-radius: 8rpx;
  font-size: 20rpx;
  color: #fff;
}
.g-badge.hot { background: #C0392B; }
.g-badge.new { background: #0F6E56; }
.g-badge.off { background: #6B7280; }
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
  color: #C0392B;
  background: rgba(255, 255, 255, 0.92);
}

/* ---------- 卡片通用 ---------- */
.card {
  margin: 20rpx 24rpx 0;
  padding: 26rpx 24rpx;
  background: #fff;
  border-radius: 20rpx;
  box-shadow: 0 2rpx 12rpx rgba(15, 110, 86, 0.04);
}
.sec-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}
.sec-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #1F2937;
}
.sec-count {
  font-weight: 400;
  color: #9CA3AF;
  font-size: 24rpx;
}
.sec-hint {
  font-size: 22rpx;
  color: #9CA3AF;
}
.sec-more {
  font-size: 24rpx;
  color: #0F6E56;
}

/* ---------- 2. 价格 ---------- */
.price-card {
  margin-top: -28rpx;
  position: relative;
  z-index: 2;
}
.price-row {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
}
.p-now {
  color: #C0392B;
  font-size: 48rpx;
  font-weight: 700;
  line-height: 1;
}
.p-yen {
  font-size: 28rpx;
  font-weight: 600;
}
.p-origin {
  margin-left: 14rpx;
  color: #9CA3AF;
  font-size: 24rpx;
  text-decoration: line-through;
}
.p-off {
  margin-left: 14rpx;
  padding: 4rpx 12rpx;
  background: #FDECEA;
  color: #C0392B;
  font-size: 22rpx;
  border-radius: 8rpx;
}
.p-rate {
  margin-left: 4rpx;
}
.member-row {
  margin-top: 16rpx;
  display: flex;
  align-items: center;
  gap: 10rpx;
}
.m-badge {
  padding: 4rpx 12rpx;
  background: #0F6E56;
  color: #fff;
  font-size: 20rpx;
  border-radius: 6rpx;
}
.m-price {
  color: #0F6E56;
  font-size: 28rpx;
  font-weight: 600;
}
.m-tip {
  color: #9CA3AF;
  font-size: 22rpx;
}
.tag-row {
  margin-top: 16rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
}
.p-tag {
  padding: 4rpx 14rpx;
  background: #F1F7F4;
  color: #0F6E56;
  font-size: 22rpx;
  border-radius: 8rpx;
}
.stat-row {
  margin-top: 16rpx;
  display: flex;
  align-items: center;
  gap: 24rpx;
}
.stat {
  color: #9CA3AF;
  font-size: 22rpx;
}
.stat-warn {
  color: #C0392B;
  font-size: 22rpx;
}

/* ---------- 3. 标题 ---------- */
.p-name {
  font-size: 34rpx;
  font-weight: 600;
  color: #1F2937;
  line-height: 1.4;
}
.p-sub {
  margin-top: 10rpx;
  font-size: 26rpx;
  color: #6B7280;
  line-height: 1.4;
}
.p-brand {
  margin-top: 16rpx;
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.brand-label {
  font-size: 20rpx;
  color: #9CA3AF;
  padding: 2rpx 10rpx;
  border: 1rpx solid #E5E7EB;
  border-radius: 6rpx;
}
.brand-name {
  font-size: 24rpx;
  color: #4B5563;
}

/* ---------- 4. 规格 ---------- */
.spec-row {
  display: flex;
  align-items: flex-start;
  margin-top: 20rpx;
}
.spec-dim {
  width: 120rpx;
  flex-shrink: 0;
  color: #9CA3AF;
  font-size: 24rpx;
  line-height: 52rpx;
}
.spec-vals {
  flex: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
}
.spec-val {
  padding: 0 24rpx;
  height: 52rpx;
  line-height: 52rpx;
  background: #FAF8F3;
  border: 1rpx solid #EDE9E1;
  border-radius: 10rpx;
  font-size: 24rpx;
  color: #4B5563;
}
.spec-val.active {
  background: #F1F7F4;
  border-color: #0F6E56;
  color: #0F6E56;
  font-weight: 600;
}

/* ---------- 5. 数量 ---------- */
.qty-card {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}
.qty {
  display: flex;
  align-items: center;
  gap: 0;
}
.q-btn {
  width: 64rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #FAF8F3;
  border: 1rpx solid #EDE9E1;
  font-size: 30rpx;
  color: #1F2937;
}
.q-btn:first-child {
  border-radius: 10rpx 0 0 10rpx;
}
.q-btn.off {
  color: #C9C4B8;
}
.q-num {
  min-width: 88rpx;
  height: 56rpx;
  line-height: 56rpx;
  text-align: center;
  border-top: 1rpx solid #EDE9E1;
  border-bottom: 1rpx solid #EDE9E1;
  font-size: 28rpx;
  background: #fff;
}
.q-btn:last-of-type {
  border-radius: 0 10rpx 10rpx 0;
}
.q-total {
  margin-left: 24rpx;
  color: #C0392B;
  font-size: 26rpx;
  font-weight: 600;
}

/* ---------- 6. 服务 ---------- */
.svc-row {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  margin-top: 20rpx;
}
.svc-ico {
  font-size: 28rpx;
  line-height: 1.2;
}
.svc-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}
.svc-strong {
  font-size: 26rpx;
  color: #1F2937;
}
.svc-desc {
  font-size: 22rpx;
  color: #9CA3AF;
}

/* ---------- 7. 详情 ---------- */
.desc-text {
  margin-top: 16rpx;
  font-size: 26rpx;
  color: #4B5563;
  line-height: 1.7;
}
.desc-meta {
  margin-top: 20rpx;
  padding-top: 18rpx;
  border-top: 1rpx dashed #EDE9E1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.dm-item {
  font-size: 22rpx;
  color: #9CA3AF;
}

/* ---------- 8. 评价 ---------- */
.rating-sum {
  margin-top: 20rpx;
  padding: 20rpx;
  background: #FAF8F3;
  border-radius: 14rpx;
  display: flex;
  align-items: center;
  gap: 24rpx;
}
.rs-left {
  display: flex;
  align-items: baseline;
}
.rs-score {
  font-size: 52rpx;
  font-weight: 700;
  color: #0F6E56;
  line-height: 1;
}
.rs-outof {
  font-size: 22rpx;
  color: #9CA3AF;
  margin-left: 4rpx;
}
.rs-right {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}
.rs-stars {
  color: #F5A623;
  font-size: 26rpx;
  letter-spacing: 2rpx;
}
.rs-count {
  font-size: 22rpx;
  color: #9CA3AF;
}
.rv {
  padding: 22rpx 0;
  border-bottom: 1rpx solid #F5F2EC;
}
.rv:last-child {
  border-bottom: none;
  padding-bottom: 0;
}
.rv-top {
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.rv-avatar {
  width: 52rpx;
  height: 52rpx;
  line-height: 52rpx;
  text-align: center;
  border-radius: 50%;
  background: #E8F3EE;
  color: #0F6E56;
  font-size: 24rpx;
  font-weight: 600;
}
.rv-user {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.rv-name {
  font-size: 24rpx;
  color: #4B5563;
}
.rv-stars {
  color: #F5A623;
  font-size: 22rpx;
}
.rv-time {
  font-size: 20rpx;
  color: #C4C0B6;
}
.rv-content {
  margin-top: 12rpx;
  font-size: 26rpx;
  color: #1F2937;
  line-height: 1.6;
}
.rv-imgs {
  margin-top: 12rpx;
  display: flex;
  gap: 10rpx;
}
.rv-img {
  width: 140rpx;
  height: 140rpx;
  border-radius: 10rpx;
  background: #f2efe8;
}
.rv-reply {
  margin-top: 12rpx;
  padding: 12rpx 16rpx;
  background: #F7F5F0;
  border-radius: 10rpx;
  font-size: 22rpx;
  color: #6B7280;
}
.rv-empty {
  padding: 40rpx 0 20rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
}
.rv-empty-ico {
  font-size: 44rpx;
}
.rv-empty-text {
  font-size: 22rpx;
  color: #9CA3AF;
  text-align: center;
  padding: 0 40rpx;
  line-height: 1.6;
}

/* ---------- 9. 猜你喜欢 ---------- */
.reco {
  margin: 28rpx 24rpx 0;
}
.reco-head {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  margin-bottom: 20rpx;
}
.reco-line {
  width: 60rpx;
  height: 1rpx;
  background: #E5E1D8;
}
.reco-title {
  font-size: 26rpx;
  font-weight: 600;
  color: #4B5563;
}
.reco-grid {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
}
.reco-card {
  width: 330rpx;
  margin-bottom: 20rpx;
  background: #fff;
  border-radius: 18rpx;
  overflow: hidden;
  box-shadow: 0 2rpx 12rpx rgba(15, 110, 86, 0.04);
}
.reco-img {
  width: 330rpx;
  height: 240rpx;
  background: #f2efe8;
}
.reco-body {
  padding: 14rpx 16rpx 18rpx;
}
.reco-name {
  font-size: 24rpx;
  color: #1F2937;
  line-height: 1.35;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  height: 66rpx;
}
.reco-foot {
  margin-top: 10rpx;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}
.reco-price {
  color: #C0392B;
  font-size: 28rpx;
  font-weight: 700;
}
.reco-sales {
  font-size: 20rpx;
  color: #9CA3AF;
}

/* ---------- 10. 底部操作条 ---------- */
.bottom-space {
  height: 140rpx;
}
.bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: #fff;
  box-shadow: 0 -4rpx 20rpx rgba(0, 0, 0, 0.06);
  z-index: 100;
}
.bar-side {
  width: 96rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2rpx;
}
.bar-ico {
  font-size: 32rpx;
  line-height: 1.1;
}
.bar-txt {
  font-size: 18rpx;
  color: #9CA3AF;
}
.bar-btn {
  flex: 1;
  height: 84rpx;
  line-height: 84rpx;
  text-align: center;
  border-radius: 42rpx;
  font-size: 28rpx;
  font-weight: 600;
  padding: 0;
  margin: 0;
}
.bar-btn.cart {
  background: #F1F7F4;
  color: #0F6E56;
  border: 1rpx solid #0F6E56;
}
.bar-btn.buy {
  background: #0F6E56;
  color: #fff;
}
.bar-btn[disabled] {
  background: #D8D4CA;
  color: #fff;
  border-color: #D8D4CA;
}
</style>
