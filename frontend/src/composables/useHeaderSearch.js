// 页头搜索框、热搜词、页脚订阅。
//
// 搜索的真相在 filters + URL（见 useShopFilters），这里只负责「把用户刚敲的字送进路由」，
// 所以刻意不持有筛选状态。
import { ref } from 'vue';

export function useHeaderSearch({
  api, router, route,
  sameShopQuery, scrollToResultsIfNeeded, markScrollToResults,
}) {
  // 头部搜索框的输入（仅承载"用户刚敲的字"，筛选的真相在 filters + URL）
  const headerKeyword = ref('');

  // 头部搜索：跳转到商城并把关键词写入路由 query，其余条件一律丢掉（搜 = 一次全新查询，
  // 与「点分类会清掉关键词」正好对称）。⚠️ 光改 URL 不够 —— 结果区在首屏下方 900+px
  // （视口才 627px），不滚屏的话用户看到的就是"点了热搜/搜索没反应"。见 scrollToResultsIfNeeded。
  async function goSearch() {
    const kw = (headerKeyword.value || '').trim();
    const query = kw ? { kw } : {};
    // 重复点同一个热搜词：路由对相同 query 的 push 会判为重复导航直接中止 → 数据不动是对的，
    // 但不能连一点反应都没有，至少把用户带到结果区。
    if (route.name === 'shop' && sameShopQuery(query, route.query)) {
      await scrollToResultsIfNeeded();
      return;
    }
    markScrollToResults();   // 由紧随其后的 loadProducts() 消费（它比在这里猜时机更准）
    await router.push({ name: 'shop', query });
  }

  // 头部热词：一键填充并搜索
  function quickSearch(kw) {
    headerKeyword.value = kw;
    return goSearch();
  }

  // 头部「热搜」词条：原先是前端写死的 5 条（既没有"怎么才算热"的规则，后台也改不了），
  // 现由后台「热搜词管理」维护，前台只读启用项。无数据时整块隐藏（别只剩一个「热搜」空标签）。
  const hotSearches = ref([]);
  async function loadHotSearches() {
    try { hotSearches.value = (await api.get('/hot-searches')) || []; } catch { hotSearches.value = []; }
  }

  // 页脚订阅
  const subEmail = ref('');
  const subMsg = ref('');
  function footerSubscribe() {
    const v = (subEmail.value || '').trim();
    const ok = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v);
    subMsg.value = ok ? '订阅成功，优惠情报将第一时间送达' : '请输入有效的邮箱地址';
    if (ok) subEmail.value = '';
  }

  return { headerKeyword, hotSearches, subEmail, subMsg, goSearch, quickSearch, loadHotSearches, footerSubscribe };
}