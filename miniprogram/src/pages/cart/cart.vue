<template>
  <!-- paddingBottom 由 measureFooter() 实测底部固定栏高度后写入 ——
       写死一个值必然被「凑单条有没有、几张、金额明细几行」打破，商品一多就压住最后一件 -->
  <view class="cart" :style="footerH ? { paddingBottom: footerH + 'px' } : ''">
    <!-- 骨架屏：首次加载时先把版式占住，避免"白屏 → 内容突然弹出"的跳动 -->
    <CartSkeleton v-if="loading" :rows="3" />

    <block v-if="!loading">
      <!-- 工具条：件数汇总 + 一键清空 -->
      <view class="tools" v-if="!needLogin && items.length">
        <text class="tools-count">共 {{ items.length }} 种 · {{ totalQty }} 件</text>
        <view class="tools-clear" @click="clearAll">
          <text class="tc-ico">🗑</text>
          <text>清空</text>
        </view>
      </view>

      <view v-for="it in items" :key="it.id" class="row">
        <image class="cover" :src="fullUrl(it.productCoverUrl)" mode="aspectFill" />
        <view class="info">
          <view class="name">{{ it.productName }}</view>
          <view class="spec" v-if="it.skuSpec">{{ specText(it.skuSpec) }}</view>
          <view class="price" v-if="it.flashSaleId">
            <text class="now">¥{{ fmt(it.productPrice) }}</text>
            <text class="origin" v-if="showOrigin(it)">¥{{ fmt(it.productOriginalPrice) }}</text>
            <text class="flash">秒杀</text>
          </view>
          <view class="price" v-else>
            <text class="now">¥{{ fmt(it.productPrice) }}</text>
            <text class="origin" v-if="showOrigin(it)">¥{{ fmt(it.productOriginalPrice) }}</text>
          </view>
          <!-- 单行小计：单价 × 件数。只显示单价的话用户得自己心算，
               也看不出合计是怎么来的（尤其件数多时）。
               数量为 1 时不显示 —— 此时单价就是小计，重复没意义。 -->
          <view class="subtotal" v-if="it.quantity > 1">
            小计 <text class="sub-amt">¥{{ fmt(subtotalOf(it)) }}</text>
          </view>
          <view class="off" v-if="!it.onSale">已下架 / 无货</view>
        </view>
        <view class="qty">
          <text class="btn" @click="dec(it)">-</text>
          <text class="num">{{ it.quantity }}</text>
          <text class="btn" @click="inc(it)">+</text>
        </view>
      </view>
    </block>

    <!-- ===== 空购物车：不只是"一句话"，给引导 + 推荐，别让整屏空着 ===== -->
    <!-- 未登录：留在本页给登录引导，不强制跳转 -->
    <view class="login-tip" v-if="needLogin">
      <view class="lt-ico">🔒</view>
      <text class="lt-title">登录后查看购物车</text>
      <text class="lt-sub">购物车会同步到你的账号，换个设备也能接着买</text>
      <view class="lt-btn" @click="goLogin">去登录</view>
    </view>

    <!-- 空态也要排除 loading：否则骨架屏和"购物车还是空的"会同时出现（items 初值是 []） -->
    <view class="empty-wrap" v-if="!loading && !needLogin && items.length === 0">
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

    <!-- 空车推荐：大家都在买（点进去能直接加购，别让用户空手离开）
         同样要排除 loading，否则上一次会话缓存的推荐会和骨架屏叠在一起 -->
    <view class="reco" v-if="!loading && items.length === 0 && recommends.length">
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
              <text class="reco-price">¥{{ fmt(p.price) }}</text>
              <text class="reco-sales">{{ p.sales || 0 }} 人买过</text>
            </view>
          </view>
        </view>
      </view>
    </view>

    <view class="footer" v-if="items.length">
      <!-- 折叠开关：凑单条 + 金额明细默认展开，但商品多的时候它们会把列表挤得没地方，
           所以给一个收起入口，状态记在本地（下次进来保持）。
           收起后「合计 / 已省 / 去结算」仍在下方常驻，核心信息不丢。 -->
      <view class="fold-bar" @click="toggleFold">
        <view class="fold-left">
          <text class="fold-title">优惠明细</text>
          <text class="fold-sum" v-if="bestPromo > 0">已省 ¥{{ fmt(bestPromo) }}</text>
        </view>
        <text class="fold-btn">
          {{ footerExpanded ? '收起' : '展开' }}<text class="fold-ar">{{ footerExpanded ? '⌄' : '⌃' }}</text>
        </text>
      </view>

      <block v-if="footerExpanded">
        <!-- ① 满减凑单进度（活动门槛）：只按**普通商品**小计算，秒杀商品不计入 -->
        <view class="promo-bar" v-if="actProgress">
          <view class="pb-track">
            <view class="pb-fill" :style="{ width: actProgress.percent + '%' }"></view>
          </view>
          <text class="pb-text" v-if="!actProgress.reachedTop">
            再买 <text class="pb-strong">¥{{ fmt(actProgress.gap) }}</text> 可 {{ actProgress.benefit }}
          </text>
          <text class="pb-text" v-else>
            <!-- benefit 对打折活动返回的是「打 8 折」，前面直接接「已享」会读成"已享打8折" -->
            已享优惠：<text class="pb-strong">{{ actProgress.benefit }}</text>（满 ¥{{ fmt(actProgress.threshold) }}）
          </text>
          <text class="pb-note">满减仅按普通商品计算，秒杀商品不计入</text>
        </view>

        <!-- ② 满 ¥99 免运费进度：与①是两条**互不相关**的门槛，必须分开显示，
             否则用户分不清「满99减X」和「满99免邮」。运费按整车（含秒杀商品）算。 -->
        <view class="promo-bar freight" v-if="freightProgress">
          <view class="pb-track">
            <view class="pb-fill blue" :style="{ width: freightProgress.percent + '%' }"></view>
          </view>
          <text class="pb-text" v-if="!freightProgress.reachedTop">
            再买 <text class="pb-strong blue-t">¥{{ fmt(freightProgress.gap) }}</text>，即可免 ¥{{ fmt(freightProgress.freight) }} 运费
          </text>
          <text class="pb-text" v-else>
            已满足满 ¥{{ fmt(freightProgress.threshold) }} 免运费
          </text>
          <text class="pb-note">运费按整车（含秒杀商品）计算</text>
        </view>

        <!-- 金额明细：商品金额 − 活动优惠 = 合计。
             口径与后端 createOrder 保持一致：
             payAmount = 商品金额 − max(券, 活动) − 积分 + 运费（购物车不涉及券/积分/运费）。 -->
        <view class="amounts">
          <view class="amt-row">
            <text class="amt-lbl">商品金额</text>
            <text class="amt-val">¥{{ fmt(total) }}</text>
          </view>
          <!-- 优惠行只显示**实际生效**的那一档（券与活动互斥取大、绝不叠加）。
               两档都写会让用户以为叠加了，而且金额加起来对不上。 -->
          <view class="amt-row" v-if="bestPromo > 0">
            <text class="amt-lbl">{{ promoLabel }}</text>
            <text class="amt-val minus">-¥{{ fmt(bestPromo) }}</text>
          </view>
          <view class="amt-row payable">
            <text class="amt-lbl">合计</text>
            <text class="amt-val strong">¥{{ fmt(payable) }}</text>
          </view>
        </view>
      </block>

      <view class="footer-line">
        <view class="sum-wrap">
          <text class="sum">合计 ¥{{ fmt(payable) }}</text>
          <text class="saved" v-if="bestPromo > 0">已省 ¥{{ fmt(bestPromo) }}</text>
        </view>
        <button class="checkout" @click="goCheckout">去结算</button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, nextTick, getCurrentInstance, watch } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getCart, updateCartItem, removeCartItem, clearCart } from '@/api/cart'
