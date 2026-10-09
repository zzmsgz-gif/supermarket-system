<template>
<section class="data-panel checkout" v-if="hasItems">
        <div class="panel-head">
          <!-- ⚠️ 必须用 navigate('cart') 而不是 `view = 'cart'`：
               view 只是 App.vue 里驱动标题/面包屑的 ref，直接赋值只会把标题换成「购物车」，
               路由仍停在 /checkout，router-view 渲染的还是本页 —— 表现为「只换了标题、内容没变」。
               navigate 同时改路由，页面才真的回到购物车。 -->
          <button class="ghost" @click="backToCart">{{ quickBuy ? '取消立即购买' : '返回购物车' }}</button>
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
              <input
                v-model="addressForm.receiverPhone"
                type="tel" inputmode="numeric" maxlength="13"
                placeholder="11 位手机号"
                @input="onPhoneInput"
              />
              <div class="region-selects">
                <select v-model="addressForm.province" @change="onRegionProvince" class="region-sel">
                  <option value="">省份</option>
                  <option v-for="(cities, p) in regionData" :key="p" :value="p">{{ p }}</option>
                </select>
                <select v-model="addressForm.city" @change="onRegionCity" :disabled="!addressForm.province" class="region-sel">
                  <option value="">城市</option>
                  <option v-for="(districts, c) in citiesOf(addressForm.province)" :key="c" :value="c">{{ c }}</option>
                </select>
                <select v-model="addressForm.district" :disabled="!addressForm.city" class="region-sel">
                  <option value="">区 / 县</option>
                  <option v-for="d in districtsOf(addressForm.province, addressForm.city)" :key="d" :value="d">{{ d }}</option>
                </select>
              </div>
              <input v-model="addressForm.detailAddress" placeholder="详细地址" />
              <label class="check-line"><input type="checkbox" v-model="addressForm.isDefault" /> 设为默认地址</label>
              <button @click="saveAddressChecked">保存地址</button>
            </div>
          </details>
        </div>

        <div class="checkout-block">
          <h3>商品清单</h3>
          <div v-if="!cart.items?.length" class="empty">购物车为空</div>

                    <!-- 满减进度条：购物车有、结算页刻意不放（2026-10-09 用户反馈）——
               结算是付钱的地方，促销信息放在这里会分散注意力；
               商品图和可点回详情保留，凑单引导留在购物车做。 -->

          <!-- 立即购买时也要显示商品图和「会员价」标签 ——
               之前这里只有纯文字，和购物车的清单长得完全不一样，
               用户看着不像同一个商品（2026-10-09 反馈）。
               类名沿用购物车那张卡（order-item-img / cart-item-link），
               这样两处的图片尺寸、圆角、hover 表现完全一致。
               点击图片/标题可回到商品详情 —— 结算页里发现买错了还能改。 -->
          <div v-for="item in checkoutItems" :key="item.id" class="list-row cart-item">
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
              <span v-if="item.flashSaleId" class="flash-chip">限时秒杀 {{ money(item.flashPrice) }} ×{{ item.flashQty }}</span>
              <small v-if="item.skuSpec" class="sku-spec">已选：{{ item.skuSpec }}</small>
              <small v-if="Number(item.flashQty || 0) > 0 && Number(item.flashQty) < Number(item.quantity)">
                <span class="seg flash">限时秒杀 {{ money(item.flashPrice) }} ×{{ item.flashQty }}</span>
                <span class="seg">原价 {{ money(item.regularPrice) }} ×{{ item.quantity - item.flashQty }}</span>
              </small>
              <small v-else>
                <span v-if="Number(item.productOriginalPrice) > Number(item.productPrice)" class="orig-strike">{{ money(item.productOriginalPrice) }}</span>
                {{ money(item.productPrice) }} × {{ item.quantity }}
                <span v-if="itemOriginalSave(item) >= 1" class="save-chip">省 {{ money(itemOriginalSave(item)) }}</span>
                <span v-if="memberTagText(item)" class="member-price-tag">{{ memberTagText(item) }}</span>
              </small>
            </div>
            <strong class="line-total">{{ money(item.subtotalAmount) }}</strong>
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
          <div class="row"><span>商品金额</span><strong>{{ money(cartLocalTotal) }}</strong></div>
          <div v-if="selectedCoupon" class="row"><span>优惠券</span><strong class="minus">- {{ money(selectedCoupon.discountAmount) }}</strong></div>
          <div v-if="Number(cart.activityDiscount) > 0" class="row"><span>活动优惠（{{ cart.activityName }}）</span><strong class="minus">- {{ money(cart.activityDiscount) }}</strong></div>
          <!-- 积分抵扣金额与券/活动并排成同一组（原先它被夹在运费行前面，读起来像在算运费） -->
          <div v-if="usePoints && memberPreview.pointsUsed > 0" class="row">
            <span>积分抵扣（{{ memberPreview.pointsUsed }} 分）</span>
            <strong class="minus">- {{ money(memberPreview.pointsValue) }}</strong>
          </div>

