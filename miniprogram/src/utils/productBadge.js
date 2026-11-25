/**
 * 商品卡图区角标 —— 对齐 PC `ProductCard.vue` 的 `.badge-row`：
 *   类目标签 → 省¥X → 热 → 新 → 会员价
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

/** 商品自带的类目标签（后台 tags 字段第一个）。**不回退** isHot/isNew —— 那两个走独立角标 */
export function customTagOf(p) {
  const raw = p && p.tags
  if (!raw || !String(raw).trim()) return ''
  const first = String(raw).split(/[,，\s]+/).filter(Boolean)[0]
  if (!first) return ''
  // 卡片图区就一小条，超过 4 个字必被截断，不如自己截干净
  return first.length > 4 ? first.slice(0, 4) : first
}

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

/** 图区角标数组：`k` 决定配色（tag/save/hot/new/vip），模板用 `'p-badge-' + b.k` */
export function pcardBadges(p) {
  const out = []
  const c = customTagOf(p)
  if (c) out.push({ t: c, k: 'tag' })
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
