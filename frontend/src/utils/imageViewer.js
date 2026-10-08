/**
 * 图片预览器（Lightbox）：点缩略图看大图。
 *
 * 为什么做成全局单例而不是每个页面各写一份：
 * 评价晒图、用户头像、售后凭证、商品图片都要求「点开看大图」，
 * 各写一份必然出现 N 套样式和 N 份「按 ESC 关闭 / 点遮罩关闭 / 记住滚动位置」的重复逻辑。
 *
 * 用法：
 *   import { openImageViewer } from '../utils/imageViewer';
 *   openImageViewer(url, { all: [url1, url2], index: 0 });   // 支持左右切换
 *   // 模板里：@click="openImageViewer(review.imageUrls[0], { all: review.imageUrls, index: 0 })"
 *
 * 实现要点：
 *   · 挂到 body 上、用 Teleport —— 避免被祖先的 overflow/transform 裁剪或错位
 *   · 打开时锁 body 滚动；关闭时**恢复原样**（记 scrollY，用 position:fixed 技巧）
 *   · 监听 Esc / 遮罩点击 / 左右键；每次打开都重置监听，避免重复绑定导致关不掉
 */


import { reactive } from 'vue';

const state = reactive({
  open: false,
  src: '',
  list: [],
  index: 0,
});

let bound = false;
let savedScrollY = 0;

function onKey(e) {
  if (!state.open) return;
  if (e.key === 'Escape') closeImageViewer();
  else if (e.key === 'ArrowLeft') step(-1);
  else if (e.key === 'ArrowRight') step(1);
}

function step(delta) {
  if (!state.list.length) return;
  const n = state.list.length;
  state.index = (state.index + delta + n) % n;
  state.src = state.list[state.index];
}

function lockScroll() {
  savedScrollY = window.scrollY;
  document.body.style.position = 'fixed';
  document.body.style.top = `-${savedScrollY}px`;
  document.body.style.width = '100%';
}

function unlockScroll() {
  document.body.style.position = '';
  document.body.style.top = '';
  document.body.style.width = '';
  window.scrollTo(0, savedScrollY);
}

export function closeImageViewer() {
  if (!state.open) return;
  state.open = false;
  unlockScroll();
  document.removeEventListener('keydown', onKey);
  bound = false;
}

export function openImageViewer(src, opts = {}) {
  if (!src) return;
  const list = Array.isArray(opts.all) && opts.all.length ? opts.all : [src];
  state.src = src;
  state.list = list;
  state.index = Math.max(0, list.indexOf(src));
  state.open = true;
  lockScroll();
  if (!bound) {
    document.addEventListener('keydown', onKey);
    bound = true;
  }
}

/** 图片 URL 拼全：库里存的可能是相对路径（如 /api/uploads/…） */
export function absImageUrl(u) {
  if (!u) return '';
  if (/^(https?:)?\/\//.test(u) || u.startsWith('data:') || u.startsWith('blob:')) return u;
  return u.startsWith('/') ? u : `/${u}`;
}

// ⚠️ 组件本身在 components/ImageViewer.vue —— 不要在这里用字符串 template 定义。
// Vite 默认 runtime-only 构建**不编译**字符串 template，组件会静默不渲染
// （控制台只有一行警告），2026-10-09 就是这么踩的：以为预览器坏了，其实是根本没挂载。

export { state as imageViewerState };
// step 供组件左右切换用（原来只在组件内部闭包里，抽成 .vue 后必须显式导出）
export { step };