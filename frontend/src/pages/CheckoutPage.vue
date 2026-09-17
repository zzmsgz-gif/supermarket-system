<template>
<section class="data-panel checkout">
        <div class="panel-head">
          <button class="ghost" @click="view = 'cart'">返回购物车</button>
        </div>

        <div class="checkout-block">
          <h3>配送方式</h3>
          <div class="fulfill-tabs">
            <button type="button" :class="{ on: fulfillment.type === 'INSTANT' }" @click="selectFulfillment('INSTANT')">
              <b>同城即时配送</b><small>商家自有配送，可选 2 小时时段</small>
            </button>
            <button type="button" :class="{ on: isExpress }" @click="selectFulfillment('EXPRESS')">
              <b>快递配送</b>
              <small>{{ cartLocalTotal >= 99 ? '已满 ¥99，免运费' : '运费 ¥8（满 ¥99 免运费）' }}</small>
            </button>
            <button type="button" :class="{ on: isPickup }" @click="selectFulfillment('PICKUP')">
              <b>门店自提</b><small>到店取货，无需收货地址</small>
            </button>
          </div>

          <!-- 同城即时配送：期望配送时段 -->
          <div v-if="fulfillment.type === 'INSTANT'" class="slot-picker">
            <span class="field-label">期望配送时段（选填）</span>
            <select v-model="fulfillment.slot">
              <option value="">尽快送达</option>
              <option v-for="slot in deliverySlots" :key="slot.value" :value="slot.value">{{ slot.label }}</option>
            </select>
          </div>

          <!-- 快递配送：时效由第三方决定，没有自选时段 -->
          <div v-else-if="isExpress" class="slot-picker">
            <span class="field-label">快递配送</span>
            <p class="pickup-notice">由第三方快递承运，商家发货后可在订单详情查看物流单号；时效以快递公司为准。</p>
            <p v-if="memberPreview.freight > 0" class="pickup-notice">本单商品小计未满 ¥99，将收取运费 {{ money(memberPreview.freight) }}。</p>
          </div>

          <!-- 门店自提：选门店 -->
          <div v-else class="store-picker">
            <span class="field-label">自提门店</span>
            <div v-if="!stores.length" class="empty">暂无可自提门店，请改选即时配送或快递配送</div>
            <div v-else class="store-list">
              <button
                v-for="store in stores"
                :key="store.id"
                type="button"
                class="store-chip"
                :class="{ on: Number(store.id) === activeStoreId }"
                @click="selectStore(store.id)"
              >
                <b>{{ store.name }}</b>
                <small>{{ store.address }}</small>
                <small v-if="store.businessHours">营业 {{ store.businessHours }}<template v-if="store.phone"> · {{ store.phone }}</template></small>
              </button>
            </div>
            <p v-if="selectedStore && selectedStore.pickupNotice" class="pickup-notice">📌 {{ selectedStore.pickupNotice }}</p>
            <p class="pickup-contact" v-if="session.user">自提联系人：{{ session.user.nickname || session.user.username }} {{ session.user.phone || '' }}</p>
          </div>
        </div>

        <div class="checkout-block" v-if="!isPickup">
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
          <div class="row"><span>配送方式</span><strong>{{ isPickup
            ? '门店自提 · ' + (selectedStore ? selectedStore.name : '待选择门店')
            : isExpress
              ? '快递配送 · 第三方物流'
              : '同城即时配送 · ' + (fulfillment.slot || '尽快送达') }}</strong></div>
          <div class="row"><span>商品合计</span><strong>{{ money(cartLocalTotal) }}</strong></div>
          <div v-if="cartOriginalSave > 0" class="row"><span>划线优惠（已省）</span><strong class="minus">- {{ money(cartOriginalSave) }}</strong></div>
          <div v-if="selectedCoupon" class="row"><span>优惠券</span><strong class="minus">- {{ money(selectedCoupon.discountAmount) }}</strong></div>
          <div v-if="Number(cart.activityDiscount) > 0" class="row"><span>活动优惠（{{ cart.activityName }}）</span><strong class="minus">- {{ money(cart.activityDiscount) }}</strong></div>
          <div v-if="memberPreview.memberDiscount > 0" class="row"><span>{{ tierNameFor(session.user.memberLevel) }}折扣</span><strong class="minus">- {{ money(memberPreview.memberDiscount) }}</strong></div>

          <div class="checkout-points" v-if="session.user">
            <label class="points-toggle">
              <input type="checkbox" v-model="usePoints" :disabled="memberPreview.maxRedeemPoints <= 0 || memberPreview.userPoints <= 0" />
              <span>使用积分抵扣</span>
              <small v-if="memberPreview.maxRedeemPoints > 0 && memberPreview.userPoints > 0">（可用 {{ Math.min(memberPreview.userPoints, memberPreview.maxRedeemPoints) }} 分，最多抵 {{ money(memberPreview.maxRedeemValue) }}）</small>
              <small v-else>当前无可用积分</small>
            </label>
            <div class="points-input" v-if="usePoints && memberPreview.maxRedeemPoints > 0">
              <input type="number" min="0" :max="memberPreview.maxRedeemPoints" v-model.number="pointsToUse" @input="clampPoints" placeholder="输入抵扣积分" />
              <button class="ghost sm" @click="useMaxPoints" type="button">全部抵扣</button>
            </div>
            <div class="row points-row" v-if="usePoints && memberPreview.pointsUsed > 0">
              <span>积分抵扣（{{ memberPreview.pointsUsed }} 分）</span>
              <strong class="minus">- {{ money(memberPreview.pointsValue) }}</strong>
            </div>
          </div>

          <div v-if="memberPreview.freight > 0" class="row"><span>运费（快递配送）</span><strong>+ {{ money(memberPreview.freight) }}</strong></div>
          <div v-else-if="isExpress" class="row"><span>运费（快递配送）</span><strong class="text-ok">已满 ¥99 免运费</strong></div>

          <div class="row pay"><span>应付金额</span><strong>{{ money(memberPreview.finalPay) }}</strong></div>
          <div class="row balance" :class="{ insufficient: !balanceSufficient }">
            <span>账户余额 {{ money(wallet.balance) }}</span>
            <strong :class="balanceSufficient ? 'text-ok' : 'text-warn'">{{ balanceSufficient ? '余额充足' : '余额不足' }}</strong>
          </div>
        </div>

        <div v-if="outOfRange" class="blocker-bar" role="alert">
          <div class="bb-head">
            <b>该地址超出同城即时配送范围</b>
            <button class="ghost sm" type="button" @click="selectFulfillment('EXPRESS')">改用快递配送</button>
          </div>
          <ul class="bb-list">
            <li>
              <span class="bb-name">{{ selectedAddress ? ((selectedAddress.city || '') + ' ' + (selectedAddress.district || '')).trim() : '' }}</span>
              <span class="bb-reason">不在配送范围内</span>
            </li>
            <li v-if="rangeCheck && rangeCheck.coverage">当前覆盖：{{ rangeCheck.coverage }}</li>
          </ul>
        </div>

        <div v-if="cartIssues.length" class="blocker-bar" role="alert">
          <div class="bb-head">
            <b>{{ cartIssues.length }} 件商品现在买不了</b>
            <button class="ghost danger sm" type="button" @click="view = 'cart'">回购物车处理</button>
          </div>
          <ul class="bb-list">
            <li v-for="it in cartIssues" :key="it.id">
              <span class="bb-name">{{ it.productName }}</span>
              <span class="bb-reason">{{ cartItemIssue(it) }}</span>
            </li>
          </ul>
        </div>

        <div class="checkout-actions">
          <button class="ghost" @click="view = 'cart'">返回</button>
          <button v-if="blockedCount > 0" class="primary" disabled title="请先移除买不了的商品">有商品买不了，无法提交</button>
          <button v-else-if="outOfRange" class="primary" disabled title="该地址超出同城即时配送范围">超出配送范围，无法提交</button>
          <button v-else-if="balanceSufficient" class="primary" :class="{ loading: paying }" :disabled="paying" @click="createOrder">
            <span v-if="paying" class="spinner"></span>
            <span>{{ paying ? '支付处理中…' : '确认付款 ' + money(memberPreview.finalPay) }}</span>
          </button>
          <button v-else class="primary" disabled>余额不足，无法支付</button>
          <a v-if="!balanceSufficient && blockedCount === 0 && !outOfRange" class="link" @click="view = 'recharge'">去充值 ›</a>
        </div>
      </section>
