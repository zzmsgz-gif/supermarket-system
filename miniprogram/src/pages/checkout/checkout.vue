<template>
  <view class="checkout" v-if="items.length">
    <view class="block">
      <view class="block-title">商品清单</view>
      <view v-for="it in items" :key="it.id" class="row">
        <image class="cover" :src="fullUrl(it.productCoverUrl)" mode="aspectFill" />
        <view class="info">
          <view class="name">{{ it.productName }}</view>
          <view class="spec" v-if="it.skuSpec">{{ it.skuSpec }}</view>
          <view class="meta">¥{{ it.productPrice }} × {{ it.quantity }}</view>
        </view>
      </view>
    </view>

    <view class="block">
      <view class="block-title">取货方式</view>
      <view class="mode-row">
        <text class="mode active">门店自提</text>
        <text class="hint">（首期仅支持到店自提，送货上门后续开放）</text>
      </view>
      <view class="stores">
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
      </view>
      <view class="empty-store" v-if="stores.length === 0 && !storeLoading">暂无可选自提门店</view>
    </view>

    <view class="total">合计 ¥{{ total }}</view>
    <view class="channel">支付方式：钱包余额（首期未接入微信支付）</view>
    <button class="pay" :disabled="!canSubmit" @click="submit">提交订单并支付</button>
  </view>

  <view class="empty" v-else>购物车是空的</view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getCart } from '@/api/cart'
import { createOrder, payOrder } from '@/api/order'
import { listStores } from '@/api/store'
import { isLoggedIn } from '@/store/auth'
import { fullUrl } from '@/config'

const items = ref([])
const stores = ref([])
const selectedStoreId = ref(null)
const storeLoading = ref(false)

// 后端购物车行已含 subtotalAmount（精确小计），直接求和
const total = computed(() =>
  items.value.reduce((s, it) => s + (Number(it.subtotalAmount) || 0), 0)
)
const canSubmit = computed(() => items.value.length > 0 && !!selectedStoreId.value)

onLoad(async () => {
  if (!isLoggedIn()) {
    uni.navigateTo({ url: '/pages/login/login' })
    return
  }
  const res = await getCart()
  items.value = (res && res.items) ? res.items : []
  storeLoading.value = true
  try {
    stores.value = await listStores()
  } catch (e) {
    stores.value = []
  } finally {
    storeLoading.value = false
  }
  if (stores.value.length) selectedStoreId.value = stores.value[0].id
})

async function submit() {
  if (!canSubmit.value) {
    uni.showToast({ title: selectedStoreId.value ? '购物车为空' : '请选择自提门店', icon: 'none' })
    return
  }
  uni.showLoading({ title: '提交中' })
  try {
    // 后端 CreateOrderRequest：cartItemIds(List<Long>) + fulfillmentType + pickupStoreId
    // 首期只支持门店自提（PICKUP），无需收货地址
    const order = await createOrder({
      cartItemIds: items.value.map((it) => it.id),
      fulfillmentType: 'PICKUP',
      pickupStoreId: selectedStoreId.value
    })
    await payOrder(order.id)
    uni.hideLoading()
    uni.showToast({ title: '支付成功', icon: 'success' })
    uni.redirectTo({ url: '/pages/orders/orders' })
  } catch (e) {
    uni.hideLoading()
    uni.showToast({ title: (e && e.message) || '下单失败', icon: 'none' })
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
.mode-row {
  display: flex;
  align-items: center;
  padding: 8rpx 0 16rpx;
}
.mode {
  font-size: 28rpx;
  font-weight: bold;
  color: #07c160;
}
.mode.active {
  background: #07c160;
  color: #fff;
  padding: 8rpx 20rpx;
  border-radius: 32rpx;
}
.hint {
  font-size: 22rpx;
  color: #999;
  margin-left: 16rpx;
}
.stores {
  display: flex;
  flex-direction: column;
}
.store {
  border: 1rpx solid #eee;
  border-radius: 12rpx;
  padding: 16rpx;
  margin-bottom: 12rpx;
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
