<script setup>
// 后台「经营看板」面板：从 AdminPanel.vue 整块搬过来的（模板 220 行 + 看板数据逻辑）。
// 与共用件不同 —— 这一块**自带状态**（insights / insightsRange / loadInsights 等），
// 父级的 adminMenuLoaders 仍要能调度它加载，所以通过 defineExpose 暴露 load()。
//
// ⚠️ adminCtx 的解构列表**照抄父级**：后台的状态/函数都在那一包里，漏解构一个就是运行时 undefined，
//    而 Vue 模板编译不会报错（要跑起来才炸）。照抄是最不容易漏的做法。
import { ref, computed, onMounted } from 'vue';
import { api } from '../api/client';
import { money, formatDate } from '../utils/format';

const props = defineProps({
  adminCtx: { type: Object, required: true },
  selectMenu: { type: Function, required: true },
});
const { adminChartProducts, adminCouponJumpPage, adminCouponKeyword, adminCoupons, adminJumpPage, adminMenu, adminOrderJumpPage, adminOrderKeyword, adminOrderStatus, adminOrders, adminProductKeyword, adminProductStatus, adminProducts, adminAnnouncements, adminBanners, adminStatsOverview, announcementForm, announcementFormOpen, bannerForm, bannerFormOpen, bannerUploading, adminUserJumpPage, adminUserKeyword, adminUserRole, adminUserStatus, adminUsers, alertDialog, askConfirm, categoryName, confirmDialog, coupons, error, fail, filters, loadAdminAnnouncements, loadAdminBanners, loadAdminCoupons, loadAdminOrders, loadAdminProducts, loadAdminStatsOverview, loadAdminUsers, loadCategories, loadProducts, loadRefundOrders, loadStockAlerts, notice, openAnnouncementForm, openBannerForm, openOrderDetail, orderDetail, orders, productForm, saveAnnouncement, products, refreshAdminData, refundJumpPage, refundOrders, refundStatusFilter, run, safeParseSpec, session, showAlert, stockAlerts, closeAnnouncementForm, closeBannerForm, saveBanner, toggleBanner, deleteBanner, toggleAnnouncement, deleteAnnouncement, adminHotSearches, hotSearchForm, hotSearchFormOpen, loadAdminHotSearches, openHotSearchForm, closeHotSearchForm, saveHotSearch, toggleHotSearch, deleteHotSearch } = props.adminCtx;
// 模板里沿用 selectAdminMenu(...) 的写法，这里给个别名，模板不用改
const selectAdminMenu = props.selectMenu;
// 本组件只在后台挂载（父级已判定 isAdmin），与 AdminPanel 内的写法保持一致
const isAdmin = { value: true };

/* ===================== 经营看板 ===================== */
const insightsRange = ref('7d');
const insights = ref(null);
const insightsLoading = ref(false);

async function loadInsights() {
  if (!isAdmin.value) return;
  insightsLoading.value = true;
  try {
    insights.value = await api.get(`/admin/stats/dashboard?range=${insightsRange.value}`);
  } catch (err) {
    fail(err?.message || '经营数据加载失败');
  } finally {
    insightsLoading.value = false;
  }
}

async function changeInsightsRange(range) {
  insightsRange.value = range;
  await loadInsights();
}

function pctText(value) {
  if (value === null || value === undefined) return '—';
  return `${value}%`;
}

// 环比上升/下降的展示样式：上升用绿(好)、下降用红(差)——经营指标不看股市红绿习惯
function growthClass(value) {
  if (value === null || value === undefined) return 'flat';
  return value >= 0 ? 'up' : 'down';
}

function growthText(value) {
  if (value === null || value === undefined) return '不可比';
  return `${value >= 0 ? '+' : ''}${value}%`;
}

const maxProductQuantity = computed(() => {
  const list = (insights.value && insights.value.topProducts) || [];
  return list.reduce((max, item) => Math.max(max, Number(item.quantity || 0)), 0) || 1;
});

const insightsRangeLabel = computed(() => ({
  today: '今日', '7d': '近 7 天', '30d': '近 30 天', '90d': '近 90 天',
}[insightsRange.value] || '近 7 天'));

const dashboardStats = computed(() => {
  const o = adminStatsOverview.value || {};
  return {
    productCount: Number(o.productTotal || 0),
    totalStock: Number(o.stockTotal || 0),
    userCount: Number(o.userTotal || 0),
    orderCount: Number(o.orderTotal || 0),
    salesAmount: Number(o.salesAmount || 0),
    todayOrderCount: Number(o.todayOrderCount || 0),
    todaySalesAmount: Number(o.todaySalesAmount || 0),
    pendingShipCount: Number(o.pendingShipCount || 0),
    pendingPayCount: Number(o.pendingPayCount || 0),
    refundApplyingCount: Number(o.refundApplyingCount || 0),
  };
});
onMounted(() => { loadInsights(); });   // 兜底：父级 watch 触发时本组件可能还没挂载，ref 还是 null
defineExpose({ load: loadInsights });   // 供父级 adminMenuLoaders 调度（点「刷新」/切回看板时）
</script>

