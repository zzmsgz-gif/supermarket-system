/**
 * 后台「售后管理」：售后单审核（2026-10-14 扶正 return_request 新链路）
 *
 * 从旧的「订单退款审核」（/admin/orders/{id}/refund-review，同意即秒退）切换为
 * return_request 完整流程（/admin/return-requests）：
 *   审核 APPLYING → 驳回 / 直接放款(未收到货) / 待寄回(已收到货)
 *   待寄回 WAITING_SHIP：等用户寄回（用户在「我的售后」填单号）
 *   已寄回 SHIPPED_BACK：商家「确认收货」后才放款
 *
 * ⚠️ 是否需寄回：管理员在审核弹窗里手动选择为准（needReturn 透传给后端）；
 *   若选择「自动」，后端按订单收货状态兜底（未收到货 PAID→不寄回直接退；已发货/收货→需寄回）。
 *   运费承担方概念已移除（2026-10-22）：退货运费统一按平台规则处理，不再单独判定。
 *
 * 依赖注入：refundOrders / refundJumpPage / refundStatusFilter 数据源 +
 *   loadRefundOrders / loadRefundApplyingCount / loadAdminOrders / money 由 AdminPanel 注入。
 */
import { computed, reactive } from 'vue';

export function useAdminRefunds({ api, run, askConfirm, money,
  refundOrders, refundJumpPage, refundStatusFilter, loadRefundOrders, loadRefundApplyingCount, loadAdminOrders }) {
  // 审核表单：是否同意 +（驳回必填）理由 +（同意时）寄回选择。
  // needReturnMode: 'auto'=按订单状态自动(不传 needReturn，后端兜底)；'yes'=强制要求寄回；'no'=免寄回直接退。
  const refundReviewForm = reactive({ id: null, approved: true, remark: '', needReturnMode: 'auto' });

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
    // 默认 = 待审核（与 App.vue 的 refundStatusFilter 初值一致，两边要同步）
    refundStatusFilter.value = 'APPLYING';
    refundOrders.page = 1;
    loadRefundOrders();
  }

  function reviewAdminRefund(id, approved) {
    refundReviewForm.id = id;
    refundReviewForm.approved = approved;
    refundReviewForm.remark = '';
    refundReviewForm.needReturnMode = 'auto';
  }

  function reviewSubmitDisabled() {
    // 驳回必须填理由；同意无需额外字段（是否寄回/运费由后端按订单收货状态自动判定）
    if (!refundReviewForm.approved) return !refundReviewForm.remark.trim();
    return false;
  }

  async function submitRefundReview(id) {
    const approved = refundReviewForm.approved;
    const req = refundOrders.items.find((item) => item.id === id);
    const confirmed = await askConfirm({
      title: approved ? '同意退款' : '驳回申请',
      message: approved
        ? '请在下方选择「是否寄回」；默认按订单收货状态自动判定，可手动改判（如已发货的质量问题判不退件）。'
        : '驳回后买家可重新申请售后或联系客服申诉。',
      confirmText: approved ? '确认同意' : '确认驳回',
      danger: approved,
      details: req
        ? [
            { label: '售后单', value: '#' + req.id },
            { label: '订单号', value: req.orderNo || '-' },
            { label: '退款金额', value: money(req.refundAmount) },
            { label: '申请原因', value: req.reason || '未填写' },
          ]
        : [],
    });
    if (!confirmed) return;
    const body = { approved, remark: refundReviewForm.remark.trim() || null };
    if (approved) {
      // needReturn 仅在管理员手动选定(是/否)时传；选「自动」则留空让后端按订单状态兜底。
      if (refundReviewForm.needReturnMode === 'yes') body.needReturn = true;
      else if (refundReviewForm.needReturnMode === 'no') body.needReturn = false;
    }
    await run(async () => {
      await api.post(`/admin/return-requests/${id}/review`, body);
      refundReviewForm.id = null;
      await Promise.all([loadRefundOrders(), loadRefundApplyingCount(), loadAdminOrders()]);
    }, approved ? '已同意该售后申请' : '已驳回该申请');
  }

  // 确认收到退货 → 放款（仅 SHIPPED_BACK 状态可点）
  async function confirmReturnReceived(id) {
    const req = refundOrders.items.find((item) => item.id === id);
    const confirmed = await askConfirm({
      title: '确认收到退货',
      message: '确认后款项立即退回买家（原路返回），订单关闭并回滚库存，不可撤销。',
      confirmText: '确认并退款',
      danger: true,
      details: req
        ? [
            { label: '订单号', value: req.orderNo || '-' },
            { label: '退款金额', value: money(req.refundAmount) },
            { label: '运单号', value: req.trackingNo || '-' },
          ]
        : [],
    });
    if (!confirmed) return;
    await run(async () => {
      await api.post(`/admin/return-requests/${id}/confirm-received`, { remark: '商家确认收货' });
      await Promise.all([loadRefundOrders(), loadRefundApplyingCount(), loadAdminOrders()]);
    }, '已确认收货，款项已退回买家');
  }

  return {
    refundReviewForm, refundTotalPages, reviewSubmitDisabled,
    searchRefunds, changeRefundPage, changeRefundPageSize, goRefundPage,
    resetRefundSearch, reviewAdminRefund, submitRefundReview, confirmReturnReceived,
  };
}
