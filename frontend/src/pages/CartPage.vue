<template>
<section class="data-panel">
        <div class="panel-head">
          <h2>商品清单</h2>
          <button v-if="cart.items?.length" class="ghost danger" @click="clearCart">清空购物车</button>
        </div>

        <div v-if="cartActivityProgress && cart.items?.length" class="cart-progress">
          <div class="cp-top">
            <span class="cp-text">
              <template v-if="cartActivityProgress.reachedTop">已满足满 {{ money(cartActivityProgress.threshold) }} 门槛，可{{ cartActivityProgress.benefit }}</template>
              <template v-else>再买 <b class="cp-gap">{{ money(cartActivityProgress.gap) }}</b>，可{{ cartActivityProgress.benefit }}</template>
            </span>
            <button class="ghost" @click="navigate('shop')">去凑单</button>
          </div>
          <div class="cp-track"><i :style="{ width: cartActivityProgress.percent + '%' }"></i></div>
          <!-- 凑单推荐：既然已经算出「还差 ¥X」，就直接给出买得起的现货，
               别只丢一个「去凑单」把用户扔回首页乱逛。 -->
          <div v-if="!cartActivityProgress.reachedTop && upsell.length" class="cart-upsell">
            <span class="cu-label">买这些能凑上：</span>
            <button
              v-for="p in upsell"
              :key="p.id"
              type="button"
              class="cu-item"
              :title="'加入购物车：' + p.name"
              @click="addToCart(p)"
            >
              <img v-if="p.coverUrl" :src="p.coverUrl" class="cu-img" alt="" @error="imgFallback($event, p.name)" />
              <span class="cu-name">{{ p.name }}</span>
              <b class="cu-price">{{ money(p.price) }}</b>
              <span class="cu-add">+</span>
            </button>
          </div>
        </div>

        <div v-if="cartIssues.length" class="blocker-bar" role="alert">
          <div class="bb-head">
            <b>{{ cartIssues.length }} 件商品现在买不了</b>
            <button class="ghost danger sm" type="button" @click="removeAllInvalid">全部移除</button>
          </div>
          <ul class="bb-list">
            <li v-for="it in cartIssues" :key="it.id">
              <span class="bb-name">{{ it.productName }}</span>
              <span class="bb-reason">{{ cartItemIssue(it) }}</span>
            </li>
          </ul>
        </div>

        <empty-state
          v-if="!cart.items?.length"
          icon="cart"
          text="购物车还是空的，先去挑选几件好物吧"
          action-text="去逛逛"
          @action="navigate('shop')"
        />
        <div v-for="item in cart.items" :key="item.id" class="list-row cart-item">
          <img
            v-if="item.productCoverUrl"
            :src="item.productCoverUrl"
            class="order-item-img cart-item-link"
            alt="商品图片"
            @error="imgFallback($event, item.productName)"
            @click="openProductDetail({ id: item.productId })"
          />
          <div>
            <strong class="cart-item-link" @click="openProductDetail({ id: item.productId })">{{ item.productName }}</strong>
            <span v-if="item.flashSaleId" class="flash-chip">限时秒杀</span>
            <span v-if="cartQtyCapped(item)" class="flash-chip capped">已达限购</span>
            <span v-if="cartItemIssue(item)" class="flash-chip invalid">{{ cartItemIssue(item) }}</span>
            <small v-if="item.skuSpec" class="sku-spec">已选：{{ item.skuSpec }}</small>
            <small v-if="unpaidHolds[item.productId]" class="flash-hold">
              其中 <b>{{ unpaidHolds[item.productId].myUnpaidQuantity }} 件</b>被未付款订单
              {{ unpaidHolds[item.productId].myUnpaidOrderNo }} 占着秒杀名额
              <button type="button" class="link-btn" @click="navigate('orders')">去支付</button>
              <button type="button" class="link-btn" @click="cancelOrder(unpaidHolds[item.productId].myUnpaidOrderId)">取消订单释放</button>
            </small>
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
              <input class="qty" type="number" min="1" :max="cartQtyMax(item)" v-model.number="item.quantity" @input="onQtyInput(item)" @change="onQtyChange(item)" />
              <button
                class="stepper"
                type="button"
                :disabled="cartQtyCapped(item)"
                :title="cartQtyCapped(item) ? flashLimitMessage(item.productId, flashLimitOfProduct(item.productId)) : '增加数量'"
                @click="stepQty(item, 1)"
              >+</button>
            </div>
            <button class="ghost danger" @click="removeCartItem(item.id)">删除</button>
          </div>
        </div>

        <div v-if="cart.items?.length && session.user" class="coupon-block">
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

        <div v-if="cart.items?.length" class="cart-settle">
          <div class="cs-row">
            <span>商品金额<template v-if="cartSelectedQty">（共 {{ cartSelectedQty }} 件）</template></span>
            <b>{{ money(cartLocalTotal) }}</b>
          </div>
          <div v-if="Number(cart.activityDiscount) > 0" class="cs-row minus">
            <span>活动优惠<template v-if="cart.activityName">（{{ cart.activityName }}）</template></span>
            <b>-{{ money(cart.activityDiscount) }}</b>
          </div>
          <div v-if="selectedCoupon" class="cs-row minus">
            <span>优惠券（{{ selectedCoupon.couponName }}）</span>
            <b>-{{ money(selectedCoupon.discountAmount) }}</b>
          </div>
          <div class="cs-row total">
            <span>应付总额<small v-if="cartTotalSaved > 0" class="cs-saved">已省 {{ money(cartTotalSaved) }}</small></span>
            <b class="cs-pay">{{ money(orderPayPreview) }}</b>
          </div>
          <small v-if="cartOriginalSave > 0" class="cs-note">另有划线价直降 {{ money(cartOriginalSave) }}，已体现在商品现价中</small>
        </div>

        <div v-if="cart.items?.length" class="submit-bar">
          <div class="submit-meta">
            <span class="total">应付 <b>{{ money(orderPayPreview) }}</b></span>
            <span v-if="cartTotalSaved > 0" class="total-save">已省 {{ money(cartTotalSaved) }}</span>
            <!-- 有失效行就地把结算口堵住：不让用户填完一遍配送信息才被打回 -->
            <span v-if="blockedCount > 0" class="total-warn">有 {{ blockedCount }} 件已勾选商品买不了，请先移除</span>
          </div>
          <button
            class="primary"
            :disabled="blockedCount > 0"
            :title="blockedCount > 0 ? '请先移除已下架/库存不足的商品' : ''"
            @click="goCheckout"
          >提交订单</button>
        </div>
      </section>
