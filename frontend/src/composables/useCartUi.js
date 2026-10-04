/**
 * 加购微交互：商品图飞入购物车胶囊 + 角标弹跳 + 切视图淡入
 *
 * 从 App.vue 抽出（原 1197-1308 行）。**纯视觉层，不含任何业务逻辑**。
 *
 * 三个刻意的设计（别改）：
 * · 幽灵元素挂 <body>（position:fixed）—— 完全脱离 Vue 与任何「包含块」，
 *   所以即使期间发生了视图切换（例如详情页加购后跳购物车）也不会把它带走。
 * · 只做视觉且 pointer-events:none —— 绝不能挡住页头或卡片的点击。
 * · 全部尊重 prefers-reduced-motion：关了动效就只剩静默的角标变化，不会退化成"点了没反应"。
 *
 * 另一个细节：角标是 v-if="cartBadgeCount"，0 → 1 时要等 nextTick 它才在 DOM 里，
 * 所以必须**更新后再弹**（popCartBadge 里 await nextTick）。
 * 角标数量变化本身也值得弹一下，但**飞入进行中不弹** —— 那次落地时自己会弹，免得同一秒弹两下。
 *
 * 依赖注入：cartBadgeCount（由外部 computed 提供）+ onViewChange（切视图时的淡入触发）由 App.vue 传入。
 */
import { ref, watch, nextTick } from 'vue';

export function useCartUi({ cartBadgeCount, getView }) {
  const contentEl = ref(null);      // 视图容器（切视图时淡入）
  const cartPillEl = ref(null);     // 页头购物车胶囊（飞入落点）
  const cartBadgeEl = ref(null);    // 胶囊上的角标
  let flyingGhosts = 0;

  function motionAllowed() {
    if (typeof window === 'undefined' || !window.matchMedia) return false;
    return !window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }

  function bob(el) {
    if (!el || typeof el.animate !== 'function' || !motionAllowed()) return;
    el.animate(
      [{ transform: 'scale(1)' }, { transform: 'scale(1.38)' }, { transform: 'scale(0.96)' }, { transform: 'scale(1)' }],
      { duration: 430, easing: 'cubic-bezier(.3,1.4,.5,1)' },
    );
  }

  // 角标是 v-if="cartBadgeCount"：0 → 1 时要等 nextTick 它才在 DOM 里，所以必须更新后再弹
  async function popCartBadge() {
    await nextTick();
    bob(cartPillEl.value);
    bob(cartBadgeEl.value);
  }

  // 从 sourceEl（一般是商品卡里的商品图）飞向页头购物车胶囊。
  // 拿不到源/目标、或用户关了动效 → 静默跳过（角标仍会弹），绝不阻断加购主流程。
  function flyToCart(sourceEl, imageUrl) {
    if (!motionAllowed() || !sourceEl || !document.body) return;
    const target = cartPillEl.value;
    if (!target || typeof target.getBoundingClientRect !== 'function') return;
    const s = sourceEl.getBoundingClientRect();
    const t = target.getBoundingClientRect();
    if (!s.width || !s.height || !t.width) return;

    const ghost = document.createElement('img');
    ghost.className = 'fly-ghost';
    ghost.setAttribute('aria-hidden', 'true');
    ghost.alt = '';
    const src = imageUrl || (sourceEl.tagName === 'IMG' ? sourceEl.currentSrc || sourceEl.src : '');
    if (src) ghost.src = src;
    ghost.style.left = `${s.left}px`;
    ghost.style.top = `${s.top}px`;
    ghost.style.width = `${s.width}px`;
    ghost.style.height = `${s.height}px`;
    document.body.appendChild(ghost);

    const dx = (t.left + t.width / 2) - (s.left + s.width / 2);
    const dy = (t.top + t.height / 2) - (s.top + s.height / 2);
    flyingGhosts += 1;
    let cleaned = false;
    const done = () => {
      if (cleaned) return;              // onfinish / oncancel 可能都触发 → 防重复回收
      cleaned = true;
      ghost.remove();
      flyingGhosts = Math.max(0, flyingGhosts - 1);
      popCartBadge();
    };
    if (typeof ghost.animate !== 'function') { done(); return; }
    const anim = ghost.animate(
      [
        { transform: 'translate(0px, 0px) scale(1)', opacity: 1, offset: 0 },
        { transform: `translate(${dx * 0.5}px, ${dy * 0.5 - 46}px) scale(0.5)`, opacity: 0.92, offset: 0.58 },
        { transform: `translate(${dx}px, ${dy}px) scale(0.12)`, opacity: 0.15, offset: 1 },
      ],
      { duration: 540, easing: 'cubic-bezier(.42,.02,.4,1)' },
    );
    anim.onfinish = done;
    anim.oncancel = done;
  }

  // 「这次点击来自哪个加购按钮」—— 用捕获阶段的委托记录，不必改 ProductCard / 详情页的 emit 签名；
  // 以后新增加购入口只要挂上 .add-fab 或 .js-add-cart 就自动带飞入。
  let lastAddSource = null;
  function onDocClickCapture(event) {
    const target = event.target;
    if (!(target instanceof Element)) return;
    const btn = target.closest('.add-fab, .js-add-cart');
    if (!btn) return;
    const card = btn.closest('.product-card');
    lastAddSource = (card && card.querySelector('.product-image img')) || card || btn;
  }
  function takeAddSource() {
    const el = lastAddSource;
    lastAddSource = null;
    return el;
  }

  // 角标数量变化本身就值得弹一下（购物车页加减件也走这里）；
  // 但飞入进行中不弹 —— 那次飞入落地时自己会弹，免得同一秒弹两下。
  watch(cartBadgeCount, (now, prev) => {
    if (now !== prev && flyingGhosts === 0) popCartBadge();
  });

  // 切视图淡入。刻意不重建组件、不动数据流：只播一段一次性动画，
  // 所以不会重新请求、不会重置滚动位置（用 :key 重建就没有这个保障）。
  watch(() => (typeof getView === 'function' ? getView() : null), async () => {
    if (!motionAllowed()) return;
    const el = contentEl.value;
    if (!el || typeof el.animate !== 'function') return;
    await nextTick();
    el.animate(
      [{ opacity: 0, transform: 'translateY(8px)' }, { opacity: 1, transform: 'none' }],
      { duration: 260, easing: 'cubic-bezier(.22,.61,.36,1)' },
    );
  });

  return {
    contentEl, cartPillEl, cartBadgeEl,
    motionAllowed, bob, popCartBadge, flyToCart,
    onDocClickCapture, takeAddSource,
  };
}
