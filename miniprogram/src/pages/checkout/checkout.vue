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
          <view class="meta">¥{{ it.productPrice }} × {{ it.quantity }}</view>
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
          快递配送：订单满 ¥99 免运费，未满收 ¥8 运费（下单后计算）
        </view>
        <view class="freight-tip" v-if="fulfillmentType === 'INSTANT'">
          同城配送：免运费，2 小时内送达
        </view>
      </view>
    </view>

    <view class="total">合计 ¥{{ total }}</view>
    <view class="channel">支付方式：钱包余额（首期未接入微信支付）</view>
    <button
      class="pay"
      :disabled="!canSubmit || submitting"
      @click="submit"
    >提交订单并支付</button>
  </view>

  <view class="empty" v-else>没有可结算的商品</view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { getCart } from '@/api/cart'
import { createOrder, payOrder, quickBuy } from '@/api/order'
import { listStores } from '@/api/store'
import { listAddresses } from '@/api/address'
import { listDeliverySlots } from '@/api/fulfillment'
import { getProduct } from '@/api/product'
import { isLoggedIn } from '@/store/auth'
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

const total = computed(() =>
  items.value.reduce(
    (s, it) => s + (Number(it.productPrice) || 0) * (Number(it.quantity) || 0),
    0
  )
)

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
  items.value = list.map((it) => ({
    key: 'c' + it.id,
    id: it.id,
    productCoverUrl: it.productCoverUrl,
    productName: it.productName,
    skuSpec: it.skuSpec,
    productPrice: it.productPrice,
    quantity: it.quantity
  }))
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
    quantity: qty
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

    const order = mode.value === 'quick' ? await quickBuy(payload) : await createOrder(payload)
    await payOrder(order.id)
    uni.hideLoading()
    uni.showToast({ title: '支付成功', icon: 'success' })
    uni.redirectTo({ url: '/pages/orders/orders' })
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
  color: #e4393c;
  margin-top: 8rpx;
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
  background: #07c160;
  color: #fff;
}
.store {
  border: 1rpx solid #eee;
  border-radius: 12rpx;
  padding: 16rpx;
  margin-top: 12rpx;
}
.store.active {
  border-color: #07c160;
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
  border: 1rpx solid #07c160;
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
  color: #07c160;
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
  background: #07c160;
  color: #fff;
}
.freight-tip {
  font-size: 22rpx;
  color: #999;
  margin-top: 12rpx;
}
.total {
  padding: 24rpx;
  font-size: 32rpx;
  font-weight: bold;
  color: #e4393c;
}
.channel {
  padding: 0 24rpx 24rpx;
  color: #999;
  font-size: 24rpx;
}
.pay {
  margin: 24rpx;
  background: #07c160;
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
</style>
