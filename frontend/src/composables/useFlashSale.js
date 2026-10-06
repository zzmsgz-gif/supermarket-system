/**
 * 限时秒杀域
 *
 * 从 App.vue 抽出（原 1267-1418 行）。含：场次拉取、秒级倒计时 tick、
 * 档期文案、每人限购名额（读后端算好的 myRemainingQuota，不在前端重算）。
 *
 * ⚠️ 倒计时基准（改这块前必读）：
 *   用本地时间戳做基准，服务端只给「剩余秒数」，前端换算成本地绝对时刻后自行走秒，
 *   这样不会因为请求延迟或前后端时钟不一致而出现倒计时跳变。
 *   ⚠️ targetAt 必须用服务端下发的**绝对时刻**（endTime/startTime），
 *      不能用 `Date.now() + countdownSeconds` 反推 —— 后者会把「浏览器时钟 vs 服务端时钟
 *      的偏差」带进来（本机实测浏览器慢约 60s，于是 10-15 00:00 的档期被显示成
 *      「10月14日 23:59 结束」，2026-09-19 发现）。countdownSeconds 只作兜底。
 *
 * 依赖注入说明：
 * - api：直接 import（无状态）。
 * - 定时器启停暴露为 startFlashTick / stopFlashTick，由 App.vue 在
 *   onMounted / onBeforeUnmount 调用（保持与拆分前一致的生命周期归属）。
 */
import { computed, ref } from 'vue';
import { api } from '../api/client';

