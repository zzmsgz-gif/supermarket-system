<template>
  <section class="data-panel amount-page">
    <div class="panel-head">
      <h3>金额明细</h3>
      <small>累计 = 钱包余额的进出（充值 / 余额支付 / 退回余额的退款）；微信、支付宝支付与退款仅展示、不计入。右侧是这笔之后的余额</small>
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

    <div v-if="loading" class="sk-stack" aria-hidden="true">
      <skeleton-list :count="6" :thumb="false" :lines="2" :tail="true" />
    </div>
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
            <span v-if="r.channelLabel" class="arr-channel">{{ r.channelLabel }}</span>
            <span v-if="r.external" class="arr-external" title="微信/支付宝不经过钱包余额，仅作记录">不计入累计</span>
            <span v-if="r.orderNo" class="arr-order" @click="goOrder(r.orderId)">
              订单 {{ r.orderNo }}
            </span>
            <!-- #713：退款时已花掉、等价扣回的发放积分 —— 让「支付 −X / 退款 +Y」对不上的差额有据可查。
                 原应退 = 入账金额(amount) + 扣回额(pointsDeducted)；仅退款且发生积分扣回时才有此行。 -->
            <span v-if="r.type === 'REFUND' && Number(r.pointsDeducted || 0) > 0" class="arr-deducted">
              已花积分等价扣回 {{ money(r.pointsDeducted) }}（原应退 {{ money(origRefund(r)) }}）
            </span>
            <!-- ⚠️ 刻意**不展示** r.remark（2026-10-09 用户反馈）：
                 那是给财务对账用的内部备注，内容是英文（Order payment / Refund approved /
                 User recharge），夹在中文界面里既看不懂、也没有信息量 ——
                 用户能看懂的部分（是支付还是退款）已经由上方 typeLabel 说明了。 -->
          </div>
        </div>
        <div class="arr-side">
          <!-- 变动后余额：让用户能自己核对「这笔之后还剩多少」，
               不用自己拿计算器加减（2026-10-09 用户要求）。
               balance_after 是钱包余额变动后的值，不是订单金额，别混用。 -->
          <small class="arr-balance">余额 {{ money(r.balanceAfter) }}</small>
          <small class="arr-time">{{ formatDate(r.createdAt) }}</small>
        </div>
      </div>

      <!-- 与订单页同款分页（Pager），数字页码样式全站统一；@go 传绝对页码 -->
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
  components: { Pager },
  setup() {
    const appCtx = inject('appCtx');
    const router = useRouter();
    const api = appCtx?.api;

    const rows = ref([]);
    const loading = ref(false);
    const page = ref(1);
    const pageSize = 20;
    const total = ref(0);
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

    /** Pager 的 go 传的是绝对页码 */
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

    /** 退款行若有「已花积分等价扣回」，原应退 = 入账金额 + 扣回额（#713） */
    function origRefund(r) {
      return Number(r?.amount || 0) + Number(r?.pointsDeducted || 0);
    }

    function goOrder(orderId) {
      if (orderId) router.push({ name: 'orderDetail', params: { id: String(orderId) } });
    }

    onMounted(() => {
      load();
      loadSummary();
    });

    return { rows, loading, page, pageSize, total, totalPages, summary,
             goPage, origRefund, goOrder, money, formatDate };
  },
};
</script>

<style scoped>
.arr-external {
  color: var(--muted);
  font-size: 11px;
  border: 1px dashed var(--line, #e5e7eb);
  border-radius: 999px;
  padding: 0 6px;
  margin-left: 6px;
  line-height: 16px;
  white-space: nowrap;
}
.arr-deducted {
  color: var(--muted);
  font-size: 11px;
  margin-left: 6px;
  line-height: 16px;
  white-space: nowrap;
}
</style>
