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
      <!-- 2026-11-10 按用户要求移除「最快 30 分钟送达 · 满 99 免运费」那行 -->

      <!-- 系统公告：后台「内容管理 → 公告」维护，多条竖向轮播；点开看全文 -->
      <view class="notice" v-if="noticeList.length" @click="openNotice">
        <text class="n-ico">📢</text>
        <swiper
          class="n-swiper"
          vertical
          circular
          autoplay
          :interval="4000"
          :duration="500"
        >
          <swiper-item v-for="n in noticeList" :key="n.id">
            <text class="n-text">{{ n.title }}</text>
          </swiper-item>
        </swiper>
        <text class="n-more">详情 ›</text>
      </view>
    </view>

    <!-- 加载失败提示：接口一件数据都没拿到时显示，把原因和重试入口摆出来。
         没有它的话页面只剩头部 + "已经到底啦"，用户完全不知道是网络问题还是没商品。 -->
    <view class="load-err" v-if="loadError">
      <text class="le-ico">⚠️</text>
      <view class="le-body">
        <text class="le-t">首页内容加载失败</text>
        <text class="le-sub">{{ loadError }}</text>
      </view>
      <text class="le-btn" @click="load">重试</text>
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

    <!-- 2026-11-10 按用户要求移除首页活动横幅。
         活动仍在「购物车凑单进度条」与「商品详情活动标签」展示，口径逻辑在 utils/activity.js。 -->

    <!-- ===== 圆形品类入口 ===== -->
    <view class="quick" v-if="quickCats.length">
      <view v-for="c in quickCats" :key="c.key" class="quick-item" @click="goCategory(c.id)">
        <view class="quick-ico" :style="{ background: c.bg }">
          <image class="quick-ico-img" :src="c.iconSrc" mode="aspectFit" />
        </view>
        <text class="quick-t">{{ c.shortName }}</text>
      </view>
    </view>

    <!-- ===== 限时秒杀：白卡容器 + 横滑商品卡（对齐用户给的参考样式） ===== -->
    <view class="flash" v-if="flashSales.length">
      <view class="flash-head">
        <view class="flash-title-wrap">
          <text class="flash-fire">🔥</text>
          <text class="flash-t">限时秒杀</text>
          <!-- 头部**不放**统一倒计时：这里每张卡是各自独立的秒杀商品、各自有场次结束时间，
               一个「剩 N 天」代表不了全部，反而让人以为所有秒杀都还剩那么久（用户反馈）。
               各自剩余时间标在卡片上，见 .f-left；只有"全部未开始"时才在头部提示 -->
          <text class="flash-sub2" v-if="flashHasUpcoming">即将开始</text>
        </view>
      </view>

      <scroll-view scroll-x class="flash-scroll" :show-scrollbar="false">
        <view v-for="f in flashSales" :key="f.id" class="fcard" @click="goFlash(f)">
          <view class="f-cover-wrap">
            <image class="f-cover" :src="fullUrl(f.productCoverUrl)" mode="aspectFill" />
            <text class="f-badge" v-if="f.state === 'RUNNING'">已抢 {{ f.progressPercent || 0 }}%</text>
            <text class="f-badge f-badge-soon" v-else>{{ flashStateLabel(f.state) }}</text>
          </view>
          <view class="f-info">
            <text class="f-name">{{ f.productName }}</text>
            <!-- 每个秒杀商品各自的剩余时间：独立场次独立算，不能用一个数代表全部 -->
            <text
              class="f-left"
              :class="{ 'f-left-soon': f.remaining > 0 && f.remaining < 3600 }"
              v-if="f.state === 'RUNNING' && f.remaining > 0"
            >{{ flashLeftLabel(f.remaining) }}</text>
            <view class="f-foot">
              <view class="f-price-box">
                <text class="f-price"><text class="f-yen">¥</text>{{ f.flashPrice }}</text>
                <text class="f-origin">¥{{ f.price }}</text>
              </view>
              <!-- ＋ 一步加购（不进详情）：秒杀限购/名额后端在 addItem 里已经校验，
                   前端只做售罄与"已买满"的提前拦截，避免点了才弹错 -->
              <view
                class="f-add"
                :class="{
                  'f-add-busy': addingId === f.productId,
                  'f-add-pop': popId === f.productId,
                  'f-add-off': f.state !== 'RUNNING' || (f.remainingQuota || 0) <= 0
                }"
                @click.stop="quickAddFlash(f)"
              >
                <text class="f-add-ico">{{ (f.state !== 'RUNNING' || (f.remainingQuota || 0) <= 0) ? '·' : '+' }}</text>
              </view>
            </view>
          </view>
        </view>
        <view class="flash-tail"></view>
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
      <EmptyState
        v-if="!fl.products.length"
        icon="🧺"
        title="该分类暂无商品"
        compact
      />
    </view>

    <!-- ===== 猜你喜欢（上拉加载）：复用楼层的双列卡样式 =====
         楼层每类固定 8 件，一部分在售商品在楼层里出不来，滑到底继续按销量加载。 -->
    <view class="floor" v-if="feed.length">
      <view class="floor-head">
        <text class="floor-t">猜你喜欢</text>
      </view>
      <view class="grid">
        <view
          v-for="p in feed"
          :key="'feed-' + p.id"
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
    </view>

    <!-- 底部三态：加载中 / 还能上拉 / 到底 -->
    <view class="footer-tip">
      <text v-if="feedLoading">加载中…</text>
      <text v-else-if="feedHasMore">上拉加载更多</text>
      <text v-else>已经到底啦 ~</text>
    </view>

    <!-- 加购后的底部轻提示条（不常驻、不挡商品）：3 秒自动收起，可直接去购物车结算 -->
    <view class="cart-tip" :class="{ show: cartTipVisible }" @click="goCartFromTip">
      <view class="cart-tip-left">
        <text class="cart-tip-emoji">🛒</text>
        <text class="cart-tip-text">已加入购物车<text class="cart-tip-count">（{{ cartTipCount }} 件）</text></text>
      </view>
      <text class="cart-tip-action">去结算 ›</text>
    </view>

    <!-- 公告详情：点顶部公告条打开，一次展示全部生效中的公告 -->
    <view class="notice-mask" v-if="noticeOpen" @click="noticeOpen = false">
      <view class="notice-sheet" @click.stop>
        <view class="ns-head">
          <text class="ns-title">系统公告</text>
          <text class="ns-close" @click="noticeOpen = false">✕</text>
        </view>
        <scroll-view scroll-y class="ns-body">
          <view v-for="n in noticeList" :key="n.id" class="ns-item">
            <view class="ns-item-head">
              <text class="ns-item-title">{{ n.title }}</text>
              <text class="ns-item-time" v-if="n.publishTime">{{ String(n.publishTime).slice(0, 10) }}</text>
            </view>
            <text class="ns-item-content">{{ n.content }}</text>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { onShow, onHide, onUnload, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import { listCategories, listProducts } from '@/api/product'
import { listBanners } from '@/api/banner'
import { listAnnouncements } from '@/api/announcement'
import { listFlashSales } from '@/api/flashSale'
import { addToCart } from '@/api/cart'
import { requireLogin } from '@/utils/authGuard'
import { fullUrl, BRAND_NAME } from '@/config'
import { flashStateLabel, formatCountdown } from '@/utils/format'
import { syncCartBadge } from '@/utils/cartBadge'
import EmptyState from '@/components/EmptyState/EmptyState.vue'

const categories = ref([])
const banners = ref([])
// 首页加载失败原因（分类一件都没拿到时用它把问题摆出来，而不是留一片空白）
const loadError = ref('')
const flashSales = ref([])
const floors = ref([])
// ===== 「猜你喜欢」上拉加载流 =====
// 楼层每类只放 8 件，112 件在售商品里有 32 件（冷冻速食 16 / 个人护理 5 / 清洁纸品 5 / 生鲜 4 / 乳品 2）
// 在首页根本出不来，用户只能逐个点「查看全部」才能看到 —— 滑到底继续按销量加载更好。
const feed = ref([])
const feedPage = ref(0)
const feedLoading = ref(false)
const feedHasMore = ref(true)
// 页容量取 20：销量前列的商品基本都已经在楼层里，一页 10 个过滤完只剩 4 个左右，
// 用户要上拉十几次才能到底，翻起来很累。
const FEED_SIZE = 20
const addingId = ref(null)
// 加号动画：popId 单独控制（不用 addingId —— 请求快时它一闪就空，动画来不及播）
const popId = ref(null)
// 系统公告（后台「内容管理 → 公告」维护）：顶部竖向轮播条 + 点击看全文
const noticeList = ref([])
const noticeOpen = ref(false)

function openNotice() {
  if (!noticeList.value.length) return
  noticeOpen.value = true
}
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

// 秒杀：**不再**在头部放统一倒计时。
// 每一张卡是独立秒杀商品（各自场次、各自 endTime），用头部一个「剩 N 天」
// 等于拿某一个场次的时间代表全部，用户会误以为抢购窗口一样长（2026-11-17 反馈）。
// 现在按卡片各自显示，见 flashLeftLabel。
const flashHasUpcoming = computed(() =>
  flashSales.value.length > 0 && flashSales.value.every((f) => f.state !== 'RUNNING')
)

/**
 * 单张秒杀卡的剩余时间文案。
 * 几十天的场次显示 HH:MM:SS 会变成「1200:58:44」这种没意义的数字，按量级切换单位。
 */
function flashLeftLabel(sec) {
  const s = Number(sec) || 0
  if (s <= 0) return ''
  if (s >= 86400) return '剩 ' + Math.floor(s / 86400) + ' 天'
  if (s >= 3600) return '剩 ' + Math.floor(s / 3600) + ' 小时'
  return '剩 ' + formatCountdown(s)
}

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
  loadError.value = ''
  // ⚠️ 这里原来是 `Promise.all([listCategories(), listBanners(), listFlashSales()])` 一把梭，
  // 只有公告带了 .catch。结果任意一个请求失败（最常见：真机/线上没配 request 合法域名，
  // 报 `request:fail url not in domain list`）→ 整个 Promise.all 抛错 →
  // 首页只渲染静态头部，数据区全空、底部显示"已经到底啦"，**且没有任何错误提示**，
  // 现场很难判断是网络问题还是没数据。改为逐个 catch：一个挂了不影响其他，并记录首个错误用于提示。
  const errors = []
  const grab = async (promise, fallback, label) => {
    try {
      return await promise
    } catch (e) {
      errors.push(`${label}：${(e && e.message) || '请求失败'}`)
      return fallback
    }
  }
  const [cats, bn, fs, nts] = await Promise.all([
    grab(listCategories(), [], '分类'),
    grab(listBanners(), [], '轮播'),
    grab(listFlashSales(), [], '秒杀'),
    grab(listAnnouncements(), [], '公告')
  ])
  categories.value = cats || []
  banners.value = (bn || []).filter((b) => b.enabled !== false)
  flashSales.value = (fs || []).map((f) => ({ ...f, remaining: f.countdownSeconds || 0 }))
  noticeList.value = (nts || []).filter((n) => n.enabled !== false)

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

  // 楼层就绪后再拉「猜你喜欢」首屏（要拿楼层已展示的 id 去重）
  await loadFeed(true)

  // 分类是首页骨架：一件都拿不到就说明接口根本没通，把原因明确摆给用户（可点重试）
  if (categories.value.length === 0 && errors.length) {
    loadError.value = errors[0]
  }
}

