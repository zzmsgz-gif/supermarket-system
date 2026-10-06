// 购物车：金额合计、增删改、可用券选择。
// 计价口径全部对齐后端 CartService / OrderService（见各 computed 的注释）。
import { reactive, ref, computed } from 'vue';

export function useCart({
  api, run, fail, askConfirm, session, isAdmin, cart, cartStore,
  setNotice, // (v) => { notice.value = v } —— notice 是 ref，只写不读，传写入器更明确
  onAddedFeedback, // (src, coverUrl) => void —— 由 App.vue 组装成 takeAddSource()+flyToCart()
  guestAdd, guestRemoveItem, guestClear, persistGuestFromItems, recomputeCartTotals, refreshGuestCartView,
  getFlashLimitOfProduct, getFlashSaleOfProduct, getCartQtyMax,
}) {
  const myCoupons = reactive({ items: [], total: 0, page: 1, size: 12, loading: false });
  const usableCoupons = ref([]);
  const selectedUserCouponId = ref('');
  const userOptedOutCoupon = ref(false);
  const cartSyncTimers = {};

  // 购物车实时合计：只累加「已勾选」商品（与后端 selectedAmount 口径一致）。
  // 这里仅用于「券门槛判定」（够不够满减）；页面展示用的那份在 useCartTotals（口径已统一到会员成交价）。
  const cartLocalTotal = computed(() => (cart.items || [])
    .filter((item) => item.selected !== false)
    .reduce((sum, item) => sum + Number(item.productPrice || 0) * Number(item.quantity || 0), 0));

  // 注：这里曾另有一份 cartOriginalSave / cartSelectedQty，但它们既没导出也没被引用，
  // 且 cartOriginalSave 调用的 itemOriginalSave 未 import —— 压缩后就是个随机的 "xxx is not a function"
  // 定时炸弹（与 2026-10-06 详情页加购报错的成因同类）。展示口径统一由 useCartTotals 负责，故删除。

  async function addToCart(product) {
    if (isAdmin.value) { fail('管理员只能查看上架商品，不能加入购物车'); return; }
    if (!session.user) {
      const ok = await guestAdd(product, 1);
      // 不跳购物车（见下方 run() 里的说明）。⚠️ 游客路径原先**没有**提示条 —— 它是靠"跳页"当反馈的，
      // 现在不跳了就必须补一句文案，否则点完像没反应。
      if (ok) { onAddedFeedback(null, product.coverUrl); setNotice('已加入购物车'); }
      return;
    }
    const stock = Number(product.stock || 0);
    if (stock <= 0) { fail(`${product.name || '该商品'} 已售罄，暂时无法加入购物车`); return; }
    const existing = (cart.items || []).find((i) => i.productId === product.id);
    const currentQty = existing ? Number(existing.quantity || 0) : 0;
    if (currentQty + 1 > stock) {
      fail(`库存不足：${product.name || '该商品'} 仅剩 ${stock} 件，购物车中已有 ${currentQty} 件`, '库存不足');
      return;
    }
    // 秒杀品是纯折扣通道：名额用完后彻底不能再加购（连原价都不行），只能去原商品按原价买。
    const flashLeft = getFlashLimitOfProduct(product.id);
    if (flashLeft !== null && currentQty + 1 > flashLeft) {
      const fsName = getFlashSaleOfProduct(product.id)?.name || '该秒杀商品';
      const fsLimit = getFlashSaleOfProduct(product.id)?.perUserLimit || flashLeft;
      fail(`「${fsName}」每人限购 ${fsLimit} 件，已达上限，请去原商品按原价购买`, '超出限购');
      return;
    }
    // 全场名额抢完（不限购场次 myRemainingQuota 为 null，只能看 remainingQuota）
    const fsSale = getFlashSaleOfProduct(product.id);
    if (fsSale && fsSale.remainingQuota !== null && fsSale.remainingQuota !== undefined
        && currentQty + 1 > Number(fsSale.remainingQuota)) {
      fail(`「${fsSale.name || '该秒杀商品'}」本场名额已抢完（共 ${fsSale.totalQuota} 件），请关注下一场`, '超出限购');
      return;
    }
    await run(async () => {
      await api.post('/cart/items', { productId: product.id, quantity: 1 });
      await loadCart();
      // 刻意**不** navigate('cart')：首页点「＋」只是"加入"，用户多半还在继续挑。
      // 反馈交给三样东西 —— 商品图飞进页头购物车、角标数字弹一下、提示条「已加入购物车」；
      // 想去购物车就点页头那个胶囊（角标刚弹过，本身就是指路）。
      // 注：详情页的「加入购物车」按钮仍会跳购物车（那是用户明确决定购买的位置），两边故意不同。
      onAddedFeedback(null, product.coverUrl);
    }, '已加入购物车');
  }

  async function loadCart() {
    if (isAdmin.value) return;
    if (!session.user) { await refreshGuestCartView(); return; }
    const cartData = await api.get('/cart');
    Object.assign(cart, cartData);
    cartStore.setCart(cartData);
    await loadMyCoupons();
    await loadUsableCoupons();
    // 首次进入：未手动放弃用券且有可用券时，自动选用优惠力度最大的券
    userOptedOutCoupon.value = false;
    autoSelectCoupon();
  }

  async function loadMyCoupons(reset = true) {
    if (!session.user || isAdmin.value) { myCoupons.items = []; myCoupons.total = 0; return; }
    const nextPage = reset ? 1 : myCoupons.page + 1;
    myCoupons.loading = true;
    try {
      const data = await api.get(`/coupons/mine?page=${nextPage}&size=${myCoupons.size}`);
      const items = data?.items || [];
      myCoupons.items = reset ? items : [...myCoupons.items, ...items];
      myCoupons.total = data?.total || 0;
      myCoupons.page = nextPage;
    } catch (e) { /* ignore */ } finally { myCoupons.loading = false; }
  }

  async function loadMoreMyCoupons() {
    await loadMyCoupons(false);
  }

  // 跳到指定页码（替换式翻页，不是「加载更多」追加）
  async function loadMyCouponsPage(page) {
    if (!session.user || isAdmin.value) return;
    const safe = Math.max(1, page | 0);
    myCoupons.loading = true;
    try {
      const data = await api.get(`/coupons/mine?page=${safe}&size=${myCoupons.size}`);
      myCoupons.items = data?.items || [];
      myCoupons.total = data?.total || 0;
      myCoupons.page = safe;
    } catch (e) { /* ignore */ } finally { myCoupons.loading = false; }
  }

  async function loadUsableCoupons() {
    if (!session.user || isAdmin.value) {
      usableCoupons.value = [];
      selectedUserCouponId.value = '';
      return;
    }
    const amount = cartLocalTotal.value;
    usableCoupons.value = await api.get(`/coupons/usable?amount=${amount}`);
    // 当前选中的券若因金额变化而不再满足条件，自动取消；随后在仍满足条件、且用户未显式放弃时补选最优券
    if (selectedUserCouponId.value && !usableCoupons.value.some((coupon) => coupon.id === selectedUserCouponId.value)) {
      selectedUserCouponId.value = '';
    }
    autoSelectCoupon();
  }

  // 该券是否满足当前购物车金额门槛（可点击/可自动使用）
  function couponEligible(coupon) {
    return usableCoupons.value.some((item) => item.id === coupon.id);
  }

  // 还差多少金额可用
  function couponShortfall(coupon) {
    return Math.max(Number(coupon.thresholdAmount || 0) - cartLocalTotal.value, 0);
  }

  function autoSelectCoupon() {
    if (userOptedOutCoupon.value || selectedUserCouponId.value) return;
    const best = usableCoupons.value.slice()
      .sort((a, b) => Number(b.discountAmount || 0) - Number(a.discountAmount || 0))[0];
    if (best) selectedUserCouponId.value = best.id;
  }

  function selectCoupon(coupon) {
    if (!couponEligible(coupon)) return;
    selectedUserCouponId.value = coupon.id;
    userOptedOutCoupon.value = false;
  }

  function chooseNoCoupon() {
    selectedUserCouponId.value = '';
    userOptedOutCoupon.value = true;
  }

  // 数量改变：价格即时变化（v-model 已更新 item.quantity，计算属性即时重算），
  // 同时防抖把新数量同步到后端，并在合适时机刷新可用券列表。
  // 修复：加入库存与秒杀限购上限校验（cartQtyMax = min(库存, 还能买几件)）。
  // 原实现是「先夹到 max 再加 delta」，到顶时反而会多给一件（5→6），这里改成「先加再夹」。
  function stepQty(item, delta) {
    const max = getCartQtyMax(item);
    const current = Math.max(1, Number(item.quantity) || 1);
    const next = Math.min(Math.max(current + delta, 1), Math.max(max, 1));
    if (next === current) return;   // 已到顶，不发这次必然被拒的请求
    item.quantity = next;
    onQtyInput(item);
  }

  function onQtyChange(item) {
    const max = getCartQtyMax(item);
    let q = Number(item.quantity) || 1;
    if (q < 1) q = 1;
    if (q > max) q = max;   // 仅按库存夹，秒杀超出部分按原价，不再夹限购
    item.quantity = q;
    onQtyInput(item);
  }

  async function onQtyInput(item) {
    clearTimeout(cartSyncTimers[item.id]);
    if (!session.user) {
      // 游客：本地车直接改本地存储并即时重算合计（无需网络防抖）
      persistGuestFromItems();
      recomputeCartTotals();
      return;
    }
    cartSyncTimers[item.id] = setTimeout(async () => {
      const max = Number(item.stock || 0);
      const qty = Number(item.quantity) || 1;
      if (max > 0 && qty > max) {
        fail(`库存不足：仅剩 ${max} 件`, '库存不足');
        await loadCart(); // 回滚到后端真实数量
        return;
      }
      try {
        // PUT 返回的就是更新后的完整购物车（含 activityDiscount），必须接住，
        // 否则改数量后活动优惠/应付合计停留在旧值，与结算页对不上
        const updated = await api.put(`/cart/items/${item.id}`, { quantity: qty, selected: item.selected !== false });
        if (updated) {
          Object.assign(cart, updated);
          cartStore.setCart(updated);
        }
        await loadUsableCoupons();
      } catch (e) {
        // 后端可能因库存不足、或超出秒杀每人限购而拒绝，回滚到真实数量并提示，
        // 避免界面与后端不一致。后端的 409 文案已是中文可读的，直接用。
        const msg = e?.message || '更新数量失败，已恢复';
        fail(msg, /限购|买满/.test(msg) ? '超出限购' : '库存不足');
        await loadCart();
      }
    }, 400);
  }

  async function removeCartItem(id) {
    if (!session.user) { guestRemoveItem(id); return; }
    await run(() => api.delete(`/cart/items/${id}`).then(loadCart), '购物车商品已删除');
  }

  async function clearCart() {
    const confirmed = await askConfirm({
      title: '清空购物车',
      message: '确定要清空购物车里的全部商品吗？此操作不可恢复。',
      confirmText: '清空',
      danger: true,
    });
    if (!confirmed) return;
    if (!session.user) { guestClear(); return; }
    const ids = (cart.items || []).map((item) => item.id);
    await run(async () => {
      await Promise.all(ids.map((id) => api.delete(`/cart/items/${id}`)));
      await loadCart();
    }, '购物车已清空');
  }

  return {
    myCoupons, usableCoupons, selectedUserCouponId, userOptedOutCoupon, cartSyncTimers,
    addToCart, loadCart, loadMyCoupons, loadMoreMyCoupons, loadMyCouponsPage, loadUsableCoupons,
    couponEligible, couponShortfall, autoSelectCoupon, selectCoupon, chooseNoCoupon,
    stepQty, onQtyChange, onQtyInput, removeCartItem, clearCart,
  };
}