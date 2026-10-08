<template>
  <section class="data-panel amount-page">
    <div class="panel-head">
      <h3>金额明细</h3>
      <small>每笔订单的钱都花在哪儿了 —— 优惠抵扣不体现在余额里，这里能看到</small>
    </div>

    <!-- 汇总：跨筛选的「总账」，不是当前筛选的小计 -->
    <div class="ar-summary">
      <div class="ars-item">
        <span>累计支出</span>
        <b class="amt-out">{{ money(summary.out) }}</b>
      </div>
      <div class="ars-item">
        <span>累计退回</span>
        <b class="amt-in">{{ money(summary.income) }}</b>
      </div>
      <div class="ars-item">
        <span>优惠省下</span>
        <b class="amt-save">{{ money(summary.saved) }}</b>
      </div>
    </div>

    <div class="filter-row">
      <button
        v-for="f in FILTERS"
        :key="f.key"
        :class="['ghost sm', { on: filter === f.key }]"
        type="button"
        @click="switchFilter(f.key)"
      >{{ f.label }}</button>
    </div>

    <div v-if="loading" class="empty">加载中…</div>
    <div v-else-if="!rows.length" class="empty">
      还没有{{ FILTERS.find(f => f.key === filter).label }}记录
    </div>

    <template v-else>
      <div v-for="r in rows" :key="r.id" class="ar-row">
        <div class="arr-main">
          <div class="arr-title">
            <span class="ar-type">{{ r.typeLabel }}</span>
            <strong :class="r.direction === 1 ? 'amt-out' : 'amt-in'">
              {{ r.direction === 1 ? '-' : '+' }}{{ money(r.amount) }}
            </strong>
          </div>
          <div class="arr-meta">
            <span v-if="r.title && r.title !== r.typeLabel">{{ r.title }}</span>
            <span v-if="r.remark">{{ r.remark }}</span>
            <span v-if="r.orderNo" class="arr-order" @click="goOrder(r.orderId)">
              订单 {{ r.orderNo }}
            </span>
          </div>
        </div>
        <small class="arr-time">{{ formatDate(r.createdAt) }}</small>
      </div>

      <AdminPager
        :page="page"
        :total-pages="totalPages"
        @change-page="changePage"
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
 * 金额明细（第 7 条：用户端「想知道金额流水」）。
 *
 * 与「钱包余额流水」（/wallet/transactions）是两回事：
 *   · 余额流水 = 钱包余额怎么变的（充值 / 支付 / 退款）
 *   · 金额明细 = 每笔订单的金额构成（实付 / 券 / 活动 / 会员 / 积分 / 运费 / 退款）
 * 优惠抵扣**不产生余额变动**，所以用户在余额流水里看不到自己省了多少 ——
 * 这正是「钱对不上」的根源。
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
    const filter = ref('ALL');
    /** 汇总基于**全部记录**算、与筛选无关 —— 用户看的是总账，不是当前筛选的小计 */
    const summary = reactive({ out: 0, income: 0, saved: 0 });

    const FILTERS = [
      { key: 'ALL', label: '全部', types: [] },
      { key: 'PAY', label: '支出', types: ['ORDER_PAY', 'FREIGHT'] },
      { key: 'DISCOUNT', label: '优惠', types: ['COUPON_DISCOUNT', 'ACTIVITY_DISCOUNT', 'MEMBER_DISCOUNT', 'POINTS_DISCOUNT'] },
      { key: 'REFUND', label: '退款', types: ['ORDER_REFUND'] },
      { key: 'POINTS', label: '积分', types: ['POINTS_EARN'] },
    ];

    const totalPages = computed(() => Math.max(1, Math.ceil((total.value || 0) / pageSize)));

    async function load() {
      if (!api) return;
      loading.value = true;
      try {
        // 一次拉 200 条后本地筛选：跨页筛选会让分页器页数对不上
        const res = await api.get(`/amount-records?page=${page.value}&size=${pageSize}`);
        const all = Array.isArray(res?.items) ? res.items : [];
        const f = FILTERS.find(x => x.key === filter.value);
        rows.value = f.types.length ? all.filter(r => f.types.includes(r.type)) : all;
        total.value = rows.value.length;
        recomputeSummary(all);
      } catch (e) {
        rows.value = [];
        total.value = 0;
      } finally {
        loading.value = false;
      }
    }

    function recomputeSummary(all) {
      summary.out = 0;
      summary.income = 0;
      summary.saved = 0;
      for (const r of all) {
        if (r.type === 'POINTS_EARN') continue;
        const v = Number(r.amount || 0);
        if (r.direction === 1) summary.out += v;
        else if (r.type === 'ORDER_REFUND') summary.income += v;
        else summary.saved += v;
      }
    }

    function switchFilter(key) {
      filter.value = key;
      page.value = 1;
      load();
    }

    function changePage(p) {
      page.value = p;
      load();
    }

    function goOrder(orderId) {
      if (orderId) router.push({ name: 'orderDetail', params: { id: String(orderId) } });
    }

    onMounted(load);

    return { rows, loading, page, totalPages, filter, FILTERS, summary,
             switchFilter, changePage, goOrder, money, formatDate };
  },
};
</script>