/**
 * 「猜你喜欢」：上拉加载更多。
 *
 * - 按销量降序（sort=sales_desc），先热销后长尾
 * - **跳过楼层已经展示过的商品**，避免同一件东西在首屏出现两次（用户会觉得列表在重复）
 * - 用 `page*size < total` 判还有没有下一页，不猜
 * - 失败时把 hasMore 置 false，否则上拉会无限重试同一个失败请求
 */
async function loadFeed(reset = false) {
  if (feedLoading.value) return
  if (reset) {
    feedPage.value = 0
    feed.value = []
    feedHasMore.value = true
  }
  if (!feedHasMore.value) return
  feedLoading.value = true
  try {
    // 楼层已展示的 id 一次性算好，后面的批次也塞进去，避免 feed 内部跨页重复
    const shown = {}
    for (const f of floors.value) {
      for (const p of (f.products || [])) shown[p.id] = true
    }
    // 首屏至少拉 2 页：销量前列基本都在楼层里，只拉一页过滤完可能只剩一两个，
    // 「猜你喜欢」区块就只剩一两件，看着像坏了。
    // 之后每次上拉继续往后翻，直到**至少攒到 4 个新品**或到底 ——
    // 实测后面几页每页只筛出 1 个新品，上拉一次只多 1 件会让用户觉得「滑了跟没滑一样」。
    // guard 是硬上限，防止异常情况下一直翻页。
    const minRounds = reset ? 2 : 1
    let guard = 0
    let added = 0
    while (guard < 6 && feedHasMore.value && (guard < minRounds || added < 4)) {
      guard += 1
      const res = await listProducts({ page: feedPage.value + 1, size: FEED_SIZE, sort: 'sales_desc' })
      const items = (res && res.items) || []
      const fresh = items.filter((p) => !shown[p.id])
      fresh.forEach((p) => { shown[p.id] = true })
      feed.value = feed.value.concat(fresh)
      feedPage.value += 1
      added += fresh.length
      const total = Number((res && res.total) || 0)
      feedHasMore.value = feedPage.value * FEED_SIZE < total
    }
  } catch (e) {
    // 加载失败就停住，别让上拉无限重试
    feedHasMore.value = false
  } finally {
    feedLoading.value = false
  }
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
  // 游客点加号 → 弹「需要登录」，取消就留在原地继续逛
  if (!requireLogin('加入购物车')) return
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

/**
 * 秒杀卡「＋」一步加购（不进详情页）。
 *
 * 几个必须守住的点：
 * - 加购用 f.productId（秒杀商品自己的商品 id），不是 f.id（那秒杀场次 id）。
 * - 后端 CartService.addItem 里已有 validateFlashQuota：按运行中的场次查每人已买/待付件数，
 *   超限直接抛 409 + 具体文案。所以前端不重复算，只做三件"点了注定失败"的提前拦截。
 * - 秒杀行在购物车和结算里不参与满减活动、不可用券（后端按 flashSaleId 拆分），
 *   这里不需要任何活动逻辑。
 * - 加完要静默重拉一次场次列表：一是让「已抢 X%」跟上，二是让 myRemainingQuota 更新
 *   （否则同一个商品连点第二次，前端还以为没买满）。
 */
async function quickAddFlash(f) {
  if (addingId.value) return
  // 游客点秒杀加号 → 先要求登录（秒杀名额是按账号算的，没有账号无法校验限购）
  if (!requireLogin('参加秒杀')) return
  if (f.state !== 'RUNNING') {
    uni.showToast({ title: (flashStateLabel(f.state) || '活动未开始') + '，暂时不能购买', icon: 'none' })
    return
  }
  if ((f.remainingQuota || 0) <= 0) {
    uni.showToast({ title: '这件已经被抢完了', icon: 'none' })
    return
  }
  // myRemainingQuota = 后端算好的「本账号还能买几件」（已扣掉已付 + 待付占用的名额）
  if (typeof f.myRemainingQuota === 'number' && f.myRemainingQuota <= 0) {
    uni.showToast({ title: `每人限 ${f.perUserLimit} 件，你已买满`, icon: 'none' })
    return
  }
  addingId.value = f.productId
  popId.value = f.productId
  setTimeout(() => { if (popId.value === f.productId) popId.value = null }, 460)
  try {
    await addToCart(f.productId, 1, '')
    const n = await refreshCartBadge()
    showCartTip(n)
    // 静默刷新场次：名额进度与 myRemainingQuota 都要更新
    try {
      const fs = await listFlashSales()
      flashSales.value = (fs || []).map((x) => ({ ...x, remaining: x.countdownSeconds || 0 }))
    } catch (e) { /* 刷新失败不影响已成功的加购 */ }
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

// 购物车角标统一走 utils/cartBadge（全局唯一实现）：
// 加购后要立刻生效，所以 force=true 跳过节流
const refreshCartBadge = () => syncCartBadge(true)

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

// 「查看更多」已按需求移除（2026-11-17）：秒杀区本身就是横滑全量列表，
// 且小程序没有秒杀独立列表页，点进去只是跳分类页 —— 用户觉得多余。

/** 圆形品类入口：进分类页并定位到该分类；「全部」不带参数，分类页落在第一个分类 */
function goCategory(id) {
  const q = id ? '?categoryId=' + id : ''
  uni.navigateTo({ url: '/pages/category/category' + q })
}

onMounted(() => {
  refreshCartBadge()
})

onShow(() => {
  // 游客也能逛首页：不再强制跳登录页。
  // 登录只在实际动作（加购/下单/看订单）时要求，见 utils/authGuard.requireLogin
  load()
  startTimer()
  // 未登录时 syncCartBadge 内部会直接清掉角标并返回 0，不发请求
  refreshCartBadge()
})

onHide(stopTimer)
onUnload(stopTimer)
onUnmounted(() => {
  stopTimer()
  if (cartTipTimer) { clearTimeout(cartTipTimer); cartTipTimer = null }
  if (sugTimer) { clearTimeout(sugTimer); sugTimer = null }
})

// 下拉刷新：复用各页已有的 load（它们本身就是「重置到第一页」的语义）。
// 必须写在 finally 里收掉刷新动画，否则请求失败时下拉圈会一直转。
onPullDownRefresh(async () => {
  try {
    await load()
  } finally {
    uni.stopPullDownRefresh()
  }
})

// 上拉到底：继续加载「猜你喜欢」（loadFeed 内部自己防重入、防无限重试）
onReachBottom(() => {
  loadFeed()
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
/* 2026-11-10 删除「最快30分钟送达·满99免运费」后，.deliver/.d-dot/.d-text 一并移除 */

/* ---------- 系统公告条（头部内，竖向轮播） ---------- */
.notice {
  margin-top: 14rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  gap: 10rpx;
  padding: 0 16rpx;
  background: rgba(255, 255, 255, 0.14);
  border-radius: 28rpx;
}
.n-ico {
  font-size: 24rpx;
  line-height: 1;
  flex-shrink: 0;
}
.n-swiper {
  flex: 1;
  height: 56rpx;
  min-width: 0;
}
.n-text {
  display: block;
  height: 56rpx;
  line-height: 56rpx;
  font-size: 22rpx;
  color: #E6F4EF;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.n-more {
  font-size: 20rpx;
  color: #8FD3BC;
  flex-shrink: 0;
}

/* ---------- 公告详情弹层 ---------- */
.notice-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 60rpx;
  z-index: 999;
}
.notice-sheet {
  width: 100%;
  max-height: 70vh;
  background: #ffffff;
  border-radius: 24rpx;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  /* 内部有 padding 子元素，统一按 border-box 算宽，避免 100% + padding 撑破右侧 */
  box-sizing: border-box;
}
.ns-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 26rpx 28rpx;
  border-bottom: 1rpx solid #F0EDE6;
  box-sizing: border-box;
}
.ns-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #1F2937;
}
.ns-close {
  font-size: 30rpx;
  color: #9CA3AF;
  padding: 0 8rpx;
  flex-shrink: 0;
}
.ns-body {
  flex: 1;
  /* ⚠️ 必须写 min-height: 0（2026-11-21 修「公告不能往下滑」）：
     flex 子项默认 min-height: auto，内容一多 scroll-view 会被内容撑高到等于内容高度，
     父容器虽然有 max-height:70vh，但它自己已经不再收缩 → 没有滚动空间 → 手指划不动。
     CouponSheet 的 .cs-list、AfterSaleSheet 的 .as-body 都写了这一行，只有这里漏了。 */
  min-height: 0;
  /* 给一个确定的上限：小程序端 flex:1 已经够用；H5 下 uni 内层滚动容器是 height:100%，
     只有父级有确定高度/上限时百分比才解析得出来，否则内层永远按内容撑开、滚不动。 */
  max-height: calc(70vh - 100rpx);
  padding: 8rpx 28rpx 24rpx;
  box-sizing: border-box;
  /* scroll-view 在 flex 里必须允许收缩，否则内容会把自身撑宽 */
  min-width: 0;
}
.ns-item {
  padding: 22rpx 0;
  border-bottom: 1rpx solid #F5F2EC;
  box-sizing: border-box;
  min-width: 0;
}
.ns-item:last-child {
  border-bottom: none;
}
.ns-item-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12rpx;
  min-width: 0;
}
.ns-item-title {
  flex: 1;
  /* ⭐ 关键：flex 子项默认 min-width:auto，长标题会拒绝收缩，
     把右侧日期一路挤出弹层（实测日期右边缘超出面板 16px，被 overflow 裁成「2026-09-」） */
  min-width: 0;
  font-size: 27rpx;
  font-weight: 600;
  color: #1F2937;
  word-break: break-word;
  overflow-wrap: anywhere;
}
.ns-item-time {
  font-size: 20rpx;
  color: #C4C0B6;
  flex-shrink: 0;
  /* 日期永远一行完整显示，别被压成「2026-09-」 */
  white-space: nowrap;
}
.ns-item-content {
  display: block;
  margin-top: 10rpx;
  font-size: 25rpx;
  color: #4B5563;
  line-height: 1.7;
  /* 公告常混英文/长串（"App"、期号），不加这两条会横向顶出面板 */
  word-break: break-word;
  overflow-wrap: anywhere;
}

/* ---------- 轮播 ---------- */
/* ---------- 首页加载失败提示 ---------- */
.load-err {
  margin: 20rpx 24rpx 0;
  display: flex;
  align-items: flex-start;
  gap: 14rpx;
  padding: 22rpx 20rpx;
  border-radius: 16rpx;
  background: #FFF6F4;
  border: 1rpx solid #F6D5CE;
}
.le-ico {
  font-size: 30rpx;
  line-height: 1.2;
}
.le-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}
.le-t {
  font-size: 26rpx;
  font-weight: 600;
  color: #A03A00;
}
/* 错误原文可能很长（如 request:fail url not in domain list），允许折行不截断 */
.le-sub {
  font-size: 21rpx;
  color: #B4746A;
  line-height: 1.5;
  word-break: break-all;
}
.le-btn {
  flex-shrink: 0;
  align-self: center;
  font-size: 24rpx;
  font-weight: 600;
  color: #ffffff;
  background: #C0392B;
  border-radius: 30rpx;
  padding: 8rpx 24rpx;
}

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