export function useFlashSale() {
  const flashSales = ref([]);
  // 用本地时间戳做倒计时基准（见文件头注释）
  const nowTick = ref(Date.now());
  let flashTickTimer = null;

  async function loadFlashSales() {
    try {
      const list = (await api.get('/flash-sales')) || [];
      flashSales.value = list.map((f) => {
        // RUNNING 时 targetAt 是结束时刻，UPCOMING 时是开始时刻。
        const raw = f.state === 'RUNNING' ? f.endTime : f.startTime;
        const at = raw ? new Date(raw).getTime() : NaN;
        return {
          ...f,
          targetAt: Number.isFinite(at) ? at : Date.now() + Number(f.countdownSeconds || 0) * 1000
        };
      });
    } catch (e) { /* 秒杀是营销位，拉不到就不展示，不打扰用户 */ }
  }

  const runningFlashSales = computed(() => flashSales.value.filter((f) => f.state === 'RUNNING'));

  function flashRemaining(sale) {
    if (!sale || !sale.targetAt) return 0;
    return Math.max(0, Math.floor((sale.targetAt - nowTick.value) / 1000));
  }

  /* 秒杀档期文案：**只有真的快结束才逐秒倒计时**（唯一实现，首页卡片与商品详情页共用）。
     此前无论档期多长都挂「距结束 25天 03:27:28」—— 既没有紧迫感、也不像有效信息，
     还让这排卡片每秒重渲染一次。超过 24h 就退回静态的结束/开抢时刻，
     把倒计时留给真正快结束的场次（如今晚 24:00 到期的那种）。 */
  const FLASH_TICK_WINDOW_SECONDS = 24 * 3600;

  function flashDeadlineText(sale) {
    if (!sale || !sale.targetAt) return '';
    const running = sale.state === 'RUNNING';
    const left = flashRemaining(sale);
    if (left > FLASH_TICK_WINDOW_SECONDS) {
      const d = new Date(sale.targetAt);
      const pad = (n) => String(n).padStart(2, '0');
      const when = `${d.getMonth() + 1}月${d.getDate()}日 ${pad(d.getHours())}:${pad(d.getMinutes())}`;
      return running ? `${when} 结束` : `${when} 开抢`;
    }
    return running ? `距结束 ${formatDuration(left)}` : `距开始 ${formatDuration(left)}`;
  }

  // 秒杀档期可能跨天，MM:SS 不够用，这里给到「天/时/分/秒」
  function formatDuration(seconds) {
    const s = Math.max(0, Math.floor(seconds || 0));
    const days = Math.floor(s / 86400);
    const hh = String(Math.floor((s % 86400) / 3600)).padStart(2, '0');
    const mm = String(Math.floor((s % 3600) / 60)).padStart(2, '0');
    const ss = String(s % 60).padStart(2, '0');
    return days > 0 ? `${days}天 ${hh}:${mm}:${ss}` : `${hh}:${mm}:${ss}`;
  }

  function startFlashTick() {
    if (flashTickTimer) return;
    flashTickTimer = setInterval(() => { nowTick.value = Date.now(); }, 1000);
  }

  function stopFlashTick() {
    if (flashTickTimer) { clearInterval(flashTickTimer); flashTickTimer = null; }
  }

  // ===== 秒杀限购：把「还能买几件」提前暴露出来，而不是等用户点结算才报错 =====

  /** 该商品此刻进行中的秒杀（同一商品同时只会有一个进行中的场次） */
  function flashSaleOfProduct(productId) {
    const id = Number(productId);
    return flashSales.value.find((f) => f.state === 'RUNNING' && Number(f.productId) === id) || null;
  }

  /**
   * 该商品「我还能买几件」—— 直接读后端算好的 myRemainingQuota（= 每人限购 − 我的已购，
   * 已把未付款订单占用的名额算进去），不在前端重算一遍，避免两处口径漂移。
   * 返回 null 表示不受限购约束（游客未登录，或该场次不限购）。
   */
  function flashLimitOfProduct(productId) {
    const sale = flashSaleOfProduct(productId);
    if (!sale || sale.myRemainingQuota === null || sale.myRemainingQuota === undefined) return null;
    return Number(sale.myRemainingQuota);
  }

  /**
   * 购物车里某行最多能加到几件：受库存、秒杀每人限购名额、秒杀全场剩余名额三重约束。
   * 秒杀品是纯折扣通道，名额用完后不能再加（连原价都不行），所以这里用「还能买几件」夹住数量。
   * 全场名额（remainingQuota）同样要算：不限购的场次里 myRemainingQuota 是 null，
   * 但整场抢完时也应该加不动，否则用户能往车里塞一堆、结算才被拒。
   * 结果至少为 1：即使用户已经超了（比如后台调小了限购），也要让他能把数量改小或删除。
   */
  function cartQtyMax(item) {
    const stock = Number(item?.stock || 0);
    const left = flashLimitOfProduct(item?.productId);
    const sale = flashSaleOfProduct(item?.productId);
    const caps = [stock];
    if (left !== null) caps.push(left);
    if (sale && sale.remainingQuota !== null && sale.remainingQuota !== undefined) {
      caps.push(Number(sale.remainingQuota));
    }
    const cap = Math.min(...caps.filter((v) => Number.isFinite(v)));
    return cap > 0 ? cap : 1;
  }

  /**
   * 该行是否达到秒杀限购上限（仅用于提示，不再禁用 + 号）：达到后继续加只按原价。
   */
  function cartQtyCapped(item) {
    const limit = flashLimitOfProduct(item?.productId);
    return limit !== null && Number(item?.quantity || 0) >= limit;
  }

  /**
   * 秒杀自动拆分提示文案：
   * - 部分秒杀：前 N 件秒杀价，超出 M 件按原价
   * - 名额已用完（仍在进行中的限购场次）：本件按原价
   * 其余情况（纯原价 / 整行都秒杀）返回空串。
   */
  function flashSplitNote(item) {
    if (!item) return '';
    const sale = flashSaleOfProduct(item.productId);
    if (!sale) return '';
    const flashQty = Number(item.flashQty || 0);
    const qty = Number(item.quantity || 0);
    if (flashQty > 0 && flashQty < qty) {
      return `前 ${flashQty} 件限时秒杀价，超出 ${qty - flashQty} 件按原价`;
    }
    if (flashQty === 0
        && sale.myRemainingQuota !== null && sale.myRemainingQuota !== undefined) {
      return `秒杀名额已用完（每人限购 ${sale.perUserLimit} 件），本件按原价`;
    }
    return '';
  }

  /** 该行是否需要拆成「秒杀段 + 原价段」两段展示 */
  function isFlashSplit(item) {
    return Number(item?.flashQty || 0) > 0 && Number(item.flashQty) < Number(item.quantity || 0);
  }

  /**
   * 因限购被挡时的统一文案（措辞与后端 409 对齐，用户在前端预检和后端拒绝时看到的是同一句话）。
   *
   * left = 后端算的「还能买几件」：它只扣了订单已占用的，没有扣购物车里已有的件数，
   * 所以说「购物车里最多放几件」而不是「你还能买几件」—— 否则与用户眼前看到的数量对不上，
   * 会被当成系统算错。
   */
  function flashLimitMessage(productId, left) {
    const sale = flashSaleOfProduct(productId);
    const limit = Number(sale?.perUserLimit || 0);
    const cap = `「${sale?.name || '该秒杀商品'}」每人限购 ${limit} 件`;
    if (left <= 0) return cap + '，你已经买满了';
    const bought = limit - Number(left);
    return cap + `，购物车里最多放 ${left} 件` + (bought > 0 ? `（已下单占用 ${bought} 件）` : '');
  }

  return {
    flashSales, nowTick, runningFlashSales, FLASH_TICK_WINDOW_SECONDS,
    loadFlashSales, flashRemaining, flashDeadlineText, formatDuration,
    startFlashTick, stopFlashTick,
    flashSaleOfProduct, flashLimitOfProduct, cartQtyMax, cartQtyCapped,
    flashSplitNote, isFlashSplit, flashLimitMessage,
  };
}
