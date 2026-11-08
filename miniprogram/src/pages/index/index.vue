<template>
  <view class="home">
    <!-- ===== 沉浸式头部（A 方案）：深绿底 + 真搜索框 + 配送承诺 ===== -->
    <view class="hero" :style="{ paddingTop: statusBarHeight + 'px' }">
      <view class="hero-nav">
        <view class="brand">
          <image class="brand-mark" src="/static/logo.png" mode="aspectFit" />
          <text class="brand-name">{{ BRAND_NAME }}</text>
        </view>
        <!-- 右侧必须留空：微信胶囊按钮就压在这个位置，放任何东西都会被挡住 -->
      </view>
      <!-- 搜索框：直接在首页输入，边打字边出结果，不必先跳搜索页 -->
      <view class="search-wrap">
        <view class="search">
          <view class="s-lens"></view>
          <input
            class="s-input"
            v-model="keyword"
            confirm-type="search"
            placeholder="搜索 帝王蟹 / 车厘子"
            placeholder-class="s-placeholder"
            @input="onKeywordInput"
            @confirm="doSearch"
          />
          <text class="s-btn" v-if="keyword.trim()" @click.stop="doSearch">搜索</text>
        </view>
        <view class="sug" v-if="sugVisible">
          <view class="sug-loading" v-if="sugLoading">搜索中…</view>
          <block v-else>
            <view
              class="sug-item"
              v-for="p in sugList"
              :key="p.id"
              @click="openSuggest(p)"
            >
              <image class="sug-img" :src="fullUrl(p.coverUrl)" mode="aspectFill" />
              <view class="sug-info">
                <text class="sug-name">{{ p.name }}</text>
                <text class="sug-sub" v-if="p.subtitle">{{ p.subtitle }}</text>
              </view>
              <text class="sug-price">¥{{ p.price }}</text>
            </view>
            <view class="sug-all" @click="doSearch" v-if="sugList.length">查看全部结果 ›</view>
            <view class="sug-empty" v-if="!sugList.length && !sugLoading">
              没找到「{{ keyword }}」相关的商品
            </view>
          </block>
        </view>
      </view>
      <view class="deliver">
        <view class="d-dot"></view>
        <text class="d-text">最快 30 分钟送达 · 满 99 免运费</text>
      </view>
    </view>

    <!-- ===== 主轮播 ===== -->
    <swiper
      v-if="banners.length"
      class="banner"
      indicator-dots
      indicator-color="rgba(255,255,255,0.45)"
      indicator-active-color="#ffffff"
      autoplay
      circular
      interval="3500"
      duration="500"
    >
      <swiper-item v-for="b in banners" :key="b.id" @click="onBanner(b)">
        <image class="b-img" :src="fullUrl(b.imageUrl)" mode="aspectFill" />
      </swiper-item>
    </swiper>

    <!-- ===== 圆形品类入口 ===== -->
    <view class="quick" v-if="quickCats.length">
      <view v-for="c in quickCats" :key="c.key" class="quick-item" @click="goCategory(c.id)">
        <view class="quick-ico" :style="{ background: c.bg }">
          <image class="quick-ico-img" :src="c.iconSrc" mode="aspectFit" />
        </view>
        <text class="quick-t">{{ c.shortName }}</text>
      </view>
    </view>

    <!-- ===== 限时秒杀（A 方案的红底块） ===== -->
    <view class="flash" v-if="flashSales.length">
      <view class="flash-head">
        <view class="flash-title">
          <text class="flash-t">限时秒杀</text>
          <text class="flash-sub" v-if="flashEndLabel">{{ flashEndLabel }}</text>
        </view>
        <text class="flash-cd" v-if="flashCountdown">{{ flashCountdown }}</text>
      </view>
      <scroll-view scroll-x class="flash-scroll" :show-scrollbar="false">
        <view v-for="f in flashSales" :key="f.id" class="fcard" @click="goFlash(f)">
          <image class="f-cover" :src="fullUrl(f.productCoverUrl)" mode="aspectFill" />
          <view class="f-info">
            <text class="f-name">{{ f.productName }}</text>
            <view class="f-price-row">
              <text class="f-price">¥{{ f.flashPrice }}</text>
              <text class="f-origin">¥{{ f.price }}</text>
            </view>
            <view class="f-bar" v-if="f.state === 'RUNNING'">
              <view class="f-bar-in" :style="{ width: (f.progressPercent || 0) + '%' }"></view>
            </view>
            <text class="f-state" v-if="f.state === 'RUNNING'">已抢 {{ f.progressPercent || 0 }}%</text>
            <text class="f-state" v-else>{{ flashStateLabel(f.state) }}</text>
          </view>
        </view>
        <view class="fcard fcard-ghost" v-if="flashSales.length > 1"></view>
      </scroll-view>
    </view>

    <!-- 热搜模块已按需求移除（2026-11-08）：首页不再展示热搜词条，
         分类楼层直接接在搜索区下方。api/hotSearch 与后台热搜管理仍然保留，
         以后想恢复只需把这段 scroll-view 加回来。 -->

    <!-- ===== 分类楼层（B 方案的双列卡：卖点标签 + 销量 + 一步加购） ===== -->
    <view
      v-for="fl in floors"
      :key="fl.category.id"
      :class="'floor floor-' + fl.category.id"
    >
      <view class="floor-head">
        <text class="floor-t">{{ fl.category.name }}</text>
        <text class="floor-more" @click="goCategory(fl.category.id)">查看全部</text>
      </view>
      <view class="grid">
        <view
          v-for="p in fl.products"
          :key="p.id"
          class="pcard"
          @click="goDetail(p.id)"
        >
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
                :class="{ 'p-add-busy': addingId === p.id, 'p-add-off': (p.stock || 0) <= 0, 'p-add-pop': popId === p.id }"
                @click.stop="quickAdd(p)"
              >
                <text class="p-add-ico">{{ (p.stock || 0) <= 0 ? '·' : '+' }}</text>
              </view>
            </view>
          </view>
        </view>
      </view>
      <view class="floor-empty" v-if="!fl.products.length">该分类暂无商品</view>
    </view>

    <view class="footer-tip">已经到底啦 ~</view>

    <!-- 加购后的底部轻提示条（不常驻、不挡商品）：3 秒自动收起，可直接去购物车结算 -->
    <view class="cart-tip" :class="{ show: cartTipVisible }" @click="goCartFromTip">
      <view class="cart-tip-left">
        <text class="cart-tip-emoji">🛒</text>
        <text class="cart-tip-text">已加入购物车<text class="cart-tip-count">（{{ cartTipCount }} 件）</text></text>
      </view>
      <text class="cart-tip-action">去结算 ›</text>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { onShow, onHide, onUnload } from '@dcloudio/uni-app'
