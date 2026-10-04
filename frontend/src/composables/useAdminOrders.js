/**
 * 后台「订单管理」：查询分页 + 发货 + 完成/取消
 *
 * 从 AdminPanel.vue 抽出（原 1468-1548 发货段 + 1705-1744 完成取消段）。
 *
 * ⚠️ 三条业务口径（别改）：
 * - 门店自提「备货完成」不弹快递单号表单（自提单没有物流），确认后直接推进到「待取货」；
 *   这是修掉「自提单被迫瞎填一个快递单号」的关键一步。
 * - 取消订单的提示文案按 `paymentStatus === 'PAID'` 分流：已付款会退回钱包 + 回滚库存。
 * - 退款金额展示里 `orderSavedTotal(order) > 0` 才加「已优惠」行（口径与订单详情一致）。
 *
 * 依赖注入：adminOrders / adminOrderKeyword / adminOrderStatus / adminOrderJumpPage 数据源，
 *   loadAdminOrders / loadRefundOrders / loadAdminUsers / money / orderSavedTotal 等由 AdminPanel 注入。
 */
import { computed, reactive } from 'vue';

export function useAdminOrders({ api, run, fail, askConfirm, money, orderSavedTotal, formatRole,
  adminOrders, adminOrderKeyword, adminOrderStatus, adminOrderJumpPage,
  loadAdminOrders, loadRefundOrders, loadAdminUsers }) {
  const shipForm = reactive({ orderId: null, shipCompany: '', shipNo: '' });

  const adminOrderTotalPages = computed(() => Math.max(1, Math.ceil((adminOrders.total || 0) / (adminOrders.size || 10))));

  async function searchAdminOrders() {
    adminOrders.page = 1;
    await loadAdminOrders();
  }

  async function changeAdminOrderPage(delta) {
    const next = adminOrders.page + delta;
    if (next < 1 || next > adminOrderTotalPages.value) return;
    adminOrders.page = next;
    await loadAdminOrders();
  }

  async function changeAdminOrderPageSize() {
    adminOrders.page = 1;
    await loadAdminOrders();
  }

  async function goAdminOrderPage() {
    const p = Number(adminOrderJumpPage.value);
    if (!Number.isInteger(p) || p < 1 || p > adminOrderTotalPages.value) {
      adminOrderJumpPage.value = adminOrders.page;
      return;
    }
    adminOrders.page = p;
    await loadAdminOrders();
  }

  function resetAdminOrderSearch() {
    adminOrderKeyword.value = '';
    adminOrderStatus.value = '';
    adminOrders.page = 1;
    loadAdminOrders();
  }

  function openShipForm(orderId) {
    shipForm.orderId = orderId;
    shipForm.shipCompany = '';
    shipForm.shipNo = '';

  }

  // 门店自提「备货完成」：自提单没有物流，所以不弹快递单号表单，确认后直接推进到「待取货」。
  // 这也是修掉「自提单被迫瞎填一个快递单号」的关键一步。
  async function adminOrderReady(order) {
    const confirmed = await askConfirm({
      title: '标记备货完成',
      message: `「${order.orderNo}」是门店自提订单，标记后会给用户推送「凭自提码到店取货」的提醒，用户即可到店取货并在订单页确认。`,
      details: [
        { label: '自提门店', value: order.pickupStoreName || '-' },
        { label: '自提码', value: order.pickupCode || '-' },
      ],
      confirmText: '确认备货完成',
    });
    if (!confirmed) return;
    await run(async () => {
      await api.post(`/admin/orders/${order.id}/ready`, {});
      await Promise.all([loadAdminOrders(), loadRefundOrders()]);
    }, '已标记备货完成，等待用户到店取货');

  }

  async function submitShip(id) {
    if (!shipForm.shipCompany.trim() || !shipForm.shipNo.trim()) { fail('请填写快递公司和快递单号'); return; }
    const confirmed = await askConfirm({
      title: '确认发货',
      message: '发货后订单进入待收货状态，物流信息将展示给买家，请核对单号无误。',
      confirmText: '确认发货',
      details: [
        { label: '快递公司', value: shipForm.shipCompany.trim() },
        { label: '快递单号', value: shipForm.shipNo.trim() },
      ],
    });
    if (!confirmed) return;
    await run(async () => {
      await api.post(`/admin/orders/${id}/ship`, { shipCompany: shipForm.shipCompany.trim(), shipNo: shipForm.shipNo.trim() });
      shipForm.orderId = null;
      await Promise.all([loadAdminOrders(), loadRefundOrders()]);
    }, '订单已发货');

  }

  async function completeAdminOrder(id) {
    const order = (adminOrders.items || []).find((item) => item.id === id);
    const confirmed = await askConfirm({
      title: '确认完成订单',
      message: '完成后订单交易结束，买家可以对商品进行评价。',
      confirmText: '确认完成',
      details: order ? [{ label: '订单号', value: order.orderNo }, { label: '金额', value: money(order.payAmount) }] : [],
    });
    if (!confirmed) return;
    await run(() => api.post(`/admin/orders/${id}/complete`).then(loadAdminOrders), '订单已完成');

  }

  async function cancelAdminOrder(id) {
    const order = (adminOrders.items || []).find((item) => item.id === id);
    const paid = order?.paymentStatus === 'PAID';
    const confirmed = await askConfirm({
      title: '取消订单',
      message: paid
        ? '该订单已支付，取消后款项将退回买家钱包并回滚库存，操作不可撤销。'
        : '取消后订单关闭并释放占用的库存，操作不可撤销。',
      confirmText: '确认取消订单',
      danger: true,
      details: order
        ? [
            { label: '订单号', value: order.orderNo },
            { label: '商品小计', value: money(order.totalAmount) },
            ...(orderSavedTotal(order) > 0 ? [{ label: '已优惠', value: `- ${money(orderSavedTotal(order))}` }] : []),
            { label: '订单金额（实付）', value: money(order.payAmount) },
            { label: '是否已付款', value: paid ? '已付款，将原路退回钱包' : '未付款' },
          ]
        : [],
    });
    if (!confirmed) return;
    await run(async () => {
      await api.post(`/admin/orders/${id}/cancel`);
      await Promise.all([loadAdminOrders(), loadAdminUsers()]);
    }, '后台订单已取消');

  }

  return {
    shipForm, adminOrderTotalPages,
    searchAdminOrders, changeAdminOrderPage, changeAdminOrderPageSize, goAdminOrderPage,
    resetAdminOrderSearch, openShipForm, adminOrderReady, submitShip,
    completeAdminOrder, cancelAdminOrder,
  };
}
