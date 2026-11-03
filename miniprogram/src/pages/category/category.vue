<template>
  <view class="cat">
    <!-- 顶栏：搜索入口（深绿底，与首页头部同色系） -->
    <view class="topbar">
      <view class="search" @click="goSearch">
        <view class="s-lens"></view>
        <text class="s-ph">搜索商品</text>
      </view>
    </view>

    <view class="body">
      <!-- 左侧一级分类 -->
      <scroll-view
        scroll-y
        class="side"
        :scroll-into-view="sideIntoView"
        :scroll-with-animation="true"
        :show-scrollbar="false"
      >
        <view
          v-for="c in categories"
          :key="c.id"
          :id="'side-' + c.id"
          class="side-item"
          :class="{ on: c.id === activeId }"
          @click="pick(c.id)"
        >
          <text class="side-t">{{ c.name }}</text>
        </view>
        <view class="side-pad"></view>
      </scroll-view>

      <!-- 右侧商品 -->
      <scroll-view
        scroll-y
        class="main"
        :lower-threshold="90"
        :show-scrollbar="false"
        @scrolltolower="loadMore"
      >
        <view class="main-head">
          <text class="main-t">{{ activeName }}</text>
          <text class="main-count" v-if="total">{{ total }} 件</text>
        </view>

        <view class="grid">
          <view v-for="p in products" :key="p.id" class="pcard" @click="goDetail(p.id)">
            <view class="p-cover-wrap">
              <image class="p-cover" :src="fullUrl(p.coverUrl)" mode="aspectFill" />
              <view class="p-tag" v-if="tagOf(p)">{{ tagOf(p) }}</view>
              <view class="p-soldout" v-if="(p.stock || 0) <= 0">
                <text class="p-soldout-t">售罄</text>
              </view>
            </view>
            <view class="p-body">
              <text class="p-name">{{ p.name }}</text>
              <view class="p-price-row">
                <text class="p-price">¥{{ p.price }}</text>
                <text class="p-origin" v-if="showOrigin(p)">¥{{ p.originalPrice }}</text>
              </view>
              <view class="p-foot">
                <text class="p-sales">{{ salesText(p) }}</text>
                <view
                  class="p-add"
                  :class="{ 'p-add-off': (p.stock || 0) <= 0 }"
                  @click.stop="quickAdd(p)"
                >
                  <text class="p-add-ico">{{ (p.stock || 0) <= 0 ? '·' : '+' }}</text>
                </view>
              </view>
            </view>
          </view>
        </view>

        <view class="list-empty" v-if="!loading && !products.length">该分类暂无商品</view>
        <view class="list-tip" v-else>{{ loading ? '加载中…' : (noMore ? '没有更多了' : '上拉加载更多') }}</view>
      </scroll-view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { listCategories, listProducts } from '@/api/product'
import { addToCart, getCart } from '@/api/cart'
import { fullUrl } from '@/config'

const PAGE_SIZE = 20

const categories = ref([])
const activeId = ref(null)
const products = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const noMore = ref(false)
const sideIntoView = ref('')
const addingId = ref(null)
let startCategoryId = null

const activeName = computed(() => {
  const hit = categories.value.find((c) => c.id === activeId.value)
  return hit ? hit.name : '全部商品'
})

function showOrigin(p) {
  return p.originalPrice != null && Number(p.originalPrice) > Number(p.price || 0)
}

function tagOf(p) {
  if (p.tags && String(p.tags).trim()) {
    const first = String(p.tags).split(/[,，\s]+/).filter(Boolean)[0]
    if (first) return first.length > 4 ? first.slice(0, 4) : first
  }
  if (p.isNew === 1) return '新品'
  if (p.isHot === 1) return '热卖'
  return ''
}

function salesText(p) {
  const n = Number(p.sales || 0)
  if (!n) return '新品上架'
  if (n >= 10000) return '已售 ' + (n / 10000).toFixed(1) + '万'
  return '已售 ' + n
}

async function loadPage(target) {
  if (loading.value) return
  loading.value = true
  try {
    const res = await listProducts({
      categoryId: activeId.value,
      page: target,
      size: PAGE_SIZE
    })
    const items = (res && res.items) || []
    total.value = (res && res.total) || 0
    products.value = target === 1 ? items : products.value.concat(items)
    page.value = target
    noMore.value = products.value.length >= total.value || items.length < PAGE_SIZE
  } catch (e) {
    // 单个分类加载失败不该让整页白屏，保留已加载内容
    noMore.value = true
  } finally {
    loading.value = false
  }
}

async function pick(id, force) {
  if (!force && id === activeId.value) return
  activeId.value = id
  sideIntoView.value = ''
  // 下一帧再设 scroll-into-view，否则同值不会触发滚动
  setTimeout(() => { sideIntoView.value = 'side-' + id }, 30)
  products.value = []
  total.value = 0
  page.value = 1
  noMore.value = false
  await loadPage(1)
}

function loadMore() {
  if (!loading.value && !noMore.value) loadPage(page.value + 1)
}

async function loadCategories() {
  const cats = await listCategories()
  categories.value = cats || []
  const wanted = startCategoryId || (categories.value[0] && categories.value[0].id)
  if (wanted) await pick(wanted, true)
}

