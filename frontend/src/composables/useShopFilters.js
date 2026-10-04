/**
 * 首页筛选状态 ↔ URL query 双向同步（含后台 tab↔URL）
 *
 * 从 App.vue 抽出（原 1219-1408 行）。
 *
 * 起因：点分类原先只改 filters + 重拉商品，URL 一直是 /shop → 不能分享/收藏、刷新即丢；
 * 返回键也拿不到「取消筛选」的语义。现在把筛选写进 URL：
 *   /shop?category=3&kw=牛奶&min=10&max=50&sort=price_asc
 *
 * 三条关键约定（都是踩过坑的，别改）：
 * 1. ⚠️ 关键词沿用既有的 `kw` 键（头部搜索 goSearch 已在用），**别改成 q**。
 * 2. **push / replace 的取舍**：筛选的「有无」变化或分类变了 → `push`（返回键才有意义）；
 *    其余（改排序/价格）→ `replace`（避免多一条历史）。后台切模块同理。
 * 3. ⚠️ `scrollToResultsAfterLoad` 这个一次性标记**不要在 goSearch 里直接消费** ——
 *    那时导航还没落地、商品还没渲染，只能靠猜时机（setTimeout/轮询）；
 *    交给紧随其后的 `fetchProducts()` 末尾最准。
 *
 * ⚠️ 点分类要清掉搜索类条件：不清会叠加成 0 条（实测「先搜牛奶 → 再点酒水饮料」
 *    = `?category=2&kw=牛奶` → 共 0 件 +「没有符合条件的商品」）。排序刻意保留（展示偏好非筛选条件）。
 *
 * ⚠️ 筛选只管这两处（syncShopQuery 写出 / applyShopQueryFromRoute 读入），
 *    AdminPanel 内不碰路由，避免两份状态各管一半。
 *
 * 依赖注入：filters / products / productsLoading / adminMenu / getRoute / router / nextTick / api
 */
import { ref, watch, nextTick } from 'vue';
import { api } from '../api/client';

