/**
 * 后台「限时秒杀」场次管理
 *
 * 从 AdminPanel.vue 抽出（原 1471-1641 行）。
 *
 * ⚠️ 两种模式（2026-10 起）：① sourceMode='existing' 克隆现有商品（有原商品，
 *    sale.sourceProductId 指向它）；② sourceMode='new' 从零新建一件独立秒杀商品
 *    （无原商品，productId 传 null 由后端建新商品）。编辑时**没有 sourceProductId
 *    的就是独立商品**，要用新建模式回填商品字段 —— 见 openFlashForm 的 standalone。
 *
 * 依赖注入：api 直接 import；isAdmin / fail / run / askConfirm 由 AdminPanel 注入。
 */
import { reactive, ref } from 'vue';
import { api } from '../api/client';

export function useAdminFlash({ isAdmin, fail, run, askConfirm }) {
  const adminFlashSales = ref([]);
  const flashFormOpen = ref(false);
  const flashEditingId = ref(null);
  const flashProductOptions = ref([]);
  const flashForm = reactive({
    // sourceMode：existing＝克隆现有商品（有原商品）；new＝从零新建一件独立秒杀商品（无原商品）
    sourceMode: 'existing',
    productId: 0, name: '', flashPrice: 0, totalQuota: 30, perUserLimit: 0,
    startTime: '', endTime: '', status: 1, sortNo: 0,
    // 仅 sourceMode==='new' 时使用的商品字段
    productName: '', categoryId: 0, price: 0, originalPrice: 0, coverUrl: '', unit: '',
  });

  async function loadAdminFlashSales() {
    if (!isAdmin.value) return;
    try {
      adminFlashSales.value = (await api.get('/admin/flash-sales')) || [];
    } catch (err) {
      fail(err?.message || '秒杀场次加载失败');
    }
  }

  // 秒杀商品候选：走后台商品接口（含已下架商品，便于提前排期）
  async function loadFlashProductOptions() {
    try {
      const page = await api.get('/admin/products?page=1&size=100');
      flashProductOptions.value = (page && page.items) || [];
    } catch (err) {
      flashProductOptions.value = [];
    }
  }

  function flashStateLabel(sale) {
    if (sale.state === 'RUNNING') return '进行中';
    if (sale.state === 'UPCOMING') return '未开始';
    return '已结束';
  }

  function flashStateClass(sale) {
    if (sale.state === 'RUNNING') return 'tag ok';
    if (sale.state === 'UPCOMING') return 'tag amber';
    return 'tag muted';
  }

  // 把后端返回的 ISO 时间转成 <input type="datetime-local"> 需要的 yyyy-MM-ddTHH:mm
  function toDateTimeInput(value) {
    if (!value) return '';
    const text = String(value);
    return text.length >= 16 ? text.slice(0, 16) : text;
  }

  function openFlashForm(sale) {
    if (!flashProductOptions.value.length) loadFlashProductOptions();
    // 没有 sourceProductId 的就是「独立秒杀商品」，编辑时要用新建模式回填商品字段
    const standalone = sale ? !sale.sourceProductId : false;
    flashEditingId.value = sale ? sale.id : null;
    Object.assign(flashForm, sale
      ? {
          sourceMode: standalone ? 'new' : 'existing',
          // 编辑时回填「原商品」：秒杀下拉里只有普通商品，而 sale.productId 现在是克隆出来的 FLASH 商品
          productId: Number(sale.sourceProductId || sale.productId),
          name: sale.name || '',
          flashPrice: Number(sale.flashPrice),
          totalQuota: Number(sale.totalQuota),
          perUserLimit: Number(sale.perUserLimit || 0),
          startTime: toDateTimeInput(sale.startTime),
          endTime: toDateTimeInput(sale.endTime),
          status: Number(sale.status),
          sortNo: Number(sale.sortNo || 0),
          // 独立商品：从场次响应里回填商品自身字段（分类不在响应里，编辑时不改）
          productName: sale.productName || '',
          categoryId: 0,
          price: Number(sale.price || 0),
          originalPrice: 0,
          coverUrl: sale.productCoverUrl || '',
          unit: sale.productUnit || '',
        }
      : {
          sourceMode: 'existing',
          productId: flashProductOptions.value.length ? Number(flashProductOptions.value[0].id) : 0,
          name: '', flashPrice: 0, totalQuota: 30, perUserLimit: 0,
          startTime: '', endTime: '', status: 1, sortNo: 0,
          productName: '', categoryId: 0, price: 0, originalPrice: 0, coverUrl: '', unit: '',
        });
    flashFormOpen.value = true;
  }

  function closeFlashForm() {
    flashFormOpen.value = false;
    flashEditingId.value = null;
  }

  async function saveFlashSale() {
    const standalone = flashForm.sourceMode === 'new';
    if (standalone) {
      if (!String(flashForm.productName || '').trim()) { fail('请填写商品名称'); return; }
      // 分类只在新建时要传（编辑时后端不改分类）
      if (!flashEditingId.value && !(Number(flashForm.categoryId) > 0)) { fail('请选择商品分类'); return; }
      if (!(Number(flashForm.price) > 0)) { fail('请填写大于 0 的商品售价'); return; }
      if (Number(flashForm.flashPrice) >= Number(flashForm.price)) { fail('秒杀价必须低于商品售价'); return; }
    } else if (!flashForm.productId) {
      fail('请选择秒杀商品'); return;
    }
    if (!(Number(flashForm.flashPrice) > 0)) { fail('请填写大于 0 的秒杀价'); return; }
    if (!(Number(flashForm.totalQuota) >= 1)) { fail('秒杀名额至少为 1'); return; }
    if (!flashForm.startTime || !flashForm.endTime) { fail('请选择开始与结束时间'); return; }
    if (flashForm.startTime >= flashForm.endTime) { fail('开始时间必须早于结束时间'); return; }
    const payload = {
      // 独立秒杀商品不传 productId（没有原商品），由后端从零建一件商品
      productId: standalone ? null : Number(flashForm.productId),
      name: flashForm.name.trim() || null,
      flashPrice: Number(flashForm.flashPrice),
      totalQuota: Number(flashForm.totalQuota),
      perUserLimit: Number(flashForm.perUserLimit || 0),
      startTime: flashForm.startTime,
      endTime: flashForm.endTime,
      status: Number(flashForm.status),
      sortNo: Number(flashForm.sortNo || 0),
    };
    if (standalone) {
      payload.productName = String(flashForm.productName || '').trim();
      payload.categoryId = Number(flashForm.categoryId) > 0 ? Number(flashForm.categoryId) : null;
      payload.price = Number(flashForm.price);
      payload.originalPrice = Number(flashForm.originalPrice) > 0 ? Number(flashForm.originalPrice) : null;
      payload.coverUrl = flashForm.coverUrl || null;
      payload.unit = String(flashForm.unit || '').trim() || '件';
    }
    try {
      await run(async () => {
        if (flashEditingId.value) await api.put(`/admin/flash-sales/${flashEditingId.value}`, payload);
        else await api.post('/admin/flash-sales', payload);
        await loadAdminFlashSales();
      }, flashEditingId.value ? '秒杀场次已更新' : '秒杀场次已创建');
      closeFlashForm();
    } catch (err) {
      // run() 已提示错误（如"秒杀价必须低于商品售价"/"该商品已有未结束的场次"），保持表单打开
    }
  }

  async function toggleFlashStatus(sale) {
    const next = Number(sale.status) === 1 ? 0 : 1;
    try {
      await run(async () => {
        await api.patch(`/admin/flash-sales/${sale.id}/status`, { status: next });
        await loadAdminFlashSales();
      }, next === 1 ? `「${sale.name}」已启用` : `「${sale.name}」已停用，前台不再展示`);
    } catch (err) {
      // 已在 run() 中提示
    }
  }

  async function deleteFlashSale(sale) {
    const confirmed = await askConfirm({
      title: '删除秒杀场次',
      message: `删除后「${sale.name}」将从前台秒杀区移除。已下单订单里的秒杀价与名额快照不受影响。`,
      confirmText: '确认删除',
      danger: true,
    });
    if (!confirmed) return;
    try {
      await run(async () => {
        await api.delete(`/admin/flash-sales/${sale.id}`);
        await loadAdminFlashSales();
      }, '秒杀场次已删除');
    } catch (err) {
      // 已在 run() 中提示
    }
  }

  return {
    adminFlashSales, flashFormOpen, flashEditingId, flashProductOptions, flashForm,
    loadAdminFlashSales, loadFlashProductOptions, flashStateLabel, flashStateClass,
    toDateTimeInput, openFlashForm, closeFlashForm, saveFlashSale,
    toggleFlashStatus, deleteFlashSale,
  };
}
