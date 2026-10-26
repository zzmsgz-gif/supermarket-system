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
        <div v-if="orderDetail.loading" class="od-skeleton" aria-hidden="true">
          <skeleton-lines :lines="3" :title="true" />
          <skeleton-list :count="2" :thumb="true" :lines="2" />
          <skeleton-lines :lines="4" />
        </div>
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
          <!-- 售后申请表单已移至页面底部操作按钮下方（见本文件 detail-actions 之后）——
             2026-10-16 用户反馈：表单放在顶部「购买商品」上方时，点底部「申请退款」后
             表单却出现在订单最顶上，位置和触发点割裂；挪到按钮旁才符合「点哪开哪」。 -->

          <!-- ===== 区块顺序 =====
               用户看订单详情是「买了什么 → 花了多少 → 订单信息」这条线。
               原先是「金额明细 → 订单信息 → 购买商品」，商品排在最后、金额在最前，
               视线要来回跳（2026-10-07 用户要求把购买商品放到金额明细上面）。 -->

          <h3 class="items-title">购买商品（{{ (orderDetail.data.items || []).length }} 件）</h3>
          <!-- 整单在途售后（orderItemId 为 null）：全单统一处理，提示用户无需逐件申请 -->
          <p v-if="hasOpenWholeOrderReturn" class="order-return-banner">
            整单售后处理中（{{ wholeOrderReturnStatusLabel }}），请到「我的售后」查看进度，无需逐件申请。
          </p>
          <div class="order-items">
            <div v-for="it in (orderDetail.data.items || [])" :key="it.id" class="order-item">
              <img v-if="it.productCoverUrl" :src="it.productCoverUrl" class="order-item-img" alt="商品图片" @error="imgFallback($event, it.productName)"  loading="lazy" decoding="async"/>
              <div class="order-item-info">
                <div class="order-item-name">{{ it.productName }}<span v-if="it.flashSaleId" class="tag warn order-item-flash-tag">秒杀 · 不参与满减/券</span></div>
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
              <!-- 2026-10-16 按订单状态统一文案：未收到货(PAID/SHIPPED)=退款，收到货(COMPLETED)=售后。
                   行内入口仅限已完成(COMPLETED)订单；未收货退款走底部整单按钮，行内不重复出按钮。 -->
              <div class="order-item-actions">
                <button
                  v-if="itemCanRefund(it)"
                  class="ghost sm"
                  :disabled="acting"
                  @click="startRefundItem(it)"
                >{{ applyRefundLabel }}</button>
                <span v-if="it.refunded === true" class="tag ok order-item-done">已{{ applyRefundLabel.slice(2) }}</span>
                <span
                  v-else-if="itemOpenReturn(it)"
                  :class="['tag', returnStatusCls(itemOpenReturn(it).status)]"
                >{{ itemOpenReturn(it).statusLabel }} · 处理中</span>
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

            <!-- ⚠️ 会员让利后端恒置 memberDiscount=0（会员价/等级折扣已下沉到单品成交价），
                 真实省下的钱按下单时的来源快照（order_item.member_source）拆成两行（2026-10-13）：
                 「会员优惠」=商品会员价更低那部分；「会员折扣」=等级折扣更低那部分。两种都有则各占一行。
                 仅作信息展示、不进优惠合计；历史订单无快照时仍合并进「会员折扣」一行。 -->
            <div class="amount-row minus" v-if="amount.memberPriceSave > 0">
              <span>会员优惠<small>商品会员价与售价之差，已含在商品小计中，不重复扣减</small></span>
              <b>- {{ money(amount.memberPriceSave) }}</b>
            </div>
            <div class="amount-row minus" v-if="amount.memberLevel > 0 || amount.tierSave > 0">
              <span>会员折扣（{{ tierNameFor(amount.memberLevel) }}）<small v-if="amount.tierSave <= 0">本单未产生等级折扣</small><small v-else>售价×等级折扣与售价之差，已含在商品小计中，不重复扣减</small></span>
              <b v-if="amount.tierSave > 0">- {{ money(amount.tierSave) }}</b>
              <b v-else class="none">无</b>
            </div>
            <div class="amount-row minus">
              <span>优惠券<template v-if="amount.couponName">（{{ amount.couponName }}）</template><small v-if="amount.coupon > 0 && amount.flashSubtotal > 0">券门槛与抵扣按非秒杀部分 {{ money(amount.activityBase) }} 计算</small></span>
              <b v-if="amount.coupon > 0">- {{ money(amount.coupon) }}</b>
              <b v-else class="none">未使用</b>
            </div>
            <div class="amount-row minus">
              <span>活动优惠<template v-if="amount.activityName">（{{ amount.activityName }}）</template><small v-if="amount.activity > 0 && amount.flashSubtotal > 0">仅按非秒杀部分 {{ money(amount.activityBase) }} 计算，秒杀 {{ money(amount.flashSubtotal) }} 不参与</small><small v-else-if="amount.activity > 0">按小计 {{ money(amount.activityBase) }} 计算</small></span>
              <b v-if="amount.activity > 0">- {{ money(amount.activity) }}</b>
              <b v-else class="none">未参与</b>
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
            <p class="amount-note" v-if="amount.activity > 0 && amount.flashSubtotal > 0">
              活动仅算非秒杀部分：{{ money(amount.activityBase) }} 参与活动、秒杀 {{ money(amount.flashSubtotal) }} 不参与，故活动优惠是 {{ money(amount.activity) }} 而非按整单小计折算
            </p>
            <p class="amount-note">
              小计 {{ money(amount.goods) }}<template v-if="amount.freight > 0"> + 运费 {{ money(amount.freight) }}</template>
              <template v-if="amount.totalDiscount > 0"> − 优惠合计 {{ money(amount.totalDiscount) }}</template>
              = 实付 {{ money(amount.pay) }}
            </p>
            <p class="amount-note" v-if="amount.pointsEarned > 0">本单获得 {{ amount.pointsEarned }} 积分</p>
            <!-- 方案D-B：退款时已花掉、等价扣回的发放积分 —— 在金额明细里让用户看得见（仅当有扣回时） -->
            <p class="amount-note refund-deduct-note" v-if="refundReturnsDeducted.length">
              退款时已花掉、等价扣回的发放积分：
              <template v-for="(r, i) in refundReturnsDeducted" :key="r.id">{{ r.pointsDeducted }} 分（{{ money(Number(r.pointsDeducted) / 100) }}）<template v-if="i < refundReturnsDeducted.length - 1">；</template></template>
              <span class="rd-sub">（已在实退金额中扣除，100 分 = ¥1）</span>
            </p>
            <!-- 方案D-回收：本单用积分抵过现、且已退款 → 抵扣分已返还到账户余额（TYPE_REFUND 返还抵扣）。
                 此前金额明细只展示了下单时的「积分抵扣 −¥X」，没告诉用户这些分在退款后回到了账户，
                 用户以为分凭空没了。这里补一行说明（仅当本单确实抵过现且已发生退款）。 -->
            <p class="amount-note refund-return-note" v-if="amount.pointsUsed > 0 && hasRefund">
              本单退款后，抵扣用的 {{ amount.pointsUsed }} 分已返还到你的积分账户（约 {{ money(amount.pointsUsed / 100) }}），可在「我的积分」中查看
            </p>
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
            <!-- 方案D-C：在途退款冻结的发放积分，让用户在本页也看得见 -->
            <div class="order-row" v-if="openReturnFrozenPoints > 0">
              <span>冻结积分</span>
              <b class="frozen-row">本单退款审核中已冻结 {{ openReturnFrozenPoints }} 分发放积分（暂不可用，退款/驳回后自动处理）</b>
            </div>
            <!-- 点6：用户申请的寄回物流信息在订单详情展示。
                 从本单售后单里挑出「待寄回/已寄回」的在途单，显示用户填的快递公司 + 运单号，
                 让用户与商家在同一页面看到同一份物流信息（此前仅 ReturnsPage 有、详情页缺）。 -->
            <template v-if="(orderReturns || []).some(r => r.status === 'WAITING_SHIP' || r.status === 'SHIPPED_BACK')">
              <div class="order-row" v-for="r in (orderReturns || []).filter(r => r.status === 'WAITING_SHIP' || r.status === 'SHIPPED_BACK')" :key="'shipback-' + r.id">
                <span>{{ r.status === 'SHIPPED_BACK' ? '寄回物流' : '待寄回' }}</span>
                <b>
                  <template v-if="r.status === 'SHIPPED_BACK'">{{ r.expressCompany || '未填快递公司' }}<template v-if="r.trackingNo"> · {{ r.trackingNo }}</template></template>
                  <template v-else>等待你寄回商品（审核通过后请填写快递公司与运单号）</template>
                </b>
              </div>
              <!-- 2026-10-22：去掉详情页内嵌的「快递公司+运单号 → 提交寄回」表单 ——
                   列表卡片已内嵌同款表单（OrdersPage card-shipback），两处重复维护；
                   详情页只保留上面的「待寄回/寄回物流」信息行。 -->

            </template>
            <div class="order-row" v-if="orderDetail.data.remark"><span>备注</span><b>{{ orderDetail.data.remark }}</b></div>
          </div>

          <!-- ===== 履约操作区（放在页面最底部）=====
               2026-10-07 用户要求「确认收货按钮改到右下角」。原先它挂在顶部状态旁，
               而用户读订单详情是自上而下扫的：状态在最上、金额在中、收货信息在底，
               操作按钮却夹在中间很突兀；而且它和「申请售后」性质不同
               （收货是履约动作，售后是异常处理），并排放在一起容易误点。

               ⚠️ 条件与后端严格一致：
                 · 确认收货：仅 SHIPPED（已发货/待取货）
                 · 申请退款/售后：PAID（已付款未发货）/ SHIPPED / COMPLETED（2026-10-14 放宽，
                   与 ReturnRequestService.apply 门禁一致；此前漏了 PAID，用户付完款找不到退款入口）
               SHIPPED 时补一句「确认收货前也可直接申请退款」——
               解释按钮为什么还在，不解释用户只会以为页面坏了（后端 409 文案他看不到）。 -->
          <div v-if="canConfirmReceipt || canApplyRefund" class="pay-actions detail-actions">
            <button v-if="canConfirmReceipt" class="primary" :disabled="acting" @click="confirmThisOrder">
              确认收货
            </button>
            <button v-if="canApplyRefund" class="ghost" :disabled="acting" @click="startRefund">{{ applyRefundLabel }}</button>
            <small v-if="canConfirmReceipt" class="refund-hint">确认收货前也可直接「申请退款」，商家审核后按原路退回</small>
          </div>
          <!-- 售后/退款申请表单：紧贴操作按钮下方（点哪个按钮就在哪展开，不再跳到页面顶部）。
               标签随订单状态：未收到货=退款原因，收到货=售后原因（2026-10-16 统一口径）。 -->
          <form v-if="localRefund.open" ref="refundFormEl" class="refund-form-inline" @submit.prevent="sendRefund">
            <h4>{{ refundTitle }}</h4>
            <label>
              {{ applyRefundLabel.slice(2) }}原因
              <!-- 2026-10-18 去掉「请选择原因」占位项：打开表单即默认选中第一项，少一步操作 -->
              <select v-model="localRefund.reason">
                <option v-for="r in REFUND_REASONS" :key="r" :value="r">{{ r }}</option>
              </select>
            </label>
            <!-- 2026-10-16：原因选「其他」时展开手填框，商家审核能看到具体内容 -->
            <input
              v-if="localRefund.reason === '其他'"
              v-model="localRefund.reasonOther"
              class="refund-other-input"
              type="text"
              maxlength="100"
              placeholder="请填写具体原因"
            />
            <p class="refund-hint">提交后由商家审核；审核通过将按原支付方式退回。</p>
            <div class="refund-actions">
              <button type="submit" class="primary" :disabled="acting">{{ acting ? '提交中…' : '提交申请' }}</button>
              <button type="button" class="ghost" :disabled="acting" @click="closeRefund">取消</button>
            </div>
          </form>
        </template>
      </section>
