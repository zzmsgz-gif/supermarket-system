// 纯展示/格式化工具函数，从 App.vue 抽取，避免巨型单文件重复定义。
// 不依赖任何组件作用域状态，可在任意组件/ composable 中复用。

// 商品图加载失败兜底：生成「浅绿底 + 名称首字」的 SVG 占位，替代破图。
// 用 dataset 防止兜底图自身再触发 error 造成死循环。
export function imgFallback(event, name) {
  const el = event?.target;
  if (!el || el.tagName !== 'IMG' || el.dataset.fallbackApplied) return;
  el.dataset.fallbackApplied = '1';
  const ch = String(name || '超').trim().charAt(0) || '超';
  const svg = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 120">'
    + '<rect width="120" height="120" rx="14" fill="#e9f6f0"/>'
    + '<text x="60" y="76" font-size="46" font-weight="700" fill="#0aa870" '
    + 'text-anchor="middle" font-family="system-ui,-apple-system,sans-serif">' + ch + '</text></svg>';
  el.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
}

export function money(value) {
  const amount = Number(value || 0);
  return `¥${amount.toFixed(2)}`;
}

// 大数压缩：10000 以上显示「x.x万」，避免长数字撑爆布局
export function compactNum(value) {
  const n = Number(value || 0);
  if (n >= 10000) return `${(n / 10000).toFixed(1).replace(/\.0$/, '')}万`;
  return String(n);
}

export function initials(name) {
  return String(name || '商').slice(0, 2).toUpperCase();
}

export function formatRole(role) {
  // USER 不能再译成「普通用户」：那现在是会员等级 0 的名字，撞车会让银卡用户误以为自己是普通用户
  const roleMap = { ADMIN: '管理员', USER: '注册用户' };
  return roleMap[role] || role || '-';
}

export function formatOrderStatus(status) {
  const statusMap = {
    PENDING_PAYMENT: '待支付',
    PAID: '已支付',
    SHIPPED: '已发货',
    COMPLETED: '已完成',
    CANCELED: '已取消',
    CLOSED: '已关闭',
  };
  return statusMap[status] || status || '-';
}

// 履约方式的中文名。三者互斥：同城即时配送（商家自有运力）/ 快递配送（第三方物流）/ 门店自提。
// 兼容改造前的旧值 DELIVERY（那时不分即时与快递），按「同城即时配送」显示。
export function fulfillmentLabel(order) {
  const type = typeof order === 'string' ? order : order?.fulfillmentType;
  if (type === 'PICKUP') return '门店自提';
  if (type === 'EXPRESS') return '快递配送';
  return '同城即时配送';
}

// 订单状态文案：SHIPPED 对自提单是「待取货」，不是「已发货」——
// 自提单没有物流，后台走的是「备货完成」，说「已发货」会让用户以为有快递在路上。
export function orderStatusLabel(order) {
  const status = typeof order === 'string' ? order : order?.status;
  const fulfillment = typeof order === 'string' ? null : order?.fulfillmentType;
  if (status === 'SHIPPED' && fulfillment === 'PICKUP') return '待取货';
  return formatOrderStatus(status);
}

/**
 * 面向用户的「订单状态」文案 —— **列表页与详情页必须用同一个来源**。
 *
 * <p><b>为什么不能直接用 {@link orderStatusLabel}</b>：它在 PAID 时说「已支付」，
 * 而用户看到的是「商家还没发货」。同一个状态在两个页面显示不同（详情页「已支付」、
 * 列表页「待发货」），用户会以为是两笔不同的单、或者系统状态乱了。
 * 这个不一致 2026-10-07 被用户点出来：「订单状态...看着感觉很乱」。
 *
 * <p>自提单的 PAID 说「备货中」而不是「待发货」——没有快递可发，说待发货是骗人。
 *
 * <p>⚠️ 物流维度（运输中 / 派送中）目前**不存在**，本函数只是把订单状态翻译成
 * 用户能理解的话，不代表有独立的物流状态列。要加得先在 OrderEntity 独立建列。
 *
 * @returns {{label: string, cls: string}} cls 是徽标配色，与 {@link orderStatusTag} 同源
 */
export function userOrderStatus(order) {
  const status = typeof order === 'string' ? order : order?.status;
  const fulfillment = typeof order === 'string' ? null : order?.fulfillmentType;
  const map = {
    PENDING_PAYMENT: { label: '待付款', cls: 'warn' },
    PAID: { label: fulfillment === 'PICKUP' ? '备货中' : '待发货', cls: 'amber' },
    SHIPPED: { label: fulfillment === 'PICKUP' ? '待取货' : '待收货', cls: 'info' },
    COMPLETED: { label: '已完成', cls: 'ok' },
    CANCELED: { label: '已取消', cls: 'muted' },
    CLOSED: { label: '已关闭', cls: 'muted' },
  };
  return map[status] || { label: orderStatusLabel(order), cls: 'muted' };
}

export function formatPaymentStatus(status) {
  const statusMap = { UNPAID: '未支付', PAID: '已支付', REFUNDED: '已退款' };
  return statusMap[status] || status || '-';
}

