// 纯展示/格式化工具函数，从 App.vue 抽取，避免巨型单文件重复定义。
// 不依赖任何组件作用域状态，可在任意组件/ composable 中复用。

export function money(value) {
  const amount = Number(value || 0);
  return `¥${amount.toFixed(2)}`;
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
