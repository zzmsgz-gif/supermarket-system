/**
 * 节日氛围开关 —— **换/撤节日只改这一个文件**。
 *
 *   null          → 无节日装饰（平时状态）
 *   'midautumn'   → 中秋：首页 hero 月光晕 + 金色微光 + 飘落桂花
 *
 * 为什么单独抽一个文件：节日装饰是**季节性**的，节后必须能干净撤掉。
 * 若把氛围写死在布局/组件里，撤的时候要翻好几个文件、还容易漏（留个孤零零的月亮）。
 * 现在只需把 `FESTIVE_THEME` 改成 `null` —— CSS 与组件都以它为开关。
 *
 * ⚠️ 装饰层一律 `pointer-events: none`：它盖在 hero 上，绝不能挡住轮播/分类/公告的点击。
 * ⚠️ `prefers-reduced-motion: reduce` 时自动关闭飘落动画（只留静态光晕）。
 */
export const FESTIVE_THEME = 'midautumn';

/** 给 <html> 打标记，CSS 用它选择性地启用装饰（未启用时相关规则完全不生效）。 */
export function applyFestiveTheme() {
  if (typeof document === 'undefined') return;
  const root = document.documentElement;
  if (FESTIVE_THEME) {
    root.setAttribute('data-festive', FESTIVE_THEME);
  } else {
    root.removeAttribute('data-festive');
  }
}