async function quickAdd(p) {
  if (addingId.value) return
  if ((p.stock || 0) <= 0) {
    uni.showToast({ title: '这件已售罄，看看别的', icon: 'none' })
    return
  }
  addingId.value = p.id
  try {
    await addToCart(p.id, 1, '')
    uni.showToast({ title: '已加入购物车', icon: 'none', duration: 1200 })
    refreshCartBadge()
  } catch (err) {
    uni.showToast({ title: (err && err.message) || '加购失败，请重试', icon: 'none' })
  } finally {
    addingId.value = null
  }
}

async function refreshCartBadge() {
  try {
    const cart = await getCart()
    const list = (cart && cart.items) || (Array.isArray(cart) ? cart : [])
    const n = list.reduce((s, it) => s + (Number(it.quantity) || 0), 0)
    if (n > 0) uni.setTabBarBadge({ index: 1, text: n > 99 ? '99+' : String(n) })
    else uni.removeTabBarBadge({ index: 1 })
  } catch (e) {
    // 角标失败不影响主流程
  }
}

function goSearch() {
  uni.navigateTo({ url: '/pages/search/search' })
}

function goDetail(id) {
  uni.navigateTo({ url: '/pages/product/detail?id=' + id })
}

onLoad((options) => {
  startCategoryId = options && options.categoryId ? Number(options.categoryId) : null
  loadCategories()
  refreshCartBadge()
})
</script>

<style scoped>
.cat {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #FAF8F3;
}

/* ---------- 顶栏 ---------- */
.topbar {
  background: #0F6E56;
  padding: 16rpx 24rpx 18rpx;
}
.search {
  display: flex;
  align-items: center;
  height: 64rpx;
  background: #ffffff;
  border-radius: 32rpx;
  padding: 0 22rpx;
}
.s-lens {
  width: 20rpx;
  height: 20rpx;
  border: 3rpx solid #9CA3AF;
  border-radius: 50%;
  margin-right: 14rpx;
}
.s-ph {
  font-size: 26rpx;
  color: #9CA3AF;
}

/* ---------- 左右分栏 ---------- */
.body {
  display: flex;
  flex: 1;
  min-height: 0;
}
.side {
  width: 180rpx;
  height: 100%;
  background: #F1EFE8;
}
.side-item {
  padding: 28rpx 16rpx;
  text-align: center;
  border-left: 6rpx solid transparent;
}
.side-item.on {
  background: #FAF8F3;
  border-left-color: #0F6E56;
}
.side-t {
  font-size: 25rpx;
  color: #5F5E5A;
  line-height: 1.3;
}
.side-item.on .side-t {
  color: #0F6E56;
  font-weight: 500;
}
.side-pad {
  height: 40rpx;
}

.main {
  flex: 1;
  height: 100%;
  padding: 0 20rpx;
}
.main-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 20rpx 4rpx 12rpx;
}
.main-t {
  font-size: 28rpx;
  color: #111827;
  font-weight: 500;
}
.main-count {
  font-size: 21rpx;
  color: #9CA3AF;
}

/* ---------- 商品卡（与首页同款，右栏窄所以尺寸略小） ---------- */
.grid {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
}
.pcard {
  width: 246rpx;
  background: #ffffff;
  border-radius: 16rpx;
  overflow: hidden;
  margin-bottom: 16rpx;
}
.p-cover-wrap {
  position: relative;
  width: 246rpx;
  height: 246rpx;
}
.p-cover {
  width: 246rpx;
  height: 246rpx;
  background: #EDF0EE;
}
.p-tag {
  position: absolute;
  left: 10rpx;
  top: 10rpx;
  background: #0F6E56;
  color: #ffffff;
  font-size: 19rpx;
  padding: 2rpx 8rpx;
  border-radius: 6rpx;
}
.p-soldout {
  position: absolute;
  left: 0;
  top: 0;
  width: 246rpx;
  height: 246rpx;
  background: rgba(255, 255, 255, 0.72);
  display: flex;
  align-items: center;
  justify-content: center;
}
.p-soldout-t {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.9);
  color: #6B7280;
  font-size: 21rpx;
  text-align: center;
  line-height: 88rpx;
}
.p-body {
  padding: 10rpx 12rpx 14rpx;
}
.p-name {
  display: block;
  font-size: 24rpx;
  color: #111827;
  line-height: 1.35;
  height: 64rpx;
  overflow: hidden;
}
.p-price-row {
  display: flex;
  align-items: baseline;
  margin-top: 6rpx;
}
.p-price {
  font-size: 28rpx;
  color: #C0392B;
  font-weight: 500;
}
.p-origin {
  font-size: 19rpx;
  color: #9CA3AF;
  text-decoration: line-through;
  margin-left: 6rpx;
}
.p-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8rpx;
}
.p-sales {
  font-size: 19rpx;
  color: #9CA3AF;
}
.p-add {
  width: 46rpx;
  height: 46rpx;
  border-radius: 50%;
  background: #0F6E56;
  display: flex;
  align-items: center;
  justify-content: center;
}
.p-add-off {
  background: #E5E7EB;
}
.p-add-ico {
  color: #ffffff;
  font-size: 28rpx;
  line-height: 1;
}

.list-tip {
  text-align: center;
  font-size: 21rpx;
  color: #C4C0B6;
  padding: 18rpx 0 30rpx;
}
.list-empty {
  text-align: center;
  font-size: 24rpx;
  color: #9CA3AF;
  padding: 80rpx 0;
}
</style>
