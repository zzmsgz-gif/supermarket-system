/**
 * 后台运营杂项：分类增删改、库存入库、公告类型标签、经营趋势判空
 *
 * 从 AdminPanel.vue 抽出（原 1344 分类表单 + 1403-1466 公告工具与入库 + 1746-1753 分类保存）。
 *
 * - 分类表单**兼作新增与编辑**：`editingCategoryId` 为 null 是新增（POST），
 *   非 null 是编辑（PUT /admin/categories/{id}）。点列表「编辑」把该行填进表单。
 * - 删除分类的前置规则（用户明确要求）：**分类下有商品就不能删**，有子分类也不能删。
 *   后端 deleteCategory 本身有 409 校验，但 message 是英文（"Category has products"），
 *   直接弹给用户很难看；所以这里先查一次商品总数 / 子分类，用中文把原因说清楚，
 *   后端 409 仍作为兜底。
 * - 入库走 `/admin/products/{id}/stock-adjustments`，bizType 固定 `PURCHASE`（采购入库），
 *   入库后要同时刷新商品列表、库存预警与前台商品（首页在售数量会变）。
 * - 公告类型的配色复用 .tag 变体：公告=蓝(info) / 活动=绿(ok) / 服务=灰(muted) / 提醒=红(warn)。
 *
 * 依赖注入：api 直接 import；run / fail / askConfirm +
 *   loadAdminProducts / loadStockAlerts / loadProducts / loadCategories / categories /
 *   adminProducts / adminStatsOverview 由 AdminPanel 注入。
 */
import { computed, reactive, ref } from 'vue';
import { api } from '../api/client';

export function useAdminCategoryStock({ run, fail, askConfirm, categories,
  loadAdminProducts, loadStockAlerts, loadProducts, loadCategories, adminProducts, adminStatsOverview }) {
  const categoryForm = reactive({ parentId: 0, name: '', iconUrl: '', sortNo: 10, status: 1 });
  // null = 新增模式；数字 = 正在编辑的分类 id
  const editingCategoryId = ref(null);
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

  function resetCategoryForm() {
    Object.assign(categoryForm, { parentId: 0, name: '', iconUrl: '', sortNo: 10, status: 1 });
    editingCategoryId.value = null;
  }

  /** 点列表「编辑」：把该行填进表单，表单进入编辑态 */
  function startEditCategory(category) {
    if (!category) return;
    editingCategoryId.value = category.id;
    Object.assign(categoryForm, {
      parentId: Number(category.parentId) || 0,
      name: category.name || '',
      iconUrl: category.iconUrl || '',
      sortNo: Number(category.sortNo ?? 10),
      status: Number(category.status ?? 1)
    });
  }

  function cancelEditCategory() {
    resetCategoryForm();
  }

  async function saveCategory() {
    if (!categoryForm.name.trim()) { fail('请填写分类名称'); return; }
    const editingId = editingCategoryId.value;
    await run(async () => {
      if (editingId) {
        await api.put(`/admin/categories/${editingId}`, categoryForm);
      } else {
        await api.post('/admin/categories', categoryForm);
      }
      resetCategoryForm();
      await loadCategories();
    }, editingId ? '分类已更新' : '分类已新增');
  }

  /**
   * 删除分类。规则（用户明确要求）：**分类下有商品就不能删**；有子分类也不能删。
   * 后端 deleteCategory 有 409 兜底，但 message 是英文，所以这里先自己判一次并给中文原因。
   */
  async function deleteCategory(category) {
    if (!category) return;
    const id = Number(category.id);
    const name = category.name || '该分类';

    const children = (categories?.value || []).filter((c) => Number(c.parentId) === id);
    if (children.length) {
      fail(`「${name}」下还有 ${children.length} 个子分类，请先删除或移走子分类`);
      return;
    }

    // 商品校验交给后端 deleteCategory —— 只有它掌握全部口径：含下架商品，也含**不在商品列表里
    // 显示的秒杀独立商品（kind=FLASH）**。
    // 前端在这里预查过两次，都因为口径追不上后端而翻车：
    //   ① 用前台 /products（只返回在售）→ 商品全下架后前端放行 → 后端 409 弹英文；
    //   ② 改用 /admin/products 仍排除 FLASH → 用户删光看得见的商品后前端放行 → 后端照样 409，
    //      而用户根本不知道还挂着一件「限时秒杀」商品（2026-11-07 实测：中秋团圆下只剩这类）。
    // 现在前端只负责「子分类检查 + 确认框」，商品明细与原因交给后端 409 的中文 message。
    const ok = await askConfirm(`确定删除分类「${name}」吗？删除后不可恢复。`);
    if (!ok) return;

    await run(async () => {
      await api.delete(`/admin/categories/${id}`);
      await loadCategories();
      // 正在编辑这个分类时，删完要把表单切回新增态，否则再点保存会 PUT 一个已删除的 id
      if (Number(editingCategoryId.value) === id) resetCategoryForm();
    }, '分类已删除');
  }

  return {
    categoryForm, stockForm, editingCategoryId,
    noticeTypeLabel, noticeTypeClass, adminProductName, trendHasData,
    openStockForm, submitStock, saveCategory,
    startEditCategory, cancelEditCategory, deleteCategory,
  };
}