import { listCategories, listProducts } from '@/api/product'
import { listBanners } from '@/api/banner'
import { listFlashSales } from '@/api/flashSale'
import { addToCart } from '@/api/cart'
import { isLoggedIn } from '@/store/auth'
import { fullUrl, BRAND_NAME } from '@/config'
import { flashStateLabel, formatCountdown } from '@/utils/format'

const categories = ref([])
const banners = ref([])
const flashSales = ref([])
const floors = ref([])
const addingId = ref(null)
// 加号动画：popId 单独控制（不用 addingId —— 请求快时它一闪就空，动画来不及播）
const popId = ref(null)
// 加购后的底部轻提示条状态
const cartTipVisible = ref(false)
const cartTipCount = ref(0)
let cartTipTimer = null
const statusBarHeight = ref(20)

// 品类圆形入口的底色（浅色系）+ 图标映射（分类名关键词 → static/category/*.png）
const QUICK_PALETTE = [
  { bg: '#EAF3DE' },
  { bg: '#FAEEDA' },
  { bg: '#E6F1FB' },
  { bg: '#FBEAF0' },
  { bg: '#EDEAE3' }
]

const CATEGORY_ICON_RULES = [
  { keys: ['中秋', '团圆', '月饼'], icon: 'moon' },
  { keys: ['生鲜', '果蔬', '水果', '蔬菜'], icon: 'fresh' },
  { keys: ['酒水', '饮料', '酒'], icon: 'wine' },
  { keys: ['零食', '休闲'], icon: 'snack' },
  { keys: ['日用', '百货'], icon: 'bottle' },
  { keys: ['粮油', '调味'], icon: 'grain' },
  { keys: ['乳品', '烘焙', '奶'], icon: 'milk' },
  { keys: ['冲调', '饮品', '咖啡', '茶'], icon: 'coffee' },
  { keys: ['冷冻', '速食'], icon: 'snow' },
  { keys: ['清洁', '纸品', '纸'], icon: 'tissue' },
  { keys: ['个人', '护理', '美妆'], icon: 'care' }
]

