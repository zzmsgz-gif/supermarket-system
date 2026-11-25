/**
 * 商品卡图区角标 —— 与 PC `ProductCard.vue` 的 `.badge-row` **完全一致**：
 *   省¥X → 热 → 新 → 会员价
 *
 * 2026-11-25：用户要求「去掉小程序卡片上的营销标签、跟 PC 统一」。
 * 实际差异只在一点 —— 小程序原来还会把后台 `tags` 的第一个词（「咖啡」「速冻」「烘焙」…）
 * 做成绿色角标，而 PC 卡片从来不显示 tags（PC 只在名称下方显示品牌·副标题）。
 * 所以这里去掉类目标签，其余四个角标保留。
 *
 * ⚠️ 为什么必须横排（模板里是 flex + wrap）：
 * PC 端这四个角标原来各自 `absolute; top:8px; left:8px`，同现时互相叠盖只露最后一个。
 * 小程序也一样，所以模板里统一包一层 `.p-badge-row`，角标自身不再定位。
 *
 * ⚠️ 小程序 wxml 不能调用 JS 方法：模板里只能用这里导出的函数，
 * 不能写 `p.tags.split(',')` / `Number(p.isHot)` 之类（运行时静默失效）。
 */
import { memberView } from '@/utils/memberPrice'
import { productActivityTag } from '@/utils/activity'

/**
 * 「省¥X」—— 会员省了多少。
 * 口径与 PC `saveVsSale` 一致：**售价 − 实付**（不是吊牌价 − 实付），满 1 元才标，
 * 避免「省¥0.3」这种噪音角标。游客 memberView 为 null → 恒为 0（不显示）。
 */
export function saveBadgeText(p) {
  const v = memberView(p)
  if (!v) return ''
  const saved = Number((p && p.price) || 0) - Number(v.price)
  if (!(saved >= 1)) return ''
  const s = String(Math.round(saved * 100) / 100).replace(/\.00$/, '')
  return '省¥' + s
}

/** 该商品是否有「专属会员价」（与 PC `memberPrice` 同判据：商品自带价且低于售价，次秒杀） */
export function hasMemberPriceBadge(p) {
  const mp = Number((p && p.memberPrice) || 0)
  return mp > 0 && mp < Number((p && p.price) || 0)
}

/** 图区角标数组：`k` 决定配色（save/hot/new/vip），模板用 `'p-badge-' + b.k` */
export function pcardBadges(p) {
  const out = []
  const save = saveBadgeText(p)
  if (save) out.push({ t: save, k: 'save' })
  if (Number((p && p.isHot) === 1)) out.push({ t: '热', k: 'hot' })
  if (Number((p && p.isNew) === 1)) out.push({ t: '新', k: 'new' })
  if (hasMemberPriceBadge(p)) out.push({ t: '会员价', k: 'vip' })
  return out
}

/**
 * 卡片右下角的活动标签（满减/折扣）。
 * 与 PC 一致：走 `productActivityTag`，**全场活动不显示**（只有限定了分类/单品的才标），
 * 否则每张卡都挂一条「满99减10」，等于没信息。
 */
export function activityTagOf(list, p) {
  return productActivityTag(list || [], p)
}
