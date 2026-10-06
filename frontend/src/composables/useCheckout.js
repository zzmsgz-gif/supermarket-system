// 结算下单：地址簿 + 去结算 + 提交订单（两条路径：购物车 / 立即购买）。
//
// 关键口径（别改）：
// · 「下单」≠「付款」。订单一建成就锁库存（后端 deductStocks 在建单时执行），
//   所以这里只提交，剩下的交给收银台倒计时，让用户显式决定要不要付。
// · 履约三选一：自提要门店，即时配送与快递都要地址，且只有即时配送带自选时段。
// · 下单会占掉秒杀名额（未付款也占），所以建单后必须重拉 loadFlashSales()。
export function useCheckout({
  api, run, fail, session, isAdmin, router, navigate, route, notice,
  cart, paying, quickBuy, selectedUserCouponId, userOptedOutCoupon,
  addresses, selectedAddressId, addressForm, usePoints, pointsToUse, memberPreview,
  isPickup, activeStoreId, isExpress, fulfillment,
  QUICKBUY_ITEM_ID,
  loadWallet, loadMyCoupons, loadUsableCoupons, loadCart, loadOrders, loadFlashSales, loadProducts,
  refreshProductDetail,
  setPendingAction, goLogin,
}) {
  async function loadAddresses() {
    if (!session.user || isAdmin.value) return;
    addresses.value = await api.get('/addresses');
    const defaultAddress = addresses.value.find((item) => item.isDefault) || addresses.value[0];
    selectedAddressId.value = defaultAddress?.id || null;
  }

  function useAddress(address) {
    selectedAddressId.value = address.id;
    Object.assign(addressForm, address);
    notice.value = '已选择该地址';
    // 从结算页补地址过来的：选好即返回结算页继续下单（query 在 /addresses 上，回到 /checkout 即清除）
    if (route.query.redirect === 'checkout') router.push({ name: 'checkout' });
  }

  async function saveAddress() {
    await run(async () => {
      const saved = await api.post('/addresses', addressForm);
      selectedAddressId.value = saved.id;
      await loadAddresses();
    }, '地址已保存');
    // 从结算页补地址过来的：保存后自动返回结算页继续下单（watch(view='checkout') 会重拉钱包/优惠券/门店等）
    if (route.query.redirect === 'checkout') router.push({ name: 'checkout' });
  }

  async function goCheckout() {
    if (!cart.items?.length) { fail('购物车为空，请先添加商品'); return; }
    if (!session.user) { setPendingAction({ type: 'checkout', redirect: 'checkout' }); goLogin({ tab: 'login' }); return; }
    if (!addresses.value.length) await loadAddresses();
    if (!selectedAddressId.value && addresses.value.length) {
      selectedAddressId.value = (addresses.value.find((item) => item.isDefault) || addresses.value[0]).id;
    }
    if (!selectedAddressId.value) { router.push({ name: 'addresses', query: { redirect: 'checkout' } }); return; }
    await Promise.all([loadWallet(), loadMyCoupons(), loadUsableCoupons()]);
    navigate('checkout');
  }

  // 履约方式写成请求体：自提带门店，另两种带地址，只有即时配送带时段。
  function applyFulfillment(body) {
    if (isPickup.value) {
      body.fulfillmentType = 'PICKUP';
      body.pickupStoreId = activeStoreId.value;
    } else {
      // 即时配送与快递配送都要地址；只有即时配送带自选时段（快递时效由第三方决定）
      body.fulfillmentType = isExpress.value ? 'EXPRESS' : 'INSTANT';
      body.addressId = selectedAddressId.value;
      if (!isExpress.value && fulfillment.slot) body.deliverySlot = fulfillment.slot;
    }
  }

  // 券与积分：两条下单路径共用这一段
  function applyDiscounts(body) {
    if (selectedUserCouponId.value) body.userCouponId = selectedUserCouponId.value;
    if (usePoints.value && memberPreview.value.pointsUsed > 0) {
      body.usePoints = true;
      body.pointsToUse = memberPreview.value.pointsUsed;
    }
  }

  // 建单成功后清理一次性状态并重拉受影响的列表
  async function afterOrderCreated(order) {
    selectedUserCouponId.value = '';
    userOptedOutCoupon.value = false;
    usePoints.value = false;
    pointsToUse.value = 0;
    await loadCart();
    await loadOrders();
    // 下单会占掉秒杀名额（未付款也占），必须重拉，否则「还能买几件」停留在旧值
    await loadFlashSales();
    // 建单即扣库存（后端 deductStocks 在建单时执行）→ 商品目录里的库存/销量已经变了。
    // 不重拉的话返回列表页看到的还是下单前的旧值（用户反馈「购买后库存跟销量没正确显示」）。
    await loadProducts();
    // 当前正停在商品详情页时（立即购买虚拟项的来源页），把它的库存/销量一并刷新
    await refreshProductDetail();
    navigate('pay', { id: order.id });
    return order;
  }

  async function createOrder() {
    if (paying.value) return;
    // 履约三选一：自提要选门店；即时配送与快递配送都要收货地址
    if (isPickup.value) {
      if (!activeStoreId.value) { fail('请选择自提门店'); return; }
    } else if (!selectedAddressId.value) {
      // 没有收货地址（且非门店自提）：跳到收货地址页补地址，加完带 redirect 自动返回结算继续下单
      router.push({ name: 'addresses', query: { redirect: 'checkout' } });
      return;
    }
    // 「立即购买」独立通道：不依赖购物车行，走 /orders/quick-buy；成功后清掉虚拟项（购物车零残留）
    if (quickBuy.value) {
      const q = quickBuy.value;
      const body = { productId: q.productId, quantity: q.qty, skuSpec: q.spec || null, remark: '前端下单' };
      applyFulfillment(body);
      applyDiscounts(body);
      paying.value = true;
      try {
        await new Promise((r) => setTimeout(r, 700));
        // 这里只「提交订单」，不自动付款 —— 订单一建成就已经锁了库存（后端 deductStocks 在建单时执行），
        // 若此刻直接扣款，用户根本不知道自己刚才跨过了「下单」这一步。改由收银台显式倒计时后再决定。
        const order = await api.post('/orders/quick-buy', body);
        quickBuy.value = null;
        cart.items = (cart.items || []).filter((i) => i.id !== QUICKBUY_ITEM_ID);
        await afterOrderCreated(order);
        return order;
      } finally {
        paying.value = false;
      }
    }
    // 购物车结算路径：只下「已勾选」的项；被「立即购买」临时取消勾选的真实项不会进入本单（不污染购物车）。
    const itemIds = (cart.items || []).filter((i) => i.selected !== false).map((i) => i.id);
    if (!itemIds.length) { fail('请先在购物车勾选要购买的商品'); return; }
    paying.value = true;
    try {
      // 模拟支付网关受理：先展示加载态，使模拟支付更逼真
      await new Promise((r) => setTimeout(r, 700));
      await run(async () => {
        const body = { cartItemIds: itemIds, remark: '前端下单' };
        applyFulfillment(body);
        applyDiscounts(body);
        // 只提交、不付款：创建即锁定库存，剩下交给收银台倒计时，由用户决定是否真正扣款
        const order = await api.post('/orders', body);
        await afterOrderCreated(order);
        return order;
      }, '订单已提交，请在限定时间内完成支付');
    } finally {
      paying.value = false;
    }
  }

  return { loadAddresses, useAddress, saveAddress, goCheckout, createOrder };
}