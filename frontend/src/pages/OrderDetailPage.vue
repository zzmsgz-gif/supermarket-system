<template>
<section class="data-panel">
        <div class="panel-head">
          <button class="ghost" @click="closeOrderDetail">← 返回</button>
          <span v-if="orderDetail.data" class="order-no-display">订单号：{{ orderDetail.data.orderNo }}</span>
          <!-- 复购入口：生鲜的核心就是周期性买同样的东西 -->
          <button
            v-if="orderDetail.data && orderDetail.data.status !== 'PENDING_PAYMENT'"
            class="ghost"
            @click="reorder(orderDetail.data)"
          >再来一单</button>
        </div>
        <div v-if="orderDetail.loading" class="empty">加载中…</div>
        <div v-else-if="orderDetail.error" class="empty error">{{ orderDetail.error }}</div>
        <template v-else-if="orderDetail.data">
          <!-- ===== 待付款横幅 + 付款/取消入口 =====
               下单后一旦离开收银台，订单详情就是找回付款的地方
               （列表页的「去付款」按钮同样会跳回收银台），避免「看得见未付款、却动不了手」。
               倒计时用后端下发的绝对 payDeadline 算，不用客户端时间反推。 -->
          <div v-if="isPendingPay" class="pay-state" :class="payExpired ? 'closed' : 'pending'">
            <span class="pay-state-badge">{{ payExpired ? '已超时' : '待付款' }}</span>
            <div class="pay-state-main">
              <h3>{{ payExpired ? '支付超时，订单已失效' : '订单已提交，等待付款' }}</h3>
              <p v-if="!payExpired" class="pay-timer">
                还剩 <strong class="countdown" :class="{ urgent: payRemainingSec <= 60 }">{{ payMmss }}</strong>
                可支付，超时订单会自动关闭。
              </p>
              <p class="pay-state-desc">{{ payExpired
                ? '超过支付时限，订单将自动关闭，占用的库存与优惠券会释放。'
                : '下单时已为你锁定库存；现在付款才会真正扣款。' }}</p>
            </div>
          </div>
          <div v-if="isPendingPay" class="pay-actions">
            <button class="primary" :disabled="payExpired || paySubmitting" @click="payNow">
              <span v-if="paySubmitting" class="spinner"></span>
              <span>{{ paySubmitting ? '支付处理中…' : '立即支付 ' + money(orderDetail.data.payAmount) }}</span>
            </button>
            <button class="ghost" :disabled="paySubmitting" @click="cancelThisOrder">取消订单</button>
          </div>

          <!-- ===== 金额明细：每一项优惠都摊开，且「小计 + 运费 − 优惠合计 = 实付」恒成立 ===== -->
          <div class="amount-block" v-if="amount">
            <h3 class="amount-title">金额明细</h3>

            <div class="amount-row">
              <span>商品小计</span>
              <b>{{ money(amount.goods) }}</b>
            </div>
            <div class="amount-row">
              <span>运费</span>
              <b>{{ amount.freight > 0 ? '+ ' + money(amount.freight) : '免运费' }}</b>
            </div>

            <div class="amount-row save" v-if="amount.originalSave > 0">
              <span>划线优惠（已省）<small>原价与售价的差额，已含在商品小计中，不重复扣减</small></span>
              <b>- {{ money(amount.originalSave) }}</b>
            </div>

            <div class="amount-row minus">
              <span>优惠券<template v-if="amount.couponName">（{{ amount.couponName }}）</template></span>
              <b v-if="amount.coupon > 0">- {{ money(amount.coupon) }}</b>
              <b v-else class="none">未使用</b>
            </div>
            <div class="amount-row minus">
              <span>活动优惠<template v-if="amount.activityName">（{{ amount.activityName }}）</template></span>
              <b v-if="amount.activity > 0">- {{ money(amount.activity) }}</b>
              <b v-else class="none">未参与</b>
            </div>
            <div class="amount-row minus">
              <span>会员折扣（{{ tierNameFor(amount.memberLevel) }}）<small v-if="amount.member <= 0">{{ amount.memberLevel > 0 ? '本单未产生等级折扣' : '普通会员无折扣' }}</small></span>
              <b v-if="amount.member > 0">- {{ money(amount.member) }}</b>
              <b v-else class="none">无</b>
            </div>
            <div class="amount-row minus">
              <span>积分抵扣<template v-if="amount.pointsUsed > 0">（{{ amount.pointsUsed }} 分）</template></span>
              <b v-if="amount.points > 0">- {{ money(amount.points) }}</b>
              <b v-else class="none">未使用</b>
            </div>

            <div class="amount-row total-minus" v-if="amount.totalDiscount > 0">
              <span>优惠合计</span>
              <b>- {{ money(amount.totalDiscount) }}</b>
            </div>

            <div class="amount-row pay">
              <span>实付金额</span>
              <b>{{ money(amount.pay) }}</b>
            </div>
            <p class="amount-note">
              小计 {{ money(amount.goods) }}<template v-if="amount.freight > 0"> + 运费 {{ money(amount.freight) }}</template>
              <template v-if="amount.totalDiscount > 0"> − 优惠合计 {{ money(amount.totalDiscount) }}</template>
              = 实付 {{ money(amount.pay) }}
            </p>
            <p class="amount-note" v-if="amount.pointsEarned > 0">本单获得 {{ amount.pointsEarned }} 积分</p>
          </div>

          <!-- ===== 订单信息 ===== -->
          <div class="order-summary">
            <div class="order-row"><span>订单状态</span><b>{{ orderStatusLabel(orderDetail.data) }}</b></div>
            <div class="order-row"><span>支付状态</span><b>{{ formatPaymentStatus(orderDetail.data.paymentStatus) }}</b></div>
            <div class="order-row"><span>配送方式</span><b>{{ fulfillmentLabel(orderDetail.data)
              + (orderDetail.data.fulfillmentType === 'INSTANT'
                ? (orderDetail.data.deliverySlot ? ' · ' + orderDetail.data.deliverySlot : ' · 尽快送达')
                : '') }}</b></div>
            <template v-if="orderDetail.data.fulfillmentType === 'PICKUP'">
              <div class="order-row"><span>自提门店</span><b>{{ orderDetail.data.pickupStoreName }}</b></div>
              <div class="order-row"><span>自提联系人</span><b>{{ orderDetail.data.receiverName }} {{ orderDetail.data.receiverPhone }}</b></div>
              <div class="order-row"><span>自提码</span><b class="pickup-code">{{ orderDetail.data.pickupCode }}</b></div>
              <div class="order-row"><span>门店地址</span><b>{{ orderDetail.data.receiverAddress }}</b></div>
            </template>
            <template v-else>
              <div class="order-row"><span>收货人</span><b>{{ orderDetail.data.receiverName }} {{ orderDetail.data.receiverPhone }}</b></div>
              <div class="order-row"><span>收货地址</span><b>{{ orderDetail.data.receiverAddress }}</b></div>
            </template>
            <div class="order-row" v-if="orderDetail.data.shipCompany || orderDetail.data.shipNo"><span>物流信息</span><b>{{ orderDetail.data.shipCompany }} {{ orderDetail.data.shipNo }}</b></div>
            <div class="order-row" v-if="orderDetail.data.refundStatus && orderDetail.data.refundStatus !== 'NONE'">
              <span>退款状态</span>
              <b>{{ formatRefundStatus(orderDetail.data.refundStatus) }}<template v-if="orderDetail.data.refundReason"> · {{ orderDetail.data.refundReason }}</template><template v-if="orderDetail.data.refundRemark"> · 处理意见：{{ orderDetail.data.refundRemark }}</template><template v-if="amount"> · 退款额 {{ money(amount.pay) }}</template></b>
            </div>
            <div class="order-row" v-if="orderDetail.data.remark"><span>备注</span><b>{{ orderDetail.data.remark }}</b></div>
          </div>

          <h3 class="items-title">购买商品（{{ (orderDetail.data.items || []).length }} 件）</h3>
          <div class="order-items">
            <div v-for="it in (orderDetail.data.items || [])" :key="it.id" class="order-item">
              <img v-if="it.productCoverUrl" :src="it.productCoverUrl" class="order-item-img" alt="商品图片" @error="imgFallback($event, it.productName)" />
              <div class="order-item-info">
                <div class="order-item-name">{{ it.productName }}</div>
                <div v-if="it.skuSpec" class="order-item-spec">规格：{{ it.skuSpec }}</div>
                <div class="order-item-meta">
                  <span>
                    <s v-if="Number(it.originalPrice || 0) > Number(it.productPrice || 0)" class="original-price">{{ money(it.originalPrice) }}</s>
                    {{ money(it.productPrice) }}
                  </span>
                  <span class="order-item-qty">× {{ it.quantity }}</span>
                  <span class="subtotal">{{ money(it.subtotalAmount) }}</span>
                </div>
              </div>
            </div>
          </div>
        </template>
      </section>
