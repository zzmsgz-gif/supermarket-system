// 后台各列表的数据加载：商品/订单/统计/售后/库存预警/优惠券/用户。
// 全是「拉一页 + 空页自动回退一页」的同构逻辑，放一起便于对照。
//
// ⚠️ 这些 state（adminProducts / adminOrders / …）仍定义在 App.vue —— 侧栏菜单
//    （adminMenuItems）要读它们的 total 来算角标，那是跨所有模块的聚合层，不能搬。
export function useAdminLoaders({
  api, isAdmin,
  adminProducts, adminProductKeyword, adminProductStatus, adminJumpPage,
  adminOrders, adminOrderKeyword, adminOrderStatus, adminOrderJumpPage,
  adminStatsOverview,
  refundOrders, refundStatusFilter, refundApplyingCount, refundJumpPage,
  pendingShipCount,
  stockAlerts,
  adminCoupons, adminCouponKeyword, adminCouponJumpPage,
  adminUsers, adminUserKeyword, adminUserRole, adminUserStatus, adminUserMemberLevel, adminUserJumpPage,
}) {
  async function loadAdminProducts() {
    if (!isAdmin.value) return;
    const params = new URLSearchParams({
      page: String(adminProducts.page),
      size: String(adminProducts.size),
    });
    if (adminProductKeyword.value.trim()) params.set('keyword', adminProductKeyword.value.trim());
    if (adminProductStatus.value) params.set('status', adminProductStatus.value);
    const data = await api.get(`/admin/products?${params}`);
    Object.assign(adminProducts, data);
    // 当前页被删空（如删掉最后一页最后一条）时，自动回退到上一页
    if (adminProducts.items.length === 0 && adminProducts.page > 1) {
      adminProducts.page -= 1;
      await loadAdminProducts();
      return;
    }
    adminJumpPage.value = adminProducts.page;
  }

  async function loadAdminOrders() {
    if (!isAdmin.value) return;
    const params = new URLSearchParams({
      page: String(adminOrders.page),
      size: String(adminOrders.size),
    });
    if (adminOrderKeyword.value.trim()) params.set('orderNo', adminOrderKeyword.value.trim());
    if (adminOrderStatus.value) params.set('status', adminOrderStatus.value);
    const data = await api.get(`/admin/orders?${params}`);
    Object.assign(adminOrders, data);
    // 当前页被删空（如取消最后一页最后一条）时，自动回退到上一页
    if (adminOrders.items.length === 0 && adminOrders.page > 1) {
      adminOrders.page -= 1;
      await loadAdminOrders();
      return;
    }
    adminOrderJumpPage.value = adminOrders.page;
  }

  // 仪表盘聚合统计：后端一次给全量口径（订单总数/成交额/今日/待办/状态分布），
  // 不再拿分页数据凑 KPI（旧实现请求 size=200 被后端 400 拒绝，环图永远是"暂无订单"）
  async function loadAdminStatsOverview() {
    if (!isAdmin.value) return;
    try {
      adminStatsOverview.value = await api.get('/admin/stats/overview');
    } catch (e) {
      adminStatsOverview.value = null;
    }
  }

  async function loadRefundOrders() {
    if (!isAdmin.value) return;
    // 2026-10-14 扶正新售后链路：改查 /admin/return-requests（return_request 表）。
    // 状态用 return 五态（APPLYING/WAITING_SHIP/SHIPPED_BACK/APPROVED/REJECTED），筛选为空=全部。
    const params = new URLSearchParams({
      page: String(refundOrders.page),
      size: String(refundOrders.size),
    });
    if (refundStatusFilter.value) {
      params.set('status', refundStatusFilter.value);
    }
    const data = await api.get(`/admin/return-requests?${params}`);
    // ⚠️ 竞态保护：面板组件用 `v-if` 挂在后台里，切换菜单会**卸载它**；
    // 而 `refundStatusFilter` 是父级 ref、切换菜单不会重置。若请求在卸载后才返回，
    // 下面这些写入（尤其 `refundJumpPage`，它绑在 AdminPager 上）会打到已卸载的链路上，
    // `totalPages` 算不出来 → AdminPager 抛错 → 整个面板 v-else 分支不再渲染，
    //    表现就是「售后管理打不开」，且**刷新页面才能恢复**（2026-10-07 用户报）。
    // 这里统一兜底：任何一步都把 page/jumpPage 钉成合法数字。
    Object.assign(refundOrders, {
      page: Number(data?.page) || 1,
      size: Number(data?.size) || refundOrders.size || 10,
      total: Number(data?.total) || 0,
      items: Array.isArray(data?.items) ? data.items : [],
    });
    if (refundOrders.items.length === 0 && refundOrders.page > 1) {
      refundOrders.page -= 1;
      await loadRefundOrders();
      return;
    }
    // 必须写成正整数：AdminPager 内部会算 totalPages = ceil(total/size)，
    // 拿到 undefined/NaN 就会抛。面板卸载后这个 ref 可能已无订阅者，写入无害，
    // 但值本身仍要合法 —— 组件被复用时（同一 ref）它还会被读到。
    const safePage = Number.isFinite(refundOrders.page) && refundOrders.page > 0
      ? Math.floor(refundOrders.page) : 1;
    refundOrders.page = safePage;
    refundJumpPage.value = safePage;
  }

  // 侧栏「售后管理」角标数据源：待审核（APPLYING）的售后单数量。
  async function loadRefundApplyingCount() {
    if (!isAdmin.value) return;
    try {
      const data = await api.get('/admin/return-requests?page=1&size=1&status=APPLYING');
      refundApplyingCount.value = Number(data?.total) || 0;
    } catch (e) {
      // 拉不到就保留旧值：角标是提醒性质，不该因为一次抖动归零误导管理员
    }
  }

  // 侧栏「订单管理」角标数据源：待发货（PAID，已付款等发货）的订单数量。
  // 语义与售后角标一致 = 需要管理员动手处理的量，0 时隐藏；同样复用列表接口拿 total。
  async function loadPendingShipCount() {
    if (!isAdmin.value) return;
    try {
      const data = await api.get('/admin/orders?page=1&size=1&status=PAID');
      pendingShipCount.value = Number(data?.total) || 0;
    } catch (e) {
      // 同上：提醒性质，失败保留旧值
    }
  }

  async function loadStockAlerts() {
    if (!isAdmin.value) return;
    stockAlerts.value = await api.get('/admin/stock-alerts');
  }

  async function loadAdminCoupons() {
    if (!isAdmin.value) return;
    const params = new URLSearchParams({
      page: String(adminCoupons.page),
      size: String(adminCoupons.size),
    });
    if (adminCouponKeyword.value.trim()) params.set('keyword', adminCouponKeyword.value.trim());
    const data = await api.get(`/admin/coupons?${params}`);
    Object.assign(adminCoupons, data);
    if (adminCoupons.items.length === 0 && adminCoupons.page > 1) {
      adminCoupons.page -= 1;
      await loadAdminCoupons();
      return;
    }
    adminCouponJumpPage.value = adminCoupons.page;
  }

  async function loadAdminUsers() {
    if (!isAdmin.value) return;
    const params = new URLSearchParams({
      page: String(adminUsers.page),
      size: String(adminUsers.size),
    });
    if (adminUserKeyword.value.trim()) params.set('keyword', adminUserKeyword.value.trim());
    if (adminUserRole.value) params.set('role', adminUserRole.value);
    if (adminUserStatus.value !== '') params.set('status', String(adminUserStatus.value));
    if (adminUserMemberLevel.value !== '') params.set('memberLevel', String(adminUserMemberLevel.value));
    const data = await api.get(`/admin/users?${params}`);
    Object.assign(adminUsers, data);
    if (adminUsers.items.length === 0 && adminUsers.page > 1) {
      adminUsers.page -= 1;
      await loadAdminUsers();
      return;
    }
    adminUserJumpPage.value = adminUsers.page;
  }

  return {
    loadAdminProducts, loadAdminOrders, loadAdminStatsOverview,
    loadRefundOrders, loadRefundApplyingCount, loadPendingShipCount, loadStockAlerts, loadAdminCoupons, loadAdminUsers,
  };
}