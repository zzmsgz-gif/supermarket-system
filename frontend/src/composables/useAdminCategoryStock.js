/**
 * 后台运营杂项：分类新增、库存入库、公告类型标签、经营趋势判空
 *
 * 从 AdminPanel.vue 抽出（原 1344 分类表单 + 1403-1466 公告工具与入库 + 1746-1753 分类保存）。
 * 这些函数分散在文件各处但都很短、彼此无依赖，合成一个模块比拆成 4 个更合理。
 *
 * - 分类表单没有编辑入口（只新增），保存后重置为默认值。
 * - 入库走 `/admin/products/{id}/stock-adjustments`，bizType 固定 `PURCHASE`（采购入库），
 *   入库后要同时刷新商品列表、库存预警与前台商品（首页在售数量会变）。
 * - 公告类型的配色复用 .tag 变体：公告=蓝(info) / 活动=绿(ok) / 服务=灰(muted) / 提醒=红(warn)。
 *
 * 依赖注入：api 直接 import；run / fail / askConfirm +
 *   loadAdminProducts / loadStockAlerts / loadProducts / loadCategories / adminProducts /
 *   adminStatsOverview 由 AdminPanel 注入。
 */
import { computed, reactive } from 'vue';
import { api } from '../api/client';

export function useAdminCategoryStock({ run, fail, askConfirm,
  loadAdminProducts, loadStockAlerts, loadProducts, loadCategories, adminProducts, adminStatsOverview }) {
  const categoryForm = reactive({ parentId: 0, name: '', iconUrl: '', sortNo: 10, status: 1 });
  const stockForm = reactive({ productId: null, quantity: 10, remark: '' });

  function noticeTypeLabel(type) {
    if (type === 'ACTIVITY') return '活动';
    if (type === 'PROMOTION') return '促销';
    if (type === 'SERVICE') return '服务';
    if (type === 'WARNING') return '提醒';
    return '公告';
  }

  // 公告类型的配色（复用 .tag 变体：公告=蓝 / 活动=绿 / 服务=灰 / 提醒=红）
  function noticeTypeClass(type) {
    if (type === 'ACTIVITY') return 'ok';
    if (type === 'WARNING') return 'warn';
    if (type === 'SERVICE') return 'muted';
    return 'info';
  }

  // 轮播卡上显示的跳转商品名（后台商品列表是分页的，不在当前页时回退成空）
  function adminProductName(id) {
    if (!id) return '';
    const hit = (adminProducts.items || []).find((p) => String(p.id) === String(id));
    return hit ? hit.name : '';
  }

  // 近 7 天有任一成交才画趋势图，否则显示空状态（全 0 贴地直线很丑）
  const trendHasData = computed(() =>
    ((adminStatsOverview.value && adminStatsOverview.value.salesTrend) || [])
      .some((p) => Number(p.orderCount || 0) > 0 || Number(p.salesAmount || 0) > 0));

  function openStockForm(product, quantity = 10) {
    stockForm.productId = product.id;
    stockForm.quantity = quantity;
    stockForm.remark = '';

  }

  async function submitStock(product) {
    const quantity = Number(stockForm.quantity);
    if (!Number.isInteger(quantity) || quantity <= 0) {
      fail('入库数量必须是不小于 1 的整数');
      return;
    }
    const confirmed = await askConfirm({
      title: '确认入库',
      message: `将为「${product.name}」增加 ${quantity} 件库存，操作会立即生效。`,
      confirmText: '确认入库',
      details: [
        { label: '商品', value: `${product.name}（${product.sku || '-'}）` },
        { label: '当前库存', value: product.stock ?? 0 },
        { label: '入库后', value: Number(product.stock || 0) + quantity },
      ],
    });
    if (!confirmed) return;
    await run(async () => {
      await api.post(`/admin/products/${product.id}/stock-adjustments`, {
        changeQuantity: quantity,
        bizType: 'PURCHASE',
        remark: stockForm.remark.trim() || '后台手动入库',
      });
      stockForm.productId = null;
      await Promise.all([loadAdminProducts(), loadStockAlerts(), loadProducts()]);
    }, `已入库 ${quantity} 件`);

  }

  async function saveCategory() {
    if (!categoryForm.name.trim()) { fail('请填写分类名称'); return; }
    await run(async () => {
      await api.post('/admin/categories', categoryForm);
      Object.assign(categoryForm, { parentId: 0, name: '', iconUrl: '', sortNo: 10, status: 1 });
      await loadCategories();
    }, '分类已新增');

  }

  return {
    categoryForm, stockForm,
    noticeTypeLabel, noticeTypeClass, adminProductName, trendHasData,
    openStockForm, submitStock, saveCategory,
  };
}
