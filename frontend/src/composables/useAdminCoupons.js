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

export function useAdminCoupons({ api, run, fail, hint, askConfirm, money,
  adminCoupons, adminCouponKeyword, adminCouponJumpPage, loadAdminCoupons }) {
  // claimType：0=每人限领一次（**默认值 = 改动前行为**）1=每天可领一次（2026-10-10）
  const couponForm = reactive({ id: null, name: '', thresholdAmount: 0, discountAmount: 0, totalCount: 0, startTime: '', endTime: '', claimType: 0 });

  // 顶层 helper：fillCouponPeriod 与 editCoupon 都要用，必须提出来（否则 editCoupon 调不到）
  const pad = (num) => String(num).padStart(2, '0');
  const toLocalInput = (date) => { const d = date instanceof Date ? date : new Date(date); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`; };

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
    const editing = !!couponForm.id;
    const payload = {
      name: couponForm.name.trim(),
      thresholdAmount: Number(couponForm.thresholdAmount),
      discountAmount: Number(couponForm.discountAmount),
      totalCount: Number(couponForm.totalCount || 0),
      claimType: Number(couponForm.claimType || 0),
      startTime: couponForm.startTime,
      endTime: couponForm.endTime,
    };
    await run(async () => {
      // 2026-10-11：有 id = 编辑（PUT），否则新建（POST）
      if (editing) await api.put(`/admin/coupons/${couponForm.id}`, payload);
      else await api.post('/admin/coupons', payload);
      resetCouponForm();
      await loadAdminCoupons();
    }, editing ? '优惠券已更新' : '优惠券已创建');

  }

  /** 表单回到「新建」态 */
  function resetCouponForm() {
    Object.assign(couponForm, { id: null, name: '', thresholdAmount: 0, discountAmount: 0, totalCount: 0, startTime: '', endTime: '', claimType: 0 });
  }

  /**
   * 编辑：把券回填到表单，表单切到「保存修改」态（2026-10-11）。
   *
   * <p>时间要转成 datetime-local 本地格式（后端返回 LocalDateTime 字符串）；
   * 已有人领取时顶栏提一句 —— 后端会拦住「下调面额/抬高门槛」这类改动，
   * 提前说明比改完才报错好。
   */
  function editCoupon(coupon) {
    Object.assign(couponForm, {
      id: coupon.id,
      name: coupon.name || '',
      thresholdAmount: Number(coupon.thresholdAmount || 0),
      discountAmount: Number(coupon.discountAmount || 0),
      totalCount: Number(coupon.totalCount || 0),
      startTime: toLocalInput(coupon.startTime),
      endTime: toLocalInput(coupon.endTime),
      claimType: Number(coupon.claimType || 0),
    });
    if (Number(coupon.receivedCount || 0) > 0) {
      hint(`已有 ${coupon.receivedCount} 人领取，优惠金额只能调大、门槛只能调低`);
    }
  }

  /** 删除（软删）：已领到手的券不受影响，仍可在有效期内使用（2026-10-11） */
  async function removeCoupon(coupon) {
    const confirmed = await askConfirm({
      title: '删除优惠券',
      message: '删除后这张券不再出现在后台列表和用户端可领列表。已领到手的券不受影响，仍可在有效期内使用。',
      confirmText: '确认删除',
      danger: true,
      details: [
        { label: '优惠券', value: coupon.name },
        { label: '优惠力度', value: `满 ${money(coupon.thresholdAmount)} 减 ${money(coupon.discountAmount)}` },
        { label: '已领取', value: `${coupon.receivedCount} 张` },
      ],
    });
    if (!confirmed) return;
    await run(async () => {
      await api.delete(`/admin/coupons/${coupon.id}`);
      // 正在编辑的那张被删了 → 表单回到新建态，否则会拿已删 id 去 PUT
      if (couponForm.id === coupon.id) resetCouponForm();
      await loadAdminCoupons();
    }, '优惠券已删除');
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
    resetAdminCouponSearch, fillCouponPeriod, saveCoupon, toggleCoupon, editCoupon, removeCoupon, resetCouponForm,
  };
}
