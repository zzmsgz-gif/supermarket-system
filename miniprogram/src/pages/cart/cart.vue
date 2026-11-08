<template>
  <view class="cart">
    <view v-for="it in items" :key="it.id" class="row">
      <image class="cover" :src="fullUrl(it.productCoverUrl)" mode="aspectFill" />
      <view class="info">
        <view class="name">{{ it.productName }}</view>
        <view class="spec" v-if="it.skuSpec">{{ specText(it.skuSpec) }}</view>
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

    <!-- ===== 空购物车：不只是"一句话"，给引导 + 推荐，别让整屏空着 ===== -->
    <view class="empty-wrap" v-if="items.length === 0">
      <view class="empty-ico">🛒</view>
      <text class="empty-title">购物车还是空的</text>
      <text class="empty-sub">挑点新鲜的放进来吧，满 ¥99 免运费</text>
      <view class="empty-btn" @click="goHome">去逛逛</view>
      <view class="empty-benefits">
        <view class="eb-item"><text class="eb-ic">⚡</text><text class="eb-tx">最快 30 分钟</text></view>
        <view class="eb-item"><text class="eb-ic">🚚</text><text class="eb-tx">满 ¥99 包邮</text></view>
        <view class="eb-item"><text class="eb-ic">🛡</text><text class="eb-tx">坏果包赔</text></view>
      </view>
    </view>

    <!-- 空车推荐：大家都在买（点进去能直接加购，别让用户空手离开） -->
    <view class="reco" v-if="items.length === 0 && recommends.length">
      <view class="reco-head">
        <text class="reco-line"></text>
        <text class="reco-title">大家都在买</text>
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
import { listHotProducts } from '@/api/product'
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

// skuSpec 存的是「规格:70g」这种带维度名的串（线上存量数据全是这个格式），
// 模板里再硬写「规格：」就显示成「规格：规格:70g」（2026-11-08 修）。
// 只有裸值（不带维度名）才补前缀，两种格式都兼容。
function specText(s) {
  if (!s) return ''
  return /[:：]/.test(s) ? s : '规格：' + s
}

async function load() {
  if (!isLoggedIn()) {
    uni.navigateTo({ url: '/pages/login/login' })
    return
  }
  const res = await getCart()
  items.value = (res && res.items) ? res.items : []
}

// —— 空车时的"大家都在买"推荐 ——
// 空购物车是最容易流失的页面，给点可点的东西；拉过就不再重复拉（tabBar 页每次 onShow 都会触发）
const recommends = ref([])

async function loadRecommends() {
  if (recommends.value.length) return
  try {
    recommends.value = await listHotProducts(6)
  } catch (e) {
    recommends.value = []
  }
}

function goHome() {
  uni.switchTab({ url: '/pages/index/index' })
}

function openProduct(p) {
  uni.navigateTo({ url: '/pages/product/detail?id=' + p.id })
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

onShow(async () => {
  await load()
  // 空车才去拉推荐（有商品时不浪费一次请求）
  if (items.value.length === 0) loadRecommends()
})
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
/* ---------- 空购物车（不是一句话，给引导 + 服务承诺） ---------- */
.empty-wrap {
  padding: 100rpx 48rpx 40rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.empty-ico {
  width: 160rpx;
  height: 160rpx;
  line-height: 160rpx;
  text-align: center;
  border-radius: 50%;
  background: #F1F7F4;
  font-size: 72rpx;
}
.empty-title {
  margin-top: 32rpx;
  font-size: 32rpx;
  font-weight: 600;
  color: #1F2937;
}
.empty-sub {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #9CA3AF;
  text-align: center;
}
.empty-btn {
  margin-top: 36rpx;
  width: 280rpx;
  height: 80rpx;
  line-height: 80rpx;
  text-align: center;
  background: #0F6E56;
  color: #ffffff;
  border-radius: 40rpx;
  font-size: 28rpx;
  font-weight: 600;
}
.empty-benefits {
  margin-top: 48rpx;
  width: 100%;
  display: flex;
  justify-content: space-between;
}
.eb-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}
.eb-ic {
  font-size: 32rpx;
  line-height: 1.2;
}
.eb-tx {
  font-size: 20rpx;
  color: #9CA3AF;
}

/* ---------- 空车推荐（样式跟详情页"猜你喜欢"保持一致） ---------- */
.reco {
  margin: 8rpx 24rpx 0;
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