export function formatRefundStatus(status) {
  const statusMap = { NONE: '-', APPLYING: '退款审核中', APPROVED: '退款成功', REJECTED: '退款被拒绝' };
  return statusMap[status] || status || '-';
}

export function refundStatusTag(status) {
  const tagMap = { APPLYING: 'warn', APPROVED: 'ok', REJECTED: 'muted' };
  return tagMap[status] || 'muted';
}

export function formatCouponStatus(status) {
  const statusMap = { UNUSED: '未使用', USED: '已使用', EXPIRED: '已过期' };
  return statusMap[status] || status || '-';
}

export function formatDate(value) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  const pad = (num) => String(num).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function formatProductStatus(status) {
  const statusMap = { ON_SALE: '上架', OFF_SALE: '下架', DRAFT: '草稿' };
  return statusMap[status] || status || '-';
}

export function orderStatusTag(status) {
  if (status === 'PENDING_PAYMENT') return 'warn';
  if (status === 'PAID') return '';
  if (status === 'SHIPPED' || status === 'COMPLETED') return 'ok';
  return 'muted';
}

export function formatUnit(unit) {
  const unitMap = { piece: '件', kg: '千克', bottle: '瓶', bag: '袋', box: '盒' };
  return unitMap[unit] || unit || '';
}

// 把后端存储的 unit（可能是代码、历史中文字面或自定义字面）还原为表单的 { unit, customUnit }
export function resolveUnit(raw) {
  const legacy = { '件': 'piece', '袋': 'bag', '千克': 'kg', '瓶': 'bottle', '盒': 'box' };
  const known = ['piece', 'kg', 'bottle', 'bag', 'box'];
  if (known.includes(raw)) return { unit: raw, customUnit: '' };
  if (legacy[raw]) return { unit: legacy[raw], customUnit: '' };
  if (raw) return { unit: 'custom', customUnit: String(raw) };
  return { unit: 'piece', customUnit: '' };
}

export function discountSave(orig, price) {
  const o = Number(orig) || 0;
  const p = Number(price) || 0;
  return o > p ? o - p : 0;
}

export function discountRate(orig, price) {
  const o = Number(orig) || 0;
  const p = Number(price) || 0;
  return (o > p && p > 0) ? (p / o * 10).toFixed(1) : '';
}

// 单个购物车项的「原价→现价」省了多少（已乘数量）；不足优惠时返回 0
export function itemOriginalSave(item) {
  const save = discountSave(item.productOriginalPrice, item.productPrice);
  return save > 0 ? save * Number(item.quantity || 1) : 0;
}

/** 省不到这个数就不值得标「省 ¥X」—— 省三毛还专门挂个标签，用户只会以为是 bug */
export const SAVE_CHIP_THRESHOLD = 1;

/**
 * 会员优惠标签的文案，**按优惠来源区分**，不能一律写「会员价」。
 *
 * 两种来源是性质不同的钱，冠同一个名字就是张冠李戴（2026-10-07 用户当场驳回：
 * 「关银卡会员价(0.98)什么事」）：
 *   · `memberSource === 'member'` → 商品专属会员价（商品表 member_price 更低）→ 写「会员价」成立
 *   · `memberSource === 'tier'`   → 只靠等级折扣（如银卡 0.98）便宜 → 写「会员价」是错的，
 *     这时写「银卡 9.8折」，与商品页/详情页 `productMemberView` 的既有口径一致
 *
 * **不设金额阈值 —— 这是 2026-10-07 用户明确纠正过的**：只有「省 ¥X」那个胶囊
 * 需要阈值（省三毛还专门挂个胶囊是噪音），但「你是金卡、享 9.5 折」是**身份与权益的告知**，
 * 省得多与省得少都该显示 —— 否则金卡用户看鸡蛋（只省几毛）完全不知道自己享了折扣，
 * 「我的会员等级到底起什么作用」就答不上来。
 * 换句话说：**金额阈值管「省了多少」，不该管「你是什么档位」。**
 *
 * 抽成共用函数而不是在购物车/结算页各写一份：两页本来就有同样的复制粘贴隐患，
 * 而「标签写错」是用户一眼就能发现的问题。
 *
 * @param {object} item 购物车/结算行（需含 memberDiscount / memberSource / quantity）
 * @param {number} [memberLevel] 用户档位，'tier' 来源时用来拼「金卡 9.5折」
 * @param {string} [tierName] 档位中文名（如「金卡会员」），缺省用「会员」
 */
export function memberTagText(item, memberLevel, tierName) {
  if (!item || item.flashSaleId || Number(item.flashPrice || 0) > 0) return '';
  if (!(Number(item.memberDiscount || 0) > 0)) return '';
  if (item.memberSource === 'tier') {
    const rates = { 0: 1, 1: 0.98, 2: 0.95, 3: 0.9, 4: 0.88, 5: 0.85, 6: 0.8 };
    const r = rates[Number(memberLevel || 0)] ?? 0.98;
    const name = (tierName || '会员').replace(/会员$/, '');
    return `${name} ${(r * 10).toFixed(1)}折`;
  }
  return '会员价';
}

