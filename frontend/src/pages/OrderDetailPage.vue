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
              <!-- 同理：还有时间付款时不解释内部机制，超时后才说明为什么付不了 -->
              <p v-if="payExpired" class="pay-state-desc">
                超过支付时限，订单将自动关闭，占用的库存与优惠券会释放。
              </p>
            </div>
          </div>
          <div v-if="isPendingPay" class="pay-actions">
            <button class="primary" :disabled="payExpired || paySubmitting" @click="payNow">
              <span v-if="paySubmitting" class="spinner"></span>
              <span>{{ paySubmitting ? '支付处理中…' : '立即支付 ' + money(orderDetail.data.payAmount) }}</span>
            </button>
            <button class="ghost" :disabled="paySubmitting" @click="cancelThisOrder">取消订单</button>
          </div>

<!-- 履约操作区已移到页面最底部（order-summary 之后）——
     2026-10-07 用户要求「确认收货按钮改到右下角」。原先夹在顶部状态旁很突兀，
     且和「申请售后」并排容易误点（收货是常规履约，售后是异常处理）。 -->
          <!-- 售后申请表单：与列表页同一套 submitRefund/openRefundForm -->
          <form v-if="localRefund.open" class="refund-form-inline" @submit.prevent="sendRefund">
            <h4>申请售后</h4>
            <label>
              售后原因
              <select v-model="localRefund.reason">
                <option value="">请选择原因</option>
                <option v-for="r in REFUND_REASONS" :key="r" :value="r">{{ r }}</option>
              </select>
            </label>
            <p class="refund-hint">提交后由商家审核；审核通过将原路退回余额，库存同步回补。</p>
            <div class="refund-actions">
              <button type="submit" class="primary" :disabled="acting">{{ acting ? '提交中…' : '提交申请' }}</button>
              <button type="button" class="ghost" :disabled="acting" @click="closeRefund">取消</button>
            </div>
          </form>

          <!-- ===== 区块顺序 =====
               用户看订单详情是「买了什么 → 花了多少 → 订单信息」这条线。
               原先是「金额明细 → 订单信息 → 购买商品」，商品排在最后、金额在最前，
               视线要来回跳（2026-10-07 用户要求把购买商品放到金额明细上面）。 -->

          <h3 class="items-title">购买商品（{{ (orderDetail.data.items || []).length }} 件）</h3>
          <div class="order-items">
            <div v-for="it in (orderDetail.data.items || [])" :key="it.id" class="order-item">
              <img v-if="it.productCoverUrl" :src="it.productCoverUrl" class="order-item-img" alt="商品图片" @error="imgFallback($event, it.productName)"  loading="lazy" decoding="async"/>
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
              <span>会员优惠（已省）<small>售价与你的成交价之差，已含在商品小计中，不重复扣减</small></span>
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
            <!-- 状态文案与配色统一走 userOrderStatus（utils/format.js），与订单列表页同一来源。
                 此前这里用 orderStatusLabel，PAID 会显示成「已支付」而列表页显示「待发货」——
                 同一状态两页不同文案，正是用户说的「看着很乱」（2026-10-07 修）。 -->
            <div class="order-row">
              <span>订单状态</span>
              <b :class="['tag', userOrderStatus(orderDetail.data).cls]">{{ userOrderStatus(orderDetail.data).label }}</b>
            </div>
            <div class="order-row"><span>支付状态</span><b>{{ formatPaymentStatus(orderDetail.data.paymentStatus) }}</b></div>
            <div class="order-row" v-for="t in timeRows" :key="t.label"><span>{{ t.label }}</span><b>{{ t.value }}</b></div>
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

          <!-- ===== 履约操作区（放在页面最底部）=====
               2026-10-07 用户要求「确认收货按钮改到右下角」。原先它挂在顶部状态旁，
               而用户读订单详情是自上而下扫的：状态在最上、金额在中、收货信息在底，
               操作按钮却夹在中间很突兀；而且它和「申请售后」性质不同
               （收货是履约动作，售后是异常处理），并排放在一起容易误点。

               ⚠️ 条件与后端严格一致：
                 · 确认收货：仅 SHIPPED（已发货/待取货）
                 · 申请售后：仅 COMPLETED（收到货之后）
               SHIPPED 时补一句「确认收货后，如有问题可申请售后」——
               售后按钮此时不出现，不解释用户只会以为页面坏了（后端 409 文案他看不到）。 -->
          <div v-if="canConfirmReceipt || canApplyRefund" class="pay-actions detail-actions">
            <button v-if="canConfirmReceipt" class="primary" :disabled="acting" @click="confirmThisOrder">
              确认收货
            </button>
            <button v-if="canApplyRefund" class="ghost" :disabled="acting" @click="startRefund">申请售后</button>
            <small v-if="canConfirmReceipt" class="refund-hint">确认收货后，如有问题可申请售后</small>
          </div>
        </template>
      </section>
