// 会员积分体系：档位、资料、积分流水、结算预览。
// 计价口径全部对齐后端 MemberService.unitPriceFor / OrderService：
// 会员价与等级折扣**取更低、不叠加**（见 productMemberView / memberUnitView 的注释）。
import { reactive, ref, computed, watch } from 'vue';

export function useMemberPoints({
  api, session, isAdmin, wallet, effectiveMemberLevel,
  orderPayPreview, cartMemberDiscount, expressFreight,
}) {
  const memberProfile = reactive({
    points: 0, memberLevel: 0, levelName: '', totalSpent: 0,
    discountRate: 1, nextLevelThreshold: null, nextLevelName: '', progressToNext: 0, maxRedeemRatio: 0.5,
  });
  const memberLedger = reactive({ items: [], total: 0, page: 1, size: 10, loading: false });
  const memberLevels = ref([]);
  const usePoints = ref(false);
  const pointsToUse = ref(0);

  // 等级档位兜底（与后端 MemberService 常量一致）：/member/levels 未就绪时也不漏算会员折扣
  const TIER_NAMES_FALLBACK = ['普通用户', '银卡会员', '金卡会员', '钻石会员', '紫钻会员', '黑卡会员', '至尊会员'];
  const TIER_RATES_FALLBACK = [1, 0.98, 0.95, 0.90, 0.88, 0.85, 0.80];

  function tierRateForLevel(level) {
    const lv = Number(level) || 0;
    const t = memberLevels.value.find((x) => x.level === lv);
    if (t) return Number(t.rate);
    if (Number(memberProfile.memberLevel) === lv && memberProfile.discountRate != null) {
      return Number(memberProfile.discountRate);
    }
    return TIER_RATES_FALLBACK[lv] != null ? TIER_RATES_FALLBACK[lv] : 1;
  }
  function tierNameFor(level) {
    const lv = Number(level) || 0;
    const t = memberLevels.value.find((x) => x.level === lv);
    if (t) return t.name;
    return TIER_NAMES_FALLBACK[lv] || '普通会员';
  }

  // 登录会员在某商品上的实付单价（与后端 unitPriceFor 同口径：会员价 与 等级折扣 取更低、不叠加）。
  // 非会员/游客返回 null —— 浏览页据此把主价切换成会员价并挂「X折会员价」标签，让列表/详情与购物车价格全程一致。
  function productMemberView(product) {
    const level = effectiveMemberLevel();
    if (level < 1) return null;
    const base = Number(product?.price || 0);
    if (!base) return null;
    const rate = tierRateForLevel(level);
    let price = base;
    let source = 'tier'; // 折扣来源：'member'=商品专属会员价更低，'tier'=等级折扣更低（默认）
    const mp = Number(product?.memberPrice || 0);
    if (mp > 0 && mp < price) { price = mp; source = 'member'; }
    const levelPrice = round2(base * rate);
    if (levelPrice < price) { price = levelPrice; source = 'tier'; }
    if (price >= base) return null;
    // 【单一基准 = 售价】会员视图的对照价一律取「售价」(base)：折率 = 实付 ÷ 售价、省 = 售价 − 实付，
    // 全系统只有一个基准，用户不会再拿吊牌价去乘折率（那是「银卡会员 7.5折」错觉的根源）。
    // 吊牌价只在「游客视图」当商品促销的划线出现，且与会员折标互斥不同屏。
    const original = base;
    return { price: round2(price), original, rate, name: tierNameFor(level), source };
  }
  // 与后端 unitPriceFor 同口径，作用于「已选规格/单品单价」(unit)：会员价取 min(商品级会员价, 等级折扣价)，
  // 默认规格价格也照常享折扣，保证 浏览(详情/卡片)→购物车 价格一致。
  function memberUnitView(unit, productMemberPrice) {
    const level = effectiveMemberLevel();
    if (level < 1) return null;
    const base = Number(unit || 0);
    if (!base) return null;
    const rate = tierRateForLevel(level);
    let price = base;
    let source = 'tier';
    const mp = Number(productMemberPrice || 0);
    if (mp > 0 && mp < price) { price = mp; source = 'member'; }
    const levelPrice = round2(base * rate);
    if (levelPrice < price) { price = levelPrice; source = 'tier'; }
    if (price >= base) return null;
    // original 同 productMemberView：【单一基准 = 售价】对照价一律取当前单价 base（含已选规格价），
    // 详情页「普通价」与折率都据此算，保证 卡片 ↔ 详情 ↔ 购物车 完全同口径。
    const original = base;
    return { price: round2(price), original, rate, name: tierNameFor(level), source };
  }
  function round2(n) { return Math.round((Number(n) || 0) * 100) / 100; }

  async function loadMemberLevels() {
    // 该接口需要登录：未登录时会 401（静默忽略，结算时由 tierRateForLevel 兜底）
    try { memberLevels.value = (await api.get('/member/levels')) || []; } catch (e) { /* ignore */ }
  }
  async function loadMemberProfile() {
    if (!session.user || isAdmin.value) return;
    try { Object.assign(memberProfile, (await api.get('/member/profile')) || {}); } catch (e) { /* ignore */ }
  }
  async function loadMemberLedger(page = 1) {
    if (!session.user || isAdmin.value) return;
    memberLedger.loading = true;
    try {
      const data = await api.get(`/member/ledger?page=${page}&size=${memberLedger.size}`);
      if (data) {
        memberLedger.items = data.items || [];
        memberLedger.total = data.total || 0;
        memberLedger.page = page;
      }
    } catch (e) { /* ignore */ } finally { memberLedger.loading = false; }
  }

  // 结算预览：会员折扣+ 积分抵现（与后端 OrderService 口径一致；当前目录无会员价，cartLocalTotal 即后端 totalAmount）
  const memberPreview = computed(() => {
    const amountAfterPromo = Number(orderPayPreview.value || 0);
    const level = session.user?.memberLevel || 0;
    // 会员等级折扣已下沉到「单品成交价」（后端逐商品取优：会员价 与 等级折扣 取更低、不叠加），
    // 购物车/预览返回的 regularPrice 已经是折后价。这里不再重复扣，而是把「已省的会员折扣」摊开成一行展示，
    // 让结算页明确写出「为什么比原价低」——金额口径与后端 Order.memberDiscount 一致（base − 会员净额）。
    const memberDiscount = round2(cartMemberDiscount.value);
    const payBeforePoints = Math.max(amountAfterPromo - memberDiscount, 0);
    const maxRedeemValue = round2(payBeforePoints * 0.5);
    const maxRedeemPoints = Math.floor(maxRedeemValue * 100);
    const userPoints = Number(session.user?.points || 0);
    let pointsUsed = 0;
    if (usePoints.value && userPoints > 0 && maxRedeemPoints > 0) {
      const want = Number(pointsToUse.value) > 0 ? Number(pointsToUse.value) : userPoints;
      pointsUsed = Math.max(0, Math.min(userPoints, want, maxRedeemPoints));
    }
    const pointsValue = round2(pointsUsed / 100);
    // 运费不参与任何折扣（券/活动/会员/积分都只作用于商品小计），最后加上去——
    // 与后端 OrderService 同口径，保证「商品小计 + 运费 − 券 − 活动 − 会员 − 积分 = 应付」成立
    const freight = expressFreight.value;
    const finalPay = Math.max(round2(payBeforePoints - pointsValue), 0) + freight;
    return { amountAfterPromo, memberDiscount, payBeforePoints, maxRedeemValue, maxRedeemPoints, userPoints, pointsUsed, pointsValue, freight, finalPay };
  });

  const balanceSufficient = computed(() => Number(wallet.balance || 0) >= memberPreview.value.finalPay);

  // 勾选「使用积分抵扣」时默认填入本次可用最大积分，保证输入框数值与实际抵扣一致
  watch(usePoints, (on) => {
    if (on) {
      pointsToUse.value = Math.min(memberPreview.value.userPoints, memberPreview.value.maxRedeemPoints);
    } else {
      pointsToUse.value = 0;
    }
  });

  return {
    memberProfile, memberLedger, memberLevels, usePoints, pointsToUse,
    TIER_NAMES_FALLBACK, TIER_RATES_FALLBACK,
    tierRateForLevel, tierNameFor, productMemberView, memberUnitView, round2,
    loadMemberLevels, loadMemberProfile, loadMemberLedger,
    memberPreview, balanceSufficient,
  };
}