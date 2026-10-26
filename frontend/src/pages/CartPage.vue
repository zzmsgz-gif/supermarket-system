<template>
<section class="data-panel">
        <div class="panel-head">
          <h2>商品清单</h2>
          <button v-if="cart.items?.length" class="ghost danger" @click="clearCart">清空购物车</button>
        </div>

        <!-- 点1：满减进度与免邮进度分离，各自一张独立卡片；
             凑单推荐区也拆出来，与进度条视觉分层，不再混在同一个框里。 -->
        <div v-if="cartActivityProgress && cart.items?.length" class="cart-progress activity">
          <div class="cp-top">
            <span class="cp-text">
              <template v-if="cartActivityProgress.reachedTop">已满足满 {{ money(cartActivityProgress.threshold) }} 门槛，可{{ cartActivityProgress.benefit }}</template>
              <template v-else>再买 <b class="cp-gap">{{ money(cartActivityProgress.gap) }}</b>，可{{ cartActivityProgress.benefit }}</template>
            </span>
            <button class="ghost" @click="navigate('shop')">去凑单</button>
          </div>
          <div class="cp-track"><i :style="{ width: cartActivityProgress.percent + '%' }"></i></div>
          <small class="cp-note">满减仅按普通商品计算，秒杀商品不计入</small>
        </div>

        <div v-if="cartFreightProgress && cart.items?.length" class="cart-progress freight">
          <div class="cp-top">
            <span class="cp-text">
              <template v-if="cartFreightProgress.reachedTop">已满足满 ¥{{ cartFreightProgress.threshold }} 免运费</template>
              <template v-else>再买 <b class="cp-gap">{{ money(cartFreightProgress.gap) }}</b>，即可免 ¥{{ cartFreightProgress.freight }} 运费</template>
            </span>
            <button class="ghost" @click="navigate('shop')">去凑单</button>
          </div>
          <div class="cp-track"><i :style="{ width: cartFreightProgress.percent + '%' }"></i></div>
          <small class="cp-note">运费按整车（含秒杀商品）计算</small>
        </div>

        <!-- 凑单推荐：独立区块，与上面的进度条视觉拆开 -->
        <div v-if="!cartActivityProgress?.reachedTop && upsell.length && cart.items?.length" class="cart-upsell-block">
          <span class="cu-label">买这些能凑上：</span>
          <button
            v-for="p in upsell"
            :key="p.id"
            type="button"
            class="cu-item"
            :title="'加入购物车：' + p.name"
            @click="addUpsellToCart(p)"
          >
            <img v-if="p.coverUrl" :src="p.coverUrl" class="cu-img" alt="" @error="imgFallback($event, p.name)"  loading="lazy" decoding="async"/>
            <span class="cu-name">{{ p.name }}</span>
            <b class="cu-price">{{ money(p.price) }}</b>
            <span class="cu-add">+</span>
          </button>
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

        <skeleton-list
          v-if="cart.loading && !cart.items?.length"
          :count="3"
          :thumb="true"
          :lines="2"
          :tail="true"
        />
        <empty-state
          v-else-if="!cart.items?.length"
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
           loading="lazy" decoding="async"/>
          <div>
            <strong class="cart-item-link" @click="openProductDetail({ id: item.productId })">{{ item.productName }}</strong>
            <span v-if="cartItemIssue(item)" class="flash-chip invalid">{{ cartItemIssue(item) }}</span>
            <small v-if="item.skuSpec" class="sku-spec">已选：{{ item.skuSpec }}</small>
            <small v-if="unpaidHolds[item.productId]" class="flash-hold">
              其中 <b>{{ unpaidHolds[item.productId].myUnpaidQuantity }} 件</b>被未付款订单
              {{ unpaidHolds[item.productId].myUnpaidOrderNo }} 占着秒杀名额
              <button type="button" class="link-btn" @click="navigate('orders')">去支付</button>
              <button type="button" class="link-btn" @click="cancelOrder(unpaidHolds[item.productId].myUnpaidOrderId)">取消订单释放</button>
            </small>
            <small v-if="item.flashSaleId">
              <span class="seg flash">限时秒杀 {{ money(item.flashPrice) }} ×{{ item.flashQty }}</span>
              = <b>{{ money(item.subtotalAmount) }}</b>
              <span v-if="itemOriginalSave(item) >= 1" class="save-chip">省 {{ money(itemOriginalSave(item)) }}</span>
            </small>
            <small v-else>
              <span v-if="Number(item.productOriginalPrice) > Number(item.productPrice)" class="orig-strike">{{ money(item.productOriginalPrice) }}</span>
              {{ money(item.productPrice) }} × {{ item.quantity }}
              <template v-if="itemOriginalSave(item) > 0"> = <b>{{ money(item.productPrice * item.quantity) }}</b></template>
              <span v-if="itemOriginalSave(item) >= 1" class="save-chip">省 {{ money(itemOriginalSave(item)) }}</span>
              <span v-if="memberTagText(item)" class="member-price-tag">{{ memberTagText(item) }}</span>
            </small>
          </div>
          <div class="row-actions">
            <div class="qty-wrap">
              <button class="stepper" type="button" @click="stepQty(item, -1)">−</button>
              <input class="qty" type="number" min="1" :max="cartQtyMax(item)" v-model.number="item.quantity" @input="onQtyInput(item)" @change="onQtyChange(item)" />
              <button
                class="stepper"
                type="button"
                title="秒杀品受每人限购限制，名额用完后不可再加"
                @click="stepQty(item, 1)"
              >+</button>
            </div>
            <button class="ghost danger" @click="removeCartItem(item.id)">删除</button>
          </div>
        </div>

        <div v-if="cart.items?.length && session.user" class="coupon-block">
          <h3>优惠券</h3>
          <!-- 2026-10-12 用户要求：
               没有任何可用券时显示「无可用优惠券」；
               只有当确实存在可用券时，才出现「不使用优惠券」这一可选项。 -->
          <p v-if="selectableCoupons.length === 0" class="coupon-empty">无可用优惠券</p>
          <div v-else class="coupon-list">
            <button class="coupon-opt" :class="{ on: !selectedUserCouponId }" @click="chooseNoCoupon">不使用优惠券</button>
            <button
              v-for="coupon in selectableCoupons"
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
          <!-- 普通商品小计：这是参与「活动优惠 / 优惠券」的基数（满减门槛、券门槛都只看它）。
               用户能核到的来源只有明细行，所以先单列这一块，再单列秒杀，逻辑才透明白。 -->
          <div class="cs-row">
            <span>普通商品小计<template v-if="cartNonFlashQty">（{{ cartNonFlashQty }} 件，参与活动 / 可用券）</template></span>
            <b>{{ money(cartNonFlashTotal) }}</b>
          </div>
          <!-- 秒杀商品小计：走独立折扣通道，绝不参与活动优惠 / 优惠券（后端同口径）。
               以前不单列，用户看到「普通商品满 200 打 8 折」却对不上总额，以为算错。 -->
          <div v-if="cartFlashTotal > 0" class="cs-row">
            <span>秒杀商品小计<template v-if="cartFlashQty">（{{ cartFlashQty }} 件，不参与活动 / 不可用券）</template></span>
            <b>{{ money(cartFlashTotal) }}</b>
          </div>
          <div class="cs-row">
            <span>商品金额（合计）<template v-if="cartSelectedQty">（共 {{ cartSelectedQty }} 件）</template></span>
            <b>{{ money(cartLocalTotal) }}</b>
          </div>
          <!-- 优惠互斥：只展示真正生效的那一档（活动优惠 或 优惠券 二选一，绝不双减）。
               会员价不列：明细行已是会员成交价，再减一次会与商品金额对不上，且「省 X」拿售价算、
                界面上无售价可核对（2026-10-06 用户实测：「哪有这回事？省5块省在哪？」）。 -->
          <div v-if="appliedPromo" class="cs-row minus">
            <span>{{ appliedPromo.type === 'coupon' ? '优惠券' : ('活动优惠（' + (appliedPromo.name || '') + '）') }}</span>
            <b>-{{ money(appliedPromo.amount) }}</b>
          </div>
          <!-- 券已选但被活动压制：静音提示，不参与运算（#12：不再显示 −券额 误导用户以为减了）。 -->
          <small v-if="selectedCoupon && appliedPromo && appliedPromo.type !== 'coupon'" class="cs-note">
            已选「{{ selectedCoupon.couponName }}」，但本单活动优惠更划算，已自动优先活动、未消耗此券（活动与券不叠加）
          </small>
          <div class="cs-row total">
            <span>应付总额</span>
            <b class="cs-pay">{{ money(orderPayPreview) }}</b>
          </div>
          <small v-if="cartOriginalSave > 0" class="cs-note">明细行已标出各商品划线价直降 {{ money(cartOriginalSave) }}，均已含在商品单价里</small>
        </div>

        <div v-if="cart.items?.length" class="submit-bar">
          <div class="submit-meta">
            <span class="total">应付 <b>{{ money(orderPayPreview) }}</b></span>
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
import { useUpsell } from '../composables/useUpsell.js';
import { cartItemIssue, cartIssueItems, memberTagText as fmtMemberTagText } from '../utils/format';
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


    // 凑单推荐：逻辑在 composables/useUpsell.js，**购物车与结算页共用一份**
    // （2026-10-09 抽出；之前只写在这里，结算页要显示就得抄一遍，抄的必然漂移）
    // ⚠️ 2026-10-12 修复「点击凑单商品无反应」：回调原先写 (p) => addToCart(p)，
    // 而 addToCart 既没 import 也没从 appCtx 解构 —— 本页面里这个名字不存在，
    // 点下去就是 ReferenceError，按钮看起来"没反应"（只在控制台报错，用户端静默）。
    // 统一走 appCtx.addToCart（游客/登录两条路径它内部都处理）。
    const { upsell, addUpsellToCart } = useUpsell(appCtx, (p) => appCtx.addToCart(p));

    // 条目4：购物车/结算选券时，已使用（USED）的券不再出现。
    // 后端 /usable 已过滤 USED，但购物车券选择区用的是「我的全部券」列表，
    // 这里再在前端挡一道，保证已核销的券不会出现在可选列表里。
    const selectableCoupons = computed(() =>
      (appCtx.myCoupons?.items || []).filter((c) => c.status !== 'USED')
    );

    // 该购物项是否享受了会员折扣（非秒杀、且后端标记 memberDiscount>0）
    function isMemberItem(item) {
      if (!item || item.flashSaleId || Number(item.flashPrice || 0) > 0) return false;
      return Number(item.memberDiscount || 0) > 0;
    }

    /**
     * 会员优惠标签文案。真正的判定与文案规则在 `utils/format.js` 的 `memberTagText`
     * （按 memberSource 区分「商品会员价」与「等级折扣」，单件省不到 1 元不显示），
     * 购物车与结算页共用同一份，避免两页各写一套又漂移。
     */
    function memberTagText(item) {
      return fmtMemberTagText(item, appCtx.session?.user?.memberLevel, appCtx.tierNameFor?.(appCtx.session?.user?.memberLevel));
    }

    return {
      upsell, addUpsellToCart, selectableCoupons,
      ...appCtx, unpaidHolds, cartIssues, blockedCount, cartItemIssue, removeAllInvalid, upsell, isMemberItem, memberTagText,
    };
  }
};
</script>
