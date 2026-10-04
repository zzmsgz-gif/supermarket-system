/**
 * 消息中心域
 *
 * 从 App.vue 抽出（原 1540-1624 行）。消息列表 + 未读数 + 按类型筛选 + 标记已读 +
 * 点开消息跳对应页面。管理员不进消息中心（与导航收敛后的口径一致）。
 *
 * 依赖注入说明：
 * - session / isAdmin：判断是否登录、是否管理员，两者都非本模块状态，由 App.vue 注入。
 * - navigate：点开消息按 linkView 跳页，属于路由职责。
 * - notice / fail：全局提示条（notice）与失败弹窗（fail），由 App.vue 注入。
 * - alertUnread：仅 accountDotTitle（头像红点）用到，跨域依赖收藏/降价提醒，
 *   该 computed 仍留在 App.vue，本模块不引用 alertUnread。
 */
import { reactive, ref } from 'vue';
import { api } from '../api/client';

export function useMessages({ getSession, isAdmin, navigate, notice, fail }) {
  const messages = reactive({ items: [], total: 0, page: 1, size: 12, loading: false });
  const messageUnread = ref(0);
  const messageTypeFilter = ref('');

  async function loadMessages(reset = true) {
    const sess = typeof getSession === 'function' ? getSession() : null;
    if (!sess?.user || isAdmin.value) { messages.items = []; messages.total = 0; return; }
    const nextPage = reset ? 1 : messages.page + 1;
    messages.loading = true;
    try {
      const query = messageTypeFilter.value ? `&type=${messageTypeFilter.value}` : '';
      const data = await api.get(`/messages?page=${nextPage}&size=${messages.size}${query}`);
      const items = data?.items || [];
      messages.items = reset ? items : [...messages.items, ...items];
      messages.total = data?.total || 0;
      messages.page = nextPage;
    } catch (e) { /* ignore */ } finally { messages.loading = false; }
  }

  async function loadMessageUnread() {
    const sess = typeof getSession === 'function' ? getSession() : null;
    if (!sess?.user || isAdmin.value) { messageUnread.value = 0; return; }
    try {
      const data = await api.get('/messages/unread-count');
      messageUnread.value = Number(data?.count || 0);
    } catch (e) { /* ignore */ }
  }

  function changeMessageFilter(type) {
    messageTypeFilter.value = type || '';
    loadMessages();
  }

  // 跳到指定页码（替换式翻页，不是「加载更多」追加）
  async function loadMessagesPage(page) {
    const sess = typeof getSession === 'function' ? getSession() : null;
    if (!sess?.user || isAdmin.value) return;
    const safe = Math.max(1, page | 0);
    messages.loading = true;
    try {
      const query = messageTypeFilter.value ? `&type=${messageTypeFilter.value}` : '';
      const data = await api.get(`/messages?page=${safe}&size=${messages.size}${query}`);
      messages.items = data?.items || [];
      messages.total = data?.total || 0;
      messages.page = safe;
    } catch (e) { /* ignore */ } finally { messages.loading = false; }
  }

  async function markMessagesRead() {
    const sess = typeof getSession === 'function' ? getSession() : null;
    if (!sess?.user || isAdmin.value || messageUnread.value <= 0) return;
    try {
      await api.post('/messages/read');
      messageUnread.value = 0;
      messages.items = messages.items.map((item) => ({ ...item, isRead: true }));
      notice.value = '消息已全部标记为已读';
    } catch (err) {
      fail(err?.message || '操作失败，请稍后重试');
    }
  }

  // 点开消息：先置为已读，再按 linkView 跳到对应页面
  async function openMessage(message) {
    if (!message) return;
    if (!message.isRead) {
      try {
        await api.post(`/messages/${message.id}/read`);
        message.isRead = true;
        messageUnread.value = Math.max(0, messageUnread.value - 1);
      } catch (e) { /* 已读失败不阻断跳转 */ }
    }
    const target = message.linkView;
    if (target === 'orderDetail' && message.linkRef) navigate('orderDetail', { id: Number(message.linkRef) });
    else if (target === 'coupons') navigate('coupons');
    else if (target === 'points') navigate('points');
    else if (target === 'favorites') navigate('favorites');
    else if (target === 'orders') navigate('orders');
    else if (target === 'shop') navigate('shop');
  }

  return {
    messages, messageUnread, messageTypeFilter,
    loadMessages, loadMessageUnread, changeMessageFilter,
    loadMessagesPage, markMessagesRead, openMessage,
  };
}
