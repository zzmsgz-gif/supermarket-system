/**
 * 后台「门店自提」管理
 *
 * 从 AdminPanel.vue 抽出（原 1358-1469 行）。门店增删改 + 启停业。
 * 前台自提门店列表（/stores）与配送时段都读这里的数据。
 *
 * 依赖注入：api 直接 import；isAdmin / fail / run / askConfirm 由 AdminPanel 注入。
 */
import { reactive, ref } from 'vue';
import { api } from '../api/client';

export function useAdminStores({ isAdmin, fail, run, askConfirm }) {
  const adminStores = ref([]);
  const storeFormOpen = ref(false);
  const storeEditingId = ref(null);
  const storeForm = reactive({
    name: '', address: '', phone: '', businessHours: '', city: '', district: '', serviceAreas: '',
    pickupNotice: '', status: 1, sortNo: 0,
  });

  async function loadAdminStores() {
    if (!isAdmin.value) return;
    try {
      adminStores.value = (await api.get('/admin/stores')) || [];
    } catch (err) {
      fail(err?.message || '门店列表加载失败');
    }
  }

  function resetStoreForm() {
    storeEditingId.value = null;
    Object.assign(storeForm, {
      name: '', address: '', phone: '', businessHours: '', city: '', district: '', serviceAreas: '',
      pickupNotice: '', status: 1, sortNo: 0,
    });
  }

  function openStoreForm(store) {
    if (store) {
      storeEditingId.value = store.id;
      Object.assign(storeForm, {
        name: store.name || '',
        address: store.address || '',
        phone: store.phone || '',
        businessHours: store.businessHours || '',
        city: store.city || '',
        district: store.district || '',
        serviceAreas: store.serviceAreas || '',
        pickupNotice: store.pickupNotice || '',
        status: Number(store.status) === 0 ? 0 : 1,
        sortNo: Number(store.sortNo || 0),
      });
    } else {
      resetStoreForm();
    }
    storeFormOpen.value = true;
  }

  function closeStoreForm() {
    storeFormOpen.value = false;
    resetStoreForm();
  }

  function storePayload() {
    return {
      name: storeForm.name.trim(),
      address: storeForm.address.trim(),
      phone: storeForm.phone.trim(),
      businessHours: storeForm.businessHours.trim(),
      city: storeForm.city.trim(),
      district: storeForm.district.trim(),
      serviceAreas: storeForm.serviceAreas.trim(),
      pickupNotice: storeForm.pickupNotice.trim(),
      status: Number(storeForm.status) === 0 ? 0 : 1,
      sortNo: Number(storeForm.sortNo) || 0,
    };
  }

  async function saveStore() {
    if (!storeForm.name.trim()) { fail('请填写门店名称'); return; }
    if (!storeForm.address.trim()) { fail('请填写门店地址'); return; }
    const payload = storePayload();
    try {
      await run(async () => {
        if (storeEditingId.value) await api.put(`/admin/stores/${storeEditingId.value}`, payload);
        else await api.post('/admin/stores', payload);
        await loadAdminStores();
      }, storeEditingId.value ? '门店已更新' : '门店已新增');
      closeStoreForm();
    } catch (err) {
      // run() 已弹出错误提示；保持表单打开便于修正
    }
  }

  async function toggleStoreStatus(store) {
    const next = Number(store.status) === 1 ? 0 : 1;
    try {
      await run(async () => {
        await api.patch(`/admin/stores/${store.id}/status`, { status: next });
        await loadAdminStores();
      }, next === 1 ? `「${store.name}」已恢复营业` : `「${store.name}」已停业，前台不再可选`);
    } catch (err) {
      // 已在 run() 中提示
    }
  }

  async function deleteStore(store) {
    const confirmed = await askConfirm({
      title: '删除门店',
      message: `删除后「${store.name}」将从自提门店列表移除，已下单订单里的门店快照不受影响。`,
      confirmText: '确认删除',
      danger: true,
    });
    if (!confirmed) return;
    try {
      await run(async () => {
        await api.delete(`/admin/stores/${store.id}`);
        await loadAdminStores();
      }, '门店已删除');
    } catch (err) {
      // 已在 run() 中提示
    }
  }

  return {
    adminStores, storeFormOpen, storeEditingId, storeForm,
    loadAdminStores, resetStoreForm, openStoreForm, closeStoreForm,
    storePayload, saveStore, toggleStoreStatus, deleteStore,
  };
}
