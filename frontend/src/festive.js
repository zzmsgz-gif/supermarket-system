import { ref } from 'vue';
import { api } from './api/client';

/**
 * 首页节日氛围开关 —— **换/撤节日只改这个文件 + 后台「首页氛围」菜单**。
 *
 *   'daily'      → 日常：柔和上浮光斑（默认，节后回到日常，常年适用）
 *   'midautumn'  → 中秋：hero 月光晕 + 金色微光 + 飘落桂花（保留，到时切回）
 *   'none'       → 关闭：完全不显示任何装饰
 *
 * 配置来源：后端 system_config.festive_theme（后台「首页氛围」菜单可切）。
 * 前端只「读配置 + 回落默认值」，不再写死。默认 daily —— 即使接口挂了或还没配，
 * 首页也是干净日常态，不会一夜回到写死的中秋。
 *
 * ⚠️ 装饰层一律 `pointer-events: none`：盖在 hero 上，绝不能挡住轮播/分类/公告的点击。
 * ⚠️ `prefers-reduced-motion: reduce` 时自动关闭飘落/上浮动画（只留静态光晕）。
 */

// 候选主题：管理端「首页氛围」下拉的单一来源（label/desc 同时给前端展示）
export const FESTIVE_THEMES = [
  { value: 'daily', label: '日常特效', desc: '柔和上浮光斑，常年适用的清爽氛围' },
  { value: 'midautumn', label: '中秋特效', desc: '月光晕 + 金色微光 + 飘落桂花' },
  { value: 'none', label: '关闭装饰', desc: '完全不显示任何节日装饰' },
];

const DEFAULT_THEME = 'daily';
const VALID = new Set(FESTIVE_THEMES.map((t) => t.value));

// 当前生效主题（reactive：接口拉回 / 后台切换都会即时反映到 FestiveDecor）
export const currentFestiveTheme = ref(DEFAULT_THEME);

/** 把传入（或当前）主题打到 <html data-festive>，CSS 据此选择性启用装饰。 */
export function applyFestiveTheme(theme = currentFestiveTheme.value) {
  if (typeof document === 'undefined') return;
  const root = document.documentElement;
  const value = VALID.has(theme) ? theme : DEFAULT_THEME;
  if (value && value !== 'none') {
    root.setAttribute('data-festive', value);
  } else {
    root.removeAttribute('data-festive');
  }
}

/** 本地即时设置（管理端预览用，不落库）：仅改内存 + DOM，不影响后端配置。 */
export function setFestiveTheme(theme) {
  if (!VALID.has(theme)) return;
  currentFestiveTheme.value = theme;
  applyFestiveTheme(theme);
}

/** 从后端拉取 festive_theme 配置并应用（main.js 启动调用一次）。失败回落默认 daily。 */
export async function fetchFestiveTheme() {
  try {
    const res = await api.get('/system-config?key=festive_theme');
    const value = res && res.configValue;
    currentFestiveTheme.value = value && VALID.has(value) ? value : DEFAULT_THEME;
  } catch (e) {
    // 接口失败：回落默认 daily，不影响首页正常展示
    currentFestiveTheme.value = DEFAULT_THEME;
  }
  applyFestiveTheme();
  return currentFestiveTheme.value;
}