<div class="checkout-points" v-if="session.user">
            <!-- 2026-10-07 布局优化：原先积分撑成 3 个独立行（勾选 / 输入 / 抵扣结果），
                 纵向拉得很高，且抵扣结果夹在运费行前面、顺序反直觉。
                 现在合成一张卡片：开关 + 用多少分 + 抵多少 + 全部抵扣，
                 抵扣金额本身移到下面的优惠区，与券/活动对齐成同一组。 -->
            <label class="points-toggle">
              <input type="checkbox" v-model="usePoints" :disabled="memberPreview.maxRedeemPoints <= 0 || memberPreview.userPoints <= 0" />
              <span>使用积分抵扣</span>
              <small v-if="memberPreview.maxRedeemPoints > 0 && memberPreview.userPoints > 0">可用 {{ memberPreview.userPoints }} 分</small>
              <small v-else>当前无可用积分</small>
            </label>
            <div class="points-panel" v-if="usePoints && memberPreview.maxRedeemPoints > 0">
              <div class="pp-field">
                <label for="checkoutPointsInput">使用分数</label>
                <input id="checkoutPointsInput" type="number" min="0" :max="memberPreview.maxRedeemPoints"
                       v-model.number="pointsToUse" @input="clampPoints" />
                <span class="pp-unit">分</span>
              </div>
              <div class="pp-result">
                <span>可抵扣</span>
                <strong>- {{ money(memberPreview.pointsValue || 0) }}</strong>
              </div>
              <button class="ghost sm" @click="useMaxPoints" type="button">全部抵扣</button>
            </div>
            <small v-if="!usePoints && memberPreview.maxRedeemPoints > 0" class="points-hint">
              最多可用 {{ memberPreview.maxRedeemPoints }} 分，本单可抵 {{ money(memberPreview.maxRedeemPoints / 100) }}
            </small>
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
            <button class="ghost danger sm" type="button" @click="backToCart">回购物车处理</button>
          </div>
          <ul class="bb-list">
            <li v-for="it in cartIssues" :key="it.id">
              <span class="bb-name">{{ it.productName }}</span>
              <span class="bb-reason">{{ cartItemIssue(it) }}</span>
            </li>
          </ul>
        </div>

        <div class="checkout-actions">
          <button class="ghost" @click="backToCart">{{ quickBuy ? '取消立即购买' : '返回' }}</button>
          <button v-if="blockedCount > 0" class="primary" disabled title="请先移除买不了的商品">有商品买不了，无法提交</button>
          <button v-else-if="outOfRange" class="primary" disabled title="该地址超出同城即时配送范围">超出配送范围，无法提交</button>
          <button v-else-if="balanceSufficient" class="primary" :class="{ loading: paying }" :disabled="paying" @click="createOrder">
            <span v-if="paying" class="spinner"></span>
            <span>{{ paying ? '提交中…' : '提交订单 ' + money(memberPreview.finalPay) }}</span>
          </button>
          <button v-else class="primary" disabled>余额不足，无法支付</button>
          <a v-if="!balanceSufficient && blockedCount === 0 && !outOfRange" class="link" @click="navigate('recharge')">去充值 ›</a>
        </div>
      </section>
      <div v-else class="checkout-empty">
        <div class="empty-illu">🛒</div>
        <h3>购物车是空的</h3>
        <p>先去挑几件商品，再来结算吧。</p>
        <div class="empty-actions">
          <button class="primary" @click="navigate('shop')">去逛逛</button>
        </div>
      </div>
</template>

