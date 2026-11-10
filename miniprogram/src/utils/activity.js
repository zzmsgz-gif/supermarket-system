/**
 * 满减 / 折扣活动 —— 纯函数工具，**判定口径必须与后端 evaluateBestActivity 和 web 端
 * `frontend/src/composables/useActivity.js` 完全一致**：
 *
 * 「取减免金额最大者」。角标、凑单进度条、结算实付必须指向同一条活动，
 * 否则会出现「卡上写满200打8折、实付按满200减50」，用户会当成价格欺骗。
 *
 * 活动字段（GET /activities/public 或 /activities/active）：
 *   id, name, type(FULL_REDUCTION|DISCOUNT|PROMOTION), scope(ALL|CATEGORY|PRODUCT),
 *   categoryId, productId, threshold, discount, startTime, endTime, status, priority
 */

// 门槛与让利都大于 0 才算「可参与计算」的让利型活动
export function isBenefit(a) {
  return !!a && Number(a.threshold || 0) > 0 && Number(a.discount || 0) > 0
}

// 口号：折扣型「满X打Y折」，满减型「满X减Y」
export function activitySlogan(a) {
  if (!a) return ''
  const t = Number(a.threshold || 0)
  return a.type === 'DISCOUNT'
    ? `满${t}打${Math.round(Number(a.discount) * 10)}折`
    : `满${t}减${Number(a.discount)}`
}

// 达到门槛时能减多少（折扣按门槛金额算），用于「取最大减免」
export function activityOffset(a) {
  if (!a) return 0
  return a.type === 'DISCOUNT'
    ? Number(a.threshold) * (1 - Number(a.discount))
    : Number(a.discount)
}

// 商品是否命中该活动
export function activityMatches(a, product) {
  if (!a || !product) return false
  const scope = String(a.scope || 'ALL')
  if (scope === 'PRODUCT') return Number(a.productId) === Number(product.id)
  if (scope === 'CATEGORY') return a.categoryId != null && Number(a.categoryId) === Number(product.categoryId)
  return true
}

// 公告条文案：活动名里已含「满/折/减」就只显名字，避免与口号重复
export function activityNoticeText(a) {
  if (!a) return ''
  const name = String(a.name || '').trim()
  const slogan = activitySlogan(a)
  if (/满|折|减/.test(name)) return name
  return name ? `${name} · ${slogan}` : slogan
}

// 全站首推：满额减免力度最大的那条（首页横幅用）
export function pickTopActivity(list) {
  const arr = (list || []).filter(isBenefit)
  if (!arr.length) return null
  return [...arr].sort((x, y) => activityOffset(y) - activityOffset(x))[0]
}

/**
 * 商品角标：**只对限定了分类/单品的活动显示**（scope ≠ ALL）。
 * 全场活动对每张卡都是同一句话，一屏重复几十次没信息量，交给首页横幅统一宣传。
 * 命中多条时取减免最大的，保证与结算实际生效的一致。
 */
export function productActivityTag(list, product) {
  const hits = (list || [])
    .filter(isBenefit)
    .filter((a) => String(a.scope || 'ALL') !== 'ALL')
    .filter((a) => activityMatches(a, product))
  if (!hits.length) return ''
  return activitySlogan([...hits].sort((x, y) => activityOffset(y) - activityOffset(x))[0])
}

/**
 * 购物车凑单进度（只看 scope=ALL 的全场活动，与 web 一致）。
 * - 已达门槛：取减免最大者，percent=100, reachedTop=true
 * - 未达门槛：若继续凑能省更多，提示「还差 ¥X 可 立减 ¥Y / 打 Z 折」
 * 返回 null 表示没有可展示的活动。
 */
export function cartActivityProgress(list, amount) {
  const base = Number(amount || 0)
  const rules = (list || [])
    .filter(isBenefit)
    .filter((a) => String(a.scope || 'ALL') === 'ALL')
  if (!rules.length || base <= 0) return null

  const discountOf = (a, money) => {
    if (money < Number(a.threshold)) return 0
    if (a.type === 'DISCOUNT') {
      const rate = Number(a.discount)
      return (rate > 0 && rate < 1) ? Math.round(money * (1 - rate) * 100) / 100 : 0
    }
    return Number(a.discount)
  }
  const benefitOf = (a) => (a.type === 'DISCOUNT'
    ? `打 ${Math.round(Number(a.discount) * 10)} 折`
    : `立减 ¥${Number(a.discount)}`)

  const bestApplied = rules
    .map((rule) => ({ rule, off: discountOf(rule, base) }))
    .sort((x, y) => y.off - x.off)[0]

  const bestUpcoming = rules
    .filter((a) => base < Number(a.threshold))
    .map((rule) => ({ rule, off: discountOf(rule, Number(rule.threshold)) }))
    .sort((x, y) => y.off - x.off || Number(x.rule.threshold) - Number(y.rule.threshold))[0]

  if (bestUpcoming && bestUpcoming.off > (bestApplied ? bestApplied.off : 0)) {
    const target = bestUpcoming.rule
    return {
      benefit: benefitOf(target),
      gap: Math.max(Number(target.threshold) - base, 0),
      threshold: Number(target.threshold),
      amount: base,
      percent: Math.min(100, Math.round((base / Number(target.threshold)) * 100)),
      reachedTop: false
    }
  }
  if (!bestApplied || bestApplied.off <= 0) return null
  return {
    benefit: benefitOf(bestApplied.rule),
    gap: 0,
    threshold: Number(bestApplied.rule.threshold),
    amount: base,
    percent: 100,
    reachedTop: true
  }
}
