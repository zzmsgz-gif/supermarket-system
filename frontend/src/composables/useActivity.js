/**
 * 活动域：满减/折扣活动的口号、商品角标、凑单进度条、顶部公告带轮播
 *
 * 从 App.vue 抽出（原 571-693 行）。**全部与后端 evaluateBestActivity 同口径**：
 * 「取减免金额最大者」—— 角标/进度条/结算必须显示同一条活动，否则会出现
 * 「卡上写满200打8折、实付按满200减50」，被当成欺骗。
 *
 * 依赖注入说明：
 * - api：直接 import（无状态）。
 * - getCartLocalTotal：凑单进度条要「商品小计」判门槛。它属购物车域，
 *   而购物车也要用活动优惠（estimateGuestActivity）→ 直接 import 会成环，故用 getter 注入。
 * - activeActivities 状态在本模块内自持（它只被活动域用）。
 */
import { computed, ref } from 'vue';
import { api } from '../api/client';

export function useActivity({ getCartLocalTotal }) {
  const activeActivities = ref([]);

  // 活动规则缓存拉取（凑单进度条用；公开接口，游客也可调）
  async function ensureActiveActivities() {
    if (activeActivities.value.length) return;
    try { activeActivities.value = (await api.get('/activities/active')) || []; } catch { /* 非关键 */ }
  }

  // 活动口号：通栏横幅/商品角标共用
  function activitySlogan(a) {
    if (!a) return '';
    const t = Number(a.threshold || 0);
    return a.type === 'DISCOUNT'
      ? `满${t}打${Math.round(Number(a.discount) * 10)}折`
      : `满${t}减${Number(a.discount)}`;
  }

  // 顶部公告带单条文案：活动名里已含「满/折/减」语义时只显名字，避免与口号重复
  function activityNoticeText(a) {
    const name = String(a.name || '').trim();
    const slogan = activitySlogan(a);
    if (/满|折|减/.test(name)) return name;
    return name ? `${name} · ${slogan}` : slogan;
  }

  // 到达门槛后能减多少 —— 与后端 evaluateBestActivity「取减免最大者」同一口径
  function activityOffset(a) {
    return a.type === 'DISCOUNT'
      ? Number(a.threshold) * (1 - Number(a.discount))
      : Number(a.discount);
  }

  // 商品是否命中该活动（ALL / CATEGORY / PRODUCT 三种范围）
  function activityMatches(a, product) {
    const scope = String(a.scope || 'ALL');
    if (scope === 'PRODUCT') return Number(a.productId) === Number(product.id);
    if (scope === 'CATEGORY') return a.categoryId != null && Number(a.categoryId) === Number(product.categoryId);
    return true;
  }

  // 全站通栏首推：按「满额时的实际减免」取力度最大者
  const topActivity = computed(() => [...(activeActivities.value || [])]
    .filter((a) => Number(a.threshold || 0) > 0 && Number(a.discount || 0) > 0)
    .sort((x, y) => activityOffset(y) - activityOffset(x))[0] || null);

  // 商品卡角标：只在活动**限定了分类或单品**（scope ≠ ALL）时才显示，且取命中的活动里「最省」的那个。
  //
  // 为什么排除全场活动：全场活动对每一张商品卡都是同一句话，一屏重复几十次没有任何信息量，
  // 反而把真正有区分度的「分类专属 / 单品专属」活动淹没了；全场活动交给页头公告条统一宣传。
  // 为什么取「最省」而不是「第一个命中的」：角标必须与结算实际生效的那条一致，
  // 否则会出现「卡上写满200打8折、实付按满200减50」，被当成欺骗。
  function productActivityTag(product) {
    const hits = (activeActivities.value || [])
      .filter((a) => Number(a.threshold || 0) > 0 && Number(a.discount || 0) > 0)
      .filter((a) => String(a.scope || 'ALL') !== 'ALL')
      .filter((a) => activityMatches(a, product));
    if (!hits.length) return '';
    return activitySlogan([...hits].sort((x, y) => activityOffset(y) - activityOffset(x))[0]);
  }

  // 凑单进度条：与后端同口径——只有达到门槛的活动才生效，实际生效取「减免金额最大者」，
  // 未达门槛时宣传的也是「达到门槛后减免最大」的同一活动，保证达标前后文案一致。
  const cartActivityProgress = computed(() => {
    const amount = typeof getCartLocalTotal === 'function' ? getCartLocalTotal() : 0;
    const rules = (activeActivities.value || [])
      .filter((a) => Number(a.threshold || 0) > 0 && Number(a.discount || 0) > 0 && String(a.scope || 'ALL') === 'ALL');
    if (!rules.length || amount <= 0) return null;
    const discountOf = (a, base) => {
      if (base < Number(a.threshold)) return 0;
      if (a.type === 'DISCOUNT') {
        const rate = Number(a.discount);
        return (rate > 0 && rate < 1) ? Math.round(base * (1 - rate) * 100) / 100 : 0;
      }
      return Number(a.discount);
    };
    const benefitOf = (a) => a.type === 'DISCOUNT'
      ? `打 ${Math.round(Number(a.discount) * 10)} 折`
      : `立减 ¥${Number(a.discount)}`;
    // 已达门槛的活动里取减免最大者（与后端 evaluateBestActivity 一致）
    const bestApplied = rules
      .map((rule) => ({ rule, off: discountOf(rule, amount) }))
      .sort((x, y) => y.off - x.off)[0];
    // 尚未达标的活动里，按「到达门槛后能减多少」取最优者，作为凑单目标
    const bestUpcoming = rules
      .filter((a) => amount < Number(a.threshold))
      .map((rule) => ({ rule, off: discountOf(rule, Number(rule.threshold)) }))
      .sort((x, y) => y.off - x.off || Number(x.rule.threshold) - Number(y.rule.threshold))[0];
    // 若继续凑单能换到更大优惠，优先展示凑单提示（当前已享优惠仍由合计区展示）
    if (bestUpcoming && bestUpcoming.off > (bestApplied ? bestApplied.off : 0)) {
      const target = bestUpcoming.rule;
      return {
        benefit: benefitOf(target),
        gap: Math.max(Number(target.threshold) - amount, 0),
        threshold: Number(target.threshold),
        amount,
        percent: Math.min(100, Math.round((amount / Number(target.threshold)) * 100)),
        reachedTop: false,
      };
    }
    if (!bestApplied) return null;
    return {
      benefit: benefitOf(bestApplied.rule),
      gap: 0,
      threshold: Number(bestApplied.rule.threshold),
      amount,
      percent: 100,
      reachedTop: true,
    };
  });

  // 公告带轮播：全部来自后台「营销活动管理」（4s 一换）
  //   · 满减/折扣活动 → 「限时活动 满200减50」
  //   · 纯文案活动(type=PROMOTION) → 直接显示 name（新人福利这类只宣传不让利的文案）
  // 不再有任何硬编码，也不再从「商城公告」取 —— 避免同一内容在公告栏与顶栏各显示一遍。
  const noticeIndex = ref(0);
  let noticeTimer = null;
  const noticeList = computed(() => {
    const items = [];
    for (const a of activeActivities.value || []) {
      if (String(a.type || '').toUpperCase() === 'PROMOTION') {
        const text = String(a.name || '').trim();
        if (text) items.push(text);
        continue;
      }
      if (Number(a.threshold || 0) > 0 && Number(a.discount || 0) > 0) {
        items.push(`限时活动 ${activityNoticeText(a)}`);
      }
    }
    return items;
  });
  const rotatingNotice = computed(() => noticeList.value[noticeIndex.value % noticeList.value.length] || '');

  // 公告轮播定时器：4 秒换一条。onMounted 时启动、onBeforeUnmount 时清理，
  // 由 App.vue 在自己的生命周期钩子里调用（保持与拆分前完全一致的行为）。
  function startNoticeRotation() {
    stopNoticeRotation();
    noticeTimer = setInterval(() => { if (noticeList.value.length > 1) noticeIndex.value += 1; }, 4000);
  }
  function stopNoticeRotation() {
    if (noticeTimer) { clearInterval(noticeTimer); noticeTimer = null; }
  }

  return {
    activeActivities, noticeIndex, noticeList, rotatingNotice, topActivity, cartActivityProgress,
    ensureActiveActivities, activitySlogan, activityNoticeText, activityOffset, activityMatches,
    productActivityTag, startNoticeRotation, stopNoticeRotation,
  };
}
