// 购物车的「纯金额计算」层：只读cart / products / usableCoupons，不发请求、不改状态。
// 刻意与 useCart（增删改）分开成独立 composable —— 它必须装在 useCart 之前
//（useMemberPoints 要读 orderPayPreview / cartMemberDiscount），而 useCart 又要用这里的
// usableCoupons 来选券。拆成两个单向依赖的 composable，比互相注入函数更清楚。
import { computed } from 'vue';
import { itemOriginalSave, round2 } from '../utils/format.js';

export function useCartTotals({ cart, usableCoupons, selectedUserCouponId, getProducts }) {
  const selectedCoupon = computed(() => usableCoupons.value.find((coupon) => coupon.id === selectedUserCouponId.value) || null);

  // 购物车实时合计：只累加「已勾选」商品（与后端 selectedAmount 口径一致）
  const cartLocalTotal = computed(() => (cart.items || [])
    .filter((item) => item.selected !== false)
    .reduce((sum, item) => sum + Number(item.productPrice || 0) * Number(item.quantity || 0), 0));

  // 划线价（原价）相对现价的优惠合计：仅统计已勾选商品，纯展示用、不计入应付
  const cartOriginalSave = computed(() => (cart.items || [])
    .filter((item) => item.selected !== false)
    .reduce((sum, item) => sum + itemOriginalSave(item), 0));

  const orderPayPreview = computed(() => {
    // 以「非会员价小计」(cartListTotal) 为基准：会员折扣尚未扣，留给 memberPreview 单独摊成一行；
    // 若用 cartLocalTotal（已是会员净额）会重复扣会员折扣，导致 应付 偏低。
    const total = cartListTotal.value;
    let pay = total;
    if (selectedCoupon.value) pay = Math.max(pay - Number(selectedCoupon.value.discountAmount || 0), 0);
    if (Number(cart.activityDiscount) > 0) pay = Math.max(pay - Number(cart.activityDiscount || 0), 0);
    return pay;
  });

  // 已勾选件数（结算明细展示用）
  const cartSelectedQty = computed(() => (cart.items || [])
    .filter((item) => item.selected !== false)
    .reduce((sum, item) => sum + Number(item.quantity || 0), 0));

  // 实际抵扣口径的共省金额：活动优惠 + 优惠券（划线价已体现在现价里，单列展示不计入）
  const cartTotalSaved = computed(() =>
    Number(cart.activityDiscount || 0)
    + (selectedCoupon.value ? Number(selectedCoupon.value.discountAmount || 0) : 0));

  // 商品目录基价（非会员售价）映射：购物项只回了会员净额 productPrice，没有「非会员价」，
  // 要把会员折扣单独摊开，就得反查目录里的 product.price（= 后端 unitPriceFor 里的 base）。
  // 列表分页可能不含该商品 → 缺失时回退 0（不显示会员折扣行，宁可不标也不算错）。
  const productBasePriceMap = computed(() => {
    const m = {};
    for (const p of (getProducts() || [])) m[Number(p.id)] = Number(p.price || 0);
    return m;
  });
  function catalogBasePrice(id) { return productBasePriceMap.value[Number(id)] || 0; }

  // 当前登录会员在各购物项上「相对非会员价」省了多少（仅非秒杀项）。
  // 直接取后端 CartItemResponse.memberDiscount（后端已按 unitPriceFor 同口径算好、跨页面稳定可用），
  // 不再依赖前端商品目录是否加载。非会员/游客该项为 0。结算页据此把「会员折扣」单独摊开。
  const cartMemberDiscount = computed(() => (cart.items || [])
    .filter((i) => i.selected !== false && !i.flashSaleId && !(Number(i.flashPrice || 0) > 0))
    .reduce((sum, i) => sum + Number(i.memberDiscount || 0), 0));

  // 商品小计（原价/非会员价）：会员净额回加会员折扣，得到未打折前的小计，用于结算页逐行展示。
  const cartListTotal = computed(() => round2(cartLocalTotal.value + cartMemberDiscount.value));

  return {
    selectedCoupon, cartLocalTotal, cartOriginalSave, orderPayPreview,
    cartSelectedQty, cartTotalSaved, productBasePriceMap, catalogBasePrice,
    cartMemberDiscount, cartListTotal,
  };
}