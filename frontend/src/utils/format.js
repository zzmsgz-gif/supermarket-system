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
  const roleMap = { ADMIN: '管理员', USER: '普通用户' };
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