/** 分类名 → 图标。后端分类是动态的，认不出来的走通用购物袋，不会出现空圆 */
function categoryIcon(name) {
  const n = name || ''
  const hit = CATEGORY_ICON_RULES.find((r) => r.keys.some((k) => n.includes(k)))
  return '/static/category/' + (hit ? hit.icon : 'bag') + '.png'
}

let timer = null

try {
  const info = uni.getWindowInfo ? uni.getWindowInfo() : uni.getSystemInfoSync()
  statusBarHeight.value = info.statusBarHeight || 20
} catch (e) {
  statusBarHeight.value = 20
}

// 首页只展示前 4 个分类 + 「全部」，其余靠分类页承接
const quickCats = computed(() => {
  const head = categories.value.slice(0, 4).map((c, i) => {
    const p = QUICK_PALETTE[i % QUICK_PALETTE.length]
    return {
      key: 'c' + c.id,
      id: c.id,
      shortName: c.name && c.name.length > 4 ? c.name.slice(0, 4) : c.name,
      iconSrc: categoryIcon(c.name),
      bg: p.bg
    }
  })
  // 第 5 格固定「全部」：id 为 null，进分类页时不带 categoryId（落在第一个分类）
  if (categories.value.length > 4) {
    head.push({
      key: 'all',
      id: null,
      shortName: '全部',
      iconSrc: '/static/category/grid.png',
      bg: QUICK_PALETTE[4].bg
    })
  }
  return head
})

// 秒杀头部：倒计时只显示「进行中最早结束」的那一场，避免一排数字挤在一起
const flashCountdown = computed(() => {
  const running = flashSales.value.find((f) => f.state === 'RUNNING' && f.remaining > 0)
  return running ? formatCountdown(running.remaining) : ''
})
const flashEndLabel = computed(() => {
  const upcoming = flashSales.value.find((f) => f.state === 'UPCOMING')
  return upcoming ? '即将开始' : ''
})

function showOrigin(p) {
  return p.originalPrice != null && Number(p.originalPrice) > Number(p.price || 0)
}

/** 商品卡卖点标签：优先用后台维护的 tags，其次按 isNew / isHot 兜底 */
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

