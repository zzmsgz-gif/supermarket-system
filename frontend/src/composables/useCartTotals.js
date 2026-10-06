// 购物车的「纯金额计算」层：只读 cart / usableCoupons，不发请求、不改状态。
// 刻意与 useCart（增删改）分开成独立 composable —— 它必须装在 useCart 之前
//（useMemberPoints 要读 orderPayPreview），而 useCart 又要用这里的 usableCoupons 来选券。
// 拆成两个单向依赖的 composable，比互相注入函数更清楚。
//
// ⚠️【唯一金额基准 —— 改这块前必读】
// 每一行 item.productPrice 已经是「该用户本行的实际成交单价」：后端 CartItemResponse 用
// MemberService.unitPriceFor（售价 / 商品会员价 / 等级折扣价 三者取最低）算好后返回，
// 前端明细行显示的就是它。于是：
//   商品金额 = Σ(成交单价 × 数量) = cartLocalTotal
// 会员带来的便宜已含在这个单价里，于是**两件事都不能做**（2026-10-06 用户连续两次指出）：
//   ① 不列「会员折扣 -X」→ 明细加起来 97.8、结算区写 104.8 再减 7，用户以为系统算错；
//   ② 不列「会员价已省 X」→ 那个 X 拿「售价」算，而售价在界面上根本不显示，用户拿明细行的
//      吊牌价（划线）一核对就对不上，只会觉得系统在乱报优惠。原话：「哪有这回事？省5块省在哪？」
// 会员的便宜由明细行的「会员价」标签 + 划线价直降说明表达，结算区不重复提。
//
// 【为什么不给「已省」换个基准再来一次】用户能核到的基准只有明细行里出现的那个数（吊牌价/划线价），
// 换成它就与每行的「省 ¥X」重复；换成售价则界面上没有售价，照样核不了。所以结论是：不提。
import { computed } from 'vue';
import { itemOriginalSave, round2 } from '../utils/format.js';

export function useCartTotals({ cart, usableCoupons, selectedUserCouponId, getProducts }) {
  const selectedCoupon = computed(() => usableCoupons.value.find((coupon) => coupon.id === selectedUserCouponId.value) || null);

  // 商品金额：只累加「已勾选」商品（与后端 selectedAmount 口径一致）。
  const cartLocalTotal = computed(() => round2((cart.items || [])
    .filter((item) => item.selected !== false)
    .reduce((sum, item) => sum + Number(item.productPrice || 0) * Number(item.quantity || 0), 0)));

  // 划线价（吊牌价）相对现价的优惠合计：仅统计已勾选商品，纯展示用、不计入应付
  const cartOriginalSave = computed(() => (cart.items || [])
    .filter((item) => item.selected !== false)
    .reduce((sum, item) => sum + itemOriginalSave(item), 0));

  // 应付总额（不含运费）：商品金额 − 优惠券 − 活动优惠。
  // 三项之外不再有任何加减 —— 会员价已含在单价里，运费在 useMemberPoints 里最后单独加上。
  const orderPayPreview = computed(() => {
    let pay = cartLocalTotal.value;
    if (selectedCoupon.value) pay = Math.max(pay - Number(selectedCoupon.value.discountAmount || 0), 0);
    if (Number(cart.activityDiscount) > 0) pay = Math.max(pay - Number(cart.activityDiscount || 0), 0);
    return round2(pay);
  });

  // 已勾选件数（结算明细展示用）
  const cartSelectedQty = computed(() => (cart.items || [])
    .filter((item) => item.selected !== false)
    .reduce((sum, item) => sum + Number(item.quantity || 0), 0));

  // 实际抵扣掉的金额合计：活动优惠 + 优惠券（会员让利与划线价都已体现在现价里，只作说明不计入）
  const cartTotalSaved = computed(() =>
    Number(cart.activityDiscount || 0)
    + (selectedCoupon.value ? Number(selectedCoupon.value.discountAmount || 0) : 0));

  // 商品目录基价（非会员售价）映射：管理端/卡片需要「不认会员时的价」时反查目录。
  // 列表分页可能不含该商品 → 缺失时回退 0。
  const productBasePriceMap = computed(() => {
    const m = {};
    for (const p of (getProducts() || [])) m[Number(p.id)] = Number(p.price || 0);
    return m;
  });
  function catalogBasePrice(id) { return productBasePriceMap.value[Number(id)] || 0; }

  return {
    selectedCoupon, cartLocalTotal, cartOriginalSave, orderPayPreview,
    cartSelectedQty, cartTotalSaved, productBasePriceMap, catalogBasePrice,
  };
}