/**
 * 后台「用户管理」：查询分页 + 启用/禁用
 *
 * 从 AdminPanel.vue 抽出（原 1756-1809 行）。
 * 禁用只影响登录，**钱包余额与历史订单不受影响**（见确认弹窗文案）。
 *
 * 依赖注入：adminUsers / adminUserKeyword / adminUserRole / adminUserStatus / adminUserJumpPage +
 *   loadAdminUsers / money / formatRole 由 AdminPanel 注入。
 */
import { computed } from 'vue';

export function useAdminUsers({ api, run, askConfirm, money, formatRole,
  adminUsers, adminUserKeyword, adminUserRole, adminUserStatus, adminUserJumpPage, loadAdminUsers }) {
  const adminUserTotalPages = computed(() => Math.max(1, Math.ceil((adminUsers.total || 0) / (adminUsers.size || 10))));

  async function searchAdminUsers() {
    adminUsers.page = 1;
    await loadAdminUsers();
  }

  async function changeAdminUserPage(delta) {
    const next = adminUsers.page + delta;
    if (next < 1 || next > adminUserTotalPages.value) return;
    adminUsers.page = next;
    await loadAdminUsers();
  }

  async function changeAdminUserPageSize() {
    adminUsers.page = 1;
    await loadAdminUsers();
  }

  async function goAdminUserPage() {
    const p = Number(adminUserJumpPage.value);
    if (!Number.isInteger(p) || p < 1 || p > adminUserTotalPages.value) {
      adminUserJumpPage.value = adminUsers.page;
      return;
    }
    adminUsers.page = p;
    await loadAdminUsers();
  }

  function resetAdminUserSearch() {
    adminUserKeyword.value = '';
    adminUserRole.value = '';
    adminUserStatus.value = '';
    adminUsers.page = 1;
    loadAdminUsers();
  }

  async function toggleUser(user) {
    const disabling = user.status === 1;
    const confirmed = await askConfirm({
      title: disabling ? '禁用账号' : '启用账号',
      message: disabling
        ? '禁用后该账号无法登录，其钱包余额与历史订单不受影响。'
        : '启用后该账号可以重新登录。',
      confirmText: disabling ? '确认禁用' : '确认启用',
      danger: disabling,
      details: [
        { label: '账号', value: user.username },
        { label: '角色', value: formatRole(user.role) },
        { label: '钱包余额', value: money(user.balance) },
      ],
    });
    if (!confirmed) return;
    await run(() => api.patch(`/admin/users/${user.id}/status`, { status: disabling ? 0 : 1 }).then(loadAdminUsers), disabling ? '账号已禁用' : '账号已启用');

  }

  return {
    adminUserTotalPages,
    searchAdminUsers, changeAdminUserPage, changeAdminUserPageSize, goAdminUserPage,
    resetAdminUserSearch, toggleUser,
  };
}
