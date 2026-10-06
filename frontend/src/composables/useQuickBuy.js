// 「立即购买」独立通道：不写购物车表，注入一条虚拟购物车项直达结算。
//
// 虚拟项的 id 固定为 QUICKBUY_ITEM_ID（'__quickbuy__'），只存在于前端 cart.items ——
// 不落库、放弃支付也不残留。结算与下单金额都基于 cart.items 计算，所以复用整套逻辑即可。
import { round2 } from '../utils/format.js';
import { QUICKBUY_ITEM_ID } from './useGuestCart.js';

export function useQuickBuy({
  api, run, fail, session, isAdmin, router, navigate, notice,
  cart, quickBuy, pendingQuickBuy,
  productDetail, detailQuantity, selectedSpecText, effectiveDetailPrice, selectedSkuPrice,
  getFlashSaleOfProduct, getFlashLimitOfProduct,
  reportDwell, guestAdd, takeAddSource, flyToCart, loadCart, loadProducts,
  setPendingAction, takePendingAction, clearPendingAction, goLogin,
}) {
  async function addDetailToCart() {
    if (isAdmin.value) { fail('管理员只能查看上架商品，不能加入购物车'); return; }
    if (!session.user) {
      const ok = await guestAdd(
        { id: productDetail.data?.id, name: productDetail.data?.name, stock: productDetail.data?.stock,
          price: productDetail.data?.price, originalPrice: productDetail.data?.originalPrice,
          coverUrl: productDetail.data?.coverUrl, unit: productDetail.data?.unit },
        detailQuantity.value || 1,
        selectedSpecText.value || ''
      );
      if (ok) { navigate('cart'); flyToCart(takeAddSource(), productDetail.data?.coverUrl); }
      return;
    }
    const stock = Number(productDetail.data?.stock || 0);
    const qty = Number(detailQuantity.value || 1);
    if (stock <= 0) { fail(`${productDetail.data?.name || '该商品'} 已售罄，暂时无法加入购物车`); return; }
    if (qty > stock) { fail(`库存不足：仅剩 ${stock} 件，您选择了 ${qty} 件`, '库存不足'); return; }
    // 秒杀品是纯折扣通道：名额用完后彻底不能加购（连原价都不行），只能去原商品按原价买。
    const sale = getFlashSaleOfProduct(productDetail.data?.id);
    const left = getFlashLimitOfProduct(productDetail.data?.id);
    if (sale && left !== null && qty > left) {
      fail(`「${sale.name || '该秒杀商品'}」每人限购 ${sale.perUserLimit || left} 件，已达上限，请去原商品按原价购买`, '超出限购');
      return;
    }
    // 全场名额抢完也要拦（不限购场次 left 为 null，只能看 remainingQuota）
    if (sale && sale.remainingQuota !== null && sale.remainingQuota !== undefined
        && qty > Number(sale.remainingQuota)) {
      fail(`「${sale.name || '该秒杀商品'}」本场名额已抢完（共 ${sale.totalQuota} 件），请关注下一场`, '超出限购');
      return;
    }
    const specNote = selectedSpecText.value ? `（${selectedSpecText.value}）` : '';
    await run(async () => {
      reportDwell();
      await api.post('/cart/items', { productId: productDetail.data.id, quantity: detailQuantity.value, skuSpec: selectedSpecText.value || null });
      // 购物车 + 商品目录一起重拉：加购不改变库存/销量，但详情页的「剩余库存」等
      // 依赖商品数据，漏刷会与其他入口的表现不一致。
      await Promise.all([loadCart(), loadProducts()]);
      const src = takeAddSource();
      navigate('cart');
      flyToCart(src, productDetail.data?.coverUrl);
    }, `已加入购物车 ${detailQuantity.value} 件${specNote}`);
  }

  // 用商品快照 + 数量 + 规格，构建一条与后端 CartItemResponse 同形状的「虚拟购物车项」。
  // 价格口径逐字对齐后端 CartItemResponse.from / OrderService.buildOrderItem：
  // 单价取「正常售价 / 会员价 / 秒杀价」三者最低；命中秒杀时按「秒杀段 + 原价段」自动拆分，
  // productPrice 取等价单价保证 productPrice×quantity == subtotalAmount，结算预览与实付不会脱节。
  // 注意：这条项只存在前端 cart.items，不落库、不写购物车表。
  // 规格价优先：regular 取「所选规格价」(effectiveDetailPrice)，会员价不与规格价叠加（与后端一致）；
  // 秒杀按折扣率套到规格价上（走规格价时 = 规格价 × (基准秒杀价 / 基准价)），否则用基准秒杀绝对值。
  function buildQuickBuyCartItem(product, qty, spec) {
    const id = Number(product.id);
    const basePrice = Number(effectiveDetailPrice.value ?? product.price ?? 0);
    const usingSkuPrice = selectedSkuPrice.value != null;
    const memberPrice = Number(product.memberPrice ?? basePrice);
    let regular = basePrice;
    // 会员价仅在未走规格价时参与比较（与后端 SkuPriceSupport / CartItemResponse 同口径）
    if (!usingSkuPrice && memberPrice > 0 && memberPrice < regular) regular = memberPrice;
    const sale = getFlashSaleOfProduct(id);
    const baseFlash = sale ? Number(sale.flashPrice) : 0;
    const rate = Number(product.price) > 0 ? baseFlash / Number(product.price) : 1;
    let flashApplies = false;
    let flashUnit = regular;
    if (sale) {
      // 走规格价 → 秒杀价 = 规格价 × 折扣率；未走规格价 → 用基准秒杀绝对值（与后端 flashPriceFor 一致）
      flashUnit = usingSkuPrice ? round2(regular * rate) : baseFlash;
      flashApplies = flashUnit < regular;
    }
    // 秒杀品是纯折扣通道：不再把超出名额的部分按原价成交，fq 即本行全部件数（已在前端按名额夹量）。
    const left = getFlashLimitOfProduct(id);
    const fq = flashApplies ? Math.min(qty, left ?? qty) : 0;
    const overflow = 0;
    // 精确小计：整行秒杀价（与后端严格限额口径一致）
    const subtotal = round2(flashUnit * fq + regular * overflow);
    const unit = round2(subtotal / qty);
    const finalSubtotal = round2(unit * qty);
    // 划线对照价：命中秒杀时用「打折前的价」（走了规格价就是规格价，否则商品基准价）
    const productOriginalPrice = flashApplies
      ? (usingSkuPrice ? basePrice : Number(product.price ?? basePrice))
      : Number(product.originalPrice ?? basePrice);
    return {
      id: QUICKBUY_ITEM_ID,
      productId: id,
      productName: product.name || '商品',
      productSku: product.sku || '',
      productCoverUrl: product.coverUrl || '',
      skuSpec: spec || '',
      productPrice: unit,
      productOriginalPrice,
      subtotalAmount: finalSubtotal,
      regularPrice: regular,
      flashQty: fq,
      flashPrice: flashApplies ? round2(flashUnit) : 0,
      flashSaleId: flashApplies ? sale.id : null,
      stock: Number(product.stock ?? 0),
      onSale: true,
      unit: product.unit || '',
      quantity: qty,
      selected: true,
    };
  }

  async function buyDetailNow() {
    if (isAdmin.value) { fail('管理员只能查看上架商品，不能下单'); return; }
    const stock = Number(productDetail.data?.stock || 0);
    const qty = Number(detailQuantity.value || 1);
    if (stock <= 0) { fail(`${productDetail.data?.name || '该商品'} 已售罄，暂时无法购买`); return; }
    if (qty > stock) { fail(`库存不足：仅剩 ${stock} 件，您选择了 ${qty} 件`, '库存不足'); return; }
    // 秒杀品是纯折扣通道：名额用完后彻底不能下单（连原价都不行），只能去原商品按原价买。
    const sale = getFlashSaleOfProduct(productDetail.data?.id);
    const left = getFlashLimitOfProduct(productDetail.data?.id);
    if (sale && left !== null && qty > left) {
      fail(`「${sale.name || '该秒杀商品'}」每人限购 ${sale.perUserLimit || left} 件，已达上限，请去原商品按原价购买`, '超出限购');
      return;
    }
    if (sale && sale.remainingQuota !== null && sale.remainingQuota !== undefined
        && qty > Number(sale.remainingQuota)) {
      fail(`「${sale.name || '该秒杀商品'}」本场名额已抢完（共 ${sale.totalQuota} 件），请关注下一场`, '超出限购');
      return;
    }
    if (!session.user) {
      // 游客：把「要买这件 + 商品快照」写入 sessionStorage，跳到登录页；登录成功后由 LoginPage 调 consumePendingQuickBuy 注入虚拟项去结算（不写本地购物车）
      setPendingAction({
        type: 'quickBuy',
        productId: productDetail.data?.id,
        spec: selectedSpecText.value || '',
        qty,
        product: productDetail.data,
        redirect: 'checkout',
      });
      goLogin({ tab: 'login' });
      notice.value = '登录后即可直接结算';
      return;
    }
    await enterQuickBuy();
  }

  // 「立即购买」：不写购物车。注入一条虚拟购物车项（仅选中它），直达结算页；
  // 下单走 /orders/quick-buy。游客态由 pendingQuickBuy 在登录成功后调用本流程。
  async function enterQuickBuy() {
    if (isAdmin.value) { fail('管理员只能查看上架商品，不能下单'); return; }
    const product = productDetail.data;
    if (!product) return;
    const qty = Number(detailQuantity.value || 1);
    const spec = selectedSpecText.value || '';
    const item = buildQuickBuyCartItem(product, qty, spec);
    // 移除上一次可能残留的虚拟项，并把真实购物车项全部取消勾选（只结算这一件）
    cart.items = (cart.items || []).filter((i) => i.id !== QUICKBUY_ITEM_ID);
    (cart.items || []).forEach((i) => { i.selected = false; });
    cart.items.push(item);
    // 活动优惠只按真实购物车算过；立即购买是单品通道，后端会重新评估，这里保守置 0 避免预览虚高
    cart.activityDiscount = 0;
    cart.activityName = null;
    quickBuy.value = { productId: product.id, spec, qty };
    navigate('checkout');
  }

  // 游客点「立即购买」→ 登录/注册成功后：用记录的快照构建虚拟项，直达结算（不写购物车）
  async function consumePendingQuickBuy(action) {
    // 游客点「立即购买」后登录：动作先前已写入 sessionStorage（setPendingAction），这里取出并消费。
    // 仍兼容旧的 in-memory pendingQuickBuy（保险）。注意 LoginPage.resumeAfterAuth 已经 takePendingAction()
    // 取过一次（会从 sessionStorage 删除），所以这里优先用调用方传入的 action，避免二次 take 拿到 null。
    const q = action || pendingQuickBuy.value || takePendingAction();
    pendingQuickBuy.value = null;
    clearPendingAction();
    if (!q || !session.user) return false;
    const product = q.product || { id: q.productId, name: '商品', price: 0, originalPrice: 0, coverUrl: '', unit: '', stock: 0, sku: '' };
    const item = buildQuickBuyCartItem(product, q.qty, q.spec);
    cart.items = (cart.items || []).filter((i) => i.id !== QUICKBUY_ITEM_ID);
    (cart.items || []).forEach((i) => { i.selected = false; });
    cart.items.push(item);
    cart.activityDiscount = 0;
    cart.activityName = null;
    quickBuy.value = { productId: q.productId, spec: q.spec, qty: q.qty };
    navigate('checkout');
    return true;
  }

  return {
    addDetailToCart, buildQuickBuyCartItem, buyDetailNow, enterQuickBuy, consumePendingQuickBuy,
  };
}