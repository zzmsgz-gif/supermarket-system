<template>
<section class="data-panel checkout">
        <div class="panel-head">
          <button class="ghost" @click="view = 'cart'">返回购物车</button>
        </div>

        <div class="checkout-block">
          <h3>收货地址</h3>
          <div v-if="selectedAddress" class="addr-card selected">
            <strong>{{ selectedAddress.receiverName }} {{ selectedAddress.receiverPhone }}</strong>
            <small>{{ selectedAddress.province }}{{ selectedAddress.city }}{{ selectedAddress.district }}{{ selectedAddress.detailAddress }}</small>
          </div>
          <div v-else class="empty">请先选择或新增收货地址</div>
          <div v-if="addresses.length" class="addr-list">
            <button
              v-for="a in addresses"
              :key="a.id"
              type="button"
              class="addr-chip"
              :class="{ on: a.id === selectedAddressId }"
              @click="selectedAddressId = a.id"
            >{{ a.receiverName }} · {{ a.receiverPhone }}</button>
          </div>
          <details class="addr-add">
            <summary>新增收货地址</summary>
            <div class="addr-form">
              <input v-model="addressForm.receiverName" placeholder="收货人" />
              <input v-model="addressForm.receiverPhone" placeholder="手机号" />
              <input v-model="addressForm.province" placeholder="省份" />
              <input v-model="addressForm.city" placeholder="城市" />
              <input v-model="addressForm.district" placeholder="区县" />
              <input v-model="addressForm.detailAddress" placeholder="详细地址" />
              <label class="check-line"><input type="checkbox" v-model="addressForm.isDefault" /> 设为默认地址</label>
              <button @click="saveAddress">保存地址</button>
            </div>
          </details>
        </div>

        <div class="checkout-block">
          <h3>商品清单</h3>
          <div v-if="!cart.items?.length" class="empty">购物车为空</div>
          <div v-for="item in cart.items" :key="item.id" class="list-row">
            <div>
              <strong>{{ item.productName }}</strong>
              <small v-if="item.skuSpec" class="sku-spec">已选：{{ item.skuSpec }}</small>
              <small>
                <span v-if="Number(item.productOriginalPrice) > Number(item.productPrice)" class="orig-strike">{{ money(item.productOriginalPrice) }}</span>
                {{ money(item.productPrice) }} × {{ item.quantity }}
                <span v-if="itemOriginalSave(item) > 0" class="save-chip">省 {{ money(itemOriginalSave(item)) }}</span>
              </small>
            </div>
            <strong class="line-total">{{ money(item.productPrice * item.quantity) }}</strong>
          </div>
        </div>

        <div class="checkout-block">
          <h3>优惠券</h3>
          <span v-if="selectedCoupon" class="coupon-picked">{{ selectedCoupon.couponName }}（减 {{ money(selectedCoupon.discountAmount) }}）</span>
          <span v-else class="coupon-picked muted">不使用优惠券</span>
        </div>

        <div class="checkout-summary">
          <div class="row"><span>商品合计</span><strong>{{ money(cartLocalTotal) }}</strong></div>
          <div v-if="cartOriginalSave > 0" class="row"><span>划线优惠（已省）</span><strong class="minus">- {{ money(cartOriginalSave) }}</strong></div>
          <div v-if="selectedCoupon" class="row"><span>优惠券</span><strong class="minus">- {{ money(selectedCoupon.discountAmount) }}</strong></div>
          <div v-if="Number(cart.activityDiscount) > 0" class="row"><span>活动优惠（{{ cart.activityName }}）</span><strong class="minus">- {{ money(cart.activityDiscount) }}</strong></div>
          <div class="row pay"><span>应付金额</span><strong>{{ money(orderPayPreview) }}</strong></div>
          <div class="row balance" :class="{ insufficient: !balanceSufficient }">
            <span>账户余额 {{ money(wallet.balance) }}</span>
            <strong :class="balanceSufficient ? 'text-ok' : 'text-warn'">{{ balanceSufficient ? '余额充足' : '余额不足' }}</strong>
          </div>
        </div>

        <div class="checkout-actions">
          <button class="ghost" @click="view = 'cart'">返回</button>
          <button v-if="balanceSufficient" class="primary" :class="{ loading: paying }" :disabled="paying" @click="createOrder">
            <span v-if="paying" class="spinner"></span>
            <span>{{ paying ? '支付处理中…' : '确认付款 ' + money(orderPayPreview) }}</span>
          </button>
          <button v-else class="primary" disabled>余额不足，无法支付</button>
          <a v-if="!balanceSufficient" class="link" @click="view = 'recharge'">去充值 ›</a>
        </div>
      </section>
</template>

<script>
import { inject } from 'vue';
export default {
  name: 'CheckoutPage',
  setup() {
    const appCtx = inject('appCtx');
    return { ...appCtx };
  }
};
</script>