async function load() {
  // 热搜已从首页移除，不再请求 /hot-searches（少一次网络往返）
  const [cats, bn, fs] = await Promise.all([
    listCategories(),
    listBanners(),
    listFlashSales()
  ])
  categories.value = cats || []
  banners.value = (bn || []).filter((b) => b.enabled !== false)
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

/** 一步加购：不跳详情页，加完弹底部轻提示条（可去结算）并同步 tabBar 角标 */
async function quickAdd(p) {
  if (addingId.value) return
  if ((p.stock || 0) <= 0) {
    uni.showToast({ title: '这件已售罄，看看别的', icon: 'none' })
    return
  }
  addingId.value = p.id
  // 加号弹一下（460ms 后清掉 class，下次点击才能重新触发动画）
  popId.value = p.id
  setTimeout(() => { if (popId.value === p.id) popId.value = null }, 460)
  try {
    await addToCart(p.id, 1, '')
    const n = await refreshCartBadge()
    showCartTip(n)
  } catch (err) {
    uni.showToast({ title: (err && err.message) || '加购失败，请重试', icon: 'none' })
  } finally {
    addingId.value = null
  }
}

/** 底部轻提示条：3 秒自动收起；连续加购时只刷新数字并重置计时 */
function showCartTip(count) {
  cartTipCount.value = Number(count) || 1
  cartTipVisible.value = true
  if (cartTipTimer) clearTimeout(cartTipTimer)
  cartTipTimer = setTimeout(() => { cartTipVisible.value = false }, 3000)
}

function goCartFromTip() {
  cartTipVisible.value = false
  if (cartTipTimer) { clearTimeout(cartTipTimer); cartTipTimer = null }
  uni.switchTab({ url: '/pages/cart/cart' })
}

async function refreshCartBadge() {
  try {
    const { getCart } = await import('@/api/cart')
    const cart = await getCart()
    const list = (cart && cart.items) || (Array.isArray(cart) ? cart : [])
    const n = list.reduce((s, it) => s + (Number(it.quantity) || 0), 0)
    if (n > 0) {
      uni.setTabBarBadge({ index: 1, text: n > 99 ? '99+' : String(n) })
    } else {
      uni.removeTabBarBadge({ index: 1 })
    }
    return n
  } catch (e) {
    // 角标失败不影响主流程
    return 0
  }
}

// —— 首页原地搜索：边打字边出结果，点结果直接进详情；
//    只有点「搜索」或「查看全部结果」才进搜索页（原来点搜索框整块跳页，用户反馈不该这样）
const keyword = ref('')
const sugList = ref([])
const sugVisible = ref(false)
const sugLoading = ref(false)
let sugTimer = null

function onKeywordInput(e) {
  const v = (e && e.detail && e.detail.value != null) ? e.detail.value : keyword.value
  keyword.value = v
  if (sugTimer) { clearTimeout(sugTimer); sugTimer = null }
  const kw = String(v || '').trim()
  if (!kw) {
    sugList.value = []
    sugVisible.value = false
    sugLoading.value = false
    return
  }
  sugVisible.value = true
  sugLoading.value = true
  // 300ms 防抖：别每敲一个字都打一次接口
  sugTimer = setTimeout(() => runSuggest(kw), 300)
}

async function runSuggest(kw) {
  try {
    const res = await listProducts({ keyword: kw, page: 1, size: 6 })
    // 回包时用户可能已经改了关键词，过期结果丢掉
    if (kw === String(keyword.value || '').trim()) {
      sugList.value = (res && res.items) || []
    }
  } catch (e) {
    sugList.value = []
  } finally {
    sugLoading.value = false
  }
}

function doSearch() {
  const kw = String(keyword.value || '').trim()
  sugVisible.value = false
  if (sugTimer) { clearTimeout(sugTimer); sugTimer = null }
  goSearch(kw)
}

function openSuggest(p) {
  sugVisible.value = false
  sugList.value = []
  keyword.value = ''
  uni.navigateTo({ url: '/pages/product/detail?id=' + p.id })
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

/** 圆形品类入口：进分类页并定位到该分类；「全部」不带参数，分类页落在第一个分类 */
function goCategory(id) {
  const q = id ? '?categoryId=' + id : ''
  uni.navigateTo({ url: '/pages/category/category' + q })
}

onMounted(() => {
  refreshCartBadge()
})

onShow(() => {
  if (!isLoggedIn()) {
    uni.navigateTo({ url: '/pages/login/login' })
    return
  }
  load()
  startTimer()
  refreshCartBadge()
})

onHide(stopTimer)
onUnload(stopTimer)
onUnmounted(() => {
  stopTimer()
  if (cartTipTimer) { clearTimeout(cartTipTimer); cartTipTimer = null }
  if (sugTimer) { clearTimeout(sugTimer); sugTimer = null }
})
</script>

<style scoped>
.home {
  min-height: 100vh;
  background: #FAF8F3;
  padding-bottom: 40rpx;
}

/* ---------- 沉浸头部 ---------- */
.hero {
  background: #0F6E56;
  padding-left: 24rpx;
  padding-right: 24rpx;
  padding-bottom: 22rpx;
  border-bottom-left-radius: 28rpx;
  border-bottom-right-radius: 28rpx;
}
.hero-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 44px;
}
.brand {
  display: flex;
  align-items: center;
}
.brand-mark {
  /* 现在是真的 logo 图（/static/logo.png，144×144 圆角渐变 + 购物袋），
     图本身带圆角，这里只控制尺寸与间距 */
  width: 44rpx;
  height: 44rpx;
  border-radius: 12rpx;
  margin-right: 14rpx;
  flex-shrink: 0;
}
.brand-name {
  font-size: 32rpx;
  color: #ffffff;
  font-weight: 600;
  letter-spacing: 1rpx;
}
/* 搜索区：容器负责给联想浮层做定位父级 */
.search-wrap {
  position: relative;
  z-index: 20;
}
.search {
  display: flex;
  align-items: center;
  height: 68rpx;
  background: #ffffff;
  border-radius: 34rpx;
  padding: 0 22rpx;
}
.s-lens {
  width: 22rpx;
  height: 22rpx;
  border: 3rpx solid #9CA3AF;
  border-radius: 50%;
  margin-right: 14rpx;
  flex-shrink: 0;
}
.s-input {
  flex: 1;
  height: 68rpx;
  font-size: 26rpx;
  color: #1F2937;
  line-height: 68rpx;
}
.s-placeholder {
  color: #9CA3AF;
  font-size: 26rpx;
}
.s-btn {
  margin-left: 12rpx;
  padding: 0 20rpx;
  height: 48rpx;
  line-height: 48rpx;
  background: #0F6E56;
  color: #ffffff;
  font-size: 24rpx;
  border-radius: 24rpx;
  flex-shrink: 0;
}

/* 搜索联想浮层：在首页直接看到结果，不必先跳搜索页 */
.sug {
  position: absolute;
  left: 0;
  right: 0;
  top: 76rpx;
  background: #ffffff;
  border-radius: 18rpx;
  box-shadow: 0 12rpx 40rpx rgba(0, 0, 0, 0.16);
  padding: 6rpx 0;
  overflow: hidden;
}
.sug-loading {
  padding: 26rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: #9CA3AF;
}
.sug-item {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 20rpx;
}
.sug-img {
  width: 76rpx;
  height: 76rpx;
  border-radius: 10rpx;
  background: #f2efe8;
  flex-shrink: 0;
}
.sug-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
  overflow: hidden;
}
.sug-name {
  font-size: 26rpx;
  color: #1F2937;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.sug-sub {
  font-size: 21rpx;
  color: #9CA3AF;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.sug-price {
  font-size: 26rpx;
  color: #C0392B;
  font-weight: 600;
  flex-shrink: 0;
}
.sug-all {
  margin-top: 4rpx;
  padding: 20rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: #0F6E56;
  border-top: 1rpx solid #F5F2EC;
}
.sug-empty {
  padding: 30rpx 20rpx;
  text-align: center;
  font-size: 24rpx;
  color: #9CA3AF;
}
.s-scan {
  width: 32rpx;
  height: 32rpx;
  border-radius: 8rpx;
  background: #F1F2F4;
}
.deliver {
  display: flex;
  align-items: center;
  margin-top: 16rpx;
}
.d-dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: #8FD3BC;
  margin-right: 10rpx;
}
.d-text {
  font-size: 22rpx;
  color: #B7E0D1;
}

