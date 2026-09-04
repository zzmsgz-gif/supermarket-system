<template>
<section class="data-panel">
        <div class="panel-head">
          <h2>购物车</h2>
          <button v-if="cart.items?.length" class="ghost danger" @click="clearCart">清空购物车</button>
        </div>
        <div v-if="!cart.items?.length" class="empty">购物车为空，先去挑选商品吧</div>
        <div v-for="item in cart.items" :key="item.id" class="list-row cart-item">
          <img v-if="item.productCoverUrl" :src="item.productCoverUrl" class="order-item-img" alt="商品图片" />
          <div>
            <strong>{{ item.productName }}</strong>
            <small v-if="item.skuSpec" class="sku-spec">已选：{{ item.skuSpec }}</small>
            <small>
              <span v-if="Number(item.productOriginalPrice) > Number(item.productPrice)" class="orig-strike">{{ money(item.productOriginalPrice) }}</span>
              {{ money(item.productPrice) }} × {{ item.quantity }}
              <template v-if="itemOriginalSave(item) > 0"> = <b>{{ money(item.productPrice * item.quantity) }}</b></template>
              <span v-if="itemOriginalSave(item) > 0" class="save-chip">省 {{ money(itemOriginalSave(item)) }}</span>
            </small>
          </div>
          <div class="row-actions">
            <div class="qty-wrap">
              <button class="stepper" type="button" @click="stepQty(item, -1)">−</button>
              <input class="qty" type="number" min="1" v-model.number="item.quantity" @input="onQtyInput(item)" @change="onQtyChange(item)" />
              <button class="stepper" type="button" @click="stepQty(item, 1)">+</button>
            </div>
            <button class="ghost danger" @click="removeCartItem(item.id)">删除</button>
          </div>
        </div>

        <div v-if="cart.items?.length" class="coupon-block">
          <h3>优惠券</h3>
          <div class="coupon-list">
            <button class="coupon-opt" :class="{ on: !selectedUserCouponId }" @click="chooseNoCoupon">不使用优惠券</button>
            <button
              v-for="coupon in myCoupons"
              :key="coupon.id"
              type="button"
              class="coupon-opt"
              :class="{ on: selectedUserCouponId === coupon.id, disabled: !couponEligible(coupon) }"
              :disabled="!couponEligible(coupon)"
              @click="selectCoupon(coupon)"
            >
              <span class="coupon-name">{{ coupon.couponName }}</span>
              <span class="coupon-sub">满 {{ money(coupon.thresholdAmount) }} 减 {{ money(coupon.discountAmount) }}</span>
              <span v-if="!couponEligible(coupon)" class="coupon-reason">
                {{ couponShortfall(coupon) > 0 ? '差 ' + money(couponShortfall(coupon)) + ' 可用' : (coupon.unusableReason || '暂不可用') }}
              </span>
            </button>
          </div>
        </div>

        <div v-if="cart.items?.length" class="submit-bar">
          <div class="submit-meta">
            <span class="total">合计 <b>{{ money(orderPayPreview) }}</b></span>
            <span v-if="cartOriginalSave > 0" class="total-save">划线价已为你省 {{ money(cartOriginalSave) }}</span>
            <span v-if="Number(cart.activityDiscount) > 0" class="total-save">活动已优惠 {{ money(cart.activityDiscount) }}</span>
          </div>
          <button class="primary" @click="goCheckout">提交订单</button>
        </div>
      </section>
</template>

<script>
import { inject } from 'vue';
export default {
  name: 'CartPage',
  setup() {
    const appCtx = inject('appCtx');
    return { ...appCtx };
  }
};
</script>