</template>

<script>
import { computed, inject, reactive, ref, nextTick, onMounted, onUnmounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
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

    // ===== 履约操作：确认收货 / 申请退款·售后（2026-10-07 补，此前只有列表页行尾有） =====
    // 条件与后端校验对齐，别让用户点了才吃 409：
    //   · 确认收货：仅 SHIPPED（OrderService.confirmReceipt 拒绝其他状态）
    //   · 申请退款/售后：PAID / SHIPPED / COMPLETED，且不在审核中（ReturnRequestService.apply 同规则）
    const acting = ref(false);
    const canConfirmReceipt = computed(() => currentOrder.value?.status === 'SHIPPED');
    // 2026-10-14「随时退款」：已付款(PAID)/已发货(SHIPPED)可「申请退款」、已收货(COMPLETED)可「申请售后」，
    // 都走新售后链路（POST /returns，商家审核决定要不要寄回、运费谁出）。
    // ⚠️ PAID 必须包含：用户付完款第一眼找的就是退款入口，漏了它就是「买完不能退款」。
    // 未付款(PENDING_PAYMENT)用「取消订单」秒退，不在此列；已在售后流程中(APPLYING/APPROVED)不再显示入口。
    const canApplyRefund = computed(() => {
      const o = currentOrder.value;
      if (!o) return false;
      if (o.status !== 'PAID' && o.status !== 'SHIPPED' && o.status !== 'COMPLETED') return false;
      // 仅在「从未申请(NONE)」或「上次被驳回(REJECTED)」时显示入口；在途/已退款均不显示
      // （与 ReturnRequestService.apply 的按单拦截、REJECTED 可重申口径一致）
      return o.refundStatus === 'NONE' || o.refundStatus === 'REJECTED';
    });
    const applyRefundLabel = computed(() => (currentOrder.value?.status === 'COMPLETED' ? '申请售后' : '申请退款'));
    // 原因选项分两套（2026-10-18）：未收到货的「申请退款」不该出现「商品质量问题/配送破损」
    // 这类只有拿到货才会发生的售后原因；已收货（COMPLETED）的「申请售后」才用那套。
    // 逐行售后入口只在 COMPLETED 出现，因此按整单状态分流即可覆盖两种表单。
    const REFUND_REASONS_UNRECEIVED = ['不想要了', '拍错了/多拍了', '商品缺货', '发货时间太长', '其他'];
    const REFUND_REASONS_RECEIVED = ['商品质量问题', '配送破损', '与描述不符', '少发/漏发', '其他'];
    const REFUND_REASONS = computed(() =>
      (currentOrder.value?.status === 'COMPLETED' ? REFUND_REASONS_RECEIVED : REFUND_REASONS_UNRECEIVED)
    );

    // ===== 2026-10-15 按商品行并行售后 =====
    // 拉本单全部售后单（GET /returns/order/{id}，按商品行并行后可有多条），
    // 用来：① 判断每行是否还能发起（在途/已退则收起入口）② 展示每行在途状态 ③ 整单在途时收起逐件入口。
    const orderReturns = ref([]);
    const OPEN_RETURN_STATUSES = ['APPLYING', 'WAITING_SHIP', 'SHIPPED_BACK'];
    async function loadOrderReturns(id) {
      try {
        const res = await appCtx.api.get(`/returns/order/${id}`);
        // 后端返回 ApiResponse<List<ReturnRequestResponse>>，api.get 已解包成数组；
        // 这里兼容「数组」与「带 items 的包装」两种形态，避免接口形态变动时拿不到数据。
        orderReturns.value = Array.isArray(res) ? res : (res?.items || []);
      } catch {
        orderReturns.value = [];
      }
    }
    // 整单在途售后（orderItemId 为 null）：存在则全单统一处理，逐件入口收起，避免重复发起
    const hasOpenWholeOrderReturn = computed(() => (orderReturns.value || []).some(
      (r) => r.orderItemId == null && OPEN_RETURN_STATUSES.includes(r.status)
    ));
    const wholeOrderReturnStatusLabel = computed(() => {
      const w = (orderReturns.value || []).find((r) => r.orderItemId == null && OPEN_RETURN_STATUSES.includes(r.status));
      return w ? w.statusLabel : '';
    });
    // 某商品行是否有在途售后（按 orderItemId 命中）；整单售后单（orderItemId=null）不命中任何行
    const itemOpenReturn = (it) => (orderReturns.value || []).find(
      (r) => r.orderItemId === it.id && OPEN_RETURN_STATUSES.includes(r.status)
    ) || null;
    // 某商品行是否可发起售后：① 整单处于可售后状态 ② 本行未退 ③ 本行无在途 ④ 无整单在途
    // 2026-10-16 收紧：行内入口只在「已完成(COMPLETED)」订单出现——未收货(PAID/SHIPPED)
    // 的退款是整单行为，走底部整单按钮，行内不再重复出「申请退款」（用户反馈两处按钮混淆）。
    const itemCanRefund = (it) => {
      const o = currentOrder.value;
      if (!o) return false;
      if (o.status !== 'COMPLETED') return false;
      if (it.refunded === true) return false;
      if (hasOpenWholeOrderReturn.value) return false;
      return !itemOpenReturn(it);
    };
    // return 五态 → 徽标配色（与 ReturnsPage / AdminRefundsPanel 同源）
    const RETURN_STATUS_TAG = { APPLYING: 'warn', WAITING_SHIP: 'warn', SHIPPED_BACK: 'info', APPROVED: 'ok', REJECTED: 'muted', PARTIAL: 'warn' };
    const returnStatusCls = (s) => RETURN_STATUS_TAG[s] || 'muted';
    // 方案D-B：已完成退款中、被现金等价扣回的发放积分（仅 pointsDeducted>0 的退款单），用于金额明细展示。
    const refundReturnsDeducted = computed(() => (orderReturns.value || []).filter(
      (r) => (Number(r.pointsDeducted) || 0) > 0
    ));
    // 方案D-C：在途退款单冻结的发放积分合计（让用户在本页也看得见「本单冻结了多少」）。
    const openReturnFrozenPoints = computed(() => (orderReturns.value || [])
      .filter((r) => OPEN_RETURN_STATUSES.includes(r.status) && (Number(r.frozenPoints) || 0) > 0)
      .reduce((sum, r) => sum + (Number(r.frozenPoints) || 0), 0));
    // 本单是否已进入退款流程（非 NONE）—— 只有退过款，"抵扣分已返还"才有意义展示。
    const hasRefund = computed(() => {
      const rs = currentOrder.value?.refundStatus;
      return !!rs && rs !== 'NONE';
    });

    // 2026-10-22：寄回表单已从详情页移除（列表卡片内嵌同款表单），相关草稿状态与提交函数一并清理。

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
    // 2026-10-15 扩展支持「按商品行申请」：orderItemId 非空 = 仅退该商品行；null = 整单售后。
    // 2026-10-16 增加 reasonOther：原因选「其他」时手填具体内容。
    const localRefund = reactive({ open: false, reason: '', reasonOther: '', orderItemId: null, itemName: '' });
    function startRefund() {
      localRefund.reason = REFUND_REASONS.value[0] || '';
      localRefund.reasonOther = '';
      localRefund.orderItemId = null;
      localRefund.itemName = '';
      localRefund.open = true;
    }
    // 逐行申请：记下要退的商品行，表单标题带上商品名，便于用户确认退的是哪一件
    function startRefundItem(it) {
      localRefund.reason = REFUND_REASONS.value[0] || '';
      localRefund.reasonOther = '';
      localRefund.orderItemId = it.id;
      localRefund.itemName = it.productName || '';
      localRefund.open = true;
    }
    function closeRefund() {
      localRefund.open = false;
    }
    async function sendRefund() {
      const o = currentOrder.value;
      if (!o || acting.value) return;
      if (!localRefund.reason) {
        appCtx.fail(`请选择${applyRefundLabel.value.slice(2)}原因`);
        return;
      }
      // 选「其他」时必须手填具体原因，否则商家只看"其他"无从判断
      let reasonText = localRefund.reason;
      if (localRefund.reason === '其他') {
        const detail = (localRefund.reasonOther || '').trim();
        if (!detail) {
          appCtx.fail('请填写具体原因');
          return;
        }
        reasonText = `其他：${detail}`;
      }
      acting.value = true;
      try {
        // 复用 appCtx 上与列表页同一个 submitRefund：它内部用共享的 refundForm.reason、
        // 自带二次确认弹窗，成功后会重拉订单/钱包/会员/秒杀名额。
        // 这里先把本页的选项写进共享 state 再调，避免两页各维护一份原因与提交逻辑。
        appCtx.refundForm.reason = reasonText;
        // 透传 orderItemId：非空=按行售后，null=整单售后（与 ReturnRequestService.apply 口径一致）
        await appCtx.submitRefund(o.id, localRefund.orderItemId || null);
        localRefund.open = false;
        localRefund.orderItemId = null;
        const fresh = await appCtx.api.get(`/orders/${o.id}`).catch(() => null);
        if (fresh) appCtx.orderDetail.data = fresh;
        // 重新拉本单售后，刷新逐行在途 / 整单在途状态（避免刚提交的行还显示「申请售后」）
        await loadOrderReturns(o.id);
      } finally {
        acting.value = false;
      }
    }
    // 弹窗标题同样跟随订单状态：未收货=申请退款，已收货=申请售后（2026-10-16 修正写死文案）
    const refundTitle = computed(() => (localRefund.orderItemId ? `${applyRefundLabel.value} · ${localRefund.itemName || '商品'}` : applyRefundLabel.value));

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

    // ===== 2026-10-16 列表页「申请退款」直达：消费 ?refund=1 =====
    // 订单数据一落地（watch currentOrder.id）就自动展开底部退款表单并滚动定位，
    // 然后立刻清掉 query 参数（防刷新/前进后退重复弹出）。条件不满足（如已在售后流程中）
    // 则只清参数不弹表单 —— 页面上的售后状态徽标已说明原因。
    const router = useRouter();
    const refundFormEl = ref(null);
    async function maybeAutoOpenRefund() {
      if (String(route.query?.refund || '') !== '1') return;
      router.replace({ query: { ...route.query, refund: undefined } });
      await nextTick();
      if (!canApplyRefund.value) return;
      startRefund();
      await nextTick();
      refundFormEl.value?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }

    // 订单详情数据由 openOrderDetail（列表页点进来）或本页 onMounted（直链/刷新）两处填充；
    // 用 watch 统一在「订单 id 变化」时拉本单售后，两处入口都覆盖，无需各自改。
    watch(
      () => currentOrder.value?.id,
      async (id) => {
        if (!id) return;
        await loadOrderReturns(id);
        await maybeAutoOpenRefund();
      },
      { immediate: true }
    );

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

      // 会员让利按**来源**拆两行（2026-10-13 用户要求）：
      //   · 商品会员价更低 → 「会员优惠」（那是商品的专属价，不是你的等级折扣）
      //   · 等级折扣价更低 → 「会员折扣」（银卡9.8折那类）
      // 一个订单两种都有时分两行各算各的。来源以下单时快照 order_item.member_source 为准。
      // 历史订单无快照（整单都没有 memberSource）→ 无法可靠拆分，沿用旧的「会员折扣」合并展示。
      let tierSave = 0;
      let memberPriceSave = 0;
      const items = d.items || [];
      const hasSource = items.some(it => it.memberSource === 'member' || it.memberSource === 'tier');
      if (hasSource) {
        for (const it of items) {
          if (it.flashSaleId) continue; // 秒杀成交的让利来自秒杀价，不是会员权益
          const save = (num(it.originalPrice) - num(it.productPrice)) * num(it.quantity || 1);
          if (!(save > 0)) continue;
          if (it.memberSource === 'member') memberPriceSave += save;
          else tierSave += save;
        }
        memberPriceSave = Math.round(memberPriceSave * 100) / 100;
        tierSave = Math.round(tierSave * 100) / 100;
      } else {
        tierSave = orderOriginalSave(d);
      }

      // 2026-10-22 用户反馈：顾客会拿整单小计去乘活动折扣（602.28×0.8=481.82），
      // 发现比实付低就误以为多收钱——根因是活动基数只有非秒杀部分（303.28），
      // 页面没把基数说出来。这里把秒杀小计 / 活动基数算出来给模板展示。
      const flashSubtotal = Math.round(items.reduce((s, it) => (
        s + (it.flashSaleId ? num(it.subtotalAmount) : 0)
      ), 0) * 100) / 100;
      const activityBase = Math.round((goods - flashSubtotal) * 100) / 100;

      return {
        goods,
        freight,
        coupon,
        couponName: d.couponName || '',
        activity,
        activityName: d.activityName || '',
        flashSubtotal,
        activityBase,
        member,
        memberLevel: num(d.memberLevel),
        points,
        pointsUsed: num(d.pointsUsed),
        pointsEarned: num(d.pointsEarned),
        // 划线优惠只是"已省"信息（已含在商品小计的单价里），不进优惠合计。
        originalSave: orderOriginalSave(d),
        memberSave: orderOriginalSave(d),
        memberPriceSave,
        tierSave,
        totalDiscount,
        pay: num(d.payAmount)
      };
    });

    return { ...appCtx, amount, timeRows, isPendingPay, payExpired, payMmss, payRemainingSec, paySubmitting, payNow, cancelThisOrder, acting, canConfirmReceipt, canApplyRefund, applyRefundLabel, confirmThisOrder, REFUND_REASONS, localRefund, startRefund, startRefundItem, closeRefund, sendRefund, refundTitle, itemCanRefund, itemOpenReturn, hasOpenWholeOrderReturn, wholeOrderReturnStatusLabel, returnStatusCls, userOrderStatus, routeOrderId, refundFormEl, orderReturns, refundReturnsDeducted, openReturnFrozenPoints, hasRefund };
  }
};
</script>
