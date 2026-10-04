/**
 * 后台「评价管理」
 *
 * 从 AdminPanel.vue 抽出（state 原 1832-1842、函数原 1844-1904 + loadAdminReviewUnreplied 原 1812）。
 * 补的是一条断掉的闭环：此前评价只能写（前台晒图评价），后台没有入口也没有查询接口，
 * 评价只在商品详情页出现 —— 商家看不到、回不了差评，等于用户说了话没人接。
 * 只做三件商家真会做的事：看（含按星级/未回复筛选）、回（公开回复）、藏（违规隐藏）。
 *
 * ⚠️ 两处口径别改：
 * - 筛选值 `adminReviewReplied` 直接映射成后端的 `replied` 布尔 —— 只有「未回复」才是待办。
 * - `reviewReplyDraft` 用服务端已存的回复回填，便于「看现状再改」而不是每次从空开始。
 * - 隐藏评价后该订单**仍算已评价**（不让用户重复评价），所以隐藏不是删除。
 *
 * 依赖注入：api 直接 import；run / askConfirm / showAlert 由 AdminPanel 注入。
 */
import { computed, reactive, ref } from 'vue';
import { api } from '../api/client';

export function useAdminReviews({ run, askConfirm, showAlert }) {
  const adminReviews = reactive({ items: [], total: 0, page: 1, size: 10 });
  const adminReviewSummary = ref(null);
  const adminReviewRating = ref('');
  const adminReviewReplied = ref('');
  const adminReviewKeyword = ref('');
  // 商家回复草稿：按评价 id 存，key 随列表增长（loadAdminReviews 会回填）
  const reviewReplyDraft = reactive({});

  const adminReviewTotalPages = computed(
    () => Math.max(1, Math.ceil(Number(adminReviews.total || 0) / Number(adminReviews.size || 10)))
  );

  async function loadAdminReviews() {
    const query = [`page=${adminReviews.page}`, `size=${adminReviews.size}`];
    if (adminReviewRating.value) query.push(`rating=${adminReviewRating.value}`);
    // 只有"未回复"才是待办，所以筛选值直接映射成后端的 replied 布尔
    if (adminReviewReplied.value) query.push(`replied=${adminReviewReplied.value === 'yes'}`);
    if (adminReviewKeyword.value.trim()) {
      query.push(`keyword=${encodeURIComponent(adminReviewKeyword.value.trim())}`);
    }
    const [data, summary] = await Promise.all([
      api.get(`/admin/reviews?${query.join('&')}`),
      api.get('/admin/reviews/summary'),
    ]);
    adminReviews.items = data?.items || [];
    adminReviews.total = Number(data?.total || 0);
    adminReviewSummary.value = summary || null;
    // 草稿用服务端已存的回复回填，便于"看现状再改"，而不是每次都从空开始
    adminReviews.items.forEach((row) => { reviewReplyDraft[row.id] = row.replyContent || ''; });
  }

  function searchAdminReviews() {
    adminReviews.page = 1;
    run(loadAdminReviews);
  }

  function changeAdminReviewPage(delta) {
    const next = Number(adminReviews.page) + delta;
    if (next < 1 || next > adminReviewTotalPages.value) return;
    adminReviews.page = next;
    run(loadAdminReviews);
  }

  // 侧边菜单角标：未回复评价数（"有人等你回话"）。拉失败就不显示角标，不打扰页面。
  async function loadAdminReviewUnreplied() {
    try {
      adminReviewSummary.value = await api.get('/admin/reviews/summary');
    } catch (err) {
      adminReviewSummary.value = null;
    }
  }

  async function saveReviewReply(review) {
    const content = (reviewReplyDraft[review.id] || '').trim();
    await run(async () => {
      await api.post(`/admin/reviews/${review.id}/reply`, { replyContent: content });
      await loadAdminReviews();
      showAlert({
        type: 'success',
        title: content ? '回复已发布' : '已撤回回复',
        message: content ? '该回复会立刻显示在商品详情页。' : '前台不再展示这条回复。',
      });
    });
  }

  async function toggleReviewHidden(review) {
    const hide = !review.hidden;
    const ok = await askConfirm({
      title: hide ? '隐藏这条评价？' : '恢复展示这条评价？',
      message: hide
        ? '隐藏后前台不再展示，但该订单仍算已评价（不会让用户重复评价）。'
        : '恢复后该评价会重新出现在商品详情页。',
      confirmText: hide ? '隐藏' : '恢复展示',
      danger: hide,
      details: [{ label: '商品', value: review.productName }, { label: '评价', value: review.content || '（无文字）' }],
    });
    if (!ok) return;
    await run(async () => {
      await api.post(`/admin/reviews/${review.id}/hidden`, { hidden: hide });
      await loadAdminReviews();
    });
  }

  return {
    adminReviews, adminReviewSummary, adminReviewRating, adminReviewReplied,
    adminReviewKeyword, reviewReplyDraft, adminReviewTotalPages,
    loadAdminReviews, searchAdminReviews, changeAdminReviewPage,
    loadAdminReviewUnreplied, saveReviewReply, toggleReviewHidden,
  };
}
