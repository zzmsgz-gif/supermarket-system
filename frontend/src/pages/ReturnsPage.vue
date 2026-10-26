<template>
  <section class="data-panel return-page">
    <div class="panel-head">
      <h3>我的售后</h3>
      <small>收到货后如有问题可申请退货退款；商家同意后按提示寄回即可</small>
    </div>

    <!-- 流程说明：这条链路长，用户最怕不知道下一步做什么 -->
    <div class="rp-steps">
      <span :class="{ on: true }">1 提交申请</span>
      <span :class="{ on: true }">2 商家审核</span>
      <span :class="{ on: true }">3 按需寄回</span>
      <span>4 确认收货</span>
      <span>5 退款到账</span>
    </div>

    <div v-if="loading" class="sk-stack" aria-hidden="true">
      <skeleton-list :count="3" :thumb="true" :lines="2" :tail="true" />
    </div>
    <div v-else-if="!rows.length" class="empty">还没有售后申请</div>

    <template v-else>
      <div v-for="r in rows" :key="r.id" class="rp-row">
        <div class="rpr-head">
          <span :class="['tag', statusCls(r.status)]">{{ r.statusLabel }}</span>
          <strong class="rpr-refund">实退 {{ money(r.refundAmount) }}</strong>
        </div>

        <!-- 在途态：提示本单冻结了积分，让方案D-C 可见（冻结额>0 才显示） -->
        <p class="rpr-frozen" v-if="isOpen(r.status) && (r.frozenPoints || 0) > 0">
          本单已冻结 <b>{{ r.frozenPoints }} 分</b> 发放积分（退款审核中暂不可用，驳回或退款后自动处理）
        </p>

        <!-- 方案D-B：已花掉、被现金退款等价扣回的发放积分 —— 仅当退款时已花掉部分发放积分时展示 -->
        <div class="rpr-deduct" v-if="(r.pointsDeducted || 0) > 0">
          <div class="rpr-deduct-row">
            <span>应退金额</span>
            <span>{{ money(Number(r.refundAmount || 0) + Number(r.pointsDeducted || 0) / 100) }}</span>
          </div>
          <div class="rpr-deduct-row minus">
            <span>已花积分等价扣回</span>
            <span>-{{ money(Number(r.pointsDeducted || 0) / 100) }}</span>
          </div>
          <div class="rpr-deduct-row total">
            <span>实退金额</span>
            <span>{{ money(r.refundAmount) }}</span>
          </div>
        </div>

        <!-- 下一步提示：直接算好给用户看，不让他猜 -->
        <p class="rpr-next">{{ r.nextStep }}</p>

        <!-- 商品行信息（2026-10-15 按商品行并行售后）：逐行售后单带商品快照；整单售后没有则隐藏 -->
        <div v-if="r.itemProductName" class="rpr-item">
          <img v-if="r.itemCoverUrl" :src="r.itemCoverUrl" class="rpr-item-img" alt="商品图片" loading="lazy" decoding="async" />
          <div class="rpr-item-info">
            <div class="rpr-item-name">{{ r.itemProductName }}</div>
            <div class="rpr-item-meta">
              <span>{{ money(r.itemUnitPrice) }}</span>
              <span>× {{ r.itemQuantity }}</span>
              <span class="subtotal">{{ money(r.itemSubtotal) }}</span>
            </div>
          </div>
        </div>

        <div class="rpr-meta">
          <span v-if="r.orderNo" class="rpr-order" @click="goOrder(r.orderId)">订单 {{ r.orderNo }}</span>
          <span v-if="r.reason">原因：{{ r.reason }}</span>
          <span v-if="r.adminRemark">商家说明：{{ r.adminRemark }}</span>
        </div>

        <!-- 待寄回：填快递信息 -->
        <form v-if="r.status === 'WAITING_SHIP'" class="rp-ship" @submit.prevent="submitShip(r.id)">
          <input v-model.trim="shipForm.company" placeholder="快递公司（如：顺丰）" maxlength="40" />
          <input v-model.trim="shipForm.trackingNo" placeholder="运单号" maxlength="64" />
          <!-- 2026-10-22：去掉字段级 disabled（.primary.sm 无禁用样式，空字段点按钮静默无反应像坏了），
               改为提交时校验并弹明确提示；提交中禁用+换文案防重复提交。 -->
          <button class="primary sm" type="submit" :disabled="shipSubmitting">
            {{ shipSubmitting ? '提交中…' : '提交寄回信息' }}
          </button>
        </form>

        <!-- 已寄回：显示快递信息 -->
        <div v-else-if="r.trackingNo" class="rp-shipped">
          <span class="ship-flag">已寄回</span>
          {{ r.expressCompany }} {{ r.trackingNo }}
          <small>{{ formatDate(r.shippedBackAt) }}</small>
        </div>

        <!-- 驳回：给申诉出口 + 重新申请（驳回不是终态，可修正后再次发起） -->
        <div v-if="r.status === 'REJECTED'" class="rp-reject">
          <button class="ghost sm" type="button" @click="contactSupport">联系客服申诉</button>
          <button class="primary sm" type="button" @click="reapplyRefund(r.orderId)">重新申请售后</button>
        </div>
      </div>

      <!-- 与订单页同款分页（Pager），数字页码样式全站统一 -->
      <Pager :page="page" :total="total" :size="pageSize" :loading="loading" @go="goPage" />
    </template>
  </section>
