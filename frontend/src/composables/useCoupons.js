// 优惠券中心：可领列表 + 我的券 + 领取动作。
// 与 useCart 里的「可用券选择」是两件事：那边服务于结算，这边服务于领券页。
export function useCoupons({ api, run, session, isAdmin, coupons, myCoupons }) {
  async function loadCoupons() {
    if (!session.user || isAdmin.value) return;
    const [available, mine] = await Promise.all([
      api.get('/coupons/available'),
      api.get(`/coupons/mine?page=1&size=${myCoupons.size}`),
    ]);
    coupons.value = available || [];
    myCoupons.items = mine?.items || [];
    myCoupons.total = mine?.total || 0;
    myCoupons.page = mine?.page || 1;
  }

  async function receiveCoupon(couponId) {
    await run(async () => {
      await api.post(`/coupons/${couponId}/receive`);
      await loadCoupons();
    }, '优惠券领取成功');
  }

  return { loadCoupons, receiveCoupon };
}