</template>

<script>
import { inject, computed, ref, watch } from 'vue';
import { cartItemIssue, cartIssueItems } from '../utils/format';
export default {
  name: 'CartPage',
  setup() {
    const appCtx = inject('appCtx');
    // 按商品索引「有未付款订单占着秒杀名额」的场次。未付款订单同样占着名额（不占就会超卖），
    // 但用户能自己解套，所以要给出「去支付 / 取消订单」入口，而不是只丢一句"已达限购"让他猜。
    const unpaidHolds = computed(() => {
      const map = {};
      (appCtx.runningFlashSales.value || []).forEach((sale) => {
        if (Number(sale.myUnpaidQuantity || 0) > 0) map[sale.productId] = sale;
      });
      return map;
    });
    // 「已下架 / 已售罄 / 库存不够」的行。后端只在提交订单时才拦，前端必须提前暴露 ——
    // 用户明确要求过「应该提前禁用+提示，而不是等结算才提示」。
    const cartIssues = computed(() => cartIssueItems(appCtx.cart.items));
    // 只有「已勾选」的失效行才真的挡住结算（未勾选的不参与下单）
    const blockedCount = computed(
      () => cartIssues.value.filter((it) => it.selected).length
    );
    function removeAllInvalid() {
      // 逐条调用既有的删除动作即可——它会顺带刷新购物车与角标
      [...cartIssues.value].forEach((it) => appCtx.removeCartItem(it.id));
    }

    // 凑单推荐：只推「现货 + 没在车里 + 单价不超过差额」的畅销品，
    // 买一件就有机会刚好补上门槛。差额极小时一件都挑不出来，退化成最便宜的现货。
    const upsell = ref([]);
    const pickUpsell = (list, inCart) => (list || [])
      .filter((p) => Number(p.stock) > 0 && !inCart.has(p.id))
      .slice(0, 3);
    async function loadUpsell() {
      const prog = appCtx.cartActivityProgress.value;
      const items = appCtx.cart.items || [];
      if (!prog || prog.reachedTop || !items.length || Number(prog.gap || 0) <= 0) {
        upsell.value = [];
        return;
      }
      const inCart = new Set(items.map((i) => i.productId));
      try {
        const fit = await appCtx.api.get(
          `/products?page=1&size=8&sort=sales_desc&maxPrice=${Number(prog.gap)}`
        );
        upsell.value = pickUpsell(fit.items, inCart);
        if (!upsell.value.length) {
          const cheap = await appCtx.api.get('/products?page=1&size=6&sort=price_asc');
          upsell.value = pickUpsell(cheap.items, inCart);
        }
      } catch {
        // 推荐只是锦上添花，拉不到就当没有，不要惊动用户
        upsell.value = [];
      }
    }
    watch(
      () => [appCtx.cartActivityProgress.value?.gap, (appCtx.cart.items || []).length],
      () => { loadUpsell(); }
    );

    return {
      ...appCtx, unpaidHolds, cartIssues, blockedCount, cartItemIssue, removeAllInvalid, upsell,
    };
  }
};
</script>