<script>
import { inject, computed, ref, watch } from 'vue';
import { checkAddress, digitsOnly } from '../utils/validate.js';
import { cartItemIssue, cartIssueItems, memberTagText as fmtMemberTagText } from '../utils/format';
import { regionData, citiesOf, districtsOf } from '../utils/regions';
import { QUICKBUY_ITEM_ID } from '../composables/useGuestCart.js';
export default {
  name: 'CheckoutPage',
  setup() {
    const appCtx = inject('appCtx');
    // 回购物车：**必须走 navigate**（改路由），不能只改 App.vue 的 view ref ——
    // view 只驱动标题/面包屑，直接赋值的话标题变了但 router-view 还停在结算页，
    // 表现为「只把『确认订单』换成『购物车』，其他都没变」。
    // 「立即购买」态下顺手清掉虚拟项，否则回到购物车会看到一件本不属于它的商品。
    function backToCart() {
      if (appCtx.quickBuy.value) {
        appCtx.quickBuy.value = null;
        appCtx.cart.items = (appCtx.cart.items || []).filter((i) => i.id !== QUICKBUY_ITEM_ID);
      }
      appCtx.navigate('cart');
    }
    // 切换省：清空市/区；切换市：清空区，避免联动下级不匹配
    const onRegionProvince = () => { appCtx.addressForm.city = ''; appCtx.addressForm.district = ''; };
    const onRegionCity = () => { appCtx.addressForm.district = ''; };
    const clampPoints = () => {
      const max = appCtx.memberPreview.value.maxRedeemPoints;
      const v = Number(appCtx.pointsToUse.value) || 0;
      if (v < 0) appCtx.pointsToUse.value = 0;
      else if (v > max) appCtx.pointsToUse.value = max;
    };
    const useMaxPoints = () => { appCtx.pointsToUse.value = appCtx.memberPreview.value.maxRedeemPoints; };
    // 结算页同样要堵住失效行：从购物车过来时可能还是好的，商品在这期间被下架/售罄
    // （或用户在别处改了下架状态）。否则用户填完配送方式＋地址才被打回，白折腾一遍。
    // 只展示「已勾选」的项：正常流程全部勾选→显示全部；「立即购买」隔离后只显示当前件
    /**
     * 结算页补地址也要校验 —— 与地址页共用 checkAddress，
     * 否则会出现「地址页拦得住、结算页拦不住」的怪现象（2026-10-09）。
     */
    function saveAddressChecked() {
      const err = checkAddress(appCtx.addressForm?.value || appCtx.addressForm);
      if (err) { appCtx.fail?.(err); return; }
      return appCtx.saveAddress();
    }

    /**
     * 手机号输入实时过滤 —— 与地址页同一套（都要写回 DOM，否则过滤看不到效果）。
     */
    function onPhoneInput(event) {
      const cleaned = digitsOnly(event.target.value, 11);
      event.target.value = cleaned;
      const f = appCtx.addressForm;
      (f && typeof f === 'object' && 'value' in f ? f.value : f).receiverPhone = cleaned;
    }

    const checkoutItems = computed(() => (appCtx.cart.items || []).filter((i) => i.selected !== false));
    const cartIssues = computed(() => cartIssueItems(checkoutItems.value));
    const blockedCount = computed(() => cartIssues.value.filter((it) => it.selected).length);
    // 购物车（含「立即购买」虚拟项）没有任何可结算项时：整个结算页降级为「购物车是空的」空态，
    // 不再渲染配送方式/地址/提交按钮 —— 否则用户直接 URL 输入 /checkout 也能看到可下单界面，还能提交 ¥0 订单。
    const hasItems = computed(() => checkoutItems.value.length > 0);

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

    // 该结算项是否享受了会员折扣（非秒杀、且后端标记 memberDiscount>0）
    function isMemberItem(item) {
      if (!item || item.flashSaleId || Number(item.flashPrice || 0) > 0) return false;
      return Number(item.memberDiscount || 0) > 0;
    }

    /**
     * 会员优惠标签文案。与购物车共用 `utils/format.js` 的 memberTagText
     * （按来源区分「商品会员价」/「银卡 9.8折」，单件省不到 1 元不显示）。
     */
    function memberTagText(item) {
      return fmtMemberTagText(item, appCtx.session?.user?.memberLevel, appCtx.tierNameFor?.(appCtx.session?.user?.memberLevel));
    }

    return {
      saveAddressChecked, digitsOnly, onPhoneInput,
      ...appCtx, clampPoints, useMaxPoints, checkoutItems, cartIssues, blockedCount, cartItemIssue,
      backToCart,
      rangeCheck, outOfRange, hasItems, isMemberItem, memberTagText,
      regionData, citiesOf, districtsOf, onRegionProvince, onRegionCity,
    };
  }
};
</script>
