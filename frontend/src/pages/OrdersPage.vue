<template>
<section class="data-panel">
        <div class="order-tabs">
          <button
            v-for="t in statusTabs"
            :key="t.key"
            type="button"
            class="order-tab"
            :class="{ on: orders.statusFilter === t.key }"
            @click="selectOrderTab(t.key)"
          >{{ t.label }}</button>
        </div>
        <skeleton-list
          v-if="orders.loading && !orders.items?.length"
          :count="4"
          :thumb="true"
          :lines="3"
          :tail="true"
        />
        <empty-state
          v-else-if="!orders.items?.length"
          icon="receipt"
          text="暂无订单，下单后可在这里跟踪发货和售后"
          action-text="去逛逛"
          @action="navigate('shop')"
        />
        <div v-for="order in orders.items" :key="order.id" class="list-row tall wrap">
          <div>
            <div class="order-head">
              <strong class="order-no-link" @click="openOrderDetail(order)">{{ order.orderNo }}</strong>
              <span :class="['tag', shipStatusOf(order).cls]">{{ shipStatusOf(order).label }}</span>
              <span :class="['tag', order.fulfillmentType === 'PICKUP' ? 'ok' : 'muted']">{{ fulfillmentLabel(order) }}</span>
              <span class="order-amount">{{ money(order.payAmount) }}</span>
            </div>
            <!-- 找订单的关键信息（2026-10-07 用户反馈：「光看一个订单编号很难找到想找的订单」）。
                 编号对人无意义，用户真正记得住的是「什么时候买的」「买了什么」。
                 最多列 3 件 —— 再多会把卡片撑高，反而更难扫。 -->
            <div class="order-goods" v-if="(order.items || []).length">
              <span v-for="it in (order.items || []).slice(0, 3)" :key="it.id" class="order-goods-item">
                <img v-if="it.productCoverUrl" :src="it.productCoverUrl" :alt="it.productName" loading="lazy" />
                <em>{{ it.productName }}</em><i>×{{ it.quantity }}</i>
              </span>
              <small v-if="order.items.length > 3" class="og-more">另有 {{ order.items.length - 3 }} 件</small>
            </div>
            <small class="order-time">下单 {{ formatDate(order.createdAt) }}</small>
            <small>{{ formatPaymentStatus(order.paymentStatus) }}<template v-if="orderSavedTotal(order) > 0"> · 已优惠 -{{ money(orderSavedTotal(order)) }}</template></small>
            <small v-if="order.status === 'PAID'" class="ship-hint">{{ order.fulfillmentType === 'PICKUP'
              ? '门店备货中，备好后凭自提码到店取货'
              : order.fulfillmentType === 'EXPRESS'
                ? '商家尚未发货，交由快递后可在订单详情查看单号'
                : '商家备货中，即将开始配送' }}</small>
            <small v-if="order.fulfillmentType === 'PICKUP'" class="ship-line">
              <span class="ship-flag">自提码</span>{{ order.pickupCode }} · {{ order.pickupStoreName }}
            </small>
            <small v-else-if="order.deliverySlot" class="ship-line">
              <span class="ship-flag">配送时段</span>{{ order.deliverySlot }}
            </small>
            <small v-if="order.shipNo" class="ship-line">
              <span class="ship-flag">已发货</span>{{ order.shipCompany }} {{ order.shipNo }}
            </small>
            <!-- 点7/点8：退款/售后状态显眼化 + 驳回可感知。
                 原先只是一行小字 tag-warn，用户根本注意不到「审核中/成功/被驳回」。
                 现在按状态给一张整宽彩色条：审核中(橙)/部分退款(橙)/退款成功(绿)/被驳回(红，并显式给出理由)。 -->
            <template v-if="order.refundStatus && order.refundStatus !== 'NONE'">
              <div :class="['refund-banner', refundBannerCls(order.refundStatus)]">
                <span class="rb-title">{{ formatRefundStatus(order.refundStatus) }}</span>
                <span v-if="order.refundStatus === 'REJECTED'" class="rb-sub">驳回理由：{{ order.refundRemark || order.refundReason || '未说明' }}</span>
                <span v-else-if="order.refundReason" class="rb-sub">原因：{{ order.refundReason }}</span>
                <span v-if="order.refundRemark && order.refundStatus !== 'REJECTED'" class="rb-sub">处理意见：{{ order.refundRemark }}</span>
              </div>
              <!-- 2026-10-22 用户反馈：待寄回还要点进订单详情才能填快递单，路径太深。
                   现在直接在卡片里内嵌「快递公司+运单号 → 提交寄回」表单（与详情页同款）。
                   按商品行并行售后可有多条待寄回，各给一条表单、带商品名区分；
                   提交成功后刷新列表，状态条自动翻成「已寄回」。 -->
              <div v-if="order.refundStatus === 'WAITING_SHIP'" class="card-shipback">
                <div v-if="shipBackInfo(order.id).loading" class="card-shipback-hint">正在加载售后信息…</div>
                <template v-else-if="shipBackInfo(order.id).waiting.length">
                  <form
                    v-for="r in shipBackInfo(order.id).waiting"
                    :key="'card-ship-' + r.id"
                    class="shipback-form"
                    @submit.prevent="submitShipBackCard(order.id, r)"
                  >
                    <div class="shipback-fields">
                      <span v-if="shipBackInfo(order.id).waiting.length > 1" class="card-ship-item" :title="r.itemProductName || ''">{{ r.itemProductName || '商品' }}</span>
                      <input v-model.trim="shipInputFor(r.id).company" placeholder="快递公司（如顺丰、中通）" maxlength="40" />
                      <input v-model.trim="shipInputFor(r.id).trackingNo" placeholder="运单号" maxlength="64" />
                      <!-- 2026-10-22 修「点完没反应」：原先空输入时按钮 disabled，但 .primary.sm
                           没有 disabled 视觉样式，用户看着可点、点了静默无效。
                           现在按钮恒可点，缺字段时在提交函数里 fail 弹明确提示；提交中才禁用+换文案。 -->
                      <button type="submit" class="primary sm" :disabled="shipSubmitting[r.id]">
                        {{ shipSubmitting[r.id] ? '提交中…' : '提交寄回' }}
                      </button>
                    </div>
                  </form>
                </template>
                <div v-else-if="shipBackInfo(order.id).error" class="card-shipback-hint">未能加载售后信息，<a href="javascript:;" class="order-no-link" @click="openOrderDetail(order)">到订单详情提交寄回</a></div>
              </div>
            </template>
          </div>
          <div class="row-actions">
            <!-- 付款一律先进收银台（/pay/:id）看一眼倒计时再决定，不再这里直接扣款。
                 订单详情里同样有入口，两条路都不会让人进入「退出后就找不到付款页」的死角。 -->
            <button v-if="order.status === 'PENDING_PAYMENT'" @click="navigate('pay', { id: order.id })">去付款</button>
            <button v-if="order.status === 'SHIPPED'" @click="confirmReceipt(order.id)">确认收货</button>
            <!-- 2026-10-15 按商品行并行售后：整单「申请退款/售后」按钮跳订单详情
                 （详情页才有逐商品行入口，同一订单不同商品可各自并行发起售后）。
                 2026-10-16 带 intent=refund：落地详情页后自动展开退款表单并定位，
                 不再让用户「列表点一次 → 详情页滚到底再点一次」。 -->
            <button
              v-if="(order.status === 'PAID' || order.status === 'SHIPPED' || order.status === 'COMPLETED') && (order.refundStatus === 'NONE' || order.refundStatus === 'REJECTED')"
              class="ghost"
              @click="openOrderDetail(order, { intent: 'refund' })"
            >{{ (order.refundStatus === 'REJECTED' ? '重新' : '') + (order.status === 'COMPLETED' ? '申请售后' : '申请退款') }}</button>
            <button v-if="order.status === 'PENDING_PAYMENT'" class="ghost" @click="cancelOrder(order.id)">取消订单</button>
            <button v-if="order.status === 'COMPLETED' && canReview(order)" class="ghost" @click="openReviewForm(order)">评价</button>
            <!-- 复购入口：生鲜的核心就是周期性买同样的东西。已付款之后的订单都该能一键回填购物车 -->
            <button v-if="order.status !== 'PENDING_PAYMENT'" class="ghost" @click="reorder(order)">再来一单</button>
          </div>
          <div v-if="reviewForm.orderId === order.id" class="row-extra review-grid">
            <!-- 一笔订单含多个商品：每个未评价的商品各一个独立评价表单（评分/内容/图片互不影响） -->
            <div v-for="it in (order.items || []).filter(i => !reviewedItemMap[i.id])" :key="it.id" class="review-item">
              <div class="review-item-head">
                <img v-if="it.productCoverUrl" :src="it.productCoverUrl" :alt="it.productName" class="review-thumb" loading="lazy" />
                <strong>{{ it.productName }}</strong><i>×{{ it.quantity }}</i>
              </div>
              <select v-model.number="reviewForm.items[it.id].rating" class="rating-select">
                <option :value="5">★★★★★ 非常满意</option>
                <option :value="4">★★★★ 满意</option>
                <option :value="3">★★★ 一般</option>
                <option :value="2">★★ 不满意</option>
                <option :value="1">★ 很差</option>
              </select>
              <input v-model="reviewForm.items[it.id].content" placeholder="写下你的评价（选填）" />
              <image-upload v-model="reviewForm.items[it.id].images" type="review" multiple :max="9" />
            </div>
            <div class="review-actions">
              <button @click="submitReview(order.id)">提交评价</button>
              <button class="ghost" @click="reviewForm.orderId = null">取消</button>
            </div>
          </div>
        </div>
      </section>
      <Pager
        :page="orders.page"
        :total="orders.total"
        :size="orders.size"
        :loading="orders.loading"
        @go="onOrderPage"
      />
