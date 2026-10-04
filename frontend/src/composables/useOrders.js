// 订单：列表、支付、取消、确认收货、退款申请、详情。
// 金额与状态文案口径对齐后端 OrderService（见 cancelOrder 里的注释）。
import { reactive } from 'vue';

export function useOrders({
  api, run, fail, askConfirm, showAlert, money, session, isAdmin, router, navigate,
  orders, reviewedMap, refundForm, orderDetail,
  // orderNavLock 是 App.vue 的 let 变量，syncRoute 也要读 → 留在原地，这里用读写器
  isOrderNavLocked, setOrderNavLock,
  loadWallet, loadMe, loadCart, loadFlashSales,
}) {
  // 「再来一单」的并发锁：老订单里常有已下架商品，逐件回报结果比笼统失败更有用
  let reorderLock = null;

  async function loadOrders(reset = true) {
    if (!session.user || isAdmin.value) { orders.items = []; orders.total = 0; return; }
    const nextPage = reset ? 1 : orders.page + 1;
    orders.loading = true;
    try {
      const data = await api.get(`/orders?page=${nextPage}&size=${orders.size}`);
      const items = data?.items || [];
      orders.items = reset ? items : [...orders.items, ...items];
      orders.total = data?.total || 0;
      orders.page = nextPage;
      await loadReviewedFlags();
    } catch (e) { /* ignore */ } finally { orders.loading = false; }
  }

  async function loadMoreOrders() {
    await loadOrders(false);
  }

  // 跳到指定页码（替换式翻页，不是「加载更多」追加）
  async function loadOrdersPage(page) {
    if (!session.user || isAdmin.value) return;
    const safe = Math.max(1, page | 0);
    orders.loading = true;
    try {
      const data = await api.get(`/orders?page=${safe}&size=${orders.size}`);
      orders.items = data?.items || [];
      orders.total = data?.total || 0;
      orders.page = safe;
      await loadReviewedFlags();
    } catch (e) { /* ignore */ } finally { orders.loading = false; }
  }

  async function loadReviewedFlags() {
    const completed = (orders.items || []).filter((order) => order.status === 'COMPLETED');
    await Promise.all(completed.map(async (order) => {
      if (reviewedMap[order.id] !== undefined) return;
      try {
        const reviews = await api.get(`/reviews/orders/${order.id}`);
        reviewedMap[order.id] = (reviews || []).length > 0;
      } catch {
        reviewedMap[order.id] = false;
      }
    }));
  }

  async function payOrder(id) {
    await run(async () => {
      await api.post(`/orders/${id}/pay`);
      await Promise.all([loadOrders(), loadWallet(), loadMe()]);
    }, '支付成功');
  }

  // 「再来一单」：把历史订单的商品回填购物车。
  // 逐件回报结果 —— 老订单里常有已下架/售罄的商品，必须让用户看清"哪件没加进来、为什么"，
  // 而不是笼统地说一句失败。提示做成单行：告警弹窗 3.2 秒自动关闭且不保留换行。
  async function reorder(order) {
    if (!order || reorderLock) return;
    reorderLock = order.id;
    try {
      const res = await api.post(`/orders/${order.id}/reorder`);
      // 购物车与秒杀额度都可能变化，一起重拉，头部角标才会立刻对上
      await Promise.all([loadCart(), loadFlashSales()]);
      const skipped = res.skipped || [];
      if (skipped.length === 0) {
        showAlert({
          type: res.addedCount ? 'success' : 'info',
          title: res.addedCount ? '已加入购物车' : '没有可加入的商品',
          message: res.addedCount
            ? `${res.addedCount} 种商品已加入购物车，去结算吧。`
            : '这个订单里的商品当前都无法再次购买。',
        });
      } else {
        const detail = skipped.map((s) => `${s.productName}（${s.reason}）`).join('、');
        showAlert({
          type: 'info',
          title: res.addedCount ? '部分商品已加入购物车' : '商品暂时买不了',
          message: `${res.addedCount ? `已加入 ${res.addedCount} 种；` : ''}未能加入 ${skipped.length} 种：${detail}`,
        });
      }
    } finally {
      reorderLock = null;
    }
  }

  async function cancelOrder(id) {
    const order = (orders.items || []).find((item) => item.id === id);
    const confirmed = await askConfirm({
      title: '取消订单',
      message: '取消后订单不可恢复，已占用的库存会立即释放。',
      confirmText: '确认取消',
      danger: true,
      details: order ? [{ label: '订单号', value: order.orderNo }, { label: '金额', value: money(order.payAmount) }] : [],
    });
    if (!confirmed) return;
    await run(async () => {
      await api.post(`/orders/${id}/cancel`);
      // 取消会回退秒杀名额与限购额度，重拉后购物车里的「还能买几件」才会立刻放开
      await Promise.all([loadOrders(), loadWallet(), loadMe(), loadFlashSales()]);
    }, '订单已取消');
  }

  async function confirmReceipt(id) {
    const confirmed = await askConfirm({
      title: '确认收货',
      message: '确认后订单交易完成，款项将结算给商家，之后如需退货只能走退款申请。',
      confirmText: '确认收货',
    });
    if (!confirmed) return;
    await run(async () => {
      await api.post(`/orders/${id}/confirm-receipt`);
      await loadOrders();
    }, '已确认收货，订单完成');
  }

  function openRefundForm(orderId) {
    refundForm.orderId = orderId;
    refundForm.reason = '';
  }

  async function submitRefund(id) {
    if (!refundForm.reason.trim()) { fail('请填写退款原因'); return; }
    const confirmed = await askConfirm({
      title: '提交退款申请',
      message: '提交后需要商家审核，审核通过后款项会退回你的钱包。',
      confirmText: '提交申请',
      danger: true,
      details: [{ label: '退款原因', value: refundForm.reason.trim() }],
    });
    if (!confirmed) return;
    await run(async () => {
      await api.post(`/orders/${id}/refund-apply`, { reason: refundForm.reason.trim() });
      refundForm.orderId = null;
      await loadOrders();
    }, '退款申请已提交，等待商家处理');
  }

  async function openOrderDetail(order) {
    if (isOrderNavLocked()) return;
    setOrderNavLock(true);
    try {
      orderDetail.data = null;
      orderDetail.error = '';
      orderDetail.loading = true;
      router.push({ name: 'orderDetail', params: { id: order.id } });
      try {
        const url = isAdmin.value ? `/admin/orders/${order.id}` : `/orders/${order.id}`;
        orderDetail.data = await api.get(url);
      } catch (err) {
        orderDetail.error = err?.message || '订单详情加载失败';
      } finally {
        orderDetail.loading = false;
      }
    } finally {
      setOrderNavLock(false);
    }
  }

  function closeOrderDetail() {
    navigate(isAdmin.value ? 'admin' : 'orders');
  }

  return {
    loadOrders, loadMoreOrders, loadOrdersPage, loadReviewedFlags,
    payOrder, reorder, cancelOrder, confirmReceipt,
    openRefundForm, submitRefund, openOrderDetail, closeOrderDetail,
  };
}