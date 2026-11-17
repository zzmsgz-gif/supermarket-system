<template>
  <view class="checkout" v-if="items.length">
    <!-- 商品清单 -->
    <view class="block">
      <view class="block-title">商品清单</view>
      <view v-for="it in items" :key="it.key" class="row">
        <image class="cover" :src="fullUrl(it.productCoverUrl)" mode="aspectFill" />
        <view class="info">
          <view class="name">{{ it.productName }}</view>
          <view class="spec" v-if="it.skuSpec">{{ it.skuSpec }}</view>
          <view class="meta">¥{{ fmt(it.productPrice) }} × {{ it.quantity }}
            <text class="meta-sum" v-if="it.quantity > 1">= ¥{{ fmt(it.subtotalAmount) }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 履约方式 -->
    <view class="block">
      <view class="block-title">取货方式</view>
      <view class="modes">
        <text
          v-for="m in modeList"
          :key="m.type"
          class="mode"
          :class="{ active: fulfillmentType === m.type }"
          @click="pickFulfillment(m.type)"
        >{{ m.label }}</text>
      </view>

      <!-- 门店自提 -->
      <view v-if="fulfillmentType === 'PICKUP'">
        <view
          v-for="s in stores"
          :key="s.id"
          class="store"
          :class="{ active: s.id === selectedStoreId }"
          @click="selectedStoreId = s.id"
        >
          <view class="s-name">{{ s.name }}</view>
          <view class="s-addr">{{ s.address }}</view>
          <view class="s-hours" v-if="s.businessHours">营业：{{ s.businessHours }}</view>
          <view class="s-phone" v-if="s.phone">{{ s.phone }}</view>
        </view>
        <view class="empty-store" v-if="stores.length === 0">暂无可选自提门店</view>
      </view>

      <!-- 配送（需地址） -->
      <view v-if="fulfillmentType === 'INSTANT' || fulfillmentType === 'EXPRESS'">
        <view
          v-if="selectedAddress"
          class="addr"
          :class="{ active: true }"
          @click="goAddresses"
        >
          <view class="a-top">
            <text class="a-name">{{ selectedAddress.receiverName }}</text>
            <text class="a-phone">{{ selectedAddress.receiverPhone }}</text>
          </view>
          <view class="a-detail">{{ addrText(selectedAddress) }}</view>
          <text class="a-edit">切换/管理 ›</text>
        </view>
        <view v-else class="no-addr" @click="goAddresses">
          请选择收货地址（去添加 ›）
        </view>

        <!-- 同城配送时段 -->
        <view v-if="fulfillmentType === 'INSTANT' && slots.length" class="slots">
          <view class="slots-t">配送时段</view>
          <scroll-view scroll-x class="slots-scroll">
            <view
              v-for="s in slots"
              :key="s.value"
              class="slot"
              :class="{ active: selectedSlot === s.value }"
              @click="selectedSlot = s.value"
            >{{ s.label }}</view>
          </scroll-view>
        </view>

        <view class="freight-tip" v-if="fulfillmentType === 'EXPRESS'">
          快递配送：订单满 ¥99 免运费，未满收 ¥8 运费
        </view>
        <view class="freight-tip" v-if="fulfillmentType === 'INSTANT'">
          同城配送：免运费，2 小时内送达
        </view>
      </view>
    </view>

    <!-- 金额明细：口径与后端下单公式一致（商品金额 − max(券,活动) + 运费 = 应付）。
         优惠券行**只做入口**，不显示金额 —— 金额放在下面「优惠」行里，
         否则券生效时两行都写 -¥10，用户会以为扣了两次。 -->
    <view class="amounts">
      <view class="amt-row">
        <text class="amt-lbl">商品金额</text>
        <text class="amt-val">¥{{ fmt(goodsTotal) }}</text>
      </view>
      <view class="amt-row cp-row" @click="openCouponSheet">
        <text class="amt-lbl">优惠券</text>
        <text class="amt-val cp-val" :class="{ 'cp-on': !!selectedCoupon && promoIsCoupon }">{{ couponLabel }}</text>
        <text class="cp-arrow">›</text>
      </view>
      <!-- 券与活动互斥取大，只显示实际生效的那一档 -->
      <view class="amt-row" v-if="hasPromo">
        <text class="amt-lbl">{{ promoIsCoupon ? ('优惠券 · ' + selectedCoupon.couponName) : (activityName || '活动优惠') }}</text>
        <text class="amt-val minus">-¥{{ fmt(bestPromo) }}</text>
      </view>
      <view class="amt-row" v-if="freight > 0">
        <text class="amt-lbl">运费</text>
        <text class="amt-val">+¥{{ fmt(freight) }}</text>
      </view>
      <view class="amt-row payable">
        <text class="amt-lbl">应付</text>
        <text class="amt-val strong">¥{{ fmt(payable) }}</text>
      </view>
    </view>
    <view class="channel">支付方式：钱包余额</view>
    <button
      class="pay"
      :disabled="!canSubmit || submitting"
      @click="submit"
    >提交订单</button>
  </view>

  <view class="empty" v-else>没有可结算的商品</view>

  <!-- 优惠券选择弹层 -->
  <view class="cp-mask" v-if="couponSheetOpen" @click="couponSheetOpen = false">
    <view class="cp-sheet" @click.stop>
      <view class="cp-head">
        <text class="cp-title">选择优惠券</text>
        <text class="cp-close" @click="couponSheetOpen = false">✕</text>
      </view>
      <text class="cp-tip">券与活动优惠不可叠加，系统自动取减免更大的那一档</text>
      <scroll-view scroll-y class="cp-list">
        <view
          v-for="c in usableCoupons"
          :key="c.id"
          class="cp-item"
          :class="{ on: c.id === selectedUserCouponId }"
          @click="pickCoupon(c)"
        >
          <view class="cp-amt-box">
            <text class="cp-amt"><text class="cp-yen">¥</text>{{ fmt(c.discountAmount) }}</text>
            <text class="cp-th">满 {{ fmt(c.thresholdAmount) }} 可用</text>
          </view>
          <view class="cp-mid">
            <text class="cp-name">{{ c.couponName }}</text>
            <text class="cp-exp" v-if="c.endTime">有效期至 {{ String(c.endTime).slice(0, 10) }}</text>
          </view>
          <text class="cp-check">{{ c.id === selectedUserCouponId ? '●' : '○' }}</text>
        </view>
      </scroll-view>
      <view class="cp-foot">
        <view class="cp-none" @click="pickCoupon(null)">不使用优惠券</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { getCart } from '@/api/cart'
import { listUsableCoupons } from '@/api/coupon'
import { syncCartBadge } from '@/utils/cartBadge'
import { createOrder, payOrder, quickBuy } from '@/api/order'
import { listStores } from '@/api/store'
import { listAddresses } from '@/api/address'
import { listDeliverySlots } from '@/api/fulfillment'
import { getProduct } from '@/api/product'
import { isLoggedIn } from '@/store/auth'
import { formatAmount } from '@/utils/format'

// 金额统一两位小数，避免 151.79999999999998 这类浮点尾巴
const fmt = (n) => formatAmount(n)
import { fullUrl } from '@/config'

const modeList = [
  { type: 'PICKUP', label: '门店自提' },
  { type: 'INSTANT', label: '同城配送' },
  { type: 'EXPRESS', label: '快递配送' }
]

const mode = ref('cart')
const items = ref([])
const fulfillmentType = ref('PICKUP')
const stores = ref([])
const selectedStoreId = ref(null)
const addresses = ref([])
const selectedAddressId = ref(null)
const slots = ref([])
const selectedSlot = ref(null)
const submitting = ref(false)

// quick 模式参数
let quick = { productId: null, quantity: 1, skuSpec: '' }

const selectedAddress = computed(
  () => addresses.value.find((a) => a.id === selectedAddressId.value) || null
)

/**
 * 商品金额：优先用后端给的 subtotalAmount（已按秒杀价拆分），兜底单价×件数。
 * 原来这里一律按 productPrice×quantity 算，秒杀行会算成原价，与购物车/后端都差钱。
 */
const goodsTotal = computed(() =>
  items.value.reduce(
    (s, it) => s + (Number(it.subtotalAmount) || (Number(it.productPrice) || 0) * (Number(it.quantity) || 0)),
    0
  )
)

/**
 * 活动优惠：直接采用后端 GET /cart 返回的 activityDiscount（evaluateBestActivity 的权威结果）。
 * 前端不自己算 —— 门槛判定要处理「多规格合并、秒杀行剔除、多活动取最优」，复刻必分叉。
 */
const activityDiscount = ref(0)
const activityName = ref('')

// ==================== 优惠券 ====================
// 券只作用于**非秒杀**部分（后端同口径），所以取可用券时用非秒杀金额作基数。
// 口径与 PC 端 useCart/useCartTotals 完全一致：
//   bestPromo = max(券折扣, 活动折扣)   ← 互斥取大，绝不双减
//   明细里**只显示实际生效的那一行**，否则用户会以为两档都扣了
const usableCoupons = ref([])
const selectedUserCouponId = ref(null)
/** 用户显式点了「不使用优惠券」：之后不再自动补选最优券 */
const couponOptedOut = ref(false)
const couponSheetOpen = ref(false)

const nonFlashTotal = computed(() =>
  items.value
    .filter((it) => it.flashSaleId == null)
    .reduce(
      (s, it) => s + (Number(it.subtotalAmount) || (Number(it.productPrice) || 0) * (Number(it.quantity) || 0)),
      0
    )
)

const selectedCoupon = computed(
  () => usableCoupons.value.find((c) => c.id === selectedUserCouponId.value) || null
)

const couponDiscount = computed(() =>
  selectedCoupon.value ? Number(selectedCoupon.value.discountAmount || 0) : 0
)

/** 券与活动二选一，取减免更大的那一档（后端 couponWins 判断同口径） */
const bestPromo = computed(() => Math.max(couponDiscount.value, Number(activityDiscount.value) || 0))

/** 生效的到底是券还是活动 —— 决定明细行显示哪个名字 */
const promoIsCoupon = computed(
  () => !!selectedCoupon.value && couponDiscount.value > (Number(activityDiscount.value) || 0)
)

/**
 * 券入口右侧文案：**只表达状态，不显示金额**。
 * 金额统一由下面「优惠」行给出（券与活动互斥，只显示生效的那一档）。
 * 如果这里也写 -¥10，而实际生效的是活动 -¥50，用户会以为两档都扣了 → 金额加起来对不上。
 */
const couponLabel = computed(() => {
  if (selectedCoupon.value) {
    return promoIsCoupon.value ? '已用 1 张' : '活动更优惠'
  }
  const n = usableCoupons.value.length
  return n > 0 ? n + ' 张可用' : '暂无可用'
})

/** 拉一次可用券（基数变了要重拉，比如改了配送方式导致金额变化时不用，但切换 tab/重进要） */
async function loadUsableCoupons() {
  try {
    const res = await listUsableCoupons(Number(nonFlashTotal.value) || 0)
    usableCoupons.value = Array.isArray(res) ? res : []
    // 已选券若因金额变化不再可用 → 取消；未显式放弃时自动补选减免最大的
    if (selectedUserCouponId.value && !usableCoupons.value.some((c) => c.id === selectedUserCouponId.value)) {
      selectedUserCouponId.value = null
    }
    if (!couponOptedOut.value && !selectedUserCouponId.value) {
      const best = usableCoupons.value
        .slice()
        .sort((a, b) => Number(b.discountAmount || 0) - Number(a.discountAmount || 0))[0]
      if (best) selectedUserCouponId.value = best.id
    }
  } catch (e) {
    usableCoupons.value = []
  }
}

/** 门槛差额（弹层里给"还差 X 元可用"用；usableCoupons 只含已满足门槛的，这里兜底给全量提示用） */
function couponShortfall(c) {
  return Math.max(Number(c.thresholdAmount || 0) - Number(nonFlashTotal.value || 0), 0)
}

function openCouponSheet() {
  if (!usableCoupons.value.length) {
    uni.showToast({ title: '暂无可用优惠券', icon: 'none' })
    return
  }
  couponSheetOpen.value = true
}

function pickCoupon(c) {
  selectedUserCouponId.value = c ? c.id : null
  couponOptedOut.value = !c
  couponSheetOpen.value = false
}

// 运费口径必须与后端 OrderService.freightOf 一致：只有快递收，且按**商品金额**判门槛
const EXPRESS_FREE_THRESHOLD = 99
const EXPRESS_FREIGHT = 8
const freight = computed(() => {
  if (fulfillmentType.value !== 'EXPRESS') return 0
  return goodsTotal.value >= EXPRESS_FREE_THRESHOLD ? 0 : EXPRESS_FREIGHT
})

/**
 * 应付 = 商品金额 − max(券, 活动) + 运费。
 * 与后端 payAmount 公式同口径（`total − max(券,活动) − 积分 + 运费`，结算页不涉及积分）。
 */
const payable = computed(() => {
  const goods = Number(goodsTotal.value) || 0
  return Math.max(goods - bestPromo.value, 0) + Number(freight.value || 0)
})

const hasPromo = computed(() => bestPromo.value > 0)

const canSubmit = computed(() => {
  if (!items.value.length) return false
  if (fulfillmentType.value === 'PICKUP') return !!selectedStoreId.value
  return !!selectedAddressId.value
})

function addrText(a) {
  return (a.province || '') + (a.city || '') + (a.district || '') + (a.detailAddress || '')
}

function pickFulfillment(type) {
  fulfillmentType.value = type
  if (type === 'INSTANT' && !slots.value.length) loadSlots()
  if ((type === 'INSTANT' || type === 'EXPRESS') && !addresses.value.length) loadAddresses()
}

function goAddresses() {
  uni.navigateTo({ url: '/pages/addresses/addresses?from=checkout' })
}

function safeParseSpec(str) {
  try {
    return JSON.parse(str || '{}') || {}
  } catch (e) {
    return {}
  }
}

// quick 模式：按 skuSpec 文本解析出规格价
function resolvePrice(product, skuSpecText) {
  const skus = product.skus || []
  if (skuSpecText) {
    const want = {}
    skuSpecText.split(' ').forEach((pair) => {
      const i = pair.indexOf(':')
      if (i > 0) want[pair.slice(0, i)] = pair.slice(i + 1)
    })
    const sku = skus.find((s) => {
      const spec = safeParseSpec(s.specJson)
      return Object.keys(want).every((k) => String(spec[k]) === String(want[k]))
    })
    if (sku && sku.price != null) return sku.price
  }
  return product.price
}

async function loadCart() {
  const res = await getCart()
  const list = (res && res.items) ? res.items : []
  // 活动优惠随购物车一起返回（后端权威值），这里接住用于金额明细
  activityDiscount.value = Number(res && res.activityDiscount) || 0
  activityName.value = (res && res.activityName) || ''
  items.value = list.map((it) => ({
    key: 'c' + it.id,
    id: it.id,
    productCoverUrl: it.productCoverUrl,
    productName: it.productName,
    skuSpec: it.skuSpec,
    productPrice: it.productPrice,
    quantity: it.quantity,
    // flashSaleId 必须带上：券/活动的计算基数只算非秒杀部分（后端同口径）
    flashSaleId: it.flashSaleId == null ? null : it.flashSaleId,
    // 单行小计：后端已按秒杀价拆好，直接带上；没有就按单价×件数兜底
    subtotalAmount: Number(it.subtotalAmount) || (Number(it.productPrice) || 0) * (Number(it.quantity) || 0)
  }))
  // 券的门槛依赖金额，必须在 items 就位之后再拉
  await loadUsableCoupons()
}

async function loadQuick(opts) {
  const product = await getProduct(opts.productId)
  const price = resolvePrice(product, opts.skuSpec)
  const qty = Number(opts.quantity) || 1
  items.value = [{
    key: 'q' + opts.productId,
    productCoverUrl: product.coverUrl,
    productName: product.name,
    skuSpec: opts.skuSpec,
    productPrice: price,
    quantity: qty,
    subtotalAmount: (Number(price) || 0) * qty
  }]
}

async function loadStores() {
  try {
    stores.value = await listStores()
    if (stores.value.length && !selectedStoreId.value) {
      selectedStoreId.value = stores.value[0].id
    }
  } catch (e) {
    stores.value = []
  }
}

async function loadAddresses() {
  try {
    addresses.value = await listAddresses()
    const def = addresses.value.find((a) => a.isDefault) || addresses.value[0]
    selectedAddressId.value = def ? def.id : null
  } catch (e) {
    addresses.value = []
  }
}

/**
 * 按收货地址自动选履约方式（用户仍可手动改）：
 *  - 收货城市与任一门店同城 → 同城配送（INSTANT，2 小时达、免运费）
 *  - 跨城 → 快递配送（EXPRESS，满 99 免运费，否则 8 元）
 *  - 没有任何收货地址 → 保持「门店自提」（不需要地址，最合理）
 * 只在 onLoad 首次调用一次；之后用户手动切换不会被覆盖。
 */
function autoPickFulfillment() {
  const def = addresses.value.find((a) => a.isDefault) || addresses.value[0]
  if (!def || !def.city) return
  const city = String(def.city).trim()
  const sameCity = (stores.value || []).some(
    (s) => String(s.city || '').trim() === city
  )
  fulfillmentType.value = sameCity ? 'INSTANT' : 'EXPRESS'
}

async function loadSlots() {
  try {
    slots.value = await listDeliverySlots()
  } catch (e) {
    slots.value = []
  }
}

onLoad(async (opts) => {
  if (!isLoggedIn()) {
    uni.navigateTo({ url: '/pages/login/login' })
    return
  }
  if (opts.mode === 'quick') {
    mode.value = 'quick'
    quick = {
      productId: opts.productId,
      quantity: Number(opts.quantity) || 1,
      skuSpec: decodeURIComponent(opts.skuSpec || '')
    }
    await loadQuick(opts)
  } else {
    await loadCart()
  }
  await loadStores()
  // 拿到默认地址后按城市自动选「同城配送 / 快递配送」——用户不必自己想选哪个
  await loadAddresses()
  autoPickFulfillment()
})

onShow(() => {
  // 从地址管理页返回时刷新地址（保留已选）
  if (addresses.value.length || selectedAddressId.value !== null) loadAddresses()
})

async function submit() {
  if (!canSubmit.value || submitting.value) {
    uni.showToast({ title: selectedAddressId.value || selectedStoreId.value ? '请完善信息' : '请完善取货信息', icon: 'none' })
    return
  }
  submitting.value = true
  uni.showLoading({ title: '提交中' })
  try {
    const payload = {}
    if (mode.value === 'quick') {
      payload.productId = quick.productId
      payload.quantity = quick.quantity
      payload.skuSpec = quick.skuSpec || undefined
    } else {
      payload.cartItemIds = items.value.map((it) => it.id)
    }
    payload.fulfillmentType = fulfillmentType.value
    if (fulfillmentType.value === 'PICKUP') payload.pickupStoreId = selectedStoreId.value
    if (fulfillmentType.value === 'INSTANT' || fulfillmentType.value === 'EXPRESS') {
      payload.addressId = selectedAddressId.value
    }
    if (fulfillmentType.value === 'INSTANT' && selectedSlot.value) {
      payload.deliverySlot = selectedSlot.value
    }
    // 优惠券：只在「券减免确实大于活动减免」时才传。
    // 后端本身是 max(券,活动) 互斥取大、传了不生效也不会消耗券，
    // 但订单详情会记录 userCouponId —— 不生效就没必要留这个痕，避免用户看到"用了券却减免 0"。
    if (promoIsCoupon.value && selectedUserCouponId.value) {
      payload.userCouponId = selectedUserCouponId.value
    }

    const order = mode.value === 'quick' ? await quickBuy(payload) : await createOrder(payload)
    // 下单会把购物车里的这些行扣掉，角标要跟着减
    // （不刷新的话用户切回购物车 tab 还看到旧数字，以为没下单成功）
    syncCartBadge(true)
    uni.hideLoading()
    // 下单成功 → 进收银台（不再在这一步直接扣款）：
    // 用户能看到待付金额、15 分钟倒计时，余额不足还能先去充值，对齐 web 的 /pay/:id。
    uni.redirectTo({ url: '/pages/pay/pay?id=' + order.id })
  } catch (e) {
    uni.hideLoading()
    uni.showToast({ title: (e && e.message) || '下单失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.checkout {
  padding-bottom: 160rpx;
}
.block {
  background: #fff;
  margin: 16rpx;
  border-radius: 12rpx;
  padding: 16rpx 24rpx;
}
.block-title {
  font-size: 28rpx;
  font-weight: bold;
  padding: 8rpx 0 16rpx;
}
.row {
  display: flex;
  align-items: center;
  padding: 16rpx 0;
  border-bottom: 1rpx solid #f2f2f2;
}
.cover {
  width: 120rpx;
  height: 120rpx;
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
.meta {
  font-size: 24rpx;
  color: #C0392B;
  margin-top: 8rpx;
}
/* 「= ¥小计」比单价弱一档，避免一行里两处红色抢眼 */
.meta-sum {
  color: #8A8578;
}
.modes {
  display: flex;
}
.mode {
  font-size: 26rpx;
  padding: 10rpx 24rpx;
  margin-right: 16rpx;
  border-radius: 32rpx;
  background: #f5f5f5;
  color: #666;
}
.mode.active {
  background: #0F6E56;
  color: #fff;
}
.store {
  border: 1rpx solid #eee;
  border-radius: 12rpx;
  padding: 16rpx;
  margin-top: 12rpx;
}
.store.active {
  border-color: #0F6E56;
  background: #f0fff7;
}
.s-name {
  font-size: 28rpx;
  font-weight: bold;
}
.s-addr {
  font-size: 24rpx;
  color: #666;
  margin-top: 6rpx;
}
.s-hours,
.s-phone {
  font-size: 22rpx;
  color: #999;
  margin-top: 4rpx;
}
.empty-store {
  color: #999;
  font-size: 24rpx;
  padding: 16rpx 0;
}
.addr {
  border: 1rpx solid #0F6E56;
  background: #f0fff7;
  border-radius: 12rpx;
  padding: 16rpx;
  margin-top: 12rpx;
}
.a-top {
  display: flex;
  align-items: center;
}
.a-name {
  font-size: 28rpx;
  font-weight: bold;
}
.a-phone {
  font-size: 24rpx;
  color: #666;
  margin-left: 16rpx;
}
.a-detail {
  font-size: 24rpx;
  color: #444;
  margin-top: 6rpx;
}
.a-edit {
  font-size: 22rpx;
  color: #0F6E56;
  margin-top: 6rpx;
  display: block;
}
.no-addr {
  border: 1rpx dashed #ddd;
  border-radius: 12rpx;
  padding: 24rpx;
  margin-top: 12rpx;
  text-align: center;
  color: #999;
  font-size: 26rpx;
}
.slots {
  margin-top: 16rpx;
}
.slots-t {
  font-size: 24rpx;
  color: #666;
  margin-bottom: 8rpx;
}
.slots-scroll {
  white-space: nowrap;
}
.slot {
  display: inline-block;
  font-size: 24rpx;
  padding: 10rpx 20rpx;
  margin-right: 12rpx;
  border-radius: 24rpx;
  background: #f5f5f5;
  color: #666;
}
.slot.active {
  background: #0F6E56;
  color: #fff;
}
.freight-tip {
  font-size: 22rpx;
  color: #999;
  margin-top: 12rpx;
}
/* 金额明细 */
.amounts {
  margin-top: 20rpx;
  padding: 20rpx 24rpx 8rpx;
  background: #fff;
  border-radius: 16rpx;
}
.amt-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 8rpx 0;
}
.amt-lbl {
  font-size: 25rpx;
  color: #8A8578;
}
.amt-val {
  font-size: 26rpx;
  color: #4B5563;
}
.amt-val.minus {
  color: #C0392B;
}
.amt-row.payable {
  margin-top: 4rpx;
  padding-top: 14rpx;
  border-top: 1rpx dashed #F0EDE6;
}
.amt-row.payable .amt-lbl {
  font-size: 28rpx;
  font-weight: 600;
  color: #1F2937;
}
.amt-val.strong {
  font-size: 34rpx;
  font-weight: 700;
  color: #C0392B;
}
.channel {
  padding: 16rpx 24rpx 24rpx;
  color: #999;
  font-size: 24rpx;
}
.pay {
  margin: 24rpx;
  background: #0F6E56;
  color: #fff;
  border-radius: 48rpx;
}
.pay[disabled] {
  background: #ccc;
}
.empty {
  text-align: center;
  color: #999;
  padding: 120rpx 0;
}

/* ---------- 优惠券入口行 ---------- */
.cp-val {
  margin-left: auto;
  color: #8A8578;
}
.cp-val.cp-on {
  color: #C0392B;
  font-weight: 600;
}
.cp-arrow {
  margin-left: 10rpx;
  font-size: 26rpx;
  color: #C4C0B6;
}

/* ---------- 优惠券弹层 ---------- */
.cp-mask {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: flex-end;
  z-index: 900;
}
.cp-sheet {
  width: 100%;
  max-height: 76vh;
  background: #fff;
  border-radius: 28rpx 28rpx 0 0;
  display: flex;
  flex-direction: column;
  padding-bottom: env(safe-area-inset-bottom);
}
.cp-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 32rpx 8rpx;
}
.cp-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #1F2937;
}
.cp-close {
  font-size: 30rpx;
  color: #9CA3AF;
  padding: 0 8rpx;
}
.cp-tip {
  padding: 0 32rpx 16rpx;
  font-size: 21rpx;
  color: #A08A6A;
  line-height: 1.5;
}
.cp-list {
  flex: 1;
  min-height: 0;
  padding: 0 32rpx;
}
.cp-item {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 22rpx 20rpx;
  margin-bottom: 14rpx;
  border: 1rpx solid #EDE8DD;
  border-radius: 16rpx;
  background: #FDFCF9;
}
.cp-item.on {
  border-color: #0F6E56;
  background: #F1F8F5;
}
.cp-amt-box {
  width: 150rpx;
  flex-shrink: 0;
  text-align: center;
}
.cp-amt {
  display: block;
  font-size: 40rpx;
  font-weight: 700;
  color: #C0392B;
}
.cp-yen {
  font-size: 22rpx;
}
.cp-th {
  display: block;
  margin-top: 4rpx;
  font-size: 19rpx;
  color: #9A8F7E;
}
.cp-mid {
  flex: 1;
  min-width: 0;
}
.cp-name {
  display: block;
  font-size: 26rpx;
  font-weight: 600;
  color: #1F2937;
}
.cp-exp {
  display: block;
  margin-top: 6rpx;
  font-size: 20rpx;
  color: #A9A296;
}
.cp-check {
  flex-shrink: 0;
  font-size: 30rpx;
  color: #0F6E56;
}
.cp-foot {
  padding: 16rpx 32rpx 28rpx;
  border-top: 1rpx solid #F5F2EC;
}
.cp-none {
  text-align: center;
  padding: 14rpx 0;
  font-size: 27rpx;
  color: #6B7280;
}
</style>
