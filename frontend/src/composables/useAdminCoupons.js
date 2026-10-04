/**
 * 后台「优惠券管理」
 *
 * 从 AdminPanel.vue 抽出（原 1622-1703 行）。
 * 校验口径：优惠金额必须 > 0、不能超过使用门槛、必须选起止时间。
 * 停用后用户无法再领取，**但已领取的券仍可在有效期内使用**（见确认弹窗文案）。
 *
 * 依赖注入：adminCoupons / adminCouponKeyword / adminCouponJumpPage 数据源 +
 *   loadAdminCoupons / money 由 AdminPanel 注入。
 */
import { computed, reactive } from 'vue';

export function useAdminCoupons({ api, run, fail, askConfirm, money,
  adminCoupons, adminCouponKeyword, adminCouponJumpPage, loadAdminCoupons }) {
  const couponForm = reactive({ name: '', thresholdAmount: 0, discountAmount: 0, totalCount: 0, startTime: '', endTime: '' });

  const adminCouponTotalPages = computed(() => Math.max(1, Math.ceil((adminCoupons.total || 0) / (adminCoupons.size || 10))));

  async function searchAdminCoupons() {
    adminCoupons.page = 1;
    await loadAdminCoupons();
  }

  async function changeAdminCouponPage(delta) {
    const next = adminCoupons.page + delta;
    if (next < 1 || next > adminCouponTotalPages.value) return;
    adminCoupons.page = next;
    await loadAdminCoupons();
  }

  async function changeAdminCouponPageSize() {
    adminCoupons.page = 1;
    await loadAdminCoupons();
  }

  async function goAdminCouponPage() {
    const p = Number(adminCouponJumpPage.value);
    if (!Number.isInteger(p) || p < 1 || p > adminCouponTotalPages.value) {
      adminCouponJumpPage.value = adminCoupons.page;
      return;
    }
    adminCoupons.page = p;
    await loadAdminCoupons();
  }

  function resetAdminCouponSearch() {
    adminCouponKeyword.value = '';
    adminCoupons.page = 1;
    loadAdminCoupons();
  }

  function fillCouponPeriod(days) {
    const pad = (num) => String(num).padStart(2, '0');
    const toLocalInput = (date) => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
    const start = new Date();
    const end = new Date(start.getTime() + days * 24 * 60 * 60 * 1000);
    couponForm.startTime = toLocalInput(start);
    couponForm.endTime = toLocalInput(end);

  }

  async function saveCoupon() {
    if (!couponForm.name.trim()) { fail('请填写优惠券名称'); return; }
    if (!(couponForm.discountAmount > 0)) { fail('优惠金额必须大于 0'); return; }
    if (couponForm.discountAmount > couponForm.thresholdAmount) { fail('优惠金额不能超过使用门槛'); return; }
    if (!couponForm.startTime || !couponForm.endTime) { fail('请选择有效起止时间'); return; }
    await run(async () => {
      await api.post('/admin/coupons', {
        name: couponForm.name.trim(),
        thresholdAmount: Number(couponForm.thresholdAmount),
        discountAmount: Number(couponForm.discountAmount),
        totalCount: Number(couponForm.totalCount || 0),
        startTime: couponForm.startTime,
        endTime: couponForm.endTime,
      });
      Object.assign(couponForm, { name: '', thresholdAmount: 0, discountAmount: 0, totalCount: 0, startTime: '', endTime: '' });
      await loadAdminCoupons();
    }, '优惠券已创建');

  }

  async function toggleCoupon(coupon) {
    const disabling = coupon.status === 1;
    const confirmed = await askConfirm({
      title: disabling ? '停用优惠券' : '启用优惠券',
      message: disabling
        ? '停用后用户无法再领取这张券，已领取的券仍可在有效期内使用。'
        : '启用后用户可以重新领取这张券。',
      confirmText: disabling ? '确认停用' : '确认启用',
      danger: disabling,
      details: [
        { label: '优惠券', value: coupon.name },
        { label: '优惠力度', value: `满 ${money(coupon.thresholdAmount)} 减 ${money(coupon.discountAmount)}` },
        { label: '已领取', value: `${coupon.receivedCount} 张` },
      ],
    });
    if (!confirmed) return;
    await run(() => api.patch(`/admin/coupons/${coupon.id}/status`, { status: disabling ? 0 : 1 }).then(loadAdminCoupons), disabling ? '优惠券已停用' : '优惠券已启用');

  }

  return {
    couponForm, adminCouponTotalPages,
    searchAdminCoupons, changeAdminCouponPage, changeAdminCouponPageSize, goAdminCouponPage,
    resetAdminCouponSearch, fillCouponPeriod, saveCoupon, toggleCoupon,
  };
}