</template>

<script>
import { inject, computed, ref, watch } from 'vue';
import { cartItemIssue, cartIssueItems } from '../utils/format';
export default {
  name: 'CheckoutPage',
  setup() {
    const appCtx = inject('appCtx');
    const clampPoints = () => {
      const max = appCtx.memberPreview.value.maxRedeemPoints;
      const v = Number(appCtx.pointsToUse.value) || 0;
      if (v < 0) appCtx.pointsToUse.value = 0;
      else if (v > max) appCtx.pointsToUse.value = max;
    };
    const useMaxPoints = () => { appCtx.pointsToUse.value = appCtx.memberPreview.value.maxRedeemPoints; };
    // 结算页同样要堵住失效行：从购物车过来时可能还是好的，商品在这期间被下架/售罄
    // （或用户在别处改了下架状态）。否则用户填完配送方式＋地址才被打回，白折腾一遍。
    const cartIssues = computed(() => cartIssueItems(appCtx.cart.items));
    const blockedCount = computed(() => cartIssues.value.filter((it) => it.selected).length);

    // 即时配送范围：选定地址后就地问后端能否送达。判定口径以后端为准（唯一真源），
    // 前端只负责**提前提示** —— 与失效行体检同一条原则，真正的强制仍在下单侧。
    const rangeCheck = ref(null);
    let rangeSeq = 0;
    async function loadRangeCheck() {
      const addr = appCtx.selectedAddress.value;
      if (!addr || appCtx.fulfillment.type !== 'INSTANT') { rangeCheck.value = null; return; }
      const seq = ++rangeSeq;
      try {
        const qs = new URLSearchParams({ city: addr.city || '', district: addr.district || '' });
        const data = await appCtx.api.get(`/delivery-range?${qs}`);
        // 快速切换地址时会有多个请求在飞，只认最后一次，避免旧响应覆盖新结果
        if (seq === rangeSeq) rangeCheck.value = data;
      } catch {
        // 查询失败就不提示，交给下单时后端兜底 —— 别因为一个提示接口抖动就挡住用户下单
        if (seq === rangeSeq) rangeCheck.value = null;
      }
    }
    const outOfRange = computed(
      () => appCtx.fulfillment.type === 'INSTANT'
        && !!rangeCheck.value && !rangeCheck.value.deliverable
    );
    watch(
      () => [appCtx.fulfillment.type, appCtx.selectedAddress.value && appCtx.selectedAddress.value.id],
      () => { loadRangeCheck(); },
      { immediate: true }
    );

    return {
      ...appCtx, clampPoints, useMaxPoints, cartIssues, blockedCount, cartItemIssue,
      rangeCheck, outOfRange,
    };
  }
};
</script>