// 骨架屏组件（也可走 easycom 自动引入，这里显式 import 更明确）
import CartSkeleton from '@/components/CartSkeleton/CartSkeleton.vue'
import { applyCartBadge } from '@/utils/cartBadge'
import { goLogin } from '@/utils/authGuard'
import { listActiveActivities } from '@/api/activity'
import { cartActivityProgress } from '@/utils/activity'
import { loadCouponState } from '@/utils/couponPromo'
import { listHotProducts } from '@/api/product'
import { isLoggedIn } from '@/store/auth'
import { fullUrl } from '@/config'
import { formatAmount } from '@/utils/format'

const items = ref([])

// 首屏加载态：初值取 true，避免刚进页面时先闪一下"空购物车"再弹内容。
// 骨架屏靠它控制，见模板顶部 <CartSkeleton v-if="loading" />。
const loading = ref(true)

// 件数汇总（工具条上显示"共 N 件"）
const totalQty = computed(() =>
  items.value.reduce((s, it) => s + (Number(it.quantity) || 0), 0)
)

// 后端 CartResponse.items 为扁平字段：productName / productPrice / productOriginalPrice /
// productCoverUrl / skuSpec / quantity / subtotalAmount / flashSaleId / onSale ...
// subtotalAmount 已是「按秒杀价拆分后的精确小计」，直接用它与即可
// 单行小计：优先后端 subtotalAmount（已按秒杀价拆分），兜底「单价 × 件数」
function subtotalOf(it) {
  const byApi = Number(it.subtotalAmount)
  if (byApi) return byApi
  return (Number(it.productPrice) || 0) * (Number(it.quantity) || 0)
}