/* 2026-11-10 首页活动横幅已按用户要求移除（.promo/.p-more + 横幅专用的 .p-tag/.p-text 已删）。
   注意：下面第 1063 行附近的 .p-tag 是**商品卡卖点标签**的另一套样式，模板里 tagOf(p) 在用，别误删。
   活动仍在购物车凑单条与商品详情标签展示，逻辑见 utils/activity.js */

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
/* ---------- 限时秒杀（白卡 + 横滑，对齐参考样式） ---------- */
.flash {
  margin: 22rpx 24rpx 0;
  background: #ffffff;
  border-radius: 22rpx;
  padding: 22rpx 0 22rpx 22rpx;
  box-shadow: 0 2rpx 12rpx rgba(31, 41, 55, 0.04);
}
.flash-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-right: 22rpx;
  margin-bottom: 18rpx;
}
.flash-title-wrap {
  display: flex;
  align-items: center;
  gap: 10rpx;
  min-width: 0;
}
.flash-fire {
  font-size: 34rpx;
  line-height: 1;
}
.flash-t {
  font-size: 32rpx;
  font-weight: 700;
  color: #1F2937;
  letter-spacing: 1rpx;
}
.flash-sub {
  font-size: 21rpx;
  font-weight: 600;
  color: #C0392B;
  background: #FDECEA;
  border-radius: 8rpx;
  padding: 3rpx 10rpx;
}
.flash-sub2 {
  font-size: 22rpx;
  font-weight: 600;
  color: #C0392B;
}
.flash-scroll {
  white-space: nowrap;
}
.fcard {
  display: inline-block;
  width: 232rpx;
  background: #FAF8F3;
  border-radius: 16rpx;
  margin-right: 16rpx;
  overflow: hidden;
  vertical-align: top;
}
.f-cover-wrap {
  position: relative;
  width: 232rpx;
  height: 232rpx;
}
.f-cover {
  width: 232rpx;
  height: 232rpx;
  background: #F1EDE5;
}
.f-badge {
  position: absolute;
  left: 10rpx;
  top: 10rpx;
  font-size: 18rpx;
  color: #ffffff;
  background: rgba(192, 57, 43, 0.9);
  border-radius: 6rpx;
  padding: 2rpx 8rpx;
}
.f-badge-soon {
  background: rgba(107, 114, 128, 0.85);
}
.f-info {
  padding: 12rpx 14rpx 16rpx;
}
/* 每张秒杀卡自己的剩余时间（独立场次独立算） */
.f-left {
  display: block;
  margin-top: 2rpx;
  font-size: 20rpx;
  color: #9A8F7E;
}
/* 不到 1 小时标红：这是真正的"快结束了"，需要区分出来 */
.f-left-soon {
  color: #C0392B;
  font-weight: 600;
}
.f-name {
  font-size: 23rpx;
  color: #1F2937;
  line-height: 1.35;
  height: 62rpx;
  overflow: hidden;
  display: -webkit-box;  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  white-space: normal;
}
.f-foot {
  margin-top: 8rpx;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
}
.f-price-box {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.f-price {
  font-size: 32rpx;
  font-weight: 700;
  color: #C0392B;
  line-height: 1.1;
}
.f-yen {
  font-size: 22rpx;
}
.f-origin {
  font-size: 19rpx;
  color: #9CA3AF;
  text-decoration: line-through;
  margin-top: 2rpx;
}
.f-add {
  width: 46rpx;
  height: 46rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #0F6E56;
  flex-shrink: 0;
  /* 与商品卡的 .p-add 同一手感：按下回弹 */
  transition: transform 0.18s cubic-bezier(0.34, 1.56, 0.64, 1), background 0.2s ease;
}
.f-add-ico {
  color: #ffffff;
  font-size: 30rpx;
  font-weight: 600;
  line-height: 1;
}
/* 复用商品卡的 p-add-pop 关键帧 */
.f-add-pop {
  animation: p-add-pop 0.46s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.f-add-busy {
  background: #1D9E75;
}
.f-add-off {
  background: #E5E7EB;
}
.flash-tail {
  display: inline-block;
  width: 8rpx;
  height: 2rpx;
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
  /* 首页是 tabBar 页：--window-bottom 在 H5 下等于 tabBar 高度、小程序下为 0，
     比手写 118rpx 这种估算值可靠 */
  bottom: calc(var(--window-bottom, 0px) + 18rpx);
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
