import { ref } from 'vue'
import { request } from '@/api/request'

/**
 * 商品评分（★4.8 · 12 人评）——全站商品卡共用同一份聚合数据。
 *
 * <p>数据源是后端 `GET /products/rating-summary`，一次返回**全部商品**的平均分与评价数
 * （与 PC 端 `appCtx.ratingSummaryMap` 完全同源，保证两端分数一致）。
 * 所以这里做成模块级单例 + 幂等加载：进任何一页拉一次，之后所有卡片共用，不按商品逐个请求。
 *
 * <p>为什么用 `ref` 而不是普通对象：卡片渲染时调 `ratingOf(p.id)`，接口是异步回来的，
 * 普通对象改完模板不会重渲染 —— 表现为「评分永远不出现」。用 ref 整体替换才会触发更新。
 */
export const ratingMap = ref({})

let loading = null

/**
 * 评价少于这个数就不展示评分：两三条评价很容易是 5.0 或 1.0，显示出来反而误导。
 * 与 PC 端 `ProductCard` 的 `ratingInfo` 口径保持一致（那边注释写的是「<3 条不展示」）。
 */
export const MIN_REVIEWS = 3

/**
 * 加载评分聚合（幂等：并发共用同一个 Promise，已有数据直接返回）。
 * 失败静默降级为空 map —— 评分拿不到不该让商品列表打不开。
 */
export function ensureRatings(force = false) {
  if (!force && Object.keys(ratingMap.value).length) return Promise.resolve(ratingMap.value)
  if (loading) return loading
  loading = request('/products/rating-summary', { auth: false })
    .then((list) => {
      const m = {}
      ;(list || []).forEach((it) => {
        const id = Number(it.productId)
        if (!id) return
        const count = Number(it.reviewCount || 0)
        if (count <= 0) return
        m[id] = { avg: Number(it.avgRating || 0), count }
      })
      ratingMap.value = m
      return m
    })
    .catch(() => {
      // 置成空对象而不是 null：避免每次渲染都判定「还没加载」而重复请求
      ratingMap.value = {}
      return {}
    })
    .finally(() => {
      loading = null
    })
  return loading
}

/**
 * 卡片用的评分视图。评价不足 {@link MIN_REVIEWS} 条、或该商品没有评价 → 返回 null，
 * 模板用 `v-if` 直接不渲染，不留空位。
 * @param {number|string} productId
 * @returns {{text: string, count: number}|null} text 已格式化成一位小数
 */
export function ratingOf(productId) {
  const hit = ratingMap.value[Number(productId)]
  if (!hit || hit.count < MIN_REVIEWS) return null
  return { text: hit.avg.toFixed(1), count: hit.count }
}
