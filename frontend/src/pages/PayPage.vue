<template>
  <section class="data-panel pay-cashier">
    <div class="panel-head">
      <button class="ghost" @click="navigate('orders')">返回我的订单</button>
    </div>

    <!-- 加载 / 异常 -->
    <div v-if="loading" class="pay-loading">正在加载订单…</div>
    <div v-else-if="loadError" class="pay-error">
      <p>{{ loadError }}</p>
      <button class="ghost" @click="navigate('orders')">返回我的订单</button>
    </div>

    <template v-else-if="order">
      <!-- 付款状态横幅 -->
      <div class="pay-state" :class="stateClass">
        <span class="pay-state-badge">{{ stateBadge }}</span>
        <div class="pay-state-main">
          <h3>{{ stateTitle }}</h3>
          <!-- 倒计时只在「待付款」时显示；金额的锁定期限由后端 payDeadline 给出 -->
          <p v-if="isPayable" class="pay-timer">
            请在 <strong class="countdown" :class="{ urgent: remainingSec <= 60 }">{{ mmss }}</strong>
            内完成支付，超时未完成将自动关闭订单。
          </p>
          <!-- 还有时间付款时不再解释「库存/扣款」这类内部机制 —— 用户只关心还剩多久、
               要不要付。仅在「已支付 / 已超时 / 已关闭」时才给一句解释，说明为什么动不了。 -->
          <p v-if="!isPayable" class="pay-state-desc">{{ stateDesc }}</p>
        </div>
      </div>

      <!-- 应付金额 -->
      <div class="pay-amount-row">
        <span>应付金额</span>
        <strong class="pay-amount">{{ money(order.payAmount) }}</strong>
      </div>

      <!-- 订单摘要 -->
      <dl class="pay-meta">
        <div><dt>订单编号</dt><dd>{{ order.orderNo }}</dd></div>
        <div><dt>下单时间</dt><dd>{{ order.createdAt ? order.createdAt.replace('T', ' ') : '—' }}</dd></div>
        <div v-if="order.fulfillmentType === 'PICKUP'"><dt>提货方式</dt><dd>门店自提：{{ order.pickupStoreName || '—' }}</dd></div>
        <div v-else><dt>收货信息</dt><dd>{{ order.receiverName }} {{ order.receiverPhone }} · {{ order.receiverAddress }}</dd></div>
        <div v-if="order.remark"><dt>订单备注</dt><dd>{{ order.remark }}</dd></div>
      </dl>

      <!-- 商品清单 -->
      <div v-if="order.items && order.items.length" class="pay-items">
        <h4>商品清单（共 {{ totalQuantity }} 件）</h4>
        <div v-for="it in order.items" :key="it.id" class="pay-item-row">
          <span class="pay-item-name">{{ it.productName }}<small v-if="it.skuSpec"> · {{ it.skuSpec }}</small></span>
          <span class="pay-item-qty">×{{ it.quantity }}</span>
          <span class="pay-item-price">{{ money(it.subtotalAmount) }}</span>
        </div>
      </div>

      <!-- 金额明细 -->
      <div class="pay-summary">
        <div class="row"><span>商品合计</span><strong>{{ money(order.totalAmount) }}</strong></div>
        <div class="row"><span>运费</span><strong>{{ money(order.freightAmount) }}</strong></div>
        <div v-if="Number(order.couponName ? order.discountAmount : 0) > 0" class="row">
          <span>优惠券抵扣</span><strong class="minus">- {{ money(order.discountAmount) }}</strong>
        </div>
        <div v-if="Number(order.pointsDiscount) > 0" class="row">
          <span>积分抵扣</span><strong class="minus">- {{ money(order.pointsDiscount) }}</strong>
        </div>
      </div>

      <!-- 操作区 -->
      <div class="pay-actions">
        <template v-if="isPending">
          <button class="primary" :class="{ loading: paying }" :disabled="paying" @click="doPay">
            <span v-if="paying" class="spinner"></span>
            <span>{{ paying ? '支付处理中…' : '立即支付 ' + money(order.payAmount) }}</span>
          </button>
          <button class="ghost" :disabled="paying" @click="doCancel">取消订单</button>
        </template>
        <template v-else>
          <button class="primary" @click="navigate('shop')">继续挑选商品</button>
          <button class="ghost" @click="navigate('orders')">查看我的订单</button>
        </template>
      </div>
    </template>
  </section>
</template>

<script>
import { inject, ref, computed, onMounted, onUnmounted } from 'vue';
import { useRoute } from 'vue-router';