// 商品金额：**优先用后端算好的 selectedAmount**，前端求和只作兜底。
// 后端 CartService 的 selectedAmount 是"选中行小计之和"的唯一权威口径
// （会员价 / 秒杀价拆分等单价逻辑全在服务端）；前端自己 reduce items，
// 一旦某行 subtotalAmount 缺失、或将来加了部分勾选，就会和后端给的
// activityDiscount 基数分叉 → 「商品金额 − 活动优惠」的合计与实付对不上。
const serverAmount = ref(null)
const total = computed(() => {
  const s = Number(serverAmount.value)
  if (Number.isFinite(s) && s > 0) return s
  return items.value.reduce(
    (sum, it) => sum + (Number(it.subtotalAmount) || Number(it.productPrice) * it.quantity || 0),
    0
  )
})

// 凑单进度只看「非秒杀」商品小计：秒杀商品不参与满减（与 web 的 cartNonFlashTotal 同口径）
// 同样优先用后端给的 nonFlashSelectedAmount
const serverNonFlashAmount = ref(null)
const nonFlashTotal = computed(() => {
  if (serverNonFlashAmount.value != null && Number.isFinite(Number(serverNonFlashAmount.value))) {
    return Number(serverNonFlashAmount.value)
  }
  return items.value
    .filter((it) => !it.flashSaleId)
    .reduce(
      (sum, it) => sum + (Number(it.subtotalAmount) || Number(it.productPrice) * it.quantity || 0),
      0
    )
})
const activities = ref([])
const actProgress = computed(() => cartActivityProgress(activities.value, nonFlashTotal.value))

/**
 * 活动优惠金额与名称 —— **直接采用后端算好的值**。
 * 为什么不在前端自己按活动规则算：门槛判定涉及「同一商品多规格合并、秒杀行剔除、
 * 多活动取最优」等规则，前端复刻一遍迟早和后端下单口径分叉（历史上就出过
 * 「购物车合计与活动优惠对不上」）。后端的 evaluateBestActivity 是唯一权威。
 */
const activityDiscount = ref(0)
const activityName = ref('')

// ==================== 优惠券（与结算页共用同一张，见 utils/couponPromo） ====================
// 用户要求购物车就显示「含券」的合计，而不是等结算页才算。
// 券选择状态落本地存储，购物车和结算页读同一份 —— 否则会出现
// 「购物车合计 147、结算页应付 157」这种自相矛盾。
const couponState = ref({ coupons: [], selectedId: null, selected: null, discount: 0 })

const couponDiscount = computed(() => Number(couponState.value.discount) || 0)

/** 券与活动互斥取大（后端 couponWins 同口径），绝不双减 */
const bestPromo = computed(() => Math.max(couponDiscount.value, Number(activityDiscount.value) || 0))

/** 生效的是券还是活动 —— 决定明细行显示哪个名字 */
const promoIsCoupon = computed(
  () => !!couponState.value.selected && couponDiscount.value > (Number(activityDiscount.value) || 0)
)

/** 明细里生效那一档的名称（券名 or 活动名） */
const promoLabel = computed(() => {
  if (promoIsCoupon.value) return '优惠券 · ' + (couponState.value.selected?.couponName || '优惠券')
  return activityName.value || '活动优惠'
})

/**
 * 合计 = 商品金额 − max(券, 活动)。
 * 购物车不涉及积分与运费（那两项在结算页才加），所以这里就是"全部优惠后的价格"。
 */
const payable = computed(() => {
  const goods = Number(total.value) || 0
  return Math.max(goods - bestPromo.value, 0)
})

/**
 * 满 ¥99 免运费进度。
 * ⚠️ 门槛/运费必须与后端 OrderService.EXPRESS_FREE_THRESHOLD / EXPRESS_FREIGHT 保持一致。
 * 与满减进度是两个独立门槛：满减只看**普通商品**，运费按**整车（含秒杀）**，所以这里用 total。
 */