export function useShopFilters({ filters, products, productsLoading, adminMenu, adminMenuKeys, getRoute, router }) {
  const SHOP_FILTER_KEYS = ['category', 'kw', 'min', 'max', 'sort'];
  const ADMIN_TAB_DEFAULT = 'insights';   // 经营看板：与 adminMenu 初值一致，默认不写进 URL

  // 「这次筛选变更之后要把结果区带进视野」的一次性标记。
  let scrollToResultsAfterLoad = false;

  function filterQueryFromFilters() {
    const q = {};
    if (filters.categoryId) q.category = String(filters.categoryId);
    if (filters.keyword) q.kw = String(filters.keyword);
    if (filters.minPrice !== '' && filters.minPrice != null) q.min = String(filters.minPrice);
    if (filters.maxPrice !== '' && filters.maxPrice != null) q.max = String(filters.maxPrice);
    if (filters.sort) q.sort = String(filters.sort);
    return q;
  }

  function sameShopQuery(a, b) {
    return SHOP_FILTER_KEYS.every((k) => String(a[k] ?? '') === String(b[k] ?? ''));
  }

  // filters → URL。push / replace 的取舍：
  //   push —— ① 筛选的"有无"发生变化（楼层 → 分类、清空筛选），或 ② **分类变了**
  //           （「搜牛奶 → 点分类酒水饮料」、「分类A → 分类B」按返回键都应退回上一步，而不是直接跳回楼层）
  //   replace —— 其余（改排序/价格），避免改一次排序就多一条历史
  function syncShopQuery() {
    const route = getRoute();
    if (route.name !== 'shop') return;              // 别在别的页面把用户拽回 /shop
    const next = filterQueryFromFilters();
    if (sameShopQuery(next, route.query)) return;   // 已是这个 URL → 不重复导航（也避免与 watcher 打环）
    const hadFilters = SHOP_FILTER_KEYS.some((k) => route.query[k]);
    const hasFilters = SHOP_FILTER_KEYS.some((k) => next[k]);
    const categoryChanged = String(next.category ?? '') !== String(route.query.category ?? '');
    const target = { name: 'shop', query: next };
    if (hadFilters !== hasFilters || categoryChanged) router.push(target); else router.replace(target);
  }

  // URL → filters（刷新、分享链接、前进/后退都靠它）。返回 true 表示 filters 真的变了（调用方据此决定是否重拉）。
  function applyShopQueryFromRoute() {
    const q = getRoute().query || {};
    const next = {
      categoryId: (q.category || '').toString(),
      keyword: (q.kw || '').toString(),
      minPrice: q.min != null ? String(q.min) : '',
      maxPrice: q.max != null ? String(q.max) : '',
      sort: (q.sort || '').toString(),
    };
    const changed = String(filters.categoryId || '') !== next.categoryId
      || String(filters.keyword || '') !== next.keyword
      || String(filters.minPrice ?? '') !== next.minPrice
      || String(filters.maxPrice ?? '') !== next.maxPrice
      || String(filters.sort || '') !== next.sort;
    if (!changed) return false;
    Object.assign(filters, next);
    return true;
  }

  // 后台当前模块也进 URL：/admin?tab=orders
  // 目的与前台筛选一致 —— 可深链、可收藏、刷新保持、返回键语义正确（回到上一个模块而不是直接退出后台）。
  function syncAdminQuery() {
    const route = getRoute();
    if (route.name !== 'admin') return;             // 别在别的页面把用户拽回后台
    const nextTab = adminMenu.value === ADMIN_TAB_DEFAULT ? '' : adminMenu.value;
    const curTab = (route.query.tab || '').toString();
    if (curTab === nextTab) return;                 // 已一致 → 不重复导航（也避免与 watch 打环）
    const query = { ...route.query };
    if (nextTab) query.tab = nextTab; else delete query.tab;
    const target = { name: 'admin', query };
    // 切模块用 push：返回键才会「回到上一个模块」，而不是直接退出后台。
    // （实测用 replace 时切模块不占历史，按返回键会一路退回进入后台之前那一页。）
    // 纯清洗非法/失效参数（目标是没有 tab）用 replace：纠错不该占一条历史。
    if (nextTab) router.push(target); else router.replace(target);
  }

  // URL → adminMenu：URL 是唯一真相（与前台筛选同一套语义）——带合法 tab 就用它，没带 / 非法即默认看板。
  // 末尾再跑一次 syncAdminQuery 是为了把非法 tab 从地址栏洗掉，保证地址栏与界面一致。
  function applyAdminQueryFromRoute() {
    const route = getRoute();
    const raw = (route.query.tab || '').toString();
    const valid = adminMenuKeys.includes(raw);
    const tab = valid ? raw : ADMIN_TAB_DEFAULT;
    if (adminMenu.value !== tab) adminMenu.value = tab;   // 改值 → watch 去写 URL；若值本就一致则完全不导航
    // 非法 tab（?tab=hacker）直接洗掉：replace 不占历史，也不进 watch 那条路径（避免两次导航）。
    if (raw && !valid) {
      const query = { ...route.query };
      delete query.tab;
      router.replace({ name: 'admin', query });
    }
  }

  // adminMenu 一变就同步 URL（唯一出口）。数据加载交给 AdminPanel 里的 watch 处理。
  watch(adminMenu, () => { syncAdminQuery(); });

  // 点分类后把结果区带进视野。**这是必须的**：从 hero 左侧分类栏点（页面顶部）时，
  // 结果区在视口下方 900+px（视口只有 627px），屏幕上什么都不会变，用户以为点了没反应。
  // 已经在上半屏内就不打扰 —— 从楼层「查看全部」进时结果区正好落在视口顶部，再滚一下反而莫名。
  async function scrollToResultsIfNeeded() {
    await nextTick();
    const el = document.querySelector('.product-area');
    if (!el) return;
    const rect = el.getBoundingClientRect();
    if (rect.top >= 0 && rect.top <= window.innerHeight * 0.5) return;
    const sticky = document.querySelector('.site-header');   // 吸顶头部会盖住结果区首行，要减掉它的高度
    const offset = (sticky ? sticky.getBoundingClientRect().height : 0) + 12;
    window.scrollTo({ top: Math.max(0, window.scrollY + rect.top - offset), behavior: 'smooth' });
  }

  async function loadProducts() {
    productsLoading.value = true;
    try {
      await fetchProducts();
    } finally {
      productsLoading.value = false;   // 用 finally：请求失败也要收骨架屏，否则骨架一直转
    }
  }

  async function fetchProducts() {
    const params = new URLSearchParams({ page: '1', size: String(products.size) });
    if (filters.categoryId) params.set('categoryId', filters.categoryId);
    if (filters.keyword) params.set('keyword', filters.keyword);
    if (filters.minPrice !== '' && filters.minPrice != null) params.set('minPrice', String(filters.minPrice));
    if (filters.maxPrice !== '' && filters.maxPrice != null) params.set('maxPrice', String(filters.maxPrice));
    if (filters.sort) params.set('sort', filters.sort);
    const data = await api.get(`/products?${params}`);
    Object.assign(products, data);
    syncShopQuery();   // 所有筛选变更都从这里汇集出口，一处同步 URL 即可
    // 头部搜索/热搜留下的标记：等这一批商品渲染完再滚（nextTick 在 scrollToResultsIfNeeded 里）
    if (scrollToResultsAfterLoad) {
      scrollToResultsAfterLoad = false;
      await scrollToResultsIfNeeded();
    }
  }

  // 点分类 = 「导航」，不是「在当前结果里再收窄」→ 先把搜索类条件（关键词/品牌/价格）清掉。
  // ⚠️ 不清就会叠加成 0 条：实测「先搜牛奶 → 再点酒水饮料」= `?category=2&kw=牛奶` → 共 0 件 +
  // 「没有符合条件的商品，换个条件试试」，用户看到的就是"点了分类不显示商品"。
  // （价格框是 v-model 直连 filters 的，随手输个数字不回车也会被带进去。）
  // 排序刻意保留：它是展示偏好，不是筛选条件。
  async function chooseCategory(categoryId) {
    filters.categoryId = categoryId;
    filters.keyword = '';
    filters.minPrice = '';
    filters.maxPrice = '';
    await loadProducts();
    await scrollToResultsIfNeeded();
  }

  function applyFilters() {
    loadProducts();
  }

  // ⚠️ 必须连 categoryId / keyword 一起清：isFiltering 把 categoryId 也算作筛选条件，
  // 只清价格/排序会**清不掉分类** → 用户卡在「筛选结果」视图回不去楼层。
  // （价格/排序工具条已于 10-01 移除，「清空筛选」按钮是 resetFilters 仅剩的入口。）
  function resetFilters() {
    filters.categoryId = '';
    filters.keyword = '';
    filters.minPrice = '';
    filters.maxPrice = '';
    filters.sort = '';
    loadProducts();
  }

  /** 头部搜索/热搜用：标记「下次商品加载完把结果区带进视野」 */
  function markScrollToResults() { scrollToResultsAfterLoad = true; }
  function consumeScrollToResults() { const v = scrollToResultsAfterLoad; scrollToResultsAfterLoad = false; return v; }

  return {
    SHOP_FILTER_KEYS, ADMIN_TAB_DEFAULT,
    filterQueryFromFilters, sameShopQuery, syncShopQuery, applyShopQueryFromRoute,
    syncAdminQuery, applyAdminQueryFromRoute,
    scrollToResultsIfNeeded, loadProducts, fetchProducts, chooseCategory,
    applyFilters, resetFilters,
    markScrollToResults, consumeScrollToResults,
  };
}
