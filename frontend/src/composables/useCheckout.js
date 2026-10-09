// 结算下单：地址簿 + 去结算 + 提交订单（两条路径：购物车 / 立即购买）。
//
// 关键口径（别改）：
// · 「下单」≠「付款」。订单一建成就锁库存（后端 deductStocks 在建单时执行），
//   所以这里只提交，剩下的交给收银台倒计时，让用户显式决定要不要付。
// · 履约三选一：自提要门店，即时配送与快递都要地址，且只有即时配送带自选时段。
// · 下单会占掉秒杀名额（未付款也占），所以建单后必须重拉 loadFlashSales()。
// · 「提交中…」的最短时长用 withMinSpinner，与支付转圈**同一常量**（用户要求两处节奏一致），
//   别再写裸的 setTimeout —— 那会让「提交订单」与「支付」的等待感不一致。
import { ref } from 'vue';
import { withMinSpinner } from '../utils/format.js';

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

  // ===== 地址编辑 / 删除 / 设默认（2026-10-09 补）=====
  // 之前只有 saveAddress() → POST，等于**只能新增、永远改不了**，
  // 而后端 PUT /addresses/{id}、DELETE、设默认接口一直都在，前端没接。
  // 同一个地址也能被反复添加（没有任何重复校验），列表一长全是重复项。

  /** 正在编辑的地址 id；null = 新增模式 */
  const editingAddressId = ref(null);
  /** 分页：地址多于一屏就该翻页，而不是拉一长条 */
  const addrPage = ref(1);
  const ADDR_PAGE_SIZE = 5;

  /** 地址指纹：收货人+电话+省市区+详细地址全同即视为同一地址 */
  function addressFingerprint(a) {
    return [a.receiverName, a.receiverPhone, a.province, a.city, a.district, a.detailAddress]
      .map((v) => String(v || '').trim().replace(/\s+/g, ''))
      .join('|');
  }

  /**
   * 查重：返回**已存在**的地址对象（编辑时排除自己）。
   *
   * <p>为什么不放后端：同一地址只对**同一用户**判重，
   * 前端手上就有全量列表，省一次请求；后端也仍应做校验（多端并发可能绕过）。
   */
  function findDuplicate(candidate) {
    const fp = addressFingerprint(candidate);
    return addresses.value.find(
      (a) => a.id !== editingAddressId.value && addressFingerprint(a) === fp,
    );
  }

  /** 进入编辑模式：把地址填进表单。编辑态与新增态共用同一个表单，但走不同接口。 */
  function startEditAddress(address) {
    editingAddressId.value = address.id;
    Object.assign(addressForm, {
      receiverName: address.receiverName || '',
      receiverPhone: address.receiverPhone || '',
      province: address.province || '',
      city: address.city || '',
      district: address.district || '',
      detailAddress: address.detailAddress || '',
      isDefault: !!address.isDefault,
    });
    notice.value = '正在编辑该地址，保存后生效';
  }

  function cancelEditAddress() {
    editingAddressId.value = null;
    Object.assign(addressForm, {
      receiverName: '', receiverPhone: '', province: '', city: '',
      district: '', detailAddress: '', isDefault: addresses.value.length === 0,
    });
  }

  async function deleteAddress(address) {
    await run(async () => {
      await api.delete(`/addresses/${address.id}`);
      if (selectedAddressId.value === address.id) selectedAddressId.value = null;
      if (editingAddressId.value === address.id) cancelEditAddress();
      await loadAddresses();
      // 删掉当前页最后一条后要回退一页，否则停在空白页
      const maxPage = Math.max(1, Math.ceil((addresses.value.length || 0) / ADDR_PAGE_SIZE));
      if (addrPage.value > maxPage) addrPage.value = maxPage;
    }, '地址已删除');
  }

  async function setDefaultAddress(address) {
    if (address.isDefault) return;
    await run(async () => {
      await api.patch(`/addresses/${address.id}/default`, {});
      await loadAddresses();
    }, '已设为默认地址');
  }

  function changeAddrPage(delta) {
    const maxPage = Math.max(1, Math.ceil((addresses.value.length || 0) / ADDR_PAGE_SIZE));
    const next = addrPage.value + delta;
    if (next < 1 || next > maxPage) return;
    addrPage.value = next;
  }

  function useAddress(address) {
    selectedAddressId.value = address.id;
    Object.assign(addressForm, address);
    notice.value = '已选择该地址';
    // 从结算页补地址过来的：选好即返回结算页继续下单（query 在 /addresses 上，回到 /checkout 即清除）
    if (route.query.redirect === 'checkout') router.push({ name: 'checkout' });
  }

  async function saveAddress() {
    // 重复地址拦截：不拦的话用户会不小心存出一堆一模一样的条目
    const dup = findDuplicate(addressForm);
    if (dup) {
      fail('这个地址已经存在了（编辑列表里那条就是），无需重复添加');
      return;
    }
    const editing = editingAddressId.value;
    await run(async () => {
      const payload = { ...addressForm };
      const saved = editing
        ? await api.put(`/addresses/${editing}`, payload)
        : await api.post('/addresses', payload);
      selectedAddressId.value = saved.id;
      editingAddressId.value = null;
      await loadAddresses();
    }, editing ? '地址已更新' : '地址已保存');
    // 成功后复位表单；失败时**保留用户输入**让他改，别把刚填的清掉。
    // 注意 run() 失败会 rethrow，所以只有走到这里才是成功。
    cancelEditAddress();
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
        // 这里只「提交订单」，不自动付款 —— 订单一建成就已经锁了库存（后端 deductStocks 在建单时执行），
        // 若此刻直接扣款，用户根本不知道自己刚才跨过了「下单」这一步。改由收银台显式倒计时后再决定。
        const order = await withMinSpinner(() => api.post('/orders/quick-buy', body));
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
      await run(async () => {
        const body = { cartItemIds: itemIds, remark: '前端下单' };
        applyFulfillment(body);
        applyDiscounts(body);
        // 只提交、不付款：创建即锁定库存，剩下交给收银台倒计时，由用户决定是否真正扣款。
        // withMinSpinner 让「提交中…」至少显示够长（与支付同一时长），否则本地几十毫秒就返回、像没反应。
        await withMinSpinner(async () => {
          const order = await api.post('/orders', body);
          await afterOrderCreated(order);
        });
      }, '订单已提交，请在限定时间内完成支付');
    } finally {
      paying.value = false;
    }
  }

  return { loadAddresses, useAddress, saveAddress, goCheckout, createOrder,
           editingAddressId, addrPage, ADDR_PAGE_SIZE,
           startEditAddress, cancelEditAddress, deleteAddress, setDefaultAddress, changeAddrPage };
}