const EXPRESS_FREE_THRESHOLD = 99
const EXPRESS_FREIGHT = 8

const freightProgress = computed(() => {
  const subtotal = Number(total.value) || 0
  if (subtotal <= 0) return null
  return {
    threshold: EXPRESS_FREE_THRESHOLD,
    freight: EXPRESS_FREIGHT,
    gap: Math.max(EXPRESS_FREE_THRESHOLD - subtotal, 0),
    percent: Math.min(100, Math.round((subtotal / EXPRESS_FREE_THRESHOLD) * 100)),
    reachedTop: subtotal >= EXPRESS_FREE_THRESHOLD
  }
})

/**
 * 金额统一保留两位小数。
 * 不格式化会出现 `¥45.19999999999999` 这种浮点误差（45.2 - 0.00000000000006 之类），
 * 用户截图里就是这个问题。web 端有 money() 干这事，小程序要自己来。
 */
const fmt = (n) => formatAmount(n)

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

// 未登录时不再一脚踢去登录页：留在购物车 tab 里给一个"去登录"的引导，
// 用户取消后还能继续逛（电商标准做法）
const needLogin = ref(false)

async function load() {
  loading.value = true
  try {
    if (!isLoggedIn()) {
      needLogin.value = true
      items.value = []
      applyCartBadge(0)
      return
    }
    needLogin.value = false
    const res = await getCart()
    items.value = (res && res.items) ? res.items : []
    // 商品金额 / 非秒杀金额：都用后端算好的值（前端求和仅兜底，见 total 的注释）
    serverAmount.value = res && res.selectedAmount != null ? Number(res.selectedAmount) : null
    serverNonFlashAmount.value =
      res && res.nonFlashSelectedAmount != null ? Number(res.nonFlashSelectedAmount) : null
    // 活动优惠由后端计算并随购物车返回，直接采用（详见 activityDiscount 的注释）
    activityDiscount.value = Number(res && res.activityDiscount) || 0
    activityName.value = (res && res.activityName) || ''
    // 券：按非秒杀金额拉可用券并定下当前用哪张（与结算页共用同一份选择）
    couponState.value = await loadCouponState(Number(nonFlashTotal.value) || 0)
    // 已经拿到购物车了，顺手把 tabBar 角标写上（不再多发一次 /cart）。
    // 改数量 / 删除 / 从别的页面切回来都会走 load，所以这一处就覆盖了全部路径。
    applyCartBadge(items.value.reduce((s, it) => s + (Number(it.quantity) || 0), 0))
  } catch (e) {
    // 必须 catch：失败也要把骨架屏收掉，否则页面会永远停在骨架状态
    items.value = []
    // 后端金额一并清掉，否则失败态/清空后还会显示上一次的金额
    serverAmount.value = null
    serverNonFlashAmount.value = null
    activityDiscount.value = 0
    activityName.value = ''
    couponState.value = { coupons: [], selectedId: null, selected: null, discount: 0 }
    uni.showToast({ title: (e && e.message) || '购物车加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

/**
 * 一键清空购物车。
 * 二次确认文案里带上件数，让用户清楚"要删多少"；确认按钮用红色，
 * 并且**不做**"5 秒内可撤销"这种假设 —— 后端是物理删除，如实告知"无法恢复"。
 */
async function clearAll() {
  if (!items.value.length) return
  const confirmed = await new Promise((resolve) => {
    uni.showModal({
      title: '清空购物车',
      content: `确定清空这 ${items.value.length} 件商品吗？清空后无法恢复。`,
      confirmText: '清空',
      confirmColor: '#C0392B',
      cancelText: '再想想',
      success: (r) => resolve(!!r.confirm),
      fail: () => resolve(false)
    })
  })
  if (!confirmed) return
  uni.showLoading({ title: '清空中', mask: true })
  try {
    await clearCart()
    items.value = []
    serverAmount.value = null
    serverNonFlashAmount.value = null
    activityDiscount.value = 0
    activityName.value = ''
    couponState.value = { coupons: [], selectedId: null, selected: null, discount: 0 }
    applyCartBadge(0)
    uni.hideLoading()
    uni.showToast({ title: '已清空', icon: 'success' })
  } catch (e) {
    uni.hideLoading()
    uni.showToast({ title: (e && e.message) || '清空失败，请重试', icon: 'none' })
  }
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

// ==================== 底部固定栏高度自适应 ====================
// 底部栏是 position:fixed，内容区必须让出等价高度，否则会压住最后一件商品。
// 但它的高度不是常量：凑单条可能有 0/1/2 条（满减 + 免运费），金额明细满减行有没有、
// 有没有活动优惠，都会改变高度 —— 写死一个值（原来是 210rpx）商品一多就盖住（用户反馈截图）。
// 所以改成实测：量 .footer 的 boundingClientRect().height，直接以 px 写回 paddingBottom。
const instance = getCurrentInstance()
const footerH = ref(0)

// ==================== 底部「优惠明细」折叠 ====================
// 凑单条 + 金额明细在商品多的时候很占地方，给一个收起入口。
// 状态存本地：用户一旦收起，下次进购物车保持收起（别每次都替他展开）；
// 收起后「合计 / 已省 / 去结算」仍然常驻在下方，核心信息不丢。
const FOLD_KEY = 'supermarket_cart_footer_folded'
const footerExpanded = ref(true)
let foldInited = false

function toggleFold() {
  footerExpanded.value = !footerExpanded.value
  try {
    uni.setStorageSync(FOLD_KEY, footerExpanded.value ? 0 : 1)
  } catch (e) {
    /* 存储失败无所谓，只是记不住偏好 */
  }
  // 底部栏高度变了，必须重新量，否则收起后仍留着展开时的空白（反之会压住商品）
  measureFooter()
}

function measureFooter() {
  nextTick(() => {
    uni
      .createSelectorQuery()
      .in(instance)
      .select('.footer')
      .boundingClientRect((rect) => {
        if (rect && rect.height) footerH.value = Math.ceil(rect.height)
      })
      .exec()
  })
}

// 下面任一项变化都会改变底部栏高度/是否存在，跟着重新量
watch(
  [
    () => items.value.length,
    () => needLogin.value,
    () => actProgress.value,
    () => freightProgress.value,
    () => activityDiscount.value,
    () => footerExpanded.value
  ],
  () => measureFooter()
)

onShow(async () => {
  // 只在首次进入时读折叠偏好（之后用户点了就实时生效，不要被 onShow 覆盖回去）
  if (!foldInited) {
    foldInited = true
    try {
      footerExpanded.value = uni.getStorageSync(FOLD_KEY) !== 1
    } catch (e) {
      footerExpanded.value = true
    }
  }
  await load()
  // 活动只在有商品时需要（凑单进度条），失败不影响购物车本身
  if (items.value.length && !activities.value.length) {
    try {
      activities.value = (await listActiveActivities()) || []
    } catch (e) {
      activities.value = []
    }
  }
  // 空车才去拉推荐（有商品时不浪费一次请求）
  if (items.value.length === 0) loadRecommends()
  measureFooter()
})
</script>

<style scoped>
.cart {
  /* 兜底值：真实高度由 measureFooter() 实测后以行内 style 覆盖；
     这里给一个偏大的保守值，万一测量失败也不会压住最后一件商品 */
  padding-bottom: 460rpx;
}
/* 工具条：件数 + 一键清空 */
.tools {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 0 28rpx 10rpx;
}
.tools-count {
  font-size: 23rpx;
  color: #8A8578;
}
.tools-clear {
  display: flex;
  align-items: center;
  gap: 6rpx;
  padding: 6rpx 10rpx;
  font-size: 23rpx;
  color: #C0392B;
}
.tc-ico {
  font-size: 22rpx;
  line-height: 1;
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
/* 单行小计（单价 × 件数） */
.subtotal {
  margin-top: 6rpx;
  font-size: 21rpx;
  color: #8A8578;
}
.sub-amt {
  font-weight: 600;
  color: #4B5563;
}
.price {
  margin-top: 12rpx;
  display: flex;
  align-items: baseline;
}
.now {
  color: #C0392B;
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
  background: #C0392B;
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
/* 未登录引导（与空态同版式，但主按钮是"去登录"） */
.login-tip {
  padding: 100rpx 48rpx 40rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.lt-ico {
  font-size: 96rpx;
  line-height: 1.2;
}
.lt-title {
  margin-top: 24rpx;
  font-size: 30rpx;
  font-weight: 600;
  color: #1F2937;
}
.lt-sub {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #8A8578;
  text-align: center;
  line-height: 1.6;
}
.lt-btn {
  margin-top: 36rpx;
  padding: 0 64rpx;
  height: 76rpx;
  line-height: 76rpx;
  border-radius: 38rpx;
  background: #0F6E56;
  color: #ffffff;
  font-size: 28rpx;
  font-weight: 600;
}

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
  /* H5 下 tabBar 是真实 DOM 元素、层级更高，bottom:0 会被它盖住（结算按钮只露一条边）；
     var(--window-bottom) 是 uni-app 提供的变量：H5 = tabBar 高度，小程序 = 0（原生 tabBar 不占页面）*/
  bottom: var(--window-bottom, 0);
  display: flex;
  flex-direction: column;
  background: #fff;
  border-top: 1rpx solid #eee;
  z-index: 10;
}
/* 折叠开关：做成窄条，收起后底部栏只剩「开关 + 合计行」两段 */
.fold-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14rpx 24rpx;
  border-top: 1rpx solid #F0EDE6;
  background: #FDFCF9;
}
.fold-left {
  display: flex;
  align-items: baseline;
  gap: 12rpx;
}
.fold-title {
  font-size: 23rpx;
  color: #6B7280;
}
.fold-sum {
  font-size: 21rpx;
  font-weight: 600;
  color: #C0392B;
}
.fold-btn {
  flex-shrink: 0;
  font-size: 22rpx;
  color: #0F6E56;
}
.fold-ar {
  margin-left: 4rpx;
  font-size: 20rpx;
}
/* 结算行：原来 .footer 自己是 flex row，现在让位给「凑单条 + 结算行」两段式 */
/* ---------- 金额明细（商品金额 − 活动优惠 = 合计） ---------- */
.amounts {
  padding: 14rpx 28rpx 2rpx;
  background: #fff;
  border-top: 1rpx solid #F5F2EC;
}
.amt-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 7rpx 0;
}
.amt-lbl {
  font-size: 23rpx;
  color: #8A8578;
}
.amt-val {
  font-size: 24rpx;
  color: #4B5563;
}
.amt-val.minus {
  color: #C0392B;
}
.amt-row.payable {
  margin-top: 2rpx;
  padding-top: 12rpx;
  border-top: 1rpx dashed #F0EDE6;
}
.amt-row.payable .amt-lbl {
  font-size: 25rpx;
  font-weight: 600;
  color: #1F2937;
}
.amt-val.strong {
  font-size: 30rpx;
  font-weight: 700;
  color: #C0392B;
}
.sum-wrap {
  display: flex;
  flex-direction: column;
}
.saved {
  margin-top: 2rpx;
  font-size: 20rpx;
  color: #C0392B;
}

.footer-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 24rpx;
}

/* ---------- 凑单进度条 ---------- */
.promo-bar {
  padding: 14rpx 24rpx 10rpx;
  background: #FFF7F0;
}
.pb-track {
  height: 8rpx;
  border-radius: 4rpx;
  background: #FFE0C7;
  overflow: hidden;
}
.pb-fill {
  height: 8rpx;
  border-radius: 4rpx;
  background: linear-gradient(90deg, #FFA94D 0%, #E8590C 100%);
  transition: width 0.25s ease;
}
.pb-text {
  display: block;
  margin-top: 10rpx;
  font-size: 22rpx;
  color: #A03A00;
}
.pb-strong {
  font-weight: 700;
  color: #E8590C;
}
/* 门槛说明（满减只算普通商品 / 运费含秒杀）—— 没有它用户不知道该凑哪一类商品 */
.pb-note {
  display: block;
  margin-top: 8rpx;
  font-size: 20rpx;
  color: #B4886B;
  line-height: 1.4;
}

/* 运费进度条：蓝色系，与满减（橙色）在视觉上分开，避免误看成同一个门槛 */
.promo-bar.freight {
  background: #F4F6FF;
  border-top: 1rpx solid #EDEFFB;
}
.promo-bar.freight .pb-track {
  background: #DDE3FF;
}
.promo-bar.freight .pb-text {
  color: #2C3E8F;
}
.promo-bar.freight .pb-note {
  color: #7E88B8;
}
.pb-fill.blue {
  background: linear-gradient(90deg, #7C8CF8 0%, #4F6EF7 100%);
}
.blue-t {
  color: #4F6EF7;
}
.sum {
  font-size: 32rpx;
  font-weight: bold;
  color: #C0392B;
}
.checkout {
  background: #0F6E56;
  color: #fff;
  border-radius: 48rpx;
  margin: 0;
}
</style>