</template>

<script>
import { inject, ref, reactive, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { money, formatDate } from '../utils/format.js';
import Pager from '../components/Pager.vue';

/**
 * 我的售后（第 13 条）。
 *
 * 流程：提交申请 → 商家审核（判责、决定要不要寄回）→ 用户寄回 →
 *      商家确认收货 → 退款到账。
 * 退货运费统一按平台规则处理（2026-10-22 起不再区分运费承担方）。
 * 每一步的「现在轮到谁做什么」由后端 nextStep 字段算好直接显示 ——
 * 中间态最容易让用户卡住，文案不该由前端猜。
 */
export default {
  name: 'ReturnsPage',
  components: { Pager },
  setup() {
    const appCtx = inject('appCtx');
    const router = useRouter();
    const api = appCtx?.api;

    const rows = ref([]);
    const loading = ref(false);
    const page = ref(1);
    const pageSize = 10;
    const total = ref(0);
    const shipForm = reactive({ company: '', trackingNo: '' });

    const totalPages = computed(() => Math.max(1, Math.ceil((total.value || 0) / pageSize)));

    async function load() {
      if (!api) return;
      loading.value = true;
      try {
        const res = await api.get(`/returns?page=${page.value}&size=${pageSize}`);
        rows.value = Array.isArray(res?.items) ? res.items : [];
        total.value = Number(res?.total) || 0;
      } catch (e) {
        rows.value = [];
        total.value = 0;
      } finally {
        loading.value = false;
      }
    }

    const shipSubmitting = ref(false);
    async function submitShip(id) {
      if (shipSubmitting.value) return;
      if (!shipForm.company || !shipForm.trackingNo) {
        appCtx?.fail?.(!shipForm.company && !shipForm.trackingNo
          ? '请先填写快递公司和运单号'
          : (!shipForm.company ? '请先填写快递公司' : '请先填写运单号'));
        return;
      }
      shipSubmitting.value = true;
      try {
        await api.post(`/returns/${id}/ship-back`, {
          expressCompany: shipForm.company,
          trackingNo: shipForm.trackingNo,
        });
        shipForm.company = '';
        shipForm.trackingNo = '';
        appCtx?.flash?.('寄回信息已提交，商家确认收到后会为你退款');
        load();
      } catch (e) {
        appCtx?.fail?.(e?.message || '提交失败');
      } finally {
        shipSubmitting.value = false;
      }
    }

    function statusCls(status) {
      return {
        APPLYING: 'warn',
        WAITING_SHIP: 'warn',
        SHIPPED_BACK: 'info',
        APPROVED: 'ok',
        REJECTED: 'muted',
      }[status] || 'muted';
    }

    // 在途态：已提交、商家还没完成退款的三种状态（与后端 OPEN_STATUSES 同源：APPLYING/WAITING_SHIP/SHIPPED_BACK）
    function isOpen(status) {
      return status === 'APPLYING' || status === 'WAITING_SHIP' || status === 'SHIPPED_BACK';
    }

    // Pager 发出的是 `go` 事件，载荷是绝对页码（不是 ±1 增量）。
    function goPage(n) {
      const next = Math.min(totalPages.value, Math.max(1, Number(n) || 1));
      if (next === page.value) return;
      page.value = next;
      // 翻页先平滑滚回顶部：和订单页一致的交互。
      window.scrollTo({ top: 0, behavior: 'smooth' });
      // 兜底：若下一页内容比当前页短（高度骤降），平滑滚动可能被截断卡在半路；
      // 数据加载完后再确认一次是否已在顶部，没到顶就补一次归零，保证一定回到顶部。
      load().finally(() => {
        if (window.scrollY > 0) window.scrollTo({ top: 0, behavior: 'auto' });
      });
    }

    function goOrder(orderId) {
      if (orderId) router.push({ name: 'orderDetail', params: { id: String(orderId) } });
    }

    // 驳回态「重新申请售后」：与我的订单页对齐 —— 直接进入订单详情并自动展开底部退款表单
    // （传 intent:'refund' 让 OrderDetailPage 消费 ?refund=1 自动展开）。
    // 原先只普通跳转到详情页，用户还得自己滚到底再点一次「申请售后」，体验割裂。
    function reapplyRefund(orderId) {
      if (orderId) appCtx.openOrderDetail({ id: orderId }, { intent: 'refund' });
    }

    function contactSupport() {
      appCtx?.flash?.('请联系客服处理，客服电话见「帮助中心」');
    }

    onMounted(load);

    return { rows, loading, page, pageSize, total, totalPages, shipForm, shipSubmitting,
             submitShip, goPage, goOrder, reapplyRefund, contactSupport, statusCls, isOpen, money, formatDate };
  },
};
</script>
