<template>
  <section class="data-panel pay-cashier">
    <div class="panel-head">
      <button class="ghost" @click="navigate('orders')">返回我的订单</button>
    </div>

    <!-- 加载 / 异常 -->
    <div v-if="loading" class="sk-stack" aria-hidden="true">
      <skeleton-lines :lines="3" :title="true" />
      <skeleton-list :count="1" :thumb="true" :lines="2" :tail="true" />
      <skeleton-lines :lines="2" />
    </div>
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

      <!-- 金额明细。
           ⚠️ 这里原先只渲染了「优惠券抵扣」和「积分抵扣」两项，导致用户反馈
           「我 200-50 他就没显示出来」——**满减属于 activityDiscount 字段，
           本页从头到尾没有渲染它**，等于金额对不上（下方实付却是减过的）。
           现在补齐四类，与订单详情页/后台的口径一致：
             · discountAmount   优惠券抵扣
             · activityDiscount 活动优惠（满减/秒杀折扣）
             · memberDiscount   会员等级优惠
             · pointsDiscount   积分抵扣
           会员优惠「已含在商品单价里、不重复扣减」—— 这句话要显示，否则用户会以为漏算。 -->
      <div class="pay-summary">
        <div class="row"><span>商品合计</span><strong>{{ money(order.totalAmount) }}</strong></div>
        <div class="row"><span>运费</span><strong>{{ money(order.freightAmount) }}</strong></div>
        <div v-if="num(order.discountAmount) > 0" class="row">
          <span>{{ order.couponName || '优惠券' }}抵扣</span>
          <strong class="minus">- {{ money(order.discountAmount) }}</strong>
        </div>
        <div v-if="num(order.activityDiscount) > 0" class="row">
          <span>活动优惠</span>
          <strong class="minus">- {{ money(order.activityDiscount) }}</strong>
        </div>
        <div v-if="num(order.memberDiscount) > 0" class="row">
          <span>会员优惠<small class="sum-note">已含在商品单价里，不重复扣减</small></span>
          <strong class="minus">- {{ money(order.memberDiscount) }}</strong>
        </div>
        <div v-if="num(order.pointsDiscount) > 0" class="row">
          <span>积分抵扣</span>
          <strong class="minus">- {{ money(order.pointsDiscount) }}</strong>
        </div>
      </div>

      <!-- 操作区 -->
      <div class="pay-actions">
        <template v-if="isPending">
          <div class="pay-methods">
            <h4>选择支付方式</h4>
            <div class="pm-list">
              <button
                v-for="m in payMethods"
                :key="m.key"
                type="button"
                class="pm-item"
                :class="{ on: selectedMethod === m.key }"
                @click="selectMethod(m)"
              >
                <span class="pm-name">{{ m.label }}</span>
                <span class="pm-hint">{{ m.hint }}</span>
                <!-- 余额支付：把「账户余额 / 余额充足」提示搬到这里（2026-10-12 用户要求：
                     原来在结算页底部摘要里，位置太远；余额是余额支付这一项最相关的信息，
                     应当跟随「余额支付」选项一起展示）。文案压短，保证三张卡单行等高不换行。 -->
                <span v-if="m.key === 'BALANCE'" class="pm-balance" :class="{ insufficient: !balanceSufficient }">
                  余额 {{ money(wallet.balance) }} · <b :class="balanceSufficient ? 'text-ok' : 'text-warn'">{{ balanceSufficient ? '充足' : '不足' }}</b>
                </span>
                <span class="pm-radio" :class="{ on: selectedMethod === m.key }"></span>
              </button>
            </div>
          </div>
          <!-- 立即支付 / 取消订单：移到右下角（2026-10-12 用户要求） -->
          <div class="pay-action-row">
            <button class="ghost" :disabled="paying" @click="doCancel">取消订单</button>
            <button class="primary" :class="{ loading: paying }" :disabled="paying" @click="doPay">
              <span v-if="paying" class="spinner"></span>
              <span>{{ paying ? '支付处理中…' : '立即支付 ' + money(order.payAmount) }}</span>
            </button>
          </div>
        </template>
        <template v-else>
          <button class="primary" @click="navigate('shop')">继续挑选商品</button>
          <button class="ghost" @click="navigate('orders')">查看我的订单</button>
        </template>
      </div>
    </template>

    <!-- 微信/支付宝模拟扫码弹窗（与充值页同款交互）：展示装饰性二维码，
         点「已完成支付」才真正向后端发起模拟支付。 -->
    <div v-if="qrChannel" class="pay-qr-modal" @click.self="closeQrModal">
      <div class="pay-qr-card">
        <div class="pay-head">
          <span class="pay-method" :class="qrChannel === 'ALIPAY' ? 'alipay' : 'wechat'">{{ qrMethodLabel }}</span>
          <h3>向「超市购物系统」付款</h3>
        </div>
        <div class="pay-amount">{{ money(order.payAmount) }}</div>
        <div class="pay-qr">
          <div class="qr-frame" v-html="qrSvg"></div>
          <p>请使用{{ qrMethodLabel }}扫一扫付款</p>
        </div>
        <div class="pay-qr-actions">
          <button class="primary" :class="{ loading: paying }" :disabled="paying" @click="confirmQrPay">
            <span v-if="paying" class="spinner"></span>
            <span>{{ paying ? '支付处理中…' : '已完成支付' }}</span>
          </button>
          <button class="ghost" :disabled="paying" @click="closeQrModal">取消</button>
        </div>
      </div>
    </div>

    <!-- 支付成功动画：从支付成功到跳转订单详情之间，给用户明确的「成了」反馈（#539） -->
    <transition name="ps-fade">
      <div v-if="paySuccess" class="pay-success-overlay">
        <div class="ps-card">
          <svg class="ps-check" viewBox="0 0 52 52">
            <circle class="ps-check-circle" cx="26" cy="26" r="24" fill="none"/>
            <path class="ps-check-mark" fill="none" d="M14 27 l8 8 l16 -18"/>
          </svg>
          <div class="ps-title">支付成功</div>
          <div class="ps-sub">正在跳转到订单详情…</div>
        </div>
      </div>
    </transition>
  </section>
