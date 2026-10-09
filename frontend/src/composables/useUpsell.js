/**
 * 凑单推荐（upsell）—— 给「还差多少钱满减」一个可执行的出口。
 *
 * <p>**购物车和结算页共用这一份实现**（2026-10-09 抽出来的）：
 * 之前这套逻辑写在 CartPage.vue 里，结算页想显示同样的「买这些能凑上」
 * 就只能抄一遍 —— 而抄来的东西一定会漂移（改一处忘另一处）。
 *
 * <p>推荐规则：门槛内现货（按销量排）→ 不够就退回最便宜的几个。
 * 排除已在购物车里的商品，避免推荐「再买一个一样的」。
 *
 * <p>推荐只是「便捷加购」，**拉不到不该阻断主流程**：catch 里直接置空。
 */
import { ref, watch } from 'vue';

/**
 * @param appCtx 需要 api、cart、cartActivityProgress
 * @param onAdd  (product) => void|Promise —— 实际加入购物车的动作，由调用方提供
 *               （购物车和结算页加购后的后续动作不同：前者要刷新列表，后者可能重算优惠）
 */
export function useUpsell(appCtx, onAdd) {
  const upsell = ref([]);

  const pickUpsell = (list, inCart) => (list || [])
    .filter((p) => Number(p.stock) > 0 && !inCart.has(p.id))
    .slice(0, 3);

  async function loadUpsell() {
    const prog = appCtx.cartActivityProgress?.value;
    const items = appCtx.cart?.items || [];
    if (!prog || prog.reachedTop || !items.length || Number(prog.gap || 0) <= 0) {
      upsell.value = [];
      return;
    }
    const inCart = new Set(items.map((i) => i.productId));
    try {
      const fit = await appCtx.api.get(
        `/products?page=1&size=8&sort=sales_desc&maxPrice=${Number(prog.gap)}`,
      );
      upsell.value = pickUpsell(fit.items, inCart);
      if (!upsell.value.length) {
        const cheap = await appCtx.api.get('/products?page=1&size=6&sort=price_asc');
        upsell.value = pickUpsell(cheap.items, inCart);
      }
    } catch {
      upsell.value = [];
    }
  }

  function addUpsellToCart(product) {
    onAdd?.(product);
  }

  watch(
    () => [appCtx.cartActivityProgress?.value?.gap, (appCtx.cart?.items || []).length],
    () => { loadUpsell(); },
  );

  return { upsell, loadUpsell, addUpsellToCart };
}
