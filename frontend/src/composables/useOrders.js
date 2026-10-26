// 订单：列表、支付、取消、确认收货、退款申请、详情。
// 金额与状态文案口径对齐后端 OrderService（见 cancelOrder 里的注释）。
import { reactive } from 'vue';
import { withMinSpinner } from '../utils/format.js';

export function useOrders({
  api, run, fail, askConfirm, showAlert, money, session, isAdmin, router, navigate,
  orders, reviewedMap, reviewedItemMap, refundForm, orderDetail, paying,
  // orderNavLock 是 App.vue 的 let 变量，syncRoute 也要读 → 留在原地，这里用读写器
  isOrderNavLocked, setOrderNavLock,
  loadWallet, loadMe, loadCart, loadFlashSales, loadProducts, refreshProductDetail,
}) {
  // 「再来一单」的并发锁：老订单里常有已下架商品，逐件回报结果比笼统失败更有用
  let reorderLock = null;

  async function loadOrders(reset = true) {
    if (!session.user || isAdmin.value) { orders.items = []; orders.total = 0; return; }
    const nextPage = reset ? 1 : orders.page + 1;
    orders.loading = true;
    try {
      const statusParam = orders.statusFilter ? `&status=${encodeURIComponent(orders.statusFilter)}` : '';
      const data = await api.get(`/orders?page=${nextPage}&size=${orders.size}${statusParam}`);
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
      const statusParam = orders.statusFilter ? `&status=${encodeURIComponent(orders.statusFilter)}` : '';
      const data = await api.get(`/orders?page=${safe}&size=${orders.size}${statusParam}`);
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
        const list = reviews || [];
        reviewedMap[order.id] = list.length > 0;
        // 按订单项粒度标记已评价：前端据此逐商品显示「已评价」、并驱动「评价」按钮显隐
        for (const r of list) {
          if (r.orderItemId) reviewedItemMap[r.orderItemId] = true;
        }
      } catch {
        reviewedMap[order.id] = false;
      }
    }));
  }

  /**
     * 就地支付某笔待付款订单（订单详情页、首页秒杀卡的「去支付」都走这里）。
     *
     * 刻意用 {@link withMinSpinner} 包住：支付接口在本地几十毫秒就返回，
     * 没有这个最短转圈时长的话点击像是「没生效」，用户会重复点。
     * 同时关掉按钮防连点 —— paying 是共享状态，收银台/详情页在付款中都不该被再次触发。
     */
    async function payOrder(id) {
      if (paying.value) return;
      paying.value = true;
      try {
        await run(async () => {
          await withMinSpinner(async () => {
            await api.post(`/orders/${id}/pay`);
            // 付款落地才真正扣销量、结算限购名额 → 订单/钱包/会员/秒杀名额/商品库存销量都要重拉。
            // 漏掉 loadProducts 时，用户回到列表页看到的仍是付款前的旧库存与旧销量。
            await Promise.all([loadOrders(), loadWallet(), loadMe(), loadFlashSales(), loadProducts()]);
            await refreshProductDetail();
          });
        }, '支付成功');
      } finally {
        paying.value = false;
      }
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
      // 2026-10-12 用户要求：点击「再来一单」后跳到购物车界面。
      // 只要成功加进了商品就跳（没加进任何商品时不跳，停留在详情页看清为什么没加）。
      if (res.addedCount) navigate('cart');
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
      // 取消会回退秒杀名额与限购额度、并把库存还回商品，重拉后「还能买几件」与库存/销量立刻放开
      await Promise.all([loadOrders(), loadWallet(), loadMe(), loadFlashSales(), loadProducts()]);
      await refreshProductDetail();
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

  async function submitRefund(id, orderItemId = null) {
    if (!refundForm.reason.trim()) { fail('请填写退款原因'); return; }
    const confirmed = await askConfirm({
      title: '提交退款申请',
      message: '提交后需要商家审核，审核通过后款项按原支付方式退回。',
      confirmText: '提交申请',
      danger: true,
      details: [{ label: '退款原因', value: refundForm.reason.trim() }],
    });
    if (!confirmed) return;
    await run(async () => {
      // 2026-10-14 走新售后链路（return_request），不再是旧的 /orders/{id}/refund-apply。
      // 2026-10-15 按商品行并行售后：orderItemId 非空 = 仅退该商品行；null = 整单售后
      // （与 ReturnRequestService.apply 的口径一致：不传则按整单拦截）。
      const body = { orderId: id, reason: refundForm.reason.trim() };
      if (orderItemId != null) body.orderItemId = orderItemId;
      await api.post('/returns', body);
      refundForm.orderId = null;
      await loadOrders();
    }, '退款申请已提交，等待商家处理');
  }

  // opts.intent === 'refund'：列表页「申请退款/售后」按钮进来 —— 落地详情页后
  // 自动展开底部退款表单并滚动定位（query ?refund=1 由 OrderDetailPage 消费后即清除），
  // 用户不再需要「列表点一次 → 详情页滚到底再点一次」。
  async function openOrderDetail(order, opts = {}) {
    if (isOrderNavLocked()) return;
    setOrderNavLock(true);
    try {
      const url = isAdmin.value ? `/admin/orders/${order.id}` : `/orders/${order.id}`;
      try {
        // 先取数、填好 orderDetail.data 再跳转 —— 这样 OrderDetailPage 挂载时 data 已就绪，
        // 不再闪一下骨架屏，也不会「挂载后又拉一次」出现双请求（#715）。
        // OrderDetailPage.onMounted 守卫会在预填 id 与路由 id 一致时跳过重拉，安全。
        orderDetail.data = await api.get(url);
        orderDetail.error = '';
      } catch (err) {
        orderDetail.data = null;
        orderDetail.error = err?.message || '订单详情加载失败';
      } finally {
        orderDetail.loading = false;
      }
      router.push({
        name: 'orderDetail',
        params: { id: order.id },
        query: opts.intent === 'refund' ? { refund: '1' } : undefined,
      });
    } finally {
      setOrderNavLock(false);
    }
  }

  // 「返回」按钮：优先走浏览器历史回退，回到「进入详情页前的那一页」及其状态。
  // 管理端场景尤其关键——点开订单号进入详情时 AdminPanel 被卸载，若用 navigate('admin')
  // 不带 ?tab= 会把 adminMenu 重置成默认看板（看起来像被推到首页），且覆盖掉 adminCtx 里
  // 本可恢复的页码。router.back() 退回到 /admin?tab=refunds 那一页，tab 与 adminCtx 里的
  // 页码都会被原样恢复，等于「取消上一次进入详情的导航」。
  // 仅当没有可回退的历史（如直接粘贴 /order/:id 深链、或刷新进来）才回退到默认列表。
  function closeOrderDetail() {
    const histState = (typeof window !== 'undefined' && window.history && window.history.state) || null;
    if (histState && histState.back != null) {
      router.back();
    } else {
      navigate(isAdmin.value ? 'admin' : 'orders');
    }
  }

  return {
    loadOrders, loadMoreOrders, loadOrdersPage, loadReviewedFlags,
    payOrder, reorder, cancelOrder, confirmReceipt,
    openRefundForm, submitRefund, openOrderDetail, closeOrderDetail,
  };
}