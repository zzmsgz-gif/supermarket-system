// 后台三个「配置型」模块：公告 / 热搜词 / 轮播位。
// 三者形态完全一致（列表 ref + 表单 reactive + 开合 ref + 增删改查），放一起；
// 都只被 AdminPanel 通过 adminCtx 消费，前台不依赖，所以不需要进 appCtx 的展示逻辑。
import { ref, reactive } from 'vue';

export function useAdminContent({ api, isAdmin, run, showAlert, askConfirm, loadHotSearches }) {
  // ===== 公告管理（后台） + 首页公告详情弹层 =====
  const adminAnnouncements = ref([]);
  const announcementForm = reactive({ id: null, title: '', content: '', type: 'NOTICE', sortOrder: 0, enabled: true });
  const announcementFormOpen = ref(false);

  async function loadAdminAnnouncements() {
    if (!isAdmin.value) return;
    try {
      adminAnnouncements.value = await api.get('/admin/announcements');
    } catch (e) {
      adminAnnouncements.value = [];
    }
  }

  function openAnnouncementForm(a) {
    Object.assign(announcementForm, a
      ? { id: a.id, title: a.title, content: a.content, type: a.type || 'NOTICE', sortOrder: a.sortOrder || 0, enabled: Number(a.enabled) === 1 }
      : { id: null, title: '', content: '', type: 'NOTICE', sortOrder: 0, enabled: true });
    announcementFormOpen.value = true;
  }
  function closeAnnouncementForm() {
    announcementFormOpen.value = false;
    announcementForm.id = null;
  }

  async function saveAnnouncement() {
    const form = announcementForm;
    if (!form.title.trim() || !form.content.trim()) { showAlert('标题和内容都要填写'); return; }
    await run(async () => {
      const payload = { title: form.title.trim(), content: form.content.trim(), type: form.type, sortOrder: Number(form.sortOrder) || 0, enabled: !!form.enabled };
      if (form.id) await api.put(`/admin/announcements/${form.id}`, payload);
      else await api.post('/admin/announcements', payload);
      closeAnnouncementForm();
      await loadAdminAnnouncements();
    }, '公告已保存');
  }

  async function toggleAnnouncement(a) {
    await run(async () => {
      await api.put(`/admin/announcements/${a.id}`, {
        title: a.title, content: a.content, type: a.type, sortOrder: a.sortOrder || 0,
        enabled: Number(a.enabled) !== 1,
      });
      await loadAdminAnnouncements();
    }, Number(a.enabled) === 1 ? '公告已停用' : '公告已启用');
  }

  async function deleteAnnouncement(a) {
    const ok = await askConfirm(`确定删除公告「${a.title}」？删除后前台立即消失。`);
    if (!ok) return;
    await run(async () => {
      await api.delete(`/admin/announcements/${a.id}`);
      await loadAdminAnnouncements();
    }, '公告已删除');
  }

  // ===== 后台「热搜词管理」：首页头部那排「热搜」词 =====
  const adminHotSearches = ref([]);
  const hotSearchForm = reactive({ id: null, keyword: '', label: '', sortOrder: 0, enabled: true });
  const hotSearchFormOpen = ref(false);

  async function loadAdminHotSearches() {
    if (!isAdmin.value) return;
    try {
      adminHotSearches.value = await api.get('/admin/hot-searches');
    } catch (e) {
      adminHotSearches.value = [];
    }
  }

  function openHotSearchForm(h) {
    Object.assign(hotSearchForm, h
      ? { id: h.id, keyword: h.keyword, label: h.label || '', sortOrder: h.sortOrder || 0, enabled: Number(h.enabled) === 1 }
      : { id: null, keyword: '', label: '', sortOrder: 0, enabled: true });
    hotSearchFormOpen.value = true;
  }

  function closeHotSearchForm() {
    hotSearchFormOpen.value = false;
    hotSearchForm.id = null;
  }

  async function saveHotSearch() {
    const form = hotSearchForm;
    if (!form.keyword.trim()) { showAlert('搜索词要填写'); return; }
    await run(async () => {
      const payload = {
        keyword: form.keyword.trim(),
        // 留空就传 null → 前台回落成关键词本身（展示词与搜索词允许不同，如显示「纯牛奶」搜「牛奶」）
        label: (form.label || '').trim() || null,
        sortOrder: Number(form.sortOrder) || 0,
        enabled: !!form.enabled,
      };
      if (form.id) await api.put(`/admin/hot-searches/${form.id}`, payload);
      else await api.post('/admin/hot-searches', payload);
      closeHotSearchForm();
      // 同时刷新前台那份，改完立刻能在头部看到（不用等刷新页面）
      await Promise.all([loadAdminHotSearches(), loadHotSearches()]);
    }, '热搜词已保存');
  }

  async function toggleHotSearch(h) {
    await run(async () => {
      await api.put(`/admin/hot-searches/${h.id}`, {
        keyword: h.keyword, label: h.label, sortOrder: h.sortOrder || 0,
        enabled: Number(h.enabled) !== 1,
      });
      await Promise.all([loadAdminHotSearches(), loadHotSearches()]);
    }, Number(h.enabled) === 1 ? '热搜词已停用' : '热搜词已启用');
  }

  async function deleteHotSearch(h) {
    const ok = await askConfirm(`确定删除热搜词「${h.label || h.keyword}」？删除后前台立即消失。`);
    if (!ok) return;
    await run(async () => {
      await api.delete(`/admin/hot-searches/${h.id}`);
      await Promise.all([loadAdminHotSearches(), loadHotSearches()]);
    }, '热搜词已删除');
  }

  // ===== 轮播位管理（后台） =====
  const adminBanners = ref([]);
  const bannerForm = reactive({ id: null, imageUrl: '', linkProductId: null, sortOrder: 0, enabled: true });
  const bannerFormOpen = ref(false);
  const bannerUploading = ref(false);

  async function loadAdminBanners() {
    if (!isAdmin.value) return;
    try {
      adminBanners.value = await api.get('/admin/banners');
    } catch (e) {
      adminBanners.value = [];
    }
  }

  function openBannerForm(b) {
    // 新建时默认排在最后（当前最大排序 + 1），避免多条都是 0 导致播放顺序随机
    const nextSort = (adminBanners.value || []).reduce((max, item) => Math.max(max, Number(item.sortOrder) || 0), 0) + 1;
    Object.assign(bannerForm, b
      ? { id: b.id, imageUrl: b.imageUrl || '', linkProductId: b.linkProductId || null, sortOrder: b.sortOrder || 0, enabled: Number(b.enabled) === 1 }
      : { id: null, imageUrl: '', linkProductId: null, sortOrder: nextSort, enabled: true });
    bannerFormOpen.value = true;
  }
  function closeBannerForm() {
    bannerFormOpen.value = false;
    bannerForm.id = null;
  }

  async function saveBanner() {
    const form = bannerForm;
    if (bannerUploading.value) { showAlert('图片还在上传中，请稍候 1-2 秒再保存'); return; }
    if (!form.imageUrl) { showAlert('请先上传轮播图片'); return; }
    await run(async () => {
      const payload = {
        imageUrl: form.imageUrl,
        linkProductId: form.linkProductId || null,
        sortOrder: Number(form.sortOrder) || 0,
        enabled: !!form.enabled,
      };
      if (form.id) await api.put(`/admin/banners/${form.id}`, payload);
      else await api.post('/admin/banners', payload);
      closeBannerForm();
      await loadAdminBanners();
    }, '轮播位已保存');
  }

  async function toggleBanner(b) {
    await run(async () => {
      await api.put(`/admin/banners/${b.id}`, {
        imageUrl: b.imageUrl,
        linkProductId: b.linkProductId,
        sortOrder: b.sortOrder || 0,
        enabled: Number(b.enabled) !== 1,
      });
      await loadAdminBanners();
    }, Number(b.enabled) === 1 ? '轮播位已停用' : '轮播位已启用');
  }

  async function deleteBanner(b) {
    const ok = await askConfirm('确定删除这个轮播位？删除后前台立即不再展示。');
    if (!ok) return;
    await run(async () => {
      await api.delete(`/admin/banners/${b.id}`);
      await loadAdminBanners();
    }, '轮播位已删除');
  }

  return {
    // 公告
    adminAnnouncements, announcementForm, announcementFormOpen,
    loadAdminAnnouncements, openAnnouncementForm, closeAnnouncementForm,
    saveAnnouncement, toggleAnnouncement, deleteAnnouncement,
    // 热搜词
    adminHotSearches, hotSearchForm, hotSearchFormOpen,
    loadAdminHotSearches, openHotSearchForm, closeHotSearchForm,
    saveHotSearch, toggleHotSearch, deleteHotSearch,
    // 轮播位
    adminBanners, bannerForm, bannerFormOpen, bannerUploading,
    loadAdminBanners, openBannerForm, closeBannerForm,
    saveBanner, toggleBanner, deleteBanner,
  };
}