/**
 * 后台「分类管理」+「库存预警」的客户端分页与筛选
 *
 * 从 AdminPanel.vue 抽出（原 1265-1342 行的分页状态 + changeCategoryPage 等 4 个分页函数）。
 *
 * ⚠️ 为什么只搬「客户端」分页：分类与库存预警是**全量数据在前端过滤/分页**
 *    （分类基于 `categories`，库存预警基于 `stockAlerts`），没有服务端分页接口。
 *    对比：订单/退款/优惠券/用户走后端分页（page/size 传给 API），那些在各自模块里。
 *
 * 依赖注入：categories / stockAlerts 数据源 + 各自的 totalPages 基数，由 AdminPanel 注入。
 */
import { computed, ref } from 'vue';

export function useAdminCategoryStockPaging({ categories, stockAlerts }) {
  // ===== 分类 =====
  const categoryKeyword = ref('');
  const categoryPage = ref(1);
  const categorySize = ref(10);
  const categoryJumpPage = ref(1);

  const categoryFiltered = computed(() => {
    const kw = categoryKeyword.value.trim().toLowerCase();
    const list = categories.value || [];
    if (!kw) return list;
    return list.filter((c) => (c.name || '').toLowerCase().includes(kw));
  });

  const categoryTotalPages = computed(() => Math.max(1, Math.ceil(categoryFiltered.value.length / (categorySize.value || 10))));

  const categoryPageItems = computed(() => {
    const total = categoryTotalPages.value;
    const page = Math.min(categoryPage.value, total);
    const start = (page - 1) * categorySize.value;
    return categoryFiltered.value.slice(start, start + categorySize.value);
  });

  function changeCategoryPage(delta) {
    const next = categoryPage.value + delta;
    if (next < 1 || next > categoryTotalPages.value) return;
    categoryPage.value = next;
  }

  function goCategoryPage() {
    const p = Number(categoryJumpPage.value);
    if (!Number.isInteger(p) || p < 1 || p > categoryTotalPages.value) {
      categoryJumpPage.value = categoryPage.value;
      return;
    }
    categoryPage.value = p;
  }

  // ===== 库存预警 =====
  const stockKeyword = ref('');
  const stockPage = ref(1);
  const stockSize = ref(10);
  const stockJumpPage = ref(1);

  const stockFiltered = computed(() => {
    const kw = stockKeyword.value.trim().toLowerCase();
    const list = stockAlerts.value || [];
    if (!kw) return list;
    return list.filter((a) => (a.name || '').toLowerCase().includes(kw) || (a.sku || '').toLowerCase().includes(kw));
  });

  const stockTotalPages = computed(() => Math.max(1, Math.ceil(stockFiltered.value.length / (stockSize.value || 10))));

  const stockPageItems = computed(() => {
    const total = stockTotalPages.value;
    const page = Math.min(stockPage.value, total);
    const start = (page - 1) * stockSize.value;
    return stockFiltered.value.slice(start, start + stockSize.value);
  });

  function changeStockPage(delta) {
    const next = stockPage.value + delta;
    if (next < 1 || next > stockTotalPages.value) return;
    stockPage.value = next;
  }

  function goStockPage() {
    const p = Number(stockJumpPage.value);
    if (!Number.isInteger(p) || p < 1 || p > stockTotalPages.value) {
      stockJumpPage.value = stockPage.value;
      return;
    }
    stockPage.value = p;
  }

  return {
    categoryKeyword, categoryPage, categorySize, categoryJumpPage,
    categoryFiltered, categoryTotalPages, categoryPageItems, changeCategoryPage, goCategoryPage,
    stockKeyword, stockPage, stockSize, stockJumpPage,
    stockFiltered, stockTotalPages, stockPageItems, changeStockPage, goStockPage,
  };
}
