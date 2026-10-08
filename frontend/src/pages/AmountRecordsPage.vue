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

    <!-- 筛选 tab：选中的一项要**看得出来**（2026-10-08 用户反馈「点击 tab 得高亮」）。
         高亮不能只靠 class：`.on` 必须有对应样式，否则点了没反应 = 页面像坏了。 -->
    <div class="filter-row" role="tablist">
      <button
        v-for="f in FILTERS"
        :key="f.key"
        :class="['ar-tab', { 'is-on': filter === f.key }]"
        type="button"
        role="tab"
        :aria-selected="filter === f.key ? 'true' : 'false'"
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

      <!-- AdminPager 的 @change 传的是**增量**（上一页 -1 / 下一页 +1），不是绝对页码；
           跳页走 v-model:jump-page + @jump。照 AdminCategoriesPanel 的写法来。 -->
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
    /** AdminPager 的跳页输入（v-model:jump-page） */
    const jumpPage = ref(null);
    /** 汇总基于**全部记录**算、与筛选无关 —— 用户看的是总账，不是当前筛选的小计 */
    const summary = reactive({ out: 0, income: 0, saved: 0 });

    /**
     * 只放 label：类型映射**在服务端**（typesOfGroup）。
     *
     * <p>早期版本把 type 列表写在前端再本地 filter，结果是：
     * 「第 2 页」拿到的是服务端的第 2 页而不是筛选结果的第 2 页，
     * 总数也用筛选后的行数算 —— 翻页和筛选一组合就彻底错乱。
     * 筛选必须下推到 SQL，前端只传分组名。
     */
    const FILTERS = [
      { key: 'ALL', label: '全部' },
      { key: 'PAY', label: '支出' },
      { key: 'DISCOUNT', label: '优惠' },
      { key: 'REFUND', label: '退款' },
      { key: 'POINTS', label: '积分' },
    ];

    const totalPages = computed(() => Math.max(1, Math.ceil((total.value || 0) / pageSize)));

    async function load() {
      if (!api) return;
      loading.value = true;
      try {
        const res = await api.get(
          `/amount-records?page=${page.value}&size=${pageSize}&group=${filter.value}`);
        rows.value = Array.isArray(res?.items) ? res.items : [];
        total.value = Number(res?.total) || 0;
      } catch (e) {
        rows.value = [];
        total.value = 0;
      } finally {
        loading.value = false;
      }
    }

    /**
     * 汇总走独立接口，且**不随筛选变化** —— 用户看的是总账。
     * 只在进页面时拉一次，切 tab 不重新请求（总账本来就不该变）。
     */
    async function loadSummary() {
      if (!api) return;
      try {
        const s = await api.get('/amount-records/summary');
        summary.out = Number(s?.totalOut || 0);
        summary.income = Number(s?.totalIncome || 0);
        summary.saved = Number(s?.totalSaved || 0);
      } catch (e) {
        // 汇总失败不该让整页打不开，留 0 即可
      }
    }

    function switchFilter(key) {
      if (filter.value === key) return;   // 点当前 tab 不必重拉
      filter.value = key;
      page.value = 1;                     // 换筛选必须回第一页，否则会停在越界页
      jumpPage.value = null;
      load();
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

    return { rows, loading, page, totalPages, filter, FILTERS, summary, jumpPage,
             switchFilter, changePage, jumpTo, goOrder, money, formatDate };
  },
};
</script>