<template>
          <div class="data-panel">
            <!-- ===== 全量概览（原「数据统计」页，已并入本页）=====
                 刻意**不随下方区间变化**：这些是"家底"（累计/当前值），而下方是"这段时间的经营表现"。
                 两者的待发货/待付款只保留一份（在下方区间卡片里），避免同一屏出现两个同名数字。 -->
            <div class="stat-grid">
              <div class="stat-card">
                <small>上架商品</small>
                <strong>{{ dashboardStats.productCount }}</strong>
              </div>
              <div class="stat-card">
                <small>库存总量</small>
                <strong>{{ dashboardStats.totalStock }}</strong>
              </div>
              <div class="stat-card">
                <small>注册用户</small>
                <strong>{{ dashboardStats.userCount }}</strong>
              </div>
              <div class="stat-card">
                <small>订单总数</small>
                <strong>{{ dashboardStats.orderCount }}</strong>
              </div>
              <div class="stat-card">
                <small>累计成交</small>
                <strong>{{ money(dashboardStats.salesAmount) }}</strong>
              </div>
              <div class="stat-card">
                <small>今日成交</small>
                <strong>{{ money(dashboardStats.todaySalesAmount) }}</strong>
              </div>
            </div>

            <div class="dash-grid">
              <div class="dash-card">
                <div class="panel-head"><h3>待办速览</h3></div>
                <div class="todo-list">
                  <button class="todo-item" @click="selectAdminMenu('orders')">
                    <span>待发货订单</span><b>{{ dashboardStats.pendingShipCount }}</b><span class="todo-go">去处理 ›</span>
                  </button>
                  <button class="todo-item" @click="selectAdminMenu('orders')">
                    <span>待付款订单</span><b>{{ dashboardStats.pendingPayCount }}</b><span class="todo-go">去处理 ›</span>
                  </button>
                  <button class="todo-item" @click="selectAdminMenu('refunds')">
                    <span>售后待审核</span><b>{{ dashboardStats.refundApplyingCount }}</b><span class="todo-go">去处理 ›</span>
                  </button>
                  <button class="todo-item" @click="selectAdminMenu('reviews')">
                    <span>评价待回复</span><b>{{ adminReviewSummary?.unrepliedCount || 0 }}</b><span class="todo-go">去处理 ›</span>
                  </button>
                </div>
              </div>
              <div class="dash-card">
                <div class="panel-head">
                  <h3>低库存预警</h3>
                  <button class="ghost" @click="selectAdminMenu('stock')">全部 ›</button>
                </div>
                <empty-state v-if="!stockAlerts.length" icon="cart" text="暂无低库存商品，备货充足" />
                <ul v-else class="stock-list">
                  <li v-for="p in stockAlerts.slice(0, 5)" :key="p.id">
                    <span>{{ p.name }}</span>
                    <b class="danger">剩 {{ p.stock }} / 阈值 {{ p.lowStockThreshold }}</b>
                  </li>
                </ul>
              </div>
            </div>

            <div class="insights-bar">
              <div class="range-tabs">
                <button
                  v-for="opt in [{ k: 'today', t: '今日' }, { k: '7d', t: '近 7 天' }, { k: '30d', t: '近 30 天' }, { k: '90d', t: '近 90 天' }]"
                  :key="opt.k"
                  class="ghost mini"
                  :class="{ on: insightsRange === opt.k }"
                  @click="changeInsightsRange(opt.k)"
                >{{ opt.t }}</button>
              </div>
              <button class="ghost mini" :disabled="insightsLoading" @click="loadInsights">
                {{ insightsLoading ? '加载中…' : '刷新' }}
              </button>
            </div>

            <p v-if="!insights" class="empty-hint">{{ insightsLoading ? '正在加载经营数据…' : '暂无数据' }}</p>
            <template v-else>
              <p class="insights-note">
                {{ insightsRangeLabel }}（{{ insights.fromDate }} ~ {{ insights.toDate }}）·
                成交额与订单数按<b>订单创建时间</b>落在区间内、且订单状态为已支付来统计（不按 paid_at，
                否则历史缺 paid_at 的订单会让上下两块数字对不上）；环比对照紧邻的等长上一区间。
              </p>

              <!-- 核心指标 -->
              <div class="stat-grid">
                <div class="stat-card">
                  <small>成交额（GMV）</small>
                  <strong>{{ money(insights.trade.gmv) }}</strong>
                  <span class="growth" :class="growthClass(insights.growth.gmvPercent)">
                    {{ growthText(insights.growth.gmvPercent) }}
                  </span>
                </div>
                <div class="stat-card">
                  <small>成交订单数</small>
                  <strong>{{ insights.trade.paidOrderCount }}</strong>
                  <span class="growth" :class="growthClass(insights.growth.orderPercent)">
                    {{ growthText(insights.growth.orderPercent) }}
                  </span>
                </div>
                <div class="stat-card">
                  <small>客单价</small>
                  <strong>{{ money(insights.trade.avgOrderValue) }}</strong>
                  <span class="growth" :class="growthClass(insights.growth.avgOrderPercent)">
                    {{ growthText(insights.growth.avgOrderPercent) }}
                  </span>
                </div>
                <div class="stat-card">
                  <small>新增用户</small>
                  <strong>{{ insights.users.newUserCount }}</strong>
                  <span class="growth flat">区间内注册</span>
                </div>
                <div class="stat-card">
                  <small>复购率</small>
                  <strong>{{ pctText(insights.users.repurchaseRate) }}</strong>
                  <span class="growth flat">{{ insights.users.repeatBuyerCount }}/{{ insights.users.buyerCount }} 人复购</span>
                </div>
                <div class="stat-card">
                  <small>退款</small>
                  <strong>{{ insights.trade.refundCount }} 单</strong>
                  <span class="growth flat">合计 {{ money(insights.trade.refundAmount) }}</span>
                </div>
                <div class="stat-card">
                  <small>待发货</small>
                  <strong>{{ insights.trade.pendingShipCount }}</strong>
                  <span class="growth flat">需尽快处理</span>
                </div>
                <div class="stat-card">
                  <small>待付款</small>
                  <strong>{{ insights.trade.pendingPayCount }}</strong>
                  <span class="growth flat">超时自动关闭</span>
                </div>
              </div>

              <div class="insights-cols">
                <!-- 成交趋势 -->
                <div class="insights-block">
                  <h4>成交趋势</h4>
                  <div class="trend-bars">
                    <div v-for="point in insights.trend" :key="point.date" class="trend-col"
                         :title="`${point.date}：${point.orderCount} 单 / ${money(point.salesAmount)}`">
                      <i :style="{ height: (insights.trend.reduce((m, p) => Math.max(m, Number(p.salesAmount || 0)), 0) > 0
                        ? Math.max(4, Math.round(Number(point.salesAmount || 0) * 100 / insights.trend.reduce((m, p) => Math.max(m, Number(p.salesAmount || 0)), 0)))
                        : 4) + '%' }"></i>
                      <small>{{ point.date.slice(5) }}</small>
                    </div>
                  </div>
                </div>

                <!-- 会员等级分布 -->
                <div class="insights-block">
                  <h4>会员等级分布</h4>
                  <div class="dist-list">
                    <div v-for="level in insights.memberLevels" :key="level.level" class="dist-row">
                      <span class="dist-label">{{ level.name }}</span>
                      <div class="dist-bar"><i :style="{ width: (insights.memberLevels.reduce((m, l) => Math.max(m, l.userCount), 0) > 0
                        ? Math.round(level.userCount * 100 / insights.memberLevels.reduce((m, l) => Math.max(m, l.userCount), 0))
                        : 0) + '%' }"></i></div>
                      <b>{{ level.userCount }} 人</b>
                    </div>
                  </div>
                </div>
              </div>

              <div class="insights-cols">
                <!-- 品类占比 -->
                <div class="insights-block">
                  <h4>品类销售占比</h4>
                  <p v-if="!insights.categoryShare.length" class="empty-hint">区间内暂无成交</p>
                  <div v-else class="dist-list">
                    <div v-for="cat in insights.categoryShare" :key="cat.categoryId" class="dist-row">
                      <span class="dist-label">{{ cat.categoryName }}</span>
                      <div class="dist-bar"><i :style="{ width: (cat.percent || 0) + '%' }"></i></div>
                      <b>{{ pctText(cat.percent) }} · {{ money(cat.amount) }}</b>
                    </div>
                  </div>
                </div>

                <!-- 畅销商品 -->
                <div class="insights-block">
                  <h4>畅销商品 Top {{ insights.topProducts.length || 0 }}</h4>
                  <p v-if="!insights.topProducts.length" class="empty-hint">区间内暂无成交</p>
                  <div v-else class="dist-list">
                    <div v-for="item in insights.topProducts" :key="item.productId" class="dist-row">
                      <span class="dist-label">{{ item.productName }}</span>
                      <div class="dist-bar"><i :style="{ width: Math.round(Number(item.quantity) * 100 / maxProductQuantity) + '%' }"></i></div>
                      <b>{{ item.quantity }} 件 · {{ money(item.amount) }}</b>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 秒杀效果 -->
              <div class="insights-block">
                <h4>限时秒杀效果</h4>
                <p v-if="!insights.flashSales.length" class="empty-hint">还没有秒杀场次</p>
                <table v-else class="admin-table">
                  <thead>
                    <tr><th>场次</th><th>商品</th><th>秒杀价</th><th>名额进度</th><th>已售件数</th><th>成交额</th></tr>
                  </thead>
                  <tbody>
                    <tr v-for="sale in insights.flashSales" :key="sale.id">
                      <td>{{ sale.name }}</td>
                      <td>{{ sale.productName }}</td>
                      <td>{{ money(sale.flashPrice) }}</td>
                      <td>
                        <div class="dist-bar slim"><i :style="{ width: (sale.soldPercent || 0) + '%' }"></i></div>
                        <span class="cell-sub">{{ sale.soldQuota }}/{{ sale.totalQuota }}（{{ pctText(sale.soldPercent) }}）</span>
                      </td>
                      <td>{{ sale.soldQuantity }}</td>
                      <td>{{ money(sale.amount) }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </template>
          </div>
</template>
