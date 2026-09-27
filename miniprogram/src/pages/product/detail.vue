<template>
  <view class="detail" v-if="product">
    <image class="cover" :src="fullUrl(product.coverUrl)" mode="aspectFill" />
    <view class="name">{{ product.name }}</view>
    <view class="price">
      <text class="now">¥{{ displayPrice }}</text>
      <text class="origin" v-if="showOriginal">¥{{ displayOriginal }}</text>
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

    <view class="bar">
      <button class="add" @click="add">加入购物车</button>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getProduct } from '@/api/product'
import { addToCart } from '@/api/cart'
import { isLoggedIn } from '@/store/auth'
import { fullUrl } from '@/config'

const product = ref(null)
const quantity = ref(1)
// { 属性名: 选中的值 }，如 { 颜色: '红色', 尺码: 'XL' }
const selectedSpec = reactive({})

function safeParseSpec(str) {
  try {
    return JSON.parse(str || '{}') || {}
  } catch (e) {
    return {}
  }
}

// 各规格维度 → 可选值列表（来自每个 SKU 的 specJson）
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

// 当前选中 SKU（按维度精确匹配），用于取规格价 / 规格原价
const selectedSku = computed(() => {
  const skus = product.value?.skus || []
  if (!Object.keys(selectedSpec).length) return null
  return skus.find((sku) => {
    const spec = safeParseSpec(sku.specJson)
    return Object.keys(spec).every((k) => String(spec[k]) === String(selectedSpec[k]))
  }) || null
})

// 展示价：选中规格且单独定价用规格价，否则商品基准价
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

// 拼成后端 skuSpec 字符串：`属性名:值` 空格连接、键排序（与 PC 端同口径）
const skuSpecText = computed(() =>
  Object.keys(selectedSpec)
    .sort()
    .map((k) => `${k}:${selectedSpec[k]}`)
    .join(' ')
)

function pickSpec(dim, val) {
  selectedSpec[dim] = val
}

onLoad(async (opts) => {
  product.value = await getProduct(opts.id)
  const skus = product.value?.skus || []
  // 有规格时默认选中第一个规格（sort_no 最小者）
  if (skus.length) {
    const first = safeParseSpec(skus[0].specJson)
    for (const k in first) selectedSpec[k] = first[k]
  }
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
</script>

<style scoped>
.detail {
  padding: 0 0 120rpx;
}
.cover {
  width: 100%;
  height: 600rpx;
  background: #eee;
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
.bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 16rpx 24rpx;
  background: #fff;
}
.add {
  background: #07c160;
  color: #fff;
  border-radius: 48rpx;
}
</style>