/* ---------- 轮播 ---------- */
.banner {
  height: 300rpx;
  margin: 20rpx 24rpx 0;
  border-radius: 20rpx;
  overflow: hidden;
}
.b-img {
  width: 100%;
  height: 300rpx;
}

/* ---------- 圆形品类 ---------- */
.quick {
  display: flex;
  justify-content: space-between;
  padding: 26rpx 28rpx 6rpx;
}
.quick-item {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.quick-ico {
  width: 84rpx;
  height: 84rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.quick-ico-img {
  width: 52rpx;
  height: 52rpx;
}
.quick-t {
  margin-top: 10rpx;
  font-size: 22rpx;
  color: #374151;
}

/* ---------- 秒杀红底块 ---------- */
.flash {
  margin: 22rpx 24rpx 0;
  background: #C0392B;
  border-radius: 22rpx;
  padding: 20rpx 20rpx 22rpx;
}
.flash-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16rpx;
}
.flash-title {
  display: flex;
  align-items: center;
}
.flash-t {
  font-size: 30rpx;
  color: #ffffff;
  font-weight: 500;
  margin-right: 12rpx;
}
.flash-sub {
  font-size: 22rpx;
  color: #FFD9D3;
}
.flash-cd {
  font-size: 28rpx;
  color: #ffffff;
  font-weight: 500;
}
.flash-scroll {
  white-space: nowrap;
}
.fcard {
  display: inline-block;
  width: 344rpx;
  background: #ffffff;
  border-radius: 16rpx;
  padding: 12rpx;
  margin-right: 16rpx;
  vertical-align: top;
}
.fcard-ghost {
  width: 8rpx;
  background: transparent;
  padding: 0;
}
.f-cover {
  width: 92rpx;
  height: 92rpx;
  border-radius: 12rpx;
  background: #F3D9D4;
  display: inline-block;
  vertical-align: top;
}
.f-info {
  display: inline-block;
  width: 216rpx;
  margin-left: 14rpx;
  vertical-align: top;
}
.f-name {
  display: block;
  font-size: 24rpx;
  color: #374151;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.f-price-row {
  display: flex;
  align-items: baseline;
  margin-top: 4rpx;
}
.f-price {
  font-size: 30rpx;
  color: #C0392B;
  font-weight: 500;
}
.f-origin {
  font-size: 20rpx;
  color: #9CA3AF;
  text-decoration: line-through;
  margin-left: 8rpx;
}
.f-bar {
  height: 8rpx;
  background: #F0D6D1;
  border-radius: 4rpx;
  margin-top: 8rpx;
  overflow: hidden;
}
.f-bar-in {
  height: 8rpx;
  background: #C0392B;
}
.f-state {
  display: block;
  font-size: 20rpx;
  color: #9CA3AF;
  margin-top: 6rpx;
}

/* ---------- 热搜 ---------- */
.hot {
  white-space: nowrap;
  padding: 22rpx 24rpx 4rpx;
}
.hot-label {
  font-size: 22rpx;
  color: #9CA3AF;
  margin-right: 14rpx;
  vertical-align: middle;
}
.hot-chip {
  display: inline-flex;
  align-items: center;
  background: #ffffff;
  border-radius: 26rpx;
  padding: 8rpx 20rpx 8rpx 8rpx;
  margin-right: 14rpx;
  vertical-align: middle;
}
.hot-rank {
  width: 30rpx;
  height: 30rpx;
  border-radius: 50%;
  background: #F1EFE8;
  color: #888780;
  font-size: 20rpx;
  text-align: center;
  line-height: 30rpx;
  margin-right: 10rpx;
}
.r1 {
  background: #C0392B;
  color: #ffffff;
}
.r2 {
  background: #D97757;
  color: #ffffff;
}
.r3 {
  background: #BA7517;
  color: #ffffff;
}
.hot-kw {
  font-size: 24rpx;
  color: #374151;
}

/* ---------- 楼层 ---------- */
.floor {
  margin-top: 24rpx;
}
.floor-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 28rpx 14rpx;
}
.floor-t {
  font-size: 30rpx;
  color: #111827;
  font-weight: 500;
}
.floor-more {
  font-size: 22rpx;
  color: #9CA3AF;
}
.grid {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  padding: 0 24rpx;
}
.pcard {
  width: 336rpx;
  background: #ffffff;
  border-radius: 20rpx;
  overflow: hidden;
  margin-bottom: 18rpx;
}
.p-cover-wrap {
  position: relative;
  width: 336rpx;
  height: 336rpx;
}
.p-cover {
  width: 336rpx;
  height: 336rpx;
  background: #EDF0EE;
}
.p-tag {
  position: absolute;
  left: 12rpx;
  top: 12rpx;
  background: #0F6E56;
  color: #ffffff;
  font-size: 20rpx;
  padding: 2rpx 10rpx;
  border-radius: 8rpx;
}
.p-soldout {
  position: absolute;
  left: 0;
  top: 0;
  width: 336rpx;
  height: 336rpx;
  background: rgba(255, 255, 255, 0.72);
  display: flex;
  align-items: center;
  justify-content: center;
}
.p-soldout-t {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.9);
  color: #6B7280;
  font-size: 22rpx;
  text-align: center;
  line-height: 96rpx;
}
.p-body {
  padding: 12rpx 14rpx 16rpx;
}
.p-name {
  display: block;
  font-size: 25rpx;
  color: #111827;
  line-height: 1.35;
  height: 68rpx;
  overflow: hidden;
}
.p-price-row {
  display: flex;
  align-items: baseline;
  margin-top: 8rpx;
}
.p-price {
  font-size: 30rpx;
  color: #C0392B;
  font-weight: 500;
}
.p-origin {
  font-size: 20rpx;
  color: #9CA3AF;
  text-decoration: line-through;
  margin-left: 8rpx;
}
.p-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10rpx;
}
.p-sales {
  font-size: 20rpx;
  color: #9CA3AF;
}
.p-add {
  width: 52rpx;
  height: 52rpx;
  border-radius: 50%;
  background: #0F6E56;
  display: flex;
  align-items: center;
  justify-content: center;
  /* 点击时按下反馈（配合 .p-add-pop 的回弹动画） */
  transition: transform 0.18s cubic-bezier(0.34, 1.56, 0.64, 1), background 0.2s ease;
}
/* 点加号时的回弹：先压下去再弹回，带一点点过冲，手感更"实" */
@keyframes p-add-pop {
  0% { transform: scale(1); }
  38% { transform: scale(0.78); }
  72% { transform: scale(1.16); }
  100% { transform: scale(1); }
}
.p-add-pop {
  animation: p-add-pop 0.46s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.p-add-busy {
  background: #1D9E75;
}
.p-add-off {
  background: #E5E7EB;
}
.p-add-ico {
  color: #ffffff;
  font-size: 32rpx;
  line-height: 1;
}
.floor-empty {
  padding: 30rpx 28rpx;
  font-size: 24rpx;
  color: #9CA3AF;
  text-align: center;
}

.footer-tip {
  text-align: center;
  font-size: 22rpx;
  color: #C4C0B6;
  padding: 24rpx 0 8rpx;
}

/* ---------- 加购后的底部轻提示条 ----------
   默认移出屏幕 + 透明 + 不接收点击；.show 时滑入。
   bottom 抬到 tabBar 之上（小程序 tabBar 约 100rpx + 安全区），避免被挡。 */
.cart-tip {
  position: fixed;
  left: 24rpx;
  right: 24rpx;
  bottom: calc(118rpx + env(safe-area-inset-bottom));
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 22rpx 26rpx;
  background: #0F6E56;
  border-radius: 18rpx;
  box-shadow: 0 10rpx 30rpx rgba(15, 110, 86, 0.3);
  transform: translateY(200rpx);
  opacity: 0;
  pointer-events: none;
  transition: transform 0.32s cubic-bezier(0.34, 1.2, 0.64, 1), opacity 0.24s ease;
  z-index: 900;
}
.cart-tip.show {
  transform: translateY(0);
  opacity: 1;
  pointer-events: auto;
}
.cart-tip-left {
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.cart-tip-emoji {
  font-size: 30rpx;
  line-height: 1;
}
.cart-tip-text {
  color: #ffffff;
  font-size: 26rpx;
}
.cart-tip-count {
  color: #CDEADF;
  font-size: 24rpx;
}
.cart-tip-action {
  color: #ffffff;
  font-size: 26rpx;
  font-weight: 600;
  padding-left: 16rpx;
}
</style>