</template>

<script>
import { computed, inject, ref, onMounted, onUnmounted } from 'vue';
import { orderOriginalSave } from '../utils/format';
export default {
  name: 'OrderDetailPage',
  setup() {
    const appCtx = inject('appCtx');
    const num = (value) => Number(value || 0);

    // ---- 待付款倒计时：用后端下发的**绝对** payDeadline 算，不用「客户端 now + 时长」反推 ----
    const paySubmitting = ref(false);
    const payRemainingSec = ref(0);
    const currentOrder = computed(() => appCtx.orderDetail.data || null);
    const isPendingPay = computed(
      () => !!currentOrder.value && currentOrder.value.status === 'PENDING_PAYMENT'
    );

    function recomputePayRemaining() {
      const o = currentOrder.value;
      if (!o || o.status !== 'PENDING_PAYMENT' || !o.payDeadline) {
        payRemainingSec.value = 0;
        return;
      }
      const ms = new Date(String(o.payDeadline).replace(' ', 'T')).getTime();
      payRemainingSec.value = Number.isNaN(ms) ? 0 : Math.max(0, Math.round((ms - Date.now()) / 1000));
    }
    const payExpired = computed(() => isPendingPay.value && payRemainingSec.value <= 0);
    const payMmss = computed(() => {
      const s = Math.max(0, payRemainingSec.value);
      return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
    });

    // 就地付款：成功后重拉本页数据，让用户原地看到「已支付」，而不是被甩到别的页面
    async function payNow() {
      const o = currentOrder.value;
      if (!o || paySubmitting.value || payExpired.value) return;
      paySubmitting.value = true;
      try {
        await appCtx.run(async () => {
          await appCtx.api.post(`/orders/${o.id}/pay`);
          await Promise.all([
            appCtx.loadOrders(), appCtx.loadWallet(), appCtx.loadMe(),
            appCtx.loadMemberProfile(), appCtx.loadFlashSales(),
          ]);
          appCtx.orderDetail.data = await appCtx.api.get(`/orders/${o.id}`);
        }, '支付成功');
      } finally {
        paySubmitting.value = false;
      }
    }

    // 取消：共用入口只重拉列表与本页无关的额度，详情页要自己再拉一次，
    // 否则取消后本页仍停留在「待付款」，看起来像没生效。
    async function cancelThisOrder() {
      const o = currentOrder.value;
      if (!o) return;
      await appCtx.cancelOrder(o.id);
      const fresh = await appCtx.api.get(`/orders/${o.id}`).catch(() => null);
      if (fresh) appCtx.orderDetail.data = fresh;
    }

    let payTick = null;
    onMounted(() => {
      recomputePayRemaining();
      payTick = setInterval(recomputePayRemaining, 1000);
    });
    onUnmounted(() => {
      if (payTick) clearInterval(payTick);
    });

    // 金额明细：把订单每一笔优惠都摊开，确保「商品小计 + 运费 − 优惠合计 = 实付」永远成立。
    // 会员等级折扣与积分抵扣此前没在详情页出现，是「数字加起来对不上」的根因。
    const amount = computed(() => {
      const d = appCtx.orderDetail.data;
      if (!d) return null;
      const goods = num(d.totalAmount);
      const freight = num(d.freightAmount);
      const coupon = num(d.discountAmount);
      const activity = num(d.activityDiscount);
      const member = num(d.memberDiscount);
      const points = num(d.pointsDiscount);
      const totalDiscount = coupon + activity + member + points;
      return {
        goods,
        freight,
        coupon,
        couponName: d.couponName || '',
        activity,
        activityName: d.activityName || '',
        member,
        memberLevel: num(d.memberLevel),
        points,
        pointsUsed: num(d.pointsUsed),
        pointsEarned: num(d.pointsEarned),
        // 划线优惠只是"已省"信息（已含在商品小计的单价里），不进优惠合计
        originalSave: orderOriginalSave(d),
        totalDiscount,
        pay: num(d.payAmount)
      };
    });

    return { ...appCtx, amount, isPendingPay, payExpired, payMmss, payRemainingSec, paySubmitting, payNow, cancelThisOrder };
  }
};
</script>
