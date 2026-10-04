/**
 * 后台「营销活动」管理
 *
 * 从 AdminPanel.vue 抽出（函数原 1715-1916 行、state 原 1245-1255 与 1355 行）。
 *
 * 三种活动类型（type）：
 * - FULL_REDUCTION 满减：threshold 门槛 + discount 减免金额（discount ≤ threshold）
 * - DISCOUNT       折扣：discount 为 0~1 的折扣率（0.9 = 9 折）
 * - PROMOTION      促销文案：**只占首页顶栏一个展示位，不参与计价** → 门槛/优惠一律传 null
 *
 * 三种生效范围（scope）：ALL 全场 / CATEGORY 指定类目 / PRODUCT 指定商品。
 * 前端 `productActivityTag` 只给非 ALL 的活动挂商品卡角标（全场活动由页头公告带统一宣传），
 * 与这里的 scope 选择互为配合。
 *
 * 依赖注入：api / money 直接 import；isAdmin / fail / run / askConfirm / categories 由 AdminPanel 注入
 * （categories 供「指定类目」下拉，App.vue 已持有全量分类）。
 */
import { computed, reactive, ref } from 'vue';
import { api } from '../api/client';
import { money } from '../utils/format';

export function useAdminActivities({ isAdmin, fail, run, askConfirm, categories }) {
  const adminActivities = reactive({ items: [], page: 1, size: 10, total: 0 });
  const adminActivityKeyword = ref('');
  const adminActivityJumpPage = ref(1);
  const adminActivityTotalPages = computed(() => Math.max(1, Math.ceil((adminActivities.total || 0) / (adminActivities.size || 10))));

  // 活动表单的「指定商品」下拉候选：整站拉一次（size=500 覆盖全部商品）
  const activityProducts = ref([]);
  const activityProductsLoaded = ref(false);

  const activityForm = reactive({ id: null, name: '', type: 'FULL_REDUCTION', scope: 'ALL', categoryId: 0, productId: 0, threshold: 0, discount: 0, startTime: '', endTime: '', priority: 0 });

  async function loadActivityProducts() {
    if (activityProductsLoaded.value) return;
    try {
      const data = await api.get('/products?page=1&size=500');
      activityProducts.value = (data && data.items) || [];
      activityProductsLoaded.value = true;
    } catch (e) {
      activityProducts.value = [];
    }
  }

  async function loadAdminActivities() {
    if (!isAdmin.value) return;
    const params = new URLSearchParams({
      page: String(adminActivities.page),
      size: String(adminActivities.size),
    });
    if (adminActivityKeyword.value.trim()) params.set('keyword', adminActivityKeyword.value.trim());
    const data = await api.get(`/admin/activities?${params}`);
    Object.assign(adminActivities, data);
    if (adminActivities.items.length === 0 && adminActivities.page > 1) {
      adminActivities.page -= 1;
      await loadAdminActivities();
      return;
    }
    adminActivityJumpPage.value = adminActivities.page;
  }

  async function searchAdminActivities() {
    adminActivities.page = 1;
    await loadAdminActivities();
  }

  async function changeAdminActivityPage(delta) {
    const next = adminActivities.page + delta;
    if (next < 1 || next > adminActivityTotalPages.value) return;
    adminActivities.page = next;
    await loadAdminActivities();
  }

  async function changeAdminActivityPageSize() {
    adminActivities.page = 1;
    await loadAdminActivities();
  }

  async function goAdminActivityPage() {
    const p = Number(adminActivityJumpPage.value);
    if (!Number.isInteger(p) || p < 1 || p > adminActivityTotalPages.value) {
      adminActivityJumpPage.value = adminActivities.page;
      return;
    }
    adminActivities.page = p;
    await loadAdminActivities();
  }

  function resetAdminActivitySearch() {
    adminActivityKeyword.value = '';
    adminActivities.page = 1;
    loadAdminActivities();
  }

  function resetActivityForm() {
    Object.assign(activityForm, { id: null, name: '', type: 'FULL_REDUCTION', scope: 'ALL', categoryId: 0, productId: 0, threshold: 0, discount: 0, startTime: '', endTime: '', priority: 0 });
  }

  function onActivityScopeChange() {
    activityForm.categoryId = 0;
    activityForm.productId = 0;
    if (activityForm.scope === 'PRODUCT') loadActivityProducts();
  }

  function fillActivityPeriod(days) {
    const pad = (num) => String(num).padStart(2, '0');
    const toLocalInput = (date) => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
    const start = new Date();
    const end = new Date(start.getTime() + days * 24 * 60 * 60 * 1000);
    activityForm.startTime = toLocalInput(start);
    activityForm.endTime = toLocalInput(end);
  }

  function activityTypeLabel(type) {
    if (type === 'DISCOUNT') return '折扣';
    if (type === 'PROMOTION') return '促销文案';
    return '满减';
  }

  function activityScopeLabel(scope) {
    if (scope === 'CATEGORY') return '指定类目';
    if (scope === 'PRODUCT') return '指定商品';
    return '全场';
  }

  function activityDiscountLabel(act) {
    // 纯文案活动：只占首页顶栏一个展示位，没有门槛/优惠值
    if (act.type === 'PROMOTION') return '首页顶栏文案（不参与计价）';
    const threshold = Number(act.threshold || 0);
    if (act.type === 'DISCOUNT') {
      const rate = Number(act.discount || 0);
      const zhe = Math.round(rate * 100) / 10;
      const zheStr = Number.isInteger(zhe) ? String(zhe) : zhe.toFixed(1);
      return threshold > 0 ? `${zheStr}折（满${money(threshold)}）` : `${zheStr}折`;
    }
    return `满 ${money(threshold)} 减 ${money(act.discount)}`;
  }

  function editActivity(act) {
    const toLocal = (value) => {
      if (!value) return '';
      const d = new Date(value);
      if (Number.isNaN(d.getTime())) return '';
      const pad = (n) => String(n).padStart(2, '0');
      return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
    };
    Object.assign(activityForm, {
      id: act.id,
      name: act.name,
      type: act.type,
      scope: act.scope,
      categoryId: act.categoryId || 0,
      productId: act.productId || 0,
      threshold: Number(act.threshold || 0),
      discount: Number(act.discount || 0),
      startTime: toLocal(act.startTime),
      endTime: toLocal(act.endTime),
      priority: Number(act.priority || 0),
    });
    if (act.scope === 'PRODUCT') loadActivityProducts();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  async function saveActivity() {
    const promotion = activityForm.type === 'PROMOTION';
    if (!activityForm.name.trim()) { fail('请填写活动名称'); return; }
    if (!activityForm.startTime || !activityForm.endTime) { fail('请选择有效起止时间'); return; }
    if (new Date(activityForm.startTime) >= new Date(activityForm.endTime)) { fail('结束时间必须晚于开始时间'); return; }
    if (!promotion && activityForm.scope === 'CATEGORY' && !activityForm.categoryId) { fail('请选择适用类目'); return; }
    if (!promotion && activityForm.scope === 'PRODUCT' && !activityForm.productId) { fail('请选择适用商品'); return; }
    // 纯文案活动没有门槛/优惠值（不参与计价），跳过这些校验
    if (!promotion) {
      if (activityForm.type === 'FULL_REDUCTION') {
        if (!(activityForm.threshold > 0)) { fail('满减门槛必须大于 0'); return; }
        if (!(activityForm.discount > 0)) { fail('优惠金额必须大于 0'); return; }
        if (Number(activityForm.discount) > Number(activityForm.threshold)) { fail('优惠金额不能超过门槛金额'); return; }
      } else {
        if (!(activityForm.discount > 0) || !(activityForm.discount < 1)) { fail('折扣率需在 0~1 之间，例如 0.9 表示 9 折'); return; }
      }
    }
    const payload = {
      name: activityForm.name.trim(),
      type: activityForm.type,
      scope: promotion ? 'ALL' : activityForm.scope,
      categoryId: !promotion && activityForm.scope === 'CATEGORY' ? Number(activityForm.categoryId) : null,
      productId: !promotion && activityForm.scope === 'PRODUCT' ? Number(activityForm.productId) : null,
      threshold: promotion ? null : Number(activityForm.threshold || 0),
      discount: promotion ? null : Number(activityForm.discount),
      startTime: activityForm.startTime,
      endTime: activityForm.endTime,
      priority: Number(activityForm.priority || 0),
    };
    await run(async () => {
      if (activityForm.id) {
        await api.put(`/admin/activities/${activityForm.id}`, payload);
      } else {
        await api.post('/admin/activities', payload);
      }
      resetActivityForm();
      await loadAdminActivities();
    }, activityForm.id ? '活动已更新' : '活动已创建');
  }

  async function toggleActivity(act) {
    const disabling = act.status === 1;
    const confirmed = await askConfirm({
      title: disabling ? '停用活动' : '启用活动',
      message: disabling
        ? '停用后该活动不再参与订单优惠计算，已下单的订单不受影响。'
        : '启用后该活动将重新参与订单优惠计算。',
      confirmText: disabling ? '确认停用' : '确认启用',
      danger: disabling,
      details: [
        { label: '活动', value: act.name },
        { label: '优惠力度', value: activityDiscountLabel(act) },
      ],
    });
    if (!confirmed) return;
    await run(() => api.patch(`/admin/activities/${act.id}/status`, { status: disabling ? 0 : 1 }).then(loadAdminActivities), disabling ? '活动已停用' : '活动已启用');
  }

  async function deleteActivity(act) {
    const confirmed = await askConfirm({
      title: '删除活动',
      message: '删除后该活动立即失效且不可恢复，已下单的订单不受影响。',
      confirmText: '确认删除',
      danger: true,
      details: [
        { label: '活动', value: act.name },
        { label: '优惠力度', value: activityDiscountLabel(act) },
      ],
    });
    if (!confirmed) return;
    await run(() => api.delete(`/admin/activities/${act.id}`).then(loadAdminActivities), '活动已删除');
  }

  return {
    adminActivities, adminActivityKeyword, adminActivityJumpPage, adminActivityTotalPages,
    activityProducts, activityProductsLoaded, activityForm,
    loadActivityProducts, loadAdminActivities, searchAdminActivities,
    changeAdminActivityPage, changeAdminActivityPageSize, goAdminActivityPage,
    resetAdminActivitySearch, resetActivityForm, onActivityScopeChange, fillActivityPeriod,
    activityTypeLabel, activityScopeLabel, activityDiscountLabel,
    editActivity, saveActivity, toggleActivity, deleteActivity,
  };
}
