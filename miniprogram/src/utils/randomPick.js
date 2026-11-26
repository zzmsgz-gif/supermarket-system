/**
 * 按 seed 洗牌取样 —— 用于「大家都在买」这类需要**每次刷新换一批、但同一屏内顺序稳定**的模块。
 *
 * 为什么不用 Math.random()：
 * 模板每次重渲染都会重新调用，随机值一变列表顺序就跳，用户看着像卡顿/闪烁。
 * 同一个 seed 必须给出同一个结果，换 seed 才换一批。
 *
 * 为什么不用后端 sort=random_<seed>：
 * 后端那个是对**全量在售商品**洗牌。而「大家都在买」的语义是热销品，
 * 所以做法是：先取热销池（/products/hot?limit=30），再在池内洗牌取前 N 件。
 */

/**
 * xorshift32 伪随机数（同 seed 稳定、分布够均匀，实现只有几行，不引依赖）。
 * @param {number} seed
 * @returns {() => number} 0~1 的生成器
 */
function mulberry(seed) {
  let s = Math.abs(Math.floor(seed)) % 4294967296
  if (s === 0) s = 0x9e3779b9
  return function next() {
    s ^= s << 13
    s >>>= 0
    s ^= s >>> 17
    s ^= s << 5
    s >>>= 0
    return s / 4294967296
  }
}

/**
 * 从 list 里按 seed 洗牌后取前 count 个（不改原数组）。
 * @param {Array} list
 * @param {number} count
 * @param {number} seed
 */
export function seededPick(list, count, seed) {
  const arr = Array.isArray(list) ? list.slice() : []
  const n = Math.max(0, Math.min(Number(count) || 0, arr.length))
  if (arr.length <= 1 || n === 0) return arr.slice(0, n)
  const rand = mulberry(seed)
  for (let i = arr.length - 1; i > 0; i -= 1) {
    const j = Math.floor(rand() * (i + 1))
    const tmp = arr[i]
    arr[i] = arr[j]
    arr[j] = tmp
  }
  return arr.slice(0, n)
}

/** 生成一个新 seed（页面进入/下拉刷新时调用） */
export function newSeed() {
  return Date.now() % 2147483647
}