// 待付款收银台：下单之后、真正扣款之前的一步。
// 之所以加这一步，是因为后端「建单即锁库存」——订单一诞生（PENDING_PAYMENT）就已经占了
// 库存与秒杀名额。之前前端在创建订单后立刻自动付款，把这个真实状态藏了起来，
// 导致用户看到「订单存在 / 名额被占」却一头雾水。这里把它显式出来：
// 显示还剩多久可付，并让用户真正掌握「付」或「不付」的决定权。
export default {
  name: 'PayPage',
  setup() {
    const appCtx = inject('appCtx');
    const route = useRoute();

    const order = ref(null);
    const loading = ref(true);
    const loadError = ref('');
    const paying = ref(false);
    // 剩余秒数：基于后端给的**绝对截止时刻**计算，绝不用「客户端当前时间 + 时长」反推，
    // 否则浏览器与服务端之间的时钟偏差会让倒计时不可信。
    const remainingSec = ref(0);

    let tickTimer = null;
    let pollTimer = null;

    const orderId = computed(() => Number(route.params.id) || 0);

    const isPending = computed(() => !!order.value && order.value.status === 'PENDING_PAYMENT');

    // 本地倒计时先到 0，或后端已把单关掉（CLOSED/CANCELED），都算不可支付
    const isPayable = computed(() => isPending.value && remainingSec.value > 0);

    const mmss = computed(() => {
      const s = Math.max(0, remainingSec.value);
      const m = Math.floor(s / 60);
      const sec = s % 60;
      return `${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`;
    });

    const stateClass = computed(() => {
      if (!order.value) return '';
      if (order.value.status === 'PAID') return 'paid';
      if (order.value.status === 'PENDING_PAYMENT') return remainingSec.value > 0 ? 'pending' : 'closed';
      return 'closed';
    });

    const stateBadge = computed(() => {
      if (!order.value) return '';
      if (order.value.status === 'PAID') return '已支付';
      if (order.value.status === 'PENDING_PAYMENT') return remainingSec.value > 0 ? '待付款' : '已超时';
      return '已关闭';
    });

    const stateTitle = computed(() => {
      if (!order.value) return '';
      if (order.value.status === 'PAID') return '该订单已完成支付';
      if (order.value.status === 'PENDING_PAYMENT') {
        return remainingSec.value > 0 ? '订单已提交，等待付款' : '支付超时，订单已失效';
      }
      return '订单已关闭';
    });

    const stateDesc = computed(() => {
      if (!order.value) return '';
      if (order.value.status === 'PAID') return '无需重复支付，可在订单列表查看物流进度。';
      // 走到这里说明「待付款却已不可支付」：模板只在 !isPayable 时才显示本句，
      // 所以不再保留「下单时已为你锁定库存…」那个待付款分支（09-25 按用户决定删掉）。
      if (order.value.status === 'PENDING_PAYMENT') {
        return '超过支付时限，订单已自动关闭，占用的库存与优惠券已释放。';
      }
      return '该订单不再可支付，占用的库存与优惠券已释放。';
    });

    const totalQuantity = computed(() =>
      (order.value && order.value.items || []).reduce((n, it) => n + (Number(it.quantity) || 0), 0)
    );

    // 由后端下发的绝对截止时刻算出剩余秒数。payDeadline 是 "2026-09-25T15:20:00" 这类
    // 不带时区的时间，与本机（服务端）同一时区，按本地时间解析即为服务端口径。
    function recomputeRemaining() {
      const deadline = order.value && order.value.payDeadline;
      if (!deadline) { remainingSec.value = 0; return; }
      const ms = new Date(String(deadline).replace(' ', 'T')).getTime();
      if (Number.isNaN(ms)) { remainingSec.value = 0; return; }
      remainingSec.value = Math.max(0, Math.round((ms - Date.now()) / 1000));
    }

    async function loadOrder() {
      try {
        const data = await appCtx.api.get(`/orders/${orderId.value}`);
        order.value = data;
        recomputeRemaining();
        loadError.value = '';
      } catch (e) {
        order.value = null;
        loadError.value = (e && e.message) || '订单加载失败，可能已被删除或不属于当前账号。';
      } finally {
        loading.value = false;
      }
    }

    // 轮询：后端超时调度器关单后同步过来；也能捕捉到别的标签页已完成支付的情况。
    // 只在「待付款且本地还剩时间」时轮询，避免无意义的请求。
    function startPoll() {
      stopPoll();
      pollTimer = setInterval(async () => {
        if (!isPending.value || remainingSec.value <= 0) return;
        try {
          const data = await appCtx.api.get(`/orders/${orderId.value}`);
          order.value = data;
          recomputeRemaining();
        } catch { /* 轮询失败不影响界面，保留上一次状态 */ }
      }, 10000);
    }
    function stopPoll() {
      if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
    }

    async function doPay() {
      if (paying.value || !isPayable.value) return;
      paying.value = true;
      try {
        await appCtx.api.post(`/orders/${orderId.value}/pay`);
        // 付款后各项额度都变了：订单列表、钱包、会员成长值、秒杀名额都要重拉
        await Promise.all([
          appCtx.loadOrders(),
          appCtx.loadWallet(),
          appCtx.loadMe(),
          appCtx.loadMemberProfile(),
          appCtx.loadFlashSales(),
        ]);
        appCtx.navigate('orders');
      } catch (e) {
        // 付失败最常见的两种：余额不足、订单已被超时关闭。刷新一次状态让用户看到真实情况
        await loadOrder();
      } finally {
        paying.value = false;
      }
    }

    async function doCancel() {
      // 走 App.vue 里统一的取消入口：自带二次确认弹窗（取消会立即释放库存，不可恢复），
      // 成功后还会重拉订单 / 钱包 / 会员 / 秒杀名额，勾选额度立刻放开。
      await appCtx.cancelOrder(orderId.value);
      // 用户在弹窗里点了「再想想」则状态不变，此时留在收银台继续倒计时
      await loadOrder();
      if (!isPending.value) appCtx.navigate('orders');
    }

    onMounted(async () => {
      await loadOrder();
      tickTimer = setInterval(recomputeRemaining, 1000);
      startPoll();
    });

    onUnmounted(() => {
      if (tickTimer) clearInterval(tickTimer);
      stopPoll();
    });

    return {
      ...appCtx,
      order, loading, loadError, paying, remainingSec, mmss, totalQuantity,
      isPending, isPayable, stateClass, stateBadge, stateTitle, stateDesc,
      doPay, doCancel,
    };
  },
};
</script>
