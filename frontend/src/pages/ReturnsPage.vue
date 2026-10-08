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

    <div v-if="loading" class="empty">加载中…</div>
    <div v-else-if="!rows.length" class="empty">还没有售后申请</div>

    <template v-else>
      <div v-for="r in rows" :key="r.id" class="rp-row">
        <div class="rpr-head">
          <span :class="['tag', statusCls(r.status)]">{{ r.statusLabel }}</span>
          <strong>退款 ¥{{ money(r.refundAmount) }}</strong>
          <small v-if="r.freightBorneBy" class="rpr-freight">{{ r.freightLabel }}</small>
        </div>

        <!-- 下一步提示：直接算好给用户看，不让他猜 -->
        <p class="rpr-next">{{ r.nextStep }}</p>

        <div class="rpr-meta">
          <span v-if="r.orderNo" class="rpr-order" @click="goOrder(r.orderId)">订单 {{ r.orderNo }}</span>
          <span v-if="r.reason">原因：{{ r.reason }}</span>
          <span v-if="r.adminRemark">商家说明：{{ r.adminRemark }}</span>
        </div>

        <!-- 待寄回：填快递信息 -->
        <form v-if="r.status === 'WAITING_SHIP'" class="rp-ship" @submit.prevent="submitShip(r.id)">
          <input v-model.trim="shipForm.company" placeholder="快递公司（如：顺丰）" maxlength="40" />
          <input v-model.trim="shipForm.trackingNo" placeholder="运单号" maxlength="64" />
          <button class="primary sm" type="submit" :disabled="!shipForm.company || !shipForm.trackingNo">
            提交寄回信息
          </button>
          <small v-if="r.freightBorneBy === 'SELLER'" class="rp-hint-good">
            退货运费由商家承担，放心寄出
          </small>
          <small v-else-if="r.freightBorneBy === 'BUYER'" class="rp-hint-warn">
            七天无理由退货运费需你自行承担
          </small>
        </form>

        <!-- 已寄回：显示快递信息 -->
        <div v-else-if="r.trackingNo" class="rp-shipped">
          <span class="ship-flag">已寄回</span>
          {{ r.expressCompany }} {{ r.trackingNo }}
          <small>{{ formatDate(r.shippedBackAt) }}</small>
        </div>

        <!-- 驳回：给申诉出口 -->
        <div v-if="r.status === 'REJECTED'" class="rp-reject">
          <button class="ghost sm" type="button" @click="contactSupport">联系客服申诉</button>
        </div>
      </div>

      <AdminPager :page="page" :total-pages="totalPages" @change-page="changePage" />
    </template>
  </section>
</template>

<script>
import { inject, ref, reactive, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { money, formatDate } from '../utils/format.js';
import AdminPager from '../components/AdminPager.vue';

/**
 * 我的售后（第 13 条）。
 *
 * 流程：提交申请 → 商家审核（判责、决定要不要寄回、运费谁出）→ 用户寄回 →
 *      商家确认收货 → 退款到账。
 * 每一步的「现在轮到谁做什么」由后端 nextStep 字段算好直接显示 ——
 * 中间态最容易让用户卡住，文案不该由前端猜。
 */
export default {
  name: 'ReturnsPage',
  components: { AdminPager },
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

    async function submitShip(id) {
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
        appCtx?.flash?.(e?.message || '提交失败');
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

    function changePage(p) {
      page.value = p;
      load();
    }

    function goOrder(orderId) {
      if (orderId) router.push({ name: 'orderDetail', params: { id: String(orderId) } });
    }

    function contactSupport() {
      appCtx?.flash?.('请联系客服处理，客服电话见「帮助中心」');
    }

    onMounted(load);

    return { rows, loading, page, totalPages, shipForm,
             submitShip, changePage, goOrder, contactSupport, statusCls, money, formatDate };
  },
};
</script>
