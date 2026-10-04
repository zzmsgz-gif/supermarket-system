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
  refundOrders, refundStatusFilter, refundJumpPage,
  stockAlerts,
  adminCoupons, adminCouponKeyword, adminCouponJumpPage,
  adminUsers, adminUserKeyword, adminUserRole, adminUserStatus, adminUserJumpPage,
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
    const params = new URLSearchParams({
      page: String(refundOrders.page),
      size: String(refundOrders.size),
      refundStatus: refundStatusFilter.value,
    });
    const data = await api.get(`/admin/orders?${params}`);
    Object.assign(refundOrders, data);
    if (refundOrders.items.length === 0 && refundOrders.page > 1) {
      refundOrders.page -= 1;
      await loadRefundOrders();
      return;
    }
    refundJumpPage.value = refundOrders.page;
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
    loadRefundOrders, loadStockAlerts, loadAdminCoupons, loadAdminUsers,
  };
}