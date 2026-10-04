/**
 * 收藏 + 降价提醒域
 *
 * 从 App.vue 抽出（原 1148-1265 行）。含：收藏心形状态（乐观更新 + 失败回滚）、
 * 收藏列表翻页、降价提醒列表与未读数。
 *
 * 依赖注入说明：
 * - api / setPendingAction：直接 import（无状态工具）。
 * - getSession / isAdmin：游客与管理员不进收藏/提醒，注入。
 * - getRoute / goLogin：游客点收藏要引导登录并记下回跳路由，注入。
 * - notice / fail：全局提示，注入。
 * - getView：判断当前是否在收藏页（收藏后要就地刷新列表），注入为 getter。
 */
import { reactive, ref } from 'vue';
import { api } from '../api/client';
import { setPendingAction } from './pendingAction.js';

export function useFavorites({ getSession, isAdmin, getRoute, goLogin, notice, fail, getView }) {
  const favoriteIds = ref([]);
  const favorites = reactive({ items: [], total: 0, page: 1, size: 12, loading: false });
  const priceAlerts = reactive({ items: [], total: 0, page: 1, size: 12, loading: false });
  const alertUnread = ref(0);
  const pendingFavorite = ref(null);

  // 游客/管理员一律不加载：少一个请求，也避免游客看到空壳页面
  function denied() {
    const s = typeof getSession === 'function' ? getSession() : null;
    return !s?.user || isAdmin.value;
  }

  function isFavorite(productId) {
    return favoriteIds.value.includes(Number(productId));
  }

  function setFavoriteLocal(productId, on) {
    const id = Number(productId);
    const next = new Set(favoriteIds.value.map(Number));
    if (on) next.add(id);
    else next.delete(id);
    favoriteIds.value = [...next];
  }

  async function loadFavoriteIds() {
    if (denied()) { favoriteIds.value = []; return; }
    try { favoriteIds.value = ((await api.get('/favorites/ids')) || []).map(Number); } catch (e) { /* ignore */ }
  }

  async function loadFavorites(reset = true) {
    if (denied()) { favorites.items = []; favorites.total = 0; return; }
    const nextPage = reset ? 1 : favorites.page + 1;
    favorites.loading = true;
    try {
      const data = await api.get(`/favorites?page=${nextPage}&size=${favorites.size}`);
      const items = data?.items || [];
      favorites.items = reset ? items : [...favorites.items, ...items];
      favorites.total = data?.total || 0;
      favorites.page = nextPage;
    } catch (e) { /* ignore */ } finally { favorites.loading = false; }
  }

  async function loadPriceAlerts(reset = true) {
    if (denied()) { priceAlerts.items = []; priceAlerts.total = 0; return; }
    const nextPage = reset ? 1 : priceAlerts.page + 1;
    priceAlerts.loading = true;
    try {
      const data = await api.get(`/price-alerts?page=${nextPage}&size=${priceAlerts.size}`);
      const items = data?.items || [];
      priceAlerts.items = reset ? items : [...priceAlerts.items, ...items];
      priceAlerts.total = data?.total || 0;
      priceAlerts.page = nextPage;
    } catch (e) { /* ignore */ } finally { priceAlerts.loading = false; }
  }

  // 跳到指定页码（替换式翻页，不是「加载更多」追加）
  async function loadFavoritesPage(page) {
    if (denied()) return;
    const safe = Math.max(1, page | 0);
    favorites.loading = true;
    try {
      const data = await api.get(`/favorites?page=${safe}&size=${favorites.size}`);
      favorites.items = data?.items || [];
      favorites.total = data?.total || 0;
      favorites.page = safe;
    } catch (e) { /* ignore */ } finally { favorites.loading = false; }
  }

  async function loadPriceAlertsPage(page) {
    if (denied()) return;
    const safe = Math.max(1, page | 0);
    priceAlerts.loading = true;
    try {
      const data = await api.get(`/price-alerts?page=${safe}&size=${priceAlerts.size}`);
      priceAlerts.items = data?.items || [];
      priceAlerts.total = data?.total || 0;
      priceAlerts.page = safe;
    } catch (e) { /* ignore */ } finally { priceAlerts.loading = false; }
  }

  async function loadAlertUnread() {
    if (denied()) { alertUnread.value = 0; return; }
    try {
      const data = await api.get('/price-alerts/unread-count');
      alertUnread.value = Number(data?.count || 0);
    } catch (e) { /* ignore */ }
  }

  async function markAlertsRead() {
    if (denied() || alertUnread.value <= 0) return;
    try {
      await api.post('/price-alerts/read');
      alertUnread.value = 0;
      priceAlerts.items = priceAlerts.items.map((item) => ({ ...item, isRead: true }));
      notice.value = '降价提醒已全部标记为已读';
    } catch (err) {
      fail(err?.message || '操作失败，请稍后重试');
    }
  }

  // 收藏/取消收藏：先乐观更新心形状态，失败再回滚；未登录则引导登录并在登录后自动补做
  async function toggleFavorite(product) {
    const id = Number(product?.id);
    if (!id) return;
    const s = typeof getSession === 'function' ? getSession() : null;
    if (!s?.user) {
      const r = typeof getRoute === 'function' ? getRoute() : null;
      setPendingAction({ type: 'favorite', product, redirect: (r?.name && r.name !== 'login') ? r.name : 'shop' });
      notice.value = '登录后即可收藏，降价时会提醒你';
      goLogin({ tab: 'login' });
      return;
    }
    const wasFav = isFavorite(id);
    setFavoriteLocal(id, !wasFav);
    try {
      if (wasFav) await api.delete(`/favorites/${id}`);
      else await api.post(`/favorites/${id}`);
      notice.value = wasFav ? '已取消收藏' : '已收藏，降价后会在「我的收藏」提醒你';
      await loadAlertUnread();
      const v = typeof getView === 'function' ? getView() : null;
      if (v === 'favorites') await Promise.all([loadFavorites(), loadPriceAlerts()]);
    } catch (err) {
      setFavoriteLocal(id, wasFav);
      fail(err?.message || '操作失败，请稍后重试');
    }
  }

  return {
    favoriteIds, favorites, priceAlerts, alertUnread, pendingFavorite,
    isFavorite, setFavoriteLocal,
    loadFavoriteIds, loadFavorites, loadPriceAlerts,
    loadFavoritesPage, loadPriceAlertsPage, loadAlertUnread, markAlertsRead,
    toggleFavorite,
  };
}
