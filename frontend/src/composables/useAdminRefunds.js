/**
 * 后台「售后管理」：退款申请审核
 *
 * 从 AdminPanel.vue 抽出（原 1549-1620 行）。
 *
 * ⚠️ 确认弹窗里的金额口径：同意退款会**立即退回买家钱包**并回滚库存、不可撤销；
 *    `danger: approved`（同意才是危险操作，因为钱真出去了）。
 *    「已优惠」行只在 `orderSavedTotal(order) > 0` 时出现。
 *
 * 依赖注入：refundOrders / refundJumpPage / refundStatusFilter 数据源 +
 *   loadRefundOrders / loadAdminOrders / money / orderSavedTotal 由 AdminPanel 注入。
 */
import { computed, reactive } from 'vue';

export function useAdminRefunds({ api, run, askConfirm, money, orderSavedTotal,
  refundOrders, refundJumpPage, refundStatusFilter, loadRefundOrders, loadAdminOrders }) {
  const refundReviewForm = reactive({ orderId: null, approved: true, remark: '' });

  const refundTotalPages = computed(() => Math.max(1, Math.ceil((refundOrders.total || 0) / (refundOrders.size || 10))));

  async function searchRefunds() {
    refundOrders.page = 1;
    await loadRefundOrders();
  }

  async function changeRefundPage(delta) {
    const next = refundOrders.page + delta;
    if (next < 1 || next > refundTotalPages.value) return;
    refundOrders.page = next;
    await loadRefundOrders();
  }

  async function changeRefundPageSize() {
    refundOrders.page = 1;
    await loadRefundOrders();
  }

  async function goRefundPage() {
    const p = Number(refundJumpPage.value);
    if (!Number.isInteger(p) || p < 1 || p > refundTotalPages.value) {
      refundJumpPage.value = refundOrders.page;
      return;
    }
    refundOrders.page = p;
    await loadRefundOrders();
  }

  function resetRefundSearch() {
    // 与 App.vue 的 refundStatusFilter 初值保持一致（空 = 全部）。
    // 保持一致很重要：这两处不同步的话，「重置」会把筛选从「全部」弹回「申请中」，
    // 用户立刻又看不到已通过的记录，以为记录又被删了。
    refundStatusFilter.value = '';
    refundOrders.page = 1;
    loadRefundOrders();
  }

  function reviewAdminRefund(orderId, approved) {
    refundReviewForm.orderId = orderId;
    refundReviewForm.approved = approved;
    refundReviewForm.remark = '';

  }

  async function submitRefundReview(id) {
    const approved = refundReviewForm.approved;
    const order = refundOrders.items.find((item) => item.id === id);
    const confirmed = await askConfirm({
      title: approved ? '同意退款' : '拒绝退款',
      message: approved
        ? '同意后款项将立即退回买家钱包，订单关闭并回滚库存，该操作不可撤销。'
        : '拒绝后买家可以重新提交申请，你也可以稍后再次处理。',
      confirmText: approved ? '确认退款' : '确认拒绝',
      danger: approved,
      details: order
        ? [
            { label: '订单号', value: order.orderNo },
            { label: '商品小计', value: money(order.totalAmount) },
            ...(orderSavedTotal(order) > 0 ? [{ label: '已优惠', value: `- ${money(orderSavedTotal(order))}` }] : []),
            { label: '退款金额（实付）', value: money(order.payAmount) },
            { label: '退款原因', value: order.refundReason || '未填写' },
          ]
        : [],
    });
    if (!confirmed) return;
    await run(async () => {
      await api.post(`/admin/orders/${id}/refund-review`, {
        approved: refundReviewForm.approved,
        remark: refundReviewForm.remark.trim() || null,
      });
      refundReviewForm.orderId = null;
      await Promise.all([loadRefundOrders(), loadAdminOrders()]);
    }, refundReviewForm.approved ? '已同意退款，款项已退回用户钱包' : '已拒绝该退款申请');

  }

  return {
    refundReviewForm, refundTotalPages,
    searchRefunds, changeRefundPage, changeRefundPageSize, goRefundPage,
    resetRefundSearch, reviewAdminRefund, submitRefundReview,
  };
}