</template>

<script>
import { inject, ref, computed, onMounted, onUnmounted } from 'vue';
import { useRoute } from 'vue-router';
import { withMinSpinner } from '../utils/format.js';

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
    // 金额比较统一走它：接口可能给 null（未参与某项优惠时），直接 Number(null)===0 没问题，
    // 但 `order.x ? order.x : 0` 这种写法会把「有值但为 0」和「无值」混在一起，容易写错判断。
    const num = (v) => Number(v || 0);
    const loading = ref(true);
    const loadError = ref('');
    const paying = ref(false);
    // 支付成功后、跳转订单详情之前的「支付成功」动画开关（#539）
    const paySuccess = ref(false);
    // 当前选中的支付方式（默认余额）。
    const selectedMethod = ref('BALANCE');
    // 余额支付已就绪；微信/支付宝为**模拟支付**（系统暂无真实商户网关），
    // 与充值流程同理：点「立即支付」即模拟网关成功、标记订单已支付，不从钱包扣款。
    const payMethods = [
      { key: 'BALANCE', label: '余额支付', hint: '从钱包余额扣款' },
      { key: 'WECHAT', label: '微信支付', hint: '扫码支付' },
      { key: 'ALIPAY', label: '支付宝', hint: '扫码支付' },
    ];
    function selectMethod(m) {
      selectedMethod.value = m.key;
    }
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

    // 微信/支付宝模拟扫码弹窗（2026-10-12 用户要求：跟充值功能一样，点支付先弹模拟二维码）。
    // qrChannel 为 null 表示弹窗关闭；WECHAT/ALIPAY 表示正在展示对应渠道的扫码页。
    const qrChannel = ref(null);
    /** 根据种子生成一张装饰性「二维码」SVG（与充值页同款，非真实二维码，仅模拟扫码页） */
    function buildQrSvg(seed) {
      const size = 21;
      const cell = 8;
      let hash = 0;
      for (let i = 0; i < seed.length; i += 1) hash = (hash * 31 + seed.charCodeAt(i)) >>> 0;
      const rand = () => {
        hash = (hash * 1103515245 + 12345) >>> 0;
        return hash / 4294967296;
      };
      let rects = '';
      for (let y = 0; y < size; y += 1) {
        for (let x = 0; x < size; x += 1) {
          if (rand() > 0.5) {
            rects += `<rect x="${x * cell}" y="${y * cell}" width="${cell}" height="${cell}" />`;
          }
        }
      }
      // 三个定位角
      const finder = (ox, oy) => `<rect x="${ox * cell}" y="${oy * cell}" width="${cell * 7}" height="${cell * 7}" fill="#fff"/><rect x="${ox * cell}" y="${oy * cell}" width="${cell * 7}" height="${cell * 7}" fill="none" stroke="#1f2933" stroke-width="${cell}"/><rect x="${(ox + 2) * cell}" y="${(oy + 2) * cell}" width="${cell * 3}" height="${cell * 3}" fill="#1f2933"/>`;
      const svg = `<svg viewBox="0 0 ${size * cell} ${size * cell}" xmlns="http://www.w3.org/2000/svg" fill="#1f2933">${rects}${finder(0, 0)}${finder(size - 7, 0)}${finder(0, size - 7)}</svg>`;
      return svg;
    }
    // 订单号 + 渠道做种子：同一单的微信码和支付宝码长得不一样，更像真的
    const qrSvg = computed(() => buildQrSvg(`${order.value?.orderNo || 'ORDER'}:${qrChannel.value || 'X'}`));
    const qrMethodLabel = computed(() => (qrChannel.value === 'WECHAT' ? '微信支付' : qrChannel.value === 'ALIPAY' ? '支付宝' : ''));

    function doPay() {
      if (paying.value || !isPayable.value) return;
      // 微信/支付宝：先弹模拟二维码扫码页，用户点「已完成支付」才真正走支付
      if (selectedMethod.value === 'WECHAT' || selectedMethod.value === 'ALIPAY') {
        qrChannel.value = selectedMethod.value;
        return;
      }
      return submitPay(selectedMethod.value);
    }

    function closeQrModal() {
      if (!paying.value) qrChannel.value = null;
    }

    async function confirmQrPay() {
      const channel = qrChannel.value;
      if (!channel) return;
      await submitPay(channel);
      // submitPay 成功会跳转订单详情；失败时留在收银台并关掉弹窗让用户重试
      qrChannel.value = null;
    }

    async function submitPay(channel) {
      // 余额通道走钱包扣款；微信/支付宝为模拟支付 —— 都只需把所选渠道带给后端，
      // 后端据此标记订单已支付（模拟渠道不从钱包扣款）。
      paying.value = true;
      try {
        // ⚠️ **跳转必须放在 withMinSpinner 之外、之后**，绝不能塞进回调里。
        // 之前 navigate('orders') 写在回调内部 → 页面立刻跳转、组件卸载 → spinner 被随之移除，
        // 用户只看到 25ms，与 MIN_SPINNER_MS 设多大完全无关（设 3000ms 也一样）。
        // 所以这里只让「支付 + 各项数据重拉」占满最短时长，跳转留到转圈真正转完之后。
        await withMinSpinner(async () => {
          await appCtx.api.post(`/orders/${orderId.value}/pay?channel=${encodeURIComponent(channel)}`);
          // 付款后各项额度都变了：订单列表、钱包、会员成长值、秒杀名额都要重拉。
          // 商品库存/销量同样在付款这一刻才结算 —— 漏掉它们，回到列表页看到的还是付款前的旧值。
          await Promise.all([
            appCtx.loadOrders(),
            appCtx.loadWallet(),
            appCtx.loadMe(),
            appCtx.loadMemberProfile(),
            appCtx.loadFlashSales(),
            appCtx.loadProducts(),
          ]);
          await appCtx.refreshProductDetail();
        });
        // 转圈转够了再离开收银台：否则用户看到的是「按钮闪一下就没了」，仍会以为点击被吞。
        //
        // 跳「订单详情」而不是订单列表（2026-10-07 用户要求）：付完最想知道的是
        // 「这一单到底怎么样了」——付了多少、还剩几天送达、有没有售后入口，详情页一次看全。
        //
        // ⚠️ 必须先把详情数据填进 appCtx.orderDetail.data 再跳：OrderDetailPage 靠这份数据
        // 渲染，而它 onMounted 只启动倒计时、**不自己拉订单**。直接 navigate 会白屏一瞬
        // （甚至一直空着），因为没有第二个地方会去补这次请求。
        const paidOrderId = orderId.value;
        const fresh = await appCtx.api.get(`/orders/${paidOrderId}`).catch(() => null);
        if (fresh) appCtx.orderDetail.data = fresh;
        // 「支付成功」动画：从支付成功到跳转订单详情之间，给用户一个明确的「成了」反馈（#539）。
        // 先关掉可能存在的扫码弹窗，再放动画，约 1.3s 后跳详情页。
        qrChannel.value = null;
        paySuccess.value = true;
        await new Promise((r) => setTimeout(r, 1300));
        appCtx.navigate('orderDetail', { id: paidOrderId });
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
      doPay, doCancel, num, selectedMethod, payMethods, selectMethod,
      qrChannel, qrSvg, qrMethodLabel, confirmQrPay, closeQrModal, paySuccess,
    };
  },
};
</script>
