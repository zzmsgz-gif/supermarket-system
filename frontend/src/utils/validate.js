/**
 * 表单校验规则 —— **所有输入校验都从这里取，不要在调用处自己写正则**。
 *
 * <p>为什么集中在这里（2026-10-09）：
 * 手机号规则当时散在 3 个地方（前端 useAuth、前端 ProfilePage、后端 RegisterRequest），
 * 各自写了一份 `^1[3-9]\d{9}$`。同一条规则抄 3 遍 = 改一处忘两处，
 * 而且**收货地址那两处干脆完全没校验** —— 用户填「123」也能存，快递送不到。
 *
 * <p>约定：
 * <ul>
 *   <li>每个校验函数返回 **null（通过）** 或 **错误文案（不通过）**，不用 boolean
 *       —— 文案直接就能展示，调用方不必再写一遍「请输入正确的手机号」。</li>
 *   <li>入参统一先 trim，避免「前后空格」导致误判。</li>
 *   <li>后端对应位置要用**同一条正则**（见 backend 的 @Pattern），两边保持一致。</li>
 * </ul>
 */

/** 中国大陆手机号：1 开头，第二位 3-9，共 11 位 */
export const PHONE_RE = /^1[3-9]\d{9}$/;

/** 邮箱：够用的宽松校验 —— 严苛正则会误杀合法地址，真正的验证靠发信 */
export const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

/**
 * 手机号校验。
 * @param {string} v
 * @param {object} [opt] required=false 时允许为空（选填字段用）
 * @returns {string|null} 错误文案，通过返回 null
 */
export function checkPhone(v, opt = {}) {
  const { required = true, label = '手机号' } = opt;
  const s = String(v ?? '').trim();
  if (!s) return required ? `请填写${label}` : null;
  // 先把常见的全角/分隔符清掉，用户从通讯录粘贴常带这些
  const digits = s.replace(/[\s\-()（）]/g, '');
  if (!/^\d+$/.test(digits)) return `${label}只能填数字`;
  if (digits.length !== 11) return `${label}应为 11 位数字`;
  if (!PHONE_RE.test(digits)) return `${label}格式不正确`;
  return null;
}

/** 邮箱校验（默认选填 —— 大多数场景下邮箱不是必填） */
export function checkEmail(v, opt = {}) {
  const { required = false, label = '邮箱' } = opt;
  const s = String(v ?? '').trim();
  if (!s) return required ? `请填写${label}` : null;
  if (!EMAIL_RE.test(s)) return `${label}格式不正确`;
  return null;
}

/** 通用文本：非空 + 长度上限 */
export function checkText(v, opt = {}) {
  const { required = true, max = 50, label = '该项' } = opt;
  const s = String(v ?? '').trim();
  if (!s) return required ? `请填写${label}` : null;
  if (s.length > max) return `${label}不能超过 ${max} 个字符`;
  return null;
}

/**
 * 收货地址整体校验 —— 地址页与结算页**共用这份**（两处用的是同一个 addressForm，
 * 校验规则也必须同一份，否则会出现「地址页能存、结算页不能存」这种怪现象）。
 *
 * @param {object} form { receiverName, receiverPhone, province, city, district, detailAddress }
 * @returns {string|null} 第一条错误，通过返回 null
 */
export function checkAddress(form) {
  const f = form || {};
  return (
    checkText(f.receiverName, { required: true, max: 50, label: '收货人姓名' })
    || checkPhone(f.receiverPhone, { required: true })
    || (!f.province ? '请选择省份' : null)
    || (!f.city ? '请选择城市' : null)
    || (!f.district ? '请选择区/县' : null)
    || checkText(f.detailAddress, { required: true, max: 100, label: '详细地址' })
    || null
  );
}

/**
 * 输入时过滤：只允许数字且最多 11 位。
 *
 * <p>用在 input 上做**实时约束**（比事后报错友好）。注意这只是体验优化，
 * 提交时仍要走 checkPhone —— 粘贴、自动填充等路径能绕过 input 事件。
 *
 * @param {string} v 当前值
 * @param {number} maxLen
 */
export function digitsOnly(v, maxLen = Infinity) {
  const s = String(v ?? '').replace(/\D/g, '');
  return maxLen ? s.slice(0, maxLen) : s;
}
