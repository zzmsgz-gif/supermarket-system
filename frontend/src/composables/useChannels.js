/**
 * 首页 4 个运营频道：热销 / 新品 / 推荐 / 停留榜 + 「换一批」轮换
 *
 * 从 App.vue 抽出（原 705-738 行）。
 *
 * ⚠️ 「换一批」为什么不能只是重新请求一次（这正是原来的 bug）：
 *   这 4 个端点都是**稳定的 top-N** —— listHot / listNew 直接带 @Cacheable，dwell 按浏览量排序，
 *   guess 按分数排序 —— 参数相同则结果完全相同。所以原来「换一批」只是把同一个请求再发一次，
 *   点了什么都不会变（死按钮）。
 *   现在改为：一次取 30 条入池（三个端点 @Max 均为 50，安全），前端按 6 条一屏**环形切片轮换**；
 *   池子不足两屏时按钮直接不渲染（别给一个点了没反应的控件）。
 *
 * 依赖注入：api 直接 import（无状态）。relatedProducts 属商品详情域，不在本模块。
 */
import { reactive, ref } from 'vue';
import { api } from '../api/client';

export function useChannels() {
  const hotProducts = ref([]);
  const newProducts = ref([]);
  const guessProducts = ref([]);
  const dwellRankProducts = ref([]);

  const CHANNEL_PAGE = 6;
  const channelPool = reactive({ hot: [], new: [], guess: [], dwell: [] });
  const channelCursor = reactive({ hot: 0, new: 0, guess: 0, dwell: 0 });
  const channelRefs = { hot: hotProducts, new: newProducts, guess: guessProducts, dwell: dwellRankProducts };

  // 从池中取下一屏（环形推进，到底自动回绕）
  function channelPage(key) {
    const pool = channelPool[key] || [];
    if (!pool.length) return [];
    const start = channelCursor[key] % pool.length;
    const size = Math.min(CHANNEL_PAGE, pool.length);
    const out = [];
    for (let i = 0; i < size; i += 1) out.push(pool[(start + i) % pool.length]);
    channelCursor[key] = (start + size) % pool.length;
    return out;
  }

  // 点5：首页运营栏目顺序每次进首页打乱，不要总是那个排序。
  // 4 个端点都是稳定的 top-N（带 @Cacheable / 固定排序），所以后端返回顺序每次相同。
  // 这里在「入池」时做一次纯随机洗牌（Fisher-Yates），让热门/新品/猜你/在看的展示顺序每次进首页都不一样。
  // 洗牌只发生在加载时（loadHomeChannels 仅在 App 初始化跑一次），浏览中切 tab / 点「换一批」都不会再乱跳；
  // 真正重新进首页（刷新或重开）才会换序 —— 正好对应用户「每次登录进首页顺序不同」的诉求。
  function shuffleInPlace(arr) {
    for (let i = arr.length - 1; i > 0; i -= 1) {
      const j = Math.floor(Math.random() * (i + 1));
      const t = arr[i]; arr[i] = arr[j]; arr[j] = t;
    }
    return arr;
  }

  async function loadChannel(key, url) {
    let list = [];
    try { list = (await api.get(url)) || []; } catch { list = []; }
    // 先洗牌再入池：保证「换一批」环形切片是在随机基序上转，而不是在后端固定 top-N 上转
    channelPool[key] = shuffleInPlace(list.slice());
    channelCursor[key] = 0;
    channelRefs[key].value = channelPage(key);
  }

  function rotateChannel(key) { channelRefs[key].value = channelPage(key); }

  // 池子够两屏才值得给「换一批」按钮（新品在售可能只有 5 个，那种情况按钮是骗人的）
  function channelRotatable(key) { return (channelPool[key] || []).length > CHANNEL_PAGE; }

  async function loadHot() {
    await loadChannel('hot', '/products/hot?limit=30');
  }

  async function loadNew() {
    await loadChannel('new', '/products/new?limit=30');
  }

  async function loadGuess() {
    await loadChannel('guess', '/recommendations/guess?limit=30');
  }

  async function loadDwellRank() {
    await loadChannel('dwell', '/dwell/rank?limit=30');
  }

  async function loadHomeChannels() {
    await Promise.all([loadHot(), loadNew(), loadGuess(), loadDwellRank()]);
  }

  return {
    hotProducts, newProducts, guessProducts, dwellRankProducts,
    CHANNEL_PAGE, channelPool, channelCursor, channelRefs,
    channelPage, loadChannel, rotateChannel, channelRotatable,
    loadHot, loadNew, loadGuess, loadDwellRank, loadHomeChannels,
  };
}