</template>

<script>
import { computed, inject, reactive, ref, onMounted, onUnmounted } from 'vue';
import { useRoute } from 'vue-router';
import { orderOriginalSave, formatDate, withMinSpinner, userOrderStatus } from '../utils/format';
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
        // 转圈时长由 withMinSpinner 的 MIN_SPINNER_MS 统一决定（与收银台/提交订单一致）：
        // 本地接口太快，用户看不见 loading 就会以为点击被吞了、进而重复点。
        // 注意传的是**函数**——若传 Promise，起始时间会取在请求已完成之后，时长保证失效。
        await withMinSpinner(() => appCtx.run(async () => {
          await appCtx.api.post(`/orders/${o.id}/pay`);
          await Promise.all([
            appCtx.loadOrders(), appCtx.loadWallet(), appCtx.loadMe(),
            appCtx.loadMemberProfile(), appCtx.loadFlashSales(), appCtx.loadProducts(),
          ]);
          await appCtx.refreshProductDetail();
          appCtx.orderDetail.data = await appCtx.api.get(`/orders/${o.id}`);
        }, '支付成功'));
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

    // ===== 履约操作：确认收货 / 申请售后（2026-10-07 补，此前只有列表页行尾有） =====
    // 条件与后端校验对齐，别让用户点了才吃 409：
    //   · 确认收货：仅 SHIPPED（OrderService.confirmReceipt 拒绝其他状态）
    //   · 申请售后：仅 PAID / SHIPPED，且不在审核中（applyRefund 同规则）
    const acting = ref(false);
    const canConfirmReceipt = computed(() => currentOrder.value?.status === 'SHIPPED');
    // 售后入口：仅「已收货(COMPLETED)」可申请，与后端 applyRefund 一致（2026-10-07 改）。
    // 原先 PAID/SHIPPED 都能申请 —— 货还没发就能退单，而收到货有问题反而不能申诉。
    const canApplyRefund = computed(() => {
      const o = currentOrder.value;
      if (!o || o.status !== 'COMPLETED') return false;
      return o.refundStatus !== 'APPLYING' && o.refundStatus !== 'APPROVED';
    });
    // 售后原因选项：与订单列表页共用同一套口径，避免两个页面选项不一样
    const REFUND_REASONS = ['商品缺货', '商品质量问题', '配送破损', '与描述不符', '不想要了', '其他'];

    async function confirmThisOrder() {
      const o = currentOrder.value;
      if (!o || acting.value) return;
      acting.value = true;
      try {
        await appCtx.confirmReceipt(o.id);
        const fresh = await appCtx.api.get(`/orders/${o.id}`).catch(() => null);
        if (fresh) appCtx.orderDetail.data = fresh;
      } finally {
        acting.value = false;
      }
    }

    // 本页局部表单。**刻意不叫 refundForm** —— appCtx 上已有一个同名共享 state
    //（列表页/本页共用的提交状态），同名会遮蔽它，两处状态互不同步（2026-10-07 踩过）。
    const localRefund = reactive({ open: false, reason: '' });
    function startRefund() {
      localRefund.reason = '';
      localRefund.open = true;
    }
    function closeRefund() {
      localRefund.open = false;
    }
    async function sendRefund() {
      const o = currentOrder.value;
      if (!o || acting.value) return;
      if (!localRefund.reason) {
        appCtx.fail('请选择售后原因');
        return;
      }
      acting.value = true;
      try {
        // 复用 appCtx 上与列表页同一个 submitRefund：它内部用共享的 refundForm.reason、
        // 自带二次确认弹窗，成功后会重拉订单/钱包/会员/秒杀名额。
        // 这里先把本页的选项写进共享 state 再调，避免两页各维护一份原因与提交逻辑。
        appCtx.refundForm.reason = localRefund.reason;
        await appCtx.submitRefund(o.id);
        localRefund.open = false;
        const fresh = await appCtx.api.get(`/orders/${o.id}`).catch(() => null);
        if (fresh) appCtx.orderDetail.data = fresh;
      } finally {
        acting.value = false;
      }
    }

    let payTick = null;
    // 路由里的订单 id：用于「本页自己没数据时去拉」。
    // 用 route 而不是 currentOrder —— 后者正是要从数据里读 id 的，data 为空时拿不到。
    // ⚠️ 走 useRoute() 而不是 appCtx.route：appCtx 上并没有挂 route（只有 ROUTE_VIEWS 常量），
    //    写 appCtx.route?.params?.id 会静默拿到 undefined，兜底逻辑永远不触发。
    const route = useRoute();
    const routeOrderId = computed(() => {
      const raw = route.params?.id ?? route.query?.id;
      const n = Number(raw);
      return Number.isFinite(n) && n > 0 ? n : null;
    });

    onMounted(async () => {
      recomputePayRemaining();
      payTick = setInterval(recomputePayRemaining, 1000);
      // ⚠️ 本页此前**完全依赖 appCtx.orderDetail.data**，自己从不拉数据。后果有两个：
      //   ① 用户刷新详情页（F5）或直接粘地址栏进来 → data 为空 → 整个页面空白；
      //   ② 支付后跳详情页时若没先填好数据，同样白屏。
      // 所以这里兜底：data 缺失或与路由 id 不一致时，主动拉一次。
      const d = currentOrder.value;
      const id = routeOrderId.value;
      if (id && (!d || Number(d.id) !== id)) {
        appCtx.orderDetail.loading = true;
        try {
          const fresh = await appCtx.api.get(`/orders/${id}`).catch(() => null);
          if (fresh) appCtx.orderDetail.data = fresh;
        } finally {
          appCtx.orderDetail.loading = false;
        }
      }
    });
    onUnmounted(() => {
      if (payTick) clearInterval(payTick);
    });

    // 订单时间线：按订单走过的生命周期节点展示对应时间戳（后端已下发，前端只挑非空的显示）。
    // 始终是「下单时间」打头，后面依次是支付/发货/完成/取消/关闭/退款 —— 不一次性全列，避免没走过的节点显示一堆「-」。
    const timeRows = computed(() => {
      const d = appCtx.orderDetail.data;
      if (!d) return [];
      const rows = [];
      const push = (label, v) => { if (v) rows.push({ label, value: formatDate(v) }); };
      push('下单时间', d.createdAt);
      push('支付时间', d.paidAt);
      push('发货时间', d.shippedAt);
      push('完成时间', d.completedAt);
      push('取消时间', d.canceledAt);
      push('关闭时间', d.closedAt);
      push('退款时间', d.refundedAt);
      return rows;
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

    return { ...appCtx, amount, timeRows, isPendingPay, payExpired, payMmss, payRemainingSec, paySubmitting, payNow, cancelThisOrder, acting, canConfirmReceipt, canApplyRefund, confirmThisOrder, REFUND_REASONS, localRefund, startRefund, closeRefund, sendRefund, userOrderStatus, routeOrderId };
  }
};
</script>