// 订单的「已优惠」合计：优惠券 + 活动优惠 + 会员等级折扣 + 积分抵扣。
// 这四项才是实付的真实扣减项，少算任何一项都会让订单列表/后台显示的优惠额与实付对不上。
// （2026-09-14 修正：会员等级折扣与积分抵扣此前被漏算，导致详情页「已优惠」比实际少。）
export function orderSavedTotal(order) {
  if (!order) return 0;
  return Number(order.discountAmount || 0)
    + Number(order.activityDiscount || 0)
    + Number(order.memberDiscount || 0)
    + Number(order.pointsDiscount || 0);
}

// 订单的「划线优惠（已省）」：Σ（下单时原价 − 实际售价）× 数量。
// 仅作"已省多少"的信息展示 —— 该差额已体现在商品小计的单价口径里，不参与实付扣减，
// 因此不能计入 orderSavedTotal，否则会重复计算导致账目对不上。
export function orderOriginalSave(order) {
  return (order?.items || []).reduce((sum, it) => {
    const save = discountSave(it.originalPrice, it.productPrice);
    return save > 0 ? sum + save * Number(it.quantity || 1) : sum;
  }, 0);
}

// 购物车行的「为什么现在买不了」。返回空串代表这行正常。
// ⚠️ 存在的意义：这些情况后端只在「提交订单」时才拦（且只丢一句笼统的错误），
// 而用户明确要求过「应该提前禁用+提示，而不是等结算才提示」（秒杀限购那次的原话）。
// 所以购物车与结算页都用它提前标出问题行，别让用户白填一遍结算信息。
export function cartItemIssue(item) {
  if (!item) return '';
  if (item.onSale === false) return '已下架';
  const stock = Number(item.stock);
  if (Number.isFinite(stock)) {
    if (stock <= 0) return '已售罄';
    if (Number(item.quantity || 0) > stock) return `库存仅剩 ${stock} 件`;
  }
  return '';
}

// 购物车里所有「买不了」的行。含未勾选的行 —— 它们虽不阻塞结算，
// 但同样该被看见，否则会一直躺在车里没人管。
export function cartIssueItems(items) {
  return (items || []).filter((it) => cartItemIssue(it) !== '');
}

// 金额保留两位：JS 的浮点加减会漂（0.1+0.2=0.30000000000000004），
// 所有展示与比较前都要过这一道。与后端 BigDecimal.setScale(2, HALF_UP) 同口径。
export function round2(n) { return Math.round((Number(n) || 0) * 100) / 100; }

/**
 * 让「转圈」至少转够 {@link MIN_SPINNER_MS} 毫秒，配合加载文案消除「点了没反应」的错觉。
 *
 * 为什么要它：本地/内网的接口往往几十毫秒就返回，loading 态肉眼根本看不见 ——
 * 按钮闪一下就跳走，用户会以为点击被吞了（多半还会再点一次，导致重复提交）。
 * 而真正慢的接口（网关超时）本来就会自然超过阈值，这里**只补最短时长、不拖慢正常流程**。
 *
 * 用法（推荐传函数，别传已启动的 Promise）：
 *   `await withMinSpinner(() => api.post('/orders/1/pay'))`
 *
 * ⚠️ **必须先记起始时间、再启动工作**。若像这样写：
 *     `withMinSpinner(api.post(...))`   // 实参在调用前就已启动
 *   则 `startedAt` 取到的是「工作已经跑完」的时刻，耗时被算成 0，转圈等于没有。
 *   所以传函数进来，由本函数在记完时间后才调用它 —— 这也是只支持函数形式的根本原因。
 *   （传 Promise 形式仍能工作，但拿不到「最短时长」保证，别用。）
 *
 * 异常语义：realWork 抛错会正常向外抛（Promise.all 的行为），不会把失败吞成成功。
 *
 * **只给「用户主动点击、期待立刻反馈」的动作用**（支付、提交订单这类不可逆或耗时操作）。
 * 不要套在后台自动触发的刷新上 —— 那是自己给自己添堵，用户并没有在等任何反馈。
 *
 * @param {() => any} work 要执行的异步工作（传函数，不传已启动的 Promise）
 * @param {number} [minMs=MIN_SPINNER_MS] 本次的最短转圈时长；
 *   绝大多数场景用默认值即可（即「与支付一致」），需要单独时长的才传第二个参数。
 */
export const MIN_SPINNER_MS = 1000;

export function withMinSpinner(work, minMs = MIN_SPINNER_MS) {
  if (typeof work !== 'function') {
    throw new TypeError('withMinSpinner 需要传函数（如 () => api.post(...)），而不是已启动的 Promise');
  }
  const startedAt = Date.now();          // 先记时间
  let result;
  try {
    result = work();                     // 再启动工作
  } catch (e) {
    return Promise.reject(e);            // 同步抛错也保持异步语义
  }
  const elapsed = Date.now() - startedAt;
  const rest = Math.max(0, minMs - elapsed);
  return Promise.all([
    Promise.resolve(result),
    new Promise((resolve) => setTimeout(resolve, rest)),
  ]).then((r) => r[0]);
}
