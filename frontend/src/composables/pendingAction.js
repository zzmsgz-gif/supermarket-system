// 登录拦截后「登录成功要接着做的事」——跨路由存活（sessionStorage）。
//
// 背景：登录/注册原本是 App.vue 里的 modal，拦截（去结算 / 立即购买 / 收藏）时只需把动作存在
// 内存 ref 里，登录成功后接着做即可。现在登录独立成 /login 路由页，一次跳转后那个内存 ref 还在
// （App.vue 不会销毁），但为了语义清晰、且避免 modal 残留态干扰，统一改用 sessionStorage 持久化。
//
// action 形状：
//   { type: 'checkout', redirect?: <routeName> }             // 游客点结算 → 登录后去结算
//   { type: 'quickBuy', productId, spec, qty, product, redirect?: <routeName> }  // 游客点立即购买
//   { type: 'favorite', product, redirect?: <routeName> }    // 游客点心形 → 登录后补收藏
// redirect 是登录成功后最终落点的路由名（收藏类用：回到原来那页；checkout/quickBuy 由消费方自己跳）。
const KEY = 'supermarket_pending_action';

export function setPendingAction(action) {
  try {
    sessionStorage.setItem(KEY, JSON.stringify(action));
  } catch (e) {
    /* 隐私模式禁用 storage 时忽略，最坏情况是登录后不自动续做 */
  }
}

// 取出并消费（读一次即删），避免重复触发。
export function takePendingAction() {
  try {
    const raw = sessionStorage.getItem(KEY);
    if (!raw) return null;
    sessionStorage.removeItem(KEY);
    return JSON.parse(raw);
  } catch (e) {
    return null;
  }
}

export function clearPendingAction() {
  try {
    sessionStorage.removeItem(KEY);
  } catch (e) {
    /* ignore */
  }
}
