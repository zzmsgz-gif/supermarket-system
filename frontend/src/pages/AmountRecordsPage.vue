<template>
  <section class="data-panel amount-page">
    <div class="panel-head">
      <h3>金额明细</h3>
      <small>只统计真正的资金进出 —— 运费、优惠已包含在订单实付里，不重复计</small>
    </div>

    <!-- 汇总：累计支出（红）/ 累计收入（绿） -->
    <div class="ar-summary two">
      <div class="ars-item">
        <span>累计支出</span>
        <b class="amt-out">{{ money(summary.out) }}</b>
      </div>
      <div class="ars-item">
        <span>累计收入</span>
        <b class="amt-in">{{ money(summary.income) }}</b>
      </div>
    </div>

    <div v-if="loading" class="empty">加载中…</div>
    <div v-else-if="!rows.length" class="empty">还没有资金流水</div>

    <template v-else>
      <!-- 不用筛选 tab：支出红 -、收入绿 +，颜色本身就是分类（2026-10-09）。
           运费/优惠/积分**不单列** —— 它们已经包含在订单实付金额里，
           再拎出来加一遍就是重复计算（实付 88 已含运费 8，加起来变 96）。 -->
      <div v-for="r in rows" :key="r.id" class="ar-row">
        <div class="arr-main">
          <div class="arr-title">
            <span class="ar-type">{{ r.typeLabel }}</span>
            <strong :class="r.direction === 1 ? 'amt-out' : 'amt-in'">
              {{ r.direction === 1 ? '-' : '+' }}{{ money(r.amount) }}
            </strong>
          </div>
          <div class="arr-meta">
            <span v-if="r.orderNo" class="arr-order" @click="goOrder(r.orderId)">
              订单 {{ r.orderNo }}
            </span>
            <span v-if="r.remark">{{ r.remark }}</span>
          </div>
        </div>
        <small class="arr-time">{{ formatDate(r.createdAt) }}</small>
      </div>

      <!-- AdminPager 的 @change 传的是增量（-1/+1），跳页走 v-model:jump-page + @jump -->
      <AdminPager
        :page="page"
        :total-pages="totalPages"
        v-model:jump-page="jumpPage"
        @change="changePage"
        @jump="jumpTo"
      />
    </template>
  </section>
</template>

<script>
import { inject, ref, reactive, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { money, formatDate } from '../utils/format.js';
import AdminPager from '../components/AdminPager.vue';

/**
 * 金额明细 —— **直接查钱包流水 wallet_transaction**（2026-10-09 重构）。
 *
 * <p>早期版本另建了一张 amount_record，把「运费 / 券 / 活动 / 会员 / 积分」
 * 拆成多条记录展示。这是错的：**这些金额已经包含在订单的实付金额里**
 * （实付 = 小计 + 运费 − 优惠），当成独立流水再累加就是重复计算。
 *
 * <p>钱包流水记的本就是真正的资金进出：
 *   RECHARGE 充值 → 收入（绿 +）
 *   PAYMENT  支付 → 支出（红 −）
 *   REFUND   退款 → 收入（绿 +）
 *
 * <p>想知道「这单省了多少」属于**订单的金额构成**，去订单详情看 ——
 * 那里有小计 / 运费 / 各项优惠 / 实付的完整拆解。
 */
export default {
  name: 'AmountRecordsPage',
  components: { AdminPager },
  setup() {
    const appCtx = inject('appCtx');
    const router = useRouter();
    const api = appCtx?.api;

    const rows = ref([]);
    const loading = ref(false);
    const page = ref(1);
    const pageSize = 20;
    const total = ref(0);
    const jumpPage = ref(null);
    const summary = reactive({ out: 0, income: 0 });

    const totalPages = computed(() => Math.max(1, Math.ceil((total.value || 0) / pageSize)));

    async function load() {
      if (!api) return;
      loading.value = true;
      try {
        const res = await api.get(`/wallet/transactions?page=${page.value}&size=${pageSize}`);
        rows.value = Array.isArray(res?.items) ? res.items : [];
        total.value = Number(res?.total) || 0;
      } catch (e) {
        rows.value = [];
        total.value = 0;
      } finally {
        loading.value = false;
      }
    }

    /** 汇总走独立接口只拉一次：它是总账，不随翻页变化 */
    async function loadSummary() {
      if (!api) return;
      try {
        const s = await api.get('/wallet/summary');
        summary.out = Number(s?.totalOut || 0);
        summary.income = Number(s?.totalIncome || 0);
      } catch (e) {
        // 汇总失败不该让整页打不开，留 0 即可
      }
    }

    /** AdminPager 的 change 传的是增量 */
    function changePage(delta) {
      const next = page.value + delta;
      if (next < 1 || next > totalPages.value) return;
      page.value = next;
      load();
    }

    function jumpTo() {
      const n = Number(jumpPage.value);
      if (!n || n < 1 || n > totalPages.value) return;
      page.value = n;
      jumpPage.value = null;
      load();
    }

    function goOrder(orderId) {
      if (orderId) router.push({ name: 'orderDetail', params: { id: String(orderId) } });
    }

    onMounted(() => {
      load();
      loadSummary();
    });

    return { rows, loading, page, totalPages, jumpPage, summary,
             changePage, jumpTo, goOrder, money, formatDate };
  },
};
</script>
