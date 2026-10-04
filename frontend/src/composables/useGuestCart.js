/**
 * 游客购物车：未登录先在 localStorage 暂存，登录后并入服务端购物车
 *
 * 从 App.vue 抽出（原 429-575 行）。
 *
 * 设计要点（别改）：
 * - 游客态没有后端购物车接口，所以本地存一份 `{productId, quantity, skuSpec}` 行数组，
 *   再用商品快照重建**与后端一致形状**的 cart（字段名对齐 CartItemResponse），页面无需分叉。
 * - `estimateGuestActivity` 与后端 `evaluateBestActivity` **同口径**（门槛校验 + 取减免最大者），
 *   只用于游客购物车展示 —— 口径漂移会让游客看到的优惠和登录后不一样。
 * - `QUICKBUY_ITEM_ID` 是「立即购买」的虚拟项 id：只存在于前端 cart.items，不落库、不进购物车表。
 *   结算页与下单金额都基于 cart.items 计算，注入这条虚拟项即可复用整套结算逻辑，
 *   而真实购物车表从头到尾不会被写入（放弃支付也不残留）。
 * - 登录成功后 `mergeGuestCartToServer` 把本地行逐条 POST 进服务端购物车，并清掉本地缓存。
 *
 * 依赖注入：api 直接 import；cart（要写它）、activeActivities、getLoadCart、fail、notice、
 *   getSession 由 App.vue 传入。
 */
import { ref } from 'vue';
import { api } from '../api/client';

const GUEST_CART_KEY = 'supermarket_guest_cart';
// 立即购买的虚拟购物车项 id：只存在于前端 cart.items，不落库、不进购物车表。
export const QUICKBUY_ITEM_ID = '__quickbuy__';