</template>

<script>
import { inject, reactive, watch } from 'vue';
import { orderSavedTotal } from '../utils/format';
export default {
  name: 'OrdersPage',
  setup() {
    const appCtx = inject('appCtx');
    const onOrderPage = (page) => {
      appCtx.loadOrdersPage(page);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    };
    // 条目12：订单状态筛选 tab。后端 /orders 已支持 status 参数；
    // 售后（AFTER_SALE）是虚拟分类 —— 后端按 refundStatus != NONE 命中，
    // 这类订单的 order.status 可能是 PAID/SHIPPED/COMPLETED，无法用单一值表达。
    const statusTabs = [
      { key: '', label: '全部' },
      { key: 'PENDING_PAYMENT', label: '待付款' },
      { key: 'PAID', label: '待发货' },
      { key: 'SHIPPED', label: '待收货' },
      { key: 'COMPLETED', label: '已完成' },
      { key: 'AFTER_SALE', label: '售后' },
    ];
    function selectOrderTab(key) {
      if (appCtx.orders.statusFilter === key) return;
      appCtx.orders.statusFilter = key;
      appCtx.loadOrders(true);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
    // 订单里还有「未评价」的商品时才显示「评价」按钮（已全评完的订单不再显示）
    const canReview = (order) => (order.items || []).some((it) => !appCtx.reviewedItemMap[it.id]);
    // 点7/点8：退款状态条配色（审核中/待寄回/部分退款 橙、已寄回 蓝、成功 绿、被驳回 红）
    const refundBannerCls = (status) => ({
      APPLYING: 'refund-applying', WAITING_SHIP: 'refund-applying', PARTIAL: 'refund-partial',
      SHIPPED_BACK: 'refund-shipped', APPROVED: 'refund-approved', REJECTED: 'refund-rejected',
    }[status] || 'refund-applying');
    // ===== 2026-10-22 卡片内嵌寄回表单 =====
    // 订单列表 DTO 不带售后单 id，这里对 refundStatus=WAITING_SHIP 的订单按需
    // 调 GET /returns/order/{id} 取「待寄回」售后单（通常 0~2 条，量小可接受）。
    const shipBackMap = reactive({});      // orderId -> { loading, loaded, error, waiting: [...] }
    const shipInputs = reactive({});       // returnId -> { company, trackingNo }
    const shipSubmitting = reactive({});   // returnId -> boolean
    function shipBackInfo(orderId) {
      if (!shipBackMap[orderId]) shipBackMap[orderId] = { loading: false, loaded: false, error: false, waiting: [] };
      return shipBackMap[orderId];
    }
    function shipInputFor(returnId) {
      if (!shipInputs[returnId]) shipInputs[returnId] = { company: '', trackingNo: '' };
      return shipInputs[returnId];
    }
    async function ensureShipBack(orderId) {
      const info = shipBackInfo(orderId);
      if (info.loading || info.loaded) return;
      info.loading = true;
      try {
        const res = await appCtx.api.get(`/returns/order/${orderId}`);
        const list = Array.isArray(res) ? res : (res?.items || []);
        info.waiting = list.filter((r) => r.status === 'WAITING_SHIP');
        info.loaded = true;
        info.error = false;
      } catch (e) {
        info.waiting = [];   // 失败时卡片显示「到订单详情提交寄回」兜底
        info.loaded = true;
        info.error = true;
      } finally {
        info.loading = false;
      }
    }
    watch(() => appCtx.orders.items, (items) => {
      (items || []).forEach((o) => {
        if (o.refundStatus === 'WAITING_SHIP') ensureShipBack(o.id);
      });
    }, { immediate: true });
    async function submitShipBackCard(orderId, r) {
      const f = shipInputFor(r.id);
      if (shipSubmitting[r.id]) return;
      // 空字段不再用 disabled 静默拦（按钮看不出禁用态，用户点了以为坏了）——
      // 改为点了就给明确提示。
      if (!f.company || !f.trackingNo) {
        appCtx.fail?.(!f.company && !f.trackingNo
          ? '请先填写快递公司和运单号'
          : (!f.company ? '请先填写快递公司' : '请先填写运单号'));
        return;
      }
      shipSubmitting[r.id] = true;
      try {
        await appCtx.api.post(`/returns/${r.id}/ship-back`, {
          expressCompany: f.company,
          trackingNo: f.trackingNo,
        });
        appCtx.flash?.('寄回信息已提交，商家确认收到后会为你退款');
        f.company = '';
        f.trackingNo = '';
        // 本地先行移除该售后单（已变 SHIPPED_BACK，表单应立即消失），
        // ⚠️ 刷新订单列表必须用 loadOrdersPage(当前页) —— loadOrders(false) 是「追加下一页」，
        //    不会刷新当前页的 refundStatus，状态条会一直卡在「待寄回」。
        const info = shipBackInfo(orderId);
        info.waiting = info.waiting.filter((x) => x.id !== r.id);
        info.loaded = true;
        info.error = false;
        await appCtx.loadOrdersPage(appCtx.orders.page || 1);
      } catch (e) {
        appCtx.fail?.(e?.message || '提交失败');
      } finally {
        shipSubmitting[r.id] = false;
      }
    }
    return { ...appCtx, orderSavedTotal, onOrderPage, canReview, statusTabs, selectOrderTab, refundBannerCls, shipBackInfo, shipInputFor, shipSubmitting, submitShipBackCard };
  }
};
</script>
