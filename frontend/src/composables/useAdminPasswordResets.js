/**
 * 后台「找回密码申请」人工处理
 *
 * 从 AdminPanel.vue 抽出（原 1809-1947 行）。用户提交找回申请（POST /auth/password-reset-request），
 * 管理员在这里核对身份后生成**一次性临时密码**或驳回。
 *
 * ⚠️ 安全约定（别改）：
 * - 临时密码只在这一次响应里返回，库里只有 BCrypt 哈希；`passwordResetResult` 关闭即销毁。
 * - 确认弹窗明确提示「临时密码只显示这一次，请当场电话告知用户，不要截图外发」。
 * - 状态全部本地化在 composable 内，**不进 App.vue 那行超长 adminCtx**（原注释的既定原则）。
 *
 * 依赖注入：api 直接 import；run / fail / askConfirm / notice 由 AdminPanel 注入。
 */
import { computed, reactive, ref } from 'vue';
import { api } from '../api/client';

export function useAdminPasswordResets({ run, fail, askConfirm, notice }) {
  const passwordResets = reactive({ items: [], page: 1, size: 10, total: 0, pending: 0 });
  const passwordResetStatus = ref('PENDING');
  const passwordResetJumpPage = ref(1);
  // 重置成功后拿到的临时密码：只存在于这一次响应里，关闭即销毁（库里只有 BCrypt 哈希）
  const passwordResetResult = ref(null);

  const passwordResetTotalPages = computed(() => Math.max(1, Math.ceil((passwordResets.total || 0) / (passwordResets.size || 10))));

  const PASSWORD_RESET_STATUS_LABELS = { PENDING: '待处理', DONE: '已重置', REJECTED: '已驳回' };

  function passwordResetStatusLabel(status) {
    return PASSWORD_RESET_STATUS_LABELS[status] || status || '-';
  }

  function passwordResetStatusClass(status) {
    if (status === 'PENDING') return 'warn';
    if (status === 'DONE') return 'ok';
    return 'muted';
  }

  async function loadPasswordResets() {
    const query = [`page=${passwordResets.page}`, `size=${passwordResets.size}`];
    if (passwordResetStatus.value) query.push(`status=${passwordResetStatus.value}`);
    const data = await api.get(`/admin/password-reset-requests?${query.join('&')}`);
    passwordResets.items = data?.items || [];
    passwordResets.total = Number(data?.total || 0);
    await loadPasswordResetPendingCount();
  }

  // 侧边菜单角标：待处理数量（拉失败不影响列表本身）
  async function loadPasswordResetPendingCount() {
    try {
      const count = await api.get('/admin/password-reset-requests/pending-count');
      passwordResets.pending = Number(count || 0);
    } catch (err) {
      passwordResets.pending = 0;
    }
  }

  async function searchPasswordResets() {
    passwordResets.page = 1;
    passwordResetJumpPage.value = 1;
    await run(() => loadPasswordResets());
  }

  async function changePasswordResetPage(delta) {
    const next = passwordResets.page + delta;
    if (next < 1 || next > passwordResetTotalPages.value) return;
    passwordResets.page = next;
    await run(() => loadPasswordResets());
  }

  async function changePasswordResetPageSize() {
    passwordResets.page = 1;
    await run(() => loadPasswordResets());
  }

  async function goPasswordResetPage() {
    const p = Number(passwordResetJumpPage.value);
    if (!Number.isInteger(p) || p < 1 || p > passwordResetTotalPages.value) {
      passwordResetJumpPage.value = passwordResets.page;
      return;
    }
    passwordResets.page = p;
    await run(() => loadPasswordResets());
  }

  async function confirmResetPassword(item) {
    const confirmed = await askConfirm({
      title: '重置该账号密码',
      message: `将为「${item.username}」生成一次性临时密码，并把该账号标记为「首次登录必须改密」。`
        + '临时密码只显示这一次，请当场电话告知用户，不要截图外发。',
      details: [
        { label: '账号', value: item.username },
        { label: '昵称', value: item.nickname || '-' },
        { label: '账号预留手机号', value: item.phone || '未填写' },
        { label: '申请人联系方式', value: item.contact || '未填写' },
      ],
      confirmText: '确认重置',
    });
    if (!confirmed) return;
    try {
      await run(async () => {
        passwordResetResult.value = await api.post(`/admin/password-reset-requests/${item.id}/reset`, {});
        await loadPasswordResets();
      }, '临时密码已生成，请立即转告用户');
    } catch (err) {
      fail(err?.message || '重置失败，请稍后重试');
    }
  }

  async function confirmRejectPasswordReset(item) {
    const confirmed = await askConfirm({
      title: '驳回找回申请',
      message: `确定驳回「${item.username}」的找回密码申请吗？驳回后用户可重新提交。`,
      confirmText: '确认驳回',
      danger: true,
    });
    if (!confirmed) return;
    try {
      await run(async () => {
        await api.post(`/admin/password-reset-requests/${item.id}/reject`, { remark: '身份核对未通过' });
        await loadPasswordResets();
      }, '申请已驳回');
    } catch (err) {
      fail(err?.message || '驳回失败，请稍后重试');
    }
  }

  async function copyTempPassword() {
    const text = passwordResetResult.value?.tempPassword;
    if (!text) return;
    try {
      await navigator.clipboard.writeText(text);
      notice.value = '临时密码已复制到剪贴板';
    } catch (err) {
      fail('复制失败，请手动选中复制');
    }
  }

  return {
    passwordResets, passwordResetStatus, passwordResetJumpPage,
    passwordResetResult, passwordResetTotalPages, PASSWORD_RESET_STATUS_LABELS,
    passwordResetStatusLabel, passwordResetStatusClass,
    loadPasswordResets, loadPasswordResetPendingCount,
    searchPasswordResets, changePasswordResetPage, changePasswordResetPageSize,
    goPasswordResetPage, confirmResetPassword, confirmRejectPasswordReset, copyTempPassword,
  };
}