export function useGuestCart({ cart, getSession, activeActivities, getLoadCart, fail, notice }) {
  const guestCartRows = ref([]); // [{ productId, quantity, skuSpec }]
  const guestProductCache = new Map(); // productId -> 商品快照（渲染本地车用）
  const pendingCheckout = ref(false); // 游客点结算 → 登录完成后继续去结算
  const pendingQuickBuy = ref(null); // 游客点「立即购买」→ 登录完成后只结算这件
  const quickBuy = ref(null); // 进行中的「立即购买」会话：{ productId, spec, qty }；非空即走 /orders/quick-buy

  function readGuestCart() {
    try { const raw = localStorage.getItem(GUEST_CART_KEY); return raw ? JSON.parse(raw) : []; } catch { return []; }
  }
  function writeGuestCart() {
    localStorage.setItem(GUEST_CART_KEY, JSON.stringify(guestCartRows.value));
  }
  function persistGuestFromItems() {
    guestCartRows.value = (cart.items || []).map((i) => ({ productId: i.productId, quantity: Number(i.quantity) || 1, skuSpec: i.skuSpec || '' }));
    writeGuestCart();
  }

  // 与后端 evaluateBestActivity 同口径的本地预估：仅用于游客购物车展示
  function estimateGuestActivity(selected) {
    if (!selected?.length || !activeActivities.value.length) return { activityDiscount: 0, activityName: null };
    const byProduct = new Map();
    for (const it of selected) {
      const pid = Number(it.productId);
      byProduct.set(pid, (byProduct.get(pid) || 0) + Number(it.productPrice || 0) * Number(it.quantity || 0));
    }
    let best = 0, bestName = null;
    for (const a of activeActivities.value) {
      const threshold = Number(a.threshold || 0);
      const discount = Number(a.discount || 0);
      if (!(threshold > 0) || !(discount > 0)) continue;
      const scope = String(a.scope || 'ALL');
      let qualifying = 0;
      for (const it of selected) {
        const pid = Number(it.productId);
        if (scope === 'ALL') qualifying += Number(it.productPrice || 0) * Number(it.quantity || 0);
        else if (scope === 'PRODUCT' && Number(a.productId) === pid) qualifying += Number(it.productPrice || 0) * Number(it.quantity || 0);
        else if (scope === 'CATEGORY' && a.categoryId != null && Number(a.categoryId) === Number(it.categoryId)) qualifying += Number(it.productPrice || 0) * Number(it.quantity || 0);
      }
      if (qualifying < threshold) continue;
      const off = (a.type === 'DISCOUNT' && discount > 0 && discount < 1)
        ? +(qualifying * (1 - discount)).toFixed(2)
        : discount;
      if (off > best) { best = off; bestName = a.name || null; }
    }
    return { activityDiscount: best, activityName: bestName };
  }

  function recomputeCartTotals() {
    const selected = (cart.items || []).filter((i) => i.selected !== false);
    cart.selectedCount = selected.reduce((s, i) => s + Number(i.quantity || 0), 0);
    cart.selectedAmount = +selected
      .reduce((s, i) => s + Number(i.productPrice || 0) * Number(i.quantity || 0), 0)
      .toFixed(2);
    // 游客态没有后端购物车接口，这里按后端同口径本地预估活动优惠（门槛校验 + 取减免最大者）
    const act = estimateGuestActivity(selected);
    cart.activityDiscount = act.activityDiscount;
    cart.activityName = act.activityName;
  }

  // 用本地行 + 商品快照重建与后端一致形状的 cart（字段名对齐 CartItemResponse），页面无需分叉
  async function refreshGuestCartView() {
    const rows = readGuestCart();
    if (!rows.length) { cart.items = []; cart.selectedCount = 0; cart.selectedAmount = 0; return; }
    const items = [];
    for (const row of rows) {
      let p = guestProductCache.get(row.productId);
      if (!p || (p.price == null && p.productPrice == null)) {
        try { p = await api.get(`/products/${row.productId}`); guestProductCache.set(row.productId, p); } catch { /* 商品可能已下架 */ }
      }
      if (!p) continue;
      const price = Number(p.price ?? p.productPrice ?? 0);
      const orig = Number(p.originalPrice ?? p.productOriginalPrice ?? price);
      const stock = Number(p.stock ?? 0);
      const qty = Math.max(1, Math.min(Number(row.quantity) || 1, Math.max(stock, 1)));
      items.push({
        id: 'g' + row.productId + '|' + (row.skuSpec || ''),
        productId: p.id ?? row.productId,
        categoryId: p.categoryId ?? null,
        productName: p.name || '商品',
        productCoverUrl: p.coverUrl || '',
        skuSpec: row.skuSpec || '',
        productPrice: price,
        productOriginalPrice: orig,
        stock,
        unit: p.unit || '',
        quantity: qty,
        selected: true,
        subtotalAmount: +(price * qty).toFixed(2),
      });
      if (qty !== (Number(row.quantity) || 1)) row.quantity = qty;
    }
    if (JSON.stringify(rows) !== JSON.stringify(guestCartRows.value)) { guestCartRows.value = rows; writeGuestCart(); }
    cart.items = items;
    recomputeCartTotals();
  }

  async function guestAdd(product, quantity = 1, skuSpec = '') {
    const q = Math.max(1, Number(quantity) || 1);
    const stock = Number(product.stock ?? product.stockQuantity ?? 0);
    const rows = readGuestCart();
    const idx = rows.findIndex((r) => r.productId === product.id && (r.skuSpec || '') === (skuSpec || ''));
    const cur = idx >= 0 ? Number(rows[idx].quantity) || 0 : 0;
    if (stock > 0 && cur + q > stock) { fail(`库存不足：仅剩 ${stock} 件，购物车中已有 ${cur} 件`, '库存不足'); return false; }
    if (idx >= 0) rows[idx].quantity = cur + q; else rows.push({ productId: product.id, quantity: q, skuSpec: skuSpec || '' });
    guestCartRows.value = rows;
    writeGuestCart();
    if (product?.id) guestProductCache.set(product.id, product);
    await refreshGuestCartView();
    return true;
  }

  function guestRemoveItem(id) {
    cart.items = (cart.items || []).filter((i) => i.id !== id);
    persistGuestFromItems();
    recomputeCartTotals();
  }

  function guestClear() {
    cart.items = [];
    cart.selectedCount = 0;
    cart.selectedAmount = 0;
    guestCartRows.value = [];
    localStorage.removeItem(GUEST_CART_KEY);
  }

  async function mergeGuestCartToServer() {
    const rows = readGuestCart();
    const s = typeof getSession === 'function' ? getSession() : null;
    if (!rows.length || !s?.user) return;
    let added = 0;
    let merged = 0;
    for (const row of rows) {
      try {
        await api.post('/cart/items', { productId: row.productId, quantity: Math.max(1, Number(row.quantity) || 1), skuSpec: row.skuSpec || undefined });
        added += Math.max(1, Number(row.quantity) || 1);
        merged += 1;
      } catch (e) {
        if (e?.authExpired) throw e;
        fail(e?.message || '部分本地商品未能并入购物车');
      }
    }
    guestCartRows.value = [];
    localStorage.removeItem(GUEST_CART_KEY);
    guestProductCache.clear();
    if (added > 0) {
      notice.value = `已将本地购物车 ${merged} 种 / ${added} 件商品并入你的账户`;
      await getLoadCart()();
    }
  }

  return {
    guestCartRows, guestProductCache, pendingCheckout, pendingQuickBuy, quickBuy,
    readGuestCart, writeGuestCart, persistGuestFromItems, estimateGuestActivity, recomputeCartTotals,
    refreshGuestCartView, guestAdd, guestRemoveItem, guestClear, mergeGuestCartToServer,
  };
}
