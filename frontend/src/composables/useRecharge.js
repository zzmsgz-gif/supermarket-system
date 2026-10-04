/**
 * 钱包充值域（模拟支付宝 / 微信支付）
 *
 * 从 App.vue 抽出（原 1894-2071 行）。含：拉余额、预设/自定义金额、
 * 模拟收款码 SVG、10 分钟支付倒计时、创建/支付/取消/超时处理。
 *
 * 依赖注入说明：
 * - api：直接 import。
 * - money：金额格式化（与后端口径一致的展示工具），直接 import。
 * - session / isAdmin / rememberUser：拉余额要把余额同步回登录用户，注入。
 * - wallet / recharge：金额与充值流程的 reactive 状态，仍由 App.vue 持有（本模块只读写），注入。
 * - notice / fail / askConfirm / run：全局提示与统一执行包装，注入。
 *
 * ⚠️ 倒计时用 recharge.timer 存在 reactive 里；离开充值页必须 clearRechargeTimer()，
 *    否则后台定时器泄漏。这个 watch 留在 App.vue（依赖 view）。
 */
import { computed } from 'vue';
import { api } from '../api/client';
import { money } from '../utils/format';

export function useRecharge({ getSession, isAdmin, rememberUser, wallet, recharge, notice, fail, askConfirm, run }) {
  async function loadWallet() {
    const s = typeof getSession === 'function' ? getSession() : null;
    if (!s?.user || isAdmin.value) return;
    const data = await api.get('/wallet');
    Object.assign(wallet, data);
    rememberUser({ ...s.user, balance: data.balance });
  }

  function methodLabel(method) {
    if (method === 'WECHAT') return '微信支付';
    return '支付宝';
  }

  function selectRechargePreset(preset) {
    recharge.amount = preset;
    recharge.customAmount = '';
  }

  function onCustomAmountInput() {
    const value = Number(recharge.customAmount);
    if (recharge.customAmount === '' || Number.isNaN(value)) {
      recharge.amount = 0;
      return;
    }
    recharge.amount = value;
  }

  function formatCountdown(seconds) {
    const s = Math.max(0, Math.floor(seconds));
    const mm = String(Math.floor(s / 60)).padStart(2, '0');
    const ss = String(s % 60).padStart(2, '0');
    return `${mm}:${ss}`;
  }

  /** 根据订单号生成一张装饰性「二维码」SVG（非真实二维码，仅用于模拟扫码页） */
  function buildQrSvg(seed) {
    const size = 21;
    const cell = 8;
    let hash = 0;
    for (let i = 0; i < seed.length; i += 1) hash = (hash * 31 + seed.charCodeAt(i)) >>> 0;
    const rand = () => {
      hash = (hash * 1103515245 + 12345) >>> 0;
      return hash / 4294967296;
    };
    let rects = '';
    for (let y = 0; y < size; y += 1) {
      for (let x = 0; x < size; x += 1) {
        if (rand() > 0.5) {
          rects += `<rect x="${x * cell}" y="${y * cell}" width="${cell}" height="${cell}" />`;
        }
      }
    }
    // 三个定位角
    const finder = (ox, oy) => `<rect x="${ox * cell}" y="${oy * cell}" width="${cell * 7}" height="${cell * 7}" fill="#fff"/><rect x="${ox * cell}" y="${oy * cell}" width="${cell * 7}" height="${cell * 7}" fill="none" stroke="#1f2933" stroke-width="${cell}"/><rect x="${(ox + 2) * cell}" y="${(oy + 2) * cell}" width="${cell * 3}" height="${cell * 3}" fill="#1f2933"/>`;
    const svg = `<svg viewBox="0 0 ${size * cell} ${size * cell}" xmlns="http://www.w3.org/2000/svg" fill="#1f2933">${rects}${finder(0, 0)}${finder(size - 7, 0)}${finder(0, size - 7)}</svg>`;
    return svg;
  }

  const qrSvg = computed(() => buildQrSvg(recharge.order?.orderNo || 'RECHARGE'));

  function startCountdown() {
    clearRechargeTimer();
    if (!recharge.order?.expireAt) return;
    const expireAt = recharge.order.expireAt;
    const tick = () => {
      const remain = Math.max(0, Math.floor((expireAt - Date.now()) / 1000));
      recharge.countdown = remain;
      if (remain <= 0) {
        clearRechargeTimer();
        handleRechargeExpired();
      }
    };
    tick();
    recharge.timer = setInterval(tick, 1000);
  }

  function clearRechargeTimer() {
    if (recharge.timer) {
      clearInterval(recharge.timer);
      recharge.timer = null;
    }
  }

  async function confirmRecharge() {
    const amount = Number(recharge.amount);
    if (!Number.isFinite(amount) || amount <= 0) {
      fail('请选择或输入大于 0 的充值金额');
      return;
    }
    const ok = await askConfirm({
      title: '确认创建充值订单',
      message: `将创建一笔 ${methodLabel(recharge.method)} 充值订单，金额 ${money(amount)}，订单 10 分钟内有效。`,
      confirmText: '创建订单',
    });
    if (!ok) return;
    await run(async () => {
      const order = await api.post('/wallet/recharge-orders', { amount, method: recharge.method });
      recharge.order = order;
      recharge.step = 'paying';
      recharge.paying = false;
      startCountdown();
      notice.value = '充值订单已创建，请在支付页完成付款';
    }, null);
  }

  async function payRechargeOrder() {
    if (!recharge.order) return;
    const ok = await askConfirm({
      title: '确认支付',
      message: `确认通过${methodLabel(recharge.method)}支付 ${money(recharge.order.amount)}？支付成功后金额即时到账。`,
      confirmText: '立即支付',
    });
    if (!ok) return;
    recharge.paying = true;
    try {
      await run(async () => {
        const paid = await api.post(`/wallet/recharge-orders/${recharge.order.id}/pay`);
        recharge.order = paid;
        await loadWallet();
        clearRechargeTimer();
        recharge.step = 'success';
      }, '充值成功，金额已到账');
    } finally {
      recharge.paying = false;
    }
  }

  async function cancelRechargeOrder() {
    if (!recharge.order) return;
    const ok = await askConfirm({
      title: '取消充值订单',
      message: '确定取消该充值订单吗？取消后需重新创建。',
      confirmText: '取消订单',
      danger: true,
    });
    if (!ok) return;
    await run(async () => {
      await api.post(`/wallet/recharge-orders/${recharge.order.id}/cancel`);
      clearRechargeTimer();
      resetRecharge();
      notice.value = '充值订单已取消';
    }, null);
  }

  async function handleRechargeExpired() {
    // 倒计时归零：尝试在服务端取消（已超时的订单也兼容处理），并提示用户
    if (recharge.order) {
      try {
        await api.post(`/wallet/recharge-orders/${recharge.order.id}/cancel`);
      } catch (e) {
        // 订单可能已被调度任务置为 EXPIRED，忽略
      }
    }
    recharge.step = 'expired';
  }

  function closeRechargeModal() {
    // 支付中不允许直接关闭，需走「取消订单」；成功/超时允许返回
    if (recharge.step === 'paying') {
      cancelRechargeOrder();
      return;
    }
    clearRechargeTimer();
    resetRecharge();
  }

  function resetRecharge() {
    clearRechargeTimer();
    recharge.step = 'form';
    recharge.order = null;
    recharge.countdown = 0;
    recharge.paying = false;
    recharge.customAmount = '';
    recharge.amount = 100;
    recharge.method = 'ALIPAY';
  }

  return {
    loadWallet, methodLabel, selectRechargePreset, onCustomAmountInput,
    formatCountdown, buildQrSvg, qrSvg,
    startCountdown, clearRechargeTimer,
    confirmRecharge, payRechargeOrder, cancelRechargeOrder,
    handleRechargeExpired, closeRechargeModal, resetRecharge,
  };
}
