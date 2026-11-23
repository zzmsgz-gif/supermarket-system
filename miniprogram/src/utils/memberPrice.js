import { ref } from 'vue'
import { getMemberProfile } from '@/api/member'
import { isLoggedIn } from '@/store/auth'

// 会员信息模块级单例：首页、详情页、猜你喜欢共用一份，避免每进一个页面都拉一次。
// 游客为 null，所有卡片/价格视图自动走原价。
export const memberProfile = ref(null)

let loading = null

/**
 * 加载会员信息（幂等：并发调用共用同一个 Promise，已加载过直接复用）。
 * 游客不请求；失败静默降级为 null（会员价失败不该让商品页打不开）。
 */
export function ensureMemberProfile(force = false) {
  if (!isLoggedIn()) { memberProfile.value = null; return Promise.resolve(null) }
  if (!force && memberProfile.value) return Promise.resolve(memberProfile.value)
  if (loading) return loading
  loading = getMemberProfile()
    .then((p) => { memberProfile.value = p || null; return memberProfile.value })
    .catch(() => { memberProfile.value = null; return null })
    .finally(() => { loading = null })
  return loading
}

/**
 * 会员实付价视图（与 PC 端 productMemberView / 后端 unitPriceFor 同口径）：
 *   min(商品专属会员价, 售价 × 等级折扣)，取低、不叠加。
 * 游客/非会员返回 null。
 * @param product 商品对象（需 price / memberPrice）
 * @param basePrice 可选：已选 SKU 的价格（详情页用），不传则取 product.price
 */
export function memberView(product, basePrice) {
  const prof = memberProfile.value
  if (!prof || !Number(prof.memberLevel)) return null
  const base = Number(basePrice != null ? basePrice : (product && product.price) || 0)
  if (!base) return null
  const rate = Number(prof.discountRate || 1)
  if (!(rate > 0 && rate < 1)) return null
  const mp = Number(product && product.memberPrice || 0)
  // source 决定标签文案：商品自己标了更低的会员价 → 'member'（挂「会员价」）；
  // 否则是等级折扣更低 → 'tier'（挂「银卡9.8折」）。
  // 不能一律写成「9.8折」—— 走专属会员价的商品用折率表达会与实际金额对不上。
  let price = base
  let source = 'tier'
  if (mp > 0 && mp < price) { price = mp; source = 'member' }
  const levelPrice = Math.round(base * rate * 100) / 100
  if (levelPrice < price) { price = levelPrice; source = 'tier' }
  if (price >= base) return null
  return {
    price: price.toFixed(2),
    rate,
    original: base.toFixed(2),
    source,
    levelName: prof.levelName || ''
  }
}

/** 等级名去掉「会员」二字：银卡会员 → 银卡（与 PC 端 shortTier 一致） */
export function shortTier(name) {
  return String(name || '').replace(/会员/g, '')
}

/** 等级折扣文案，如「9.8折」 */
export function memberRateText(view) {
  return view ? (view.rate * 10).toFixed(1) + '折' : ''
}

/**
 * 价格旁那枚会员标签的统一文案（四处商品卡共用）：
 *   source='member' → 「会员价」（价格本身已是折后价，小尺寸下不重复写金额）
 *   source='tier'   → 「银卡9.8折」
 */
export function memberLabel(view) {
  if (!view) return ''
  if (view.source === 'member') return '会员价'
  return shortTier(view.levelName) + (view.rate * 10).toFixed(1) + '折'
}
